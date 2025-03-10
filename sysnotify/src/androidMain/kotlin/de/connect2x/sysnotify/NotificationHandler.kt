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
import kotlinx.atomicfu.atomic
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import android.app.NotificationChannel as AndroidNotificationChannel

internal typealias ContextGetter = () -> Context
internal typealias ActivityGetter = () -> Activity?
internal typealias ActivationFactory = (Context, Notification) -> Intent

internal class AndroidNotificationHandler( // @formatter:off
    override val name: String,
    override val id: String,
    override val priority: NotificationPriority,
    internal val permissionCallback: PermissionCallback
) : NotificationHandler { // @formatter:on
    companion object {
        const val PERMISSION_REQUEST_CODE: Int = 0xBEEF
    }

    internal val channel: AndroidNotificationChannel = AndroidNotificationChannel(
        id, name, priority.nativeImportance
    ).apply { manager.createNotificationChannel(this) }

    internal var contextGetter: ContextGetter? by atomic(null)
    internal var activityGetter: ActivityGetter? by atomic { context as? Activity }
    internal var activationIntent: ActivationFactory? by atomic(null)

    override val platform: NotificationPlatform = NotificationPlatform.ANDROID
    override val capabilities: NotificationCapabilities = NotificationCapabilities(NotificationCapabilities.ALL)

    internal val manager: NotificationManagerCompat
        get() = NotificationManagerCompat.from(context)

    override val hasPermissions: Boolean
        get() = hasNotificationPermissions(context)

    override fun close() {
        manager.deleteNotificationChannel(id)
    }

    override fun getHandle(tag: String): NotificationHandle {
        require(tag.isNotEmpty()) { "Handle tag may not be empty" }
        return AndroidNotificationHandle(tag)
    }

    override suspend fun requestPermissions() {
        if (hasPermissions) return
        // If we are API level T or above, request permission to push notifications
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                requireNotNull(activity) { "No current activity with set context, use withActivity() to set it" },
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    @OptIn(ExperimentalUuidApi::class)
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
                setPriority(notification.priority.nativePriority)
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
                        activationIntent?.let { it(context, notification) }
                            ?: context.getDefaultActivationFactory()(context, notification),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )

                callback(this)
            }.build()
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    @SuppressLint("MissingPermission") // It is the callers responsibility to make sure perms are defined
    override suspend fun push(notification: Notification): NotificationHandle {
        if (!hasPermissions) return AndroidNotificationHandle()
        return createImpl(notification).apply {
            manager.notify(hashCode(), requireNotNull(value) { "Invalid notification handle" })
        }
    }

    @SuppressLint("MissingPermission") // It is the callers responsibility to make sure perms are defined
    override suspend fun update(
        handle: NotificationHandle,
        notification: Notification
    ): NotificationHandle {
        if (!hasPermissions) return handle
        require(handle is AndroidNotificationHandle) { "Handle is not an Android notification handle" }
        require(handle.isValid) { "Handle must be valid" }
        return createImpl(notification, handle).apply {
            manager.notify(hashCode(), requireNotNull(value) { "Invalid notification handle" })
        }
    }

    override suspend fun pop(handle: NotificationHandle) {
        if (!hasPermissions) return
        require(handle is AndroidNotificationHandle) { "Handle is not an Android notification handle" }
        require(handle.isValid) { "Handle must be valid" }
        manager.cancel(handle.hashCode())
    }
}

// TODO: document this
val NotificationHandler.context: Context
    get() {
        require(this is AndroidNotificationHandler) { "Handle is not an Android notification handle" }
        return requireNotNull(contextGetter) { "No context associated with NotificationHandler, use withContext() to set it" }()
    }

// TODO: document this
val NotificationHandler.activity: Activity?
    get() {
        require(this is AndroidNotificationHandler) { "Handle is not an Android notification handle" }
        return requireNotNull(activityGetter) { "No activity associated with NotificationHandler, use withActivity() to set it" }()
    }

// TODO: document this
fun NotificationHandler.withContext(getter: () -> Context): NotificationHandler {
    require(this is AndroidNotificationHandler) { "Handle is not an Android notification handle" }
    contextGetter = getter
    return this
}

/**
 * Overrides the getter function which provides the current activity instance.
 * If the [NotificationHandler] instance was created by a service instead of an activity,
 * this function must be called before using either [NotificationHandler.requestPermissions] or [getLaunchNotification].
 *
 * @param activity The current activity instance.
 * @return This notification handler instance.
 */
fun NotificationHandler.withActivity(getter: () -> Activity): NotificationHandler {
    require(this is AndroidNotificationHandler) { "Handle is not an Android notification handle" }
    activityGetter = getter
    return this
}

