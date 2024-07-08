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
import jni.jobject
import jni.jstring
import jni.jvalue
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.alloc
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toCPointer
import kotlinx.coroutines.runBlocking

internal expect fun createNotificationHandler(name: String, id: String): NotificationHandler

private val typeNativeNotificationHandler =
    JvmRefType("de.connect2x.sysnotify.NativeNotificationHandler")
private val typeNativeNotificationAction =
    JvmRefType("de.connect2x.sysnotify.NativeNotificationAction")
private val typeNativeDismissalAction =
    JvmRefType("de.connect2x.sysnotify.NativeDismissalAction")
private val typeString = JvmRefType("java.lang.String")

internal fun registerNatives() {
    typeNativeNotificationHandler.jClass.apply {
        registerNativeFunction("create", staticCFunction(::create)) {
            parameter(typeString)
            parameter(typeString)
            returns(JvmBuiltinType.LONG)
        }
        registerNativeFunction("destroy", staticCFunction(::destroy)) {
            parameter(JvmBuiltinType.LONG)
        }
        registerNativeFunction("getPlatform", staticCFunction(::getPlatform)) {
            parameter(JvmBuiltinType.LONG)
            returns(JvmBuiltinType.INT)
        }
        registerNativeFunction("isTitleSupported", staticCFunction(::isTitleSupported)) {
            parameter(JvmBuiltinType.LONG)
            returns(JvmBuiltinType.BOOLEAN)
        }
        registerNativeFunction(
            "isDescriptionSupported",
            staticCFunction(::isDescriptionSupported)
        ) {
            parameter(JvmBuiltinType.LONG)
            returns(JvmBuiltinType.BOOLEAN)
        }
        registerNativeFunction("isIconSupported", staticCFunction(::isIconSupported)) {
            parameter(JvmBuiltinType.LONG)
            returns(JvmBuiltinType.BOOLEAN)
        }
        registerNativeFunction("isActionSupported", staticCFunction(::isActionSupported)) {
            parameter(JvmBuiltinType.LONG)
            returns(JvmBuiltinType.BOOLEAN)
        }
        registerNativeFunction("isPopSupported", staticCFunction(::isPopSupported)) {
            parameter(JvmBuiltinType.LONG)
            returns(JvmBuiltinType.BOOLEAN)
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
            parameter(typeNativeNotificationAction)
            parameter(typeNativeDismissalAction)
            parameter(typeNativeNotificationAction)
            parameter(typeNativeNotificationAction)
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

private fun create(env: JNIEnvVar, clazz: jclass, name: jstring, id: jstring): jlong {
    return StableRef.create(createNotificationHandler(name.toKString(), id.toKString()))
        .asCPointer().rawValue.toLong()
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

private fun isTitleSupported(env: JNIEnvVar, clazz: jclass, handler: jlong): jboolean {
    return requireNotNull(handler.toCPointer<CPointed>())
        .asStableRef<NotificationHandler>()
        .get().capabilities.isTitleSupported.toJBoolean()
}

private fun isDescriptionSupported(env: JNIEnvVar, clazz: jclass, handler: jlong): jboolean {
    return requireNotNull(handler.toCPointer<CPointed>())
        .asStableRef<NotificationHandler>()
        .get().capabilities.isDescriptionSupported.toJBoolean()
}

private fun isIconSupported(env: JNIEnvVar, clazz: jclass, handler: jlong): jboolean {
    return requireNotNull(handler.toCPointer<CPointed>())
        .asStableRef<NotificationHandler>()
        .get().capabilities.isIconSupported.toJBoolean()
}

private fun isActionSupported(env: JNIEnvVar, clazz: jclass, handler: jlong): jboolean {
    return requireNotNull(handler.toCPointer<CPointed>())
        .asStableRef<NotificationHandler>()
        .get().capabilities.isActionSupported.toJBoolean()
}

private fun isPopSupported(env: JNIEnvVar, clazz: jclass, handler: jlong): jboolean {
    return requireNotNull(handler.toCPointer<CPointed>())
        .asStableRef<NotificationHandler>()
        .get().capabilities.isPopSupported.toJBoolean()
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
    activateAction: jobject?,
    dismissAction: jobject?,
    failAction: jobject?,
    removeAction: jobject?
): jlong {
    var activateActionRef = activateAction?.newGlobalRef()
    var dismissActionRef = dismissAction?.newGlobalRef()
    var failActionRef = failAction?.newGlobalRef()
    var removeActionRef = removeAction?.newGlobalRef()
    return runBlocking {
        requireNotNull(handler.toCPointer<CPointed>())
            .asStableRef<NotificationHandler>()
            .get().push(
                Notification(
                    title = title?.toKString() ?: "Unknown",
                    description = description?.toKString(),
                    icon = icon?.let { NotificationIcon(it.toByteArray(), iconWidth, iconHeight) },
                    onActivated = callback@{
                        if (activateActionRef == null) return@callback
                        jvmEnvironment {
                            requireNotNull(pointed?.CallVoidMethodA) { "Could not access JNIEnv::CallVoidMethodA" }(
                                this@jvmEnvironment.ptr,
                                activateActionRef,
                                typeNativeNotificationAction.jClass.getMethod("invoke") {},
                                null
                            )
                        }
                    },
                    onDismissed = callback@{ reason ->
                        if (dismissActionRef == null) return@callback
                        jvmEnvironment {
                            memScoped {
                                requireNotNull(pointed?.CallVoidMethodA) { "Could not access JNIEnv::CallVoidMethodA" }(
                                    this@jvmEnvironment.ptr,
                                    dismissActionRef,
                                    typeNativeDismissalAction.jClass.getMethod("invoke") {
                                        parameter(JvmBuiltinType.INT)
                                    },
                                    alloc<jvalue> {
                                        i = reason.ordinal
                                    }.ptr // Allocate reason as index on stack
                                )
                            }
                        }
                    },
                    onFailed = callback@{
                        if (failActionRef == null) return@callback
                        jvmEnvironment {
                            requireNotNull(pointed?.CallVoidMethodA) { "Could not access JNIEnv::CallVoidMethodA" }(
                                this@jvmEnvironment.ptr,
                                failActionRef,
                                typeNativeNotificationAction.jClass.getMethod("invoke") {},
                                null
                            )
                        }
                    },
                    onRemoved = callback@{
                        jvmEnvironment {
                            activateActionRef?.deleteGlobalRef()
                            activateActionRef = null
                            dismissActionRef?.deleteGlobalRef()
                            dismissActionRef = null
                            failActionRef?.deleteGlobalRef()
                            failActionRef = null
                            if (removeActionRef == null) return@callback
                            requireNotNull(pointed?.CallVoidMethodA) { "Could not access JNIEnv::CallVoidMethodA" }(
                                this@jvmEnvironment.ptr,
                                removeActionRef,
                                typeNativeNotificationAction.jClass.getMethod("invoke") {},
                                null
                            )
                            removeActionRef!!.deleteGlobalRef()
                            removeActionRef = null
                        }
                    }
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
