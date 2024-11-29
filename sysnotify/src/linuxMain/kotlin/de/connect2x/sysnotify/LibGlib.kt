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

package de.connect2x.sysnotify

import io.karma.dlfcn.SharedLibrary
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.invoke
import kotlinx.cinterop.staticCFunction
import libnotify.GBytes
import libnotify.GError
import libnotify.GObject
import libnotify.gsize
import platform.posix.atexit

private typealias _g_bytes_new_static = (
    address: COpaquePointer?,
    size: gsize
) -> CPointer<GBytes>

private typealias _g_error_free = (error: CPointer<GError>?) -> Unit
private typealias _g_bytes_unref = (data: CPointer<GBytes>?) -> Unit
private typealias _g_object_unref = (obj: CPointer<GObject>?) -> Unit

@Suppress("FunctionName")
internal object LibGlib {
    private val library: SharedLibrary? = SharedLibrary.open("libglib-2.0.so")?.also {
        println("Loaded libglib-2.0 successfully")
    }

    init {
        atexit(staticCFunction<Unit> {
            LibGlib.library?.close()
            println("Unloaded libglib-2.0 successfully")
        })
    }

    fun g_bytes_new_static(address: COpaquePointer?, size: gsize): CPointer<GBytes>? {
        return library?.findFunctionOrNull<_g_bytes_new_static>("g_bytes_new_static")
            ?.invoke(address, size)
    }

    fun g_error_free(error: CPointer<GError>?) {
        library?.findFunctionOrNull<_g_error_free>("g_error_free")
            ?.invoke(error)
    }

    fun g_bytes_unref(data: CPointer<GBytes>?) {
        library?.findFunctionOrNull<_g_bytes_unref>("g_bytes_unref")
            ?.invoke(data)
    }

    fun g_object_unref(obj: CPointer<GObject>?) {
        library?.findFunctionOrNull<_g_object_unref>("g_object_unref")
            ?.invoke(obj)
    }
}
