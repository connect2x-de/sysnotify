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

import jni.jboolean
import jni.jbyteArray
import jni.jlong
import jni.jstring
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.toCPointer
import kotlinx.coroutines.runBlocking
import kotlin.experimental.ExperimentalNativeApi

internal expect fun createNotificationHandler(name: String): NotificationHandler

@Suppress("UNUSED")
@CName("sn_notification_handler_create")
fun snNotificationHandlerCreate(name: jstring): jlong {
    return StableRef.create(createNotificationHandler(name.toKString())).asCPointer().rawValue.toLong()
}

@Suppress("UNUSED")
@CName("sn_notification_handler_destroy")
fun snNotificationHandlerDestroy(handler: jlong) {
    checkNotNull(handler.toCPointer<CPointed>())
        .asStableRef<NotificationHandler>()
        .dispose()
}

@Suppress("UNUSED")
@CName("sn_notification_handler_is_title_supported")
fun snNotificationHandlerIsTitleSupported(handler: jlong): jboolean {
    return checkNotNull(handler.toCPointer<CPointed>())
        .asStableRef<NotificationHandler>()
        .get().capabilities.isTitleSupported.toJBoolean()
}

@Suppress("UNUSED")
@CName("sn_notification_handler_is_description_supported")
fun snNotificationHandlerIsDescriptionSupported(handler: jlong): jboolean {
    return checkNotNull(handler.toCPointer<CPointed>())
        .asStableRef<NotificationHandler>()
        .get().capabilities.isDescriptionSupported.toJBoolean()
}

@Suppress("UNUSED")
@CName("sn_notification_handler_is_icon_supported")
fun snNotificationHandlerIsIconSupported(handler: jlong): jboolean {
    return checkNotNull(handler.toCPointer<CPointed>())
        .asStableRef<NotificationHandler>()
        .get().capabilities.isIconSupported.toJBoolean()
}

@Suppress("UNUSED")
@CName("sn_notification_handler_is_action_supported")
fun snNotificationHandlerIsActionSupported(handler: jlong): jboolean {
    return checkNotNull(handler.toCPointer<CPointed>())
        .asStableRef<NotificationHandler>()
        .get().capabilities.isActionSupported.toJBoolean()
}

@Suppress("UNUSED")
@CName("sn_notification_handler_is_pop_supported")
fun snNotificationHandlerIsPopSupported(handler: jlong): jboolean {
    return checkNotNull(handler.toCPointer<CPointed>())
        .asStableRef<NotificationHandler>()
        .get().capabilities.isPopSupported.toJBoolean()
}

@Suppress("UNUSED")
@CName("sn_notification_handler_request_permissions")
fun snNotificationHandlerRequestPermissions(handler: jlong) {
    runBlocking {
        checkNotNull(handler.toCPointer<CPointed>())
            .asStableRef<NotificationHandler>()
            .get().requestPermissions()
    }
}

@Suppress("UNUSED")
@CName("sn_notification_handler_push")
fun snNotificationHandlerPush(
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
fun snNotificationHandlerUpdate(
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
fun snNotificationHandlerPop(
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
