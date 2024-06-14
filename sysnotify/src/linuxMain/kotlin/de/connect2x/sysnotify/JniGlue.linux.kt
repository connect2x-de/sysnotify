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
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.convert
import kotlinx.coroutines.runBlocking
import libnotify.NotifyNotification
import kotlin.experimental.ExperimentalNativeApi

@CName("sn_notification_handler_create")
fun snNotificationHandlerCreate(name: jstring): jlong {
    return StableRef.create(LinuxNotificationHandler(name.toKString())).asCPointer().rawValue.toLong()
}

@CName("sn_notification_handler_is_title_supported")
fun snNotificationHandlerIsTitleSupported(handler: jlong): jboolean {
    return handler.convert<COpaquePointer>()
        .asStableRef<LinuxNotificationHandler>()
        .get().capabilities.isTitleSupported.toJBoolean()
}

@CName("sn_notification_handler_is_description_supported")
fun snNotificationHandlerIsDescriptionSupported(handler: jlong): jboolean {
    return handler.convert<COpaquePointer>()
        .asStableRef<LinuxNotificationHandler>()
        .get().capabilities.isDescriptionSupported.toJBoolean()
}

@CName("sn_notification_handler_is_icon_supported")
fun snNotificationHandlerIsIconSupported(handler: jlong): jboolean {
    return handler.convert<COpaquePointer>()
        .asStableRef<LinuxNotificationHandler>()
        .get().capabilities.isIconSupported.toJBoolean()
}

@CName("sn_notification_handler_is_action_supported")
fun snNotificationHandlerIsActionSupported(handler: jlong): jboolean {
    return handler.convert<COpaquePointer>()
        .asStableRef<LinuxNotificationHandler>()
        .get().capabilities.isActionSupported.toJBoolean()
}

@CName("sn_notification_handler_is_pop_supported")
fun snNotificationHandlerIsPopSupported(handler: jlong): jboolean {
    return handler.convert<COpaquePointer>()
        .asStableRef<LinuxNotificationHandler>()
        .get().capabilities.isPopSupported.toJBoolean()
}

@CName("sn_notification_handler_request_permissions")
fun snNotificationHandlerRequestPermissions(handler: jlong) {
    runBlocking {
        handler.convert<COpaquePointer>()
            .asStableRef<LinuxNotificationHandler>()
            .get().requestPermissions()
    }
}

@CName("sn_notification_handler_push")
fun snNotificationHandlerPush(
    handler: jlong,
    title: jstring?,
    description: jstring?,
    icon: jbyteArray?
): jlong {
    return runBlocking {
        handler.convert<COpaquePointer>()
            .asStableRef<LinuxNotificationHandler>()
            .get().push(
                Notification(
                    title = title?.toKString() ?: "Unknown",
                    description = description?.toKString(),
                    icon = icon?.toByteArray()
                )
            )
    }?.let { it as? LinuxNotificationHandle }?.address?.rawValue?.toLong() ?: 0
}

@CName("sn_notification_handler_update")
fun snNotificationHandlerUpdate(
    handler: jlong,
    notification: jlong,
    title: jstring?,
    description: jstring?,
    icon: jbyteArray?
): jlong {
    return runBlocking {
        handler.convert<COpaquePointer>()
            .asStableRef<LinuxNotificationHandler>()
            .get().update(
                LinuxNotificationHandle(notification.convert<CPointer<NotifyNotification>>()),
                Notification(
                    title = title?.toKString() ?: "Unknown",
                    description = description?.toKString(),
                    icon = icon?.toByteArray()
                )
            )
    }?.let { it as? LinuxNotificationHandle }?.address?.rawValue?.toLong() ?: 0
}

@CName("sn_notification_handler_pop")
fun snNotificationHandlerPop(
    handler: jlong,
    notification: jlong
) {
    runBlocking {
        handler.convert<COpaquePointer>()
            .asStableRef<LinuxNotificationHandler>()
            .get().pop(LinuxNotificationHandle(notification.convert<CPointer<NotifyNotification>>()))
    }
}

@CName("sn_notification_handler_destroy")
fun snNotificationHandlerDestroy(handler: jlong) {
    handler.convert<COpaquePointer>()
        .asStableRef<LinuxNotificationHandler>()
        .dispose()
}
