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

internal interface JvmType {
    val typeName: String
    val objectName: String
        get() = typeName

    fun toSignatureString(): String = typeName
    fun array(depth: Int = 1): JvmArrayType = JvmArrayType(this, depth)
}

internal data class JvmRefType(override val typeName: String) : JvmType {
    override val objectName: String = typeName.replace('.', '/')

    override fun toSignatureString(): String {
        return "L$objectName;"
    }
}

internal enum class JvmBuiltinType(private val signature: String) : JvmType {
    // @formatter:off
    BYTE    ("B"),
    SHORT   ("S"),
    INT     ("I"),
    LONG    ("J"),
    FLOAT   ("F"),
    DOUBLE  ("D"),
    BOOLEAN ("Z"),
    CHAR    ("C"),
    VOID    ("V");
    // @formatter:on

    override val typeName: String = name.lowercase()

    override fun toSignatureString(): String = signature
}

internal data class JvmArrayType(private val type: JvmType, private val depth: Int = 1) : JvmType {
    override val typeName: String = type.typeName

    override fun toSignatureString(): String {
        val builder = StringBuilder()
        for (i in 0 until depth) builder.append('[')
        builder.append(type.toSignatureString())
        return builder.toString()
    }
}
