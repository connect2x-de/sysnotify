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

import android.app.Notification
import kotlin.uuid.Uuid

/**
 * Android-specific implementation of [NotificationHandle] that wraps an Android [Notification] object.
 *
 * This class is used internally by the Sysnotify Android notification subsystem to track and manage notifications.
 *
 * @property tag A unique identifier for this notification handle. Defaults to a random UUID.
 */
internal class AndroidNotificationHandle(
    override val tag: String = Uuid.random().toHexString()
) : NotificationHandle {
    /**
     * The actual Android [Notification] object that this handle references.
     * This can be null if the notification hasn't been created yet or has been removed.
     */
    var value: Notification? = null

    /**
     * Compares this handle with another object for equality.
     * Two [AndroidNotificationHandle] instances are considered equal if they have the same [tag].
     *
     * @param other The object to compare with.
     * @return true if the objects are equal, false otherwise.
     */
    override fun equals(other: Any?): Boolean {
        return if (other !is AndroidNotificationHandle) false
        else tag == other.tag
    }

    /**
     * Returns a hash code value for this handle based on its [tag].
     *
     * @return The hash code value.
     */
    override fun hashCode(): Int = tag.hashCode()

    /**
     * Returns a string representation of this handle, which is its [tag].
     *
     * @return The string representation.
     */
    override fun toString(): String = tag
}

/**
 * Extension property to access the Android [Notification] object from a [NotificationHandle].
 *
 * This property performs several checks to ensure the handle is valid and contains a proper
 * Android notification reference before returning it.
 *
 * @throws IllegalArgumentException If the handle is not an [AndroidNotificationHandle],
 *                                  if the handle is not valid, or if the handle does not
 *                                  contain a valid Android Notification reference.
 * @return The Android [Notification] object.
 */
val NotificationHandle.notification: Notification
    get() {
        require(this is AndroidNotificationHandle) { "Handle is not an Android notification handle" }
        require(isValid) { "Handle must be valid" }
        return requireNotNull(value) { "Handle does not contain a valid Android Notification reference" }
    }
