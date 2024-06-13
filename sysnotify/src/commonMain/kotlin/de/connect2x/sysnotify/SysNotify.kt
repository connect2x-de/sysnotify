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

import com.benasher44.uuid.Uuid
import com.benasher44.uuid.uuid4

enum class NotificationPlatform {
    ANDROID,
    APPLE,
    JVM,
    LINUX,
    WINDOWS
}

data class NotificationCapabilities(
    val isTitleSupported: Boolean,
    val isDescriptionSupported: Boolean,
    val isIconSupported: Boolean,
    val isActionSupported: Boolean,
    val isPopSupported: Boolean
) {
    val isSupported: Boolean =
        isTitleSupported || isDescriptionSupported || isIconSupported || isActionSupported
}

data class Notification(
    val title: String? = null,
    val description: String? = null,
    val icon: ByteArray? = null,
    val action: (() -> Unit)? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as Notification

        if (title != other.title) return false
        if (description != other.description) return false
        if (icon != null) {
            if (other.icon == null) return false
            if (!icon.contentEquals(other.icon)) return false
        } else if (other.icon != null) return false
        if (action != other.action) return false

        return true
    }

    override fun hashCode(): Int {
        var result = title?.hashCode() ?: 0
        result = 31 * result + (description?.hashCode() ?: 0)
        result = 31 * result + (icon?.contentHashCode() ?: 0)
        result = 31 * result + (action?.hashCode() ?: 0)
        return result
    }

}

interface NotificationHandle

@OptIn(ExperimentalStdlibApi::class)
interface NotificationHandler : AutoCloseable {
    val name: String
    val platform: NotificationPlatform
    val capabilities: NotificationCapabilities

    fun requestPermissions() {}

    fun push(notification: Notification): NotificationHandle?
    fun pop(handle: NotificationHandle)

    fun popIfSupported(handle: NotificationHandle) {
        if (!capabilities.isPopSupported) throw IllegalStateException("Pop is not supported on $platform")
        pop(handle)
    }

    fun update(handle: NotificationHandle, notification: Notification): NotificationHandle? {
        pop(handle)
        return push(notification)
    }

    override fun close() {}
}

expect fun createNotificationHandler(
    name: String,
    context: Any? = null
): NotificationHandler
