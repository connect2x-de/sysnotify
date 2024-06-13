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

import jni.JNIEnvVar
import jni.JNI_ERR
import jni.JNI_OK
import jni.JNI_VERSION_1_8
import jni.JavaVMVar
import jni.jboolean
import jni.jbyteArray
import jni.jint
import jni.jlong
import jni.jstring
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.invoke
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlin.experimental.ExperimentalNativeApi

// JNI load hooks and utils

internal lateinit var environment: JNIEnvVar

@Suppress("UNUSED_PARAMETER")
@CName("JNI_OnLoad")
fun jniOnLoad(vm: JavaVMVar, reserved: COpaquePointer): jint {
    if (vm.pointed!!.GetEnv!!(
            vm.ptr,
            environment.ptr.reinterpret(),
            JNI_VERSION_1_8
        ) != JNI_OK
    ) {
        return JNI_ERR
    }
    return JNI_VERSION_1_8
}

@Suppress("UNUSED_PARAMETER")
@CName("JNI_OnUnload")
fun jniOnUnload(vm: JavaVMVar, reserved: COpaquePointer) {

}

// Exposed high-level C-API functions

@CName("sn_notification_handler_create")
fun snCreate(name: jstring?): jlong {
    return 0
}

@CName("sn_notification_handler_is_title_supported")
fun snIsTitleSupported(handler: jlong): jboolean {
    return 0U
}

@CName("sn_notification_handler_is_description_supported")
fun snIsDescriptionSupported(handler: jlong): jboolean {
    return 0U
}

@CName("sn_notification_handler_is_icon_supported")
fun snIsIconSupported(handler: jlong): jboolean {
    return 0U
}

@CName("sn_notification_handler_is_action_supported")
fun snIsActionSupported(handler: jlong): jboolean {
    return 0U
}

@CName("sn_notification_handler_is_pop_supported")
fun snIsPopSupported(handler: jlong): jboolean {
    return 0U
}

@CName("sn_notification_handler_request_permissions")
fun snRequestPermissions(handler: jlong) {

}

@CName("sn_notification_handler_push")
fun snPush(
    handler: jlong,
    name: jstring,
    description: jstring?,
    icon: jbyteArray?
): jlong {
    val actualName = name.toKString(environment)
    val actualDescription = description?.toKString(environment) ?: ""
    return 0
}

@CName("sn_notification_handler_update")
fun snUpdate(
    handler: jlong,
    notification: jlong,
    name: jstring,
    description: jstring?,
    icon: jbyteArray?
) {
    val actualName = name.toKString(environment)
    val actualDescription = description?.toKString(environment) ?: ""
}

@CName("sn_notification_handler_pop")
fun snPop(
    handler: jlong,
    notification: jlong
) {

}

@CName("sn_notification_handler_destroy")
fun snDestroy(handler: jlong) {

}
