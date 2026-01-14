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

package de.connect2x.sysnotify

interface NativeActivationCallback {
    companion object {
        fun create(closure: (Notification) -> Unit): NativeActivationCallback =
            object : NativeActivationCallback {
                // TODO: this is a horribly unreadable mess, find some nicer way for this eventually
                override fun invoke(notification: Long) {
                    val iconWidth = Memory.getInt(notification + Memory.pointerSize * 3)
                    val iconHeight = Memory.getInt(notification + Memory.pointerSize * 3 + 4)
                    closure(
                        Notification(
                            title = Memory.getString(Memory.getPointer(notification)),
                            description = Memory.getString(Memory.getPointer(notification + Memory.pointerSize)),
                            icon = NotificationIcon(
                                data = Memory.getByteArray(
                                    Memory.getPointer(notification + Memory.pointerSize * 2),
                                    (iconWidth * iconHeight) shl 2
                                ),
                                width = iconWidth,
                                height = iconHeight
                            ),
                            callbackData = Memory.getString(Memory.getPointer(notification + Memory.pointerSize * 3 + 8)),
                            dismissible = Memory.getBoolean(notification + Memory.pointerSize * 4 + 8),
                            priority = NotificationPriority.entries[Memory.getInt(notification + Memory.pointerSize * 4 + 12)],
                            playSound = Memory.getBoolean(notification + Memory.pointerSize * 4 + 16),
                            soundPath = Memory.getString(Memory.getPointer(notification + Memory.pointerSize * 4 + 20)),
                            group = Memory.getString(Memory.getPointer(notification + Memory.pointerSize * 5 + 20))
                        )
                    )
                }
            }
    }

    fun invoke(notification: Long)
}
