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

package de.connect2x.sysnotify

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.pointed
import kotlinx.cinterop.toKStringFromUtf8
import libnotify.GError

@OptIn(ExperimentalForeignApi::class)
internal fun <P : CPointer<GError>> P.freeAndThrow() {
    val message = this.pointed.message?.toKStringFromUtf8()
    LibGLib.g_error_free(this)
    throw IllegalStateException(message)
}
