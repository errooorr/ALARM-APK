package ru.genrih.alarm;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int FILE_PICK = 7;
    private static final int REQUEST_NOTIFICATIONS = 8;
    private static final int BG = Color.rgb(16, 23, 37);
    private static final int FG = Color.rgb(240, 244, 251);
    private static final int MUTED = Color.rgb(158, 175, 196);
    private int hour;
    private int minute;
    private String selectedUri;
    private String selectedName;
    private TextView timeText;
    private TextView songText;
    private TextView statusText;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        hour = AlarmSettings.hour(this);
        minute = AlarmSettings.minute(this);
        selectedUri = AlarmSettings.songUri(this);
        selectedName = AlarmSettings.songName(this);
        createUi();
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
        }
    }

    private int dp(float value) {
        return (int) (getResources().getDisplayMetrics().density * value + 0.5f);
    }
    private TextView label(String text, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(color);
        t.setTextSize(size);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        return t;
    }
    private Button button(String title, LinearLayout layout, View.OnClickListener action) {
        Button b = new Button(this);
        b.setText(title);
        b.setAllCaps(false);
        b.setTextSize(16);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(56));
        p.topMargin = dp(12);
        layout.addView(b, p);
        b.setOnClickListener(action);
        return b;
    }
    private void createUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(22), dp(38), dp(22), dp(40));
        scroll.addView(root);
        setContentView(scroll);

        TextView title = label("⏰ Генрих Будильник", 26, FG, true);
        root.addView(title);
        TextView sub = label("Своя музыка • каждый день • без интернета", 14, MUTED, false);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.topMargin = dp(12);
        root.addView(sub, sp);

        timeText = label(String.format(Locale.getDefault(), "%02d:%02d", hour, minute), 76, FG, true);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2);
        tp.topMargin = dp(38);
        root.addView(timeText, tp);
        button("🕒 Выбрать время", root, v -> new TimePickerDialog(this,
                (picker, h, m) -> {
                    hour = h;
                    minute = m;
                    timeText.setText(String.format(Locale.getDefault(), "%02d:%02d", hour, minute));
                }, hour, minute, true).show());

        TextView songLabel = label("ПЕСНЯ ИЗ ПАПКИ ТЕЛЕФОНА", 13, MUTED, true);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.topMargin = dp(30);
        root.addView(songLabel, slp);
        songText = label(selectedName, 18, FG, false);
        LinearLayout.LayoutParams snp = new LinearLayout.LayoutParams(-1, -2);
        snp.topMargin = dp(12);
        root.addView(songText, snp);
        button("🎵 Выбрать MP3 / музыку", root, v -> openMusicPicker());

        button("✅ СОХРАНИТЬ И ВКЛЮЧИТЬ", root, v -> saveAlarm());
        button("⏹ Выключить ежедневный будильник", root, v -> disableAlarm());
        button("▶ Проверить звук", root, v -> {
            if (!selectedUri.equals(AlarmSettings.songUri(this))) {
                Toast.makeText(this, "Сначала сохраните выбранную песню", Toast.LENGTH_LONG).show();
                return;
            }
            Intent s = new Intent(this, AlarmService.class).setAction(AlarmService.START);
            try {
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(s);
                else startService(s);
            } catch (Exception e) { Toast.makeText(this, "Ошибка: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
        });
        button("■ Остановить звук", root, v ->
                stopService(new Intent(this, AlarmService.class)));

        statusText = label("", 15, MUTED, false);
        LinearLayout.LayoutParams stp = new LinearLayout.LayoutParams(-1, -2);
        stp.topMargin = dp(28);
        root.addView(statusText, stp);
        updateStatus();

        TextView advice = label("Если звонок не открывается на заблокированном экране, проверь разрешение полноэкранных уведомлений и ограничения батареи для приложения.", 13, MUTED, false);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-1, -2);
        ap.topMargin = dp(28);
        root.addView(advice, ap);
        if (Build.VERSION.SDK_INT >= 34) {
            button("⚙ Разрешение показа поверх блокировки", root, v -> {
                NotificationManager nm = getSystemService(NotificationManager.class);
                if (nm != null && !nm.canUseFullScreenIntent()) {
                    Intent settings = new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                            Uri.parse("package:" + getPackageName()));
                    try { startActivity(settings); }
                    catch (Exception e) { Toast.makeText(this, "Открой настройки уведомлений вручную", Toast.LENGTH_LONG).show(); }
                } else {
                    Toast.makeText(this, "Полноэкранные уведомления уже разрешены", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void openMusicPicker() {
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        pick.setType("audio/*");
        pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(pick, FILE_PICK);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != FILE_PICK || result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
        catch (SecurityException e) {
            Toast.makeText(this, "Файл может стать недоступен после перезагрузки", Toast.LENGTH_LONG).show();
        }
        selectedUri = uri.toString();
        selectedName = getFileName(uri);
        songText.setText(selectedName);
        Toast.makeText(this, "Теперь нажми «Сохранить и включить»", Toast.LENGTH_SHORT).show();
    }

    private String getFileName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                if (name != null && !name.isEmpty()) return name;
            }
        } catch (Exception ignored) { }
        return "Выбранный аудиофайл";
    }

    private void saveAlarm() {
        AlarmSettings.prefs(this).edit()
                .putInt("hour", hour)
                .putInt("minute", minute)
                .putString("song_uri", selectedUri)
                .putString("song_name", selectedName)
                .putBoolean("enabled", true)
                .apply();
        try {
            long next = AlarmScheduler.schedule(this);
            Toast.makeText(this, "Будильник включён: " + AlarmSettings.displayDateTime(next), Toast.LENGTH_LONG).show();
        } catch (SecurityException e) {
            AlarmSettings.prefs(this).edit().putBoolean("enabled", false).apply();
            Toast.makeText(this, "Разреши точные будильники в настройках", Toast.LENGTH_LONG).show();
            if (Build.VERSION.SDK_INT >= 31) {
                try { startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:" + getPackageName()))); }
                catch (Exception ignored) { }
            }
        } catch (Exception e) {
            AlarmSettings.prefs(this).edit().putBoolean("enabled", false).apply();
            Toast.makeText(this, "Не удалось создать будильник: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        updateStatus();
    }

    private void disableAlarm() {
        AlarmSettings.prefs(this).edit().putBoolean("enabled", false).apply();
        AlarmScheduler.cancel(this);
        updateStatus();
        Toast.makeText(this, "Ежедневный будильник выключен", Toast.LENGTH_SHORT).show();
    }

    private void updateStatus() {
        if (AlarmSettings.enabled(this)) {
            statusText.setText("● АКТИВЕН: каждый день в " + AlarmSettings.displayTime(this)
                    + "\nПесня: " + AlarmSettings.songName(this));
            statusText.setTextColor(Color.rgb(138, 229, 179));
        } else {
            statusText.setText("○ Будильник выключен");
            statusText.setTextColor(MUTED);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (statusText != null) updateStatus();
    }
}
