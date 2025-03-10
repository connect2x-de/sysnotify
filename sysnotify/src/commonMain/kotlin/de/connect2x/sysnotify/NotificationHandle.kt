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
 * This interface describes a notification that was created by an [NotificationHandler] instance
 * on any given platform.
 */
interface NotificationHandle {
    // TODO: document this
    val tag: String

    // TODO: document this
    val isValid: Boolean
        get() = tag.isNotEmpty()

    /**
     * This object is used to represent handles which are non-null but not valid for use with any
     * other functions that accept them as their parameters.
     */
    object INVALID : NotificationHandle {
        override val tag: String = ""
    }
}
