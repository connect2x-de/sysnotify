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

@file:OptIn(ExperimentalForeignApi::class)

package de.connect2x.sysnotify

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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import libnotify.GError
import libnotify.GdkColorspace
import libnotify.NotifyNotification
import libnotify.gpointer // This needs to stay!
import platform.posix.atexit
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private data class PendingNotification(
    val tag: String,
    val isSameContent: Boolean,
    val notification: Notification,
    val handler: LinuxNotificationHandler
)

@OptIn(ExperimentalAtomicApi::class)
internal class LinuxNotificationHandler(
    override val appId: String,
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

        private suspend fun onNotificationClosed(
            state: NotificationActivationState
        ) {
            state.handler.apply {
                notificationsLock.withLock {
                    notifications -= state.tag
                }
                activationStatesLock.withLock {
                    activationStates -= state.tag
                }
            }
        }

        private fun onNotificationActivated(
            state: NotificationActivationState
        ) {
            state.activated = true // Notify close signal handler that we've handled this notification already
            state.handler.activationHandler.load()?.invoke(state.notification)
        }

        private val isEventLoopRunning: AtomicBoolean = AtomicBoolean(true)
        private val errorOnExternalThread: AtomicBoolean = AtomicBoolean(false)
        private var shouldDispachNotifications: AtomicBoolean = AtomicBoolean(true)
        private val pendingNotifications: ArrayDeque<PendingNotification> = ArrayDeque()
        private val pendingNotificationsLock: Mutex = Mutex()

        // TODO this does not survive a restart
        private val notifications: HashMap<String, LinuxNotificationHandle> = HashMap()
        private val notificationsLock: Mutex = Mutex()
        private val activationStates: HashMap<String, StableRef<NotificationActivationState>> = HashMap()
        private val activationStatesLock: Mutex = Mutex()

        private inline fun threadMain(crossinline block: suspend () -> Unit) {
            runBlocking {
                try {
                    block()
                } catch (_: Throwable) {
                    errorOnExternalThread.store(true)
                }
            }
        }

        private val eventLoopThread: Thread = Thread {
            Thread.name = "Sysnotify Event Thread"
            threadMain {
                val mainContext = requireNotNull(LibGLib.g_main_context_default()) {
                    "Could not retrieve default main context"
                }
                val mainLoop = requireNotNull(LibGLib.g_main_loop_new(mainContext, 1)) {
                    "Could not create main loop"
                }
                while (isEventLoopRunning.load() && !errorOnExternalThread.load()) {
                    LibGLib.g_main_context_acquire(mainContext)
                    if (LibGLib.g_main_context_iteration(mainContext, 0) == 1) {
                        LibGLib.g_main_context_release(mainContext)
                        continue
                    }
                    LibGLib.g_main_context_release(mainContext)
                    Thread.sleep(10)
                }
                LibGLib.g_main_loop_unref(mainLoop)
            }
        }

        private val dequeueThread: Thread = Thread {
            Thread.name = "Sysnotify Notification Thread"
            fun isRunning(): Boolean = shouldDispachNotifications.load() && !errorOnExternalThread.load()
            threadMain {
                while (isRunning()) {
                    while (isRunning() && pendingNotificationsLock.withLock { pendingNotifications.isNotEmpty() }) {
                        val (tag, isSameContent, notification, handler) = pendingNotificationsLock.withLock { pendingNotifications.removeFirst() }
                        val handle = create(tag, notification, handler)
                        handle.check<LinuxNotificationHandle>()
                        notificationsLock.withLock {
                            notifications[tag] = handle
                        }
                        // Minimum required DBus timeouts to prevent dropping notifications
                        val delay = if (isSameContent) timeout else minimumDelay
                        // Only delay if the state hasn't changed since then
                        if (shouldDispachNotifications.load()) Thread.sleep(delay.inWholeMilliseconds)
                    }
                    Thread.sleep(10)
                }
            }
        }

        init {
            atexit(staticCFunction<Unit> {
                LinuxNotificationHandler.apply {
                    shouldDispachNotifications.store(false)
                    dequeueThread.join()
                    isEventLoopRunning.store(false)
                    eventLoopThread.join()
                }
            })
        }

        private suspend fun create(
            tag: String,
            notification: Notification,
            handler: LinuxNotificationHandler
        ): NotificationHandle {
            if (errorOnExternalThread.load()) return NotificationHandle.INVALID

            val notificationAddress = LibNotify.notify_notification_new(
                summary = notification.title,
                body = notification.description,
                icon = DEFAULT_ICON
            ) ?: return NotificationHandle.INVALID

            LibNotify.notify_notification_set_app_name(notificationAddress, handler.name)
            LibNotify.notify_notification_set_timeout(notificationAddress, timeout.inWholeMilliseconds.toInt())
            LibNotify.notify_notification_set_urgency(notificationAddress, notification.priority.urgency)

            if (notification.playSound) {
                val soundPath = notification.soundPath ?: DEFAULT_SOUND
                LibNotify.notify_notification_set_hint_string(notificationAddress, HINT_SOUND_FILE, soundPath)
            }
            setIcon(notificationAddress, notification)

            // We create a new state for this notification and create a stable reference to it
            val stateRef = StableRef.create(NotificationActivationState(handler, tag, notification))
            activationStatesLock.withLock {
                activationStates[tag] = stateRef
            }

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
                    runBlocking { // Warning can be ignored
                        onNotificationClosed(stateRef.get())
                    }
                    stateRef.dispose()
                }.reinterpret(),
                data = stateRef.asCPointer(),
                destroyData = null,
                connectFlags = 0U
            )

            return if (show(notificationAddress)) LinuxNotificationHandle(tag, handler).apply {
                address = notificationAddress
            }
            else NotificationHandle.INVALID
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
            if (errorOnExternalThread.load()) return false
            memScoped {
                val errorAddress = allocPointerTo<GError>()
                if (LibNotify.notify_notification_show(notification, errorAddress.ptr) == 0) {
                    errorAddress.value?.freeAndThrow()
                    return false // Return if we fall through, which doesn't happen, but no _Noreturn..
                }
            }
            return true
        }
    }

    init {
        check(LibNotify.notify_init(name) != 0) { "Could not initialize libnotify" }
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

    private var lastNotification: AtomicReference<Notification?> = AtomicReference(null)
    internal var activationHandler: AtomicReference<ActivationHandler?> = AtomicReference(null)

    override fun getHandle(tag: String): NotificationHandle {
        require(tag.isNotEmpty()) { "Handle tag may not be empty" }
        return LinuxNotificationHandle(tag, this)
    }

    override suspend fun push(notification: Notification, tag: String): NotificationHandle {
        // If this notification is already pending or active, return early
        if (notificationsLock.withLock { tag in notifications }
            || pendingNotificationsLock.withLock { pendingNotifications.any { it.tag == tag } }
            || errorOnExternalThread.load()) return NotificationHandle.INVALID
        pendingNotificationsLock.withLock {
            pendingNotifications += PendingNotification(tag, lastNotification == notification, notification, this)
        }
        lastNotification.store(notification)
        return LinuxNotificationHandle(tag, this) // Early return so we don't block
    }

    override suspend fun update(handle: NotificationHandle, notification: Notification): NotificationHandle {
        if (errorOnExternalThread.load()) return NotificationHandle.INVALID
        handle.check<LinuxNotificationHandle>()
        val tag = handle.tag
        // If the notification is already active, just update it immediately
        val actualHandle = notificationsLock.withLock { notifications[tag] }
        if (actualHandle != null) {
            val notificationAddress =
                requireNotNull(actualHandle.address) { "Handle does not contain a valid address" }
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
        pendingNotificationsLock.withLock {
            val outdatedEntry =
                pendingNotifications.find { it.tag == tag } ?: return NotificationHandle.INVALID
            val outdatedIndex = pendingNotifications.indexOf(outdatedEntry)
            if (outdatedIndex == -1) return NotificationHandle.INVALID
            val isSameContent = lastNotification == notification
            pendingNotifications[outdatedIndex] = PendingNotification(tag, isSameContent, notification, this)
        }
        lastNotification.store(notification)
        return handle
    }

    override suspend fun pop(handle: NotificationHandle) {
        if (errorOnExternalThread.load()) return
        handle.check<LinuxNotificationHandle>()
        val tag = handle.tag
        // If the notification is already active, close it
        val actualHandle = notificationsLock.withLock { notifications[tag] }
        if (actualHandle != null) {
            val address =
                requireNotNull(actualHandle.address) { "Handle does not contain a valid address" }
            memScoped {
                val errorAddress = allocPointerTo<GError>()
                if (LibNotify.notify_notification_close(address, errorAddress.ptr) == 0) {
                    errorAddress.value?.freeAndThrow()
                    return
                }
            }
            LibGObject.g_object_unref(address.reinterpret())
            notificationsLock.withLock {
                notifications -= tag
            }
            return
        }
        // Otherwise dequeue the pending notification early
        pendingNotificationsLock.withLock {
            val outdatedEntry = pendingNotifications.find { it.tag == tag } ?: return
            pendingNotifications -= outdatedEntry
        }
    }

    override suspend fun clearAll() {
        if (errorOnExternalThread.load()) return
        val activeToRemove = notificationsLock.withLock {
            notifications.filter { it.value.handler === this }
        }
        for ((tag, notification) in activeToRemove) {
            memScoped {
                val errorAddress = allocPointerTo<GError>()
                if (LibNotify.notify_notification_close(notification.address, errorAddress.ptr) == 0) {
                    errorAddress.value?.freeAndThrow()
                }
            }
            notificationsLock.withLock {
                notifications -= tag
            }
        }
        pendingNotificationsLock.withLock {
            val pendingToRemove = pendingNotifications.filter { it.handler === this }
            pendingToRemove.forEach(pendingNotifications::remove)
        }
    }

    override suspend fun unregister() {
        clearAll()
        close()
    }

    override fun close() {
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
    appId: String
): NotificationHandler = try {
    LinuxNotificationHandler(appId = appId, name = name, id = id, priority = priority)
} catch (error: Throwable) {
    NoopNotificationHandler
}
