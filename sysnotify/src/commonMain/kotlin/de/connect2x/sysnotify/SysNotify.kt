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
    UNKNOWN,
    ANDROID,
    APPLE, // This includes iOS and macOS
    LINUX,
    WINDOWS,
    WEB,
}

/**
 * Describes the priority of a given notification.
 * This usually impacts the order in which notifications
 * are shown by the underlying platform when presenting
 * them to the user.
 */
enum class NotificationPriority {
    LOW,
    DEFAULT,
    HIGH,
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
        val ALL: UInt = IS_TITLE_SUPPORTED or
                IS_DESCRIPTION_SUPPORTED or
                IS_ICON_SUPPORTED or
                IS_ACTION_SUPPORTED or
                IS_POP_SUPPORTED
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

/**
 * Describes a notification including its title, description and icon among other things.
 *
 * @param title The title of the notification, should be short.
 * @param description The description or "content" of a notification.
 * @param icon The icon shown as part of the notification. This can be the application icon or an avatar.
 * @param userData A string that is passed to the application when it is started from activating a notification.
 * @param dismissible Determines whether this notification can be "swiped away" by the user. False means the
 *  notification will remain persistent until explicitly popped.
 * @param priority The priority of this notification within the same notification handler context.
 *  This usually affects how notifications within the same group are sorted.
 */
data class Notification(
    val title: String? = null,
    val description: String? = null,
    val icon: NotificationIcon? = null,
    val userData: String? = null,
    val dismissible: Boolean = true,
    val priority: NotificationPriority = NotificationPriority.DEFAULT,
)

/**
 * This interface describes a notification that was created by an [NotificationHandler] instance
 * on any given platform.
 */
interface NotificationHandle {
    companion object
}

/**
 * This object is used to represent handles which are non-null but not valid for use with any
 * other functions that accept them as their parameters.
 */
object InvalidNotificationHandle : NotificationHandle

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
    /**
     * The display name of the notification handler.
     */
    val name: String

    /**
     * The internal ID of the notification handler.
     * This usually follows the underlying platform's naming conventions;
     * Package group on JVM and Android, AUMID on Windows etc.
     */
    val id: String

    /**
     * The platform on which the current notification handler is implemented.
     * See [NotificationPlatform] for all possible values.
     */
    val platform: NotificationPlatform

    /**
     * The capabilities of this notification handler.
     * This allows querying which features are supported exactly
     * by this notification handler instance.
     * See [NotificationCapabilities] for all possible feature flags.
     */
    val capabilities: NotificationCapabilities

    /**
     * Determines whether this notification handler has been granted
     * permission to display notifications by the underlying platform.
     * If the underlying platform has no concept of permissions for this
     * application, the value of this property will be `true`.
     */
    val hasPermissions: Boolean

    /**
     * Determines the general priority of all notifications pushed
     * by this notification handler instance.
     */
    val priority: NotificationPriority

    /**
     * Creates a new notification which has a valid associated system notification,
     * but is not pushed to be displayed. This can be useful on certain systems like
     * Android to create Notification instances without explicitly pushing them.
     *
     * @param notification The notification contents to create the new notification from.
     * @return A new notification with the given content.
     */
    fun create(notification: Notification): NotificationHandle

    /**
     * Requests the permissions to show notifications from the underlying platform
     * when possible. Otherwise this function returns without any side effects.
     */
    suspend fun requestPermissions() {}

    /**
     * Clears all notifications that were created by this notification handler instance.
     */
    suspend fun clearAll() {}

    /**
     * Creates a new notification with the given content and pushes it to
     * be displayed by the underlying platform.
     *
     * @param notification The notification contents to create the new notification from.
     * @return A new notification with the given content which has been shown by the system
     *  when successful, null otherwise.
     */
    suspend fun push(notification: Notification): NotificationHandle?

    /**
     * Hides and removes the given notification if it is valid and if it exists.
     *
     * @param handle A handle to the notification to hide and remove.
     */
    suspend fun pop(handle: NotificationHandle)

    /**
     * Updates the given existing notification with the given content if it is valid.
     *
     * @param handle A handle to the notification to update the contents of.
     * @return The same handle that was passed in when successful, null otherwise.
     */
    suspend fun update(
        handle: NotificationHandle,
        notification: Notification,
    ): NotificationHandle? {
        pop(handle)
        return push(notification)
    }

    /**
     * Frees all underlying resources allocated by this notification handler if any
     * and returns them to the underlying operating system.
     * **Always call this when you're done using the notification handler!**
     */
    override fun close() {}

    companion object // Used for create implementations
}

object NoopNotificationHandler : NotificationHandler {
    override val name: String = "NOOP"
    override val id: String = "no.op.noop"
    override val platform: NotificationPlatform = NotificationPlatform.UNKNOWN
    override val hasPermissions: Boolean = false
    override val capabilities: NotificationCapabilities = NotificationCapabilities(0U)
    override val priority: NotificationPriority = NotificationPriority.DEFAULT

    override fun create(notification: Notification): NotificationHandle = InvalidNotificationHandle
    override suspend fun push(notification: Notification): NotificationHandle = InvalidNotificationHandle

    override suspend fun update(
        handle: NotificationHandle,
        notification: Notification
    ): NotificationHandle = InvalidNotificationHandle

    override suspend fun pop(handle: NotificationHandle) {}
}
