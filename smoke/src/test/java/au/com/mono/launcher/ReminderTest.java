package au.com.mono.launcher;

import static org.junit.Assert.*;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import java.util.Calendar;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ReminderTest {
    static Calendar wednesdayHalfThree() {
        Calendar now = Calendar.getInstance();
        now.set(2026, Calendar.OCTOBER, 14, 15, 30, 0);
        now.set(Calendar.MILLISECOND, 0);
        return now;
    }

    static void expect(String spoken, String task, int dayOffset, int hour, int minute) {
        Calendar now = wednesdayHalfThree();
        ReminderParser.Result r = ReminderParser.parse(spoken, now);
        assertEquals(spoken, task, r.task);
        Calendar due = ReminderParser.at(now, dayOffset, hour, minute);
        assertEquals(spoken, due.getTimeInMillis(), r.when);
    }

    @Test
    public void understandsSpokenTimes() {
        expect("Remind me to call Mum at 5pm", "Call Mum", 0, 17, 0);
        expect("remind me to take the bins out tomorrow morning", "Take the bins out", 1, 9, 0);
        expect("dentist on Friday at 9:30 a.m.", "Dentist", 2, 9, 30);
        expect("Buy milk tonight", "Buy milk", 0, 20, 0);
        expect("Call Steve at five thirty", "Call Steve", 0, 17, 30);
        expect("walk the dog at noon", "Walk the dog", 1, 12, 0);
        expect("Gym next Monday 6am", "Gym", 5, 6, 0);
        expect("put washing on at 14:30", "Put washing on", 1, 14, 30);
        Calendar now = wednesdayHalfThree();
        ReminderParser.Result r = ReminderParser.parse("in 20 minutes check the oven", now);
        assertEquals("Check the oven", r.task);
        assertEquals(now.getTimeInMillis() + 20 * 60000L, r.when);
        r = ReminderParser.parse("remind me in half an hour to move the car", now);
        assertEquals("Move the car", r.task);
        assertEquals(now.getTimeInMillis() + 30 * 60000L, r.when);
        r = ReminderParser.parse("feed the cat", now);
        assertEquals("Feed the cat", r.task);
        assertEquals(-1, r.when);
    }

    @Test
    public void ringsUntilMarkedComplete() {
        Context context = ApplicationProvider.getApplicationContext();
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Reminders.Item item = Reminders.add(context, "Take pills", System.currentTimeMillis() + 3600000L);
        assertNotNull("alarm scheduled", Shadows.shadowOf(am).getNextScheduledAlarm());

        Reminders.fire(context, item.id);
        Notification n = Shadows.shadowOf(nm).getNotification(item.id);
        assertNotNull("notification posted", n);
        assertTrue("keeps ringing", (n.flags & Notification.FLAG_INSISTENT) != 0);
        assertTrue("can't be swiped away", (n.flags & Notification.FLAG_ONGOING_EVENT) != 0);
        assertEquals(2, n.actions.length);
        assertTrue(Reminders.all(context).get(0).ringing);

        Shadows.shadowOf(nm).cancel(item.id);
        Reminders.dismissed(context, item.id);
        assertNotNull("comes back after a swipe", Shadows.shadowOf(nm).getNotification(item.id));

        Reminders.snooze(context, item.id, 10);
        assertNull(Shadows.shadowOf(nm).getNotification(item.id));
        assertFalse(Reminders.all(context).get(0).done);

        Reminders.fire(context, item.id);
        Reminders.complete(context, item.id);
        assertNull("stops after Mark complete", Shadows.shadowOf(nm).getNotification(item.id));
        List<Reminders.Item> items = Reminders.all(context);
        assertTrue(items.get(0).done);
        Reminders.fire(context, item.id);
        assertNull("a completed reminder never rings again", Shadows.shadowOf(nm).getNotification(item.id));

        Reminders.delete(context, item.id);
        assertTrue(Reminders.all(context).isEmpty());
    }

    @Test
    public void reminderScreensOpen() {
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        ShadowLooper.idleMainLooper();
        a.showReminders();
        assertNotNull(a.drawer);
        assertTrue(SmokeTest.hasText(a.drawer, "No reminders yet"));
        Reminders.add(a, "Call Mum", System.currentTimeMillis() + 3600000L);
        a.drawerRefresh.run();
        assertTrue(SmokeTest.hasText(a.drawer, "Call Mum"));
        a.confirmReminder("remind me to call the bank tomorrow at 9am");
        a.confirmReminder("");
        ShadowLooper.idleMainLooper();
        a.onBackPressed();
        assertNull(a.drawer);
    }
}
