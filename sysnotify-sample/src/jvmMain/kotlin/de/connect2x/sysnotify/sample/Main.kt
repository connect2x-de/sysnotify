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

import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import de.connect2x.sysnotify.NotificationHandler
import de.connect2x.sysnotify.NotificationIcon
import de.connect2x.sysnotify.create
import de.connect2x.sysnotify.fromBufferedImage
import javax.imageio.ImageIO

fun main() {
    application {
        val icon =
            this::class.java.getResourceAsStream("/assets/icons/app_icon.png")
                .use { ImageIO.read(it) }
        val notificationHandler = NotificationHandler.create(
            name = "Sysnotify Sample",
            id = "Connect2x.Sysnotify.Sample",
            activationCallback = { println(it.callbackData) } // TODO: Callback data is not global state
        )

        Window(
            title = "Sysnotify Sample",
            icon = BitmapPainter(icon.toComposeImageBitmap()),
            onCloseRequest = {
                notificationHandler.close()
                exitApplication()
            }) {
            App(notificationHandler, NotificationIcon.fromBufferedImage(icon))
        }
    }
}
