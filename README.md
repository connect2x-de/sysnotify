# Sysnotify
Cross-platform system notifications for Kotlin Multiplatform.

![](doc/sysnotify-arch.png)

## Example
Using Sysnotify is as easy as creating a `NotificationHandler` and calling the `push` function!  
The API is also completely thread-safe and works nicely with coroutines.

```kotlin
import de.connect2x.sysnotify.Notification
import de.connect2x.sysnotify.NotificationHandler
import kotlinx.coroutines.GlobalScope

// Android usage example
class MyApplication : Application {
    companion object {
        internal lateinit var notificationHandler: NotificationHandler
    }

    override fun onCreate() {
        super.onCreate()
        notificationHandler = NotificationHandler.create("MyApp", this)
    }

    internal fun onMessageReceived(message: Message) {
        // Process the message..
        GlobalScope.async {
            notificationHandler.push(
                Notification(
                    title = message.sender.name,
                    description = message.content
                )
            )
        }
    }
}
```

## Supported platforms

### JVM
The JVM supports all native targets mentioned below for every architecture supported by Kotlin/Native.

### Android
Android is supported starting with **API level 28**.  
On Android you need to declare the `android.permission.POST_NOTIFICATIONS` permission in your app
manifest starting with **API level 33**. See [here](https://developer.android.com/develop/ui/views/notifications/build-notification?hl=en).

### macOS/iOS
macOS and iOS are supported starting with versions **10.14** and **10.0** respectively.

### Windows
Windows is supported starting with Windows 10.

### Linux
Linux is supported if your distribution/desktop environment supports [libnotify](https://github.com/GNOME/libnotify).  
This includes desktop environments which use Qt or GTK like [GNOME](https://www.gnome.org) and [KDE](https://kde.org).  
Note that you need to have `glib-2.0` and `gdk-pixbuf-2.0` installed on a GNOME systems.  
