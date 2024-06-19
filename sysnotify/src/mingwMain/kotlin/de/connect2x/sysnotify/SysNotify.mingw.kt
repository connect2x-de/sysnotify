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

import de.connect2x.sysnotify.com.SYSNOTIFY_OK
import de.connect2x.sysnotify.com.sn_error
import de.connect2x.sysnotify.com.sn_notification
import de.connect2x.sysnotify.com.sn_notificationVar
import de.connect2x.sysnotify.com.sn_notification_content_t
import de.connect2x.sysnotify.com.sn_notification_handler
import de.connect2x.sysnotify.com.sn_notification_handlerVar
import de.connect2x.sysnotify.com.sysnotify_com_create
import de.connect2x.sysnotify.com.sysnotify_com_destroy
import de.connect2x.sysnotify.com.sysnotify_com_get_last_error
import de.connect2x.sysnotify.com.sysnotify_com_pop
import de.connect2x.sysnotify.com.sysnotify_com_push
import de.connect2x.sysnotify.com.sysnotify_com_update
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKStringFromUtf8
import kotlinx.cinterop.value

@Suppress("NOTHING_TO_INLINE")
private inline fun sn_error.check() {
    if (this == SYSNOTIFY_OK) return
    throwLastError()
}

@Suppress("NOTHING_TO_INLINE")
private inline fun throwLastError() {
    sysnotify_com_get_last_error()?.apply {
        throw IllegalStateException(toKStringFromUtf8())
    }
}

internal data class WindowsNotificationHandle(
    val address: sn_notification
) : NotificationHandle

internal class WindowsNotificationHandler(
    override val name: String,
) : NotificationHandler {
    private val address: sn_notification_handler = memScoped {
        val pointer = alloc<sn_notification_handlerVar>()
        sysnotify_com_create(name, pointer.ptr).check()
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
        sysnotify_com_destroy(address).check()
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun MemScope.allocNotification(notification: Notification): sn_notification_content_t {
        val content = alloc<sn_notification_content_t>()
        content.title = allocCString(notification.title)
        content.description = allocCString(notification.description)
        return content
    }

    override suspend fun push(notification: Notification): NotificationHandle {
        return memScoped {
            val content = allocNotification(notification)
            val handle = alloc<sn_notificationVar>()
            sysnotify_com_push(address, content.ptr, handle.ptr).check()
            WindowsNotificationHandle(requireNotNull(handle.value))
        }
    }

    override suspend fun update(
        handle: NotificationHandle,
        notification: Notification
    ): NotificationHandle {
        require(handle is WindowsNotificationHandle) { "Handle is not a Windows notification handle" }
        memScoped {
            val content = allocNotification(notification)
            sysnotify_com_update(address, handle.address, content.ptr).check()
        }
        return handle
    }

    override suspend fun pop(handle: NotificationHandle) {
        require(handle is WindowsNotificationHandle) { "Handle is not a Windows notification handle" }
        sysnotify_com_pop(address, handle.address).check()
    }
}

fun NotificationHandler.Companion.create(
    name: String
): NotificationHandler = WindowsNotificationHandler(name)
