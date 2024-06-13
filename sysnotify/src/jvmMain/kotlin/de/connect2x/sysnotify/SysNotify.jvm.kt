/*
 * Copyright 2024 connect2x
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

// This is the high-level C-API implemented by desktop targets JniGlue class
private object NativeNotificationHandler {
    external fun create(name: String): Long
    external fun isTitleSupported(handler: Long): Boolean
    external fun isDescriptionSupported(handler: Long): Boolean
    external fun isIconSupported(handler: Long): Boolean
    external fun isActionSupported(handler: Long): Boolean
    external fun isPopSupported(handler: Long): Boolean
    external fun requestPermissions(handler: Long)
    external fun push(handler: Long, name: String, description: String?, icon: ByteArray?): Long
    external fun update(handler: Long, notification: Long, name: String, description: String?, icon: ByteArray?): Long
    external fun pop(handler: Long, notification: Long)
    external fun destroy(handler: Long)
}

private data class JvmNotificationHandle(
    val address: Long
) : NotificationHandle

private class JvmNotificationHandler(
    override val name: String
) : NotificationHandler {
    private val address: Long = NativeNotificationHandler.create(name)
    override val platform: NotificationPlatform = NotificationPlatform.JVM

    override val capabilities: NotificationCapabilities = NotificationCapabilities(
        isTitleSupported = NativeNotificationHandler.isTitleSupported(address),
        isDescriptionSupported = NativeNotificationHandler.isDescriptionSupported(address),
        isIconSupported = NativeNotificationHandler.isIconSupported(address),
        isActionSupported = NativeNotificationHandler.isActionSupported(address),
        isPopSupported = NativeNotificationHandler.isPopSupported(address)
    )

    override fun close() {
        NativeNotificationHandler.destroy(address)
    }

    override fun requestPermissions() {
        NativeNotificationHandler.requestPermissions(address)
    }

    override fun push(notification: Notification): NotificationHandle? {
        return null // TODO: ...
    }

    override fun pop(handle: NotificationHandle) {
        // TODO: ...
    }
}

actual fun createNotificationHandler(
    name: String,
    context: Any?
): NotificationHandler = JvmNotificationHandler(name)
