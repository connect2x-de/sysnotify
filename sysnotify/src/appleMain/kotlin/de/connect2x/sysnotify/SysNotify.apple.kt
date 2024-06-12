/*
 * Copyright 2024 Karma Krafts & associates
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

package io.karma.sysnotify

import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationRequest

internal class AppleNotificationHandler(override val name: String) : NotificationHandler {
    override val platform: NotificationPlatform = NotificationPlatform.APPLE

    override val capabilities: NotificationCapabilities = NotificationCapabilities(
        isTitleSupported = true,
        isDescriptionSupported = true,
        isIconSupported = false,
        isActionSupported = false,
        isPopSupported = true
    )

    override fun requestPermissions() {
        UNUserNotificationCenter.currentNotificationCenter()
            .requestAuthorizationWithOptions(
                UNAuthorizationOptionSound
                        or UNAuthorizationOptionBadge
                        or UNAuthorizationOptionAlert
            ) { _, error ->
                error?.localizedDescription?.let {
                    throw IllegalStateException("Could not request permission: $it")
                }
            }
    }

    override fun push(notification: Notification): NotificationHandle {
        val handle = NotificationHandle()
        val content = UNMutableNotificationContent().apply {
            setTitle(notification.title ?: handle.uuid.toString())
            notification.description?.let(::setBody)
        }
        UNUserNotificationCenter.currentNotificationCenter()
            .addNotificationRequest(
                UNNotificationRequest.requestWithIdentifier(
                    handle.uuid.toString(), content, null
                )
            ) { error ->
                error?.localizedDescription?.let {
                    throw IllegalStateException("Could not request notification: $it")
                }
            }
        return handle
    }

    @Suppress("UNCHECKED_CAST")
    override fun pop(handle: NotificationHandle) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        val identifier = handle.uuid.toString()
        var found = false
        // First try to remove it from the pending notifications
        center.getPendingNotificationRequestsWithCompletionHandler {
            for (request in it as List<UNNotificationRequest>) {
                if (request.identifier != identifier) continue
                center.removePendingNotificationRequestsWithIdentifiers(listOf(identifier))
                handle.isValid = false
                found = true
                break
            }
        }
        if (found) return
        // Otherwise it was already delivered
        center.getDeliveredNotificationsWithCompletionHandler {
            for (notification in it as List<UNNotification>) {
                if (notification.request.identifier != identifier) continue
                center.removeDeliveredNotificationsWithIdentifiers(listOf(identifier))
                handle.isValid = false
                break
            }
        }
    }
}

actual fun createNotificationHandler(
    name: String,
    context: Any?
): NotificationHandler = AppleNotificationHandler(name)