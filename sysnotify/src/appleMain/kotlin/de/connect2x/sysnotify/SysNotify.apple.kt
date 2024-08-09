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

import com.benasher44.uuid.Uuid
import com.benasher44.uuid.uuid4
import de.connect2x.sysnotify.NoopNotificationHandler.platform
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter

internal data class AppleNotificationHandle(
    val uuid: Uuid = uuid4()
) : NotificationHandle

internal class AppleNotificationHandler(
    override val name: String,
    override val id: String,
    private val activationCallback: (Notification) -> Unit
) : NotificationHandler {
    override val platform: NotificationPlatform = NotificationPlatform.APPLE
    override val hasPermissions: Boolean = false // TODO: Implement hasPermissions flag

    override val capabilities: NotificationCapabilities = NotificationCapabilities(
        isTitleSupported = true,
        isDescriptionSupported = true,
        isIconSupported = false,
        isActionSupported = false,
        isPopSupported = true
    )

    override suspend fun requestPermissions() {
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

    override suspend fun push(notification: Notification): NotificationHandle {
        val handle = AppleNotificationHandle()
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
    override suspend fun pop(handle: NotificationHandle) {
        require(handle is AppleNotificationHandle) { "Handle is not an Apple notification handle" }
        val center = UNUserNotificationCenter.currentNotificationCenter()
        val identifier = handle.uuid.toString()
        var found = false
        // First try to remove it from the pending notifications
        center.getPendingNotificationRequestsWithCompletionHandler {
            for (request in it as List<UNNotificationRequest>) {
                if (request.identifier != identifier) continue
                center.removePendingNotificationRequestsWithIdentifiers(listOf(identifier))
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
                break
            }
        }
    }
}

fun NotificationHandler.Companion.create(
    name: String,
    id: String = name,
    activationCallback: (Notification) -> Unit = {}
): NotificationHandler = AppleNotificationHandler(name, id, activationCallback)
