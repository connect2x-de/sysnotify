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

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.window.ComposeViewport
import de.connect2x.sysnotify.NotificationIcon
import de.connect2x.sysnotify_sample.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.skiko.wasm.onWasmReady

@OptIn(ExperimentalComposeUiApi::class, ExperimentalResourceApi::class)
suspend fun main() {
    onWasmReady {
        ComposeViewport {
            App(getIcon = {
                val icon = Res.readBytes("files/app_icon.png").decodeToImageBitmap()
                val width = icon.width
                val height = icon.height
                val pixelCount = width * height
                val pixels = icon.toPixelMap(0, 0, width, height).buffer

                val iconDataSize = pixelCount shl 2
                val iconData = ByteArray(iconDataSize)
                // We need to swizzle the color channels from ARGB to RGBA
                for (i in pixels.indices) {
                    val pixel = pixels[i]
                    val componentIndex = i shl 2
                    iconData[componentIndex] = ((pixel shr 16) and 0xFF).toByte()
                    iconData[componentIndex + 1] = ((pixel shr 8) and 0xFF).toByte()
                    iconData[componentIndex + 2] = (pixel and 0xFF).toByte()
                    iconData[componentIndex + 3] = ((pixel shr 24) and 0xFF).toByte()
                }
                NotificationIcon(iconData, width, height)
            })
        }
    }
}
