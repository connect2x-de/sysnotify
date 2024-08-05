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
data class NotificationCapabilities(val mask: UInt) {
    companion object {
        const val IS_TITLE_SUPPORTED: UInt = 1U
        const val IS_DESCRIPTION_SUPPORTED: UInt = 2U
        const val IS_ICON_SUPPORTED: UInt = 4U
        const val IS_ACTION_SUPPORTED: UInt = 8U
        const val IS_POP_SUPPORTED: UInt = 16U
    }

    constructor(
        isTitleSupported: Boolean,
        isDescriptionSupported: Boolean,
        isIconSupported: Boolean,
        isActionSupported: Boolean,
        isPopSupported: Boolean
    ) : this(
        if (isTitleSupported) IS_TITLE_SUPPORTED else 0U
                or if (isDescriptionSupported) IS_DESCRIPTION_SUPPORTED else 0U
                or if (isIconSupported) IS_ICON_SUPPORTED else 0U
                or if (isActionSupported) IS_ACTION_SUPPORTED else 0U
                or if (isPopSupported) IS_POP_SUPPORTED else 0U
    )

    val isTitleSupported: Boolean = (mask and IS_TITLE_SUPPORTED) == IS_TITLE_SUPPORTED
    val isDescriptionSupported: Boolean = (mask and IS_DESCRIPTION_SUPPORTED) == IS_DESCRIPTION_SUPPORTED
    val isIconSupported: Boolean = (mask and IS_ICON_SUPPORTED) == IS_ICON_SUPPORTED
    val isActionSupported: Boolean = (mask and IS_ACTION_SUPPORTED) == IS_ACTION_SUPPORTED
    val isPopSupported: Boolean = (mask and IS_POP_SUPPORTED) == IS_POP_SUPPORTED

    /**
     * If true, the underlying implementation supports at least
     * one of three ways to display a basic notification. That is,
     * either using a title, description or icon, or any combination
     * of the aforementioned components.
     */
    val isSupported: Boolean =
        isTitleSupported || isDescriptionSupported || isIconSupported
}

/**
 * A raw bitmap type for storing icon data and its width/height.
 * Data is expected to be in RGBA8888 format. Use per-platform helpers for creating instances.
 */
data class NotificationIcon(val data: ByteArray, val width: Int, val height: Int) {
    companion object {}

    // Begin ugly generated code
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as NotificationIcon

        if (!data.contentEquals(other.data)) return false
        if (width != other.width) return false
        if (height != other.height) return false

        return true
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + width
        result = 31 * result + height
        return result
    }
    // End ugly generated code
}

data class Notification(
    val title: String? = null,
    val description: String? = null,
    val icon: NotificationIcon? = null,
    val userData: HashMap<String, Any?> = HashMap()
)

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
 *
 * The [name] may be used for displaying while the [id] is a unique internal identifier for your application
 * using this library and should not contain any whitespaces.
 */
interface NotificationHandler : AutoCloseable {
    val name: String
    val id: String
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
    override val id: String = "no.op.noop"
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
