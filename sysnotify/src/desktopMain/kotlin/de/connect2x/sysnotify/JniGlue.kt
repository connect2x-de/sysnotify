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
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.cValue
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toCPointer
import kotlinx.coroutines.runBlocking

internal expect fun createNotificationHandler(
    name: String,
    id: String,
    priority: NotificationPriority,
    activationHandler: ActivationHandler,
    closeCallback: () -> Unit = {},
    isDebugEnabled: Boolean = false,
    appId: String
): NotificationHandler

private val typeNativeNotificationHandler =
    JvmRefType("de.connect2x.sysnotify.NativeNotificationHandler")
private val typeNativeActivationCallback =
    JvmRefType("de.connect2x.sysnotify.NativeActivationCallback")
private val typeNativePermissionCallback =
    JvmRefType("de.connect2x.sysnotify.NativePermissionCallback")
private val typeString = JvmRefType("java.lang.String")

internal fun registerNatives(env: JNIEnvVar) {
    typeNativeNotificationHandler.getJClass(env).apply {
        registerNativeFunction(env, "create", staticCFunction(::create)) {
            typeString named "name"
            typeString named "id"
            JvmBuiltinType.INT named "priority"
            typeNativeActivationCallback named "activationCallback"
            JvmBuiltinType.BOOLEAN named "isDebugEnabled"
            typeString named "appId"
            returns(JvmBuiltinType.LONG)
        }
        registerNativeFunction(env, "hasPermissions", staticCFunction(::hasPermissions)) {
            JvmBuiltinType.LONG named "handler"
            returns(JvmBuiltinType.BOOLEAN)
        }
        registerNativeFunction(env, "destroy", staticCFunction(::destroy)) {
            JvmBuiltinType.LONG named "handler"
        }
        registerNativeFunction(env, "getPlatform", staticCFunction(::getPlatform)) {
            JvmBuiltinType.LONG named "handler"
            returns(JvmBuiltinType.INT)
        }
        registerNativeFunction(env, "getCapabilities", staticCFunction(::getCapabilities)) {
            JvmBuiltinType.LONG named "handler"
            returns(JvmBuiltinType.INT)
        }
        registerNativeFunction(env, "requestPermissions", staticCFunction(::requestPermissions)) {
            JvmBuiltinType.LONG named "handler"
            typeNativePermissionCallback named "callback"
        }
        registerNativeFunction(env, "push", staticCFunction(::push)) {
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
        registerNativeFunction(env, "getHandle", staticCFunction(::getHandle)) {
            JvmBuiltinType.LONG named "handler"
            typeString named "tag"
            returns(JvmBuiltinType.LONG)
        }
        registerNativeFunction(env, "update", staticCFunction(::update)) {
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
        registerNativeFunction(env, "pop", staticCFunction(::pop)) {
            JvmBuiltinType.LONG named "handler"
            JvmBuiltinType.LONG named "notification"
        }
        registerNativeFunction(env, "clearAll", staticCFunction(::clearAll)) {
            JvmBuiltinType.LONG named "handler"
        }
    }
}

private fun create(
    env: JNIEnvVar,
    clazz: jclass,
    name: jstring,
    id: jstring,
    priority: jint,
    activationCallback: jobject,
    isDebugEnabled: jboolean,
    appId: jstring
): jlong {
    val globalCallback = activationCallback.newGlobalRef(env)
    val handler = createNotificationHandler(
        name = name.toKString(env),
        id = id.toKString(env),
        priority = NotificationPriority.entries[priority],
        activationHandler = { notification ->
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
                jvmEnvironment {
                    typeNativeActivationCallback.getJClass(this).getMethod("invoke") {
                        JvmBuiltinType.LONG named "notification"
                    }.call(globalCallback) {
                        put("notification", data.ptr.rawValue.toLong())
                    }
                }
            }
        },
        closeCallback = {
            jvmEnvironment {
                globalCallback.deleteGlobalRef(this)
            }
        },
        isDebugEnabled = isDebugEnabled.toKBoolean(),
        appId = appId.toKString(env)
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
    val globalCallback = callback.newGlobalRef(env)
    requireNotNull(handler.toCPointer<CPointed>())
        .asStableRef<NotificationHandler>()
        .get()
        .requestPermissions { granted ->
            jvmEnvironment {
                typeNativePermissionCallback.getJClass(this).getMethod("invoke") {
                    JvmBuiltinType.BOOLEAN named "granted"
                }.call(globalCallback) {
                    put("granted", granted)
                }
                globalCallback.deleteGlobalRef(this)
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
                title = title?.toKString(env) ?: "Unknown",
                description = description?.toKString(env),
                icon = icon?.let { NotificationIcon(it.toByteArray(env), iconWidth, iconHeight) },
                playSound = playSound.toKBoolean(),
                soundPath = soundPath?.toKString(env),
                group = group?.toKString(env),
                callbackData = callbackData?.toKString(env)
            ), tag.toKString(env)
        )
}.let { StableRef.create(it).asCPointer().rawValue.toLong() }

private fun getHandle(
    env: JNIEnvVar,
    clazz: jclass,
    handler: jlong,
    tag: jstring,
): jlong {
    val handle = requireNotNull(handler.toCPointer<CPointed>())
        .asStableRef<NotificationHandler>()
        .get()
        .getHandle(tag.toKString(env))

    return StableRef.create(handle).asCPointer().rawValue.toLong()
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
                    title = title?.toKString(env) ?: "Unknown",
                    description = description?.toKString(env),
                    icon = icon?.let { NotificationIcon(it.toByteArray(env), iconWidth, iconHeight) },
                    group = group?.toKString(env),
                    callbackData = callbackData?.toKString(env)
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


private fun clearAll(
    env: JNIEnvVar,
    clazz: jclass,
    handler: jlong
) {
    runBlocking {
        requireNotNull(handler.toCPointer<CPointed>())
            .asStableRef<NotificationHandler>()
            .get()
            .clearAll()
    }
}
