package dev.codex.lyricatagger;

import android.content.Context;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

final class Id3Writer {
    private static final Charset UTF_16LE = Charset.forName("UTF-16LE");

    private Id3Writer() {
    }

    static void writeTaggedMp3(
            Context context,
            Uri sourceAudio,
            byte[] coverBytes,
            String coverMime,
            OutputStream output,
            String title,
            String artist,
            String album,
            String lyrics
    ) throws IOException {
        byte[] source = readAll(context, sourceAudio);
        int audioOffset = existingId3Size(source);

        ByteArrayOutputStream frames = new ByteArrayOutputStream();
        addTextFrame(frames, "TIT2", clean(title));
        addTextFrame(frames, "TPE1", clean(artist));
        addTextFrame(frames, "TALB", clean(album));
        addLyricsFrame(frames, clean(lyrics));
        addCoverFrame(frames, coverBytes, coverMime);

        byte[] frameBytes = frames.toByteArray();
        output.write(new byte[]{'I', 'D', '3', 3, 0, 0});
        output.write(syncSafe(frameBytes.length));
        output.write(frameBytes);
        output.write(source, audioOffset, source.length - audioOffset);
    }

    static byte[] readAll(Context context, Uri uri) throws IOException {
        try (InputStream input = context.getContentResolver().openInputStream(uri);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (input == null) {
                throw new IOException("Cannot open selected file.");
            }
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private static void addTextFrame(ByteArrayOutputStream frames, String id, String value) throws IOException {
        if (value.isEmpty()) {
            return;
        }
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(1);
        body.write(new byte[]{(byte) 0xFF, (byte) 0xFE});
        body.write(value.getBytes(UTF_16LE));
        writeFrame(frames, id, body.toByteArray());
    }

    private static void addLyricsFrame(ByteArrayOutputStream frames, String lyrics) throws IOException {
        if (lyrics.isEmpty()) {
            return;
        }
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(1);
        body.write("eng".getBytes(StandardCharsets.ISO_8859_1));
        body.write(new byte[]{0, 0});
        body.write(new byte[]{(byte) 0xFF, (byte) 0xFE});
        body.write(lyrics.getBytes(UTF_16LE));
        writeFrame(frames, "USLT", body.toByteArray());
    }

    private static void addCoverFrame(ByteArrayOutputStream frames, byte[] coverBytes, String mime) throws IOException {
        if (coverBytes == null || coverBytes.length == 0) {
            return;
        }
        String safeMime = (mime == null || mime.trim().isEmpty()) ? "image/jpeg" : mime;
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(0);
        body.write(safeMime.getBytes(StandardCharsets.ISO_8859_1));
        body.write(0);
        body.write(3);
        body.write(0);
        body.write(coverBytes);
        writeFrame(frames, "APIC", body.toByteArray());
    }

    private static void writeFrame(ByteArrayOutputStream frames, String id, byte[] body) throws IOException {
        frames.write(id.getBytes(StandardCharsets.ISO_8859_1));
        frames.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(body.length).array());
        frames.write(new byte[]{0, 0});
        frames.write(body);
    }

    private static byte[] syncSafe(int value) {
        return new byte[]{
                (byte) ((value >> 21) & 0x7F),
                (byte) ((value >> 14) & 0x7F),
                (byte) ((value >> 7) & 0x7F),
                (byte) (value & 0x7F)
        };
    }

    private static int existingId3Size(byte[] data) {
        if (data.length < 10 || data[0] != 'I' || data[1] != 'D' || data[2] != '3') {
            return 0;
        }
        int size = ((data[6] & 0x7F) << 21)
                | ((data[7] & 0x7F) << 14)
                | ((data[8] & 0x7F) << 7)
                | (data[9] & 0x7F);
        return Math.min(data.length, 10 + size);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
