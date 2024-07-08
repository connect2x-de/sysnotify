/*
 * Copyright 2024 connect2x GmbH
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:OptIn(ExperimentalForeignApi::class)
@file:Suppress("NOTHING_TO_INLINE")

package de.connect2x.sysnotify

import com.benasher44.uuid.Uuid
import de.connect2x.sysnotify.com.SYSNOTIFY_OK
import de.connect2x.sysnotify.com.SYSNOTIFY_TRUE
import de.connect2x.sysnotify.com.sn_boolVar
import de.connect2x.sysnotify.com.sn_error
import de.connect2x.sysnotify.com.sn_notification_content
import de.connect2x.sysnotify.com.sn_notification_dismissal_reason
import de.connect2x.sysnotify.com.sn_notification_handler
import de.connect2x.sysnotify.com.sn_notification_handlerVar
import de.connect2x.sysnotify.com.sn_notification_tag
import de.connect2x.sysnotify.com.sysnotify_get_last_error
import de.connect2x.sysnotify.com.sysnotify_handler_create
import de.connect2x.sysnotify.com.sysnotify_handler_destroy
import de.connect2x.sysnotify.com.sysnotify_handler_is_valid_tag
import de.connect2x.sysnotify.com.sysnotify_handler_pop
import de.connect2x.sysnotify.com.sysnotify_handler_push
import de.connect2x.sysnotify.com.sysnotify_handler_update
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.Pinned
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pin
import kotlinx.cinterop.ptr
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toKStringFromUtf8
import kotlinx.cinterop.value

private inline fun sn_error.check() {
    if (this == SYSNOTIFY_OK) return
    throwLastError()
}

private inline fun throwLastError() {
    sysnotify_get_last_error()?.apply {
        throw IllegalStateException(toKStringFromUtf8())
    }
}

private inline fun sn_notification_tag.toUuid(): Uuid = Uuid(lower.toLong(), upper.toLong())

private inline fun MemScope.allocNotificationTag(tag: Uuid): sn_notification_tag {
    return alloc<sn_notification_tag> {
        lower = tag.leastSignificantBits.toULong()
        upper = tag.mostSignificantBits.toULong()
    }
}

internal data class WindowsNotificationHandle(
    val handler: WindowsNotificationHandler,
    val tag: Uuid
) : NotificationHandle {
    inline val isValid: Boolean
        get() = memScoped {
            val result = alloc<sn_boolVar>()
            sysnotify_handler_is_valid_tag(
                handler.address,
                allocNotificationTag(tag).ptr,
                result.ptr
            ).check()
            return result.value == SYSNOTIFY_TRUE
        }
}

internal class WindowsNotificationHandler(
    override val name: String,
    override val id: String
) : NotificationHandler {
    internal val address: sn_notification_handler = memScoped {
        val pointer = alloc<sn_notification_handlerVar>()
        sysnotify_handler_create(
            name,
            id,
            pointer.ptr
        ).check()
        requireNotNull(pointer.value) { "Could not create COM adaptor instance" }
    }
    override val platform: NotificationPlatform = NotificationPlatform.WINDOWS

    override val capabilities: NotificationCapabilities = NotificationCapabilities(
        isTitleSupported = true,
        isDescriptionSupported = true,
        isIconSupported = true,
        isActionSupported = true,
        isPopSupported = true
    )

    override fun close() {
        sysnotify_handler_destroy(address).check()
    }

    override suspend fun push(notification: Notification): NotificationHandle {
        return memScoped {
            val handle = alloc<sn_notification_tag>()
            val content = allocNotification(notification)
            sysnotify_handler_push(address, content.ptr, handle.ptr).check()
            WindowsNotificationHandle(this@WindowsNotificationHandler, handle.toUuid())
        }
    }

    override suspend fun update(
        handle: NotificationHandle,
        notification: Notification
    ): NotificationHandle {
        require(handle is WindowsNotificationHandle) { "Handle is not a Windows notification handle" }
        require(handle.isValid) { "Handle is not valid" }
        memScoped {
            val tag = allocNotificationTag(handle.tag)
            val content = allocNotification(notification)
            sysnotify_handler_update(
                address,
                tag.ptr,
                content.ptr
            ).check()
        }
        return handle
    }

    override suspend fun pop(handle: NotificationHandle) {
        require(handle is WindowsNotificationHandle) { "Handle is not a Windows notification handle" }
        require(handle.isValid) { "Handle is not valid" }
        memScoped {
            sysnotify_handler_pop(address, allocNotificationTag(handle.tag).ptr).check()
        }
    }

    private inline fun MemScope.allocNotification(notification: Notification): sn_notification_content {
        return alloc<sn_notification_content> {
            title = allocCString(notification.title)
            description = allocCString(notification.description)
            val iconData = notification.icon?.data?.pin()?.apply {
                icon = addressOf(0)
            }
            user_data = StableRef.create(Pair(notification, iconData)).asCPointer()
            icon_width = notification.icon?.width ?: 0
            icon_height = notification.icon?.height ?: 0
            activate_action =
                staticCFunction { _: CPointer<sn_notification_tag>?, userData: COpaquePointer? ->
                    requireNotNull(userData).asStableRef<Pair<Notification, Pinned<ByteArray>?>>()
                        .get().first.onActivated?.invoke()
                }
            dismiss_action =
                staticCFunction { _: CPointer<sn_notification_tag>?, userData: COpaquePointer?, reason: sn_notification_dismissal_reason ->
                    requireNotNull(userData).asStableRef<Pair<Notification, Pinned<ByteArray>?>>()
                        .get().first.onDismissed?.invoke(NotificationDismissalReason.entries[reason])
                }
            fail_action =
                staticCFunction { _: CPointer<sn_notification_tag>?, userData: COpaquePointer? ->
                    requireNotNull(userData).asStableRef<Pair<Notification, Pinned<ByteArray>?>>().get().first.onFailed?.invoke()
                }
            remove_action =
                staticCFunction { _: CPointer<sn_notification_tag>?, userData: COpaquePointer? ->
                    requireNotNull(userData).asStableRef<Pair<Notification, Pinned<ByteArray>?>>().apply {
                        get().apply {
                            first.onRemoved?.invoke()
                            second?.unpin()
                        }
                        dispose()
                    }
                }
        }
    }
}

fun NotificationHandler.Companion.create(
    name: String,
    id: String = name
): NotificationHandler = WindowsNotificationHandler(name, id)
