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

package de.connect2x.sysnotify

import de.connect2x.sysnotify.com.SYSNOTIFY_FALSE
import de.connect2x.sysnotify.com.SYSNOTIFY_TRUE
import de.connect2x.sysnotify.com.sn_notification_content
import de.connect2x.sysnotify.com.sn_notification_handler
import de.connect2x.sysnotify.com.sn_notification_handlerVar
import de.connect2x.sysnotify.com.sn_notification_tag
import de.connect2x.sysnotify.com.sysnotify_handler_create
import de.connect2x.sysnotify.com.sysnotify_handler_destroy
import de.connect2x.sysnotify.com.sysnotify_handler_pop
import de.connect2x.sysnotify.com.sysnotify_handler_push
import de.connect2x.sysnotify.com.sysnotify_handler_update
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.alloc
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.value
import kotlin.uuid.ExperimentalUuidApi

internal class WindowsNotificationHandler(
    override val name: String,
    override val id: String,
    override val priority: NotificationPriority,
    private val activationCallback: (Notification) -> Unit,
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
    override val hasPermissions: Boolean = true

    override val capabilities: NotificationCapabilities = NotificationCapabilities(
        isTitleSupported = true,
        isDescriptionSupported = true,
        isIconSupported = true,
        isActionSupported = true,
        isPopSupported = true,
        isSoundSupported = true,
        isCustomSoundSupported = true,
        isGroupingSupported = false
    )

    override fun close() {
        sysnotify_handler_destroy(address).check()
    }

    override fun create(notification: Notification): NotificationHandle =
        InvalidNotificationHandle // TODO: implement this

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun push(notification: Notification): NotificationHandle {
        return memScoped {
            val handle = alloc<sn_notification_tag>()
            val content = allocNotification(notification)
            sysnotify_handler_push(address, content.ptr, handle.ptr).check()
            WindowsNotificationHandle(this@WindowsNotificationHandler, handle.toUuid())
        }
    }

    @OptIn(ExperimentalUuidApi::class)
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
                content.ptr,
            ).check()
        }
        return handle
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun pop(handle: NotificationHandle) {
        require(handle is WindowsNotificationHandle) { "Handle is not a Windows notification handle" }
        require(handle.isValid) { "Handle is not valid" }
        memScoped {
            sysnotify_handler_pop(address, allocNotificationTag(handle.tag).ptr).check()
        }
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun MemScope.allocNotification(notification: Notification): sn_notification_content {
        return alloc<sn_notification_content> {
            title = notification.title?.cstr?.ptr
            description = notification.description?.cstr?.ptr
            user_data = StableRef.create(Pair(notification, this)).asCPointer()
            icon_width = notification.icon?.width ?: 0
            icon_height = notification.icon?.height ?: 0
            is_silent = if (notification.playSound) SYSNOTIFY_TRUE else SYSNOTIFY_FALSE
            sound_path = notification.soundPath?.cstr?.ptr
            activate_callback = staticCFunction { _, callbackData ->
                callbackData?.asStableRef<Pair<Notification, WindowsNotificationHandler>>()?.apply {
                    val (currentNotification, currentHandler) = get()
                    currentHandler.activationCallback(currentNotification)
                    dispose()
                }
            }
        }
    }
}

fun NotificationHandler.Companion.create(
    name: String,
    id: String = name,
    activationCallback: (Notification) -> Unit = {},
    priority: NotificationPriority = NotificationPriority.DEFAULT,
): NotificationHandler = WindowsNotificationHandler(name, id, priority, activationCallback)
