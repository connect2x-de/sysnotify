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

import kotlinx.cinterop.COpaquePointer
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
import platform.posix.atexit
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalForeignApi::class)
internal object NotificationDispatcher {
    private const val HINT_SOUND_FILE: String = "sound-file"
    private const val DEFAULT_SOUND: String = "/usr/share/sounds/freedesktop/stereo/message.oga"
    private const val ACTION_OPEN: String = "open"
    private const val SIGNAL_CLOSED: String = "closed"
    const val DEFAULT_ICON: String = "dialog-information"

    // @formatter:off
    val isAvailable: Boolean = LibGdkPixbuf.isAvailable
            && LibGLib.isAvailable
            && LibGObject.isAvailable
            && LibNotify.isAvailable
    // @formatter:on

    private val isEventLoopRunning: AtomicBoolean = AtomicBoolean(true)
    private val errorOnExternalThread: AtomicBoolean = AtomicBoolean(false)
    private var shouldDispachNotifications: AtomicBoolean = AtomicBoolean(true)
    private val pendingNotifications: ArrayDeque<PendingNotification> = ArrayDeque()
    private val pendingNotificationsLock: Mutex = Mutex()

    private val notifications: HashMap<String, LinuxNotificationHandle> = HashMap()
    private val notificationsLock: Mutex = Mutex()
    private val activationStates: HashMap<String, StableRef<NotificationActivationState>> = HashMap()
    private val activationStatesLock: Mutex = Mutex()

    private val minimumDelay: Duration = 1.seconds  // The minimum delay between any notification
    private val timeout: Duration = 5.seconds       // The timeout until a notification expires

    private inline fun threadMain(crossinline block: suspend () -> Unit) {
        runBlocking {
            try {
                block()
            } catch (_: Throwable) {
                errorOnExternalThread.store(true)
            }
        }
    }

    private lateinit var eventLoopThread: Thread
    private lateinit var dequeueThread: Thread

    init {
        if (isAvailable) {
            // Event loop thread
            eventLoopThread = Thread {
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
            // Dequeue thread
            dequeueThread = Thread {
                Thread.name = "Sysnotify Notification Thread"
                fun isRunning(): Boolean = shouldDispachNotifications.load() && !errorOnExternalThread.load()
                threadMain {
                    while (isRunning()) {
                        while (isRunning() && pendingNotificationsLock.withLock { pendingNotifications.isNotEmpty() }) {
                            val (tag, isSameContent, notification, handler) = pendingNotificationsLock.withLock { pendingNotifications.removeFirst() }
                            val handle = create(tag, notification, handler)
                            if(handle !is LinuxNotificationHandle) continue // Only proceed when notification is valid
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
            // Close hook
            atexit(staticCFunction<Unit> {
                NotificationDispatcher.apply {
                    shouldDispachNotifications.store(false)
                    dequeueThread.join()
                    isEventLoopRunning.store(false)
                    eventLoopThread.join()
                }
            })
        }
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
            handler = staticCFunction<CPointer<NotifyNotification>, COpaquePointer?, Unit> { _, userData ->
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

    // Thread-safe API

    fun show(notification: CPointer<NotifyNotification>): Boolean {
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

    fun setIcon(nativeNotification: CPointer<NotifyNotification>, notification: Notification) {
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

    suspend fun removePendingNotification(index: Int) {
        if (!isAvailable) return
        pendingNotificationsLock.withLock {
            pendingNotifications.removeAt(index)
        }
    }

    suspend fun removeNotification(tag: String) {
        if (!isAvailable) return
        notificationsLock.withLock {
            notifications -= tag
        }
    }

    suspend fun updatePendingNotification(index: Int, notification: PendingNotification) {
        if (!isAvailable) return
        pendingNotificationsLock.withLock {
            pendingNotifications[index] = notification
        }
    }

    suspend fun getPendingNotificationIndex(notification: PendingNotification): Int {
        return if (isAvailable) pendingNotificationsLock.withLock {
            pendingNotifications.indexOf(notification)
        } else -1
    }

    suspend fun findPendingNotification(tag: String): PendingNotification? {
        return if (isAvailable) pendingNotificationsLock.withLock {
            pendingNotifications.find { notification -> notification.tag == tag }
        } else null
    }

    suspend fun findNotification(tag: String): LinuxNotificationHandle? {
        return if (isAvailable) notificationsLock.withLock {
            notifications[tag]
        } else null
    }

    suspend fun getNotificationsFor(handler: LinuxNotificationHandler): List<LinuxNotificationHandle> {
        return if (isAvailable) notificationsLock.withLock {
            notifications.filter { (_, handle) -> handle.handler === handler }.values.toList()
        } else emptyList()
    }

    suspend fun getPendingNotificationsFor(handler: LinuxNotificationHandler): List<PendingNotification> {
        return if (isAvailable) pendingNotificationsLock.withLock {
            pendingNotifications.filter { handle -> handle.handler === handler }.toList()
        } else emptyList()
    }

    suspend fun hasTag(tag: String): Boolean {
        return if (isAvailable) notificationsLock.withLock {
            tag in notifications
        } else false
    }

    suspend fun hasPendingTag(tag: String): Boolean {
        return if (isAvailable) pendingNotificationsLock.withLock {
            pendingNotifications.any { notification -> notification.tag == tag }
        } else false
    }

    suspend fun enqueue(notification: PendingNotification) {
        if (!isAvailable) return
        pendingNotificationsLock.withLock {
            pendingNotifications += notification
        }
    }
}
