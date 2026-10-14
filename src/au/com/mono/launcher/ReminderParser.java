package au.com.mono.launcher;

import java.util.Calendar;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a spoken reminder such as "remind me to call Mum at 5pm tomorrow" into a task and a time,
 * on the phone with no network. Understands relative times ("in 20 minutes", "in half an hour"),
 * days ("tomorrow", "on Friday"), clock times ("at 7:30", "5 pm", "noon") and parts of the day
 * ("this evening", "tonight").
 */
final class ReminderParser {
    static final class Result {
        final String task;
        /** When the reminder should go off, or -1 when the words didn't include a time. */
        final long when;

        Result(String task, long when) {
            this.task = task;
            this.when = when;
        }
    }

    static final String[] NUMBERS = {"zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
            "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen", "twenty"};
    static final String[] DAYS = {"sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday"};

    static final Pattern HALF_HOUR = Pattern.compile("\\b(?:in|after) (?:half an? hour|a half hour)\\b");
    static final Pattern RELATIVE = Pattern.compile("\\b(?:in|after) (\\d+(?:\\.\\d+)?|an?|a couple of|a few) (minutes?|mins?|hours?|hrs?|days?|weeks?)\\b");
    static final Pattern DAY_AFTER = Pattern.compile("\\b(?:on )?the day after tomorrow\\b");
    static final Pattern TOMORROW = Pattern.compile("\\btomorrow(?: (morning|afternoon|evening|night))?\\b");
    static final Pattern WEEKDAY = Pattern.compile("\\b(?:on |this |next )?(sunday|monday|tuesday|wednesday|thursday|friday|saturday)(?: (morning|afternoon|evening|night))?\\b");
    static final Pattern PART = Pattern.compile("\\b(?:this |in the )(morning|afternoon|evening)\\b|\\b(tonight|today)\\b");
    static final Pattern NOON = Pattern.compile("\\b(?:at |by )?(noon|midday|midnight)\\b");
    static final Pattern CLOCK_AMPM = Pattern.compile("\\b(?:at |by |around )?(\\d{1,2})(?:[:.](\\d{2}))? ?(am|pm)\\b");
    static final Pattern CLOCK_COLON = Pattern.compile("\\b(?:at |by |around )?(\\d{1,2}):(\\d{2})\\b");
    static final Pattern CLOCK_AT = Pattern.compile("\\b(?:at|by|around) (\\d{1,2})\\b(?! ?(?:minutes?|mins?|hours?|days?))");
    static final Pattern LEAD = Pattern.compile("^(?:(?:hey|ok|okay|please|mono)[ ,]+)*(?:can you |could you |please )?(?:remind me (?:to |that |about |of )?|set (?:a |an )?reminder (?:to |for |that )?|reminder (?:to |for )?|don't forget (?:to )?|i need to |i have to )");
    static final Pattern EDGES = Pattern.compile("^(?:[ ,.]|to |at |on |in |by |for |and |about )+|(?:[ ,.]|\\bto|\\bat|\\bby|\\bfor|\\band)+$");

