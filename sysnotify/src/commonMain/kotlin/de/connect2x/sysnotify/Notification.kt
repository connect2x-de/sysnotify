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

/**
 * Describes a notification including its title, description and icon among other things.
 *
 * @param title The title of the notification, should be short.
 * @param description The description or "content" of a notification.
 * @param icon The icon shown as part of the notification. This can be the application icon or an avatar.
 * @param callbackData A string that is passed to the application when it is started from activating a notification.
 * @param dismissible Determines whether this notification can be "swiped away" by the user. False means the
 *  notification will remain persistent until explicitly popped.
 * @param priority The priority of this notification within the same notification handler context.
 *  This usually affects how notifications within the same group are sorted.
 * @param playSound Whether to play a sound when this notification appears or not.
 * @param soundPath The URI to a sound file to play instead of the default sound when supported.
 *  Null means the default sound will be used. The path structure may vary depending on the platform.
 * @param group All notifications with the same group tag will be visually grouped together on the
 *  target platform if supported. Null counts as the default group.
 */
data class Notification(
    val title: String? = null,
    val description: String? = null,
    val icon: NotificationIcon? = null,
    val callbackData: String? = null,
    val dismissible: Boolean = true,
    val priority: NotificationPriority = NotificationPriority.DEFAULT,
    val playSound: Boolean = true,
    val soundPath: String? = null,
    val group: String? = null,
)
