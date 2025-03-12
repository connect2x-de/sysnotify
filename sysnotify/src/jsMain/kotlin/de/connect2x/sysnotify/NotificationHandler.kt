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

import co.touchlab.stately.collections.SharedHashMap
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.await
import org.w3c.notifications.GRANTED
import org.w3c.notifications.NotificationOptions
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.uuid.ExperimentalUuidApi
import org.w3c.notifications.Notification as JsNotification
import org.w3c.notifications.NotificationPermission as JsNotificationPermission

// https://developer.mozilla.org/en-US/docs/Web/API/Notification
private class WebNotificationHandler(
    override val name: String,
    override val id: String,
    override val priority: NotificationPriority
) : NotificationHandler {
    private val notifications: SharedHashMap<String, JsNotification> = SharedHashMap()

    override val platform: NotificationPlatform = NotificationPlatform.WEB
    override var hasPermissions: Boolean by atomic(false)

    override val capabilities: NotificationCapabilities = NotificationCapabilities(
        isTitleSupported = true,
        isDescriptionSupported = true,
        isIconSupported = true,
        isActionSupported = false,
        isPopSupported = true,
        isSoundSupported = true,
        isCustomSoundSupported = false,
        isGroupingSupported = false
    )

    override fun getHandle(tag: String): NotificationHandle {
        require(tag.isNotEmpty()) { "Handle tag may not be empty" }
        require(tag in notifications) { "Handle must be that of an existing notification" }
        return WebNotificationHandle(tag, notifications[tag])
    }

    override suspend fun requestPermissions(callback: PermissionCallback) {
        if (hasPermissions) {
            callback(this, true)
            return
        }
        // https://developer.mozilla.org/en-US/docs/Web/API/Notification/requestPermission_static
        val perms = JsNotification.requestPermission().await()
        hasPermissions = perms === JsNotificationPermission.GRANTED
        callback(this, hasPermissions)
    }

    @OptIn(ExperimentalEncodingApi::class, ExperimentalUuidApi::class)
    override suspend fun push(notification: Notification, tag: String): NotificationHandle {
        if (!hasPermissions) return NotificationHandle.INVALID
        val icon = notification.icon?.data?.let { Base64.encode(it) }
        return WebNotificationHandle(
            instance = JsNotification(
                title = notification.title ?: "",
                options = NotificationOptions(
                    body = notification.description,
                    icon = icon?.let { "data:image/*;base64,$it" },
                    silent = !notification.playSound,
                    sound = notification.soundPath,
                )
            ).apply {
                // https://developer.mozilla.org/en-US/docs/Web/API/Notification/close_event
                addEventListener(
                    type = "close",
                    callback = { event ->
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
}

actual fun NotificationHandler( // @formatter:off
    name: String,
    id: String,
    priority: NotificationPriority
): NotificationHandler { // @formatter:on
    return WebNotificationHandler(name, id, priority)
}
