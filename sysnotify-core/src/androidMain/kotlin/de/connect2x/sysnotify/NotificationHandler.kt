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

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import kotlin.concurrent.atomics.AtomicReference
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

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
typealias ActivityGetter = () -> ComponentActivity?

/**
 * Function type for creating an Intent that will be triggered when a notification is activated.
 * @param context The Android Context to use for creating the Intent
 * @param notification The Notification that was activated
 * @return An Intent that will be used as the PendingIntent for the notification
 */
typealias NotificationIntentFactory = (context: Context, notification: Notification) -> Intent

@InternalNotificationApi
internal class AndroidNotificationHandler( // @formatter:off
    override val appId: String,
    override val name: String,
    override val id: String,
    override val priority: NotificationPriority,
    override val contributesToCounter: Boolean
) : NotificationHandler { // @formatter:on
    companion object {
        private const val EXTRA_APP_ID: String = "sysnotify_app_id"
        private const val EXTRA_CHANNEL_ID: String = "sysnotify_channel_id"
    }

    val contextGetter: AtomicReference<ContextGetter?> = AtomicReference(null)
    val activityGetter: AtomicReference<ActivityGetter?> = AtomicReference(null)
    val activationIntent: AtomicReference<NotificationIntentFactory?> = AtomicReference(null)
    val deletionIntent: AtomicReference<NotificationIntentFactory?> = AtomicReference(null)

    override val platform: NotificationPlatform = NotificationPlatform.ANDROID
    override val capabilities: NotificationCapabilities = NotificationCapabilities(NotificationCapabilities.ALL)

    val manager: NotificationManagerCompat
        get() = NotificationManagerCompat.from(context)

    override val hasPermissions: Boolean
        get() = hasNotificationPermissions(context)

    val channelDelegate: AndroidNotificationChannel by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AndroidNotificationChannel.Real(id, name, priority.nativeImportance, contributesToCounter)
        } else AndroidNotificationChannel.Fake(id, name, priority.nativeImportance, contributesToCounter)
    }

    fun getActiveNotifications(): Set<String> {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // If we are on API level 26 and above, we can directly use channelId
            return manager.activeNotifications
                .filter { notification -> notification.notification.channelId == id }
                .map { notification -> notification.tag }
                .toSet()
        }
        return manager.activeNotifications
            .filter { notification -> notification.notification.extras.getString(EXTRA_CHANNEL_ID) == id }
            .map { notification -> notification.tag }
            .toSet()
    }

    override fun getHandle(tag: String): NotificationHandle {
        require(tag.isNotEmpty()) { "Handle tag may not be empty" }
        return AndroidNotificationHandle(tag)
    }

    override suspend fun requestPermissions(callback: PermissionCallback) {
        // If we are API level T or above, request permission to push notifications
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (hasPermissions) {
                callback(true)
                return
            }
            val currentActivity =
                requireNotNull(activity) { "No current activity with set context, use withActivity() to set it" }

            var unregister: () -> Unit = {}
            val callbackKey = Uuid.random().toString()
            val launcher = currentActivity.activityResultRegistry.register(
                callbackKey,
                ActivityResultContracts.RequestPermission(),
            ) { granted ->
                callback(granted)
                unregister()
            }
            unregister = { launcher.unregister() }

            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        callback(true) // On all other versions where perm is not needed, we invoke callback directly
    }

    private fun createActivationIntent(notification: Notification): Intent? {
        return activationIntent.load()?.let { it(context, notification) }
            ?: activity?.getDefaultActivationIntentFactory(appId)(context, notification)
    }

    private fun createDeletionIntent(notification: Notification): Intent? {
        return activationIntent.load()?.let { it(context, notification) }
    }

    context(builder: NotificationCompat.Builder)
    private fun setupIntents(notification: Notification) = with(builder) {
        createActivationIntent(notification)?.let { intent ->
            setContentIntent(
                PendingIntent.getActivity(
                    activity,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        }
        createDeletionIntent(notification)?.let { intent ->
            setDeleteIntent(
                PendingIntent.getBroadcast(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        }
    }

    context(builder: NotificationCompat.Builder)
    private fun setupIcons(notification: Notification) = with(builder) {
        notification.icon?.let(NotificationIcon::toBitmap)?.let(::setLargeIcon)
        val smallIcon =
            notification.statusIcon?.let(NotificationIcon::toBitmap)?.let(IconCompat::createWithBitmap)
        if (smallIcon != null) setSmallIcon(smallIcon)
        else setSmallIcon(R.drawable.ic_notification)
    }

    context(builder: NotificationCompat.Builder)
    private fun setupSound(notification: Notification) = with(builder) {
        if (notification.playSound) notification.soundPath?.let { setSound(it.toUri()) }
        else {
            setSilent(true)
            setSound(null)
        }
    }

    context(builder: NotificationCompat.Builder)
    private fun setupExtras() = with(builder) {
        setExtras(Bundle().apply {
            putString(EXTRA_APP_ID, appId)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                // For API level 26 and below, we need to add a channel tag to the extras so we can filter later on
                putString(EXTRA_CHANNEL_ID, id)
            }
        })
    }

    fun create(
        notification: Notification,
        handle: AndroidNotificationHandle,
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

                if (contributesToCounter) {
                    setBadgeIconType(NotificationCompat.BADGE_ICON_NONE)
                    setNumber(1)
                }

                setupExtras()
                setupSound(notification)
                setupIcons(notification)
                setupIntents(notification)

                callback(this)
            }.build()
        }
    }

    @SuppressLint("MissingPermission") // It is the callers responsibility to make sure perms are defined
    override suspend fun push(
        notification: Notification,
        tag: String
    ): NotificationHandle {
        if (!hasPermissions) return NotificationHandle.INVALID
        // If this is already active, we just create a valid handle to the existing notification
        if (tag in getActiveNotifications()) return AndroidNotificationHandle(tag)
        // If this handler contributes to the global counter, we increment BEFORE creating the new notification
        return create(notification, AndroidNotificationHandle(tag)).apply {
            manager.notify(this.tag, 0, requireNotNull(value) { "Invalid notification handle" })
        }
    }

    @SuppressLint("MissingPermission") // It is the callers responsibility to make sure perms are defined
    override suspend fun update(
        handle: NotificationHandle,
        notification: Notification
    ): NotificationHandle {
        if (!hasPermissions || handle !is AndroidNotificationHandle) return NotificationHandle.INVALID
        // If the notification is not active anymore, we ignore the update
        if (handle.tag !in getActiveNotifications()) return NotificationHandle.INVALID
        return create(notification, handle).apply {
            manager.notify(tag, 0, requireNotNull(value) { "Invalid notification handle" })
        }
    }

    override suspend fun pop(handle: NotificationHandle) {
        if (!hasPermissions || handle !is AndroidNotificationHandle) return
        manager.cancel(handle.tag, 0)
    }

    override suspend fun clearAll() {
        if (!hasPermissions) return
        manager.cancelAll()
    }

    override suspend fun unregister() {
        clearAll()
        channelDelegate.delete(context)
    }

    override fun close() {}
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
        check(this is AndroidNotificationHandler) { "In order to retrieve Android context, NotificationHandler must be valid" }
        return requireNotNull(contextGetter.load()?.invoke()) {
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
val NotificationHandler.activity: ComponentActivity?
    get() {
        check(this is AndroidNotificationHandler) { "In order to retrieve Android activity, NotificationHandler must be valid" }
        return activityGetter.load()?.invoke()
    }

/**
 * Associates an Android Context with this NotificationHandler.
 *
 * This method must be called before using the NotificationHandler.
 *
 * @param getter A function that provides the Android Context
 * @return This NotificationHandler for method chaining
 */
fun NotificationHandler.withContext(getter: () -> Context): NotificationHandler {
    if (this !is AndroidNotificationHandler) return this
    contextGetter.store(getter)
    channelDelegate.init(getter()) // Initialize channel delegate
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
 */
fun NotificationHandler.withActivity(getter: () -> ComponentActivity): NotificationHandler {
    if (this !is AndroidNotificationHandler) return this
    activityGetter.store(getter)
    val activity = getter()
    channelDelegate.init(activity) // Initialize channel delegate
    return this
}

/**
 * Associates an [NotificationIntentFactory] with this NotificationHandler instance
 * used to create the activation intent for when the user taps on the notification.
 *
 * @param factory A function that creates an Intent for notification activation
 * @return This NotificationHandler for method chaining
 */
fun NotificationHandler.withActivationIntent(factory: NotificationIntentFactory): NotificationHandler {
    if (this !is AndroidNotificationHandler) return this
    activationIntent.store(factory)
    return this
}

/**
 * Associates an [NotificationIntentFactory] with this NotificationHandler instance
 * used to create the deletion intent for when the user swipes away the notification.
 *
 * @param factory A function that creates an Intent for notification activation
 * @return This NotificationHandler for method chaining
 */
fun NotificationHandler.withDeletionIntent(factory: NotificationIntentFactory): NotificationHandler {
    if (this !is AndroidNotificationHandler) return this
    deletionIntent.store(factory)
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
 */
fun NotificationHandler.create(
    notification: Notification,
    tag: String = Uuid.random().toHexString(),
    callback: NotificationCompat.Builder.() -> Unit,
): NotificationHandle {
    if (this !is AndroidNotificationHandler) return NotificationHandle.INVALID
    return create(
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
 */
@OptIn(ExperimentalUuidApi::class)
@SuppressLint("MissingPermission") // It is the callers responsibility to make sure perms are defined
fun NotificationHandler.push(
    notification: Notification,
    tag: String = Uuid.random().toHexString(),
    callback: NotificationCompat.Builder.() -> Unit,
): NotificationHandle {
    if (!hasPermissions || this !is AndroidNotificationHandler) return NotificationHandle.INVALID
    // If this is already active, we just create a valid handle to the existing notification
    if (tag in getActiveNotifications()) return AndroidNotificationHandle(tag)
    return create(
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
 */
@OptIn(ExperimentalUuidApi::class)
@SuppressLint("MissingPermission") // It is the callers responsibility to make sure perms are defined
fun NotificationHandler.update(
    handle: NotificationHandle,
    notification: Notification,
    callback: NotificationCompat.Builder.() -> Unit,
): NotificationHandle {
    if (!hasPermissions || this !is AndroidNotificationHandler || handle !is AndroidNotificationHandle) return NotificationHandle.INVALID
    return create(
        notification = notification,
        callback = callback,
        handle = handle,
    ).apply {
        manager.notify(tag, 0, requireNotNull(value) { "Invalid notification handle" })
    }
}

/**
 * Gets the notification that launched the current activity, if any.
 *
 * This method can be used to determine if the activity was launched from a notification
 * and to retrieve the notification data.
 *
 * @return The notification that launched the activity, or null if the activity was not launched from a notification
 */
@Suppress("DEPRECATION")
fun NotificationHandler.getLaunchNotification(): ParcelableNotification? {
    if (this !is AndroidNotificationHandler) return null
    return (activity?.intent as? NotificationIntent)?.notification
}

/**
 * Gets the Android notification channel associated with this NotificationHandler.
 * **Note: before calling this function, make sure to provide a [Context] via [withContext] or [withActivity]!**
 *
 * @return The Android notification channel
 * @throws IllegalStateException if the handler is not an AndroidNotificationHandler
 */
@get:RequiresApi(Build.VERSION_CODES.O)
val NotificationHandler.channel: NotificationChannel
    get() {
        check(this is AndroidNotificationHandler) { "In order to retrieve notification channel, NotificationHandler must be valid" }
        val delegate = channelDelegate
        check(delegate is AndroidNotificationChannel.Real) {
            "NotificationChannel requires API level 26 and above"
        }
        return delegate.channel
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
fun Activity.getDefaultActivationIntentFactory(appId: String): NotificationIntentFactory = { context, notification ->
    NotificationActivationIntent(context, this::class.java, appId, notification)
}

actual fun NotificationHandler( // @formatter:off
    name: String,
    id: String,
    priority: NotificationPriority,
    appId: String,
    contributesToCounter: Boolean
): NotificationHandler { // @formatter:on
    return AndroidNotificationHandler(
        appId = appId,
        name = name,
        id = id,
        priority = priority,
        contributesToCounter = contributesToCounter
    )
}
