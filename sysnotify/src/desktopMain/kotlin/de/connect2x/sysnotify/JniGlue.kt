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
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.cValue
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
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
private val typeNativePermissionCallback =
    JvmRefType("de.connect2x.sysnotify.NativePermissionCallback")
private val typeString = JvmRefType("java.lang.String")

internal fun registerNatives() {
    typeNativeNotificationHandler.jClass.apply {
        registerNativeFunction("create", staticCFunction(::create)) {
            typeString named "name"
            typeString named "id"
            JvmBuiltinType.INT named "priority"
            typeNativeActivationCallback named "activationCallback"
            returns(JvmBuiltinType.LONG)
        }
        registerNativeFunction("hasPermissions", staticCFunction(::hasPermissions)) {
            JvmBuiltinType.LONG named "handler"
            returns(JvmBuiltinType.BOOLEAN)
        }
        registerNativeFunction("destroy", staticCFunction(::destroy)) {
            JvmBuiltinType.LONG named "handler"
        }
        registerNativeFunction("getPlatform", staticCFunction(::getPlatform)) {
            JvmBuiltinType.LONG named "handler"
            returns(JvmBuiltinType.INT)
        }
        registerNativeFunction("getCapabilities", staticCFunction(::getCapabilities)) {
            JvmBuiltinType.LONG named "handler"
            returns(JvmBuiltinType.INT)
        }
        registerNativeFunction("requestPermissions", staticCFunction(::requestPermissions)) {
            JvmBuiltinType.LONG named "handler"
            typeNativePermissionCallback named "callback"
        }
        registerNativeFunction("push", staticCFunction(::push)) {
            JvmBuiltinType.LONG named "handler"
            typeString named "tag"
            typeString named "title"
            typeString named "description"
            JvmBuiltinType.BYTE.array() named "icon"
            JvmBuiltinType.INT named "iconWidth"
            JvmBuiltinType.INT named "iconHeight"
            JvmBuiltinType.BOOLEAN named "playSound"
            typeString named "soundPath"
            typeString named "group"
            typeString named "callbackData"
            returns(JvmBuiltinType.LONG)
        }
        registerNativeFunction("update", staticCFunction(::update)) {
            JvmBuiltinType.LONG named "handler"
            JvmBuiltinType.LONG named "notification"
            typeString named "title"
            typeString named "description"
            JvmBuiltinType.BYTE.array() named "icon"
            JvmBuiltinType.INT named "iconWidth"
            JvmBuiltinType.INT named "iconHeight"
            typeString named "group"
            typeString named "callbackData"
            returns(JvmBuiltinType.LONG)
        }
        registerNativeFunction("pop", staticCFunction(::pop)) {
            JvmBuiltinType.LONG named "handler"
            JvmBuiltinType.LONG named "notification"
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
    val handler = createNotificationHandler(
        name.toKString(), id.toKString(), { notification ->
            jvmEnvironment { // Ensure thread is attached to JVM from the get-go
                memScoped {
                    val data = cValue<SNNotificationData> {
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
                    typeNativeActivationCallback.jClass.getMethod("invoke") {
                        JvmBuiltinType.LONG named "notification"
                    }.call(globalCallback) {
                        put("notification", data.ptr.rawValue.toLong())
                    }
                }
            }
        },
        NotificationPriority.entries[priority],
        { globalCallback.deleteGlobalRef() }
    )
    return StableRef.create(handler).asCPointer().rawValue.toLong()
}

private fun hasPermissions(env: JNIEnvVar, clazz: jclass, handler: jlong): jboolean {
    return requireNotNull(handler.toCPointer<CPointed>())
        .asStableRef<NotificationHandler>()
        .get()
        .hasPermissions.toJBoolean()
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
        .get()
        .platform.ordinal
}

private fun getCapabilities(env: JNIEnvVar, clazz: jclass, handler: jlong): jint {
    return requireNotNull(handler.toCPointer<CPointed>())
        .asStableRef<NotificationHandler>()
        .get()
        .capabilities.mask.toInt()
}

private fun requestPermissions(env: JNIEnvVar, clazz: jclass, handler: jlong, callback: jobject) = runBlocking {
    requireNotNull(handler.toCPointer<CPointed>())
        .asStableRef<NotificationHandler>()
        .get()
        .requestPermissions { _, granted ->
            typeNativePermissionCallback.jClass.getMethod("invoke") {
                JvmBuiltinType.BOOLEAN named "granted"
            }.call(callback) {
                put("granted", granted)
            }
        }
}

private fun push(
    env: JNIEnvVar,
    clazz: jclass,
    handler: jlong,
    tag: jstring,
    title: jstring?,
    description: jstring?,
    icon: jbyteArray?,
    iconWidth: jint,
    iconHeight: jint,
    playSound: jboolean,
    soundPath: jstring?,
    group: jstring?,
    callbackData: jstring?
): jlong = runBlocking {
    requireNotNull(handler.toCPointer<CPointed>())
        .asStableRef<NotificationHandler>()
        .get()
        .push(
            Notification(
                title = title?.toKString() ?: "Unknown",
                description = description?.toKString(),
                icon = icon?.let { NotificationIcon(it.toByteArray(), iconWidth, iconHeight) },
                playSound = playSound.toKBoolean(),
                soundPath = soundPath?.toKString(),
                group = group?.toKString(),
                callbackData = callbackData?.toKString()
            ), tag.toKString()
        )
}.let { StableRef.create(it).asCPointer().rawValue.toLong() }

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
    }.let { StableRef.create(it).asCPointer().rawValue.toLong() }
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
