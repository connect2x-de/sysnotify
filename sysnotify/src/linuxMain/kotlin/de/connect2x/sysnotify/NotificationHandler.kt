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

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocPointerTo
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import libnotify.GError
import libnotify.GdkColorspace
import libnotify.NotifyNotification

internal class LinuxNotificationHandler(
    override val name: String,
    override val id: String,
    override val priority: NotificationPriority,
    private val activationCallback: (Notification) -> Unit,
) : NotificationHandler {
    companion object {
        private const val DEFAULT_ICON: String = "dialog-information"
        private const val DEFAULT_SOUND: String = "/usr/share/sounds/freedesktop/stereo/message.oga"
        private const val ACTION_ACTIVATE: String = "default"
    }

    override val platform: NotificationPlatform = NotificationPlatform.LINUX
    override val hasPermissions: Boolean = true // Desktop on Linux always has permission to send notifications

    override val capabilities: NotificationCapabilities = NotificationCapabilities(
        isTitleSupported = true,
        isDescriptionSupported = true,
        isIconSupported = true,
        isActionSupported = false,
        isPopSupported = true,
        isSoundSupported = true,
        isCustomSoundSupported = true,
    )

    init {
        require(LibNotify.notify_init(name) != 0) { "Could not initialize libnotify" }
    }

    override fun close() {
        LibNotify.notify_uninit()
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun setIcon(nativeNotification: CPointer<NotifyNotification>, notification: Notification) {
        notification.icon?.let { icon ->
            val width = icon.width
            val height = icon.height
            if (width <= 0 || height <= 0) return // Return if our size is invalid
            icon.data.usePinned { pinnedArray ->
                val data = LibGlib.g_bytes_new_static(pinnedArray.addressOf(0), icon.data.size.toULong())
                val buffer = LibGdkPixbuf.gdk_pixbuf_new_from_bytes(
                    data, GdkColorspace.GDK_COLORSPACE_RGB, 1, 8, width, height, width shl 2
                )
                LibNotify.notify_notification_set_icon_from_pixbuf(nativeNotification, buffer)
                LibGdkPixbuf.gdk_pixbuf_unref(buffer)
                LibGlib.g_bytes_unref(data)
            }
        }
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun show(notification: CPointer<NotifyNotification>): Boolean {
        memScoped {
            val errorAddress = allocPointerTo<GError>()
            if (LibNotify.notify_notification_show(notification, errorAddress.ptr) == 0) {
                errorAddress.value?.freeAndThrow()
                return false // Return if we fall through, which doesn't happen, but no _Noreturn..
            }
        }
        return true
    }

    override fun create(notification: Notification): NotificationHandle =
        InvalidNotificationHandle // TODO: implement this

    override suspend fun push(notification: Notification): NotificationHandle? {
        val notificationAddress = LibNotify.notify_notification_new(
            summary = notification.title,
            body = notification.description,
            icon = DEFAULT_ICON
        ) ?: return null

        LibNotify.notify_notification_set_app_name(notificationAddress, name)
        LibNotify.notify_notification_set_timeout(notificationAddress, 5000) // 5 seconds by default
        LibNotify.notify_notification_set_urgency(notificationAddress, notification.priority.urgency)

        //notify_notification_add_action(notificationAddress, ACTION_ACTIVATE, name,
        //    staticCFunction { _: CPointer<NotifyNotification>?, action: CPointer<ByteVar>?, userData: gpointer? ->
        //        requireNotNull(userData).asStableRef<Notification>().apply {
        //            get().onActivated?.invoke()
        //        }
        //        Unit
        //    }, StableRef.create(notification).asCPointer(),
        //    staticCFunction { userData: gpointer? ->
        //        userData?.asStableRef<Notification>()?.dispose()
        //        Unit
        //    }
        //)

        if (notification.playSound) {
            LibNotify.notify_notification_set_hint_string(
                notificationAddress,
                "sound-file",
                notification.soundPath ?: DEFAULT_SOUND
            )
        }
        setIcon(notificationAddress, notification)
        return if (show(notificationAddress)) LinuxNotificationHandle(notificationAddress) else null
    }

    override suspend fun update(handle: NotificationHandle, notification: Notification): NotificationHandle? {
        require(handle is LinuxNotificationHandle) { "Handle is not a Linux notification handle" }
        val notificationAddress = handle.address
        LibNotify.notify_notification_update(
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
            if (LibNotify.notify_notification_close(handle.address, errorAddress.ptr) == 0) {
                errorAddress.value?.freeAndThrow()
                return
            }
        }
        LibGlib.g_object_unref(handle.address.reinterpret())
    }
}

fun NotificationHandler.Companion.create(
    name: String,
    id: String = name,
    activationCallback: (Notification) -> Unit = {},
    priority: NotificationPriority = NotificationPriority.DEFAULT,
): NotificationHandler = LinuxNotificationHandler(name, id, priority, activationCallback)
