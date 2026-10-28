package com.lt5lyrics.client.island;

import com.lt5lyrics.client.island.font.UnicodeClusterer;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.ToDoubleFunction;

/** Width-based Sky wrapping. Each span keeps its UTF-16 offset for karaoke word timing. */
final class SkyLineWrap {
    record Span(int start, int end, String text) {
        Span {
            if (start < 0 || end < start || text == null) throw new IllegalArgumentException();
        }
        int originalIndex(int localIndex) { return start + localIndex; }
    }

    static List<Span> wrap(String input, float maximumWidth, ToDoubleFunction<String> measure) {
        if (input == null || input.isEmpty()) return List.of();
        if (measure == null) throw new IllegalArgumentException("measure is required");
        double limit = Float.isFinite(maximumWidth) ? Math.max(1.0, maximumWidth) : 250.0;
        List<Span> rows = new ArrayList<>();
        int paragraphStart = 0;
        for (int i = 0; i < input.length();) {
            int cp = input.codePointAt(i);
            if (!hardBreak(cp)) { i += Character.charCount(cp); continue; }
            wrapParagraph(input, paragraphStart, i, limit, measure, rows);
            i += Character.charCount(cp);
            if (cp == '\r' && i < input.length() && input.charAt(i) == '\n') i++;
            paragraphStart = i;
        }
        wrapParagraph(input, paragraphStart, input.length(), limit, measure, rows);
        return List.copyOf(rows);
    }

    private static void wrapParagraph(String input, int start, int end, double limit,
            ToDoubleFunction<String> measure, List<Span> rows) {
        if (start == end) { rows.add(new Span(start, start, "")); return; }
        String paragraph = input.substring(start, end);
        BreakIterator words = BreakIterator.getLineInstance(Locale.ROOT);
        words.setText(paragraph);
        boolean[] safeEnds = new boolean[paragraph.length() + 1];
        safeEnds[0] = true;
        safeEnds[paragraph.length()] = true;
        for (UnicodeClusterer.Cluster c : UnicodeClusterer.segment(paragraph)) safeEnds[c.end()] = true;
        int cursor = 0;
        boolean any = false;
        while (cursor < paragraph.length()) {
            cursor = skipSpaces(paragraph, cursor);
            if (cursor >= paragraph.length()) break;
            int fitEnd = -1, fitNext = -1;
            for (int boundary = words.following(cursor); boundary != BreakIterator.DONE; boundary = words.next()) {
                int visibleEnd = trimSpaces(paragraph, cursor, boundary);
                if (visibleEnd <= cursor || !safeEnds[visibleEnd]) continue;
                double width = measure.applyAsDouble(paragraph.substring(cursor, visibleEnd));
                if (Double.isFinite(width) && width <= limit + 0.001) {
                    fitEnd = visibleEnd;
                    fitNext = boundary;
                } else break;
            }
            if (fitEnd < 0) {
                // A long word or an unspaced language: split only at the same safe shaping
                // cluster boundaries used by the dynamic font. One very wide cluster is kept intact.
                String rest = paragraph.substring(cursor);
                int fallback = -1, first = -1;
                for (UnicodeClusterer.Cluster c : UnicodeClusterer.segment(rest)) {
                    if (c.end() <= 0) continue;
                    if (first < 0) first = c.end();
                    double width = measure.applyAsDouble(rest.substring(0, c.end()));
                    if (Double.isFinite(width) && width <= limit + 0.001) fallback = c.end();
                    else break;
                }
                int length = fallback > 0 ? fallback : first;
                if (length <= 0) length = Character.charCount(rest.codePointAt(0));
                fitEnd = Math.min(paragraph.length(), cursor + length);
                fitNext = fitEnd;
            }
            int absoluteStart = start + cursor, absoluteEnd = start + fitEnd;
            rows.add(new Span(absoluteStart, absoluteEnd, input.substring(absoluteStart, absoluteEnd)));
            any = true;
            cursor = Math.max(fitNext, fitEnd);
        }
        if (!any) rows.add(new Span(start, start, ""));
    }

    private static int skipSpaces(String s, int from) {
        int i = from;
        while (i < s.length()) {
            int cp = s.codePointAt(i);
            if (!breakSpace(cp)) break;
            i += Character.charCount(cp);
        }
        return i;
    }

    private static int trimSpaces(String s, int start, int end) {
        int i = end;
        while (i > start) {
            int cp = s.codePointBefore(i);
            if (!breakSpace(cp)) break;
            i -= Character.charCount(cp);
        }
        return i;
    }

    private static boolean breakSpace(int cp) {
        return cp == 0x200B || (cp != 0x00A0 && cp != 0x202F
                && (Character.isWhitespace(cp) || Character.isSpaceChar(cp)));
    }

    private static boolean hardBreak(int cp) {
        return cp == '\n' || cp == '\r' || cp == 0x2028 || cp == 0x2029;
    }

    private SkyLineWrap() {}
}
