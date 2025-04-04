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
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKStringFromUtf8
import kotlinx.coroutines.runBlocking
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationAction
import platform.UserNotifications.UNNotificationCategory
import platform.UserNotifications.UNNotificationCategoryOptionNone
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.experimental.ExperimentalNativeApi
import kotlin.uuid.ExperimentalUuidApi
import platform.posix.getenv

@OptIn(ExperimentalNativeApi::class)
internal class AppleNotificationHandler(
    override val name: String,
    override val id: String,
    override val priority: NotificationPriority,
    private val closeCallback: () -> Unit = {}
) : NotificationHandler {
    @OptIn(ExperimentalForeignApi::class)
    internal val isDebugEnabled: Boolean = getenv("SYSNOTIFY_DEBUG")?.toKStringFromUtf8() == "1"

    override val platform: NotificationPlatform = NotificationPlatform.APPLE
    override var hasPermissions: Boolean by atomic(false)

    override val capabilities: NotificationCapabilities = NotificationCapabilities(
        isTitleSupported = true,
        isDescriptionSupported = true,
        isIconSupported = false,
        isActionSupported = false,
        isPopSupported = true,
        isSoundSupported = true,
        isCustomSoundSupported = true,
        isGroupingSupported = true
    )

    private val notificationCategories: SharedHashMap<String, UNNotificationCategory> = SharedHashMap()

    private val notificationCenter: UNUserNotificationCenter? = if(isDebugEnabled) null
    else UNUserNotificationCenter.currentNotificationCenter()

    private fun ensureCategoryExists(key: String) {
        if (key in notificationCategories) return
        val category = UNNotificationCategory.categoryWithIdentifier(
            identifier = key,
            actions = emptyList<UNNotificationAction>(),
            intentIdentifiers = emptyList<String>(),
            options = UNNotificationCategoryOptionNone
        )
        notificationCategories[key] = category
        // We have to update ALL the categories every time we add a new one
        notificationCenter?.setNotificationCategories(notificationCategories.values.toSet())
    }

    override fun getHandle(tag: String): NotificationHandle {
        require(tag.isNotEmpty()) { "Handle tag may not be empty" }
        return AppleNotificationHandle(tag)
    }

    override suspend fun requestPermissions(callback: PermissionCallback) {
        if (hasPermissions) return
        UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
            UNAuthorizationOptionSound or UNAuthorizationOptionBadge or UNAuthorizationOptionAlert
        ) { granted, error ->
            if (granted) hasPermissions = true
            runBlocking { callback(this@AppleNotificationHandler, granted) }
            error?.localizedDescription?.let {
                throw IllegalStateException("Could not request permission: $it")
            }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun push(notification: Notification, tag: String): NotificationHandle {
        if (!hasPermissions) return NotificationHandle.INVALID
        val handle = AppleNotificationHandle(tag)

        val content = UNMutableNotificationContent().apply {
            setTitle(notification.title ?: handle.tag)
            setRelevanceScore(notification.priority.relevanceScore)
            notification.description?.let(::setBody)
            if (notification.playSound) {
                setSound(notification.soundPath?.let {
                    UNNotificationSound.soundNamed(it)
                } ?: UNNotificationSound.defaultSound)
            }
            notification.group?.let {
                ensureCategoryExists(it)
                setCategoryIdentifier(it)
            }
        }

        notificationCenter?.addNotificationRequest(
            UNNotificationRequest.requestWithIdentifier(
                handle.tag, content, null
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
        if (!hasPermissions) return
        handle.check<AppleNotificationHandle>()

        val identifier = handle.tag
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

    override suspend fun clearAll() {
        if (!hasPermissions) return
        notificationCenter?.removeAllPendingNotificationRequests()
        notificationCenter?.removeAllDeliveredNotifications()
    }

    override fun close() {
        if (!hasPermissions) return
        closeCallback()
        runBlocking { clearAll() }
        notificationCategories.clear()
    }
}

actual fun NotificationHandler.withActivationHandler(handler: ActivationHandler): NotificationHandler {
    // TODO: implement activation handlers for iOS/macOS
    return this
}

@OptIn(ExperimentalForeignApi::class)
actual fun NotificationHandler( // @formatter:off
    name: String,
    id: String,
    priority: NotificationPriority
): NotificationHandler { // @formatter:on
    return AppleNotificationHandler(name, id, priority)
}
