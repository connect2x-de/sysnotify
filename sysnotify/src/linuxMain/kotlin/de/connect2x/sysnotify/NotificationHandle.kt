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

package de.connect2x.sysnotify

import kotlinx.atomicfu.atomic
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import libnotify.NotifyNotification
import kotlin.uuid.Uuid

internal class LinuxNotificationHandle(
    override val tag: String = Uuid.random().toHexString(),
    val handler: LinuxNotificationHandler
) : NotificationHandle {
    var address: CPointer<NotifyNotification>? by atomic(null)
}

/**
 * Gets the native pointer to the Linux notification object.
 *
 * This extension property provides access to the underlying C pointer that references
 * the native libnotify notification object. It's used for interacting with the
 * libnotify API when updating or removing notifications.
 *
 * @return The C pointer to the NotifyNotification object
 * @throws IllegalStateException if the handle is not a LinuxNotificationHandle
 * @throws IllegalArgumentException if the handle is not valid
 * @throws IllegalStateException if the address is null (notification not initialized)
 */
val NotificationHandle.address: CPointer<NotifyNotification>
    get() {
        check<LinuxNotificationHandle>()
        require(isValid) { "Handle must be valid" }
        return requireNotNull(address) { "Handle does not contain a valid Linux Notification reference" }
    }
