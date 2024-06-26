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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.connect2x.sysnotify.Notification
import de.connect2x.sysnotify.NotificationHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)

@Composable
fun App(notificationHandler: NotificationHandler) {
    coroutineScope.launch {
        notificationHandler.requestPermissions() // Request permissions in-place because we are lazy here
    }

    var title by remember { mutableStateOf("Hello") }
    var description by remember { mutableStateOf("World!") }

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
            // TODO: implement icon
            Row {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            notificationHandler.push(Notification(
                                title = title,
                                description = description,
                                activateAction = { println("Notification activated!") },
                                dismissAction = { println("Notification dismissed!") },
                                failAction = { println("Notification failed!") },
                                removeAction = { println("Notification removed!") }
                            ))
                        }
                    },
                    modifier = Modifier.weight(1F)
                ) { Text("Test") }
            }
        }
    }
}
