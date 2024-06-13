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

@file:OptIn(ExperimentalForeignApi::class)

package de.connect2x.sysnotify

import jni.JNIEnvVar
import jni.jstring
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.invoke
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKStringFromUtf8

internal fun jstring.toKString(env: JNIEnvVar): String {
    if (env.rawPtr.toLong() == 0L) throw IllegalStateException("Environment cannot be null")
    val data = env.pointed!!.GetStringUTFChars!!(env.ptr, this, null)
    val result = data?.toKStringFromUtf8() ?: return ""
    env.pointed!!.ReleaseStringUTFChars!!(env.ptr, this, data)
    return result
}
