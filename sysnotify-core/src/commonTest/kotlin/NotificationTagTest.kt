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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class NotificationTagTest {
    private fun handler(id: String): NotificationHandler = object : NotificationHandler by NoopNotificationHandler {
        override val id: String = id
    }

    @Test
    fun `same tag in different handler IDs has distinct keys`() {
        assertNotEquals(handler("messages").scopedTag("status"), handler("calls").scopedTag("status"))
    }

    @Test
    fun `handler ID can be reused across instances`() {
        assertEquals(handler("messages").scopedTag("status"), handler("messages").scopedTag("status"))
    }

    @Test
    fun `different tags in the same handler have distinct keys`() {
        val handler = handler("messages")
        assertNotEquals(handler.scopedTag("first"), handler.scopedTag("second"))
    }
}
