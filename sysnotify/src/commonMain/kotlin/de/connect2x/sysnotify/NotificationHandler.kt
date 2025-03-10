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

typealias PermissionCallback = (NotificationHandler, Boolean) -> Unit

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

    // TODO: document this
    fun getHandle(tag: String): NotificationHandle = NotificationHandle.INVALID

    /**
     * Requests the permissions to show notifications from the underlying platform
     * when possible. Otherwise, this function returns without any side effects.
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
}

// TODO: document this
expect fun NotificationHandler(
    name: String,
    id: String = name,
    priority: NotificationPriority = NotificationPriority.DEFAULT,
    permissionCallback: PermissionCallback = { _, _ -> }
): NotificationHandler

// TODO: document this
object NoopNotificationHandler : NotificationHandler {
    override val name: String = "NOOP"
    override val id: String = "no.op.noop"
    override val platform: NotificationPlatform = NotificationPlatform.UNKNOWN
    override val hasPermissions: Boolean = false
    override val capabilities: NotificationCapabilities = NotificationCapabilities(0U)
    override val priority: NotificationPriority = NotificationPriority.DEFAULT

    override fun getHandle(tag: String): NotificationHandle = NotificationHandle.INVALID

    override suspend fun push(notification: Notification): NotificationHandle = NotificationHandle.INVALID

    override suspend fun update(
        handle: NotificationHandle,
        notification: Notification
    ): NotificationHandle = NotificationHandle.INVALID

    override suspend fun pop(handle: NotificationHandle) {}
}
