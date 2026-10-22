package ru.genrih.alarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        try { AlarmScheduler.schedule(context); }
        catch (Exception e) { Log.w("GenrihAlarm", "Reschedule failed", e); }
    }
}
