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
import kotlinx.cinterop.CValues
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cstr
import kotlinx.cinterop.invoke
import kotlinx.cinterop.staticCFunction
import libnotify.GCallback
import libnotify.GClosureNotify
import libnotify.GConnectFlags
import libnotify.GObject
import libnotify.gcharVar
import libnotify.gpointer
import libnotify.gulong
import platform.posix.atexit

private typealias _g_signal_connect_data = (
    instance: CPointer<GObject>?,
    detailedSignal: CValues<gcharVar>,
    handler: GCallback?,
    data: gpointer?,
    destroyData: GClosureNotify?,
    connectFlags: GConnectFlags
) -> gulong

private typealias _g_object_unref = (obj: CPointer<GObject>?) -> Unit

/**
 * See [the GTK documentation](https://docs.gtk.org/gobject/index.html)
 */
object LibGObject {
    private val library: SharedLibrary? = SharedLibrary.open("libgobject-2.0.so")

    init {
        atexit(staticCFunction<Unit> {
            LibGObject.apply {
                library?.close()
            }
        })
    }

    /**
     * See [the GTK documentation](https://docs.gtk.org/gobject/func.signal_connect_data.html)
     */
    fun g_signal_connect_data(
        instance: CPointer<GObject>?,
        detailedSignal: String,
        handler: GCallback?,
        data: gpointer?,
        destroyData: GClosureNotify?,
        connectFlags: GConnectFlags
    ): gulong {
        return library?.findFunction<_g_signal_connect_data>("g_signal_connect_data")
            ?.invoke(instance, detailedSignal.cstr, handler, data, destroyData, connectFlags) ?: 0UL
    }

    /**
     * See [the GTK documentation](https://docs.gtk.org/gobject/method.Object.unref.html)
     */
    fun g_object_unref(obj: CPointer<GObject>?) {
        library?.findFunction<_g_object_unref>("g_object_unref")
            ?.invoke(obj)
    }
}
