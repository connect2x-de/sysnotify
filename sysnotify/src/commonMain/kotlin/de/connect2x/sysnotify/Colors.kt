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

@file:Suppress("NOTHING_TO_INLINE")

package de.connect2x.sysnotify

enum class ColorFormat(
    val red: Int,
    val green: Int,
    val blue: Int,
    val alpha: Int,
) {
    RGBA(red = 24, green = 16, blue = 8, alpha = 0),
    ARGB(red = 16, green = 8, blue = 0, alpha = 24)
}

inline fun unpackRed(from: ColorFormat, input: UInt): UByte = input.shr(from.red).toUByte()
inline fun unpackGreen(from: ColorFormat, input: UInt): UByte = input.shr(from.green).toUByte()
inline fun unpackBlue(from: ColorFormat, input: UInt): UByte = input.shr(from.blue).toUByte()
inline fun unpackAlpha(from: ColorFormat, input: UInt): UByte = input.shr(from.alpha).toUByte()

inline fun pack(to: ColorFormat, red: UByte, green: UByte, blue: UByte, alpha: UByte): UInt {
    return red.toUInt().shl(to.red) or
            green.toUInt().shl(to.green) or
            blue.toUInt().shl(to.blue) or
            alpha.toUInt().shl(to.alpha)
}

inline fun swizzle(from: ColorFormat, to: ColorFormat, input: UInt): UInt {
    return pack(
        to,
        unpackRed(from, input),
        unpackGreen(from, input),
        unpackBlue(from, input),
        unpackAlpha(from, input)
    )
}
