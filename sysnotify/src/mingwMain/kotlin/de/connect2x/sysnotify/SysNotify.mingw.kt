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

import de.connect2x.sysnotify.com.SYSNOTIFY_ERR_ABORTED
import de.connect2x.sysnotify.com.SYSNOTIFY_ERR_INVALID_ARG
import de.connect2x.sysnotify.com.SYSNOTIFY_OK
import de.connect2x.sysnotify.com.sn_error
import de.connect2x.sysnotify.com.sn_notification_handler
import de.connect2x.sysnotify.com.sn_notification_handlerVar
import de.connect2x.sysnotify.com.sysnotify_com_create
import de.connect2x.sysnotify.com.sysnotify_com_destroy
import de.connect2x.sysnotify.com.sysnotify_com_get_last_error
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocPointerTo
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKStringFromUtf8
import kotlinx.cinterop.value

internal data class WindowsNotificationHandle(
    val address: COpaquePointer
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

    override suspend fun push(notification: Notification): NotificationHandle? {
        return null // TODO: ...
    }

    override suspend fun pop(handle: NotificationHandle) {
        // TODO: ...
    }

    private fun sn_error.check() {
        if (this == SYSNOTIFY_OK) return
        throw IllegalStateException(
            when (this) {
                SYSNOTIFY_ERR_INVALID_ARG -> "Invalid COM argument"
                SYSNOTIFY_ERR_ABORTED -> "COM operation aborted"
                else -> "Unknown COM error"
            }
        )
    }

    private fun sn_error.throwIfError() {
        memScoped {
            val error = allocPointerTo<ByteVar>()
            sysnotify_com_get_last_error(address, error.ptr)
            error.value?.apply {
                throw IllegalStateException(toKStringFromUtf8())
            }
        }
    }
}

fun NotificationHandler.Companion.create(
    name: String
): NotificationHandler = WindowsNotificationHandler(name)
