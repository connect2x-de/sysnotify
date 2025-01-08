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

@file:OptIn(ExperimentalForeignApi::class)

package de.connect2x.sysnotify

import kotlinx.cinterop.ExperimentalForeignApi
import libnotify.NotifyUrgency

internal fun NotificationPriority.toUrgency(): NotifyUrgency = when (this) {
    NotificationPriority.LOW -> NotifyUrgency.NOTIFY_URGENCY_LOW
    NotificationPriority.DEFAULT -> NotifyUrgency.NOTIFY_URGENCY_NORMAL
    NotificationPriority.HIGH -> NotifyUrgency.NOTIFY_URGENCY_CRITICAL
}
