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

import kotlin.jvm.JvmInline

internal enum class ColorChannel {
    R, G, B, A;

    val offset: Int = ordinal shl 3 // index * 8
}

@JvmInline
internal value class PackedColor(val value: UInt) {
    constructor(r: UByte, g: UByte, b: UByte, a: UByte) : this( // @formatter:off
        (r.toUInt() shl 24) or
        (g.toUInt() shl 16) or
        (b.toUInt() shl 8) or
        a.toUInt()
    ) // @formatter:on

    val r: UByte
        get() = ((value shr 24) and 0xFFU).toUByte()
    val g: UByte
        get() = ((value shr 16) and 0xFFU).toUByte()
    val b: UByte
        get() = ((value shr 8) and 0xFFU).toUByte()
    val a: UByte
        get() = (value and 0xFFU).toUByte()

    /**
     * Swizzles the color components of the given unsigned packed color.
     * This allows swapping color channels to convert between different
     * packings of RGB(A).
     *
     * @param swizzleR The channel (in RGBA order) to which the red channel is mapped.
     * @param swizzleG The channel (in RGBA order) to which the green channel is mapped.
     * @param swizzleB The channel (in RGBA order) to which the blue channel is mapped.
     * @param swizzleA The channel (in RGBA order) to which the alpha channel is mapped.
     * @return The original color value repacked in the channel ordering described by
     *  the mapping parameters of this function.
     */
    internal fun swizzle(
        swizzleR: ColorChannel,
        swizzleG: ColorChannel,
        swizzleB: ColorChannel,
        swizzleA: ColorChannel,
    ): PackedColor {
        if (swizzleR == ColorChannel.R
            && swizzleG == ColorChannel.G
            && swizzleB == ColorChannel.B
            && swizzleA == ColorChannel.A
        ) {
            return this // No re-ordering has to be performed
        }
        return PackedColor( // @formatter:off
            (((value shr 24) and 0xFFU) shl swizzleR.offset) or
            (((value shr 16) and 0xFFU) shl swizzleG.offset) or
            (((value shr 8) and 0xFFU) shl swizzleB.offset) or
            ((value and 0xFFU) shl swizzleA.offset)
        ) // @formatter:on
    }
}
