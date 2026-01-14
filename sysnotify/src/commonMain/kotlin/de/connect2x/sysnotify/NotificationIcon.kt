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

package de.connect2x.sysnotify

/**
 * Represents an icon used in notifications across different platforms.
 *
 * This class stores raw pixel data in RGBA format (Red, Green, Blue, Alpha) along with
 * the dimensions of the image. It provides a platform-agnostic way to represent
 * notification icons, with platform-specific extensions for conversion to and from
 * native image formats (like Bitmap on Android or BufferedImage on JVM).
 *
 * The pixel data is stored as a continuous byte array in row-major order,
 * with each pixel represented by 4 consecutive bytes (R, G, B, A).
 */
data class NotificationIcon(
    /**
     * Raw pixel data in RGBA format (Red, Green, Blue, Alpha).
     * The data is stored as a continuous byte array in row-major order,
     * with each pixel represented by 4 consecutive bytes.
     */
    val data: ByteArray,

    /**
     * Width of the icon in pixels.
     */
    val width: Int,

    /**
     * Height of the icon in pixels.
     */
    val height: Int
) {
    companion object;

    // Begin ugly generated code
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as NotificationIcon

        if (!data.contentEquals(other.data)) return false
        if (width != other.width) return false
        if (height != other.height) return false

        return true
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + width
        result = 31 * result + height
        return result
    }
    // End ugly generated code
}
