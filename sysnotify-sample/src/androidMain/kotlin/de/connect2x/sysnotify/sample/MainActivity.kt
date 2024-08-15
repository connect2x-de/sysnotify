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

package de.connect2x.sysnotify.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import de.connect2x.sysnotify.NotificationHandler
import de.connect2x.sysnotify.SysNotifyIntent
import de.connect2x.sysnotify.create
import de.connect2x.sysnotify.getLaunchNotification
import de.connect2x.sysnotify.handlePermissionRequest
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    private lateinit var notificationHandler: NotificationHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationHandler = NotificationHandler.create(
            name = "Sysnotify Sample",
            id = "Connect2x.Sysnotify",
            contextGetter = { this },
            activationIntent = { context, notification ->
                SysNotifyIntent(
                    context,
                    MainActivity::class.java,
                    notification
                )
            },
            permissionCallback = { if (!it) finishAffinity() }
        )
        runBlocking { notificationHandler.requestPermissions() }
        setContent {
            App(
                notificationHandler = notificationHandler,
                launchNotification = notificationHandler.getLaunchNotification()
            )
        }
    }

    @Suppress("DEPRECATION")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        notificationHandler.handlePermissionRequest(requestCode, grantResults)
    }

    override fun onDestroy() {
        super.onDestroy()
        notificationHandler.close()
    }
}
