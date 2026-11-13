package ru.genrih.alarm;

import android.Manifest;
import android.app.Activity;
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

    private static final int BG = Color.rgb(13, 27, 57);
    private static final int PANEL = Color.rgb(38, 65, 115);
    private static final int PANEL_LIGHT = Color.rgb(64, 91, 153);
    private static final int PURPLE = Color.rgb(141, 91, 237);
    private static final int LIME = Color.rgb(80, 210, 155);
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
                new int[]{Color.rgb(57, 88, 147), BG}));
        frame.setPadding(dp(14), dp(6), dp(14), dp(6));
        setContentView(frame);

        TextView title = text("Генрих", 21, WHITE, true);
        title.setPadding(0, dp(10), 0, dp(10));
        frame.addView(title, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        page = vertical();
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setPadding(dp(6), dp(8), dp(6), dp(22));
        scroll.addView(page);
        frame.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setPadding(dp(3), dp(6), dp(3), dp(5));
        nav.setBackground(background(Color.rgb(33, 58, 108), Color.rgb(25, 45, 84), 18));
        frame.addView(nav, new LinearLayout.LayoutParams(-1, -2));
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
        String[] names = {"⏰", "♫", "✓", "⚙"};
        String[] hints = {"Будильник", "Музыка", "Доступ", "Статус"};
        for (int i = 0; i < names.length; i++) {
            final int target = i;
            LinearLayout item = vertical();
            item.setGravity(Gravity.CENTER);
            item.setPadding(dp(1), dp(5), dp(1), dp(5));
            if (i == currentTab) item.setBackground(background(PANEL_LIGHT, PANEL, 12));
            TextView symbol = text(names[i], 24, i == currentTab ? WHITE : MUTED, true);
            item.addView(symbol);
            TextView name = text(hints[i], 10, i == currentTab ? WHITE : MUTED, false);
            name.setMaxLines(2);
            item.addView(name);
            nav.addView(item, new LinearLayout.LayoutParams(0, -2, 1f));
            item.setContentDescription(hints[i]);
            item.setOnClickListener(v -> showTab(target));
        }
    }

    private void alarmPage() {
        imageHero();
        timeText = heading(page, timeString(), 48, 2);
        timeText.setOnClickListener(v -> pickTime());
        description(page, "Будильник · нажми на время для изменения", 1);
        LinearLayout c = card(page, 18);
        Switch toggle = new Switch(this);
        toggle.setText("Ежедневный будильник");
        toggle.setTextColor(WHITE);
        toggle.setTextSize(15);
        toggle.setPadding(dp(5), dp(10), dp(5), dp(10));
        toggle.setChecked(AlarmSettings.enabled(this));
        c.addView(toggle, new LinearLayout.LayoutParams(-1, -2));
        toggle.setOnCheckedChangeListener((view, checked) -> {
            if (checked) saveAlarm();
            else disableAlarm();
            if (AlarmSettings.enabled(this) != checked) view.setChecked(AlarmSettings.enabled(this));
        });
        button(c, "Изменить время", false, v -> pickTime());
        statusText = description(page, "", 18);
        updateStatus();
    }

    private void imageHero() {
        ImageView image = new ImageView(this);
        image.setImageResource(R.drawable.genrih_ui_alarm);
        image.setAdjustViewBounds(true);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(164), dp(164));
        lp.topMargin = dp(12);
        page.addView(image, lp);
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

    private void musicPage() {
        heading(page, "Выбор музыки", 23, 4);
        description(page, "Любимые треки вместо системного сигнала", 4);
        LinearLayout c = card(page, 20);
        button(c, "▶   Моя музыка", true, v -> openMusicPicker());
        button(c, "⤨   Случайный трек", false, v -> openMultiMusicPicker());
        button(c, "☷   Плейлист", false, v -> showPlaylist());
        songText = heading(page, selectedName, 16, 22);
        songText.setMaxLines(3);
        description(page, "Резервная мелодия", 5);
        int count = AlarmPlaylistRepository.getInstance(this).loadPlaylist().size();
        description(page, "Случайный плейлист: " + count + " треков", 12);
        button(page, "Сохранить и включить будильник", true, v -> saveAlarm());
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

    private void permissionsPage() {
        TextView shield = heading(page, "🛡️", 76, 20);
        shield.setContentDescription("Защита и разрешения");
        heading(page, "Разрешения", 25, 5);
        LinearLayout c = card(page, 22);
        permissionLine(c, "♫ Доступ к музыке", "Через выбор файлов", true);
        boolean notifications = Build.VERSION.SDK_INT < 33 ||
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        permissionLine(c, "▣ Уведомления", notifications ? "Разрешены" : "Требуется разрешение", notifications);
        boolean full = Build.VERSION.SDK_INT < 34 || fullScreenAllowed();
        permissionLine(c, "▣ Полный экран", full ? "Разрешён" : "Нужно разрешение", full);
        button(page, "Разрешить уведомления", true, v -> requestNotifications());
        if (Build.VERSION.SDK_INT >= 34) {
            button(page, "Полный экран при звонке", false, v -> requestFullScreen());
        }
        description(page, "На Huawei также проверь автозапуск и ограничения батареи для Генриха.", 24);
    }

    private void permissionLine(LinearLayout root, String title, String summary, boolean granted) {
        LinearLayout line = new LinearLayout(this);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(dp(6), dp(8), dp(6), dp(8));
        TextView titleView = text(title + "\n" + summary, 13, WHITE, false);
        titleView.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        line.addView(titleView, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView state = text(granted ? "✓" : "!", 21, granted ? LIME : Color.rgb(255, 211, 103), true);
        line.addView(state, new LinearLayout.LayoutParams(dp(36), -2));
        root.addView(line, new LinearLayout.LayoutParams(-1, -2));
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
        heading(page, "📞", 78, 18);
        heading(page, "Рингтон", 25, 5);
        description(page, "Текущий трек:", 13);
        String current = AlarmSettings.songName(this);
        heading(page, current, 17, 9).setMaxLines(3);
        int count = AlarmPlaylistRepository.getInstance(this).loadPlaylist().size();
        if (count > 0) description(page, "Случайный плейлист: " + count + " треков", 14);
        LinearLayout c = card(page, 20);
        statusText = text("", 16, WHITE, true);
        c.addView(statusText);
        updateStatus();
        button(page, "▶ Проверить звук", true, v -> previewSound());
        button(page, "■ Остановить звук", false, v ->
                stopService(new Intent(this, AlarmService.class)));
        button(page, "Выключить будильник", false, v -> disableAlarm());
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
