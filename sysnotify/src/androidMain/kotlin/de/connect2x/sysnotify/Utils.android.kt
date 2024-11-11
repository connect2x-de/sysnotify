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

package de.connect2x.sysnotify

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Parcel
import androidx.core.app.ActivityCompat
import kotlin.math.max

fun Drawable.toBitmap(): Bitmap {
    return when (this) {
        is BitmapDrawable -> bitmap
        else -> Bitmap.createBitmap(
            max(1, intrinsicWidth),
            max(1, intrinsicHeight),
            Bitmap.Config.ARGB_8888
        ).apply {
            setBounds(0, 0, width, height)
            draw(Canvas(this))
        }
    }
}

fun hasNotificationPermissions(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else true
}

internal inline fun <T> Parcel.writeNullable(value: T?, writer: Parcel.(T) -> Unit) {
    if (value == null) {
        writeInt(0)
        return
    }
    writeInt(1)
    writer(this, value)
}

internal inline fun <T> Parcel.readNullable(reader: Parcel.() -> T): T? {
    return if (readInt() == 0) null
    else reader(this)
}
