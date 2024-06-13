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
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.benasher44.uuid.Uuid
import com.benasher44.uuid.uuid4

private data class AndroidNotificationHandle(
    val uuid: Uuid = uuid4()
) : NotificationHandle

private class AndroidNotificationHandler(
    override val name: String,
    context: Any?
) : NotificationHandler {
    companion object {
        private const val PERMISSION_REQUEST_CODE: Int = 1337
    }

    private val activity = context as? Activity
        ?: throw IllegalStateException("Android notifications require current Activity")
    private val manager =
        activity.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: throw IllegalStateException("Could not retrieve notification manager instance")
    private val channelId: String = uuid4().toString()

    override val platform: NotificationPlatform = NotificationPlatform.ANDROID

    override val capabilities: NotificationCapabilities = NotificationCapabilities(
        isTitleSupported = true,
        isDescriptionSupported = true,
        isIconSupported = true,
        isActionSupported = false, // TODO: implement this
        isPopSupported = true
    )

    init {
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    channelId, name, NotificationManager.IMPORTANCE_HIGH
                )
            )
        )
    }

    override fun close() {
        manager.deleteNotificationChannel(channelId)
    }

    override suspend fun requestPermissions() {
        // If we are API level T or above, request permission to push notifications
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    activity,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    activity,
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
        with(NotificationManagerCompat.from(activity)) {
            cancel(handle.uuid.hashCode())
        }
    }

    @SuppressLint("MissingPermission") // It is the callers responsibility to make sure perms are defined
    private fun pushImpl(
        notification: Notification,
        handle: AndroidNotificationHandle = AndroidNotificationHandle()
    ): AndroidNotificationHandle {
        with(NotificationManagerCompat.from(activity)) {
            notify(
                handle.uuid.hashCode(), NotificationCompat.Builder(activity, channelId)
                    .setContentTitle(notification.title ?: name)
                    .setContentText(notification.description)
                    .setLargeIcon(notification.icon?.let {
                        BitmapFactory.decodeByteArray(it, 0, it.size)
                    })
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true) // Remove notification when user taps it
                    .build()
            )
        }
        return handle
    }
}

fun NotificationHandler.Companion.create(
    name: String,
    context: Context
): NotificationHandler = AndroidNotificationHandler(name, context)
