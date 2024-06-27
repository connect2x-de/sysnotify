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

/**
 * A complete list of all platforms supported by Sysnotify.
 * The current platform can be queried from every [NotificationHandler] instance
 * via the [NotificationHandler.platform] property.
 */
enum class NotificationPlatform {
    ANDROID,
    APPLE, // This includes iOS and macOS
    LINUX,
    WINDOWS,
    WEB
}

/**
 * A data class describing the capabilities of a given [NotificationHandler] instance.
 * Each flag indicates whether a feature is supported.
 * The [isSupported] flag indicates if notifications are supported on the current platform at all.
 */
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

enum class NotificationDismissalReason {
    /**
     * This action is triggered when the notification is dismissed by
     * a user action like swiping it away.
     */
    USER_CANCELED,

    /**
     * This action is triggered when the notification is dismissed
     * programmatically, like by calling [NotificationHandler.pop].
     */
    APPLICATION_HIDDEN,

    /**
     * This action is triggered when the notification is hidden
     * automatically if the default amount for being visible has expired.
     */
    TIMED_OUT
}

data class Notification(
    val title: String? = null,
    val description: String? = null,
    val icon: ByteArray? = null,
    val onActivated: (() -> Unit)? = null,
    val onDismissed: ((NotificationDismissalReason) -> Unit)? = null,
    val onFailed: (() -> Unit)? = null,
    val onRemoved: (() -> Unit)? = null
) {
    // Begin ugly generated code
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
        if (onActivated != other.onActivated) return false
        if (onDismissed != other.onDismissed) return false
        if (onFailed != other.onFailed) return false
        if (onRemoved != other.onRemoved) return false

        return true
    }

    override fun hashCode(): Int {
        var result = title?.hashCode() ?: 0
        result = 31 * result + (description?.hashCode() ?: 0)
        result = 31 * result + (icon?.contentHashCode() ?: 0)
        result = 31 * result + (onActivated?.hashCode() ?: 0)
        result = 31 * result + (onDismissed?.hashCode() ?: 0)
        result = 31 * result + (onFailed?.hashCode() ?: 0)
        result = 31 * result + (onRemoved?.hashCode() ?: 0)
        return result
    }
    // End ugly generated code
}

/**
 * This interface describes a notification that was created by an [NotificationHandler] instance
 * on any given platform.
 */
interface NotificationHandle

/**
 * This interface provides platform-agnostic functions to interact with the OSs notifications.
 * It allows pushing, updating and popping notifications based on the supported platform feature set.
 * A single application may create multiple notification handlers to act as different notification "channels",
 * much like on a system like Android.
 *
 * Since this API may invoke native/foreign APIs which may allocate unmanaged heap memory,
 * this interface extends [AutoCloseable] to allow closing notification handlers when the lifecycle
 * of the process using it ends.
 * It is very important to invoke the provided [close] function in order to free up allocated resources,
 * otherwise a memory leak might occur based on the implementation.
 */
interface NotificationHandler : AutoCloseable {
    val name: String
    val platform: NotificationPlatform
    val capabilities: NotificationCapabilities

    suspend fun requestPermissions() {}

    suspend fun push(notification: Notification): NotificationHandle?
    suspend fun pop(handle: NotificationHandle)

    suspend fun update(
        handle: NotificationHandle,
        notification: Notification
    ): NotificationHandle? {
        pop(handle)
        return push(notification)
    }

    override fun close() {}

    companion object // Used for create implementations
}

object NoopNotificationHandler : NotificationHandler {
    override val name: String = "NOOP"
    override val platform: NotificationPlatform
        get() = throw UnsupportedOperationException()
    override val capabilities: NotificationCapabilities = NotificationCapabilities(
        isTitleSupported = false,
        isDescriptionSupported = false,
        isIconSupported = false,
        isActionSupported = false,
        isPopSupported = false
    )

    override suspend fun push(notification: Notification): NotificationHandle? {
        throw UnsupportedOperationException()
    }

    override suspend fun update(
        handle: NotificationHandle,
        notification: Notification
    ): NotificationHandle? {
        throw UnsupportedOperationException()
    }

    override suspend fun pop(handle: NotificationHandle) {
        throw UnsupportedOperationException()
    }
}
