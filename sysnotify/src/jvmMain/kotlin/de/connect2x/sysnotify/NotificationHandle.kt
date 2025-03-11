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

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
internal class JvmNotificationHandle(
    override val tag: String = Uuid.random().toHexString(),
    val address: Long = 0L,
) : NotificationHandle {
    override fun equals(other: Any?): Boolean {
        return if (other !is JvmNotificationHandle) false
        else tag == other.tag
    }

    override fun hashCode(): Int = tag.hashCode()
    override fun toString(): String = tag
}
