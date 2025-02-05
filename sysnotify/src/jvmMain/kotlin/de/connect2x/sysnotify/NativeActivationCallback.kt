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

interface NativeActivationCallback {
    companion object {
        fun create(closure: (Notification) -> Unit): NativeActivationCallback =
            object : NativeActivationCallback {
                // TODO: this is a horribly unreadable mess, find some nicer way for this eventually
                override fun invoke(notification: Long) = with(Memory.instance) {
                    val iconWidth = getInt(notification + pointerSize * 3)
                    val iconHeight = getInt(notification + pointerSize * 3 + 4)
                    closure(
                        Notification(
                            title = getString(getPointer(notification)),
                            description = getString(getPointer(notification + pointerSize)),
                            icon = NotificationIcon(
                                data = getByteArray(
                                    getPointer(notification + pointerSize * 2),
                                    (iconWidth * iconHeight) shl 2
                                ),
                                width = iconWidth,
                                height = iconHeight
                            ),
                            callbackData = getString(getPointer(notification + pointerSize * 3 + 8)),
                            dismissible = getBoolean(notification + pointerSize * 4 + 8),
                            priority = NotificationPriority.entries[getInt(notification + pointerSize * 4 + 12)],
                            playSound = getBoolean(notification + pointerSize * 4 + 16),
                            soundPath = getString(getPointer(notification + pointerSize * 4 + 20)),
                            group = getString(getPointer(notification + pointerSize * 5 + 20))
                        )
                    )
                }
            }
    }

    fun invoke(notification: Long)
}
