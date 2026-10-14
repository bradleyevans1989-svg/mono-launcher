package au.com.mono.launcher;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.text.format.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Voice reminders that ring like an alarm and keep coming back until they are marked complete.
 *
 * Reminders live in the launcher's preferences. Each one has an exact alarm; when it fires the
 * reminder posts an alarm-sound notification with Mark complete and Snooze, and schedules itself
 * to ring again in {@link #NAG_MINUTES} minutes until it is completed.
 */
final class Reminders {
    static final String PREFS = "mono";
    static final String KEY = "reminders";
    static final String CHANNEL = "reminders-alarm";
    static final String QUIET_CHANNEL = "reminders-quiet";
    static final String ACTION_FIRE = "au.com.mono.launcher.REMINDER_FIRE";
    static final String ACTION_COMPLETE = "au.com.mono.launcher.REMINDER_COMPLETE";
    static final String ACTION_SNOOZE = "au.com.mono.launcher.REMINDER_SNOOZE";
    static final String ACTION_DISMISSED = "au.com.mono.launcher.REMINDER_DISMISSED";
    static final String ACTION_SHOW = "au.com.mono.launcher.SHOW_REMINDERS";
    static final String EXTRA_ID = "id";
    static final int NAG_MINUTES = 10;
    static final int SNOOZE_MINUTES = 10;
    static final int KEEP_DONE = 20;

    static final class Item {
        int id;
        String text;
        long due;
        /** The next time the alarm rings: the due time, then every nag or snooze after it. */
        long alertAt;
        boolean ringing;
        boolean done;
        long doneAt;

        JSONObject toJson() throws Exception {
            return new JSONObject().put("id", this.id).put("text", this.text).put("due", this.due).put("alertAt", this.alertAt)
                    .put("ringing", this.ringing).put("done", this.done).put("doneAt", this.doneAt);
        }

        static Item fromJson(JSONObject o) {
            Item item = new Item();
            item.id = o.optInt("id");
            item.text = o.optString("text", "Reminder");
            item.due = o.optLong("due");
            item.alertAt = o.optLong("alertAt", item.due);
            item.ringing = o.optBoolean("ringing");
            item.done = o.optBoolean("done");
            item.doneAt = o.optLong("doneAt");
            return item;
        }
    }

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static synchronized List<Item> all(Context context) {
        List<Item> items = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs(context).getString(KEY, "[]"));
            for (int i = 0; i < array.length(); i++) {
                items.add(Item.fromJson(array.getJSONObject(i)));
            }
        } catch (Exception e) {
            // A damaged list starts again empty rather than breaking the launcher.
        }
        Collections.sort(items, new Comparator<Item>() {
            @Override
            public int compare(Item a, Item b) {
                if (a.done != b.done) {
                    return a.done ? 1 : -1;
                }
                return a.done ? Long.compare(b.doneAt, a.doneAt) : Long.compare(a.due, b.due);
            }
        });
        return items;
    }

    static synchronized void save(Context context, List<Item> items) {
        JSONArray array = new JSONArray();
        int done = 0;
        for (Item item : items) {
            if (item.done && ++done > KEEP_DONE) {
                continue;
            }
            try {
                array.put(item.toJson());
            } catch (Exception e) {
                // Skip an item that can't be written.
            }
        }
        prefs(context).edit().putString(KEY, array.toString()).commit();
    }

    static Item find(List<Item> items, int id) {
        for (Item item : items) {
            if (item.id == id) {
                return item;
            }
        }
        return null;
    }

    static synchronized Item add(Context context, String text, long due) {
        SharedPreferences prefs = prefs(context);
        Item item = new Item();
        item.id = prefs.getInt("reminderNext", 1);
        prefs.edit().putInt("reminderNext", item.id + 1).commit();
        item.text = text == null || text.trim().isEmpty() ? "Reminder" : text.trim();
        item.due = due;
        item.alertAt = due;
        List<Item> items = all(context);
        items.add(item);
        save(context, items);
        schedule(context, item);
        return item;
    }

    static synchronized void complete(Context context, int id) {
        List<Item> items = all(context);
        Item item = find(items, id);
        if (item == null) {
            return;
        }
        item.done = true;
        item.ringing = false;
        item.doneAt = System.currentTimeMillis();
        save(context, items);
        cancelAlarm(context, id);
        notifications(context).cancel(id);
    }

    static synchronized void reopen(Context context, int id) {
        List<Item> items = all(context);
        Item item = find(items, id);
        if (item == null) {
            return;
        }
        item.done = false;
        item.alertAt = Math.max(item.due, System.currentTimeMillis() + 60000L);
        save(context, items);
        schedule(context, item);
    }

    static synchronized void delete(Context context, int id) {
        List<Item> items = all(context);
        Item item = find(items, id);
        if (item != null) {
            items.remove(item);
            save(context, items);
        }
        cancelAlarm(context, id);
        notifications(context).cancel(id);
    }

    static synchronized void snooze(Context context, int id, int minutes) {
        List<Item> items = all(context);
        Item item = find(items, id);
        if (item == null || item.done) {
            return;
        }
        item.ringing = false;
        item.alertAt = System.currentTimeMillis() + minutes * 60000L;
        save(context, items);
        notifications(context).cancel(id);
        schedule(context, item);
    }

    /** The alarm went off: ring now and come back in a few minutes unless it gets marked complete. */
    static synchronized void fire(Context context, int id) {
        List<Item> items = all(context);
        Item item = find(items, id);
        if (item == null || item.done) {
            return;
        }
        item.ringing = true;
        item.alertAt = System.currentTimeMillis() + NAG_MINUTES * 60000L;
        save(context, items);
        notifications(context).notify(id, build(context, item, true));
        schedule(context, item);
    }

    /** Swiping a ringing reminder away doesn't finish it: it comes straight back, quietly, until it's marked complete. */
    static synchronized void dismissed(Context context, int id) {
        Item item = find(all(context), id);
        if (item != null && !item.done && item.ringing) {
            notifications(context).notify(id, build(context, item, false));
        }
    }

    /** After a reboot or update the system forgets alarms, so put them all back. */
    static synchronized void restore(Context context) {
        long now = System.currentTimeMillis();
        for (Item item : all(context)) {
            if (item.done) {
                continue;
            }
            if (item.alertAt <= now) {
                fire(context, item.id);
            } else {
                schedule(context, item);
            }
        }
    }

    static void schedule(Context context, Item item) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) {
            return;
        }
        PendingIntent fire = broadcast(context, ACTION_FIRE, item.id);
        try {
            alarms.setAlarmClock(new AlarmManager.AlarmClockInfo(item.alertAt, openList(context, item.id)), fire);
        } catch (SecurityException e) {
            // Exact alarms were refused; a slightly late reminder is better than none.
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, item.alertAt, fire);
        }
    }

    static void cancelAlarm(Context context, int id) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms != null) {
            alarms.cancel(broadcast(context, ACTION_FIRE, id));
        }
    }

    static PendingIntent broadcast(Context context, String action, int id) {
        Intent intent = new Intent(context, ReminderReceiver.class).setAction(action).putExtra(EXTRA_ID, id);
        int code = id * 8 + (ACTION_FIRE.equals(action) ? 0 : ACTION_COMPLETE.equals(action) ? 1 : ACTION_SNOOZE.equals(action) ? 2 : 3);
        return PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static PendingIntent openList(Context context, int id) {
        Intent intent = new Intent(context, MainActivity.class).setAction(ACTION_SHOW).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static NotificationManager notifications(Context context) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel loud = new NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_HIGH);
            loud.setDescription("Rings like an alarm until you mark the reminder complete");
            Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (sound == null) {
                sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            }
            loud.setSound(sound, new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            loud.enableVibration(true);
            loud.setVibrationPattern(new long[]{0, 600, 400, 600, 400, 600});
            loud.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            manager.createNotificationChannel(loud);
        }
        if (manager.getNotificationChannel(QUIET_CHANNEL) == null) {
            NotificationChannel quiet = new NotificationChannel(QUIET_CHANNEL, "Reminders waiting", NotificationManager.IMPORTANCE_LOW);
            quiet.setDescription("Reminders you haven't marked complete yet");
            manager.createNotificationChannel(quiet);
        }
        return manager;
    }

    static Notification build(Context context, Item item, boolean loud) {
        Notification.Builder builder = new Notification.Builder(context, loud ? CHANNEL : QUIET_CHANNEL)
                .setSmallIcon(Icon.createWithBitmap(bell(context)))
                .setContentTitle(item.text)
                .setContentText("Due " + when(context, item.due) + ". Mark it complete to stop the alarm.")
                .setColor(0xff72e3ef)
                .setCategory(Notification.CATEGORY_ALARM)
                .setOngoing(true)
                .setAutoCancel(false)
                .setShowWhen(true)
                .setWhen(item.due)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(openList(context, item.id))
                .setDeleteIntent(broadcast(context, ACTION_DISMISSED, item.id))
                .addAction(new Notification.Action.Builder((Icon) null, "Mark complete", broadcast(context, ACTION_COMPLETE, item.id)).build())
                .addAction(new Notification.Action.Builder((Icon) null, "Snooze " + SNOOZE_MINUTES + " min", broadcast(context, ACTION_SNOOZE, item.id)).build());
        Notification notification = builder.build();
        if (loud) {
            notification.flags |= Notification.FLAG_INSISTENT;
        }
        return notification;
    }

    /** A plain bell drawn in code, because the launcher ships without drawable resources. */
    static Bitmap bell(Context context) {
        int size = Math.max(24, Math.round(24 * context.getResources().getDisplayMetrics().density));
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        float u = size / 24f;
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0xffffffff);
        canvas.scale(u, u);
        android.graphics.Path body = new android.graphics.Path();
        body.moveTo(12, 3);
        body.cubicTo(8, 3, 6, 6, 6, 10);
        body.lineTo(6, 15);
        body.lineTo(4, 17.5f);
        body.lineTo(20, 17.5f);
        body.lineTo(18, 15);
        body.lineTo(18, 10);
        body.cubicTo(18, 6, 16, 3, 12, 3);
        body.close();
        canvas.drawPath(body, paint);
        canvas.drawCircle(12, 20, 2.2f, paint);
        return bitmap;
    }

    /** "Today 5:00 pm", "Tomorrow 9:00 am" or "Fri 16 Oct, 9:30 am". */
    static String when(Context context, long time) {
        Calendar now = Calendar.getInstance();
        Calendar then = Calendar.getInstance();
        then.setTimeInMillis(time);
        String clock = new SimpleDateFormat(DateFormat.is24HourFormat(context) ? "HH:mm" : "h:mm a", Locale.getDefault()).format(new Date(time)).toLowerCase(Locale.getDefault());
        int days = then.get(Calendar.YEAR) * 400 + then.get(Calendar.DAY_OF_YEAR) - (now.get(Calendar.YEAR) * 400 + now.get(Calendar.DAY_OF_YEAR));
        if (days == 0) {
            return "today " + clock;
        }
        if (days == 1) {
            return "tomorrow " + clock;
        }
        if (days == -1) {
            return "yesterday " + clock;
        }
        return new SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(new Date(time)) + ", " + clock;
    }
}
