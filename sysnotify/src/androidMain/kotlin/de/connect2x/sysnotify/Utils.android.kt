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

private enum class TypeTag(val value: UByte) {
    // @formatter:off
    NULL        (0x0U),
    BYTE        (0x1U),
    SHORT       (0x2U),
    INT         (0x3U),
    LONG        (0x4U),
    FLOAT       (0x5U),
    DOUBLE      (0x6U),
    BOOLEAN     (0x7U),
    STRING      (0x8U),
    PARCELABLE  (0x9U)
    // @formatter:on
}

@Suppress("NOTHING_TO_INLINE")
private inline fun Parcel.writeTypeTag(tag: TypeTag) = writeByte(tag.value.toByte())

@Suppress("NOTHING_TO_INLINE")
private inline fun Parcel.readTypeTag(): TypeTag = TypeTag.entries[readByte().toInt()]

private inline fun <reified T : Any?> Parcel.writeAny(value: T?) {
    when (value) { // @formatter:off
        null -> writeTypeTag(TypeTag.NULL)
        is Byte -> {
            writeTypeTag(TypeTag.BYTE)
            writeByte(value)
        }
        is Short -> {
            writeTypeTag(TypeTag.SHORT)
            writeInt(value.toInt())
        }
        is Int -> {
            writeTypeTag(TypeTag.INT)
            writeInt(value)
        }
        is Long -> {
            writeTypeTag(TypeTag.LONG)
            writeLong(value)
        }
        is Float -> {
            writeTypeTag(TypeTag.FLOAT)
            writeFloat(value)
        }
        is Double -> {
            writeTypeTag(TypeTag.DOUBLE)
            writeDouble(value)
        }
        is Boolean -> {
            writeTypeTag(TypeTag.BOOLEAN)
            writeInt(if (value) 1 else 0)
        }
        is String -> {
            writeTypeTag(TypeTag.STRING)
            writeString(value)
        }
        is Parcelable -> {
            writeTypeTag(TypeTag.PARCELABLE)
            writeParcelable(value, 0)
        }
        else -> throw UnsupportedOperationException("Unsupported data type")
    } // @formatter:on
}

@Suppress("DEPRECATION")
private inline fun <reified T : Any?> Parcel.readAny(): T? {
    return when (readTypeTag()) {
        TypeTag.BYTE -> readNullable { readByte() }
        TypeTag.SHORT -> readNullable { readInt().toShort() }
        TypeTag.INT -> readNullable { readInt() }
        TypeTag.LONG -> readNullable { readLong() }
        TypeTag.FLOAT -> readNullable { readFloat() }
        TypeTag.DOUBLE -> readNullable { readDouble() }
        TypeTag.BOOLEAN -> readNullable { readInt() == 1 }
        TypeTag.STRING -> readNullable { readString() }
        TypeTag.PARCELABLE -> readNullable<T?> { readParcelable(ClassLoader.getSystemClassLoader()) }
        else -> null
    } as? T?
}

private inline fun <K, V> Parcel.writeMap(
    map: Map<K, V>,
    keyWriter: Parcel.(K) -> Unit,
    valueWriter: Parcel.(V) -> Unit
) {
    writeInt(map.size)
    for ((key, value) in map) {
        keyWriter(this, key)
        valueWriter(this, value)
    }
}

private inline fun <K, V> Parcel.readMap(
    keyReader: Parcel.() -> K?,
    valueReader: Parcel.() -> V?
): HashMap<K, V> {
    return HashMap<K, V>().apply {
        val size = readInt()
        for (i in 0..<size) {
            put(
                keyReader(this@readMap) ?: throw IllegalStateException("Could not read map key"),
                valueReader(this@readMap) ?: throw IllegalStateException("Could not read map value")
            )
        }
    }
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
        parcel.writeMap(notification.userData, Parcel::writeString, Parcel::writeAny)
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
                    userData = parcel.readMap(Parcel::readString, Parcel::readAny)
                )
            )
        }

        override fun newArray(size: Int): Array<ParcelableNotification?> {
            return arrayOfNulls(size)
        }
    }
}
