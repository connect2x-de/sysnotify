/*
 * Copyright 2024 connect2x
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

package de.connect2x.sysnotify

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocPointerTo
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKStringFromUtf8
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import libnotify.GError
import libnotify.GdkColorspace
import libnotify.NotifyNotification
import libnotify.g_error_free
import libnotify.gdk_pixbuf_new_from_bytes
import libnotify.gdk_pixbuf_unref
import libnotify.notify_init
import libnotify.notify_notification_close
import libnotify.notify_notification_new
import libnotify.notify_notification_set_image_from_pixbuf
import libnotify.notify_notification_set_timeout
import libnotify.notify_notification_show
import libnotify.notify_uninit
import kotlin.math.sqrt

@OptIn(ExperimentalForeignApi::class)
fun <P : CPointer<GError>> P.freeAndThrow() {
    val message = this.pointed.message?.toKStringFromUtf8()
    g_error_free(this)
    throw IllegalStateException(message)
}

@OptIn(ExperimentalForeignApi::class)
internal class LinuxNotificationHandle(
    internal val address: CPointer<NotifyNotification>
) : NotificationHandle

@OptIn(ExperimentalForeignApi::class)
internal class LinuxNotificationHandler(
    override val name: String,
) : NotificationHandler {
    override val platform: NotificationPlatform = NotificationPlatform.LINUX

    override val capabilities: NotificationCapabilities = NotificationCapabilities(
        isTitleSupported = true,
        isDescriptionSupported = true,
        isIconSupported = true,
        isActionSupported = false,
        isPopSupported = true
    )

    private var isInitialized: Boolean = false

    init {
        isInitialized = notify_init(name) == 1
    }

    override fun close() {
        if (!isInitialized) return
        notify_uninit()
    }

    override suspend fun push(notification: Notification): NotificationHandle? {
        if (!isInitialized) return null
        val notificationAddress = notify_notification_new(
            summary = notification.title,
            body = notification.description,
            icon = "dialog-information" // Default fallback icon
        ) ?: return null
        notify_notification_set_timeout(notificationAddress, 5000) // 5 seconds by default
        memScoped {
            notification.icon?.let { array ->
                array.usePinned {
                    val size =
                        sqrt(array.size.toFloat()).toInt() // Assume the icon is always square
                    val rowStride = size shl 2 // We have 4 bytes per pixel
                    val buffer = gdk_pixbuf_new_from_bytes(
                        it.addressOf(0).reinterpret(),
                        GdkColorspace.GDK_COLORSPACE_RGB, 1, 8, size, size, rowStride
                    )
                    notify_notification_set_image_from_pixbuf(notificationAddress, buffer)
                    gdk_pixbuf_unref(buffer)
                }
            }
            val errorAddress = allocPointerTo<GError>()
            if (notify_notification_show(notificationAddress, errorAddress.ptr) == 0) {
                errorAddress.value?.freeAndThrow()
                return null // Return if we fall through, which doesn't happen, but no _Noreturn..
            }
        }
        return LinuxNotificationHandle(notificationAddress)
    }

    override suspend fun pop(handle: NotificationHandle) {
        if (!isInitialized || handle !is LinuxNotificationHandle) return
        memScoped {
            val errorAddress = allocPointerTo<GError>()
            if (notify_notification_close(handle.address, errorAddress.ptr) == 0) {
                errorAddress.value?.freeAndThrow()
                return
            }
        }
    }
}

actual fun createNotificationHandler(
    name: String,
    context: Any?
): NotificationHandler = LinuxNotificationHandler(name)
