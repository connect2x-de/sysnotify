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

@file:OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
@file:Suppress("NOTHING_TO_INLINE")

package de.connect2x.sysnotify

import jni.JNIEnvVar
import jni.JNINativeMethod
import jni.JNI_ERR
import jni.JNI_VERSION_1_8
import jni.JavaVMVar
import jni.jboolean
import jni.jbyteArray
import jni.jclass
import jni.jint
import jni.jstring
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.allocPointerTo
import kotlinx.cinterop.convert
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import platform.posix.memcpy
import kotlin.experimental.ExperimentalNativeApi

internal lateinit var environment: JNIEnvVar

@Suppress("UNUSED_PARAMETER", "UNUSED")
@CName("JNI_OnLoad")
fun jniOnLoad(vm: JavaVMVar, reserved: COpaquePointer): jint {
    return vm.pointed?.let {
        memScoped {
            val address = allocPointerTo<JNIEnvVar>()
            checkNotNull(it.GetEnv)(
                vm.ptr,
                address.ptr.reinterpret(),
                JNI_VERSION_1_8
            )
            environment = checkNotNull(address.pointed)
        }
        registerNatives()
        return@let JNI_VERSION_1_8
    } ?: JNI_ERR
}

internal inline fun Boolean.toJBoolean(): jboolean = if (this) 1U else 0U

internal inline fun jstring.toKString(): String {
    val data = checkNotNull(
        checkNotNull(environment.pointed?.GetStringUTFChars) { "Could not access JNI:GetStringUTFChars function" }(
            environment.ptr,
            this,
            null
        )
    ) { "Could not retrieve string base address" }
    val result = data.toKString()
    checkNotNull(environment.pointed?.ReleaseStringUTFChars) { "Could not access JNI:ReleaseStringUTFChars function" }(
        environment.ptr,
        this,
        data
    )
    return result
}

internal inline fun jbyteArray.toByteArray(): ByteArray {
    return checkNotNull(environment.pointed?.let {
        ByteArray(checkNotNull(it.GetArrayLength)(environment.ptr, this)).apply {
            usePinned { pinnedArray ->
                val data =
                    checkNotNull(
                        checkNotNull(it.GetPrimitiveArrayCritical) { "Could not access JNI:GetPrimitiveArrayCritical function" }(
                            environment.ptr,
                            reinterpret(),
                            null
                        )
                    ) { "Could not retrieve array base address" }
                memcpy(pinnedArray.addressOf(0), data, size.convert())
                checkNotNull(it.ReleasePrimitiveArrayCritical) { "Could not access JNI:ReleasePrimitiveArrayCritical function" }(
                    environment.ptr,
                    reinterpret(),
                    data,
                    0
                )
            }
        }
    })
}

internal inline fun MemScope.allocCString(value: String?): CPointer<ByteVar>? {
    if(value == null) return null
    val length = value.length
    val data = allocArray<ByteVar>(length + 1)
    data[length] = 0 // Make sure we have a null-terminator at the end
    value.encodeToByteArray().usePinned { array ->
        memcpy(data, array.addressOf(0), length.convert())
    }
    return data
}

internal inline fun jclass.registerNativeFunction(
    name: String,
    signature: String,
    address: COpaquePointer
) {
    memScoped {
        val method = alloc<JNINativeMethod>()
        method.name = allocCString(name)
        method.signature = allocCString(signature)
        method.fnPtr = address
        checkNotNull(environment.pointed?.RegisterNatives) { "Could not access JNI:RegisterNatives function" }(
            environment.ptr,
            this@registerNativeFunction,
            method.ptr,
            1
        )
    }
}
