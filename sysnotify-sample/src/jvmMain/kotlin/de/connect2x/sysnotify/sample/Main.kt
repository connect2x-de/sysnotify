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

package de.connect2x.sysnotify.sample

import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import de.connect2x.sysnotify.NotificationHandler
import de.connect2x.sysnotify.NotificationIcon
import de.connect2x.sysnotify.fromBufferedImage
import de.connect2x.sysnotify.withActivationHandler
import sun.misc.Unsafe
import java.awt.Frame
import java.awt.Toolkit
import javax.imageio.ImageIO

object Main {
    private const val APP_NAME: String = "Sysnotify Sample"

    @JvmStatic
    fun main(args: Array<String>) {
        NotificationHandler.installHooks()
        updateAppClassName()
        application {
            val icon = Main::class.java.getResourceAsStream("/assets/icons/app_icon.png").use { ImageIO.read(it) }
            val windowState = rememberWindowState()
            windowState.size = DpSize(800.dp, 820.dp)
            windowState.placement = WindowPlacement.Floating
            windowState.position = WindowPosition(Alignment.Center)
            Window(
                title = "SysNotify Sample",
                icon = BitmapPainter(icon.toComposeImageBitmap()),
                onCloseRequest = {
                    exitApplication()
                }, state = windowState
            ) {
                // On Desktop platforms, we manually need to handle bringing up the application window
                App(getIcon = { NotificationIcon.fromBufferedImage(icon) }) {
                    withActivationHandler {
                        window.state = Frame.NORMAL
                        window.requestFocus()
                    }
                }
            }
        }
    }

    /**
     * Workaround for https://youtrack.jetbrains.com/issue/CMP-3308 adjusted to use an unsafe callback.
     *
     * Since the module system security makes it impossible to use reflection for this on our minimum target JDK, we need to
     * use sun.misc.Unsafe to try to override the field value, without tripping up reflection security checks. What a
     * beautiful mess.
     */
    @Suppress("DiscouragedPrivateApi")
    private fun updateAppClassName() {
        try {
            val toolkit = Toolkit.getDefaultToolkit()
            if (toolkit::class.java.simpleName != "XToolkit") return
            val classNameField = toolkit.javaClass.getDeclaredField("awtAppClassName")

            try {
                classNameField.isAccessible = true
                classNameField.set(null, APP_NAME)
            } catch (_: Throwable) {
                val theUnsafe = Unsafe::class.java.getDeclaredField("theUnsafe")
                theUnsafe.isAccessible = true
                val unsafe = theUnsafe.get(null) as? Unsafe ?: return

                val fieldBase = unsafe.staticFieldBase(classNameField)
                val fieldOffset = unsafe.staticFieldOffset(classNameField)
                unsafe.putObjectVolatile(fieldBase, fieldOffset, APP_NAME)
            }
        } catch (_: Throwable) {
            // SILENCE
        }
    }
}
