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

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.CValues
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cstr
import kotlinx.cinterop.invoke
import kotlinx.cinterop.staticCFunction
import libnotify.GError
import libnotify.GdkPixbuf
import libnotify.NotifyNotification
import libnotify.NotifyUrgency
import libnotify.gboolean
import libnotify.gint
import platform.posix.atexit

private typealias _notify_init = (name: CValues<ByteVar>?) -> Int
private typealias _notify_uninit = () -> Unit

private typealias _notify_notification_set_icon_from_pixbuf = (
    notification: CPointer<NotifyNotification>?,
    buffer: CPointer<GdkPixbuf>?
) -> Unit

private typealias _notify_notification_show = (
    notification: CPointer<NotifyNotification>?,
    error: CPointer<CPointerVar<GError>>?
) -> gboolean

private typealias _notify_notification_new = (
    summary: CValues<ByteVar>?,
    body: CValues<ByteVar>?,
    icon: CValues<ByteVar>?
) -> CPointer<NotifyNotification>?

private typealias _notify_notification_set_app_name = (
    notification: CPointer<NotifyNotification>?,
    name: CValues<ByteVar>?
) -> Unit

private typealias _notify_notification_set_timeout = (
    notification: CPointer<NotifyNotification>?,
    timeout: gint
) -> Unit

private typealias _notify_notification_set_urgency = (
    notification: CPointer<NotifyNotification>?,
    urgency: NotifyUrgency
) -> Unit

private typealias _notify_notification_set_hint_string = (
    notification: CPointer<NotifyNotification>?,
    name: CValues<ByteVar>?,
    path: CValues<ByteVar>?
) -> Unit

private typealias _notify_notification_update = (
    notification: CPointer<NotifyNotification>?,
    summary: CValues<ByteVar>?,
    body: CValues<ByteVar>?,
    icon: CValues<ByteVar>?
) -> gboolean

private typealias _notify_notification_close = (
    notification: CPointer<NotifyNotification>?,
    error: CPointer<CPointerVar<GError>>?
) -> gboolean

@Suppress("FunctionName")
internal object LibNotify {
    private val library: SharedLibrary? = SharedLibrary.open("libnotify.so")?.also {
        println("Loaded libnotify successfully")
    }

    init {
        atexit(staticCFunction<Unit> {
            // Must use explicit object ref to avoid non-capturing limitations
            LibNotify.library?.close()
            println("Unloaded libnotify successfully")
        })
    }

    fun notify_init(name: String): Int {
        return library?.findFunctionOrNull<_notify_init>("notify_init")
            ?.invoke(name.cstr) ?: -1
    }

    fun notify_uninit() {
        library?.findFunctionOrNull<_notify_uninit>("notify_uninit")
            ?.invoke()
    }

    fun notify_notification_set_icon_from_pixbuf(
        notification: CPointer<NotifyNotification>?,
        buffer: CPointer<GdkPixbuf>?
    ) {
        library?.findFunctionOrNull<_notify_notification_set_icon_from_pixbuf>("notify_notification_set_icon_from_pixbuf")
            ?.invoke(notification, buffer)
    }

    fun notify_notification_show(
        notification: CPointer<NotifyNotification>?,
        error: CPointer<CPointerVar<GError>>?
    ): gboolean {
        return library?.findFunctionOrNull<_notify_notification_show>("notify_notification_show")
            ?.invoke(notification, error) ?: 0
    }

    fun notify_notification_new(
        summary: String?,
        body: String?,
        icon: String?
    ): CPointer<NotifyNotification>? {
        return library?.findFunctionOrNull<_notify_notification_new>("notify_notification_new")
            ?.invoke(summary?.cstr, body?.cstr, icon?.cstr)
    }

    fun notify_notification_set_app_name(
        notification: CPointer<NotifyNotification>?,
        name: String?
    ) {
        library?.findFunctionOrNull<_notify_notification_set_app_name>("notify_notification_set_app_name")
            ?.invoke(notification, name?.cstr)
    }

    fun notify_notification_set_timeout(
        notification: CPointer<NotifyNotification>?,
        timeout: gint
    ) {
        library?.findFunctionOrNull<_notify_notification_set_timeout>("notify_notification_set_timeout")
            ?.invoke(notification, timeout)
    }

    fun notify_notification_set_urgency(
        notification: CPointer<NotifyNotification>?,
        urgency: NotifyUrgency
    ) {
        library?.findFunctionOrNull<_notify_notification_set_urgency>("notify_notification_set_urgency")
            ?.invoke(notification, urgency)
    }

    fun notify_notification_set_hint_string(
        notification: CPointer<NotifyNotification>?,
        name: String?,
        path: String?
    ) {
        library?.findFunctionOrNull<_notify_notification_set_hint_string>("notify_notification_set_hint_string")
            ?.invoke(notification, name?.cstr, path?.cstr)
    }

    fun notify_notification_update(
        notification: CPointer<NotifyNotification>?,
        summary: String?,
        body: String?,
        icon: String?
    ): gboolean {
        return library?.findFunctionOrNull<_notify_notification_update>("notify_notification_update")
            ?.invoke(notification, summary?.cstr, body?.cstr, icon?.cstr) ?: 0
    }

    fun notify_notification_close(
        notification: CPointer<NotifyNotification>?,
        error: CPointer<CPointerVar<GError>>?
    ): gboolean {
        return library?.findFunctionOrNull<_notify_notification_close>("notify_notification_close")
            ?.invoke(notification, error) ?: 0
    }
}
