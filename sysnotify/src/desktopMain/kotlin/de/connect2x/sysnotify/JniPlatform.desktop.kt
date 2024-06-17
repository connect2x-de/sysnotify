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

package de.connect2x.sysnotify

import jni.JNIEnvVar
import jni.JNI_ERR
import jni.JNI_VERSION_1_8
import jni.JavaVMVar
import jni.jboolean
import jni.jbyteArray
import jni.jint
import jni.jstring
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.invoke
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKStringFromUtf8
import kotlinx.cinterop.usePinned
import platform.posix.memcpy
import kotlin.experimental.ExperimentalNativeApi

internal lateinit var environment: JNIEnvVar

@Suppress("UNUSED_PARAMETER", "UNUSED")
@CName("JNI_OnLoad")
fun jniOnLoad(vm: JavaVMVar, reserved: COpaquePointer): jint {
    return vm.pointed?.let {
        checkNotNull(it.GetEnv)(
            vm.ptr,
            environment.ptr.reinterpret(),
            JNI_VERSION_1_8
        )
        registerNatives()
        return@let JNI_VERSION_1_8
    } ?: JNI_ERR
}

@Suppress("UNUSED_PARAMETER", "UNUSED")
@CName("JNI_OnUnload")
fun jniOnUnload(vm: JavaVMVar, reserved: COpaquePointer) {
}

@Suppress("NOTHING_TO_INLINE") // Because of Kotlin/JVM
internal inline fun Boolean.toJBoolean(): jboolean = if (this) 1U else 0U

@Suppress("NOTHING_TO_INLINE") // Because of Kotlin/JVM
internal inline fun jstring.toKString(): String {
    return checkNotNull(environment.pointed?.let {
        val data = checkNotNull(it.GetStringUTFChars)(environment.ptr, this, null)
        val result = data?.toKStringFromUtf8() ?: return ""
        checkNotNull(it.ReleaseStringUTFChars)(environment.ptr, this, data)
        result
    })
}

@Suppress("NOTHING_TO_INLINE") // Because of Kotlin/JVM
internal inline fun jbyteArray.toByteArray(): ByteArray {
    return checkNotNull(environment.pointed?.let {
        ByteArray(checkNotNull(it.GetArrayLength)(environment.ptr, this)).apply {
            usePinned { pinnedArray ->
                val data = checkNotNull(it.GetPrimitiveArrayCritical)(environment.ptr, reinterpret(), null)
                    ?: throw IllegalStateException("Could not retrieve array base address")
                memcpy(pinnedArray.addressOf(0), data, size.convert())
                checkNotNull(it.ReleasePrimitiveArrayCritical)(environment.ptr, reinterpret(), data, 0)
            }
        }
    })
}
