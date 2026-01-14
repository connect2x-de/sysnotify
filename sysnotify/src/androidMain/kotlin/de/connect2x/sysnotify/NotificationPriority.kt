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

import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * The native notification importance associated with this priority enum.
 */
inline val NotificationPriority.nativeImportance: Int
    get() = when (this) {
        NotificationPriority.LOW -> NotificationManagerCompat.IMPORTANCE_LOW
        NotificationPriority.HIGH -> NotificationManagerCompat.IMPORTANCE_HIGH
        else -> NotificationManagerCompat.IMPORTANCE_DEFAULT
    }

/**
 * The native notification priority associated with this priority enum.
 */
inline val NotificationPriority.nativePriority: Int
    get() = when (this) {
        NotificationPriority.LOW -> NotificationCompat.PRIORITY_LOW
        NotificationPriority.HIGH -> NotificationCompat.PRIORITY_HIGH
        else -> NotificationCompat.PRIORITY_DEFAULT
    }
