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

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocPointerTo
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.value
import libnotify.GError
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
internal class LinuxNotificationHandler(
    override val appId: String,
    override val name: String,
    override val id: String,
    override val priority: NotificationPriority,
    private val closeCallback: () -> Unit = {}
) : NotificationHandler {
    init {
        check(LibNotify.notify_init(name) != 0) { "Could not initialize libnotify" }
    }

    override val platform: NotificationPlatform = NotificationPlatform.LINUX
    override val hasPermissions: Boolean = true // Desktop on Linux always has permission to send notifications

    override val capabilities: NotificationCapabilities = NotificationCapabilities(
        isTitleSupported = true,
        isDescriptionSupported = true,
        isIconSupported = true,
        isActionSupported = true,
        isPopSupported = true,
        isSoundSupported = true,
        isCustomSoundSupported = true,
        isGroupingSupported = false,
        isStatusIconSupported = false
    )

    private var lastNotification: AtomicReference<Notification?> = AtomicReference(null)
    internal var activationHandler: AtomicReference<ActivationHandler?> = AtomicReference(null)

    override fun getHandle(tag: String): NotificationHandle {
        require(tag.isNotEmpty()) { "Handle tag may not be empty" }
        return LinuxNotificationHandle(tag, this)
    }

    override suspend fun push(notification: Notification, tag: String): NotificationHandle {
        // If this notification is already pending or active, return early
        if (NotificationDispatcher.hasTag(tag)
            || NotificationDispatcher.hasPendingTag(tag)
        ) return NotificationHandle.INVALID
        NotificationDispatcher.enqueue(PendingNotification(tag, lastNotification == notification, notification, this))
        lastNotification.store(notification)
        return LinuxNotificationHandle(tag, this) // Early return so we don't block
    }

    override suspend fun update(handle: NotificationHandle, notification: Notification): NotificationHandle {
        handle.check<LinuxNotificationHandle>()
        val tag = handle.tag
        // If the notification is already active, just update it immediately
        val actualHandle = NotificationDispatcher.findNotification(tag)
        if (actualHandle != null) { // TODO: this logic should be encapsulated in NotificationDispatcher
            val notificationAddress =
                requireNotNull(actualHandle.address) { "Handle does not contain a valid address" }
            LibNotify.notify_notification_update(
                notification = notificationAddress,
                summary = notification.title,
                body = notification.description,
                icon = NotificationDispatcher.DEFAULT_ICON
            )
            NotificationDispatcher.setIcon(notificationAddress, notification)
            return if (NotificationDispatcher.show(notificationAddress)) handle
            else NotificationHandle.INVALID
        }
        // Otherwise update the pending notification associated with the given tag
        val outdatedEntry = NotificationDispatcher.findPendingNotification(tag) ?: return NotificationHandle.INVALID
        val outdatedIndex = NotificationDispatcher.getPendingNotificationIndex(outdatedEntry)
        if (outdatedIndex == -1) return NotificationHandle.INVALID
        val isSameContent = lastNotification == notification
        NotificationDispatcher.updatePendingNotification(
            outdatedIndex,
            PendingNotification(tag, isSameContent, notification, this)
        )
        lastNotification.store(notification)
        return handle
    }

    override suspend fun pop(handle: NotificationHandle) {
        handle.check<LinuxNotificationHandle>()
        val tag = handle.tag
        // If the notification is already active, close it
        val actualHandle = NotificationDispatcher.findNotification(tag)
        if (actualHandle != null) {
            val address =
                requireNotNull(actualHandle.address) { "Handle does not contain a valid address" }
            memScoped {
                val errorAddress = allocPointerTo<GError>()
                if (LibNotify.notify_notification_close(address, errorAddress.ptr) == 0) {
                    errorAddress.value?.freeAndThrow()
                    return
                }
            }
            LibGObject.g_object_unref(address.reinterpret())
            NotificationDispatcher.removeNotification(tag)
            return
        }
        // Otherwise dequeue the pending notification early
        val outdatedEntry = NotificationDispatcher.findPendingNotification(tag) ?: return
        val outdatedIndex = NotificationDispatcher.getPendingNotificationIndex(outdatedEntry)
        if (outdatedIndex == -1) return
        NotificationDispatcher.removePendingNotification(outdatedIndex)
    }

    override suspend fun clearAll() {
        val activeToRemove = NotificationDispatcher.getNotificationsFor(this)
        for (notification in activeToRemove) {
            memScoped {
                val errorAddress = allocPointerTo<GError>()
                if (LibNotify.notify_notification_close(notification.address, errorAddress.ptr) == 0) {
                    errorAddress.value?.freeAndThrow()
                }
            }
            NotificationDispatcher.removeNotification(notification.tag)
        }
        val pendingToRemove = NotificationDispatcher.getPendingNotificationsFor(this)
        for (pending in pendingToRemove) {
            val pendingIndex = NotificationDispatcher.getPendingNotificationIndex(pending)
            NotificationDispatcher.removePendingNotification(pendingIndex)
        }
    }

    override suspend fun unregister() {
        clearAll()
        close()
    }

    override fun close() {
        closeCallback()
    }
}

@OptIn(ExperimentalAtomicApi::class)
actual fun NotificationHandler.withActivationHandler(handler: ActivationHandler): NotificationHandler {
    check<LinuxNotificationHandler>()
    activationHandler.store(handler)
    return this
}

actual fun NotificationHandler(
    name: String,
    id: String,
    priority: NotificationPriority,
    appId: String
): NotificationHandler {
    return if (!NotificationDispatcher.isAvailable) NoopNotificationHandler
    else try {
        LinuxNotificationHandler(appId = appId, name = name, id = id, priority = priority)
    } catch (_: Throwable) {
        NoopNotificationHandler
    }
}
