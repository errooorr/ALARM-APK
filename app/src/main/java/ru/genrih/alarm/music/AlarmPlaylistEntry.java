package ru.genrih.alarm.music;

/** Immutable reference to a document selected by the user. */
public final class AlarmPlaylistEntry {
    public final String uri;
    public final String displayName;

    public AlarmPlaylistEntry(String uri, String displayName) {
        this.uri = uri;
        this.displayName = displayName;
    }
}
