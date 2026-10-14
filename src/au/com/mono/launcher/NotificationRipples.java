package au.com.mono.launcher;

import android.app.Notification;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/**
 * When the user grants notification access, each new notification sends a ripple across the home
 * screen from that app's icon. Only the app's package name is used; notification content is never read.
 */
public class NotificationRipples extends NotificationListenerService {
    interface Listener {
        void rippleFor(String packageName);
    }

    static volatile Listener listener;
    final Handler main = new Handler(Looper.getMainLooper());

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        final Listener target = listener;
        if (target == null || sbn == null || sbn.isOngoing() || getPackageName().equals(sbn.getPackageName())) {
            return;
        }
        Notification n = sbn.getNotification();
        if (n != null && (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) {
            return;
        }
        final String pkg = sbn.getPackageName();
        this.main.post(new Runnable() {
            @Override
            public void run() {
                target.rippleFor(pkg);
            }
        });
    }
}
