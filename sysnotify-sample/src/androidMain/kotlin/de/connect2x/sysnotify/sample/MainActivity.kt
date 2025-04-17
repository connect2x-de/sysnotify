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
import de.connect2x.sysnotify.getLaunchNotification
import de.connect2x.sysnotify.getNotificationIcon
import de.connect2x.sysnotify.handlePermissionRequest
import de.connect2x.sysnotify.withActivity

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onAppInit { withActivity { this@MainActivity } }
        setContent {
            App(
                launchUserData = { getLaunchNotification()?.callbackData },
                icon = { resources.getNotificationIcon(R.drawable.ic_android) }
            )
        }
    }

    @Deprecated("For direct handling of permission results, use permission result callbacks")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        notificationHandler.handlePermissionRequest(requestCode, grantResults)
    }

    override fun onDestroy() {
        onAppExit()
        super.onDestroy()
    }
}
