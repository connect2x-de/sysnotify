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

package de.connect2x.sysnotify

import de.connect2x.sysnotify.com.SYSNOTIFY_TRUE
import de.connect2x.sysnotify.com.sn_boolVar
import de.connect2x.sysnotify.com.sysnotify_handler_is_valid_tag
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class, ExperimentalForeignApi::class)
internal data class WindowsNotificationHandle(
    val handler: WindowsNotificationHandler,
    val tag: Uuid,
) : NotificationHandle {
    inline val isValid: Boolean
        get() = memScoped {
            val result = alloc<sn_boolVar>()
            sysnotify_handler_is_valid_tag(
                handler.address,
                allocNotificationTag(tag).ptr,
                result.ptr
            ).check()
            return result.value == SYSNOTIFY_TRUE
        }
}
