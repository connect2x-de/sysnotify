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

import java.awt.image.BufferedImage

private data class JvmNotificationHandle(
    val address: Long,
) : NotificationHandle

private class JvmNotificationHandler(
    override val name: String,
    override val id: String,
    override val priority: NotificationPriority,
    private val activationCallback: (Notification) -> Unit,
) : NotificationHandler {
    private val address: Long = NativeNotificationHandler.create(name, id, {
        activationCallback(it) // Delegate the call to the closure
    })
    override val platform: NotificationPlatform =
        NotificationPlatform.entries[NativeNotificationHandler.getPlatform(address)]
    override val hasPermissions: Boolean = false // TODO: Implement hasPermissions flag

    override val capabilities: NotificationCapabilities = NotificationCapabilities(
        NativeNotificationHandler.getCapabilities(address).toUInt()
    )

    override fun close() {
        NativeNotificationHandler.destroy(address)
    }

    override suspend fun requestPermissions() {
        NativeNotificationHandler.requestPermissions(address)
    }

    override fun create(notification: Notification): NotificationHandle =
        InvalidNotificationHandle // TODO: implement this

    override suspend fun push(notification: Notification): NotificationHandle? {
        val handle = NativeNotificationHandler.push(
            handler = address,
            title = notification.title ?: "Unknown",
            description = notification.description,
            icon = notification.icon?.data,
            iconWidth = notification.icon?.width ?: 0,
            iconHeight = notification.icon?.height ?: 0,
        )
        return if (handle != 0L) JvmNotificationHandle(handle)
        else null
    }

    override suspend fun update(
        handle: NotificationHandle,
        notification: Notification
    ): NotificationHandle? {
        require(handle is JvmNotificationHandle)
        val newHandle = NativeNotificationHandler.update(
            handler = address,
            notification = handle.address,
            title = notification.title ?: "Unknown",
            description = notification.description,
            icon = notification.icon?.data,
            iconWidth = notification.icon?.width ?: 0,
            iconHeight = notification.icon?.height ?: 0,
        )
        return if (newHandle != 0L) JvmNotificationHandle(newHandle)
        else null
    }

    override suspend fun pop(handle: NotificationHandle) {
        require(handle is JvmNotificationHandle)
        NativeNotificationHandler.pop(address, handle.address)
    }
}

fun NotificationHandler.Companion.create(
    name: String,
    id: String = name,
    activationCallback: (Notification) -> Unit = {},
    priority: NotificationPriority = NotificationPriority.DEFAULT,
): NotificationHandler = JvmNotificationHandler(name, id, priority, activationCallback)

fun NotificationIcon.Companion.fromBufferedImage(image: BufferedImage): NotificationIcon {
    val width = image.width
    val height = image.height
    val iconPixelCount = width * height
    val rawPixelData = IntArray(iconPixelCount) { 0 }
    image.getRGB(0, 0, width, height, rawPixelData, 0, width)
    val iconDataSize = iconPixelCount shl 2
    val iconData = ByteArray(iconDataSize) { 0 }
    // We need to swizzle the color channels from ARGB to RGBA
    for (i in rawPixelData.indices) {
        val pixel = rawPixelData[i]
        val componentIndex = i shl 2
        iconData[componentIndex] = ((pixel shr 16) and 0xFF).toByte()
        iconData[componentIndex + 1] = ((pixel shr 8) and 0xFF).toByte()
        iconData[componentIndex + 2] = (pixel and 0xFF).toByte()
        iconData[componentIndex + 3] = ((pixel shr 24) and 0xFF).toByte()
    }
    return NotificationIcon(iconData, width, height)
}