    static Result parse(String spoken, Calendar now) {
        String text = normalise(spoken);
        String original = spoken == null ? "" : spoken.trim();
        long when = -1;

        Matcher m = HALF_HOUR.matcher(text);
        if (m.find()) {
            when = now.getTimeInMillis() + 30 * 60000L;
            text = cut(text, m);
        } else if ((m = RELATIVE.matcher(text)).find()) {
            double amount = amount(m.group(1));
            String unit = m.group(2);
            long step = unit.startsWith("m") ? 60000L : unit.startsWith("h") ? 3600000L : unit.startsWith("d") ? 86400000L : 604800000L;
            when = now.getTimeInMillis() + Math.round(amount * step);
            text = cut(text, m);
        } else {
            int dayOffset = -1;
            String part = null;
            if ((m = DAY_AFTER.matcher(text)).find()) {
                dayOffset = 2;
                text = cut(text, m);
            } else if ((m = TOMORROW.matcher(text)).find()) {
                dayOffset = 1;
                part = m.group(1);
                text = cut(text, m);
            } else if ((m = WEEKDAY.matcher(text)).find()) {
                int target = indexOf(DAYS, m.group(1)) + 1;
                int today = now.get(Calendar.DAY_OF_WEEK);
                dayOffset = (target - today + 7) % 7;
                if (dayOffset == 0) {
                    dayOffset = 7;
                }
                part = m.group(2);
                text = cut(text, m);
            }
            if ((m = PART.matcher(text)).find()) {
                String found = m.group(1) != null ? m.group(1) : m.group(2);
                if (!"today".equals(found)) {
                    part = found;
                }
                if (dayOffset < 0) {
                    dayOffset = 0;
                }
                text = cut(text, m);
            }
            if ("tonight".equals(part)) {
                part = "night";
            }
            int hour = -1;
            int minute = 0;
            boolean meridiem = false;
            if ((m = NOON.matcher(text)).find()) {
                hour = "midnight".equals(m.group(1)) ? 0 : 12;
                meridiem = true;
                if (hour == 0 && dayOffset < 0) {
                    dayOffset = 1;
                }
                text = cut(text, m);
            } else if ((m = CLOCK_AMPM.matcher(text)).find()) {
                hour = Integer.parseInt(m.group(1)) % 12 + ("pm".equals(m.group(3)) ? 12 : 0);
                minute = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
                meridiem = true;
                text = cut(text, m);
            } else if ((m = CLOCK_COLON.matcher(text)).find()) {
                hour = Integer.parseInt(m.group(1));
                minute = Integer.parseInt(m.group(2));
                meridiem = hour == 0 || hour > 12;
                text = cut(text, m);
            } else if ((m = CLOCK_AT.matcher(text)).find()) {
                hour = Integer.parseInt(m.group(1));
                meridiem = hour == 0 || hour > 12;
                text = cut(text, m);
            }
            if (hour > 23 || minute > 59) {
                hour = -1;
            }
            if (hour >= 0 || part != null || dayOffset >= 0) {
                boolean explicitDay = dayOffset >= 0;
                if (dayOffset < 0) {
                    dayOffset = 0;
                }
                if (hour < 0) {
                    hour = part == null ? 9 : "morning".equals(part) ? 9 : "afternoon".equals(part) ? 14 : "evening".equals(part) ? 18 : 20;
                    minute = 0;
                } else if (!meridiem && hour < 12) {
                    if (part != null && !"morning".equals(part)) {
                        hour += 12;
                    } else if (part == null && dayOffset == 0) {
                        // "at 5" today means the next 5 o'clock still to come.
                        Calendar am = at(now, 0, hour, minute);
                        if (!am.after(now)) {
                            hour += 12;
                        }
                    } else if (part == null && hour < 7) {
                        hour += 12;
                    }
                }
                Calendar due = at(now, dayOffset, hour, minute);
                if (!explicitDay && !due.after(now)) {
                    due.add(Calendar.DAY_OF_MONTH, 1);
                }
                when = due.getTimeInMillis();
            }
        }
        return new Result(task(text, original), when);
    }

    static Calendar at(Calendar now, int dayOffset, int hour, int minute) {
        Calendar c = (Calendar) now.clone();
        c.add(Calendar.DAY_OF_MONTH, dayOffset);
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c;
    }

    static String normalise(String spoken) {
        String text = (spoken == null ? "" : spoken).toLowerCase(Locale.ROOT).trim();
        text = text.replace("a.m.", "am").replace("p.m.", "pm").replace("o'clock", "").replace("o’clock", "").replace("’", "'");
        text = text.replaceAll("(\\d)(am|pm)\\b", "$1 $2");
        // Spoken numbers next to time words become digits so the patterns above can read them.
        for (int n = NUMBERS.length - 1; n >= 0; n--) {
            text = text.replaceAll("\\b(at|by|around|in|after) " + NUMBERS[n] + "\\b", "$1 " + n);
            text = text.replaceAll("\\b" + NUMBERS[n] + " (am|pm|minutes?|mins?|hours?|days?|weeks?)\\b", n + " $1");
        }
        text = text.replaceAll("\\b(\\d+) (thirty|fifteen|forty five|forty-five)\\b", "$1:$2").replace(":thirty", ":30").replace(":fifteen", ":15").replace(":forty five", ":45").replace(":forty-five", ":45");
        return text.replaceAll("\\s+", " ");
    }

    static double amount(String word) {
        if ("a".equals(word) || "an".equals(word)) {
            return 1;
        }
        if ("a couple of".equals(word)) {
            return 2;
        }
        if ("a few".equals(word)) {
            return 3;
        }
        return Double.parseDouble(word);
    }

    static String cut(String text, Matcher m) {
        return (text.substring(0, m.start()) + " " + text.substring(m.end())).replaceAll("\\s+", " ").trim();
    }

    static int indexOf(String[] values, String value) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(value)) {
                return i;
            }
        }
        return -1;
    }

    static String task(String rest, String original) {
        String task = rest.trim();
        Matcher lead = LEAD.matcher(task);
        if (lead.find()) {
            task = task.substring(lead.end());
        }
        String before;
        do {
            before = task;
            task = EDGES.matcher(task).replaceAll("").trim();
        } while (!task.equals(before));
        if (task.isEmpty()) {
            return "Reminder";
        }
        // Keep the speaker's capitals where the words survived unchanged (names like "Mum").
        int at = original.toLowerCase(Locale.ROOT).indexOf(task);
        if (at >= 0) {
            task = original.substring(at, at + task.length());
        }
        return Character.toUpperCase(task.charAt(0)) + task.substring(1);
    }
}
