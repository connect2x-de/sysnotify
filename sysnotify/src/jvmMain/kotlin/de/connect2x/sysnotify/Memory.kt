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

import jdk.incubator.foreign.MemoryAccess
import jdk.incubator.foreign.MemoryAddress
import jdk.incubator.foreign.MemorySegment
import jdk.incubator.foreign.ResourceScope

private val memoryInstance: Memory by lazy { ForeignMemory() }

internal interface Memory {
    companion object : Memory by memoryInstance

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

internal class ForeignMemory : Memory {
    override val systemBitness: Long = System.getProperty("sun.arch.data.model").toLong()
    override val pointerSize: Long = systemBitness shr 3

    private fun Long.asGlobalSegment(size: Long): MemorySegment {
        return MemoryAddress.ofLong(this).asSegment(size, ResourceScope.globalScope())
    }

    override fun getPointer(address: Long): Long {
        return MemoryAccess.getAddress(address.asGlobalSegment(pointerSize)).toRawLongValue()
    }

    override fun getByte(address: Long): Byte {
        return MemoryAccess.getByte(address.asGlobalSegment(Byte.SIZE_BYTES.toLong()))
    }

    override fun getInt(address: Long): Int {
        return MemoryAccess.getInt(address.asGlobalSegment(Int.SIZE_BYTES.toLong()))
    }
}
