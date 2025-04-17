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

object NativeNotificationHandler {
    init {
        NativeLoader.ensureLoaded()
    }

    @JvmStatic
    external fun create( // @formatter:off
        name: String,
        id: String,
        priority: Int,
        activationCallback: NativeActivationCallback
    ): Long // @formatter:on

    @JvmStatic
    external fun hasPermissions(handler: Long): Boolean

    @JvmStatic
    external fun getPlatform(handler: Long): Int

    @JvmStatic
    external fun getCapabilities(handler: Long): Int

    @JvmStatic
    external fun requestPermissions( // @formatter:off
        handler: Long,
        callback: NativePermissionCallback
    ) // @formatter:on

    @JvmStatic
    external fun push(
        handler: Long,
        tag: String,
        title: String,
        description: String?,
        icon: ByteArray?,
        iconWidth: Int,
        iconHeight: Int,
        playSound: Boolean,
        soundPath: String?,
        group: String?,
        callbackData: String?
    ): Long

    @JvmStatic
    external fun update(
        handler: Long,
        notification: Long,
        title: String,
        description: String?,
        icon: ByteArray?,
        iconWidth: Int,
        iconHeight: Int,
        group: String?,
        callbackData: String?
    ): Long

    @JvmStatic
    external fun pop(handler: Long, notification: Long)

    @JvmStatic
    external fun clearAll(handler: Long)

    @JvmStatic
    external fun destroy(handler: Long)
}
