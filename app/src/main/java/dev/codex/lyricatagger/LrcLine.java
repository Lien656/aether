package dev.codex.lyricatagger;

final class LrcLine {
    final long timeMs;
    final String text;

    LrcLine(long timeMs, String text) {
        this.timeMs = timeMs;
        this.text = text;
    }
}
