package au.com.mono.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Rings reminders, handles Mark complete and Snooze from the notification, and restores alarms after a reboot. */
public class ReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        int id = intent.getIntExtra(Reminders.EXTRA_ID, -1);
        if (Reminders.ACTION_FIRE.equals(action)) {
            Reminders.fire(context, id);
        } else if (Reminders.ACTION_COMPLETE.equals(action)) {
            Reminders.complete(context, id);
        } else if (Reminders.ACTION_SNOOZE.equals(action)) {
            Reminders.snooze(context, id, Reminders.SNOOZE_MINUTES);
        } else if (Reminders.ACTION_DISMISSED.equals(action)) {
            Reminders.dismissed(context, id);
        } else if (Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            Reminders.restore(context);
        }
    }
}
