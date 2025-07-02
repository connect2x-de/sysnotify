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

@file:OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class, ExperimentalAtomicApi::class)

package de.connect2x.sysnotify

import jni.JNIEnvVar
import jni.JNINativeMethod
import jni.JNI_EDETACHED
import jni.JNI_EVERSION
import jni.JNI_OK
import jni.JNI_VERSION_1_8
import jni.JavaVMVar
import jni.jboolean
import jni.jbyteArray
import jni.jclass
import jni.jint
import jni.jobject
import jni.jstring
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.cstr
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKStringFromUtf16
import kotlinx.cinterop.usePinned
import platform.posix.memcpy
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.experimental.ExperimentalNativeApi

private const val JNI_VERSION: Int = JNI_VERSION_1_8
private lateinit var virtualMachine: JavaVMVar

@Suppress("UNUSED_PARAMETER", "UNUSED")
@CName("JNI_OnLoad")
fun jniOnLoad(vm: JavaVMVar, reserved: COpaquePointer): jint {
    virtualMachine = vm
    registerNatives()
    return JNI_VERSION
}

internal inline fun <reified R> jvmEnvironment(
    scope: JNIEnvVar.() -> R
): R = memScoped {
    val getEnv = requireNotNull(virtualMachine.pointed?.GetEnv) { "Could not get JNI:GetEnv function" }
    val environment = alloc<JNIEnvVar>()
    when (getEnv(virtualMachine.ptr, environment.ptr.reinterpret(), JNI_VERSION)) {
        JNI_OK -> environment.scope()
        JNI_EDETACHED -> {
            val attachThread = requireNotNull(virtualMachine.pointed?.AttachCurrentThread) {
                "Could not get JNI:AttachCurrentThread function"
            }
            if (attachThread(virtualMachine.ptr, environment.ptr.reinterpret(), null) != JNI_OK) {
                error("Could not attach current thread to JVM instance")
            }
            val detachThread = requireNotNull(virtualMachine.pointed?.DetachCurrentThread) {
                "Could not get JNI:DetachCurrentThread function"
            }
            detachThread(virtualMachine.ptr)
            environment.scope()
        }

        JNI_EVERSION -> error("Could not retrieve JNI environment: invalid JNI version")
        else -> error("Could not retrieve JNI environment: unknown error")
    }
}

internal inline fun <reified O : jobject> O.newGlobalRef(): O = jvmEnvironment {
    val function = requireNotNull(pointed?.NewGlobalRef) {
        "Could not access JNI:NewGlobalRef"
    }
    function(ptr, this@newGlobalRef) as O
}

internal inline fun <reified O : jobject> O.deleteGlobalRef() = jvmEnvironment {
    val function = requireNotNull(pointed?.DeleteGlobalRef) {
        "Could not access JNI:DeleteGlobalRef"
    }
    function(ptr, this@deleteGlobalRef)
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun Boolean.toJBoolean(): jboolean = if (this) 1U else 0U

@Suppress("NOTHING_TO_INLINE")
internal inline fun jboolean.toKBoolean(): Boolean = this == 1U.toUByte()

@Suppress("NOTHING_TO_INLINE")
internal inline fun jstring.toKString(): String {
    return jvmEnvironment {
        val acquireString = requireNotNull(pointed?.GetStringCritical) {
            "Could not access JNI:GetStringUTFChars function"
        }
        val data = requireNotNull(acquireString(ptr, this@toKString, null)) {
            "Could not retrieve string base address"
        }
        val result = data.toKStringFromUtf16()
        val releaseString = requireNotNull(pointed?.ReleaseStringCritical) {
            "Could not access JNI:ReleaseStringUTFChars function"
        }
        releaseString(ptr, this@toKString, data)
        result
    }
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun jbyteArray.toByteArray(): ByteArray {
    return jvmEnvironment {
        val size = requireNotNull(pointed?.GetArrayLength)(
            ptr,
            this@toByteArray
        )
        ByteArray(size).apply {
            usePinned { pinnedArray ->
                val acquireElements = requireNotNull(pointed?.GetByteArrayElements) {
                    "Could not access JNI:GetPrimitiveArrayCritical function"
                }
                val data = acquireElements(this@jvmEnvironment.ptr, this@toByteArray.reinterpret(), null)
                memcpy(pinnedArray.addressOf(0), data, size.toULong())
                val releaseElements = requireNotNull(pointed?.ReleaseByteArrayElements) {
                    "Could not access JNI:ReleasePrimitiveArrayCritical function"
                }
                releaseElements(this@jvmEnvironment.ptr, this@toByteArray.reinterpret(), data, 0)
            }
        }
    }
}

internal inline fun jclass.registerNativeFunction(
    name: String,
    address: COpaquePointer,
    signature: JvmSignatureBuilder.() -> Unit = {}
) = memScoped {
    val method = alloc<JNINativeMethod>()
    method.name = name.cstr.ptr
    method.signature = JvmSignature.of(signature).signatureString.cstr.ptr
    method.fnPtr = address
    jvmEnvironment {
        val registerNatives = requireNotNull(pointed?.RegisterNatives) {
            "Could not access JNI:RegisterNatives function"
        }
        registerNatives(ptr, this@registerNativeFunction, method.ptr, 1)
    }
}

internal inline val JvmRefType.jClass: jclass
    get() = jvmEnvironment {
        memScoped {
            val findClass = requireNotNull(pointed?.FindClass) { "Could not access JNIEnv::FindClass" }
            requireNotNull(findClass(this@jvmEnvironment.ptr, objectName.cstr.ptr)) {
                "Could not find class '$typeName'"
            }
        }
    }
