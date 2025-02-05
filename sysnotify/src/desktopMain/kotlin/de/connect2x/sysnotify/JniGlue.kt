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

import de.connect2x.sysnotify.native.SNNotificationData
import jni.JNIEnvVar
import jni.jboolean
import jni.jbyteArray
import jni.jclass
import jni.jint
import jni.jlong
import jni.jobject
import jni.jstring
import jni.jvalue
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.cstr
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toCPointer
import kotlinx.coroutines.runBlocking

internal expect fun createNotificationHandler(
    name: String,
    id: String,
    activationCallback: (Notification) -> Unit,
    priority: NotificationPriority,
    closeCallback: () -> Unit = {}
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
            parameter(JvmBuiltinType.INT)
            parameter(typeNativeActivationCallback)
            returns(JvmBuiltinType.LONG)
        }
        registerNativeFunction("hasPermissions", staticCFunction(::hasPermissions)) {
            parameter(JvmBuiltinType.LONG)
            returns(JvmBuiltinType.BOOLEAN)
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
            parameter(JvmBuiltinType.BOOLEAN)
            parameter(typeString)
            parameter(typeString)
            parameter(typeString)
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
            parameter(typeString)
            parameter(typeString)
            returns(JvmBuiltinType.LONG)
        }
        registerNativeFunction("pop", staticCFunction(::pop)) {
            parameter(JvmBuiltinType.LONG)
            parameter(JvmBuiltinType.LONG)
        }
    }
}

private fun create(
    env: JNIEnvVar,
    clazz: jclass,
    name: jstring,
    id: jstring,
    priority: jint,
    activationCallback: jobject
): jlong {
    val globalCallback = activationCallback.newGlobalRef()
    return StableRef.create(createNotificationHandler(name.toKString(), id.toKString(), { notification ->
        memScoped {
            jvmEnvironment {
                val data = alloc<SNNotificationData> {
                    title = notification.title?.cstr?.ptr
                    description = notification.description?.cstr?.ptr
                    icon_data = notification.icon?.let { allocArrayOf(it.data) }
                    icon_width = notification.icon?.width ?: 0
                    icon_height = notification.icon?.height ?: 0
                    callback_data = notification.callbackData?.cstr?.ptr
                    dismissible = if (notification.dismissible) 1 else 0
                    this.priority = notification.priority.ordinal
                    play_sound = if (notification.playSound) 1 else 0
                    sound_path = notification.soundPath?.cstr?.ptr
                    group = notification.group?.cstr?.ptr
                }
                val method = typeNativeActivationCallback.jClass.getMethod("invoke") {
                    parameter(JvmBuiltinType.LONG)
                }
                val args = allocArray<jvalue>(1) { _: Int -> j = data.ptr.rawValue.toLong() }
                requireNotNull(pointed?.CallVoidMethodA)(ptr, globalCallback, method, args)
            }
        }
    },
        NotificationPriority.entries[priority],
        { globalCallback.deleteGlobalRef() }
    )).asCPointer().rawValue.toLong()
}

private fun hasPermissions(env: JNIEnvVar, clazz: jclass, handler: jlong): jboolean {
    return requireNotNull(handler.toCPointer<CPointed>())
        .asStableRef<NotificationHandler>()
        .get().hasPermissions.toJBoolean()
}

private fun destroy(env: JNIEnvVar, clazz: jclass, handler: jlong) {
    requireNotNull(handler.toCPointer<CPointed>())
        .asStableRef<NotificationHandler>()
        .apply {
            get().close()
            dispose()
        }
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
    iconHeight: jint,
    playSound: jboolean,
    soundPath: jstring?,
    group: jstring?,
    callbackData: jstring?
): jlong {
    return runBlocking {
        requireNotNull(handler.toCPointer<CPointed>())
            .asStableRef<NotificationHandler>()
            .get().push(
                Notification(
                    title = title?.toKString() ?: "Unknown",
                    description = description?.toKString(),
                    icon = icon?.let { NotificationIcon(it.toByteArray(), iconWidth, iconHeight) },
                    playSound = playSound.toKBoolean(),
                    soundPath = soundPath?.toKString(),
                    group = group?.toKString(),
                    callbackData = callbackData?.toKString()
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
    iconHeight: jint,
    group: jstring?,
    callbackData: jstring?
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
                    group = group?.toKString(),
                    callbackData = callbackData?.toKString()
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
