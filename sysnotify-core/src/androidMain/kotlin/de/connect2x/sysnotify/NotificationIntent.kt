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

import android.content.Context
import android.content.Intent

abstract class NotificationIntent(
    context: Context,
    clazz: Class<*>,
    appId: String,
    notification: Notification
) : Intent(context, clazz) {
    companion object {
        const val EXTRA_SYSNOTIFY_APP_ID: String = "sysnotify_app_id"
        const val EXTRA_SYSNOTIFY_NOTIFICATION: String = "sysnotify_notification"
    }

    init {
        putExtra(EXTRA_SYSNOTIFY_APP_ID, appId)
        putExtra(EXTRA_SYSNOTIFY_NOTIFICATION, ParcelableNotification(notification))
    }

    inline val appId: String?
        get() = extras?.getString(EXTRA_SYSNOTIFY_APP_ID)

    @Suppress("DEPRECATION") // New API is 33+
    inline val notification: ParcelableNotification?
        get() = extras?.getParcelable(EXTRA_SYSNOTIFY_NOTIFICATION)
}
