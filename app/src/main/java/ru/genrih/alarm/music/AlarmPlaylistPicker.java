package ru.genrih.alarm.music;

import android.content.Context;
import android.net.Uri;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Chooses one playable-looking document; final decoding is checked by MediaPlayer. */
public final class AlarmPlaylistPicker {
    private static final Random RANDOM = new Random();
    private AlarmPlaylistPicker() { }

    public static Uri pickTrack(Context context) {
        AlarmPlaylistRepository repo = AlarmPlaylistRepository.getInstance(context);
        List<Uri> available = new ArrayList<>();
        for (AlarmPlaylistEntry entry : repo.loadPlaylist()) {
            Uri uri = Uri.parse(entry.uri);
            if (!"content".equals(uri.getScheme())) continue;
            try (android.os.ParcelFileDescriptor descriptor =
                         context.getContentResolver().openFileDescriptor(uri, "r")) {
                if (descriptor != null) available.add(uri);
            } catch (Exception ignored) {
                // Missing/revoked document: choose another one or fall back.
            }
        }
        if (available.isEmpty()) return null;
        if (available.size() == 1) return available.get(0);

        List<Uri> candidates = new ArrayList<>(available);
        if (available.size() >= 6) {
            Set<String> recent = repo.getLastPlayedUris(5);
            candidates.removeIf(uri -> recent.contains(uri.toString()));
        } else {
            String last = repo.getLastPlayedUri();
            if (last != null) candidates.removeIf(uri -> last.equals(uri.toString()));
        }
        if (candidates.isEmpty()) candidates = available;
        return candidates.get(RANDOM.nextInt(candidates.size()));
    }
}
