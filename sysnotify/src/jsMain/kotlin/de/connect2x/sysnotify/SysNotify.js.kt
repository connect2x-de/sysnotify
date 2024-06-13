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
import org.w3c.notifications.Notification as JsNotification
import org.w3c.notifications.NotificationPermission as JsNotificationPermission

private class WebNotificationHandle(
    val instance: JsNotification
) : NotificationHandle

private class WebNotificationHandler(
    override val name: String
) : NotificationHandler {
    override val platform: NotificationPlatform = NotificationPlatform.WEB

    override val capabilities: NotificationCapabilities = NotificationCapabilities(
        isTitleSupported = true,
        isDescriptionSupported = true,
        isIconSupported = false, // TODO: implement icon support
        isActionSupported = true,
        isPopSupported = true
    )

    override suspend fun requestPermissions() {
        if (JsNotification.permission == JsNotificationPermission.GRANTED) return
        JsNotification.requestPermission().await()
    }

    override suspend fun push(notification: Notification): NotificationHandle {
        val instance = JsNotification(
            notification.title ?: "Undefined", NotificationOptions(
                body = notification.description
            )
        ).apply {
            notification.action?.let { action ->
                onclick = {
                    it.preventDefault()
                    action()
                }
            }
        }
        return WebNotificationHandle(instance)
    }

    override suspend fun pop(handle: NotificationHandle) {
        if (handle !is WebNotificationHandle) throw IllegalArgumentException("Handle is not a web notification handle")
        handle.instance.close()
    }
}

actual fun createNotificationHandler(
    name: String,
    context: Any?
): NotificationHandler = WebNotificationHandler(name)
