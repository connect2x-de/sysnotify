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

@file:JvmName("NotificationHandler\$Activation")

package de.connect2x.sysnotify

import kotlin.jvm.JvmName

/**
 * Registers a callback function that will be invoked when a notification created by this handler is activated (clicked) by the user.
 *
 * This extension function allows setting up a handler that processes user interactions with notifications.
 * When a user clicks or activates a notification, the provided [handler] function will be called with the
 * [Notification] object that was activated.
 *
 * @param handler The callback function to be invoked when a notification is activated. It receives the [Notification]
 *                that was activated as its parameter.
 * @return The same [NotificationHandler] instance, allowing for method chaining.
 */
expect fun NotificationHandler.withActivationHandler(handler: ActivationHandler): NotificationHandler
