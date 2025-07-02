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

import jni.JNIEnvVar
import jni.JNINativeMethod
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
import kotlinx.cinterop.allocPointerTo
import kotlinx.cinterop.cstr
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKStringFromUtf16
import kotlinx.cinterop.usePinned
import platform.posix.memcpy
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.concurrent.ThreadLocal

private lateinit var virtualMachine: JavaVMVar

@ThreadLocal
private var environment: JNIEnvVar? = null

@Suppress("UNUSED_PARAMETER", "UNUSED")
@CName("JNI_OnLoad")
fun jniOnLoad(vm: JavaVMVar, reserved: COpaquePointer): jint {
    virtualMachine = vm
    registerNatives()
    return JNI_VERSION_1_8
}

/**
 * This functions allows using the JNI environment in any thread context.
 * If the current thread is not attached to the current VM instance, it is
 * done so temporarily. When the scope is left, the thread is automatically detached.
 */
internal inline fun <reified R> jvmEnvironment(
    scope: JNIEnvVar.() -> R
): R {
    return memScoped {
        if (environment != null) {
            return environment!!.scope()
        }
        val vm = requireNotNull(virtualMachine.pointed)
        val localEnv = allocPointerTo<JNIEnvVar>()
        require(
            requireNotNull(vm.AttachCurrentThread)(
                virtualMachine.ptr,
                localEnv.ptr.reinterpret(),
                null
            ) == JNI_OK
        )
        environment = requireNotNull(localEnv.pointed)
        val result = requireNotNull(environment).scope()
        environment = null
        requireNotNull(vm.DetachCurrentThread)(virtualMachine.ptr)
        result
    }
}

internal inline fun <reified O : jobject> O.newGlobalRef(): O {
    return jvmEnvironment {
        requireNotNull(pointed?.NewGlobalRef)(ptr, this@newGlobalRef) as O
    }
}

internal inline fun <reified O : jobject> O.deleteGlobalRef() {
    jvmEnvironment {
        requireNotNull(pointed?.DeleteGlobalRef)(ptr, this@deleteGlobalRef)
    }
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun Boolean.toJBoolean(): jboolean = if (this) 1U else 0U

@Suppress("NOTHING_TO_INLINE")
internal inline fun jboolean.toKBoolean(): Boolean = this == 1U.toUByte()

@Suppress("NOTHING_TO_INLINE")
internal inline fun jstring.toKString(): String {
    return jvmEnvironment {
        val data = requireNotNull(
            requireNotNull(pointed?.GetStringCritical) { "Could not access JNI:GetStringUTFChars function" }(
                ptr,
                this@toKString,
                null
            )
        ) { "Could not retrieve string base address" }
        val result = data.toKStringFromUtf16()
        requireNotNull(pointed?.ReleaseStringCritical) { "Could not access JNI:ReleaseStringUTFChars function" }(
            ptr,
            this@toKString,
            data
        )
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
                val data =
                    requireNotNull(pointed?.GetByteArrayElements) { "Could not access JNI:GetPrimitiveArrayCritical function" }(
                        this@jvmEnvironment.ptr,
                        this@toByteArray.reinterpret(),
                        null
                    )
                memcpy(pinnedArray.addressOf(0), data, size.toULong())
                requireNotNull(pointed?.ReleaseByteArrayElements) { "Could not access JNI:ReleasePrimitiveArrayCritical function" }(
                    this@jvmEnvironment.ptr,
                    this@toByteArray.reinterpret(),
                    data,
                    0
                )
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
        requireNotNull(pointed?.RegisterNatives) { "Could not access JNI:RegisterNatives function" }(
            ptr,
            this@registerNativeFunction,
            method.ptr,
            1
        )
    }
}

internal inline val JvmRefType.jClass: jclass
    get() = jvmEnvironment {
        memScoped {
            requireNotNull(
                requireNotNull(pointed?.FindClass) { "Could not access JNIEnv::FindClass" }(
                    this@jvmEnvironment.ptr,
                    objectName.cstr.ptr
                )
            ) { "Could not find class '$typeName'" }
        }
    }
