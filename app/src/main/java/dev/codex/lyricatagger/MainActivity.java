package dev.codex.lyricatagger;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int PICK_AUDIO = 1001;
    private static final int PICK_COVER = 1002;
    private static final int READ_AUDIO_PERMISSION = 2001;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            updateLyrics();
            handler.postDelayed(this, 180);
        }
    };

    private Uri audioUri;
    private Uri coverUri;
    private byte[] coverBytes;
    private String coverMime = "image/jpeg";
    private MediaPlayer player;
    private List<LrcLine> lrcLines = new ArrayList<>();
    private final List<MediaTrack> musicTracks = new ArrayList<>();
    private final List<MediaTrack> voiceTracks = new ArrayList<>();
    private final List<MediaTrack> playlist = new ArrayList<>();
    private ValueAnimator coverPulse;
    private boolean showingVoice;

    private TextView selectedSong;
    private TextView selectedCover;
    private TextView lyricNow;
    private TextView lyricNext;
    private TextView miniTitle;
    private TextView miniArtist;
    private TextView musicTab;
    private TextView voiceTab;
    private TextView libraryStatus;
    private LinearLayout trackList;
    private LinearLayout playlistList;
    private EditText playlistNameInput;
    private ImageView coverPreview;
    private ImageView miniCover;
    private TextView playButton;
    private EditText titleInput;
    private EditText artistInput;
    private EditText albumInput;
    private EditText lyricsInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.rgb(3, 2, 13));
        setContentView(buildUi());
        handler.post(ticker);
        requestLibraryAccess();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(ticker);
        if (coverPulse != null) {
            coverPulse.cancel();
        }
        if (player != null) {
            player.release();
            player = null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
        }

        if (requestCode == PICK_AUDIO) {
            audioUri = uri;
            selectedSong.setText("Track loaded");
            resetPlayer();
            miniTitle.setText(value(titleInput, "Untitled track"));
            miniArtist.setText(value(artistInput, "AETHER session"));
            animatePop(selectedSong);
        } else if (requestCode == PICK_COVER) {
            coverUri = uri;
            coverMime = getContentResolver().getType(uri);
            coverPreview.setImageURI(uri);
            miniCover.setImageURI(uri);
            selectedCover.setText("Cover loaded");
            try {
                coverBytes = Id3Writer.readAll(this, coverUri);
            } catch (Exception error) {
                coverBytes = null;
                toast("Cannot read cover: " + error.getMessage());
            }
            animatePop(coverPreview);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == READ_AUDIO_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadLibrary();
            } else if (libraryStatus != null) {
                libraryStatus.setText("No library permission. Manual import still works.");
            }
        }
    }

    private View buildUi() {
        FrameLayout shell = new FrameLayout(this);
        shell.addView(new AetherBackgroundView(this), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, 0, 0, dp(112));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(30), dp(18), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        shell.addView(scroll);

        LinearLayout top = row();
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo = text("AETHER", 31, Color.rgb(214, 153, 255), true);
        logo.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        logo.setLetterSpacing(0.08f);
        logo.setShadowLayer(dp(10), 0, 0, Color.rgb(173, 60, 255));
        TextView mute = glassChip("clean mode");
        top.addView(logo, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        top.addView(mute);
        root.addView(top);

        root.addView(space(16));
        TextView heading = text("Build the track.", 31, Color.WHITE, true);
        heading.setLetterSpacing(0f);
        root.addView(heading);
        root.addView(text("Cover, album, embedded lyrics, synced playback.", 14, Color.argb(190, 235, 229, 245), false));
        root.addView(space(14));

        TextView search = glassChip("Search tracks, artists...");
        search.setGravity(Gravity.CENTER_VERTICAL);
        search.setTextColor(Color.argb(180, 255, 255, 255));
        search.setPadding(dp(16), 0, dp(16), 0);
        root.addView(search, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(54)
        ));
        root.addView(space(18));

        root.addView(libraryPanel());
        root.addView(space(18));

        root.addView(featureStrip());
        root.addView(space(18));

        LinearLayout coverPanel = glassPanel(30);
        coverPanel.setGravity(Gravity.CENTER_HORIZONTAL);
        coverPreview = new ImageView(this);
        coverPreview.setBackground(gradientBg(30, Color.rgb(28, 18, 54), Color.rgb(110, 52, 190)));
        coverPreview.setClipToOutline(true);
        coverPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        coverPanel.addView(coverPreview, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(256)
        ));
        coverPanel.addView(space(14));

        LinearLayout pickRow = row();
        TextView pickSong = pill("Choose song", true);
        pickSong.setOnClickListener(v -> pick("audio/*", PICK_AUDIO));
        TextView pickCover = pill("Choose cover", false);
        pickCover.setOnClickListener(v -> pick("image/*", PICK_COVER));
        pickRow.addView(pickSong, weightParams());
        pickRow.addView(spaceW(10));
        pickRow.addView(pickCover, weightParams());
        coverPanel.addView(pickRow);

        selectedSong = subText("No song");
        selectedCover = subText("No cover");
        coverPanel.addView(space(8));
        coverPanel.addView(selectedSong);
        coverPanel.addView(selectedCover);
        root.addView(coverPanel);

        root.addView(space(14));
        LinearLayout editor = glassPanel(30);
        editor.addView(sectionTitle("Metadata"));
        titleInput = input("Title");
        artistInput = input("Artist");
        albumInput = input("Album");
        playlistNameInput = input("Playlist / album name");
        lyricsInput = input("LRC lyrics: [00:12.30] line");
        lyricsInput.setMinLines(8);
        lyricsInput.setGravity(Gravity.TOP | Gravity.START);
        lyricsInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        lyricsInput.setOnFocusChangeListener((view, focused) -> {
            if (!focused) {
                refreshLrc();
            }
        });
        editor.addView(titleInput);
        editor.addView(space(9));
        editor.addView(artistInput);
        editor.addView(space(9));
        editor.addView(albumInput);
        editor.addView(space(9));
        editor.addView(playlistNameInput);
        editor.addView(space(9));
        editor.addView(lyricsInput);
        root.addView(editor);

        root.addView(space(14));
        LinearLayout lyricPanel = glassPanel(30);
        lyricPanel.setGravity(Gravity.CENTER_HORIZONTAL);
        lyricPanel.addView(sectionTitle("Live lyrics"));
        lyricNow = text("Current line will appear here", 24, Color.WHITE, true);
        lyricNow.setGravity(Gravity.CENTER);
        lyricNow.setShadowLayer(dp(8), 0, 0, Color.rgb(145, 63, 255));
        lyricNext = text("Use LRC timestamps for synced lines", 15, Color.argb(168, 240, 232, 255), false);
        lyricNext.setGravity(Gravity.CENTER);
        lyricPanel.addView(lyricNow);
        lyricPanel.addView(space(8));
        lyricPanel.addView(lyricNext);
        lyricPanel.addView(space(14));
        LinearLayout actionRow = row();
        playButton = pill("Play", false);
        playButton.setOnClickListener(v -> togglePlay());
        TextView syncButton = pill("Sync text", false);
        syncButton.setOnClickListener(v -> refreshLrc());
        actionRow.addView(playButton, weightParams());
        actionRow.addView(spaceW(10));
        actionRow.addView(syncButton, weightParams());
        lyricPanel.addView(actionRow);
        root.addView(lyricPanel);

        root.addView(space(14));
        TextView save = pill("Export tagged MP3", true);
        save.setOnClickListener(v -> saveTaggedMp3());
        root.addView(save, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(58)
        ));
        root.addView(space(10));
        TextView saveAlbum = pill("Save playlist as album", true);
        saveAlbum.setOnClickListener(v -> savePlaylistAsAlbum());
        root.addView(saveAlbum, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(58)
        ));

        TextView note = subText("Vocal reduction is a separate ML/DSP feature. This build keeps the player honest.");
        note.setGravity(Gravity.CENTER);
        root.addView(space(12));
        root.addView(note);

        shell.addView(miniPlayer(), bottomParams());
        animateEntrance(root);
        return shell;
    }

    private LinearLayout libraryPanel() {
        LinearLayout panel = glassPanel(30);
        panel.addView(sectionTitle("Library"));

        LinearLayout tabs = row();
        musicTab = pill("Music", true);
        voiceTab = pill("Voice notes", false);
        musicTab.setOnClickListener(v -> {
            showingVoice = false;
            renderLibrary();
        });
        voiceTab.setOnClickListener(v -> {
            showingVoice = true;
            renderLibrary();
        });
        tabs.addView(musicTab, weightParams());
        tabs.addView(spaceW(10));
        tabs.addView(voiceTab, weightParams());
        panel.addView(tabs);
        panel.addView(space(10));

        libraryStatus = subText("Loading media library...");
        panel.addView(libraryStatus);
        panel.addView(space(10));

        trackList = new LinearLayout(this);
        trackList.setOrientation(LinearLayout.VERTICAL);
        panel.addView(trackList);

        panel.addView(space(14));
        TextView add = pill("Add selected to playlist", false);
        add.setOnClickListener(v -> addSelectedToPlaylist());
        panel.addView(add, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(54)
        ));

        panel.addView(space(12));
        panel.addView(sectionTitle("Current playlist"));
        playlistList = new LinearLayout(this);
        playlistList.setOrientation(LinearLayout.VERTICAL);
        panel.addView(playlistList);
        renderPlaylist();
        return panel;
    }

    private HorizontalScrollView featureStrip() {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = row();
        row.setPadding(0, 0, dp(18), 0);
        row.addView(featureCard("Cover", "embedded art", Color.rgb(125, 49, 255), Color.rgb(31, 17, 58)));
        row.addView(spaceW(12));
        row.addView(featureCard("Lyrics", "LRC sync", Color.rgb(70, 120, 255), Color.rgb(20, 25, 54)));
        row.addView(spaceW(12));
        row.addView(featureCard("Album", "clean tags", Color.rgb(167, 52, 255), Color.rgb(24, 18, 46)));
        row.addView(spaceW(12));
        row.addView(featureCard("Export", "new MP3", Color.rgb(58, 218, 255), Color.rgb(16, 35, 48)));
        scroll.addView(row);
        return scroll;
    }

    private LinearLayout featureCard(String title, String subtitle, int a, int b) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(12));
        card.setBackground(gradientBg(28, a, b));
        card.setClipToOutline(true);
        TextView fakeArt = text("A", 42, Color.argb(230, 255, 255, 255), true);
        fakeArt.setGravity(Gravity.CENTER);
        fakeArt.setShadowLayer(dp(12), 0, 0, a);
        card.addView(fakeArt, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));
        card.addView(text(title, 17, Color.WHITE, true));
        card.addView(text(subtitle, 13, Color.argb(178, 255, 255, 255), false));
        card.setElevation(dp(8));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(142), dp(188));
        card.setLayoutParams(params);
        return card;
    }

    private LinearLayout miniPlayer() {
        LinearLayout mini = glassPanel(36);
        mini.setGravity(Gravity.CENTER_VERTICAL);
        mini.setOrientation(LinearLayout.HORIZONTAL);
        mini.setPadding(dp(14), dp(10), dp(14), dp(10));
        mini.setElevation(dp(18));

        miniCover = new ImageView(this);
        miniCover.setBackground(gradientBg(16, Color.rgb(126, 50, 255), Color.rgb(25, 11, 45)));
        miniCover.setClipToOutline(true);
        miniCover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        mini.addView(miniCover, new LinearLayout.LayoutParams(dp(48), dp(48)));
        mini.addView(spaceW(12));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        miniTitle = text("Untitled track", 16, Color.WHITE, true);
        miniArtist = text("AETHER session", 12, Color.argb(170, 255, 255, 255), false);
        info.addView(miniTitle);
        info.addView(miniArtist);
        mini.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView prev = text("I<", 22, Color.WHITE, true);
        TextView next = text(">|", 22, Color.WHITE, true);
        prev.setGravity(Gravity.CENTER);
        next.setGravity(Gravity.CENTER);
        mini.addView(prev, new LinearLayout.LayoutParams(dp(42), dp(42)));
        mini.addView(next, new LinearLayout.LayoutParams(dp(42), dp(42)));
        return mini;
    }

    private FrameLayout.LayoutParams bottomParams() {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(86),
                Gravity.BOTTOM
        );
        params.setMargins(dp(14), 0, dp(14), dp(14));
        return params;
    }

    private void requestLibraryAccess() {
        String permission = Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.READ_MEDIA_AUDIO
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
            loadLibrary();
        } else {
            requestPermissions(new String[]{permission}, READ_AUDIO_PERMISSION);
        }
    }

    private void loadLibrary() {
        musicTracks.clear();
        voiceTracks.clear();

        String[] projection = new String[]{
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.IS_MUSIC,
                MediaStore.Audio.Media.RELATIVE_PATH
        };

        try (Cursor cursor = getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                MediaStore.Audio.Media.DATE_ADDED + " DESC"
        )) {
            if (cursor == null) {
                libraryStatus.setText("Media library is empty or unavailable.");
                return;
            }

            int idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
            int titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
            int artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
            int albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
            int durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
            int musicCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_MUSIC);
            int pathCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH);

            while (cursor.moveToNext()) {
                long id = cursor.getLong(idCol);
                long duration = cursor.getLong(durationCol);
                if (duration < 2_000) {
                    continue;
                }
                String title = safe(cursor.getString(titleCol), "Untitled");
                String artist = safe(cursor.getString(artistCol), "Unknown Artist");
                String album = safe(cursor.getString(albumCol), "Unknown Album");
                String path = safe(cursor.getString(pathCol), "");
                boolean isMusic = cursor.getInt(musicCol) != 0;
                boolean voice = looksLikeVoiceNote(isMusic, title, artist, album, path, duration);
                Uri uri = Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, String.valueOf(id));
                MediaTrack track = new MediaTrack(uri, title, artist, album, duration, voice);
                if (voice) {
                    voiceTracks.add(track);
                } else {
                    musicTracks.add(track);
                }
            }
        } catch (Exception error) {
            libraryStatus.setText("Library scan failed: " + error.getMessage());
        }

        renderLibrary();
    }

    private boolean looksLikeVoiceNote(boolean isMusic, String title, String artist, String album, String path, long durationMs) {
        String haystack = (title + " " + artist + " " + album + " " + path).toLowerCase(Locale.US);
        boolean recorderPath = haystack.contains("record")
                || haystack.contains("voice")
                || haystack.contains("диктофон")
                || haystack.contains("запис")
                || haystack.contains("call");
        boolean unknownTags = artist.toLowerCase(Locale.US).contains("unknown")
                && album.toLowerCase(Locale.US).contains("unknown");
        boolean shortUnknown = durationMs < 15 * 60_000L && unknownTags;
        return recorderPath || !isMusic || shortUnknown;
    }

    private void renderLibrary() {
        if (trackList == null) {
            return;
        }
        trackList.removeAllViews();
        List<MediaTrack> source = showingVoice ? voiceTracks : musicTracks;
        musicTab.setBackground(showingVoice
                ? new GlassDrawable(28, getResources().getDisplayMetrics().density)
                : gradientBg(28, Color.rgb(184, 62, 255), Color.rgb(86, 76, 255)));
        voiceTab.setBackground(showingVoice
                ? gradientBg(28, Color.rgb(184, 62, 255), Color.rgb(86, 76, 255))
                : new GlassDrawable(28, getResources().getDisplayMetrics().density));

        libraryStatus.setText(musicTracks.size() + " music / " + voiceTracks.size() + " voice");
        int limit = Math.min(source.size(), 12);
        for (int i = 0; i < limit; i++) {
            MediaTrack track = source.get(i);
            trackList.addView(trackRow(track));
            if (i < limit - 1) {
                trackList.addView(space(8));
            }
        }
        if (source.isEmpty()) {
            trackList.addView(subText(showingVoice ? "No voice notes found." : "No music found."));
        }
    }

    private View trackRow(MediaTrack track) {
        LinearLayout row = glassPanel(22);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(8), dp(12), dp(8));

        TextView badge = text(track.voice ? "V" : "M", 18, Color.WHITE, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(gradientBg(18,
                track.voice ? Color.rgb(52, 155, 255) : Color.rgb(151, 58, 255),
                Color.rgb(26, 17, 52)));
        row.addView(badge, new LinearLayout.LayoutParams(dp(42), dp(42)));
        row.addView(spaceW(10));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text(track.title, 15, Color.WHITE, true));
        copy.addView(text(track.artist + " • " + formatDuration(track.durationMs), 12, Color.argb(165, 255, 255, 255), false));
        row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.setOnClickListener(v -> selectTrack(track));
        return row;
    }

    private void selectTrack(MediaTrack track) {
        audioUri = track.uri;
        titleInput.setText(track.title);
        artistInput.setText(track.artist);
        albumInput.setText(track.album);
        selectedSong.setText((track.voice ? "Voice selected: " : "Track selected: ") + track.title);
        miniTitle.setText(track.title);
        miniArtist.setText(track.artist);
        resetPlayer();
        animatePop(selectedSong);
    }

    private void addSelectedToPlaylist() {
        if (audioUri == null) {
            toast("Select a track first.");
            return;
        }
        MediaTrack selected = findTrackByUri(audioUri);
        if (selected == null) {
            selected = new MediaTrack(
                    audioUri,
                    value(titleInput, "Untitled"),
                    value(artistInput, "Unknown Artist"),
                    value(albumInput, "Unknown Album"),
                    0,
                    false
            );
        }
        playlist.add(selected);
        if (playlistNameInput.getText().toString().trim().isEmpty()) {
            playlistNameInput.setText(value(albumInput, "AETHER Album"));
        }
        renderPlaylist();
    }

    private MediaTrack findTrackByUri(Uri uri) {
        for (MediaTrack track : musicTracks) {
            if (track.uri.equals(uri)) {
                return track;
            }
        }
        for (MediaTrack track : voiceTracks) {
            if (track.uri.equals(uri)) {
                return track;
            }
        }
        return null;
    }

    private void renderPlaylist() {
        if (playlistList == null) {
            return;
        }
        playlistList.removeAllViews();
        if (playlist.isEmpty()) {
            playlistList.addView(subText("No tracks yet."));
            return;
        }
        for (int i = 0; i < playlist.size(); i++) {
            MediaTrack track = playlist.get(i);
            TextView item = subText((i + 1) + ". " + track.title + " — " + track.artist);
            item.setPadding(0, dp(4), 0, dp(4));
            playlistList.addView(item);
        }
    }

    private void savePlaylistAsAlbum() {
        if (playlist.isEmpty()) {
            toast("Add tracks to playlist first.");
            return;
        }
        String albumName = value(playlistNameInput, value(albumInput, "AETHER Album"));
        int saved = 0;
        for (MediaTrack track : playlist) {
            String fileName = sanitize(track.artist + " - " + track.title) + ".mp3";
            ContentValues values = new ContentValues();
            values.put(MediaStore.Audio.Media.DISPLAY_NAME, fileName);
            values.put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg");
            values.put(MediaStore.Audio.Media.TITLE, track.title);
            values.put(MediaStore.Audio.Media.ARTIST, track.artist);
            values.put(MediaStore.Audio.Media.ALBUM, albumName);
            values.put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/AETHER/" + sanitize(albumName));
            values.put(MediaStore.Audio.Media.IS_PENDING, 1);

            Uri outputUri = getContentResolver().insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);
            if (outputUri == null) {
                continue;
            }
            try (OutputStream output = getContentResolver().openOutputStream(outputUri)) {
                if (output == null) {
                    throw new IllegalStateException("Cannot open output stream.");
                }
                Id3Writer.writeTaggedMp3(
                        this,
                        track.uri,
                        coverBytes,
                        coverMime,
                        output,
                        track.title,
                        track.artist,
                        albumName,
                        lyricsInput.getText().toString()
                );
                values.clear();
                values.put(MediaStore.Audio.Media.IS_PENDING, 0);
                getContentResolver().update(outputUri, values, null, null);
                saved++;
            } catch (Exception error) {
                getContentResolver().delete(outputUri, null, null);
            }
        }
        toast("Saved " + saved + " tracks as album: " + albumName);
    }

    private void pick(String mime, int requestCode) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mime);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, requestCode);
    }

    private void togglePlay() {
        if (audioUri == null) {
            toast("Choose a song first.");
            return;
        }
        try {
            if (player == null) {
                player = MediaPlayer.create(this, audioUri);
                if (player == null) {
                    toast("Cannot play this file.");
                    return;
                }
                player.setOnCompletionListener(mp -> {
                    playButton.setText("Play");
                    stopCoverPulse();
                });
            }
            if (player.isPlaying()) {
                player.pause();
                playButton.setText("Play");
                stopCoverPulse();
            } else {
                refreshLrc();
                player.start();
                playButton.setText("Pause");
                startCoverPulse();
            }
        } catch (Exception error) {
            toast("Playback failed: " + error.getMessage());
        }
    }

    private void saveTaggedMp3() {
        if (audioUri == null) {
            toast("Choose a song first.");
            return;
        }

        String title = value(titleInput, "Untitled");
        String artist = value(artistInput, "Unknown Artist");
        String album = value(albumInput, "Unknown Album");
        String fileName = sanitize(artist + " - " + title) + ".mp3";
        miniTitle.setText(title);
        miniArtist.setText(artist);

        ContentValues values = new ContentValues();
        values.put(MediaStore.Audio.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg");
        values.put(MediaStore.Audio.Media.TITLE, title);
        values.put(MediaStore.Audio.Media.ARTIST, artist);
        values.put(MediaStore.Audio.Media.ALBUM, album);
        values.put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/AETHER");
        values.put(MediaStore.Audio.Media.IS_PENDING, 1);

        Uri outputUri = getContentResolver().insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);
        if (outputUri == null) {
            toast("Cannot create output file.");
            return;
        }

        try (OutputStream output = getContentResolver().openOutputStream(outputUri)) {
            if (output == null) {
                throw new IllegalStateException("Cannot open output stream.");
            }
            Id3Writer.writeTaggedMp3(
                    this,
                    audioUri,
                    coverBytes,
                    coverMime,
                    output,
                    title,
                    artist,
                    album,
                    lyricsInput.getText().toString()
            );
            values.clear();
            values.put(MediaStore.Audio.Media.IS_PENDING, 0);
            getContentResolver().update(outputUri, values, null, null);
            toast("Saved to Music/AETHER/" + fileName);
        } catch (Exception error) {
            getContentResolver().delete(outputUri, null, null);
            toast("Export failed: " + error.getMessage());
        }
    }

    private void updateLyrics() {
        if (player == null || !player.isPlaying() || lrcLines.isEmpty()) {
            return;
        }
        int position = player.getCurrentPosition();
        int current = -1;
        for (int i = 0; i < lrcLines.size(); i++) {
            if (lrcLines.get(i).timeMs <= position) {
                current = i;
            } else {
                break;
            }
        }
        if (current >= 0) {
            lyricNow.setText(lrcLines.get(current).text);
            lyricNext.setText(current + 1 < lrcLines.size() ? lrcLines.get(current + 1).text : "");
        }
    }

    private void refreshLrc() {
        lrcLines = LrcParser.parse(lyricsInput.getText().toString());
        if (lrcLines.isEmpty()) {
            lyricNow.setText("No synced lines yet");
            lyricNext.setText("Example: [00:12.30] lyric text");
        } else {
            lyricNow.setText(lrcLines.get(0).text);
            lyricNext.setText(lrcLines.size() > 1 ? lrcLines.get(1).text : "");
        }
        animatePop(lyricNow);
    }

    private void resetPlayer() {
        if (player != null) {
            player.release();
            player = null;
        }
        playButton.setText("Play");
        stopCoverPulse();
    }

    private void startCoverPulse() {
        if (coverPulse != null && coverPulse.isRunning()) {
            return;
        }
        coverPulse = ValueAnimator.ofFloat(1f, 1.025f, 1f);
        coverPulse.setDuration(1500);
        coverPulse.setRepeatCount(ValueAnimator.INFINITE);
        coverPulse.addUpdateListener(animation -> {
            float scale = (float) animation.getAnimatedValue();
            coverPreview.setScaleX(scale);
            coverPreview.setScaleY(scale);
        });
        coverPulse.start();
    }

    private void stopCoverPulse() {
        if (coverPulse != null) {
            coverPulse.cancel();
        }
        if (coverPreview != null) {
            coverPreview.animate().scaleX(1f).scaleY(1f).setDuration(180).start();
        }
    }

    private void animateEntrance(View view) {
        view.setAlpha(0f);
        view.setTranslationY(dp(18));
        view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(520)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private void animatePop(View view) {
        ObjectAnimator sx = ObjectAnimator.ofFloat(view, View.SCALE_X, 0.96f, 1.03f, 1f);
        ObjectAnimator sy = ObjectAnimator.ofFloat(view, View.SCALE_Y, 0.96f, 1.03f, 1f);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(sx, sy);
        set.setDuration(260);
        set.start();
    }

    private EditText input(String hint) {
        EditText editText = new EditText(this);
        editText.setHint(hint);
        editText.setTextColor(Color.WHITE);
        editText.setHintTextColor(Color.argb(145, 255, 255, 255));
        editText.setTextSize(15);
        editText.setSingleLine(false);
        editText.setPadding(dp(14), dp(10), dp(14), dp(10));
        editText.setBackground(new GlassDrawable(20, getResources().getDisplayMetrics().density));
        return editText;
    }

    private LinearLayout glassPanel(float radiusDp) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(14), dp(14), dp(14));
        panel.setBackground(new GlassDrawable(radiusDp, getResources().getDisplayMetrics().density));
        panel.setClipToOutline(true);
        panel.setElevation(dp(12));
        return panel;
    }

    private TextView pill(String label, boolean hot) {
        TextView view = text(label, 15, Color.WHITE, true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(14), 0, dp(14), 0);
        view.setMinHeight(dp(50));
        if (hot) {
            view.setBackground(gradientBg(28, Color.rgb(184, 62, 255), Color.rgb(86, 76, 255)));
            view.setShadowLayer(dp(10), 0, 0, Color.rgb(167, 63, 255));
        } else {
            view.setBackground(new GlassDrawable(28, getResources().getDisplayMetrics().density));
        }
        view.setClipToOutline(true);
        view.setClickable(true);
        view.setFocusable(true);
        return view;
    }

    private TextView glassChip(String label) {
        TextView view = text(label, 13, Color.argb(210, 255, 255, 255), true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(14), 0, dp(14), 0);
        view.setMinHeight(dp(42));
        view.setBackground(new GlassDrawable(28, getResources().getDisplayMetrics().density));
        view.setClipToOutline(true);
        return view;
    }

    private TextView sectionTitle(String label) {
        TextView view = text(label, 18, Color.WHITE, true);
        view.setPadding(0, 0, 0, dp(10));
        return view;
    }

    private TextView subText(String value) {
        return text(value, 13, Color.argb(170, 244, 238, 255), false);
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView textView = new TextView(this);
        textView.setText(value);
        textView.setTextSize(sp);
        textView.setTextColor(color);
        textView.setIncludeFontPadding(true);
        textView.setLetterSpacing(0f);
        if (bold) {
            textView.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        }
        return textView;
    }

    private GradientDrawable gradientBg(float radiusDp, int start, int end) {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{start, end});
        drawable.setCornerRadius(dp(Math.round(radiusDp)));
        drawable.setStroke(dp(1), Color.argb(96, 255, 255, 255));
        return drawable;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }

    private LinearLayout.LayoutParams weightParams() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private Space space(int dp) {
        Space space = new Space(this);
        space.setLayoutParams(new LinearLayout.LayoutParams(1, dp(dp)));
        return space;
    }

    private Space spaceW(int dp) {
        Space space = new Space(this);
        space.setLayoutParams(new LinearLayout.LayoutParams(dp(dp), 1));
        return space;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String formatDuration(long durationMs) {
        if (durationMs <= 0) {
            return "--:--";
        }
        long totalSeconds = durationMs / 1000L;
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return String.format(Locale.US, "%d:%02d", minutes, seconds);
    }

    private String safe(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim();
    }

    private String value(EditText input, String fallback) {
        String value = input.getText().toString().trim();
        return value.isEmpty() ? fallback : value;
    }

    private String sanitize(String value) {
        String cleaned = value.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return cleaned.isEmpty() ? "tagged-song" : cleaned.toLowerCase(Locale.US);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
