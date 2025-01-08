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

/**
 * @author Alexander Hinze
 * @since 07/01/2025
 *
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
