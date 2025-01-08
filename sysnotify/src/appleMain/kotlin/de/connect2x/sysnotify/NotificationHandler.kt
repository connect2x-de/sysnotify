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

import kotlinx.atomicfu.atomic
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.experimental.ExperimentalNativeApi
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalNativeApi::class)
internal class AppleNotificationHandler(
    override val name: String,
    override val id: String,
    override val priority: NotificationPriority,
    private val activationCallback: (Notification) -> Unit,
) : NotificationHandler {
    override val platform: NotificationPlatform = NotificationPlatform.APPLE
    override var hasPermissions: Boolean by atomic(false)

    override val capabilities: NotificationCapabilities = NotificationCapabilities(
        isTitleSupported = true,
        isDescriptionSupported = true,
        isIconSupported = false,
        isActionSupported = false,
        isPopSupported = true,
        isSoundSupported = true,
        isCustomSoundSupported = false,
    )

    private val notificationCenter: UNUserNotificationCenter? = try {
        UNUserNotificationCenter.currentNotificationCenter()
    } catch (error: Throwable) {/* This is a soft fail, so we swallow the exception */
        // TODO: add a log warning once we support some logging API
        null
    }

    override suspend fun requestPermissions() {
        if (Platform.isDebugBinary || hasPermissions) return
        UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
            UNAuthorizationOptionSound or UNAuthorizationOptionBadge or UNAuthorizationOptionAlert
        ) { granted, error ->
            if (granted) hasPermissions = true
            error?.localizedDescription?.let {
                throw IllegalStateException("Could not request permission: $it")
            }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    override fun create(notification: Notification): NotificationHandle {
        return if (hasPermissions) AppleNotificationHandle()
        else InvalidNotificationHandle
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun push(notification: Notification): NotificationHandle {
        val handle = AppleNotificationHandle()
        if (Platform.isDebugBinary || !hasPermissions) return handle

        val content = UNMutableNotificationContent().apply {
            setTitle(notification.title ?: handle.uuid.toString())
            notification.description?.let(::setBody)
            if (notification.playSound) {
                setSound(notification.soundPath?.let {
                    UNNotificationSound.soundNamed(it)
                } ?: UNNotificationSound.defaultSound)
            }
        }
        notificationCenter?.addNotificationRequest(
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
    @OptIn(ExperimentalUuidApi::class)
    override suspend fun pop(handle: NotificationHandle) {
        require(handle is AppleNotificationHandle) { "Handle is not an Apple notification handle" }
        if (Platform.isDebugBinary || !hasPermissions) return

        val identifier = handle.uuid.toString()
        var found = false
        // First try to remove it from the pending notifications
        notificationCenter?.getPendingNotificationRequestsWithCompletionHandler {
            for (request in it as List<UNNotificationRequest>) {
                if (request.identifier != identifier) continue
                notificationCenter.removePendingNotificationRequestsWithIdentifiers(listOf(identifier))
                found = true
                break
            }
        }
        if (found) return
        // Otherwise it was already delivered
        notificationCenter?.getDeliveredNotificationsWithCompletionHandler {
            for (notification in it as List<UNNotification>) {
                if (notification.request.identifier != identifier) continue
                notificationCenter.removeDeliveredNotificationsWithIdentifiers(listOf(identifier))
                break
            }
        }
    }
}

fun NotificationHandler.Companion.create(
    name: String,
    id: String = name,
    activationCallback: (Notification) -> Unit = {},
    priority: NotificationPriority = NotificationPriority.DEFAULT,
): NotificationHandler = AppleNotificationHandler(name, id, priority, activationCallback)
