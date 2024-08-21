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
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.benasher44.uuid.Uuid
import com.benasher44.uuid.uuid4
import android.app.Notification as AndroidNotification
import android.app.NotificationChannel as AndroidNotificationChannel

private data class AndroidNotificationHandle(
    val uuid: Uuid = uuid4(),
    var value: AndroidNotification? = null
) : NotificationHandle

internal class AndroidNotificationHandler(
    override val name: String,
    override val id: String,
    override val priority: NotificationPriority,
    internal val contextGetter: () -> Context,
    internal var activityGetter: () -> Activity?,
    private val activationIntent: (Context, Notification) -> SysNotifyIntent,
    internal var permissionCallback: (Activity, Boolean) -> Unit
) : NotificationHandler {
    companion object {
        const val PERMISSION_REQUEST_CODE: Int = 0xBEEF
    }

    internal val channel: AndroidNotificationChannel = AndroidNotificationChannel(
        id, name, priority.nativeImportance
    ).apply { manager.createNotificationChannel(this) }
    private val manager: NotificationManagerCompat
        get() = NotificationManagerCompat.from(contextGetter())

    override val platform: NotificationPlatform = NotificationPlatform.ANDROID
    override val capabilities: NotificationCapabilities = NotificationCapabilities(NotificationCapabilities.ALL)
    override val hasPermissions: Boolean
        get() = hasNotificationPermissions(contextGetter())

    override fun close() {
        manager.deleteNotificationChannel(id)
    }

    override suspend fun requestPermissions() {
        if (hasPermissions) return
        val activity = requireNotNull(activityGetter()) { "No current activity set" }
        // If we are API level T or above, request permission to push notifications
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun createImpl(
        notification: Notification,
        handle: AndroidNotificationHandle = AndroidNotificationHandle()
    ): AndroidNotificationHandle {
        return handle.apply {
            val context = contextGetter()
            value = NotificationCompat.Builder(context, id).apply {
                setContentTitle(notification.title ?: name)
                setContentText(notification.description)
                setPriority(notification.priority.nativePriority)
                setOngoing(!notification.dismissible)
                setAutoCancel(true) // Remove notification when user taps it
                // Setup icon, fallback to default icon if none was specified
                val icon = notification.icon
                if (icon != null) {
                    val iconData = icon.data
                    setSmallIcon(IconCompat.createWithData(iconData, 0, iconData.size))
                    setLargeIcon(BitmapFactory.decodeByteArray(iconData, 0, iconData.size))
                } else setSmallIcon(R.drawable.ic_notification)
                // Setup activation intent if present
                setContentIntent(
                    PendingIntent.getActivity(
                        context,
                        0,
                        activationIntent(context, notification),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
            }.build()
        }
    }

    override fun create(notification: Notification): NotificationHandle = createImpl(notification)

    @SuppressLint("MissingPermission") // It is the callers responsibility to make sure perms are defined
    override suspend fun push(notification: Notification): NotificationHandle {
        if (!hasPermissions) return AndroidNotificationHandle()
        return createImpl(notification).apply {
            manager.notify(uuid.hashCode(), requireNotNull(value) { "Invalid notification handle" })
        }
    }

    @SuppressLint("MissingPermission") // It is the callers responsibility to make sure perms are defined
    override suspend fun update(
        handle: NotificationHandle,
        notification: Notification
    ): NotificationHandle {
        if (!hasPermissions) return handle
        require(handle is AndroidNotificationHandle) { "Handle is not an android notification handle" }
        return createImpl(notification, handle).apply {
            manager.notify(uuid.hashCode(), requireNotNull(value) { "Invalid notification handle" })
        }
    }

    override suspend fun pop(handle: NotificationHandle) {
        if (!hasPermissions) return
        require(handle is AndroidNotificationHandle) { "Handle is not an android notification handle" }
        manager.cancel(handle.uuid.hashCode())
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
    require(this is AndroidNotificationHandler)
    if (grantResults.first() == PackageManager.PERMISSION_DENIED) {
        permissionCallback(requireNotNull(activityGetter()) { "No current activity set" }, false)
        return
    }
    permissionCallback(requireNotNull(activityGetter()) { "No current activity set" }, true)
}

/**
 * Overrides the getter function which provides the current activity instance.
 * If the [NotificationHandler] instance was created by a service instead of an activity,
 * this function must be called before using either [NotificationHandler.requestPermissions] or [getLaunchNotification].
 *
 * @param activity The current activity instance.
 * @return This notification handler instance.
 */
fun NotificationHandler.withActivity(activity: Activity): NotificationHandler {
    require(this is AndroidNotificationHandler)
    activityGetter = { activity }
    return this
}

/**
 * Retrieves the [Notification] instance the current activity was started from,
 * including the specified user data.
 *
 * @return The [Notification] instance the current activity was started from.
 */
@Suppress("DEPRECATION")
fun NotificationHandler.getLaunchNotification(): Notification? {
    require(this is AndroidNotificationHandler)
    return requireNotNull(activityGetter()) { "No current activity set" }.intent?.let {
        return@let if (!it.hasExtra(SysNotifyIntent.EXTRA_LAUNCH_NOTIFICATION)) null
        else requireNotNull(it.getParcelableExtra<ParcelableNotification>(SysNotifyIntent.EXTRA_LAUNCH_NOTIFICATION)?.notification)
    }
}

/**
 * The native [AndroidNotificationChannel] instance associated with this notification handler.
 */
val NotificationHandler.nativeChannel: AndroidNotificationChannel
    get() {
        require(this is AndroidNotificationHandler) { "Handle is not an Android notification handler" }
        return channel
    }

/**
 * The native [AndroidNotification] instance associated with this notification handle.
 */
val NotificationHandle.nativeNotification: AndroidNotification
    get() {
        require(this is AndroidNotificationHandle) { "Handle is not an Android notification handle" }
        return requireNotNull(value) { "Handle does not contain a valid Android Notification reference" }
    }

/**
 * The native notification importance associated with this priority enum.
 */
val NotificationPriority.nativeImportance: Int
    get() = when (this) {
        NotificationPriority.LOW -> NotificationManagerCompat.IMPORTANCE_LOW
        NotificationPriority.HIGH -> NotificationManagerCompat.IMPORTANCE_HIGH
        else -> NotificationManagerCompat.IMPORTANCE_DEFAULT
    }

/**
 * The native notification priority associated with this priority enum.
 */
val NotificationPriority.nativePriority: Int
    get() = when (this) {
        NotificationPriority.LOW -> NotificationCompat.PRIORITY_LOW
        NotificationPriority.HIGH -> NotificationCompat.PRIORITY_HIGH
        else -> NotificationCompat.PRIORITY_DEFAULT
    }

/**
 * Creates a new Android notification handler instance with the given name and ID.
 * The internal ID of the handler should ideally match the app's package group.
 *
 * @param name The display name of the notification handler. May appear in notifications.
 * @param id The internal ID of the notification handler.
 * @param contextGetter A getter closure which provides the context associated with your app. This may be an
 *  application context, a service or an activity.
 * @param activityGetter A getter closure which provides the current activity of your app.
 *  This is required for requesting permissions and retrieving the launch notification.
 * @param activationIntent A factory closure which creates a new [SysNotifyIntent] or any subtype of it.
 *  For each pushed notification, this closure will be invoked to create an associated launch intent,
 *  which will be activated when the user taps on one of the notifications pushed by SysNotify.
 * @param permissionCallback An optional callback closure which is called when the permission grant results are processed.
 *  The boolean parameter will be true if the permission was granted, false otherwise.
 * @return A new notification handler with the given properties.
 */
fun NotificationHandler.Companion.create(
    name: String,
    id: String = name,
    contextGetter: () -> Context,
    activityGetter: () -> Activity? = { contextGetter() as? Activity },
    activationIntent: (Context, Notification) -> SysNotifyIntent,
    permissionCallback: (Activity, Boolean) -> Unit = { _, _ -> },
    priority: NotificationPriority = NotificationPriority.DEFAULT
): NotificationHandler =
    AndroidNotificationHandler(name, id, priority, contextGetter, activityGetter, activationIntent, permissionCallback)
