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

import android.app.Notification
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
internal class AndroidNotificationHandle(
    val tag: Uuid = Uuid.random(),
    var value: Notification? = null,
) : NotificationHandle {
    override fun equals(other: Any?): Boolean {
        return if (other !is AndroidNotificationHandle) false
        else tag == other.tag
    }

    override fun hashCode(): Int = tag.hashCode()
    override fun toString(): String = tag.toString()
}

/**
 * The native AndroidNotification instance associated with this notification handle.
 */
val NotificationHandle.nativeNotification: Notification
    get() {
        require(this is AndroidNotificationHandle) { "Handle is not an Android notification handle" }
        return requireNotNull(value) { "Handle does not contain a valid Android Notification reference" }
    }

/**
 * Creates a new notification handle for the given tag
 * which can be used to query or invalidate existing
 * notifications.
 * @param tag The tag of the notification associated with this handle.
 * @return A new [NotificationHandle] instance containing the given tag.
 */
@OptIn(ExperimentalUuidApi::class)
fun NotificationHandle.Companion.create(tag: Uuid): NotificationHandle = AndroidNotificationHandle(tag)
