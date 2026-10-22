package ru.genrih.alarm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import java.util.Calendar;

public final class AlarmScheduler {
    private AlarmScheduler() { }

    private static PendingIntent alarmIntent(Context context) {
        Intent intent = new Intent(context, AlarmReceiver.class);
        return PendingIntent.getBroadcast(context, 100, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static void cancel(Context context) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager != null) manager.cancel(alarmIntent(context));
    }

    /** Returns the time of the next saved alarm, or -1 when no alarm is enabled. */
    public static long schedule(Context context) throws SecurityException {
        cancel(context);
        if (!AlarmSettings.enabled(context)) return -1L;
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) throw new IllegalStateException("AlarmManager is unavailable");
        if (Build.VERSION.SDK_INT >= 31 && !manager.canScheduleExactAlarms()) {
            throw new SecurityException("Exact alarm permission is missing");
        }
        Calendar next = Calendar.getInstance();
        next.set(Calendar.HOUR_OF_DAY, AlarmSettings.hour(context));
        next.set(Calendar.MINUTE, AlarmSettings.minute(context));
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        if (next.getTimeInMillis() <= System.currentTimeMillis() + 1000) {
            next.add(Calendar.DAY_OF_YEAR, 1);
        }
        Intent show = new Intent(context, MainActivity.class);
        PendingIntent showIntent = PendingIntent.getActivity(context, 101, show,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        manager.setAlarmClock(new AlarmManager.AlarmClockInfo(next.getTimeInMillis(), showIntent),
                alarmIntent(context));
        return next.getTimeInMillis();
    }
}
