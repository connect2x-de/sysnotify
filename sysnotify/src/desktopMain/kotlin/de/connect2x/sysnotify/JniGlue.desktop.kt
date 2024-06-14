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

@file:OptIn(ExperimentalNativeApi::class, ExperimentalForeignApi::class)

package de.connect2x.sysnotify

import jni.JNINativeMethod
import jni.JNI_OK
import jni.jboolean
import jni.jbyteArray
import jni.jlong
import jni.jstring
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toCPointer
import kotlinx.coroutines.runBlocking
import kotlin.experimental.ExperimentalNativeApi

internal expect fun createNotificationHandler(name: String): NotificationHandler

internal fun registerNatives() {
    val envInstance = checkNotNull(environment.pointed)
    memScoped {
        val clazz = checkNotNull(envInstance.FindClass)(
            environment.ptr,
            allocArrayOf("de/connect2x/sysnotify/NativeNotificationHandler".encodeToByteArray())
        )
        val methods = allocArray<JNINativeMethod>(11) { index: Int ->
            memScoped {
                when (index) {
                    0 -> {
                        name = allocArrayOf("create".encodeToByteArray())
                        signature = allocArrayOf("(Ljava/lang/String;)J".encodeToByteArray())
                        fnPtr = staticCFunction(::snNotificationHandlerCreate)
                    }

                    1 -> {
                        name = allocArrayOf("destroy".encodeToByteArray())
                        signature = allocArrayOf("(J)V".encodeToByteArray())
                        fnPtr = staticCFunction(::snNotificationHandlerDestroy)
                    }

                    2 -> {
                        name = allocArrayOf("isTitleSupported".encodeToByteArray())
                        signature = allocArrayOf("(J)Z".encodeToByteArray())
                        fnPtr = staticCFunction(::snNotificationHandlerIsTitleSupported)
                    }

                    3 -> {
                        name = allocArrayOf("isDescriptionSupported".encodeToByteArray())
                        signature = allocArrayOf("(J)Z".encodeToByteArray())
                        fnPtr = staticCFunction(::snNotificationHandlerIsDescriptionSupported)
                    }

                    4 -> {
                        name = allocArrayOf("isIconSupported".encodeToByteArray())
                        signature = allocArrayOf("(J)Z".encodeToByteArray())
                        fnPtr = staticCFunction(::snNotificationHandlerIsIconSupported)
                    }

                    5 -> {
                        name = allocArrayOf("isActionSupported".encodeToByteArray())
                        signature = allocArrayOf("(J)Z".encodeToByteArray())
                        fnPtr = staticCFunction(::snNotificationHandlerIsActionSupported)
                    }

                    6 -> {
                        name = allocArrayOf("isPopSupported".encodeToByteArray())
                        signature = allocArrayOf("(J)Z".encodeToByteArray())
                        fnPtr = staticCFunction(::snNotificationHandlerIsPopSupported)
                    }

                    7 -> {
                        name = allocArrayOf("requestPermissions".encodeToByteArray())
                        signature = allocArrayOf("(J)V".encodeToByteArray())
                        fnPtr = staticCFunction(::snNotificationHandlerRequestPermissions)
                    }

                    8 -> {
                        name = allocArrayOf("push".encodeToByteArray())
                        signature = allocArrayOf("(JLjava/lang/String;Ljava/lang/String;[B)J".encodeToByteArray())
                        fnPtr = staticCFunction(::snNotificationHandlerPush)
                    }

                    9 -> {
                        name = allocArrayOf("update".encodeToByteArray())
                        signature = allocArrayOf("(JJLjava/lang/String;Ljava/lang/String;[B)".encodeToByteArray())
                        fnPtr = staticCFunction(::snNotificationHandlerUpdate)
                    }

                    10 -> {
                        name = allocArrayOf("pop".encodeToByteArray())
                        signature = allocArrayOf("(JJ)V".encodeToByteArray())
                        fnPtr = staticCFunction(::snNotificationHandlerPop)
                    }
                }
            }
        }
        if (checkNotNull(envInstance.RegisterNatives)(environment.ptr, clazz, methods, 0) != JNI_OK) {
            throw IllegalStateException("Could not register native functions")
        }
    }
}

@Suppress("UNUSED")
@CName("sn_notification_handler_create")
private fun snNotificationHandlerCreate(name: jstring): jlong {
    return StableRef.create(createNotificationHandler(name.toKString())).asCPointer().rawValue.toLong()
}

@Suppress("UNUSED")
@CName("sn_notification_handler_destroy")
private fun snNotificationHandlerDestroy(handler: jlong) {
    checkNotNull(handler.toCPointer<CPointed>())
        .asStableRef<NotificationHandler>()
        .dispose()
}

@Suppress("UNUSED")
@CName("sn_notification_handler_is_title_supported")
private fun snNotificationHandlerIsTitleSupported(handler: jlong): jboolean {
    return checkNotNull(handler.toCPointer<CPointed>())
        .asStableRef<NotificationHandler>()
        .get().capabilities.isTitleSupported.toJBoolean()
}

@Suppress("UNUSED")
@CName("sn_notification_handler_is_description_supported")
private fun snNotificationHandlerIsDescriptionSupported(handler: jlong): jboolean {
    return checkNotNull(handler.toCPointer<CPointed>())
        .asStableRef<NotificationHandler>()
        .get().capabilities.isDescriptionSupported.toJBoolean()
}

@Suppress("UNUSED")
@CName("sn_notification_handler_is_icon_supported")
private fun snNotificationHandlerIsIconSupported(handler: jlong): jboolean {
    return checkNotNull(handler.toCPointer<CPointed>())
        .asStableRef<NotificationHandler>()
        .get().capabilities.isIconSupported.toJBoolean()
}

@Suppress("UNUSED")
@CName("sn_notification_handler_is_action_supported")
private fun snNotificationHandlerIsActionSupported(handler: jlong): jboolean {
    return checkNotNull(handler.toCPointer<CPointed>())
        .asStableRef<NotificationHandler>()
        .get().capabilities.isActionSupported.toJBoolean()
}

@Suppress("UNUSED")
@CName("sn_notification_handler_is_pop_supported")
private fun snNotificationHandlerIsPopSupported(handler: jlong): jboolean {
    return checkNotNull(handler.toCPointer<CPointed>())
        .asStableRef<NotificationHandler>()
        .get().capabilities.isPopSupported.toJBoolean()
}

@Suppress("UNUSED")
@CName("sn_notification_handler_request_permissions")
private fun snNotificationHandlerRequestPermissions(handler: jlong) {
    runBlocking {
        checkNotNull(handler.toCPointer<CPointed>())
            .asStableRef<NotificationHandler>()
            .get().requestPermissions()
    }
}

@Suppress("UNUSED")
@CName("sn_notification_handler_push")
private fun snNotificationHandlerPush(
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

@Suppress("UNUSED")
@CName("sn_notification_handler_update")
private fun snNotificationHandlerUpdate(
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

@Suppress("UNUSED")
@CName("sn_notification_handler_pop")
private fun snNotificationHandlerPop(
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
