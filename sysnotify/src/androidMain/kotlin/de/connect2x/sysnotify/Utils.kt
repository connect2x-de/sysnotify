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
import androidx.core.graphics.createBitmap
import kotlin.math.max

/**
 * Converts a Drawable to a Bitmap.
 * If the Drawable is already a BitmapDrawable, returns its bitmap.
 * Otherwise, creates a new bitmap with the Drawable's dimensions and draws the Drawable onto it.
 *
 * @return A Bitmap representation of the Drawable
 */
fun Drawable.toBitmap(): Bitmap {
    return when (this) {
        is BitmapDrawable -> bitmap
        else -> createBitmap(max(1, intrinsicWidth), max(1, intrinsicHeight)).apply {
            setBounds(0, 0, width, height)
            draw(Canvas(this))
        }
    }
}

/**
 * Checks if the application has permission to post notifications.
 * On Android Tiramisu (API 33) and above, this checks for the POST_NOTIFICATIONS permission.
 * On earlier Android versions, this always returns true as the permission is not required.
 *
 * @param context The context used to check for permissions
 * @return true if the app has notification permissions, false otherwise
 */
fun hasNotificationPermissions(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else true
}

/**
 * Extension function for Parcel that writes a nullable value.
 * If the value is null, writes 0 to indicate null.
 * If the value is not null, writes 1 followed by the value using the provided writer function.
 *
 * @param T The type of the value to write
 * @param value The nullable value to write to the parcel
 * @param writer A function that writes a non-null value of type T to the parcel
 */
internal inline fun <T> Parcel.writeNullable(value: T?, writer: Parcel.(T) -> Unit) {
    if (value == null) {
        writeInt(0)
        return
    }
    writeInt(1)
    writer(this, value)
}

/**
 * Extension function for Parcel that reads a nullable value.
 * First reads an integer flag: if 0, returns null; if 1, reads and returns the value using the provided reader function.
 * This function is designed to be used in conjunction with [writeNullable].
 *
 * @param T The type of the value to read
 * @param reader A function that reads a non-null value of type T from the parcel
 * @return The read value of type T, or null if the value was written as null
 */
internal inline fun <T> Parcel.readNullable(reader: Parcel.() -> T): T? {
    return if (readInt() == 0) null
    else reader(this)
}
