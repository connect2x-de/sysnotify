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

@file:OptIn(ExperimentalForeignApi::class)

package de.connect2x.sysnotify

import co.touchlab.stately.collections.SharedHashMap
import co.touchlab.stately.collections.SharedLinkedList
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
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import libnotify.GError
import libnotify.GdkColorspace
import libnotify.NotifyNotification
import libnotify.gpointer
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalAtomicApi::class)
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
        private val minimumDelay: Duration = 1.seconds  // The minimum delay between any notification
        private val timeout: Duration = 5.seconds       // The timeout until a notification expires

        private fun onNotificationClosed(
            state: NotificationActivationState
        ) {
            state.handler.apply {
                notifications -= state.tag
                activationStates -= state.tag
            }
        }

        private fun onNotificationActivated(
            state: NotificationActivationState
        ) {
            state.activated = true // Notify close signal handler that we've handled this notification already
            state.handler.activationHandler.load()?.invoke(state.notification)
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

    private val coroutineScope: CoroutineScope = CoroutineScope(
        Dispatchers.Default
                + CoroutineName("sysnotify-default")
                + SupervisorJob()
                + CoroutineExceptionHandler { _, throwable ->
            // TODO: implement proper logging
            println("NotificationHandler coroutine could not complete: $throwable")
        })

    private val activationStates: SharedHashMap<String, StableRef<NotificationActivationState>> = SharedHashMap()
    private val pendingNotifications: SharedLinkedList<Triple<String, Boolean, Notification>> = SharedLinkedList()
    private val notifications: SharedHashMap<String, LinuxNotificationHandle> = SharedHashMap()

    private var shouldDispachNotifications: AtomicBoolean = AtomicBoolean(false)
    private var shouldHandleEvents: AtomicBoolean = AtomicBoolean(true)
    private var lastNotification: AtomicReference<Notification?> = AtomicReference(null)
    internal var activationHandler: AtomicReference<ActivationHandler?> = AtomicReference(null)

    init {
        check(LibNotify.notify_init(name) != 0) { "Could not initialize libnotify" }
    }

    // Flushes notifications from the internal queue to the system periodically
    private val dequeueJob: Job = coroutineScope.launch(start = CoroutineStart.ATOMIC) {
        while (shouldDispachNotifications.load()) {
            while (shouldDispachNotifications.load() && pendingNotifications.isNotEmpty()) {
                val (tag, isSameContent, notification) = pendingNotifications.removeFirst()
                val handle = create(tag, notification)
                handle.check<LinuxNotificationHandle>()
                notifications[tag] = handle
                // Minimum required DBus timeouts to prevent dropping notifications
                val delay = if (isSameContent) timeout else minimumDelay
                // Only delay if the state hasn't changed since then
                if (shouldDispachNotifications.load()) delay(delay)
            }
            yield()
        }
    }

    // An out-of-band event pump so actions and signals work for our notifications
    private val eventLoopJob: Job = coroutineScope.launch(start = CoroutineStart.ATOMIC) {
        val eventLoop = requireNotNull(LibGLib.g_main_loop_new(null, 0)) {
            "Could not create GMainLoop instance"
        }
        val loopContext = requireNotNull(LibGLib.g_main_loop_get_context(eventLoop)) {
            "Could not obtain GMainContext for event loop"
        }
        while (shouldHandleEvents.load()) {
            if (LibGLib.g_main_context_iteration(loopContext, 0) == 1) continue
            yield()
        }
        LibGLib.g_main_loop_unref(eventLoop)
    }

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

    private fun create(tag: String, notification: Notification): NotificationHandle {
        val notificationAddress = LibNotify.notify_notification_new(
            summary = notification.title,
            body = notification.description,
            icon = DEFAULT_ICON
        ) ?: return NotificationHandle.INVALID

        LibNotify.notify_notification_set_app_name(notificationAddress, name)
        LibNotify.notify_notification_set_timeout(notificationAddress, timeout.inWholeMilliseconds.toInt())
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
                onNotificationActivated(stateRef.get())
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
                onNotificationClosed(stateRef.get())
                stateRef.dispose()
            }.reinterpret(),
            data = stateRef.asCPointer(),
            destroyData = null,
            connectFlags = 0U
        )

        return if (show(notificationAddress)) LinuxNotificationHandle(tag).apply {
            address = notificationAddress
        }
        else NotificationHandle.INVALID
    }

    override suspend fun push(notification: Notification, tag: String): NotificationHandle {
        // If this notification is already pending or active, return early
        if (tag in notifications || pendingNotifications.any { it.first == tag }) return NotificationHandle.INVALID
        pendingNotifications += Triple(tag, lastNotification == notification, notification)
        lastNotification.store(notification)
        return LinuxNotificationHandle(tag) // Early return so we don't block
    }

    override suspend fun update(handle: NotificationHandle, notification: Notification): NotificationHandle {
        handle.check<LinuxNotificationHandle>()
        val tag = handle.tag
        // If the notification is already active, just update it immediately
        if (tag in notifications) {
            val notificationAddress =
                requireNotNull(handle.address) { "Handle does not contain a valid address" }
            LibNotify.notify_notification_update(
                notification = notificationAddress,
                summary = notification.title,
                body = notification.description,
                icon = DEFAULT_ICON
            )
            setIcon(notificationAddress, notification)
            return if (show(notificationAddress)) handle
            else NotificationHandle.INVALID
        }
        // Otherwise update the pending notification associated with the given tag
        val outdatedEntry =
            pendingNotifications.find { it.first == tag } ?: return NotificationHandle.INVALID
        val outdatedIndex = pendingNotifications.indexOf(outdatedEntry)
        if (outdatedIndex == -1) return NotificationHandle.INVALID
        val isSameContent = lastNotification == notification
        pendingNotifications[outdatedIndex] = Triple(tag, isSameContent, notification)
        lastNotification.store(notification)
        return handle
    }

    override suspend fun pop(handle: NotificationHandle) {
        handle.check<LinuxNotificationHandle>()
        val tag = handle.tag
        // If the notification is already active, close it
        if (tag in notifications) {
            val address = handle.address
            memScoped {
                val errorAddress = allocPointerTo<GError>()
                if (LibNotify.notify_notification_close(address, errorAddress.ptr) == 0) {
                    errorAddress.value?.freeAndThrow()
                    return
                }
            }
            LibGObject.g_object_unref(address?.reinterpret())
            notifications -= tag
            return
        }
        // Otherwise dequeue the pending notification early
        val outdatedEntry = pendingNotifications.find { it.first == tag } ?: return
        pendingNotifications -= outdatedEntry
    }

    override suspend fun clearAll() {
        for ((_, notification) in notifications) {
            memScoped {
                val errorAddress = allocPointerTo<GError>()
                if (LibNotify.notify_notification_close(notification.address, errorAddress.ptr) == 0) {
                    errorAddress.value?.freeAndThrow()
                }
            }
        }
        notifications.clear()
        pendingNotifications.clear()
    }

    override fun close() {
        runBlocking { clearAll() }
        runBlocking {
            shouldDispachNotifications.store(false)
            dequeueJob.join()
        }
        runBlocking {
            shouldHandleEvents.store(false)
            eventLoopJob.join()
        }
        coroutineScope.cancel()
        LibNotify.notify_uninit()
        closeCallback()
    }
}

@OptIn(ExperimentalAtomicApi::class)
actual fun NotificationHandler.withActivationHandler(handler: ActivationHandler): NotificationHandler {
    check<LinuxNotificationHandler>()
    activationHandler.store(handler)
    return this
}

actual fun NotificationHandler(
    name: String,
    id: String,
    priority: NotificationPriority,
    isDebugEnabled: Boolean,
): NotificationHandler = LinuxNotificationHandler(name, id, priority)
