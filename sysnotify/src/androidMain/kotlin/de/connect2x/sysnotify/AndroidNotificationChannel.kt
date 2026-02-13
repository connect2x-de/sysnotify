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

import android.app.NotificationChannel
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationManagerCompat

/**
 * Small abstraction over [android.app.NotificationChannel]
 * to support API levels < 26.
 */
@SysnotifyPrivateApi
internal sealed interface AndroidNotificationChannel {
    @RequiresApi(Build.VERSION_CODES.O)
    data class Real(
        override val id: String,
        override val name: String,
        override val priority: Int
    ) : AndroidNotificationChannel {
        lateinit var channel: NotificationChannel
            private set

        override fun init(context: Context) {
            val manager = NotificationManagerCompat.from(context)
            channel = NotificationChannel(id, name, priority)
            manager.createNotificationChannel(channel)
        }
    }

    data class Fake(
        override val id: String,
        override val name: String,
        override val priority: Int
    ) : AndroidNotificationChannel {
        override fun init(context: Context) = Unit
    }

    val id: String
    val name: String
    val priority: Int

    fun init(context: Context)
}
