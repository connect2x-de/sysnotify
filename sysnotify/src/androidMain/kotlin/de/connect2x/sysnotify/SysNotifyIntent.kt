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

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.Creator

data class ParcelableNotification(
    val title: String?,
    val description: String?,
    val userData: String?,
) : Parcelable {
    constructor(notification: Notification) : this(notification.title, notification.description, notification.userData)

    override fun describeContents(): Int = 0

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeNullable(title, Parcel::writeString)
        parcel.writeNullable(description, Parcel::writeString)
        parcel.writeNullable(userData, Parcel::writeString)
    }

    companion object CREATOR : Creator<ParcelableNotification> {
        override fun createFromParcel(parcel: Parcel): ParcelableNotification {
            return ParcelableNotification(
                title = parcel.readNullable(Parcel::readString),
                description = parcel.readNullable(Parcel::readString),
                userData = parcel.readNullable(Parcel::readString)
            )
        }

        override fun newArray(size: Int): Array<ParcelableNotification?> {
            return arrayOfNulls(size)
        }
    }
}

open class SysNotifyIntent(
    packageContext: Context,
    clazz: Class<out Activity>,
    notification: Notification,
) : Intent(packageContext, clazz) {
    companion object {
        const val EXTRA_LAUNCH_NOTIFICATION: String = "launch_notification"
    }

    init {
        this.putExtra(EXTRA_LAUNCH_NOTIFICATION, ParcelableNotification(notification))
    }
}
