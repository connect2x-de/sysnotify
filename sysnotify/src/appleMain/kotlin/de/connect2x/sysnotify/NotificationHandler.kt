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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.Foundation.NSBundle
import platform.Foundation.NSError
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationAction
import platform.UserNotifications.UNNotificationCategory
import platform.UserNotifications.UNNotificationCategoryOptionNone
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.resume
import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class, ExperimentalAtomicApi::class)
internal class AppleNotificationHandler(
    override val appId: String,
    override val name: String,
    override val id: String,
    override val priority: NotificationPriority,
    private val closeCallback: () -> Unit = {},
) : NotificationHandler {
    @OptIn(ExperimentalForeignApi::class)

    override val platform: NotificationPlatform = NotificationPlatform.APPLE

    override val hasPermissions: Boolean
        get() = runBlocking { notificationCenter.hasPermissions() }

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

    private val notificationCenter: UNUserNotificationCenter = UNUserNotificationCenter.currentNotificationCenter()

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
            notificationCenter.setNotificationCategories(notificationCategories.values.toSet())
        }
    }

    override fun getHandle(tag: String): NotificationHandle {
        require(tag.isNotEmpty()) { "Handle tag may not be empty" }
        return AppleNotificationHandle(tag)
    }

    override suspend fun requestPermissions(callback: PermissionCallback) {
        if (notificationCenter.hasPermissions()) return
        val (granted, error) = notificationCenter.requestDefaultAuthorization()

        error?.localizedDescription?.let {
            println("Could not request permission: $it")
        }

        callback(granted)
    }

    override suspend fun push(notification: Notification, tag: String): NotificationHandle {
        if (!notificationCenter.hasPermissions()) return NotificationHandle.INVALID
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

        notificationCenter.addNotificationRequest(
            UNNotificationRequest.requestWithIdentifier(
                identifier = handle.tag,
                content = content,
                trigger = null
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
        if (!notificationCenter.hasPermissions()) return
        handle.check<AppleNotificationHandle>()

        val identifier = handle.tag

        // First try to remove it from the pending notifications
        notificationCenter.getPendingNotificationRequestsWithCompletionHandler { requests ->
            for (request in requests as List<UNNotificationRequest>) {
                if (request.identifier != identifier) continue
                notificationCenter.removePendingNotificationRequestsWithIdentifiers(listOf(identifier))
                return@getPendingNotificationRequestsWithCompletionHandler
            }

            // If the for loop doesn't return, we should enumerate the delivered notifications for deletion.
            notificationCenter.getDeliveredNotificationsWithCompletionHandler { notifications ->
                for (notification in notifications as List<UNNotification>) {
                    if (notification.request.identifier != identifier) continue
                    notificationCenter.removeDeliveredNotificationsWithIdentifiers(listOf(identifier))
                    return@getDeliveredNotificationsWithCompletionHandler
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun update(handle: NotificationHandle, notification: Notification): NotificationHandle {
        if (!notificationCenter.hasPermissions() || !handle.isValid) return NotificationHandle.INVALID

        // This is done like this because, following to the Apple documentation, the API can choose the run the handler
        // closure in a separate background thread.
        val found = suspendCancellableCoroutine { continuation ->
            notificationCenter.getPendingNotificationRequestsWithCompletionHandler { requests ->
                for (request in requests as List<UNNotificationRequest>) {
                    if (request.identifier != handle.tag) continue
                    continuation.resume(true) { _, _, _ -> }
                    return@getPendingNotificationRequestsWithCompletionHandler
                }

                notificationCenter.getDeliveredNotificationsWithCompletionHandler { notifications ->
                    val foundDelivered = (notifications as List<UNNotification>)
                        .any { it.request.identifier == handle.tag }
                    continuation.resume(foundDelivered) { _, _, _ -> }
                }
            }


        }

        if (!found) {
            return NotificationHandle.INVALID
        }

        return push(notification, tag = handle.tag)
    }

    override suspend fun clearAll() {
        if (!notificationCenter.hasPermissions()) return
        notificationCenter.removeAllPendingNotificationRequests()
        notificationCenter.removeAllDeliveredNotifications()
    }

    override suspend fun unregister() {
        if (!notificationCenter.hasPermissions()) return
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
    return createAppleOrNoopNotificationHandler(
        name = name,
        id = id,
        priority = priority,
        closeCallback = {},
        appId = appId,
    )
}

internal fun createAppleOrNoopNotificationHandler(
    name: String,
    id: String,
    priority: NotificationPriority,
    closeCallback: () -> Unit,
    appId: String
): NotificationHandler {
    if (NSBundle.mainBundle.infoDictionary?.get("CFBundlePackageType") != "APPL") {
        println("WARNING: Not running as Application (CFBundlePackageType != APPL)")
        println("WARNING: For notification on apple platforms running as application is required")
        println("WARNING: Registering NoopNotificationHandler instead for development")

        return NoopNotificationHandler
    }

    return AppleNotificationHandler(appId, name, id, priority, closeCallback)
}

private suspend fun UNUserNotificationCenter.requestDefaultAuthorization(): Pair<Boolean, NSError?> =
    suspendCancellableCoroutine { continuation ->
        requestAuthorizationWithOptions(
            UNAuthorizationOptionSound or UNAuthorizationOptionBadge or UNAuthorizationOptionAlert
        ) { granted, error -> continuation.resume(granted to error) }
    }

private suspend fun UNUserNotificationCenter.hasPermissions(): Boolean = suspendCancellableCoroutine { continuation ->
    getNotificationSettingsWithCompletionHandler { notificationSettings ->
        val hasPermission = when (notificationSettings?.authorizationStatus) {
            UNAuthorizationStatusAuthorized, UNAuthorizationStatusEphemeral, UNAuthorizationStatusProvisional -> true
            else -> false
        }
        continuation.resume(hasPermission)
    }
}
