package ru.genrih.alarm;

import android.content.Context;
import android.content.SharedPreferences;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class AlarmSettings {
    private static final String PREFS = "alarm";
    private AlarmSettings() { }

    public static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean enabled(Context c) { return prefs(c).getBoolean("enabled", false); }
    public static int hour(Context c) { return prefs(c).getInt("hour", 7); }
    public static int minute(Context c) { return prefs(c).getInt("minute", 0); }
    public static String songUri(Context c) { return prefs(c).getString("song_uri", ""); }
    public static String songName(Context c) { return prefs(c).getString("song_name", "Не выбрана (системный звук)"); }

    public static String displayTime(Context c) {
        return String.format(Locale.getDefault(), "%02d:%02d", hour(c), minute(c));
    }
    public static String displayDateTime(long millis) {
        return new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(new Date(millis));
    }
}
