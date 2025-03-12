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

@file:OptIn(ExperimentalForeignApi::class)

package de.connect2x.sysnotify

import co.touchlab.stately.collections.SharedHashMap
import co.touchlab.stately.collections.SharedLinkedList
import kotlinx.atomicfu.atomic
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocPointerTo
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.yield
import libnotify.GError
import libnotify.GdkColorspace
import libnotify.NotifyNotification
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

internal class LinuxNotificationHandler(
    override val name: String,
    override val id: String,
    override val priority: NotificationPriority,
    private val closeCallback: () -> Unit = {}
) : NotificationHandler {
    companion object {
        private const val DEFAULT_ICON: String = "dialog-information"
        private const val DEFAULT_SOUND: String = "/usr/share/sounds/freedesktop/stereo/message.oga"
    }

    override val platform: NotificationPlatform = NotificationPlatform.LINUX
    override val hasPermissions: Boolean = true // Desktop on Linux always has permission to send notifications

    override val capabilities: NotificationCapabilities = NotificationCapabilities(
        isTitleSupported = true,
        isDescriptionSupported = true,
        isIconSupported = true,
        isActionSupported = false,
        isPopSupported = true,
        isSoundSupported = true,
        isCustomSoundSupported = true,
        isGroupingSupported = false
    )

    private val coroutineScope: CoroutineScope =
        CoroutineScope(Dispatchers.Default + SupervisorJob() + CoroutineExceptionHandler { _, throwable ->
            println("NotificationHandler coroutine could not complete: $throwable")
        })

    private val pendingNotifications: SharedLinkedList<Triple<String, Boolean, Notification>> = SharedLinkedList()
    private val notifications: SharedHashMap<String, LinuxNotificationHandle> = SharedHashMap()
    private var lastPushTime: TimeSource.Monotonic.ValueTimeMark? by atomic(null)
    private val dequeueSemaphore: Semaphore = Semaphore(1)
    private var lastNotification: Notification? by atomic(null)

    private var isDequeueJobRunning: Boolean by atomic(true)
    private val dequeueJob: Job = coroutineScope.launch {
        while (isDequeueJobRunning) {
            while (pendingNotifications.isNotEmpty()) {
                dequeueSemaphore.acquire()
                val (tag, isSameContent, notification) = pendingNotifications.removeFirst()
                val handle = create(tag, notification)
                handle.check<LinuxNotificationHandle>()
                notifications[tag] = handle
                dequeueSemaphore.release()
                // Minimum required DBus timeouts to prevent dropping notifications
                delay(if (isSameContent) 1.seconds else 500.milliseconds)
            }
            yield()
        }
    }

    init {
        check(LibNotify.notify_init(name) != 0) { "Could not initialize libnotify" }
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun setIcon(nativeNotification: CPointer<NotifyNotification>, notification: Notification) {
        notification.icon?.let { icon ->
            val width = icon.width
            val height = icon.height
            if (width <= 0 || height <= 0) return // Return if our size is invalid
            icon.data.usePinned { pinnedArray ->
                val data = LibGlib.g_bytes_new_static(pinnedArray.addressOf(0), icon.data.size.toULong())
                val buffer = LibGdkPixbuf.gdk_pixbuf_new_from_bytes(
                    data, GdkColorspace.GDK_COLORSPACE_RGB, 1, 8, width, height, width shl 2
                )
                LibNotify.notify_notification_set_icon_from_pixbuf(nativeNotification, buffer)
                LibGdkPixbuf.gdk_pixbuf_unref(buffer)
                LibGlib.g_bytes_unref(data)
            }
        }
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun show(notification: CPointer<NotifyNotification>): Boolean {
        memScoped {
            val errorAddress = allocPointerTo<GError>()
            if (LibNotify.notify_notification_show(notification, errorAddress.ptr) == 0) {
                errorAddress.value?.freeAndThrow()
                return false // Return if we fall through, which doesn't happen, but no _Noreturn..
            }
        }
        return true
    }

    @Suppress("NOTHING_TO_INLINE")
    private suspend inline fun create(tag: String, notification: Notification): NotificationHandle {
        val notificationAddress = LibNotify.notify_notification_new(
            summary = notification.title,
            body = notification.description,
            icon = DEFAULT_ICON
        ) ?: return NotificationHandle.INVALID

        LibNotify.notify_notification_set_app_name(notificationAddress, name)
        LibNotify.notify_notification_set_timeout(notificationAddress, 5000) // 5 seconds by default
        LibNotify.notify_notification_set_urgency(notificationAddress, notification.priority.urgency)

        if (notification.playSound) {
            LibNotify.notify_notification_set_hint_string(
                notificationAddress, "sound-file", notification.soundPath ?: DEFAULT_SOUND
            )
        }
        setIcon(notificationAddress, notification)
        return if (show(notificationAddress)) LinuxNotificationHandle(tag).apply {
            setAddress(notificationAddress)
        }
        else NotificationHandle.INVALID
    }

    override suspend fun push(notification: Notification, tag: String): NotificationHandle {
        // If this notification is already pending or active, return early
        if (tag in notifications || pendingNotifications.any { it.first == tag }) return NotificationHandle.INVALID
        val lastPushTime = this.lastPushTime
        if (lastPushTime != null) {
            val isSameContent = lastNotification == notification
            val currentTime = TimeSource.Monotonic.markNow()
            val elapsedTime = currentTime.minus(lastPushTime)
            if (elapsedTime >= 500.milliseconds) {
                pendingNotifications += Triple(tag, isSameContent, notification)
                this.lastPushTime = TimeSource.Monotonic.markNow()
                lastNotification = notification
                return LinuxNotificationHandle(tag) // Early return so we don't block
            }
        }
        // Handle immediate notification creation
        val handle = create(tag, notification)
        handle.check<LinuxNotificationHandle>()
        notifications[tag] = handle
        this.lastPushTime = TimeSource.Monotonic.markNow()
        lastNotification = notification
        return handle
    }

    override suspend fun update(handle: NotificationHandle, notification: Notification): NotificationHandle =
        dequeueSemaphore.withPermit {
            handle.check<LinuxNotificationHandle>()
            val tag = handle.tag
            // If the notification is already active, just update it immediately
            if (tag in notifications) {
                val notificationAddress =
                    requireNotNull(handle.getAddress()) { "Handle does not contain a valid address" }
                LibNotify.notify_notification_update(
                    notification = notificationAddress,
                    summary = notification.title,
                    body = notification.description,
                    icon = DEFAULT_ICON
                )
                setIcon(notificationAddress, notification)
                return@withPermit if (show(notificationAddress)) handle
                else NotificationHandle.INVALID
            }
            // Otherwise update the pending notification associated with the given tag
            val outdatedEntry =
                pendingNotifications.find { it.first == tag } ?: return@withPermit NotificationHandle.INVALID
            val outdatedIndex = pendingNotifications.indexOf(outdatedEntry)
            if (outdatedIndex == -1) return@withPermit NotificationHandle.INVALID
            val isSameContent = lastNotification == notification
            pendingNotifications[outdatedIndex] = Triple(tag, isSameContent, notification)
            lastNotification = notification
            handle
        }

    override suspend fun pop(handle: NotificationHandle) = dequeueSemaphore.withPermit {
        handle.check<LinuxNotificationHandle>()
        val tag = handle.tag
        // If the notification is already active, close it
        if (tag in notifications) {
            val address = handle.getAddress()
            memScoped {
                val errorAddress = allocPointerTo<GError>()
                if (LibNotify.notify_notification_close(address, errorAddress.ptr) == 0) {
                    errorAddress.value?.freeAndThrow()
                    return
                }
            }
            LibGlib.g_object_unref(address?.reinterpret())
        }
        // Otherwise dequeue the pending notification early
        val outdatedEntry = pendingNotifications.find { it.first == tag } ?: return
        pendingNotifications -= outdatedEntry
    }

    override suspend fun clearAll() = dequeueSemaphore.withPermit {
        pendingNotifications.clear() // Make sure all pending notifications are cleared first
        for ((_, notification) in notifications) {
            memScoped {
                val errorAddress = allocPointerTo<GError>()
                if (LibNotify.notify_notification_close(notification.getAddress(), errorAddress.ptr) == 0) {
                    errorAddress.value?.freeAndThrow()
                }
            }
        }
        notifications.clear()
    }

    override fun close() = runBlocking {
        dequeueSemaphore.withPermit {
            closeCallback()
            isDequeueJobRunning = false
            dequeueJob.join()
            coroutineScope.cancel()
            pendingNotifications.clear()
            notifications.clear()
            LibNotify.notify_uninit()
        }
    }
}

actual fun NotificationHandler(
    name: String,
    id: String,
    priority: NotificationPriority
): NotificationHandler = LinuxNotificationHandler(name, id, priority)
