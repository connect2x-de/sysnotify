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

@file:OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)

package de.connect2x.sysnotify

import jni.jclass
import jni.jmethodID
import jni.jobject
import jni.jvalue
import kotlinx.cinterop.CArrayPointer
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.cValue
import kotlinx.cinterop.convert
import kotlinx.cinterop.cstr
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.sizeOf
import platform.posix.memcpy
import kotlin.experimental.ExperimentalNativeApi

internal data class JvmParameter(
    val name: String,
    val type: JvmType
)

/**
 * A small DSL builder class for constructing JNI signature strings
 * in a safe(r) manner than by hand.
 * See [JvmType], [JvmBuiltinType], [JvmRefType] and [JvmArrayType].
 */
internal class JvmSignatureBuilder {
    val parameters: ArrayList<JvmParameter> = ArrayList()
    var returnType: JvmType = JvmBuiltinType.VOID
        internal set

    infix fun JvmType.named(name: String) {
        parameters += JvmParameter(name, this)
    }

    fun returns(type: JvmType): JvmSignatureBuilder {
        returnType = type
        return this
    }
}

internal data class JvmSignature(
    val parameters: ArrayList<JvmParameter>,
    val returnType: JvmType,
    val signatureString: String,
) {
    companion object {
        inline fun of(scope: JvmSignatureBuilder.() -> Unit): JvmSignature {
            val signatureBuilder = JvmSignatureBuilder().apply(scope)
            val builder = StringBuilder()
            builder.append('(')
            for (param in signatureBuilder.parameters) {
                builder.append(param.type.toSignatureString())
            }
            builder.append(')')
            builder.append(signatureBuilder.returnType.toSignatureString())
            return JvmSignature(signatureBuilder.parameters, signatureBuilder.returnType, builder.toString())
        }
    }
}

internal value class JvmArgumentBuffer(private val values: HashMap<String, CValue<jvalue>> = HashMap()) {
    fun put(name: String, value: Byte) {
        values[name] = cValue<jvalue> { b = value }
    }

    fun put(name: String, value: Short) {
        values[name] = cValue<jvalue> { s = value }
    }

    fun put(name: String, value: Int) {
        values[name] = cValue<jvalue> { i = value }
    }

    fun put(name: String, value: Long) {
        values[name] = cValue<jvalue> { j = value }
    }

    fun put(name: String, value: Float) {
        values[name] = cValue<jvalue> { f = value }
    }

    fun put(name: String, value: Double) {
        values[name] = cValue<jvalue> { d = value }
    }

    fun put(name: String, value: Boolean) {
        values[name] = cValue<jvalue> { z = value.toJBoolean() }
    }

    fun put(name: String, value: jobject) {
        values[name] = cValue<jvalue> { l = value }
    }

    fun MemScope.associate(signature: JvmSignature): CArrayPointer<jvalue> {
        val sequentialParams = signature.parameters
        check(sequentialParams.size == values.size) { "Mismatched parameter count" }
        return allocArray(values.size) { index ->
            val param = requireNotNull(sequentialParams.getOrNull(index)) { "No parameter for index $index" }
            val name = param.name
            val value = requireNotNull(values[name]) { "No value for parameter $name ($index)" }
            memcpy(ptr, value.ptr, sizeOf<jvalue>().convert())
        }
    }
}

// Call functions may be used within the jvmEnvironment scope
internal data class JvmMethod(
    val signature: JvmSignature,
    val handle: jmethodID,
    val owner: jclass,
    val isStatic: Boolean,
) {
    fun call(
        instance: jobject? = null,
        argBuffer: JvmArgumentBuffer.() -> Unit
    ) = jvmEnvironment {
        memScoped {
            val args = JvmArgumentBuffer().apply(argBuffer)
            val argsBaseAddress = with(args) { associate(signature) }
            val env = requireNotNull(pointed) { "Could not access JNI environment" }
            if (isStatic) {
                requireNotNull(env.CallStaticVoidMethodA) {
                    "Could not access JNIEnv::CallStaticVoidMethodA"
                }(ptr, owner, handle, argsBaseAddress)
            } else {
                require(instance != null) { "Calling non-static method requires valid instance" }
                requireNotNull(env.CallVoidMethodA) {
                    "Could not access JNIEnv::CallVoidMethodA"
                }(ptr, instance, handle, argsBaseAddress)
            }
        }
    }
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun jclass.getMethod(
    name: String,
    isStatic: Boolean = false,
    signature: JvmSignatureBuilder.() -> Unit
): JvmMethod {
    val signatureData = JvmSignature.of(signature)
    val handle = jvmEnvironment {
        memScoped {
            requireNotNull(
                if (isStatic) requireNotNull(pointed?.GetStaticMethodID)(
                    this@jvmEnvironment.ptr,
                    this@getMethod,
                    name.cstr.ptr,
                    signatureData.signatureString.cstr.ptr
                )
                else requireNotNull(pointed?.GetMethodID)(
                    this@jvmEnvironment.ptr,
                    this@getMethod,
                    name.cstr.ptr,
                    signatureData.signatureString.cstr.ptr
                )
            ) { "Could not find method $name${signatureData.signatureString}" }
        }
    }
    return JvmMethod(signatureData, handle, this, isStatic)
}
