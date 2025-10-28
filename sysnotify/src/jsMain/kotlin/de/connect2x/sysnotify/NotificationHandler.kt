/*
 * Copyright 2025 connect2x GmbH
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

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import org.khronos.webgl.Int8Array
import org.khronos.webgl.Uint8ClampedArray
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.ImageData
import org.w3c.dom.url.URL
import org.w3c.notifications.GRANTED
import org.w3c.notifications.NotificationOptions
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.uuid.ExperimentalUuidApi
import org.w3c.notifications.Notification as JsNotification
import org.w3c.notifications.NotificationPermission as JsNotificationPermission

// https://developer.mozilla.org/en-US/docs/Web/API/Notification
@OptIn(ExperimentalAtomicApi::class)
private class WebNotificationHandler(
    override val appId: String,
    override val name: String,
    override val id: String,
    override val priority: NotificationPriority
) : NotificationHandler {
    // TODO this does not survive a restart
    private val notifications: MutableMap<String, JsNotification> = mutableMapOf()

    override val platform: NotificationPlatform = NotificationPlatform.WEB
    override val hasPermissions: Boolean
        get() = JsNotification.permission == JsNotificationPermission.GRANTED

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

    val activationHandler: AtomicReference<ActivationHandler> = AtomicReference {}

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
        val perms = JsNotification.requestPermission().await()
        callback(perms == JsNotificationPermission.GRANTED)
    }

    private fun encodeIcon(notification: Notification): String? {
        return notification.icon?.let { icon ->
            val (data, width, height) = icon

            val canvas = document.createElement("canvas") as HTMLCanvasElement
            canvas.width = width
            canvas.height = height

            val context = canvas.getContext("2d") as CanvasRenderingContext2D
            val imageBuffer = Int8Array(data.toTypedArray()).buffer
            context.putImageData(ImageData(Uint8ClampedArray(imageBuffer), width, height), 0.0, 0.0)

            canvas.toDataURL("image/png")
        }
    }

    @OptIn(ExperimentalEncodingApi::class, ExperimentalUuidApi::class)
    override suspend fun push(notification: Notification, tag: String): NotificationHandle {
        if (!hasPermissions) return NotificationHandle.INVALID
        val iconUrl = encodeIcon(notification)
        return WebNotificationHandle(
            instance = JsNotification(
                title = notification.title ?: "",
                options = NotificationOptions(
                    body = notification.description,
                    icon = iconUrl,
                    silent = !notification.playSound,
                    sound = notification.soundPath,
                    tag = tag,
                )
            ).apply {
                // https://developer.mozilla.org/en-US/docs/Web/API/Notification/click_event
                addEventListener(
                    type = "click",
                    callback = {
                        activationHandler.load()(notification)
                        iconUrl?.let(URL::revokeObjectURL)
                        notifications -= tag
                    }
                )
                // https://developer.mozilla.org/en-US/docs/Web/API/Notification/close_event
                addEventListener(
                    type = "close",
                    callback = {
                        iconUrl?.let(URL::revokeObjectURL)
                        notifications -= tag
                    }
                )
                notifications[tag] = this
            },
            tag = tag
        )
    }

    override suspend fun pop(handle: NotificationHandle) {
        if (!hasPermissions) return
        handle.check<WebNotificationHandle>()
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

@OptIn(ExperimentalAtomicApi::class)
actual fun NotificationHandler.withActivationHandler(handler: ActivationHandler): NotificationHandler {
    check<WebNotificationHandler>()
    val previousHandler = activationHandler.load()
    activationHandler.store {
        handler(it)
        previousHandler(it)
    }
    return this
}

actual fun NotificationHandler( // @formatter:off
    name: String,
    id: String,
    priority: NotificationPriority,
    isDebugEnabled: Boolean,
    appId: String
): NotificationHandler { // @formatter:on
    return WebNotificationHandler(appId, name, id, priority)
}
