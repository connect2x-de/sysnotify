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

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.Drawable

/**
 * Create a new [NotificationIcon] instance from the given [Bitmap].
 * The bitmap must have the [Bitmap.Config.ARGB_8888] format.
 *
 * @param bitmap The bitmap to create a new [NotificationIcon] from.
 * @return A new [NotificationIcon] instance containing the pixel data
 *  of the given [Bitmap].
 */
fun NotificationIcon.Companion.fromBitmap(bitmap: Bitmap): NotificationIcon {
    require(bitmap.config == Bitmap.Config.ARGB_8888) { "Bitmap must be in ARGB_8888 format" }
    val width = bitmap.width
    val height = bitmap.height
    val data = ByteArray((width * height) shl 2)
    for (y in 0..<height) {
        for (x in 0..<width) {
            val index = (y * width + x) shl 2
            val color = bitmap.getPixel(x, y).toUInt()
            data[index + 0] = unpackRed(from = ColorFormat.ARGB, color).toByte()
            data[index + 1] = unpackGreen(from = ColorFormat.ARGB, color).toByte()
            data[index + 2] = unpackBlue(from = ColorFormat.ARGB, color).toByte()
            data[index + 3] = unpackAlpha(from = ColorFormat.ARGB, color).toByte()
        }
    }
    return NotificationIcon(data, width, height)
}

/**
 * Creates a new [NotificationIcon] instance from the given [Drawable].
 *
 * @param drawable The drawable to create a new [NotificationIcon] from.
 *  The [Drawable] will be rasterized as is and must provide an intrinsic size.
 * @return A new [NotificationIcon] instance containing the pixel data
 *  of the rasterized [Drawable].
 */
fun NotificationIcon.Companion.fromDrawable(drawable: Drawable): NotificationIcon {
    return fromBitmap(drawable.toBitmap())
}

/**
 * Creates a new [NotificationIcon] from the given [Drawable] resource.
 * This is just a convenience function which calls [fromDrawable].
 *
 * @param resource The resource ID to create the new notification icon from.
 * @return A new [NotificationIcon] containing the swizzled bitmap data
 * of the rasterized [Drawable] associated with the given resource ID.
 */
fun Resources.getNotificationIcon(resource: Int): NotificationIcon {
    return NotificationIcon.fromDrawable(getDrawable(resource, null))
}

/**
 * Converts this [NotificationIcon] instance to a new [Bitmap] with the
 * [Bitmap.Config.ARGB_8888] format.
 *
 * @return A new [Bitmap] instance with the swizzled data of this
 *  notification icon instance.
 */
fun NotificationIcon.toBitmap(): Bitmap {
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
        for (y in 0..<height) {
            for (x in 0..<width) {
                val index = (y * width + x) shl 2
                val pixel = pack(
                    to = ColorFormat.ARGB,
                    red = data[index].toUByte(),
                    green = data[index + 1].toUByte(),
                    blue = data[index + 2].toUByte(),
                    alpha = data[index + 3].toUByte()
                )
                setPixel(x, y, pixel.toInt())
            }
        }
    }
}
