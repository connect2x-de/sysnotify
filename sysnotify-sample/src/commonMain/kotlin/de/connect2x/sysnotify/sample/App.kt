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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.connect2x.sysnotify.Notification
import de.connect2x.sysnotify.NotificationHandler
import de.connect2x.sysnotify.NotificationIcon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)

// Internal because in Android we need to access it from onRequestPermissionsResult still, TODO: fix this
internal lateinit var notificationHandler: NotificationHandler

fun onAppInit(initCallback: NotificationHandler.() -> Unit = {}) {
    notificationHandler = NotificationHandler(
        name = "Sysnotify Sample",
        id = "de.connect2x.sysnotify"
    )
    notificationHandler.initCallback()
}

fun onAppExit() {
    coroutineScope.cancel()
    notificationHandler.close()
}

@Composable
fun App(
    icon: NotificationHandler.() -> NotificationIcon? = { null },
    launchUserData: NotificationHandler.() -> String? = { null },
) {
    // TODO: Add wait-state to show "waiting for perms" modal
    LaunchedEffect(Unit) {
        notificationHandler.requestPermissions()
    }

    var title by remember { mutableStateOf("Hello") }
    var description by remember { mutableStateOf("World!") }
    var callbackData by remember { mutableStateOf("This is some user data to attach to the notification") }

    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Column {
            Text(
                text = "Sysnotify Sample",
                style = MaterialTheme.typography.h4,
                modifier = Modifier.padding(vertical = 10.dp)
            )
            Text(
                text = "This sample application allows testing the Sysnotify library by pushing a custom notification with the given properties.",
                modifier = Modifier.padding(vertical = 5.dp)
            )
            // Title
            Text(
                text = "Title",
                modifier = Modifier.padding(vertical = 5.dp),
                style = MaterialTheme.typography.h5,
            )
            Row {
                TextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.weight(1F).padding(vertical = 10.dp)
                )
            }
            // Description
            Text(
                text = "Description",
                modifier = Modifier.padding(vertical = 5.dp),
                style = MaterialTheme.typography.h5,
            )
            Row {
                TextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.weight(1F).padding(vertical = 10.dp)
                )
            }
            // User Data
            Text(
                text = "User Data",
                modifier = Modifier.padding(vertical = 5.dp),
                style = MaterialTheme.typography.h5,
            )
            Row {
                TextField(
                    value = callbackData,
                    onValueChange = { callbackData = it },
                    modifier = Modifier.weight(1F).padding(vertical = 10.dp)
                )
            }
            // Launch Notification
            notificationHandler.launchUserData()?.let {
                Text(
                    text = "Launch Notification",
                    modifier = Modifier.padding(vertical = 5.dp),
                    style = MaterialTheme.typography.h5,
                )
                Text(
                    modifier = Modifier.padding(vertical = 5.dp),
                    text = "User Data: $launchUserData"
                )
            }

            Row {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            notificationHandler.push(
                                Notification(
                                    title = title,
                                    description = description,
                                    icon = notificationHandler.icon(),
                                    callbackData = callbackData
                                )
                            )
                        }
                    },
                    modifier = Modifier.weight(0.5F)
                ) { Text("Test") }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        coroutineScope.launch {
                            notificationHandler.clearAll()
                        }
                    },
                    modifier = Modifier.weight(0.5F)
                ) { Text("Clear") }
            }
        }
    }
}
