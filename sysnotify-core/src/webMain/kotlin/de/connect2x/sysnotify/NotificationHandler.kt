/*
 * Copyright 2026 connect2x GmbH
 *
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

import js.objects.unsafeJso
import js.typedarrays.Uint8ClampedArray
import js.typedarrays.toInt8Array
import web.canvas.CanvasRenderingContext2D
import web.canvas.ID
import web.dom.TagName
import web.dom.document
import web.events.EventType
import web.events.addEventListener
import web.html.HTMLCanvasElement
import web.images.ImageData
import web.mouse.MouseEvent
import web.notifications.granted
import web.notifications.requestPermission
import web.url.URL
import web.notifications.Notification as JsNotification
import web.notifications.NotificationPermission as JsNotificationPermission

// https://developer.mozilla.org/en-US/docs/Web/API/Notification
private class WebNotificationHandler(
    override val appId: String,
    override val name: String,
    override val id: String,
    override val priority: NotificationPriority,
    override val contributesToCounter: Boolean
) : NotificationHandler {
    // TODO this does not survive a restart
    private val notifications: MutableMap<String, JsNotification> = mutableMapOf()

    override val platform: NotificationPlatform = NotificationPlatform.WEB
    override val hasPermissions: Boolean
        get() = JsNotification.permission == JsNotificationPermission.granted

    override val capabilities: NotificationCapabilities = NotificationCapabilities(
        isTitleSupported = true,
        isDescriptionSupported = true,
        isIconSupported = true,
        isActionSupported = true,
        isPopSupported = true,
        isSoundSupported = false, // TODO: reimplement this
        isCustomSoundSupported = true,
        isGroupingSupported = false,
        isStatusIconSupported = false,
        isCounterSupported = false
    )

    var activationHandler: ActivationHandler = {}

    override fun getHandle(tag: String): NotificationHandle {
        require(tag.isNotEmpty()) { "Handle tag may not be empty" }
        val notification = notifications[tag] ?: return NotificationHandle.INVALID
        return WebNotificationHandle(tag, notification)
    }

    override suspend fun requestPermissions(callback: PermissionCallback) {
        if (hasPermissions) {
            callback(true)
            return
        }
        // https://developer.mozilla.org/en-US/docs/Web/API/Notification/requestPermission_static
        val perms = JsNotification.requestPermission()
        callback(perms == JsNotificationPermission.granted)
    }

    private fun encodeIcon(icon: NotificationIcon?): String? {
        return icon?.let { icon ->
            val (data, width, height) = icon

            val canvas = document.createElement(TagName<HTMLCanvasElement>("canvas"))
            canvas.width = width
            canvas.height = height

            val context = checkNotNull(canvas.getContext(CanvasRenderingContext2D.ID)) {
                "Could not obtain CanvasRenderingContext2D to blit notification icon"
            }
            val imageBuffer = data.toInt8Array().buffer
            context.putImageData(ImageData(Uint8ClampedArray(imageBuffer), width, height), 0, 0)

            canvas.toDataURL("image/png")
        }
    }

    override suspend fun push(notification: Notification, tag: String): NotificationHandle {
        if (!hasPermissions) return NotificationHandle.INVALID
        val iconUrl = encodeIcon(notification.icon)
        val badgeUrl = encodeIcon(notification.statusIcon)
        return WebNotificationHandle(
            instance = JsNotification(
                title = notification.title ?: "",
                options = unsafeJso {
                    body = notification.description ?: ""
                    icon = iconUrl
                    silent = !notification.playSound
                    this.tag = tag
                    badge = badgeUrl
                }
            ).apply {
                // https://developer.mozilla.org/en-US/docs/Web/API/Notification/click_event
                addEventListener(EventType<MouseEvent>("click"), {
                    activationHandler(notification)
                    iconUrl?.let(URL::revokeObjectURL)
                    notifications -= tag
                })
                // https://developer.mozilla.org/en-US/docs/Web/API/Notification/close_event
                addEventListener(EventType("close"), {
                    iconUrl?.let(URL::revokeObjectURL)
                    notifications -= tag
                })
                notifications[tag] = this
            },
            tag = tag
        )
    }

    override suspend fun pop(handle: NotificationHandle) {
        if (!hasPermissions || handle !is WebNotificationHandle) return
        handle.instance?.close()
        notifications -= handle.tag
    }

    override suspend fun clearAll() {
        if (!hasPermissions) return
        for ((_, notification) in notifications) {
            notification.close()
        }
        notifications.clear()
    }

    override suspend fun unregister() {
        if (!hasPermissions) return
        clearAll()
        close()
    }

    override fun close() {}
}

actual fun NotificationHandler.withActivationHandler(handler: ActivationHandler): NotificationHandler {
    if (this !is WebNotificationHandler) return this
    val previousHandler = activationHandler
    activationHandler = { notification ->
        handler(notification)
        previousHandler(notification)
    }
    return this
}

actual fun NotificationHandler( // @formatter:off
    name: String,
    id: String,
    priority: NotificationPriority,
    appId: String,
    contributesToCounter: Boolean
): NotificationHandler = WebNotificationHandler( // @formatter:on
    appId = appId,
    name = name,
    id = id,
    priority = priority,
    contributesToCounter = contributesToCounter
)
