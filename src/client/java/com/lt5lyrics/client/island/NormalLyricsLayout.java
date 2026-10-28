package com.lt5lyrics.client.island;

import java.util.ArrayList;
import java.util.List;

/** Normal 3D-only word wrapping. Offsets are UTF-16 indices into the original lyric. */
final class NormalLyricsLayout {
    record Span(int start, int end, String text) {
        Span {
            if (start < 0 || end < start || text == null) throw new IllegalArgumentException();
        }
        int originalIndex(int localIndex) { return start + localIndex; }
    }

    /**
     * Wrap only at existing breakable whitespace, not inside a word or Unicode cluster.
     * Existing hard line breaks (including CRLF and Unicode paragraph breaks) are retained.
     * Empty source lines become empty spans. Leading/trailing break whitespace is not rendered;
     * punctuation, word contents and interior whitespace remain byte-for-byte unchanged.
     */
    static List<Span> wrap(String source, int maximumWords) {
        if (source == null || source.isEmpty()) return List.of();
        int limit = Math.max(1, maximumWords);
        List<Span> result = new ArrayList<>();
        int i = 0, start = -1, end = -1, words = 0;
        boolean endedWithBreak = false;
        while (i < source.length()) {
            int cp = source.codePointAt(i);
            if (hardBreak(cp)) {
                if (start >= 0) result.add(new Span(start, end, source.substring(start, end)));
                else result.add(new Span(i, i, ""));
                i += Character.charCount(cp);
                if (cp == '\r' && i < source.length() && source.charAt(i) == '\n') i++;
                start = -1; end = -1; words = 0; endedWithBreak = true;
                continue;
            }
            if (breakableSpace(cp)) {
                i += Character.charCount(cp);
                continue;
            }
            int wordStart = i;
            do {
                i += Character.charCount(cp);
                if (i >= source.length()) break;
                cp = source.codePointAt(i);
            } while (!hardBreak(cp) && !breakableSpace(cp));
            if (words >= limit) {
                result.add(new Span(start, end, source.substring(start, end)));
                start = -1; words = 0;
            }
            if (start < 0) start = wordStart;
            end = i;
            words++;
            endedWithBreak = false;
        }
        if (start >= 0) result.add(new Span(start, end, source.substring(start, end)));
        else if (endedWithBreak) result.add(new Span(source.length(), source.length(), ""));
        return List.copyOf(result);
    }

    private static boolean hardBreak(int cp) {
        return cp == '\n' || cp == '\r' || cp == 0x2028 || cp == 0x2029;
    }

    private static boolean breakableSpace(int cp) {
        // NBSP and narrow NBSP deliberately stay within a word; zero-width space is a break.
        return cp == 0x200B || (cp != 0x00A0 && cp != 0x202F
            && (Character.isWhitespace(cp) || Character.isSpaceChar(cp)));
    }

    static float rowGap(float fontSize, float spacing) {
        return Math.max(0.0F, fontSize) * (0.40F + 0.18F * (Math.clamp(spacing, 0.75F, 2.0F) - 0.75F));
    }

    private NormalLyricsLayout() {}
}
