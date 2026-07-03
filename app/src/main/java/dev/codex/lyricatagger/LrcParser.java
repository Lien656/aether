package dev.codex.lyricatagger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class LrcParser {
    private static final Pattern TIME_TAG = Pattern.compile("\\[(\\d{1,2}):(\\d{2})(?:\\.(\\d{1,3}))?]");

    private LrcParser() {
    }

    static List<LrcLine> parse(String raw) {
        List<LrcLine> lines = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) {
            return lines;
        }

        for (String row : raw.split("\\R")) {
            Matcher matcher = TIME_TAG.matcher(row);
            List<Long> times = new ArrayList<>();
            int lyricStart = 0;
            while (matcher.find()) {
                lyricStart = matcher.end();
                int min = Integer.parseInt(matcher.group(1));
                int sec = Integer.parseInt(matcher.group(2));
                String frac = matcher.group(3);
                int ms = 0;
                if (frac != null) {
                    if (frac.length() == 1) {
                        ms = Integer.parseInt(frac) * 100;
                    } else if (frac.length() == 2) {
                        ms = Integer.parseInt(frac) * 10;
                    } else {
                        ms = Integer.parseInt(frac);
                    }
                }
                times.add((min * 60_000L) + (sec * 1000L) + ms);
            }
            if (!times.isEmpty() && lyricStart <= row.length()) {
                String text = row.substring(lyricStart).trim();
                for (Long time : times) {
                    lines.add(new LrcLine(time, text));
                }
            }
        }

        Collections.sort(lines, Comparator.comparingLong(line -> line.timeMs));
        return lines;
    }
}
