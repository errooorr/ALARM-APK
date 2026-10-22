package ru.genrih.alarm;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

public class AlarmService extends Service {
    public static final String START = "ru.genrih.alarm.START";
    public static final String STOP = "ru.genrih.alarm.STOP";
    private static final String CHANNEL = "alarm_ringing_v1";
    private static final int NOTIFICATION_ID = 200;
    private MediaPlayer player;
    private PowerManager.WakeLock wakeLock;
    private boolean usingFallback = false;

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null && Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "Звучащий будильник",
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Уведомления при срабатывании будильника");
            channel.setSound(null, null); // The selected song is played by MediaPlayer instead.
            channel.enableVibration(true);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(channel);
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        if (player == null) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GenrihAlarm:Ring");
                wakeLock.acquire(10 * 60 * 1000L); // Bound battery use even if user cannot dismiss.
            }
            String saved = AlarmSettings.songUri(this);
            Uri audio = saved.isEmpty() ? null : Uri.parse(saved);
            startPlayback(audio);
        }
        return START_NOT_STICKY;
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, AlarmActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPending = PendingIntent.getActivity(this, 201, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stop = new Intent(this, StopReceiver.class);
        PendingIntent stopPending = PendingIntent.getBroadcast(this, 202, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Генрих: подъём!")
                .setContentText("Нажми «Выключить», чтобы остановить песню")
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setContentIntent(openPending)
                .setFullScreenIntent(openPending, true)
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_menu_close_clear_cancel, "Выключить", stopPending).build())
                .build();
    }

    private void startPlayback(Uri desired) {
        releasePlayer();
        if (desired == null) {
            desired = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        }
        if (desired == null) {
            desired = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        }
        if (desired == null) return;
        final MediaPlayer mp = new MediaPlayer();
        player = mp;
        try {
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            mp.setDataSource(this, desired);
            mp.setLooping(true);
            mp.setOnPreparedListener(MediaPlayer::start);
            mp.setOnErrorListener((p, what, extra) -> {
                Log.w("GenrihAlarm", "Unable to play selected file: " + what + "/" + extra);
                fallback();
                return true;
            });
            mp.prepareAsync();
        } catch (Exception e) {
            Log.e("GenrihAlarm", "Playback failed", e);
            fallback();
        }
    }

    private void fallback() {
        if (usingFallback) { stopSelf(); return; }
        usingFallback = true;
        startPlayback(null);
    }

    private void releasePlayer() {
        if (player == null) return;
        try { player.setOnErrorListener(null); player.reset(); player.release(); }
        catch (Exception ignored) { }
        player = null;
    }

    @Override public void onDestroy() {
        releasePlayer();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
