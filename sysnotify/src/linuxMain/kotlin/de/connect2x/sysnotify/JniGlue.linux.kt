/*
 * Copyright 2024 connect2x
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
import jni.JavaVM
import jni.JavaVMVar
import jni.jbyteArray
import jni.jint
import jni.jobject
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.invoke
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlin.experimental.ExperimentalNativeApi

// JNI load hooks

internal lateinit var environment: JNIEnvVar

@CName("JNI_OnLoad")
external fun jniOnLoad(vm: JavaVMVar, reserved: COpaquePointer): jint {
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

@CName("JNI_OnUnload")
external fun jniOnUnload(vm: JavaVM, reserved: COpaquePointer) {

}

// Exposed high-level C-API functions

@CName("sn_notification_handler_create")
external fun snNotificationHandlerCreate(name: String?, context: jobject?) {

}

@CName("sn_notification_handler_push")
external fun snNotificationHandlerPush(
    handler: COpaquePointer?,
    name: String?,
    description: String?,
    icon: jbyteArray?
): COpaquePointer? {
    return null
}

@CName("sn_notification_handler_update")
external fun snNotificationHandlerUpdate(
    handler: COpaquePointer?,
    notification: COpaquePointer?,
    name: String?,
    description: String?,
    icon: jbyteArray?
) {

}

@CName("sn_notification_handler_pop")
external fun snNotificationHandlerPop(
    handler: COpaquePointer?,
    notification: COpaquePointer?
) {

}

@CName("sn_notification_handler_destroy")
external fun snNotificationHandlerDestroy(handler: COpaquePointer?) {

}