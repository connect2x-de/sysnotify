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

import platform.Foundation.NSLock
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationContent
import platform.UserNotifications.UNNotificationPresentationOptionBanner
import platform.UserNotifications.UNNotificationPresentationOptionSound
import platform.UserNotifications.UNNotificationPresentationOptions
import platform.UserNotifications.UNNotificationResponse
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
import platform.darwin.NSObject

internal typealias ActionHandler = (
    identifier: String,
    action: String,
    content: UNNotificationContent,
    notification: Notification
) -> Unit

internal class DefaultNotificationDelegate : NSObject(), UNUserNotificationCenterDelegateProtocol {
    private var _actionHandler: ActionHandler? = null
    private val actionHandlerLock: NSLock = NSLock()

    var actionHandler: ActionHandler?
        get() = actionHandlerLock.withLock(::_actionHandler)
        set(value) {
            actionHandlerLock.withLock { _actionHandler = value }
        }

    inline fun addActionHandler(crossinline handler: ActionHandler) = actionHandlerLock.withLock {
        val oldHandler = _actionHandler
        _actionHandler = { identifier, action, content, notification ->
            oldHandler?.invoke(identifier, action, content, notification)
            handler(identifier, action, content, notification)
        }
    }

    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        willPresentNotification: UNNotification,
        withCompletionHandler: (UNNotificationPresentationOptions) -> Unit
    ) {
        withCompletionHandler(UNNotificationPresentationOptionBanner or UNNotificationPresentationOptionSound)
    }

    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        didReceiveNotificationResponse: UNNotificationResponse,
        withCompletionHandler: () -> Unit
    ) {
        val identifier = didReceiveNotificationResponse.notification.request.identifier
        val action = didReceiveNotificationResponse.actionIdentifier
        val content = didReceiveNotificationResponse.notification.request.content
        val notification = Notification(
            title = content.title,
            description = content.body,
            callbackData = content.userInfo[USERINFO_CALLBACK_DATA] as? String,
            group = content.categoryIdentifier
        )
        actionHandler?.invoke(identifier, action, content, notification)
        withCompletionHandler()
    }
}
