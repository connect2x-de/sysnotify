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

package de.connect2x.sysnotify

import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.invoke
import kotlinx.cinterop.staticCFunction
import libnotify.GBytes
import libnotify.GError
import libnotify.GMainContext
import libnotify.GMainLoop
import libnotify.gboolean
import libnotify.gsize
import platform.posix.atexit

private typealias _g_bytes_new_static = (
    address: COpaquePointer?,
    size: gsize
) -> CPointer<GBytes>

private typealias _g_error_free = (error: CPointer<GError>?) -> Unit
private typealias _g_bytes_unref = (data: CPointer<GBytes>?) -> Unit

private typealias _g_main_loop_new = (
    context: CPointer<GMainContext>?,
    isRunning: gboolean
) -> CPointer<GMainLoop>?

private typealias _g_main_loop_unref = (loop: CPointer<GMainLoop>?) -> Unit
private typealias _g_main_loop_get_context = (loop: CPointer<GMainLoop>?) -> CPointer<GMainContext>?

private typealias _g_main_context_iteration = (
    context: CPointer<GMainContext>?,
    mayBlock: gboolean
) -> gboolean

private typealias _g_main_loop_quit = (loop: CPointer<GMainLoop>?) -> Unit

/**
 * See [the GTK documentation](https://docs.gtk.org/glib/)
 */
@Suppress("FunctionName")
internal object LibGLib {
    private val library: SharedLibrary? = SharedLibrary.open("libglib-2.0.so")

    init {
        atexit(staticCFunction<Unit> {
            // Need a fully qualified reference to avoid capturing implicitly
            LibGLib.library?.close()
        })
    }

    /**
     * See [the GTK documentation](https://docs.gtk.org/glib/ctor.Bytes.new_static.html)
     */
    fun g_bytes_new_static(address: COpaquePointer?, size: gsize): CPointer<GBytes>? {
        return library?.findFunction<_g_bytes_new_static>("g_bytes_new_static")
            ?.invoke(address, size)
    }

    /**
     * See [the GTK documentation](https://docs.gtk.org/glib/method.Error.free.html)
     */
    fun g_error_free(error: CPointer<GError>?) {
        library?.findFunction<_g_error_free>("g_error_free")
            ?.invoke(error)
    }

    /**
     * See [the GTK documentation](https://docs.gtk.org/glib/method.Bytes.unref.html)
     */
    fun g_bytes_unref(data: CPointer<GBytes>?) {
        library?.findFunction<_g_bytes_unref>("g_bytes_unref")
            ?.invoke(data)
    }

    /**
     * See [the GTK documentation](https://docs.gtk.org/glib/ctor.MainLoop.new.html)
     */
    fun g_main_loop_new(
        context: CPointer<GMainContext>?,
        isRunning: gboolean
    ): CPointer<GMainLoop>? {
        return library?.findFunction<_g_main_loop_new>("g_main_loop_new")
            ?.invoke(context, isRunning)
    }

    /**
     * See [the GTK documentation](https://docs.gtk.org/glib/method.MainLoop.unref.html)
     */
    fun g_main_loop_unref(loop: CPointer<GMainLoop>?) {
        library?.findFunction<_g_main_loop_unref>("g_main_loop_unref")
            ?.invoke(loop)
    }

    /**
     * See [the GTK documentation](https://docs.gtk.org/glib/method.MainLoop.get_context.html)
     */
    fun g_main_loop_get_context(loop: CPointer<GMainLoop>?): CPointer<GMainContext>? {
        return library?.findFunction<_g_main_loop_get_context>("g_main_loop_get_context")
            ?.invoke(loop)
    }

    /**
     * See [the GTK documentation](https://docs.gtk.org/glib/method.MainLoop.quit.html)
     */
    fun g_main_loop_quit(loop: CPointer<GMainLoop>?) {
        library?.findFunction<_g_main_loop_quit>("g_main_loop_quit")?.invoke(loop)
    }

    /**
     * See [the GTK documentation](https://docs.gtk.org/glib/method.MainContext.iteration.html)
     */
    fun g_main_context_iteration(
        context: CPointer<GMainContext>?,
        mayBlock: gboolean
    ): gboolean {
        return library?.findFunction<_g_main_context_iteration>("g_main_context_iteration")
            ?.invoke(context, mayBlock) ?: 0
    }
}
