package com.lt5lyrics.client.music;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Reads LRCLIB's public Lyricsfile 1.0 field. Timestamps are absolute milliseconds.
 * This is deliberately a read-only, bounded, safe YAML parser: no Java tags,
 * duplicate keys, collection aliases, or unbounded documents. When a line's
 * words cannot be aligned with its text, keep the line and estimate its sweep.
 */
public final class LyricsFileParser {
    private static final int MAX_CHARS = 2_097_152;
    private static final long MAX_TIME_MS = 86_400_000L;
    private static final int MAX_LINES = 10_000;
    private static final int MAX_WORDS = 2_000;
    private static final int MAX_LINE_CHARS = 4_096;
    private LyricsFileParser() {}

    private record Piece(String text, long start, Long end) {}

    public static List<LyricsFetcher.LyricLine> parse(String document) {
        if (document == null || document.isBlank() || document.length() > MAX_CHARS) return List.of();
        try {
            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(false);
            options.setMaxAliasesForCollections(0);
            options.setNestingDepthLimit(32);
            options.setCodePointLimit(MAX_CHARS);
            Object loaded = new Yaml(new SafeConstructor(options)).load(document);
            if (!(loaded instanceof Map<?, ?> root) || !"1.0".equals(root.get("version"))
                    || !(root.get("metadata") instanceof Map<?, ?>)
                    || !(root.get("lines") instanceof List<?> input) || input.size() > MAX_LINES) return List.of();
            List<LyricsFetcher.LyricLine> lines = new ArrayList<>();
            for (Object item : input) {
                if (!(item instanceof Map<?, ?> line)) continue;
                if (!(line.get("text") instanceof String raw) || raw.length() > MAX_LINE_CHARS) continue;
                String text = raw.trim();
                Long start = millis(line.get("start_ms"));
                if (start == null || !LyricsFetcher.isRenderableLine(text)) continue;
                Long lineEnd = line.containsKey("end_ms") ? millis(line.get("end_ms")) : null;
                if (line.containsKey("end_ms") && (lineEnd == null || lineEnd < start)) continue;
                List<LyricsFetcher.WordSpan> words = words(line.get("words"), text, lineEnd);
                lines.add(new LyricsFetcher.LyricLine(start / 1000.0F, text, "", words));
            }
            lines.sort(Comparator.comparingDouble(LyricsFetcher.LyricLine::time));
            return List.copyOf(lines);
        } catch (RuntimeException e) {
            // A malformed, unsafe, or unsupported file must never break the existing LRC fallback.
            return List.of();
        }
    }

    private static List<LyricsFetcher.WordSpan> words(Object value, String lineText, Long lineEnd) {
        if (!(value instanceof List<?> entries) || entries.isEmpty() || entries.size() > MAX_WORDS) return List.of();
        List<Piece> pieces = new ArrayList<>();
        StringBuilder joined = new StringBuilder();
        long previous = -1;
        for (Object entry : entries) {
            if (!(entry instanceof Map<?, ?> word) || !(word.get("text") instanceof String text)) return List.of();
            Long start = millis(word.get("start_ms"));
            Long end = word.containsKey("end_ms") ? millis(word.get("end_ms")) : null;
            if (start == null || start < previous || (word.containsKey("end_ms") && (end == null || end < start))) return List.of();
            if (joined.length() + text.length() > MAX_LINE_CHARS + 512) return List.of();
            pieces.add(new Piece(text, start, end));
            joined.append(text);
            previous = start;
        }
        String all = joined.toString();
        String trimmed = all.trim();
        // The format recommends exact reconstruction. Never substitute incomplete or different word text.
        if (!trimmed.equals(lineText)) return List.of();
        int leading = all.indexOf(trimmed);
        if (leading < 0) return List.of();
        List<LyricsFetcher.WordSpan> spans = new ArrayList<>();
        int rawOffset = 0;
        for (int i = 0; i < pieces.size(); i++) {
            Piece piece = pieces.get(i);
            int begin = Math.clamp(rawOffset - leading, 0, lineText.length());
            rawOffset += piece.text().length();
            int endChar = Math.clamp(rawOffset - leading, 0, lineText.length());
            if (endChar <= begin) continue;
            long end = piece.end() != null ? piece.end()
                : i + 1 < pieces.size() ? pieces.get(i + 1).start()
                : lineEnd != null && lineEnd >= piece.start() ? lineEnd
                : Math.min(MAX_TIME_MS, piece.start() + 450L);
            // Missing ends are inferred from the next start or line end; they are not new source data.
            spans.add(new LyricsFetcher.WordSpan(piece.start() / 1000.0F, end / 1000.0F, begin, endChar));
        }
        return List.copyOf(spans);
    }

    private static Long millis(Object value) {
        if (!(value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long)) return null;
        long n = ((Number) value).longValue();
        return n >= 0 && n <= MAX_TIME_MS ? n : null;
    }
}
