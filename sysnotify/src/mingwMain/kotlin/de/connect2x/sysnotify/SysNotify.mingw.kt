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
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
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

private inline fun MemScope.allocNotification(notification: Notification): sn_notification_content {
    val content = alloc<sn_notification_content>()
    content.title = allocCString(notification.title)
    content.description = allocCString(notification.description)
    return content
}

private inline fun MemScope.allocNotificationTag(tag: Uuid): sn_notification_tag {
    val result = alloc<sn_notification_tag>()
    result.lower = tag.leastSignificantBits.toULong()
    result.upper = tag.mostSignificantBits.toULong()
    return result
}

internal data class WindowsNotificationHandle(
    val handler: WindowsNotificationHandler,
    val tag: Uuid
) : NotificationHandle {
    val isValid: Boolean
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
) : NotificationHandler {
    internal val address: sn_notification_handler = memScoped {
        val pointer = alloc<sn_notification_handlerVar>()
        sysnotify_handler_create(
            name,
            "de.connect2x.sysnotify.$name",
            SYSNOTIFY_TRUE,
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
        isPopSupported = false
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
        memScoped {
            sysnotify_handler_pop(address, allocNotificationTag(handle.tag).ptr).check()
        }
    }
}

fun NotificationHandler.Companion.create(
    name: String
): NotificationHandler = WindowsNotificationHandler(name)
