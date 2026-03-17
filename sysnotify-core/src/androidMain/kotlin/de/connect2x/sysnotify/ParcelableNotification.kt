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

import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.Creator

/**
 * A Parcelable implementation of the [Notification] class for Android intents.
 * This class allows notification data to be passed between Android components
 * through intents.
 *
 * @property title The title of the notification, can be null
 * @property description The description/content of the notification, can be null
 * @property callbackData Custom data associated with the notification for callback handling, can be null
 */
data class ParcelableNotification(
    val title: String?,
    val description: String?,
    val callbackData: String?,
) : Parcelable {
    /**
     * Secondary constructor that creates a ParcelableNotification from a [Notification] object.
     *
     * @param notification The notification object to convert to a parcelable format
     */
    constructor(notification: Notification) : this(
        notification.title,
        notification.description,
        notification.callbackData
    )

    override fun describeContents(): Int = 0

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeNullable(title, Parcel::writeString)
        parcel.writeNullable(description, Parcel::writeString)
        parcel.writeNullable(callbackData, Parcel::writeString)
    }

    /**
     * Creator for ParcelableNotification objects, used by the Android Parcel system.
     */
    companion object CREATOR : Creator<ParcelableNotification> {
        override fun createFromParcel(parcel: Parcel): ParcelableNotification {
            return ParcelableNotification(
                title = parcel.readNullable(Parcel::readString),
                description = parcel.readNullable(Parcel::readString),
                callbackData = parcel.readNullable(Parcel::readString)
            )
        }

        override fun newArray(size: Int): Array<ParcelableNotification?> {
            return arrayOfNulls(size)
        }
    }
}
