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

import ru.genrih.alarm.music.AlarmPlaylistPicker;
import ru.genrih.alarm.music.AlarmPlaylistRepository;

import java.util.ArrayList;
import java.util.List;

public class AlarmService extends Service {
    public static final String START = "ru.genrih.alarm.START";
    public static final String STOP = "ru.genrih.alarm.STOP";
    private static final String CHANNEL = "alarm_ringing_v1";
    private static final int NOTIFICATION_ID = 200;
    private MediaPlayer player;
    private PowerManager.WakeLock wakeLock;
    public static final String EXTRA_PREVIEW = "ru.genrih.alarm.PREVIEW";
    private static final int PLAYLIST = 1;
    private static final int SAVED = 2;
    private static final int SYSTEM = 3;
    private final List<PlaybackChoice> playbackChoices = new ArrayList<>();
    private int nextChoice;
    private boolean stopping;
    private boolean preview;

    private static final class PlaybackChoice {
        final Uri uri;
        final int source;
        PlaybackChoice(Uri uri, int source) { this.uri = uri; this.source = source; }
    }

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
            stopping = true;
            releasePlayer();
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
            stopping = false;
            preview = intent != null && intent.getBooleanExtra(EXTRA_PREVIEW, false);
            prepareChoices();
            tryNextChoice();
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

    private void prepareChoices() {
        playbackChoices.clear();
        nextChoice = 0;

        Uri playlist = AlarmPlaylistPicker.pickTrack(this);
        if (playlist != null) playbackChoices.add(new PlaybackChoice(playlist, PLAYLIST));

        String saved = AlarmSettings.songUri(this);
        if (saved != null && !saved.isEmpty()) {
            Uri old = Uri.parse(saved);
            if (!old.equals(playlist)) playbackChoices.add(new PlaybackChoice(old, SAVED));
        }

        Uri systemAlarm = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        Uri systemNotification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        if (systemAlarm != null) playbackChoices.add(new PlaybackChoice(systemAlarm, SYSTEM));
        if (systemNotification != null && !systemNotification.equals(systemAlarm)) {
            playbackChoices.add(new PlaybackChoice(systemNotification, SYSTEM));
        }
    }

    private void tryNextChoice() {
        if (stopping) return;
        releasePlayer();
        if (nextChoice >= playbackChoices.size()) {
            Log.e("GenrihAlarm", "All alarm audio sources failed");
            stopping = true;
            stopSelf();
            return;
        }
        final PlaybackChoice choice = playbackChoices.get(nextChoice++);
        final MediaPlayer mp = new MediaPlayer();
        player = mp;
        try {
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            mp.setDataSource(this, choice.uri);
            mp.setLooping(true);
            mp.setOnPreparedListener(prepared -> {
                if (stopping || player != prepared) return;
                try {
                    prepared.start();
                    if (choice.source == PLAYLIST && !preview) {
                        AlarmPlaylistRepository.getInstance(this).recordPlayed(choice.uri);
                    }
                } catch (RuntimeException e) {
                    Log.e("GenrihAlarm", "Could not start alarm audio", e);
                    if (player == prepared && !stopping) tryNextChoice();
                }
            });
            mp.setOnErrorListener((failed, what, extra) -> {
                Log.w("GenrihAlarm", "Audio source failed: " + what + "/" + extra);
                if (player == failed && !stopping) tryNextChoice();
                return true;
            });
            mp.prepareAsync();
        } catch (Exception e) {
            Log.e("GenrihAlarm", "Cannot prepare alarm audio", e);
            if (player == mp && !stopping) tryNextChoice();
        }
    }

    private void releasePlayer() {
        MediaPlayer old = player;
        player = null; // Invalidate asynchronous callbacks before touching the old player.
        if (old == null) return;
        try {
            old.setOnPreparedListener(null);
            old.setOnErrorListener(null);
            old.reset();
        } catch (Exception ignored) { }
        try { old.release(); } catch (Exception ignored) { }
    }

    @Override public void onDestroy() {
        stopping = true;
        releasePlayer();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
