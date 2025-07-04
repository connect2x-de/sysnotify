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

@file:JvmMultifileClass
@file:JvmName("NotificationHandler$")

package de.connect2x.sysnotify

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.jvm.JvmMultifileClass
import kotlin.jvm.JvmName
import kotlin.uuid.Uuid

/**
 * A callback function that is invoked after permission to show notifications has been requested.
 *
 * @param handler The notification handler that requested the permission
 * @param granted Boolean indicating whether permission was granted (true) or denied (false)
 */
typealias PermissionCallback = (handler: NotificationHandler, granted: Boolean) -> Unit

/**
 * A callback function that is invoked when a user interacts with (clicks/activates) a notification.
 *
 * @param notification The notification object that was activated by the user
 */
typealias ActivationHandler = (notification: Notification) -> Unit

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
     * A standard convention of <tld>.<company>.<product> must be used
     * to guarantee correct platform-specific conversion under the hood.
     */
    val id: String

    val appId: String
        get() = id

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
     * Retrieves a notification handle for a notification with the given tag.
     *
     * @param tag A unique string identifying a particular notification.
     * @return The notification handle associated with the tag, or an invalid handle if no notification with the given tag exists.
     */
    fun getHandle(tag: String): NotificationHandle = NotificationHandle.INVALID

    /**
     * Requests the permissions to show notifications from the underlying platform
     * when possible. Otherwise, this function returns without any side effects.
     */
    suspend fun requestPermissions(callback: PermissionCallback = { _, _ -> }) {}

    /**
     * Clears all notifications that were created by this notification handler instance.
     */
    suspend fun clearAll() {}

    /**
     * Creates a new notification with the given content and pushes it to
     * be displayed by the underlying platform.
     *
     * @param notification The notification contents to create the new notification from.
     * @param tag A unique string identifying this particular notification. Must be application-unique.
     * @return A new notification with the given content which has been shown by the system
     *  when successful, null otherwise.
     */
    suspend fun push(
        notification: Notification,
        tag: String = Uuid.random().toHexString()
    ): NotificationHandle

    /**
     * Hides and removes the given notification if it is valid and if it exists.
     *
     * @param handle A handle to the notification to hide and remove.
     */
    suspend fun pop(handle: NotificationHandle)

    /**
     * Hides and removes a notification identified by its tag.
     * This is a convenience method that retrieves the notification handle using the tag
     * and then calls the primary pop method.
     *
     * @param tag A unique string identifying the notification to hide and remove.
     */
    suspend fun pop(tag: String) = pop(getHandle(tag))

    /**
     * Updates the given existing notification with the given content if it is valid.
     *
     * @param handle A handle to the notification to update the contents of.
     * @return The same handle that was passed in when successful, null otherwise.
     */
    suspend fun update(
        handle: NotificationHandle,
        notification: Notification,
    ): NotificationHandle {
        pop(handle)
        return push(notification)
    }

    /**
     * Updates an existing notification identified by its tag with the given content.
     * This is a convenience method that retrieves the notification handle using the tag
     * and then calls the primary update method.
     *
     * @param tag A unique string identifying the notification to update.
     * @param notification The new notification contents to update with.
     * @return The notification handle associated with the updated notification when successful,
     *  or an invalid handle if the update failed.
     */
    suspend fun update(
        tag: String,
        notification: Notification
    ): NotificationHandle = update(getHandle(tag), notification)

    /**
     * Frees all underlying resources allocated by this notification handler if any
     * and returns them to the underlying operating system.
     * **Always call this when you're done using the notification handler!**
     */
    override fun close() {}
}

/**
 * Type-checking utility function that verifies if the receiver [NotificationHandler] is of type [H].
 *
 * This function uses Kotlin contracts to inform the compiler that if this function returns normally
 * (without throwing an exception), then the receiver can be safely cast to type [H] in the calling context.
 *
 * @param H The specific [NotificationHandler] type to check against
 * @throws ClassCastException implicitly if the receiver is not of type [H]
 */
@PublishedApi
@OptIn(ExperimentalContracts::class)
internal inline fun <reified H : NotificationHandler> NotificationHandler.check() {
    contract { returns() implies (this@check is H) }
}

/**
 * Create a NotificationHandler.
 *
 * @param name The display name of the notification handler.
 * @param id The internal ID of the notification handler.
 * @param priority The priority of the notification handler. See [NotificationPriority] for more information.
 * @param isDebugEnabled Used in macOS/iOS to disable the notifications when in development mode; a bundled app is needed for notifications to work properly here. On other systems, it is ignored. Defaults to false.
 */
expect fun NotificationHandler(
    name: String,
    id: String = name,
    priority: NotificationPriority = NotificationPriority.DEFAULT,
    isDebugEnabled: Boolean = false,
): NotificationHandler

/**
 * A no-operation implementation of the [NotificationHandler] interface.
 *
 * This implementation does not perform any actual notification operations and is useful in scenarios where:
 * - Notifications are not supported or available on the current platform
 * - A placeholder implementation is needed during testing or development
 * - A fallback is required when the primary notification handler cannot be initialized
 *
 * All methods return invalid or null values, and no actual notifications will be displayed.
 * This handler always reports [NotificationPlatform.UNKNOWN] as its platform and has no permissions or capabilities.
 */
object NoopNotificationHandler : NotificationHandler {
    override val name: String = "NOOP"
    override val id: String = "no.op.noop"
    override val platform: NotificationPlatform = NotificationPlatform.UNKNOWN
    override val hasPermissions: Boolean = false
    override val capabilities: NotificationCapabilities = NotificationCapabilities(0U)
    override val priority: NotificationPriority = NotificationPriority.DEFAULT

    override fun getHandle(tag: String): NotificationHandle = NotificationHandle.INVALID

    override suspend fun push(
        notification: Notification,
        tag: String
    ): NotificationHandle = NotificationHandle.INVALID

    override suspend fun update(
        handle: NotificationHandle,
        notification: Notification
    ): NotificationHandle = NotificationHandle.INVALID

    override suspend fun pop(handle: NotificationHandle) {}
}
