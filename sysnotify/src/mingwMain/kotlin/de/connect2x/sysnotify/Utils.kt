/*
 * Copyright 2026 connect2x GmbH
 *
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
@file:Suppress("NOTHING_TO_INLINE")

package de.connect2x.sysnotify

import de.connect2x.sysnotify.com.SYSNOTIFY_OK
import de.connect2x.sysnotify.com.sn_error
import de.connect2x.sysnotify.com.sysnotify_get_last_error
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKStringFromUtf8

internal inline fun sn_error.check() {
    if (this == SYSNOTIFY_OK) return
    throwLastError()
}

internal inline fun throwLastError() {
    sysnotify_get_last_error()?.apply {
        throw IllegalStateException(toKStringFromUtf8())
    }
}
