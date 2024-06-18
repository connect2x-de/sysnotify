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
import libnotify.notify_notification_update
import libnotify.notify_uninit
import kotlin.math.abs
import kotlin.math.floor
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
    companion object {
        private const val DEFAULT_ICON: String = "dialog-information"
    }

    override val platform: NotificationPlatform = NotificationPlatform.LINUX

    override val capabilities: NotificationCapabilities = NotificationCapabilities(
        isTitleSupported = true,
        isDescriptionSupported = true,
        isIconSupported = true,
        isActionSupported = false,
        isPopSupported = true
    )

    init {
        require(notify_init(name) != 0) { "Could not initialize libnotify" }
    }

    override fun close() {
        notify_uninit()
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun setIcon(notification: CPointer<NotifyNotification>, icon: ByteArray?) {
        icon?.let { array ->
            array.usePinned {
                // Determine the side-length of the icon by sqrt(data.size / 4)
                val size = sqrt((array.size shr 2).toFloat()).apply {
                    // Check if the root was pulled with any odd decimal points, if it was, icon is not square
                    require((this - floor(abs(this))) == 0F) { "Icon image is not square" }
                }.toInt()
                // Determine row stride; we have 4 bytes per pixel
                val rowStride = size shl 2
                val buffer = gdk_pixbuf_new_from_bytes(
                    it.addressOf(0).reinterpret(),
                    GdkColorspace.GDK_COLORSPACE_RGB, 1, 8, size, size, rowStride
                )
                notify_notification_set_image_from_pixbuf(notification, buffer)
                gdk_pixbuf_unref(buffer)
            }
        }
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun show(notification: CPointer<NotifyNotification>): Boolean {
        memScoped {
            val errorAddress = allocPointerTo<GError>()
            if (notify_notification_show(notification, errorAddress.ptr) == 0) {
                errorAddress.value?.freeAndThrow()
                return false // Return if we fall through, which doesn't happen, but no _Noreturn..
            }
        }
        return true
    }

    override suspend fun push(notification: Notification): NotificationHandle? {
        val notificationAddress = notify_notification_new(
            summary = notification.title,
            body = notification.description,
            icon = DEFAULT_ICON
        ) ?: return null
        notify_notification_set_timeout(notificationAddress, 5000) // 5 seconds by default
        setIcon(notificationAddress, notification.icon)
        return if (show(notificationAddress)) LinuxNotificationHandle(notificationAddress) else null
    }

    override suspend fun update(handle: NotificationHandle, notification: Notification): NotificationHandle? {
        require(handle is LinuxNotificationHandle) { "Handle is not a Linux notification handle" }
        val notificationAddress = handle.address
        notify_notification_update(
            notification = notificationAddress,
            summary = notification.title,
            body = notification.description,
            icon = DEFAULT_ICON
        )
        setIcon(notificationAddress, notification.icon)
        return if (show(notificationAddress)) handle else null
    }

    override suspend fun pop(handle: NotificationHandle) {
        require(handle is LinuxNotificationHandle) { "Handle is not a Linux notification handle" }
        memScoped {
            val errorAddress = allocPointerTo<GError>()
            if (notify_notification_close(handle.address, errorAddress.ptr) == 0) {
                errorAddress.value?.freeAndThrow()
                return
            }
        }
    }
}

fun NotificationHandler.Companion.create(
    name: String,
): NotificationHandler = LinuxNotificationHandler(name)
