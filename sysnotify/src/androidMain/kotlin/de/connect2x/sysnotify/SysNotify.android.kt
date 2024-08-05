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
import android.app.NotificationChannel
import android.app.NotificationManager
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

private data class AndroidNotificationHandle(
    val uuid: Uuid = uuid4()
) : NotificationHandle

private class AndroidNotificationHandler(
    override val name: String,
    override val id: String,
    private val activityGetter: () -> Activity,
    private val activationIntent: ((Activity, Notification) -> SysNotifyIntent)?
) : NotificationHandler {
    companion object {
        private const val PERMISSION_REQUEST_CODE: Int = 1337
    }

    private val manager =
        activityGetter().getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: throw IllegalStateException("Could not retrieve notification manager instance")

    override val platform: NotificationPlatform = NotificationPlatform.ANDROID

    override val capabilities: NotificationCapabilities = NotificationCapabilities(
        isTitleSupported = true,
        isDescriptionSupported = true,
        isIconSupported = true,
        isActionSupported = true,
        isPopSupported = true
    )

    init {
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    id, name, NotificationManager.IMPORTANCE_HIGH
                )
            )
        )
    }

    override fun close() {
        manager.deleteNotificationChannel(id)
    }

    override suspend fun requestPermissions() {
        // If we are API level T or above, request permission to push notifications
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    activityGetter(),
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    activityGetter(),
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    PERMISSION_REQUEST_CODE
                )
            }
        }
    }

    override suspend fun push(notification: Notification): NotificationHandle {
        return pushImpl(notification)
    }

    override suspend fun update(
        handle: NotificationHandle,
        notification: Notification
    ): NotificationHandle {
        if (handle !is AndroidNotificationHandle) throw IllegalArgumentException("Handle is not an android notification handle")
        return pushImpl(notification, handle)
    }

    override suspend fun pop(handle: NotificationHandle) {
        if (handle !is AndroidNotificationHandle) throw IllegalArgumentException("Handle is not an android notification handle")
        with(NotificationManagerCompat.from(activityGetter())) {
            cancel(handle.uuid.hashCode())
        }
    }

    @SuppressLint("MissingPermission") // It is the callers responsibility to make sure perms are defined
    private fun pushImpl(
        notification: Notification,
        handle: AndroidNotificationHandle = AndroidNotificationHandle()
    ): AndroidNotificationHandle {
        val activity = activityGetter()
        with(NotificationManagerCompat.from(activity)) {
            val builder = NotificationCompat.Builder(activity, id)
                .setContentTitle(notification.title ?: name)
                .setContentText(notification.description)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true) // Remove notification when user taps it

            val icon = notification.icon
            if (icon != null) {
                val iconData = icon.data
                builder.setSmallIcon(IconCompat.createWithData(iconData, 0, iconData.size))
                    .setLargeIcon(BitmapFactory.decodeByteArray(iconData, 0, iconData.size))
            } else builder.setSmallIcon(R.drawable.ic_notification)

            activationIntent?.let {
                val intent = it(activity, notification)
                val pendingIntent = PendingIntent.getActivity(
                    activity,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.setContentIntent(pendingIntent)
            }

            notify(handle.uuid.hashCode(), builder.build())
        }
        return handle
    }
}

fun NotificationHandler.Companion.create(
    name: String,
    activityGetter: () -> Activity,
    id: String = name,
    activationIntent: ((Activity, Notification) -> SysNotifyIntent)? = { activity, notification ->
        SysNotifyIntent(
            activity,
            activity::class.java,
            notification
        )
    }
): NotificationHandler = AndroidNotificationHandler(name, id, activityGetter, activationIntent)
