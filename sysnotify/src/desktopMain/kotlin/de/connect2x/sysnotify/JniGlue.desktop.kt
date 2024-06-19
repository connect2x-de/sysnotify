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

@file:OptIn(ExperimentalForeignApi::class)
@file:Suppress("UNUSED_PARAMETER")

package de.connect2x.sysnotify

import jni.JNIEnvVar
import jni.jboolean
import jni.jbyteArray
import jni.jclass
import jni.jint
import jni.jlong
import jni.jstring
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toCPointer
import kotlinx.coroutines.runBlocking

internal expect fun createNotificationHandler(name: String): NotificationHandler

internal fun registerNatives() {
    memScoped {
        val env = checkNotNull(environment.pointed) { "Could not access JNI environment" }
        checkNotNull(
            checkNotNull(env.FindClass) { "Could not access JNI:FindClass function" }(
                environment.ptr,
                allocCString("de/connect2x/sysnotify/NativeNotificationHandler")
            )
        ) { "Could not find class de.connect2x.sysnotify.NativeNotificationHandler" }
    }.apply {
        registerNativeFunction("create", "(Ljava/lang/String;)J", staticCFunction(::create))
        registerNativeFunction("destroy", "(J)V", staticCFunction(::destroy))
        registerNativeFunction("getPlatform", "(J)I", staticCFunction(::getPlatform))
        registerNativeFunction("isTitleSupported", "(J)Z", staticCFunction(::isTitleSupported))
        registerNativeFunction(
            "isDescriptionSupported",
            "(J)Z",
            staticCFunction(::isDescriptionSupported)
        )
        registerNativeFunction("isIconSupported", "(J)Z", staticCFunction(::isIconSupported))
        registerNativeFunction("isActionSupported", "(J)Z", staticCFunction(::isActionSupported))
        registerNativeFunction("isPopSupported", "(J)Z", staticCFunction(::isPopSupported))
        registerNativeFunction("requestPermissions", "(J)V", staticCFunction(::requestPermissions))
        registerNativeFunction(
            "push",
            "(JLjava/lang/String;Ljava/lang/String;[B)J",
            staticCFunction(::push)
        )
        registerNativeFunction(
            "update",
            "(JJLjava/lang/String;Ljava/lang/String;[B)J",
            staticCFunction(::update)
        )
        registerNativeFunction("pop", "(JJ)V", staticCFunction(::pop))
    }
}

private fun create(env: JNIEnvVar, clazz: jclass, name: jstring): jlong {
    return StableRef.create(createNotificationHandler(name.toKString()))
        .asCPointer().rawValue.toLong()
}

private fun destroy(env: JNIEnvVar, clazz: jclass, handler: jlong) {
    checkNotNull(handler.toCPointer<CPointed>())
        .asStableRef<NotificationHandler>()
        .dispose()
}

private fun getPlatform(env: JNIEnvVar, clazz: jclass, handler: jlong): jint {
    return checkNotNull(handler.toCPointer<CPointed>())
        .asStableRef<NotificationHandler>()
        .get().platform.ordinal
}

private fun isTitleSupported(env: JNIEnvVar, clazz: jclass, handler: jlong): jboolean {
    return checkNotNull(handler.toCPointer<CPointed>())
        .asStableRef<NotificationHandler>()
        .get().capabilities.isTitleSupported.toJBoolean()
}

private fun isDescriptionSupported(env: JNIEnvVar, clazz: jclass, handler: jlong): jboolean {
    return checkNotNull(handler.toCPointer<CPointed>())
        .asStableRef<NotificationHandler>()
        .get().capabilities.isDescriptionSupported.toJBoolean()
}

private fun isIconSupported(env: JNIEnvVar, clazz: jclass, handler: jlong): jboolean {
    return checkNotNull(handler.toCPointer<CPointed>())
        .asStableRef<NotificationHandler>()
        .get().capabilities.isIconSupported.toJBoolean()
}

private fun isActionSupported(env: JNIEnvVar, clazz: jclass, handler: jlong): jboolean {
    return checkNotNull(handler.toCPointer<CPointed>())
        .asStableRef<NotificationHandler>()
        .get().capabilities.isActionSupported.toJBoolean()
}

private fun isPopSupported(env: JNIEnvVar, clazz: jclass, handler: jlong): jboolean {
    return checkNotNull(handler.toCPointer<CPointed>())
        .asStableRef<NotificationHandler>()
        .get().capabilities.isPopSupported.toJBoolean()
}

private fun requestPermissions(env: JNIEnvVar, clazz: jclass, handler: jlong) {
    runBlocking {
        checkNotNull(handler.toCPointer<CPointed>())
            .asStableRef<NotificationHandler>()
            .get().requestPermissions()
    }
}

private fun push(
    env: JNIEnvVar,
    clazz: jclass,
    handler: jlong,
    title: jstring?,
    description: jstring?,
    icon: jbyteArray?
): jlong {
    return runBlocking {
        checkNotNull(handler.toCPointer<CPointed>())
            .asStableRef<NotificationHandler>()
            .get().push(
                Notification(
                    title = title?.toKString() ?: "Unknown",
                    description = description?.toKString(),
                    icon = icon?.toByteArray()
                )
            )
    }?.let { StableRef.create(it).asCPointer().rawValue.toLong() } ?: 0
}

private fun update(
    env: JNIEnvVar,
    clazz: jclass,
    handler: jlong,
    notification: jlong,
    title: jstring?,
    description: jstring?,
    icon: jbyteArray?
): jlong {
    val handle = checkNotNull(notification.toCPointer<CPointed>()).asStableRef<NotificationHandle>()
    val result = runBlocking {
        checkNotNull(handler.toCPointer<CPointed>())
            .asStableRef<NotificationHandler>()
            .get().update(
                handle.get(),
                Notification(
                    title = title?.toKString() ?: "Unknown",
                    description = description?.toKString(),
                    icon = icon?.toByteArray()
                )
            )
    }?.let { StableRef.create(it).asCPointer().rawValue.toLong() } ?: 0
    handle.dispose()
    return result
}

private fun pop(
    env: JNIEnvVar,
    clazz: jclass,
    handler: jlong,
    notification: jlong
) {
    val handle = checkNotNull(notification.toCPointer<CPointed>()).asStableRef<NotificationHandle>()
    runBlocking {
        checkNotNull(handler.toCPointer<CPointed>())
            .asStableRef<NotificationHandler>()
            .get().pop(handle.get())
        handle.dispose() // Make sure to dispose old handle
    }
}
