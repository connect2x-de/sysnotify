/*
 * Copyright 2026 connect2x GmbH
 *
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

import de.connect2x.sysnotify.AppleNotificationHandler.Companion.decrementGlobalCounter
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.Foundation.NSLock
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
import platform.UserNotifications.UNNotificationDefaultActionIdentifier
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.decrementAndFetch
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.coroutines.resume
import kotlin.experimental.ExperimentalNativeApi

internal const val USERINFO_CALLBACK_DATA: String = "sysnotify_callback_data"
internal const val USERINFO_SYSNOTIFY_MARKER: String = "__sysnotify__"

private val notificationDelegate: DefaultNotificationDelegate = DefaultNotificationDelegate().apply {
    addActionHandler { _, _, _, _ ->
        decrementGlobalCounter()
    }
}

internal expect fun initializePlatform()

@OptIn(ExperimentalNativeApi::class)
internal class AppleNotificationHandler(
    override val appId: String,
    override val name: String,
    override val id: String,
    override val priority: NotificationPriority,
    override val contributesToCounter: Boolean,
    private val closeCallback: () -> Unit = {}
) : NotificationHandler {
    companion object {
        private var isDelegateInstalled: Boolean = false
        private val isDelegateInstalledLock: NSLock = NSLock()
        private val globalCount: AtomicInt = AtomicInt(0)

        fun incrementGlobalCounter() {
            UNUserNotificationCenter.currentNotificationCenter()
                .setBadgeCount(globalCount.incrementAndFetch().toLong(), null)
        }

        fun decrementGlobalCounter() {
            UNUserNotificationCenter.currentNotificationCenter()
                .setBadgeCount(globalCount.decrementAndFetch().coerceAtLeast(0).toLong(), null)
        }

        fun setGlobalCounter(count: Int) {
            globalCount.store(count)
            UNUserNotificationCenter.currentNotificationCenter().setBadgeCount(count.toLong(), null)
        }

        fun installDelegates() = isDelegateInstalledLock.withLock {
            if (isDelegateInstalled) return@withLock
            UNUserNotificationCenter.currentNotificationCenter().setDelegate(notificationDelegate)
            initializePlatform()
            isDelegateInstalled = true
        }
    }

    override val platform: NotificationPlatform = NotificationPlatform.APPLE

    override val hasPermissions: Boolean
        get() = runBlocking { UNUserNotificationCenter.currentNotificationCenter().hasPermissions() }

    override val capabilities: NotificationCapabilities = NotificationCapabilities(
        isTitleSupported = true,
        isDescriptionSupported = true,
        isIconSupported = false,
        isActionSupported = false,
        isPopSupported = true,
        isSoundSupported = true,
        isCustomSoundSupported = true,
        isGroupingSupported = true,
        isStatusIconSupported = false,
        isCounterSupported = true
    )

    private val notificationCategories: HashMap<String, UNNotificationCategory> = HashMap()
    private val notificationCategoriesLock: Mutex = Mutex()

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
            UNUserNotificationCenter.currentNotificationCenter()
                .setNotificationCategories(notificationCategories.values.toSet())
        }
    }

    override fun getHandle(tag: String): NotificationHandle {
        require(tag.isNotEmpty()) { "Handle tag may not be empty" }
        return AppleNotificationHandle(tag)
    }

    override suspend fun requestPermissions(callback: PermissionCallback) {
        val notificationCenter = UNUserNotificationCenter.currentNotificationCenter()
        if (notificationCenter.hasPermissions()) return
        // @formatter:off
        notificationCenter.requestAuthorizationWithOptions(UNAuthorizationOptionAlert
                or UNAuthorizationOptionSound
                or UNAuthorizationOptionBadge) { granted, _ ->
            callback(granted)
        }
        // @formatter:on
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun notificationExists(tag: String): Boolean {
        val notificationCenter = UNUserNotificationCenter.currentNotificationCenter()
        return suspendCancellableCoroutine { continuation ->
            notificationCenter.getPendingNotificationRequestsWithCompletionHandler { requests ->
                for (request in requests as List<UNNotificationRequest>) {
                    if (request.identifier != tag) continue
                    continuation.resume(true)
                    return@getPendingNotificationRequestsWithCompletionHandler
                }
                notificationCenter.getDeliveredNotificationsWithCompletionHandler { notifications ->
                    val foundDelivered = (notifications as List<UNNotification>)
                        .any { it.request.identifier == tag }
                    continuation.resume(foundDelivered)
                }
            }
        }
    }

    private suspend fun createNotificationContent(
        handle: AppleNotificationHandle,
        notification: Notification
    ): UNMutableNotificationContent {
        return UNMutableNotificationContent().apply {
            setTitle(notification.title ?: handle.tag)
            setRelevanceScore(notification.priority.relevanceScore)
            notification.description?.let(::setBody)
            if (notification.playSound) {
                setSound(notification.soundPath?.let { soundPath ->
                    UNNotificationSound.soundNamed(soundPath)
                } ?: UNNotificationSound.defaultSound)
            }
            (notification.group ?: "default").let { group ->
                ensureCategoryExists(group)
                setCategoryIdentifier(group)
            }
            setUserInfo(buildMap {
                notification.callbackData?.let { data ->
                    this[USERINFO_CALLBACK_DATA] = data
                    this[USERINFO_SYSNOTIFY_MARKER] = true // Just store a boolean flag as the marker value
                }
            })
        }
    }

    override suspend fun push(notification: Notification, tag: String): NotificationHandle {
        val notificationCenter = UNUserNotificationCenter.currentNotificationCenter()
        if (!notificationCenter.hasPermissions()) return NotificationHandle.INVALID
        val handle = AppleNotificationHandle(tag)
        if (notificationExists(tag)) return handle

        notificationCenter.addNotificationRequest(
            UNNotificationRequest.requestWithIdentifier(
                identifier = tag,
                content = createNotificationContent(handle, notification),
                trigger = null
            )
        ) { error ->
            error?.localizedDescription?.let {
                throw IllegalStateException("Could not request notification: $it")
            }
            if (!contributesToCounter) return@addNotificationRequest
            incrementGlobalCounter()
        }

        return handle
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun pop(handle: NotificationHandle) {
        val notificationCenter = UNUserNotificationCenter.currentNotificationCenter()
        if (!notificationCenter.hasPermissions() || handle !is AppleNotificationHandle) return
        val identifier = handle.tag
        // First try to remove it from the pending notifications
        notificationCenter.getPendingNotificationRequestsWithCompletionHandler { requests ->
            for (request in requests as List<UNNotificationRequest>) {
                if (request.identifier != identifier) continue
                notificationCenter.removePendingNotificationRequestsWithIdentifiers(listOf(identifier))
                if (!contributesToCounter) return@getPendingNotificationRequestsWithCompletionHandler
                decrementGlobalCounter()
                return@getPendingNotificationRequestsWithCompletionHandler
            }
            // If the for loop doesn't return, we should enumerate the delivered notifications for deletion.
            notificationCenter.getDeliveredNotificationsWithCompletionHandler { notifications ->
                for (notification in notifications as List<UNNotification>) {
                    if (notification.request.identifier != identifier) continue
                    notificationCenter.removeDeliveredNotificationsWithIdentifiers(listOf(identifier))
                    if (!contributesToCounter) return@getDeliveredNotificationsWithCompletionHandler
                    decrementGlobalCounter()
                    return@getDeliveredNotificationsWithCompletionHandler
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun update(handle: NotificationHandle, notification: Notification): NotificationHandle {
        val notificationCenter = UNUserNotificationCenter.currentNotificationCenter()
        if (handle !is AppleNotificationHandle || !notificationCenter.hasPermissions() || !handle.isValid) return NotificationHandle.INVALID
        if (!notificationExists(handle.tag)) return NotificationHandle.INVALID

        notificationCenter.addNotificationRequest(
            UNNotificationRequest.requestWithIdentifier(
                identifier = handle.tag,
                content = createNotificationContent(handle, notification),
                trigger = null
            )
        ) { error ->
            error?.localizedDescription?.let {
                throw IllegalStateException("Could not request notification: $it")
            }
        }

        return handle
    }

    override suspend fun clearAll() {
        val notificationCenter = UNUserNotificationCenter.currentNotificationCenter()
        if (!notificationCenter.hasPermissions()) return
        notificationCenter.removeAllPendingNotificationRequests()
        notificationCenter.removeAllDeliveredNotifications()

        if (!contributesToCounter) return
        setGlobalCounter(0)
    }

    override suspend fun unregister() {
        val notificationCenter = UNUserNotificationCenter.currentNotificationCenter()
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
    if (this !is AppleNotificationHandler) {
        println("!!! withActivationHandler call ignored because NotificationHandler is not AppleNotificationHandler !!!")
        return this
    }
    notificationDelegate.addActionHandler { _, action, _, notification ->
        if (action != UNNotificationDefaultActionIdentifier) return@addActionHandler
        handler(notification)
    }
    return this
}

@OptIn(ExperimentalForeignApi::class)
actual fun NotificationHandler( // @formatter:off
    name: String,
    id: String,
    priority: NotificationPriority,
    appId: String,
    contributesToCounter: Boolean
): NotificationHandler { // @formatter:on
    return createAppleOrNoopNotificationHandler(
        name = name,
        id = id,
        priority = priority,
        closeCallback = {},
        appId = appId,
        contributesToCounter = contributesToCounter
    )
}

internal fun createAppleOrNoopNotificationHandler(
    name: String,
    id: String,
    priority: NotificationPriority,
    closeCallback: () -> Unit,
    appId: String,
    contributesToCounter: Boolean
): NotificationHandler {
    if (!isBundledApplication) {
        println("WARNING: Not running as Application (CFBundlePackageType != APPL)")
        println("WARNING: For notification on apple platforms running as application is required")
        println("WARNING: Registering NoopNotificationHandler instead for development")
        return NoopNotificationHandler
    }

    return AppleNotificationHandler(
        appId = appId,
        name = name,
        id = id,
        priority = priority,
        contributesToCounter = contributesToCounter,
        closeCallback = closeCallback
    )
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
