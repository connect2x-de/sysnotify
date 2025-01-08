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

import kotlinx.coroutines.await
import org.w3c.notifications.GRANTED
import org.w3c.notifications.NotificationOptions
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import org.w3c.notifications.Notification as JsNotification
import org.w3c.notifications.NotificationPermission as JsNotificationPermission

private class WebNotificationHandler(
    override val name: String,
    override val id: String,
    override val priority: NotificationPriority,
) : NotificationHandler {
    override val platform: NotificationPlatform = NotificationPlatform.WEB
    override val hasPermissions: Boolean = false // TODO: Implement hasPermissions flag

    override val capabilities: NotificationCapabilities = NotificationCapabilities(
        isTitleSupported = true,
        isDescriptionSupported = true,
        isIconSupported = true,
        isActionSupported = true,
        isPopSupported = true,
        isSoundSupported = true,
        isCustomSoundSupported = false,
        isGroupingSupported = false
    )

    override suspend fun requestPermissions() {
        if (JsNotification.permission == JsNotificationPermission.GRANTED) return
        JsNotification.requestPermission().await()
    }

    @OptIn(ExperimentalEncodingApi::class)
    override fun create(notification: Notification): NotificationHandle {
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
            )
        )
    }

    override suspend fun push(notification: Notification): NotificationHandle {
        return create(notification)
    }

    override suspend fun pop(handle: NotificationHandle) {
        if (handle !is WebNotificationHandle) throw IllegalArgumentException("Handle is not a web notification handle")
        handle.instance.close()
    }
}

fun NotificationHandler.Companion.create(
    name: String,
    id: String = name,
    priority: NotificationPriority = NotificationPriority.DEFAULT,
): NotificationHandler = WebNotificationHandler(name, id, priority)
