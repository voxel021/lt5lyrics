package com.lt5lyrics.client.island;

import com.lt5lyrics.client.island.font.UnicodeClusterer;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.ToDoubleFunction;

/** Source indices, hard breaks and cluster safety for long Sky lyrics. */
final class SkyWrapCheck {
    static void run() {
        englishAndOffsets();
        hardBreaks();
        longWordsAndUnicode();
        System.out.println("PASS: Sky width wrapping, centered source spans, hard breaks and Unicode/karaoke indices");
    }

    private static final ToDoubleFunction<String> SIMPLE_WIDTH = s -> s.codePointCount(0, s.length());

    private static void checkSpans(String input, List<SkyLineWrap.Span> spans) {
        int prior = 0;
        Set<Integer> safe = new HashSet<>(); safe.add(0); safe.add(input.length());
        for (UnicodeClusterer.Cluster c : UnicodeClusterer.segment(input)) { safe.add(c.start()); safe.add(c.end()); }
        for (SkyLineWrap.Span span : spans) {
            assert span.start() >= prior && span.end() <= input.length();
            assert input.substring(span.start(), span.end()).equals(span.text());
            assert safe.contains(span.start()) && safe.contains(span.end()) : "split a Unicode cluster";
            for (int i = 0; i < span.text().length(); i++)
                assert span.originalIndex(i) == span.start() + i : "karaoke source offset changed";
            prior = span.end();
        }
    }

    private static void englishAndOffsets() {
        String input = "One long lyric should stay in a single centered place when the next line is sung";
        List<SkyLineWrap.Span> rows = SkyLineWrap.wrap(input, 14, SIMPLE_WIDTH);
        assert rows.size() >= 4 : rows;
        for (SkyLineWrap.Span row : rows) assert SIMPLE_WIDTH.applyAsDouble(row.text()) <= 14 : row;
        checkSpans(input, rows);
        String rebuilt = String.join(" ", rows.stream().map(SkyLineWrap.Span::text).toList());
        assert rebuilt.equals(input) : rebuilt;
        assert SkyLineWrap.wrap("Short line", 30, SIMPLE_WIDTH).size() == 1;
        assert SkyLineWrap.wrap("", 10, SIMPLE_WIDTH).isEmpty();
    }

    private static void hardBreaks() {
        String source = "first\r\n\r\nlast";
        List<SkyLineWrap.Span> rows = SkyLineWrap.wrap(source, 20, SIMPLE_WIDTH);
        assert rows.size() == 3 && rows.get(0).text().equals("first") && rows.get(1).text().isEmpty()
                && rows.get(2).text().equals("last") : rows;
        assert rows.get(2).start() == 9;
        assert SkyLineWrap.wrap("line\n", 10, SIMPLE_WIDTH).size() == 2;
    }

    private static void longWordsAndUnicode() {
        String english = "supercalifragilisticexpialidocious";
        List<SkyLineWrap.Span> rows = SkyLineWrap.wrap(english, 6, SIMPLE_WIDTH);
        assert rows.size() > 3;
        assert String.join("", rows.stream().map(SkyLineWrap.Span::text).toList()).equals(english);
        checkSpans(english, rows);
        String international = "កម្ពុជា ខ្មែរ 👩‍🎤👩‍🎤 中文没有空格的长句 e\u0301e\u0301e\u0301";
        List<SkyLineWrap.Span> internationalRows = SkyLineWrap.wrap(international, 7, SIMPLE_WIDTH);
        assert internationalRows.size() > 3;
        checkSpans(international, internationalRows);
        for (SkyLineWrap.Span row : internationalRows) {
            assert !row.text().endsWith("\u200d") : "split emoji joiner";
            assert row.text().isEmpty() || !Character.isLowSurrogate(row.text().charAt(0));
            assert row.text().isEmpty() || !Character.isHighSurrogate(row.text().charAt(row.text().length() - 1));
        }
        String arabic = "مرحبابالعالم";
        List<SkyLineWrap.Span> rtl = SkyLineWrap.wrap(arabic, 3, SIMPLE_WIDTH);
        assert rtl.size() == 1 && rtl.getFirst().text().equals(arabic) : "do not split a shaped RTL run";
        String noBreak = "hello\u00a0world";
        List<SkyLineWrap.Span> nbsp = SkyLineWrap.wrap(noBreak, 20, SIMPLE_WIDTH);
        assert nbsp.size() == 1 && nbsp.getFirst().text().equals(noBreak);
    }

    private SkyWrapCheck() {}
}
