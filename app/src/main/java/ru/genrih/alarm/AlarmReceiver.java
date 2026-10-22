package ru.genrih.alarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class AlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!AlarmSettings.enabled(context)) return;
        try {
            // Re-arm first, so a daily alarm continues even when UI is not opened.
            AlarmScheduler.schedule(context);
        } catch (Exception e) {
            Log.e("GenrihAlarm", "Could not schedule tomorrow's alarm", e);
        }
        Intent service = new Intent(context, AlarmService.class);
        service.setAction(AlarmService.START);
        try {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(service);
            else context.startService(service);
        } catch (Exception e) {
            Log.e("GenrihAlarm", "Could not start alarm sound", e);
        }
    }
}
