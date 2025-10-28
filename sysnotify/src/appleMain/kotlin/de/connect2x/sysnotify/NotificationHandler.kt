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

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class, ExperimentalAtomicApi::class)
internal class AppleNotificationHandler(
    override val appId: String,
    override val name: String,
    override val id: String,
    override val priority: NotificationPriority,
    private val closeCallback: () -> Unit = {},
    isDebugEnabled: Boolean,
) : NotificationHandler {
    @OptIn(ExperimentalForeignApi::class)

    override val platform: NotificationPlatform = NotificationPlatform.APPLE
    val _hasPermissions: AtomicBoolean = AtomicBoolean(false)

    override val hasPermissions: Boolean
        get() = _hasPermissions.load()

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

    // TODO this does not survive a restart
    private val notificationCategories: HashMap<String, UNNotificationCategory> = HashMap()
    private val notificationCategoriesLock: Mutex = Mutex()

    private val notificationCenter: UNUserNotificationCenter? =
        if (isDebugEnabled) null else UNUserNotificationCenter.currentNotificationCenter()

    private suspend fun ensureCategoryExists(key: String) {
        if (notificationCategoriesLock.withLock { key in notificationCategories }) return
        val category = UNNotificationCategory.categoryWithIdentifier(
            identifier = key,
            actions = emptyList<UNNotificationAction>(),
            intentIdentifiers = emptyList<String>(),
            options = UNNotificationCategoryOptionNone
        )
        notificationCategoriesLock.withLock {
            notificationCategories[key] = category
            // We have to update ALL the categories every time we add a new one
            notificationCenter?.setNotificationCategories(notificationCategories.values.toSet())
        }
    }

    override fun getHandle(tag: String): NotificationHandle {
        require(tag.isNotEmpty()) { "Handle tag may not be empty" }
        return AppleNotificationHandle(tag)
    }

    override suspend fun requestPermissions(callback: PermissionCallback) {
        if (_hasPermissions.load()) return
        val barrier = CompletableDeferred<Unit>()
        notificationCenter?.requestAuthorizationWithOptions(
            UNAuthorizationOptionSound or UNAuthorizationOptionBadge or UNAuthorizationOptionAlert
        ) { granted, error ->
            _hasPermissions.store(granted)
            barrier.complete(Unit)
            error?.localizedDescription?.let {
                println("Could not request permission: $it")
            }
        } ?: return
        coroutineScope {
            launch {
                barrier.await()
                callback(_hasPermissions.load())
            }
        }
    }

    override suspend fun push(notification: Notification, tag: String): NotificationHandle {
        if (!_hasPermissions.load()) return NotificationHandle.INVALID
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
    override suspend fun pop(handle: NotificationHandle) {
        if (!_hasPermissions.load()) return
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
        if (!_hasPermissions.load()) return
        notificationCenter?.removeAllPendingNotificationRequests()
        notificationCenter?.removeAllDeliveredNotifications()
    }

    override suspend fun unregister() {
        if (!_hasPermissions.load()) return
        clearAll()
        notificationCategoriesLock.withLock { notificationCategories.clear() }
        close()
    }

    override fun close() {
        closeCallback()
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
    priority: NotificationPriority,
    isDebugEnabled: Boolean,
    appId: String
): NotificationHandler { // @formatter:on
    return AppleNotificationHandler(appId, name, id, priority, closeCallback = {}, isDebugEnabled)
}
