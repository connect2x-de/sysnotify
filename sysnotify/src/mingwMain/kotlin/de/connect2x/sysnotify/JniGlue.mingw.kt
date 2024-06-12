/*
 * Copyright 2024 Karma Krafts & associates
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

package io.karma.sysnotify

import jni.JNI_VERSION_1_8
import jni.JavaVMVar
import jni.jbyteArray
import jni.jint
import jni.jobject
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlin.experimental.ExperimentalNativeApi

// JNI load hooks

@CName("JNI_OnLoad")
fun jniOnLoad(vm: JavaVMVar, reserved: COpaquePointer): jint {
    return JNI_VERSION_1_8
}

@CName("JNI_OnUnload")
fun jniOnUnload(vm: JavaVMVar, reserved: COpaquePointer) {

}

// Exposed high-level C-API functions

@CName("sn_notification_handler_create")
fun snNotificationHandlerCreate(name: String?, context: jobject?) {

}

@CName("sn_notification_handler_push")
fun snNotificationHandlerPush(
    handler: COpaquePointer?,
    name: String?,
    description: String?,
    icon: jbyteArray?
): COpaquePointer? {
    return null
}

@CName("sn_notification_handler_update")
fun snNotificationHandlerUpdate(
    handler: COpaquePointer?,
    notification: COpaquePointer?,
    name: String?,
    description: String?,
    icon: jbyteArray?
) {

}

@CName("sn_notification_handler_pop")
fun snNotificationHandlerPop(
    handler: COpaquePointer?,
    notification: COpaquePointer?
) {

}

@CName("sn_notification_handler_destroy")
fun snNotificationHandlerDestroy(handler: COpaquePointer?) {

}