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

import sun.misc.Unsafe
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout

internal interface Memory {
    companion object {
        val instance: Memory by lazy { UnsafeMemory.create() ?: ForeignMemory() }
    }

    val systemBitness: Long
    val pointerSize: Long

    fun getPointer(address: Long): Long
    fun getByte(address: Long): Byte
    fun getInt(address: Long): Int

    fun getByteArray(address: Long, size: Int): ByteArray {
        return ByteArray(size) { getByte(address + it) }
    }

    fun getBoolean(address: Long): Boolean {
        return getInt(address) == 1
    }

    fun getStringLength(address: Long): Long {
        if (address == 0L) return 0L
        var currentAddress = address
        while (getByte(currentAddress) != 0.toByte()) {
            ++currentAddress
        }
        return currentAddress - address
    }

    fun getString(address: Long): String? {
        return if (address == 0L) null
        else ByteArray(getStringLength(address).toInt()) {
            getByte(address + it)
        }.decodeToString()
    }
}

internal class UnsafeMemory private constructor(private val unsafe: Any) : Memory {
    companion object {
        @Suppress("DiscouragedPrivateApi")
        fun create(): UnsafeMemory? {
            return try {
                Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe").run {
                    isAccessible = true
                    val instance = get(null) ?: return null
                    isAccessible = false
                    UnsafeMemory(instance)
                }
            } catch (error: Throwable) {
                null
            }
        }
    }

    override val systemBitness: Long = System.getProperty("sun.arch.data.model").toLong()
    override val pointerSize: Long = systemBitness shr 3

    override fun getPointer(address: Long): Long {
        return (unsafe as Unsafe).getAddress(address)
    }

    override fun getByte(address: Long): Byte {
        return (unsafe as Unsafe).getByte(address)
    }

    override fun getInt(address: Long): Int {
        return (unsafe as Unsafe).getInt(address)
    }
}

internal class ForeignMemory : Memory {
    override val systemBitness: Long = System.getProperty("sun.arch.data.model").toLong()
    override val pointerSize: Long = systemBitness shr 3

    override fun getPointer(address: Long): Long {
        return MemorySegment.ofAddress(address).asSlice(0, pointerSize).get(ValueLayout.ADDRESS_UNALIGNED, 0L).address()
    }

    override fun getByte(address: Long): Byte {
        return MemorySegment.ofAddress(address).asSlice(0, Byte.SIZE_BYTES.toLong()).get(ValueLayout.JAVA_BYTE, 0L)
    }

    override fun getInt(address: Long): Int {
        return MemorySegment.ofAddress(address).asSlice(0, Int.SIZE_BYTES.toLong())
            .get(ValueLayout.JAVA_INT_UNALIGNED, 0L)
    }
}
