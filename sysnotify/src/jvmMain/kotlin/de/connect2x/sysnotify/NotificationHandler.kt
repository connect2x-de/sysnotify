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

package de.connect2x.sysnotify

import kotlinx.coroutines.runBlocking

private class JvmNotificationHandler(
    override val name: String,
    override val id: String,
    override val priority: NotificationPriority
) : NotificationHandler {
    var activationHandler: ActivationHandler? = null

    private val address: Long by lazy {
        NativeNotificationHandler.create(
            name,
            id,
            priority.ordinal,
            NativeActivationCallback.create {
                activationHandler?.invoke(it)
            }
        )
    }

    override val platform: NotificationPlatform by lazy {
        NotificationPlatform.entries[NativeNotificationHandler.getPlatform(address)]
    }

    override val hasPermissions: Boolean by lazy {
        NativeNotificationHandler.hasPermissions(address)
    }

    override val capabilities: NotificationCapabilities by lazy {
        NotificationCapabilities(NativeNotificationHandler.getCapabilities(address).toUInt())
    }

    override fun close() {
        NativeNotificationHandler.destroy(address)
    }

    override fun getHandle(tag: String): NotificationHandle {
        require(tag.isNotEmpty()) { "Handle tag may not be empty" }
        return JvmNotificationHandle(tag)
    }

    override suspend fun requestPermissions(callback: PermissionCallback) {
        NativeNotificationHandler.requestPermissions(address) {
            runBlocking {
                callback(this@JvmNotificationHandler, it)
            }
        }
    }

    override suspend fun push(notification: Notification, tag: String): NotificationHandle {
        val handle = NativeNotificationHandler.push(
            handler = address,
            tag = tag,
            title = notification.title ?: "Unknown",
            description = notification.description,
            icon = notification.icon?.data,
            iconWidth = notification.icon?.width ?: 0,
            iconHeight = notification.icon?.height ?: 0,
            playSound = notification.playSound,
            soundPath = notification.soundPath,
            group = notification.group,
            callbackData = notification.callbackData
        )
        return if (handle != 0L) JvmNotificationHandle(tag, address)
        else NotificationHandle.INVALID
    }

    override suspend fun update(
        handle: NotificationHandle,
        notification: Notification
    ): NotificationHandle {
        handle.check<JvmNotificationHandle>()
        val newHandle = NativeNotificationHandler.update(
            handler = address,
            notification = handle.address,
            title = notification.title ?: "Unknown",
            description = notification.description,
            icon = notification.icon?.data,
            iconWidth = notification.icon?.width ?: 0,
            iconHeight = notification.icon?.height ?: 0,
            group = notification.group,
            callbackData = notification.callbackData
        )
        return if (newHandle != 0L) JvmNotificationHandle(address = newHandle)
        else NotificationHandle.INVALID
    }

    override suspend fun pop(handle: NotificationHandle) {
        require(handle is JvmNotificationHandle)
        NativeNotificationHandler.pop(address, handle.address)
    }
}

// TODO: document this
fun NotificationHandler.withActivationHandler(handler: ActivationHandler): NotificationHandler {
    check<JvmNotificationHandler>()
    activationHandler = handler
    return this
}

actual fun NotificationHandler( // @formatter:off
    name: String,
    id: String,
    priority: NotificationPriority
): NotificationHandler { // @formatter:on
    return JvmNotificationHandler(name, id, priority)
}
