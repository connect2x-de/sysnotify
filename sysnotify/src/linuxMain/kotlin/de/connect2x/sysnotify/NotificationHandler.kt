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
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocPointerTo
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.staticCFunction
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
import libnotify.gpointer
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
        private const val HINT_SOUND_FILE: String = "sound-file"
        private const val DEFAULT_ICON: String = "dialog-information"
        private const val DEFAULT_SOUND: String = "/usr/share/sounds/freedesktop/stereo/message.oga"
        private const val ACTION_OPEN: String = "open"
        private const val SIGNAL_CLOSED: String = "closed"

        private fun onNotificationClosed(
            state: NotificationActivationState
        ) {
            state.handler.apply {
                pendingNotifications.removeAll { it.first == state.tag }
                notifications -= state.tag
                activationStates -= state.tag
            }
        }

        private fun onNotificationActivated(
            state: NotificationActivationState
        ) {
            state.activated = true // Notify close signal handler that we handles this notification already
            state.handler.activationHandler?.invoke(state.notification)
        }
    }

    override val platform: NotificationPlatform = NotificationPlatform.LINUX
    override val hasPermissions: Boolean = true // Desktop on Linux always has permission to send notifications

    override val capabilities: NotificationCapabilities = NotificationCapabilities(
        isTitleSupported = true,
        isDescriptionSupported = true,
        isIconSupported = true,
        isActionSupported = true,
        isPopSupported = true,
        isSoundSupported = true,
        isCustomSoundSupported = true,
        isGroupingSupported = false
    )

    init {
        check(LibNotify.notify_init(name) != 0) { "Could not initialize libnotify" }
    }

    private val coroutineScope: CoroutineScope =
        CoroutineScope(Dispatchers.Default + SupervisorJob() + CoroutineExceptionHandler { _, throwable ->
            // TODO: implement proper logging
            println("NotificationHandler coroutine could not complete: $throwable")
        })

    private val pendingNotifications: SharedLinkedList<Triple<String, Boolean, Notification>> = SharedLinkedList()
    private val notifications: SharedHashMap<String, LinuxNotificationHandle> = SharedHashMap()
    private var lastPushTime: TimeSource.Monotonic.ValueTimeMark? by atomic(null)
    private val dequeueSemaphore: Semaphore = Semaphore(1)
    private var lastNotification: Notification? by atomic(null)
    private var isRunning: Boolean by atomic(true)

    // Flushes notifications from the internal queue to the system periodically
    private val dequeueJob: Job = coroutineScope.launch {
        while (isRunning) {
            while(pendingNotifications.isEmpty()) yield()
            dequeueSemaphore.acquire()
            val (tag, isSameContent, notification) = pendingNotifications.removeFirst()
            // Minimum required DBus timeouts to prevent dropping notifications
            val delay = if (isSameContent) 5.seconds else 1.seconds
            delay(delay)
            val handle = create(tag, notification)
            handle.check<LinuxNotificationHandle>()
            notifications[tag] = handle
            dequeueSemaphore.release()
        }
    }

    // An out-of-band event pump so actions and signals work for our notifications
    private val eventLoopJob: Job = coroutineScope.launch {
        val eventLoop = requireNotNull(LibGLib.g_main_loop_new(null, 0)) {
            "Could not create GMainLoop instance"
        }
        val loopContext = requireNotNull(LibGLib.g_main_loop_get_context(eventLoop)) {
            "Could not obtain GMainContext for event loop"
        }
        while (isRunning) {
            // Empty scope so yield() doesn't cause a reformat
            while (LibGLib.g_main_context_iteration(loopContext, 0) == 1) {}
            yield()
        }
        LibGLib.g_main_loop_unref(eventLoop)
    }

    private val activationStates: SharedHashMap<String, StableRef<NotificationActivationState>> = SharedHashMap()
    internal var activationHandler: ActivationHandler? by atomic(null)

    private fun setIcon(nativeNotification: CPointer<NotifyNotification>, notification: Notification) {
        notification.icon?.let { icon ->
            val width = icon.width
            val height = icon.height
            if (width <= 0 || height <= 0) return // Return if our size is invalid
            icon.data.usePinned { pinnedArray ->
                val data = LibGLib.g_bytes_new_static(pinnedArray.addressOf(0), icon.data.size.toULong())
                val buffer = LibGdkPixbuf.gdk_pixbuf_new_from_bytes(
                    data, GdkColorspace.GDK_COLORSPACE_RGB, 1, 8, width, height, width shl 2
                )
                LibNotify.notify_notification_set_icon_from_pixbuf(nativeNotification, buffer)
                LibGdkPixbuf.gdk_pixbuf_unref(buffer)
                LibGLib.g_bytes_unref(data)
            }
        }
    }

    private fun show(notification: CPointer<NotifyNotification>): Boolean {
        memScoped {
            val errorAddress = allocPointerTo<GError>()
            if (LibNotify.notify_notification_show(notification, errorAddress.ptr) == 0) {
                errorAddress.value?.freeAndThrow()
                return false // Return if we fall through, which doesn't happen, but no _Noreturn..
            }
        }
        return true
    }

    private suspend fun create(tag: String, notification: Notification): NotificationHandle {
        val notificationAddress = LibNotify.notify_notification_new(
            summary = notification.title,
            body = notification.description,
            icon = DEFAULT_ICON
        ) ?: return NotificationHandle.INVALID

        LibNotify.notify_notification_set_app_name(notificationAddress, name)
        LibNotify.notify_notification_set_timeout(notificationAddress, 5000) // 5 seconds by default
        LibNotify.notify_notification_set_urgency(notificationAddress, notification.priority.urgency)

        if (notification.playSound) {
            val soundPath = notification.soundPath ?: DEFAULT_SOUND
            LibNotify.notify_notification_set_hint_string(notificationAddress, HINT_SOUND_FILE, soundPath)
        }
        setIcon(notificationAddress, notification)

        // We create a new state for this notification and create a stable reference to it
        val stateRef = StableRef.create(NotificationActivationState(this, tag, notification))
        activationStates[tag] = stateRef

        // Register activation callback
        LibNotify.notify_notification_add_action(
            notification = notificationAddress,
            action = ACTION_OPEN,
            label = "Open",
            callback = staticCFunction { _, _, userData ->
                val stateRef = userData?.asStableRef<NotificationActivationState>() ?: return@staticCFunction
                LinuxNotificationHandler.onNotificationActivated(stateRef.get())
            },
            userData = stateRef.asCPointer(),
            freeFunc = null
        )

        // Register close callback using a GObject signal
        LibGObject.g_signal_connect_data(
            instance = notificationAddress.reinterpret(),
            detailedSignal = SIGNAL_CLOSED,
            handler = staticCFunction<CPointer<NotifyNotification>, gpointer?, Unit> { _, userData ->
                val stateRef = userData?.asStableRef<NotificationActivationState>() ?: return@staticCFunction
                LinuxNotificationHandler.onNotificationClosed(stateRef.get())
                stateRef.dispose()
            }.reinterpret(),
            data = stateRef.asCPointer(),
            destroyData = null,
            connectFlags = 0U
        )

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
            val elapsedTime = currentTime - lastPushTime
            if (elapsedTime < 1.seconds) {
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
                    return@withPermit
                }
            }
            LibGLib.g_object_unref(address?.reinterpret())
            notifications -= tag
            return@withPermit
        }
        // Otherwise dequeue the pending notification early
        val outdatedEntry = pendingNotifications.find { it.first == tag } ?: return@withPermit
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

    override fun close() {
        runBlocking {
            dequeueSemaphore.withPermit {
                closeCallback()
                isRunning = false
                dequeueJob.join()
                eventLoopJob.join()
                coroutineScope.cancel()
            }
            clearAll()
        }
        LibNotify.notify_uninit()
    }
}

actual fun NotificationHandler.withActivationHandler(handler: ActivationHandler): NotificationHandler {
    check<LinuxNotificationHandler>()
    activationHandler = handler
    return this
}

actual fun NotificationHandler(
    name: String,
    id: String,
    priority: NotificationPriority
): NotificationHandler = LinuxNotificationHandler(name, id, priority)
