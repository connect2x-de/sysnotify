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

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.IconCompat
import co.touchlab.stately.collections.SharedLinkedList
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicReference
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import android.app.NotificationChannel as AndroidNotificationChannel

/**
 * Function type for retrieving the Android Context.
 * Used to provide a Context to the notification handler when needed.
 */
typealias ContextGetter = () -> Context

/**
 * Function type for retrieving the current Activity.
 * Used to provide an Activity reference to the notification handler when needed.
 * May return null if no Activity is currently available.
 */
typealias ActivityGetter = () -> Activity?

/**
 * Function type for creating an Intent that will be triggered when a notification is activated.
 * @param context The Android Context to use for creating the Intent
 * @param notification The Notification that was activated
 * @return An Intent that will be used as the PendingIntent for the notification
 */
typealias ActivationFactory = (context: Context, notification: Notification) -> Intent

internal class AndroidNotificationHandler( // @formatter:off
    override val name: String,
    override val id: String,
    override val priority: NotificationPriority,
) : NotificationHandler { // @formatter:on
    companion object {
        const val PERMISSION_REQUEST_CODE: Int = 0xBEEF
    }

    private var channel: AtomicReference<AndroidNotificationChannel?> = AtomicReference(null)

    internal var contextGetter: AtomicReference<ContextGetter?> = AtomicReference(null)
    internal var activityGetter: AtomicReference<ActivityGetter?> = AtomicReference(null)
    internal var activationIntent: AtomicReference<ActivationFactory?> = AtomicReference(null)

    override val platform: NotificationPlatform = NotificationPlatform.ANDROID
    override val capabilities: NotificationCapabilities = NotificationCapabilities(NotificationCapabilities.ALL)
    internal val permissionRequests: SharedLinkedList<PermissionCallback> = SharedLinkedList()

    internal val manager: NotificationManagerCompat
        get() = NotificationManagerCompat.from(context)

    override val hasPermissions: Boolean
        get() = hasNotificationPermissions(context)

    internal fun getOrCreateChannel(): AndroidNotificationChannel {
        val current = channel.get()
        if (current != null) return current
        val new = AndroidNotificationChannel(
            id, name, priority.nativeImportance
        ).apply { manager.createNotificationChannel(this) }
        channel.set(new)
        return new
    }

    override fun getHandle(tag: String): NotificationHandle {
        require(tag.isNotEmpty()) { "Handle tag may not be empty" }
        return AndroidNotificationHandle(tag)
    }

    override suspend fun requestPermissions(callback: PermissionCallback) {
        // If we are API level T or above, request permission to push notifications
        permissionRequests += callback
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                requireNotNull(activity) { "No current activity with set context, use withActivity() to set it" },
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    internal fun createImpl(
        notification: Notification,
        handle: AndroidNotificationHandle = AndroidNotificationHandle(),
        callback: NotificationCompat.Builder.() -> Unit = {},
    ): AndroidNotificationHandle {
        return handle.apply {
            val context = this@AndroidNotificationHandler.context
            value = NotificationCompat.Builder(context, id).apply {
                setContentTitle(notification.title ?: name)
                setContentText(notification.description)
                priority = notification.priority.nativePriority
                setOngoing(!notification.dismissible)
                setAutoCancel(true) // Remove notification when user taps it
                setGroup(notification.group)

                // Setup sound
                if (notification.playSound) notification.soundPath?.let { setSound(Uri.parse(it)) }
                else {
                    setSilent(true)
                    setSound(null)
                }

                // Setup icon, fallback to default icon if none was specified
                val icon = notification.icon
                if (icon != null) {
                    icon.toBitmap().apply {
                        setSmallIcon(IconCompat.createWithBitmap(this))
                        setLargeIcon(this)
                    }
                } else setSmallIcon(R.drawable.ic_notification)

                // Setup activation intent if present
                setContentIntent(
                    PendingIntent.getActivity(
                        context,
                        0,
                        activationIntent.get()?.let { it(context, notification) }
                            ?: context.getDefaultActivationFactory()(context, notification),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )

                callback(this)
            }.build()
        }
    }

    @SuppressLint("MissingPermission") // It is the callers responsibility to make sure perms are defined
    override suspend fun push(notification: Notification, tag: String): NotificationHandle {
        if (!hasPermissions) return NotificationHandle.INVALID
        return createImpl(notification, AndroidNotificationHandle(tag)).apply {
            manager.notify(this.tag, 0, requireNotNull(value) { "Invalid notification handle" })
        }
    }

    @SuppressLint("MissingPermission") // It is the callers responsibility to make sure perms are defined
    override suspend fun update(
        handle: NotificationHandle,
        notification: Notification
    ): NotificationHandle {
        if (!hasPermissions) return NotificationHandle.INVALID
        handle.check<AndroidNotificationHandle>()
        return createImpl(notification, handle).apply {
            manager.notify(tag, 0, requireNotNull(value) { "Invalid notification handle" })
        }
    }

    override suspend fun pop(handle: NotificationHandle) {
        if (!hasPermissions) return
        handle.check<AndroidNotificationHandle>()
        manager.cancel(handle.tag, 0)
    }

    override suspend fun clearAll() {
        if (!hasPermissions) return
        manager.cancelAll()
    }

    override fun close() {
        permissionRequests.clear()
        manager.deleteNotificationChannel(id)
        runBlocking { clearAll() }
    }
}

/**
 * Gets the Android Context associated with this NotificationHandler.
 *
 * @return The Android Context
 * @throws ClassCastException if the handler is not an AndroidNotificationHandler
 * @throws IllegalStateException if no context has been set
 */
val NotificationHandler.context: Context
    get() {
        check<AndroidNotificationHandler>()
        return activityGetter.get()?.invoke()
            ?: requireNotNull(contextGetter.get()?.invoke()) {
                "No context associated with NotificationHandler, use withContext() to set it"
            }
    }

/**
 * Gets the current Activity associated with this NotificationHandler.
 *
 * @return The current Activity, or null if no Activity is available
 * @throws ClassCastException if the handler is not an AndroidNotificationHandler
 * @throws IllegalStateException if no activity getter has been set
 */
val NotificationHandler.activity: Activity?
    get() {
        check<AndroidNotificationHandler>()
        return activityGetter.get()?.invoke()
    }

/**
 * Associates an Android Context with this NotificationHandler.
 *
 * This method must be called before using the NotificationHandler.
 *
 * @param getter A function that provides the Android Context
 * @return This NotificationHandler for method chaining
 * @throws ClassCastException if the handler is not an AndroidNotificationHandler
 */
fun NotificationHandler.withContext(getter: () -> Context): NotificationHandler {
    check<AndroidNotificationHandler>()
    contextGetter.set(getter)
    getOrCreateChannel() // Make sure channel is created once context is available
    return this
}

/**
 * Associates an Activity with this NotificationHandler.
 *
 * This method should be called if you need to request notification permissions,
 * as permission requests require an Activity.
 *
 * @param getter A function that provides the current Activity
 * @return This NotificationHandler for method chaining
 * @throws ClassCastException if the handler is not an AndroidNotificationHandler
 */
fun NotificationHandler.withActivity(getter: () -> Activity): NotificationHandler {
    check<AndroidNotificationHandler>()
    activityGetter.set(getter)
    getOrCreateChannel() // Make sure channel is created once activity is available
    return this
}

/**
 * Associates an ActivationFactory with this NotificationHandler.
 *
 * The ActivationFactory is used to create Intents that determine what happens
 * when a user clicks on a notification. This allows customizing the activation
 * behavior of notifications.
 *
 * @param factory A function that creates an Intent for notification activation
 * @return This NotificationHandler for method chaining
 * @throws ClassCastException if the handler is not an AndroidNotificationHandler
 */
fun NotificationHandler.withActivationFactory(factory: ActivationFactory): NotificationHandler {
    check<AndroidNotificationHandler>()
    activationIntent.set(factory)
    return this
}

/**
 * Creates a notification without displaying it.
 *
 * This method is useful when you want to create a notification but display it later
 * or when you need to customize the notification beyond what the standard parameters allow.
 *
 * @param notification The notification to create
 * @param tag A unique identifier for the notification (defaults to a random UUID)
 * @param callback Additional configuration to apply to the notification builder
 * @return A handle for the created notification
 * @throws ClassCastException if the handler is not an AndroidNotificationHandler
 */
fun NotificationHandler.create(
    notification: Notification,
    tag: String = Uuid.random().toHexString(),
    callback: NotificationCompat.Builder.() -> Unit,
): NotificationHandle {
    check<AndroidNotificationHandler>()
    return createImpl(
        notification = notification,
        handle = AndroidNotificationHandle(tag),
        callback = callback
    )
}

/**
 * Creates and displays a notification with additional customization options.
 *
 * This extension function allows for more customization of the notification
 * through the callback parameter.
 *
 * @param notification The notification to display
 * @param tag A unique identifier for the notification (defaults to a random UUID)
 * @param callback Additional configuration to apply to the notification builder
 * @return A handle for the displayed notification, or INVALID if permissions are not granted
 * @throws ClassCastException if the handler is not an AndroidNotificationHandler
 */
@OptIn(ExperimentalUuidApi::class)
@SuppressLint("MissingPermission") // It is the callers responsibility to make sure perms are defined
fun NotificationHandler.push(
    notification: Notification,
    tag: String = Uuid.random().toHexString(),
    callback: NotificationCompat.Builder.() -> Unit,
): NotificationHandle {
    if (!hasPermissions) return NotificationHandle.INVALID
    check<AndroidNotificationHandler>()
    return createImpl(
        notification = notification,
        callback = callback,
        handle = AndroidNotificationHandle(tag)
    ).apply {
        manager.notify(this.tag, 0, requireNotNull(value) { "Invalid notification handle" })
    }
}

/**
 * Updates an existing notification with new content and additional customization options.
 *
 * @param handle The handle of the notification to update
 * @param notification The new notification content
 * @param callback Additional configuration to apply to the notification builder
 * @return The updated notification handle, or INVALID if permissions are not granted
 * @throws ClassCastException if the handler is not an AndroidNotificationHandler or if the handle is not an AndroidNotificationHandle
 */
@OptIn(ExperimentalUuidApi::class)
@SuppressLint("MissingPermission") // It is the callers responsibility to make sure perms are defined
fun NotificationHandler.update(
    handle: NotificationHandle,
    notification: Notification,
    callback: NotificationCompat.Builder.() -> Unit,
): NotificationHandle {
    if (!hasPermissions) return NotificationHandle.INVALID
    check<AndroidNotificationHandler>()
    handle.check<AndroidNotificationHandle>()
    return createImpl(
        notification = notification,
        callback = callback,
        handle = handle,
    ).apply {
        manager.notify(tag, 0, requireNotNull(value) { "Invalid notification handle" })
    }
}

/**
 * Handles the result of a permission request.
 *
 * This method should be called from the Activity's onRequestPermissionsResult method
 * to process the result of a notification permission request.
 *
 * @param requestCode The request code from onRequestPermissionsResult
 * @param grantResults The grant results from onRequestPermissionsResult
 * @throws ClassCastException if the handler is not an AndroidNotificationHandler
 */
fun NotificationHandler.handlePermissionRequest(requestCode: Int, grantResults: IntArray) {
    if (requestCode != AndroidNotificationHandler.PERMISSION_REQUEST_CODE || grantResults.isEmpty()) return
    check<AndroidNotificationHandler>()
    if (grantResults.first() == PackageManager.PERMISSION_DENIED) {
        for (request in permissionRequests) {
            runBlocking { request(this@handlePermissionRequest, false) }
        }
        permissionRequests.clear()
        return
    }
    for (request in permissionRequests) {
        runBlocking { request(this@handlePermissionRequest, true) }
    }
    permissionRequests.clear()
}

/**
 * Gets the notification that launched the current activity, if any.
 *
 * This method can be used to determine if the activity was launched from a notification
 * and to retrieve the notification data.
 *
 * @return The notification that launched the activity, or null if the activity was not launched from a notification
 * @throws ClassCastException if the handler is not an AndroidNotificationHandler
 */
@Suppress("DEPRECATION")
fun NotificationHandler.getLaunchNotification(): ParcelableNotification? {
    check<AndroidNotificationHandler>()
    return activity?.intent?.let { intent ->
        return@let if (!intent.hasExtra(SysNotifyIntent.EXTRA_LAUNCH_NOTIFICATION)) null
        else intent.getParcelableExtra(SysNotifyIntent.EXTRA_LAUNCH_NOTIFICATION)
    }
}

/**
 * Gets the Android notification channel associated with this NotificationHandler.
 *
 * @return The Android notification channel
 * @throws ClassCastException if the handler is not an AndroidNotificationHandler
 */
val NotificationHandler.channel: AndroidNotificationChannel
    get() {
        check<AndroidNotificationHandler>()
        return getOrCreateChannel()
    }

/**
 * Creates a default activation factory for the given Context.
 *
 * The activation factory creates an Intent that will be triggered when a notification is activated.
 * The default implementation creates an Intent that will launch the current Activity.
 *
 * @return An ActivationFactory that creates Intents for notification activation
 * @throws IllegalStateException if the Context is not an Activity
 */
fun Context.getDefaultActivationFactory(): ActivationFactory = { context, notification ->
    check(this is Activity) { "An activity is required for a notification intent" }
    SysNotifyIntent(context, this::class.java, notification)
}

actual fun NotificationHandler( // @formatter:off
    name: String,
    id: String,
    priority: NotificationPriority,
    isDebugEnabled: Boolean,
): NotificationHandler { // @formatter:on
    return AndroidNotificationHandler(name, id, priority)
}
