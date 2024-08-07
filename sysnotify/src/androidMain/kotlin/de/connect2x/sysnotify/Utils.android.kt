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

import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.Creator

private inline fun <T> Parcel.writeNullable(value: T?, writer: Parcel.(T) -> Unit) {
    if (value == null) {
        writeInt(0)
        return
    }
    writeInt(1)
    writer(this, value)
}

private inline fun <T> Parcel.readNullable(reader: Parcel.() -> T): T? {
    return if (readInt() == 0) null
    else reader(this)
}

@JvmInline
internal value class ParcelableNotification(val notification: Notification) : Parcelable {
    override fun describeContents(): Int = 0

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeNullable(notification.title, Parcel::writeString)
        parcel.writeNullable(notification.description, Parcel::writeString)
        parcel.writeNullable(notification.icon) {
            writeInt(it.data.size)
            writeByteArray(it.data)
            writeInt(it.width)
            writeInt(it.height)
        }
        parcel.writeNullable(notification.userData, Parcel::writeString)
    }

    companion object CREATOR : Creator<ParcelableNotification> {
        override fun createFromParcel(parcel: Parcel): ParcelableNotification {
            return ParcelableNotification(
                Notification(
                    title = parcel.readNullable(Parcel::readString),
                    description = parcel.readNullable(Parcel::readString),
                    icon = parcel.readNullable {
                        NotificationIcon(
                            data = readInt().let {
                                ByteArray(it).apply {
                                    readByteArray(this)
                                }
                            },
                            width = readInt(),
                            height = readInt()
                        )
                    },
                    userData = parcel.readNullable(Parcel::readString)
                )
            )
        }

        override fun newArray(size: Int): Array<ParcelableNotification?> {
            return arrayOfNulls(size)
        }
    }
}
