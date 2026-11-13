package ru.genrih.alarm.music;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Small, local playlist. No network, Google services or Huawei services required. */
public final class AlarmPlaylistRepository {
    private static final String PLAYLIST_PREFS = "alarm_playlist_v2";
    private static final String HISTORY_PREFS = "alarm_history_v2";
    private static final String PLAYLIST_KEY = "playlist_json";
    private static final String HISTORY_KEY = "history_json";
    private static final int HISTORY_SIZE = 5;

    private final SharedPreferences playlistPrefs;
    private final SharedPreferences historyPrefs;

    private AlarmPlaylistRepository(Context context) {
        Context appContext = context.getApplicationContext();
        playlistPrefs = appContext.getSharedPreferences(PLAYLIST_PREFS, Context.MODE_PRIVATE);
        historyPrefs = appContext.getSharedPreferences(HISTORY_PREFS, Context.MODE_PRIVATE);
    }

    public static AlarmPlaylistRepository getInstance(Context context) {
        return new AlarmPlaylistRepository(context);
    }

    public synchronized List<AlarmPlaylistEntry> loadPlaylist() {
        String json = playlistPrefs.getString(PLAYLIST_KEY, "[]");
        List<AlarmPlaylistEntry> entries = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) continue;
                String uri = object.optString("uri", "");
                String name = object.optString("name", "Неизвестный трек");
                if (!uri.isEmpty() && "content".equals(Uri.parse(uri).getScheme())) {
                    entries.add(new AlarmPlaylistEntry(uri, name));
                }
            }
        } catch (JSONException ignored) {
            // Corrupt entries must never prevent the alarm from using its fallback sound.
        }
        return entries;
    }

    public synchronized int addEntries(List<Uri> uris, List<String> names) {
        if (uris == null || uris.isEmpty()) return 0;
        List<AlarmPlaylistEntry> existing = loadPlaylist();
        LinkedHashSet<String> knownUris = new LinkedHashSet<>();
        JSONArray array = new JSONArray();
        for (AlarmPlaylistEntry entry : existing) {
            if (knownUris.add(entry.uri)) appendEntry(array, entry.uri, entry.displayName);
        }
        int added = 0;
        for (int i = 0; i < uris.size(); i++) {
            Uri uri = uris.get(i);
            if (uri == null || !"content".equals(uri.getScheme())) continue;
            String value = uri.toString();
            if (!knownUris.add(value)) continue;
            String name = names != null && i < names.size() && names.get(i) != null
                    ? names.get(i) : "Неизвестный трек";
            appendEntry(array, value, name);
            added++;
        }
        // Commit ensures the next alarm sees the full playlist after the picker closes.
        if (added > 0) playlistPrefs.edit().putString(PLAYLIST_KEY, array.toString()).commit();
        return added;
    }

    private static void appendEntry(JSONArray array, String uri, String name) {
        JSONObject entry = new JSONObject();
        try {
            entry.put("uri", uri);
            entry.put("name", name);
            array.put(entry);
        } catch (JSONException ignored) {
            // JSON string values do not normally cause this exception.
        }
    }

    public synchronized Set<String> getLastPlayedUris(int limit) {
        if (limit <= 0) return Collections.emptySet();
        List<String> ordered = loadHistory();
        int from = Math.max(0, ordered.size() - limit);
        return new LinkedHashSet<>(ordered.subList(from, ordered.size()));
    }

    public synchronized String getLastPlayedUri() {
        List<String> ordered = loadHistory();
        return ordered.isEmpty() ? null : ordered.get(ordered.size() - 1);
    }

    public synchronized void recordPlayed(Uri uri) {
        if (uri == null) return;
        List<String> ordered = loadHistory();
        ordered.remove(uri.toString());
        ordered.add(uri.toString());
        while (ordered.size() > HISTORY_SIZE) ordered.remove(0);
        historyPrefs.edit().putString(HISTORY_KEY, new JSONArray(ordered).toString()).commit();
    }

    private List<String> loadHistory() {
        List<String> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(historyPrefs.getString(HISTORY_KEY, "[]"));
            for (int i = 0; i < array.length(); i++) {
                String uri = array.optString(i, "");
                if (!uri.isEmpty()) {
                    result.remove(uri);
                    result.add(uri);
                }
            }
        } catch (JSONException ignored) {
            // A damaged history must not stop the alarm.
        }
        return result;
    }
}
