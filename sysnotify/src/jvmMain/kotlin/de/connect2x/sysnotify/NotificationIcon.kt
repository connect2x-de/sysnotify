/*
 * Copyright 2025 connect2x GmbH
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

import java.awt.image.BufferedImage

fun NotificationIcon.Companion.fromBufferedImage(image: BufferedImage): NotificationIcon {
    val width = image.width
    val height = image.height
    val iconPixelCount = width * height
    val rawPixelData = IntArray(iconPixelCount) { 0 }
    image.getRGB(0, 0, width, height, rawPixelData, 0, width)
    val iconDataSize = iconPixelCount shl 2
    val iconData = ByteArray(iconDataSize) { 0 }
    // We need to swizzle the color channels from ARGB to RGBA
    for (i in rawPixelData.indices) {
        val pixel = rawPixelData[i]
        val componentIndex = i shl 2
        iconData[componentIndex] = ((pixel shr 16) and 0xFF).toByte()
        iconData[componentIndex + 1] = ((pixel shr 8) and 0xFF).toByte()
        iconData[componentIndex + 2] = (pixel and 0xFF).toByte()
        iconData[componentIndex + 3] = ((pixel shr 24) and 0xFF).toByte()
    }
    return NotificationIcon(iconData, width, height)
}