// TODO: document this
@OptIn(ExperimentalUuidApi::class)
fun NotificationHandler.create(
    notification: Notification,
    tag: String = Uuid.random().toHexString(),
    callback: NotificationCompat.Builder.() -> Unit,
): NotificationHandle {
    require(this is AndroidNotificationHandler) { "Handle is not an Android notification handle" }
    return createImpl(notification, AndroidNotificationHandle(tag), callback)
}

/**
 * Same as [NotificationHandler.push] but with an additional
 * builder callback to customize the platform-specific notification instance.
 */
@OptIn(ExperimentalUuidApi::class)
@SuppressLint("MissingPermission") // It is the callers responsibility to make sure perms are defined
fun NotificationHandler.push(
    notification: Notification,
    tag: String = Uuid.random().toHexString(),
    callback: NotificationCompat.Builder.() -> Unit,
): NotificationHandle {
    require(this is AndroidNotificationHandler) { "Handle is not an Android notification handle" }
    if (!hasPermissions) return AndroidNotificationHandle()
    return createImpl(
        notification = notification,
        callback = callback,
        handle = AndroidNotificationHandle(tag)
    ).apply {
        manager.notify(hashCode(), requireNotNull(value) { "Invalid notification handle" })
    }
}

/**
 * Same as [NotificationHandler.update] but with an additional
 * builder callback to customize the platform-specific notification instance.
 */
@OptIn(ExperimentalUuidApi::class)
@SuppressLint("MissingPermission") // It is the callers responsibility to make sure perms are defined
fun NotificationHandler.update(
    handle: NotificationHandle,
    notification: Notification,
    callback: NotificationCompat.Builder.() -> Unit,
): NotificationHandle {
    if (!hasPermissions) return handle // TODO: maybe log an error/warning?
    require(this is AndroidNotificationHandler && handle is AndroidNotificationHandle) { "Handle is not an Android notification handle" }
    require(handle.isValid) { "Handle must be valid" }
    return createImpl(
        notification = notification,
        callback = callback,
        handle = handle,
    ).apply {
        manager.notify(hashCode(), requireNotNull(value) { "Invalid notification handle" })
    }
}

/**
 * Call this in the [Activity.onRequestPermissionsResult] of your main activity
 * to automatically handle notification permission requests initiated by [NotificationHandler.requestPermissions].
 * This will invoke the `permissionCallback` specified when creating the notification handler instance.
 *
 * @param requestCode The unique code of the permission request. `0xBEEF` for SysNotify requests.
 * @param grantResults An array of all grant results for each requested permission.
 */
fun NotificationHandler.handlePermissionRequest(requestCode: Int, grantResults: IntArray) {
    if (requestCode != AndroidNotificationHandler.PERMISSION_REQUEST_CODE || grantResults.isEmpty()) return
    require(this is AndroidNotificationHandler) { "Handle is not an Android notification handle" }
    if (grantResults.first() == PackageManager.PERMISSION_DENIED) {
        permissionCallback(this, false)
        return
    }
    permissionCallback(this, true)
}

/**
 * Retrieves the [Notification] instance the current activity was started from,
 * including the specified user data.
 *
 * @return The [Notification] instance the current activity was started from.
 */
@Suppress("DEPRECATION")
fun NotificationHandler.getLaunchNotification(): ParcelableNotification? {
    require(this is AndroidNotificationHandler) { "Handle is not an Android notification handle" }
    return activity?.intent?.let {
        return@let if (!it.hasExtra(SysNotifyIntent.EXTRA_LAUNCH_NOTIFICATION)) null
        else it.getParcelableExtra(SysNotifyIntent.EXTRA_LAUNCH_NOTIFICATION)
    }
}

/**
 * The native [AndroidNotificationChannel] instance associated with this notification handler.
 */
val NotificationHandler.channel: AndroidNotificationChannel
    get() {
        require(this is AndroidNotificationHandler) { "Handler is not an Android notification handler" }
        return channel
    }

/**
 * Create a default [SysNotifyIntent] from the activity associated with this context.
 *
 * @return A new [SysNotifyIntent] which invokes the current activity.
 */
fun Context.getDefaultActivationFactory(): ActivationFactory = { context, notification ->
    require(this is Activity) { "An activity is required for a notification intent" }
    SysNotifyIntent(context, this::class.java, notification)
}

actual fun NotificationHandler( // @formatter:off
    name: String,
    id: String,
    priority: NotificationPriority,
    permissionCallback: PermissionCallback
): NotificationHandler { // @formatter:on
    return AndroidNotificationHandler(name, id, priority, permissionCallback)
}
