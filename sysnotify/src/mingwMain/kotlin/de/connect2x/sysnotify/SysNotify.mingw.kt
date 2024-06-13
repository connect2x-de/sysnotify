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

import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi

private data class WindowsNotificationHandle(
    val address: COpaquePointer
) : NotificationHandle

private class WindowsNotificationHandler(
    override val name: String,
) : NotificationHandler {
    override val platform: NotificationPlatform = NotificationPlatform.WINDOWS

    override val capabilities: NotificationCapabilities = NotificationCapabilities(
        isTitleSupported = true,
        isDescriptionSupported = true,
        isIconSupported = true,
        isActionSupported = true,
        isPopSupported = false
    )

    override suspend fun push(notification: Notification): NotificationHandle? {
        return null
    }

    override suspend fun pop(handle: NotificationHandle) {

    }
}

fun NotificationHandler.Companion.create(
    name: String
): NotificationHandler = WindowsNotificationHandler(name)
