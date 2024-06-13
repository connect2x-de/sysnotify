package de.connect2x.sysnotify

import kotlinx.coroutines.await
import org.w3c.notifications.GRANTED
import org.w3c.notifications.NotificationOptions
import org.w3c.notifications.Notification as JsNotification
import org.w3c.notifications.NotificationPermission as JsNotificationPermission

private class WebNotificationHandle(
    val instance: JsNotification
) : NotificationHandle

private class WebNotificationHandler(
    override val name: String
) : NotificationHandler {
    override val platform: NotificationPlatform = NotificationPlatform.WEB

    override val capabilities: NotificationCapabilities = NotificationCapabilities(
        isTitleSupported = true,
        isDescriptionSupported = true,
        isIconSupported = false, // TODO: implement icon support
        isActionSupported = true,
        isPopSupported = true
    )

    override suspend fun requestPermissions() {
        if (JsNotification.permission == JsNotificationPermission.GRANTED) return
        JsNotification.requestPermission().await()
    }

    override suspend fun push(notification: Notification): NotificationHandle {
        val instance = JsNotification(
            notification.title ?: "Undefined", NotificationOptions(
                body = notification.description
            )
        ).apply {
            notification.action?.let { action ->
                onclick = {
                    it.preventDefault()
                    action()
                }
            }
        }
        return WebNotificationHandle(instance)
    }

    override suspend fun pop(handle: NotificationHandle) {
        if (handle !is WebNotificationHandle) throw IllegalArgumentException("Handle is not a web notification handle")
        handle.instance.close()
    }
}

actual fun createNotificationHandler(
    name: String,
    context: Any?
): NotificationHandler = WebNotificationHandler(name)
