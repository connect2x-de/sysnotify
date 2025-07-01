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

@file:JvmName("NotificationHandle$") // Work around duplicate JVM class error

package de.connect2x.sysnotify

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.jvm.JvmName

/**
 * Represents a handle to a notification that has been pushed to the system.
 *
 * A notification handle is a reference that can be used to update or remove
 * a notification after it has been displayed. Each handle has a unique tag
 * that identifies it within the notification system.
 */
interface NotificationHandle {
    /**
     * The unique identifier for this notification handle.
     *
     * This tag is used to identify the notification within the system and
     * should not be empty for valid handles.
     */
    val tag: String

    /**
     * Indicates whether this handle is valid and can be used for operations.
     *
     * A handle is considered valid if its tag is not empty. Invalid handles
     * cannot be used to update or remove notifications.
     */
    val isValid: Boolean
        get() = tag.isNotEmpty()

    /**
     * A singleton representing an invalid notification handle.
     *
     * This is returned by operations that fail to create or update a notification,
     * such as when permissions are not granted or when the notification system
     * is not available.
     */
    object INVALID : NotificationHandle {
        override val tag: String = ""
    }
}

/**
 * Checks if this notification handle is valid and of the expected type.
 *
 * This function uses Kotlin contracts to ensure type safety when working with
 * platform-specific notification handle implementations. It throws an exception
 * if the handle is not valid.
 *
 * @param H The expected type of the notification handle
 * @throws IllegalStateException if the handle is not valid
 */
@OptIn(ExperimentalContracts::class)
internal inline fun <reified H : NotificationHandle> NotificationHandle.check() {
    contract { returns() implies (this@check is H) }
    check(isValid) { "Notification handle must be valid" }
}
