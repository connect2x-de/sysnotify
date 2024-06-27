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

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocPointerTo
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toKStringFromUtf8
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import libnotify.GError
import libnotify.GdkColorspace
import libnotify.g_bytes_new_static
import libnotify.g_bytes_unref
import libnotify.g_error_free
import libnotify.g_object_unref
import libnotify.gdk_pixbuf_new_from_bytes
import libnotify.gdk_pixbuf_unref
import libnotify.gpointer
import libnotify.NotifyNotification
import libnotify.NotifyUrgency
import libnotify.notify_init
import libnotify.notify_notification_add_action
import libnotify.notify_notification_close
import libnotify.notify_notification_new
import libnotify.notify_notification_set_app_name
import libnotify.notify_notification_set_hint_string
import libnotify.notify_notification_set_icon_from_pixbuf
import libnotify.notify_notification_set_timeout
import libnotify.notify_notification_set_urgency
import libnotify.notify_notification_show
import libnotify.notify_notification_update
import libnotify.notify_uninit

fun <P : CPointer<GError>> P.freeAndThrow() {
    val message = this.pointed.message?.toKStringFromUtf8()
    g_error_free(this)
    throw IllegalStateException(message)
}

internal class LinuxNotificationHandle(
    internal val address: CPointer<NotifyNotification>
) : NotificationHandle

internal class LinuxNotificationHandler(
    override val name: String,
) : NotificationHandler {
    companion object {
        private const val DEFAULT_ICON: String = "dialog-information"
        private const val ACTION_ACTIVATE: String = "default"
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

    private inline fun setIcon(nativeNotification: CPointer<NotifyNotification>, notification: Notification) {
        notification.icon?.let { icon ->
            val width = icon.width
            val height = icon.height
            if (width <= 0 || height <= 0) return // Return if our size is invalid
            icon.data.usePinned { pinnedArray ->
                val data = g_bytes_new_static(pinnedArray.addressOf(0), icon.data.size.toULong())
                val buffer = gdk_pixbuf_new_from_bytes(
                    data, GdkColorspace.GDK_COLORSPACE_RGB, 1, 8, width, height, width shl 2
                )
                notify_notification_set_icon_from_pixbuf(nativeNotification, buffer)
                gdk_pixbuf_unref(buffer)
                g_bytes_unref(data)
            }
        }
    }

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

        notify_notification_set_app_name(notificationAddress, name)
        notify_notification_set_timeout(notificationAddress, 5000) // 5 seconds by default
        notify_notification_set_urgency(notificationAddress, NotifyUrgency.NOTIFY_URGENCY_NORMAL)

        notify_notification_add_action(notificationAddress, ACTION_ACTIVATE, name,
            staticCFunction { _: CPointer<NotifyNotification>?, action: CPointer<ByteVar>?, userData: gpointer? ->
                requireNotNull(userData).asStableRef<Notification>().apply {
                    get().onActivated?.invoke()
                }
                Unit
            }, StableRef.create(notification).asCPointer(),
            staticCFunction { userData: gpointer? ->
                userData?.asStableRef<Notification>()?.dispose()
                Unit
            }
        )

        // On supported FreeDesktop based platforms like GNOME and KDE this will play the default notification sound
        notify_notification_set_hint_string(
            notificationAddress,
            "sound-file",
            "/usr/share/sounds/freedesktop/stereo/message.oga"
        )
        setIcon(notificationAddress, notification)
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
        setIcon(notificationAddress, notification)
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
        g_object_unref(handle.address)
    }
}

fun NotificationHandler.Companion.create(
    name: String,
): NotificationHandler = LinuxNotificationHandler(name)
