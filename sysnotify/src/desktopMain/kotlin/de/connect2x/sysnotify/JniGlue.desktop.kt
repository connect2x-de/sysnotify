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
import jni.jbyteArray
import jni.jclass
import jni.jint
import jni.jlong
import jni.jobject
import jni.jstring
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toCPointer
import kotlinx.coroutines.runBlocking

internal expect fun createNotificationHandler(
    name: String,
    id: String,
    activationCallback: (Notification) -> Unit,
    priority: NotificationPriority
): NotificationHandler

private val typeNativeNotificationHandler =
    JvmRefType("de.connect2x.sysnotify.NativeNotificationHandler")
private val typeNativeActivationCallback =
    JvmRefType("de.connect2x.sysnotify.NativeActivationCallback")
private val typeString = JvmRefType("java.lang.String")

internal fun registerNatives() {
    typeNativeNotificationHandler.jClass.apply {
        registerNativeFunction("create", staticCFunction(::create)) {
            parameter(typeString)
            parameter(typeString)
            parameter(typeNativeActivationCallback)
            returns(JvmBuiltinType.LONG)
        }
        registerNativeFunction("destroy", staticCFunction(::destroy)) {
            parameter(JvmBuiltinType.LONG)
        }
        registerNativeFunction("getPlatform", staticCFunction(::getPlatform)) {
            parameter(JvmBuiltinType.LONG)
            returns(JvmBuiltinType.INT)
        }
        registerNativeFunction("getCapabilities", staticCFunction(::getCapabilities)) {
            parameter(JvmBuiltinType.LONG)
            returns(JvmBuiltinType.INT)
        }
        registerNativeFunction("requestPermissions", staticCFunction(::requestPermissions)) {
            parameter(JvmBuiltinType.LONG)
        }
        registerNativeFunction("push", staticCFunction(::push)) {
            parameter(JvmBuiltinType.LONG)
            parameter(typeString)
            parameter(typeString)
            parameter(JvmBuiltinType.BYTE.array())
            parameter(JvmBuiltinType.INT)
            parameter(JvmBuiltinType.INT)
            returns(JvmBuiltinType.LONG)
        }
        registerNativeFunction("update", staticCFunction(::update)) {
            parameter(JvmBuiltinType.LONG)
            parameter(JvmBuiltinType.LONG)
            parameter(typeString)
            parameter(typeString)
            parameter(JvmBuiltinType.BYTE.array())
            parameter(JvmBuiltinType.INT)
            parameter(JvmBuiltinType.INT)
            returns(JvmBuiltinType.LONG)
        }
        registerNativeFunction("pop", staticCFunction(::pop)) {
            parameter(JvmBuiltinType.LONG)
            parameter(JvmBuiltinType.LONG)
        }
    }
}

private fun create(env: JNIEnvVar, clazz: jclass, name: jstring, id: jstring, activationCallback: jobject, priority: jint): jlong {
    return StableRef.create(createNotificationHandler(name.toKString(), id.toKString(), {
        // TODO: implement this
    }, NotificationPriority.entries[priority])).asCPointer().rawValue.toLong()
}

private fun destroy(env: JNIEnvVar, clazz: jclass, handler: jlong) {
    requireNotNull(handler.toCPointer<CPointed>())
        .asStableRef<NotificationHandler>()
        .dispose()
}

private fun getPlatform(env: JNIEnvVar, clazz: jclass, handler: jlong): jint {
    return requireNotNull(handler.toCPointer<CPointed>())
        .asStableRef<NotificationHandler>()
        .get().platform.ordinal
}

private fun getCapabilities(env: JNIEnvVar, clazz: jclass, handler: jlong): jint {
    return requireNotNull(handler.toCPointer<CPointed>())
        .asStableRef<NotificationHandler>()
        .get().capabilities.mask.toInt()
}

private fun requestPermissions(env: JNIEnvVar, clazz: jclass, handler: jlong) {
    runBlocking {
        requireNotNull(handler.toCPointer<CPointed>())
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
    icon: jbyteArray?,
    iconWidth: jint,
    iconHeight: jint
): jlong {
    return runBlocking {
        requireNotNull(handler.toCPointer<CPointed>())
            .asStableRef<NotificationHandler>()
            .get().push(
                Notification(
                    title = title?.toKString() ?: "Unknown",
                    description = description?.toKString(),
                    icon = icon?.let { NotificationIcon(it.toByteArray(), iconWidth, iconHeight) }
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
    icon: jbyteArray?,
    iconWidth: jint,
    iconHeight: jint
): jlong {
    val handle =
        requireNotNull(notification.toCPointer<CPointed>()).asStableRef<NotificationHandle>()
    val result = runBlocking {
        requireNotNull(handler.toCPointer<CPointed>())
            .asStableRef<NotificationHandler>()
            .get().update(
                handle.get(),
                Notification(
                    title = title?.toKString() ?: "Unknown",
                    description = description?.toKString(),
                    icon = icon?.let { NotificationIcon(it.toByteArray(), iconWidth, iconHeight) },
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
    val handle =
        requireNotNull(notification.toCPointer<CPointed>()).asStableRef<NotificationHandle>()
    runBlocking {
        requireNotNull(handler.toCPointer<CPointed>())
            .asStableRef<NotificationHandler>()
            .get().pop(handle.get())
        handle.dispose() // Make sure to dispose old handle
    }
}
