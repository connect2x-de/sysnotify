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

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.invoke
import kotlinx.cinterop.staticCFunction
import libnotify.GBytes
import libnotify.GdkColorspace
import libnotify.GdkPixbuf
import libnotify.gboolean
import libnotify.gint
import platform.posix.atexit

private typealias _gdk_pixbuf_new_from_bytes = (
    data: CPointer<GBytes>?,
    colorSpace: GdkColorspace,
    hasAlpha: gboolean,
    bitsPerSample: gint,
    width: gint,
    height: gint,
    rowStride: gint
) -> CPointer<GdkPixbuf>?

private typealias _gdk_pixbuf_unref = (buffer: CPointer<GdkPixbuf>?) -> Unit

/**
 * See [the GTK documentation](https://docs.gtk.org/gdk-pixbuf/index.html)
 */
@Suppress("FunctionName")
internal object LibGdkPixbuf {
    private val library: SharedLibrary? = SharedLibrary.open("libgdk_pixbuf-2.0.so")

    init {
        atexit(staticCFunction<Unit> {
            // Need a fully qualified reference to avoid capturing implicitly
            LibGdkPixbuf.library?.close()
        })
    }

    /**
     * See [the GTK documentation](https://docs.gtk.org/gdk-pixbuf/ctor.Pixbuf.new_from_bytes.html)
     */
    fun gdk_pixbuf_new_from_bytes(
        data: CPointer<GBytes>?,
        colorSpace: GdkColorspace,
        hasAlpha: gboolean,
        bitsPerSample: gint,
        width: gint,
        height: gint,
        rowStride: gint
    ): CPointer<GdkPixbuf>? {
        return library?.findFunction<_gdk_pixbuf_new_from_bytes>("gdk_pixbuf_new_from_bytes")
            ?.invoke(data, colorSpace, hasAlpha, bitsPerSample, width, height, rowStride)
    }

    /**
     * See [the GTK documentation](https://docs.gtk.org/gdk-pixbuf/method.Pixbuf.unref.html)
     */
    fun gdk_pixbuf_unref(buffer: CPointer<GdkPixbuf>?) {
        library?.findFunction<_gdk_pixbuf_unref>("gdk_pixbuf_unref")
            ?.invoke(buffer)
    }
}
