package dev.codex.lyricatagger;

import android.net.Uri;

final class MediaTrack {
    final Uri uri;
    final String title;
    final String artist;
    final String album;
    final long durationMs;
    final boolean voice;

    MediaTrack(Uri uri, String title, String artist, String album, long durationMs, boolean voice) {
        this.uri = uri;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.durationMs = durationMs;
        this.voice = voice;
    }
}
