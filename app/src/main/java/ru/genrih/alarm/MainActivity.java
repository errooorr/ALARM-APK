package ru.genrih.alarm;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.content.res.ColorStateList;
import android.app.NotificationManager;
import android.app.TimePickerDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import ru.genrih.alarm.music.AlarmPlaylistEntry;
import ru.genrih.alarm.music.AlarmPlaylistRepository;

/** Responsive four-tab UI. All scheduling and playback remain in existing classes. */
public class MainActivity extends Activity {
    private static final int FILE_PICK = 7;
    private static final int MULTI_FILE_PICK = 9;
    private static final int REQUEST_NOTIFICATIONS = 8;

    private static final int BG = Color.rgb(19, 59, 99);
    private static final int PANEL = Color.rgb(42, 77, 133);
    private static final int PANEL_LIGHT = Color.rgb(75, 105, 169);
    private static final int PURPLE = Color.rgb(149, 76, 238);
    private static final int LIME = Color.rgb(55, 205, 158);
    private static final int WHITE = Color.WHITE;
    private static final int MUTED = Color.rgb(194, 211, 237);

    private int hour;
    private int minute;
    private int currentTab = 0;
    private String selectedUri;
    private String selectedName;
    private LinearLayout page;
    private LinearLayout nav;
    private TextView timeText;
    private TextView songText;
    private TextView statusText;
    private boolean rendering;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        hour = AlarmSettings.hour(this);
        minute = AlarmSettings.minute(this);
        selectedUri = AlarmSettings.songUri(this);
        selectedName = AlarmSettings.songName(this);
        if (state != null) currentTab = state.getInt("tab", 0);
        createUi();
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
        }
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("tab", currentTab);
    }

    private int dp(float n) {
        return (int) (getResources().getDisplayMetrics().density * n + 0.5f);
    }

    private GradientDrawable background(int start, int end, int radius) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{start, end});
        d.setCornerRadius(dp(radius));
        return d;
    }

    private TextView text(String value, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextColor(color);
        t.setTextSize(sp);
        t.setGravity(Gravity.CENTER);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        t.setIncludeFontPadding(true);
        return t;
    }

    private LinearLayout vertical() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private void add(LinearLayout root, View child, int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(top);
        root.addView(child, lp);
    }

    private TextView heading(LinearLayout root, String title, float size, int top) {
        TextView tv = text(title, size, WHITE, true);
        add(root, tv, top);
        return tv;
    }

    private TextView description(LinearLayout root, String value, int top) {
        TextView tv = text(value, 14, MUTED, false);
        tv.setLineSpacing(dp(3), 1f);
        add(root, tv, top);
        return tv;
    }

    private LinearLayout card(LinearLayout root, int top) {
        LinearLayout c = vertical();
        c.setPadding(dp(15), dp(15), dp(15), dp(15));
        c.setBackground(background(PANEL_LIGHT, PANEL, 18));
        add(root, c, top);
        return c;
    }

    private Button button(LinearLayout root, String value, boolean primary, View.OnClickListener click) {
        Button b = new Button(this);
        b.setText(value);
        b.setAllCaps(false);
        b.setTextColor(WHITE);
        b.setTextSize(15);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(12), dp(7), dp(12), dp(7));
        b.setMinHeight(dp(54));
        b.setBackground(background(primary ? PURPLE : PANEL_LIGHT,
                primary ? Color.rgb(112, 68, 214) : PANEL, 16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(10);
        root.addView(b, lp);
        b.setOnClickListener(click);
        return b;
    }

    private void createUi() {
        LinearLayout frame = vertical();
        frame.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.rgb(53, 82, 143), Color.rgb(23, 74, 116)}));
        frame.setPadding(dp(8), dp(2), dp(8), dp(2));
        setContentView(frame);

        TextView title = text("Генрих", 20, WHITE, true);
        title.setPadding(0, dp(14), 0, dp(10));
        frame.addView(title, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        page = vertical();
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setPadding(dp(15), dp(5), dp(15), dp(20));
        scroll.addView(page);
        frame.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(2), dp(2), dp(2), dp(2));
        nav.setBackground(background(Color.rgb(50, 93, 153), Color.rgb(37, 76, 128), 13));
        frame.addView(nav, new LinearLayout.LayoutParams(-1, dp(58)));
        showTab(currentTab);
    }

    private void showTab(int tab) {
        currentTab = tab;
        rendering = true;
        page.removeAllViews();
        timeText = null;
        songText = null;
        statusText = null;
        switch (tab) {
            case 0: alarmPage(); break;
            case 1: musicPage(); break;
            case 2: permissionsPage(); break;
            default: ringtonePage(); break;
        }
        renderNav();
        rendering = false;
    }

    private void renderNav() {
        nav.removeAllViews();
        String[] symbols = {"⏰", "♫", "☎", "⚙"};
        String[] labels = {"Будильник", "Музыка", "Рингтон", "Разрешения"};
        int[] tabs = {0, 1, 3, 2};
        for (int i = 0; i < symbols.length; i++) {
            final int target = tabs[i];
            TextView item = text(symbols[i], 29,
                    target == currentTab ? WHITE : MUTED, true);
            item.setContentDescription(labels[i]);
            if (target == currentTab) item.setBackground(background(
                    Color.rgb(76, 112, 178), Color.rgb(38, 99, 148), 9));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
            lp.leftMargin = dp(2);
            lp.rightMargin = dp(2);
            nav.addView(item, lp);
            item.setOnClickListener(v -> showTab(target));
        }
    }

    private void iconHero(int drawable, int size, int top) {
        ImageView view = new ImageView(this);
        view.setImageResource(drawable);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(size), dp(size));
        lp.topMargin = dp(top);
        page.addView(view, lp);
    }

    private void alarmPage() {
        iconHero(R.drawable.genrih_ref_clock, 180, 42);
        timeText = heading(page, timeString(), 54, 0);
        timeText.setOnClickListener(v -> pickTime());
        description(page, "Будильник", 2);
        Switch toggle = new Switch(this);
        toggle.setText("");
        toggle.setThumbTintList(ColorStateList.valueOf(WHITE));
        toggle.setTrackTintList(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{LIME, Color.rgb(71, 87, 119)}));
        toggle.setChecked(AlarmSettings.enabled(this));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(80), dp(53));
        lp.topMargin = dp(12);
        page.addView(toggle, lp);
        toggle.setOnCheckedChangeListener((view, checked) -> {
            if (checked) saveAlarm(); else disableAlarm();
            if (AlarmSettings.enabled(this) != checked)
                view.setChecked(AlarmSettings.enabled(this));
        });
    }

    private String timeString() {
        return String.format(Locale.getDefault(), "%02d:%02d", hour, minute);
    }

    private void pickTime() {
        new TimePickerDialog(this, (picker, h, m) -> {
            hour = h;
            minute = m;
            if (timeText != null) timeText.setText(timeString());
            Toast.makeText(this, "Нажми «Включить», чтобы сохранить новое время", Toast.LENGTH_LONG).show();
        }, hour, minute, true).show();
    }

    private void musicRow(String symbol, String name, boolean purple, View.OnClickListener click) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(4), dp(9), dp(4));
        row.setBackground(background(purple ? Color.rgb(93, 99, 171) : PANEL_LIGHT,
                purple ? Color.rgb(55, 88, 151) : PANEL, 14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(64));
        lp.topMargin = dp(12);
        page.addView(row, lp);
        TextView icon = text(symbol, 24, WHITE, true);
        icon.setBackground(background(purple ? Color.rgb(168, 98, 247) : Color.rgb(80, 119, 174),
                purple ? Color.rgb(109, 62, 207) : Color.rgb(44, 90, 147), 11));
        row.addView(icon, new LinearLayout.LayoutParams(dp(54), dp(54)));
        TextView title = text(name, 16, WHITE, true);
        title.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        title.setPadding(dp(12), 0, 0, 0);
        row.addView(title, new LinearLayout.LayoutParams(0, -1, 1f));
        TextView arrow = text("›", 27, MUTED, false);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(21), -1));
        row.setOnClickListener(click);
    }

    private void musicPage() {
        heading(page, "Выбор музыки", 23, 30);
        musicRow("▶", "Моя музыка", true, v -> openMusicPicker());
        musicRow("⤨", "Случайный трек", true, v -> openMultiMusicPicker());
        musicRow("☷", "Плейлист", false, v -> showPlaylist());
        if (selectedUri != null && !selectedUri.isEmpty() &&
                !selectedUri.equals(AlarmSettings.songUri(this)))
            button(page, "Сохранить мелодию", true, v -> saveAlarm());
    }

    private void showPlaylist() {
        page.removeAllViews();
        heading(page, "Плейлист", 23, 6);
        List<AlarmPlaylistEntry> songs = AlarmPlaylistRepository.getInstance(this).loadPlaylist();
        description(page, songs.size() + " треков · случайный выбор при звонке", 10);
        if (songs.isEmpty()) description(page, "Плейлист пока пуст", 30);
        for (int i = 0; i < songs.size(); i++) {
            AlarmPlaylistEntry entry = songs.get(i);
            LinearLayout c = card(page, 10);
            TextView name = text((i + 1) + ". " + entry.displayName, 15, WHITE, false);
            name.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            name.setMaxLines(3);
            c.addView(name);
        }
        button(page, "+ Добавить музыку", true, v -> openMultiMusicPicker());
        button(page, "← Назад к выбору", false, v -> showTab(1));
    }

    private boolean exactAlarmsAllowed() {
        if (Build.VERSION.SDK_INT < 31) return true;
        AlarmManager mgr = getSystemService(AlarmManager.class);
        return mgr != null && mgr.canScheduleExactAlarms();
    }

    private void permissionsPage() {
        iconHero(R.drawable.genrih_ref_shield, 138, 31);
        heading(page, "Разрешения", 25, 3);
        LinearLayout panel = vertical();
        panel.setPadding(dp(12), dp(12), dp(12), dp(18));
        panel.setBackground(background(Color.rgb(239, 249, 253), Color.rgb(209, 236, 249), 18));
        add(page, panel, 12);
        permissionLine(panel, "♫", "Доступ к музыке", "Через выбор файлов", true);
        boolean notifications = Build.VERSION.SDK_INT < 33 ||
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        permissionLine(panel, "▣", "Уведомления", notifications ? "Разрешены" : "Не разрешены", notifications);
        boolean full = Build.VERSION.SDK_INT < 34 || fullScreenAllowed();
        permissionLine(panel, "▣", "Полный экран", full ? "Разрешён" : "Не разрешён", full);
        permissionLine(panel, "◷", "Точный будильник",
                exactAlarmsAllowed() ? "Разрешён" : "Нужно разрешение", exactAlarmsAllowed());
        button(panel, "Разрешить", true, v -> {
            if (!notifications) requestNotifications();
            else if (!full) requestFullScreen();
            else if (!exactAlarmsAllowed()) {
                try { startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:" + getPackageName()))); }
                catch (Exception e) { Toast.makeText(this, "Открой настройки вручную", Toast.LENGTH_LONG).show(); }
            } else Toast.makeText(this, "Все проверяемые разрешения получены", Toast.LENGTH_SHORT).show();
        });
    }

    private void permissionLine(LinearLayout root, String symbol, String title, String subtitle, boolean granted) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(2), dp(8), dp(2), dp(8));
        TextView ico = text(symbol, 20, Color.rgb(33, 80, 116), true);
        row.addView(ico, new LinearLayout.LayoutParams(dp(33), dp(41)));
        LinearLayout detail = vertical();
        TextView titleView = text(title, 14, Color.rgb(19, 53, 81), true);
        titleView.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        detail.addView(titleView);
        TextView subtitleView = text(subtitle, 11, Color.rgb(57, 89, 120), false);
        subtitleView.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        detail.addView(subtitleView);
        row.addView(detail, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView check = text(granted ? "✓" : "!", 18, WHITE, true);
        check.setBackground(background(granted ? LIME : Color.rgb(218, 161, 75),
                granted ? Color.rgb(28, 145, 114) : Color.rgb(192, 126, 47), 30));
        row.addView(check, new LinearLayout.LayoutParams(dp(27), dp(27)));
        root.addView(row);
    }

    private boolean fullScreenAllowed() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        return nm != null && nm.canUseFullScreenIntent();
    }

    private void requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
        } else {
            Toast.makeText(this, "Уведомления уже разрешены", Toast.LENGTH_SHORT).show();
        }
    }

    private void requestFullScreen() {
        if (Build.VERSION.SDK_INT < 34 || fullScreenAllowed()) {
            Toast.makeText(this, "Полноэкранные уведомления уже разрешены", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            Toast.makeText(this, "Открой настройки уведомлений вручную", Toast.LENGTH_LONG).show();
        }
    }

    private void ringtonePage() {
        iconHero(R.drawable.genrih_ref_phone, 175, 37);
        heading(page, "Рингтон", 28, 1);
        description(page, "Текущий трек:", 16);
        String selected = AlarmSettings.songName(this);
        if (selected == null || selected.isEmpty()) selected = "Системный звук";
        TextView name = text(selected, 17, LIME, false);
        name.setMaxLines(2);
        add(page, name, 10);
        TextView state = text(AlarmSettings.enabled(this) ? "✓  Установлен" : "○  Не установлен",
                16, WHITE, true);
        state.setPadding(dp(16), dp(11), dp(16), dp(11));
        state.setBackground(background(Color.rgb(85, 215, 159), Color.rgb(28, 162, 133), 12));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
        p.topMargin = dp(30);
        page.addView(state, p);
        state.setOnClickListener(v -> previewSound());
        // Additional controls remain accessible lower down without crowding the reference layout.
        button(page, "▶ Проверить звук", false, v -> previewSound());
        button(page, "■ Остановить звук", false,
                v -> stopService(new Intent(this, AlarmService.class)));
    }

    private void openMusicPicker() {
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        pick.setType("*/*");
        pick.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"audio/*", "video/mp4"});
        pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(pick, FILE_PICK);
    }

    private void openMultiMusicPicker() {
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        pick.setType("*/*");
        pick.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"audio/*", "video/mp4"});
        pick.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(pick, MULTI_FILE_PICK);
    }

    private void handleMultiMusicResult(Intent data) {
        List<Uri> uris = new ArrayList<>();
        List<String> names = new ArrayList<>();
        int unavailable = 0;
        ClipData clips = data.getClipData();
        int count = clips != null ? clips.getItemCount() : (data.getData() != null ? 1 : 0);
        for (int i = 0; i < count; i++) {
            Uri uri = clips != null ? clips.getItemAt(i).getUri() : data.getData();
            if (uri == null) continue;
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                uris.add(uri);
                names.add(getFileName(uri));
            } catch (SecurityException e) {
                unavailable++;
            }
        }
        int added = AlarmPlaylistRepository.getInstance(this).addEntries(uris, names);
        showTab(1);
        Toast.makeText(this, "Добавлено: " + added + ", без постоянного доступа: " + unavailable,
                Toast.LENGTH_LONG).show();
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null) return;
        if (request == MULTI_FILE_PICK) {
            handleMultiMusicResult(data);
            return;
        }
        if (request != FILE_PICK || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException e) {
            Toast.makeText(this, "Файл может стать недоступен после перезагрузки", Toast.LENGTH_LONG).show();
        }
        selectedUri = uri.toString();
        selectedName = getFileName(uri);
        showTab(1);
        Toast.makeText(this, "Нажми «Сохранить и включить»", Toast.LENGTH_SHORT).show();
    }

    private String getFileName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                if (name != null && !name.isEmpty()) return name;
            }
        } catch (Exception ignored) { }
        return "Выбранный медиафайл";
    }

    private void previewSound() {
        if (!selectedUri.equals(AlarmSettings.songUri(this))) {
            Toast.makeText(this, "Сначала сохрани выбранную песню", Toast.LENGTH_LONG).show();
            return;
        }
        Intent service = new Intent(this, AlarmService.class)
                .setAction(AlarmService.START)
                .putExtra(AlarmService.EXTRA_PREVIEW, true);
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service);
            else startService(service);
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
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
            Toast.makeText(this, "Будильник включён: " + AlarmSettings.displayDateTime(next),
                    Toast.LENGTH_LONG).show();
        } catch (SecurityException e) {
            AlarmSettings.prefs(this).edit().putBoolean("enabled", false).apply();
            Toast.makeText(this, "Разреши точные будильники в настройках", Toast.LENGTH_LONG).show();
            if (Build.VERSION.SDK_INT >= 31) {
                try {
                    startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:" + getPackageName())));
                } catch (Exception ignored) { }
            }
        } catch (Exception e) {
            AlarmSettings.prefs(this).edit().putBoolean("enabled", false).apply();
            Toast.makeText(this, "Не удалось создать будильник: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        if (!rendering) showTab(currentTab);
    }

    private void disableAlarm() {
        AlarmSettings.prefs(this).edit().putBoolean("enabled", false).apply();
        AlarmScheduler.cancel(this);
        if (!rendering) showTab(currentTab);
        Toast.makeText(this, "Ежедневный будильник выключен", Toast.LENGTH_SHORT).show();
    }

    private void updateStatus() {
        if (statusText == null) return;
        if (AlarmSettings.enabled(this)) {
            statusText.setText("✓ АКТИВЕН: каждый день в " + AlarmSettings.displayTime(this));
            statusText.setTextColor(LIME);
        } else {
            statusText.setText("○ Будильник выключен");
            statusText.setTextColor(MUTED);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (page != null) showTab(currentTab);
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == REQUEST_NOTIFICATIONS && page != null && currentTab == 2) showTab(2);
    }
}
