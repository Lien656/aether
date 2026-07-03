# AETHER

Android MVP for importing an MP3, adding cover art, album metadata, and embedded lyrics, then exporting a new tagged MP3 to `Music/AETHER`.

## What works

- Requests audio-library access and scans local audio through Android MediaStore.
- Separates likely music from likely voice recordings using Android metadata plus path/tag heuristics.
- Shows separate Music and Voice Notes tabs.
- Pick an audio file with Android's file picker.
- Pick cover art.
- Fill title, artist, album, and lyrics.
- Build an in-app playlist from selected tracks.
- Save the playlist as an album by exporting tagged MP3 copies with a shared album name and cover.
- Play the selected song.
- Show synced lyrics when the lyrics field uses LRC timestamps:

```text
[00:12.30] First line
[00:18.10] Second line
```

- Export a new MP3 with ID3v2.3 frames:
  - `TIT2` title
  - `TPE1` artist
  - `TALB` album
  - `APIC` cover
  - `USLT` lyrics

## Limits

- Export is MP3-only for now.
- The app writes a new tagged copy instead of mutating the original file.
- Music vs voice-note detection is heuristic. It handles common Samsung recorder cases, but edge cases still need manual cleanup later.
- Apple Music Sing-style vocal reduction is not implemented. That needs real DSP/ML source separation.
- Synced lyrics are used inside this app from LRC text. Exported embedded lyrics are plain `USLT`, because broad player support for embedded synced lyric frames is inconsistent.

## Build locally

```powershell
.\gradlew.bat assembleDebug
```

APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## GitHub Actions

Push this folder to GitHub. The included workflow builds the debug APK and uploads it as an artifact.
