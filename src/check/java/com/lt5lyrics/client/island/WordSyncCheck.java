package com.lt5lyrics.client.island;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.lt5lyrics.client.music.BetterLyricsProvider;
import com.lt5lyrics.client.music.LrcmuxLyrics;
import com.lt5lyrics.client.music.LyricsFetcher;
import com.lt5lyrics.client.music.LyricsFileParser;
import com.lt5lyrics.client.music.MusicTracker;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Deterministic, synthetic tests: these do not claim to test real Windows audio. */
public final class WordSyncCheck {
    private WordSyncCheck() {}
    public static void run() throws Exception {
        lyricsfile(); enhancedLrc(); ttml(); mux(); karaoke(); clock(); selectionAndSettings();
        System.out.println("PASS: word-source parsing, safety, clock, future-word masking, offsets, fallback and source preference");
    }
    private static void near(float actual, float expected, float tolerance) {
        assert Math.abs(actual - expected) <= tolerance : actual + " expected " + expected;
    }
    private static void lyricsfile() throws Exception {
        String yaml = """
            version: "1.0"
            metadata:
              title: Sample
              artist: Example
            lines:
              - text: "Hello world"
                start_ms: 1000
                end_ms: 4000
                words:
                  - text: "Hello "
                    start_ms: 1000
                  - text: "world"
                    start_ms: 2500
              - text: 'សួស្តី ពិភពលោក'
                start_ms: 5000
                words:
                  - text: 'សួស្តី '
                    start_ms: 5000
                    end_ms: 5600
                  - text: ពិភពលោក
                    start_ms: 5700
                    end_ms: 6500
            """;
        var lines = LyricsFileParser.parse(yaml);
        assert lines.size() == 2 && LyricsFetcher.hasWordTiming(lines);
        var one = lines.get(0); assert one.text().equals("Hello world") && one.words().size() == 2;
        near(one.words().get(0).startSec(), 1, .0001F); near(one.words().get(0).endSec(), 2.5F, .0001F);
        near(one.words().get(1).endSec(), 4, .0001F);
        assert one.text().substring(one.words().get(1).charStart(), one.words().get(1).charEnd()).equals("world");
        assert lines.get(1).text().substring(lines.get(1).words().get(1).charStart()).equals("ពិភពលោក");
        near(Karaoke.sungChars(one, .5F, 1.75F), 3.0F, .001F);
        JsonObject record = new JsonObject(); record.addProperty("trackName", "Sample"); record.addProperty("artistName", "Example");
        record.addProperty("duration", 100); record.addProperty("lyricsfile", yaml);
        record.addProperty("syncedLyrics", "[00:01.00]Hello world\n[00:05.00]សួស្តី ពិភពលោក");
        assert LyricsFetcher.hasWordTiming(LyricsFetcher.parseLrclibTimed(record));
        JsonArray array = new JsonArray(); array.add(record);
        var manual = LyricsFetcher.parseManualLrclib(array.toString(), 100, true);
        assert manual.size() == 1 && manual.get(0).result().syncQuality() == LyricsFetcher.SyncQuality.WORD_SYNCED;
        // Unaligned words do not replace the text, and only line-timed LRC is used.
        record.addProperty("lyricsfile", yaml.replace("text: \"Hello \"", "text: \"Different \""));
        var fallback = LyricsFetcher.parseLrclibTimed(record);
        assert fallback.size() == 2 && fallback.get(0).text().equals("Hello world") && !fallback.get(0).hasWordSpans();
        assert LyricsFileParser.parse(yaml.replace("version: \"1.0\"", "version: '2.0'")).isEmpty();
        assert LyricsFileParser.parse("!!java.net.URL ['https://example.invalid']").isEmpty();
        assert LyricsFileParser.parse(yaml + "\nversion: '1.0'\n").isEmpty();
        assert LyricsFileParser.parse("x".repeat(2_097_153)).isEmpty();
        String badTimes = yaml.replace("start_ms: 2500", "start_ms: 500");
        assert LyricsFileParser.parse(badTimes).get(0).words().isEmpty();
    }
    private static void enhancedLrc() {
        String lrc = "[offset:+500]\n[00:10.00][00:20.00] Hello <00:11.00>world<00:13.00>\n";
        var lines = LyricsFetcher.parseLrc(lrc); assert lines.size() == 2 : lines;
        var a = lines.get(0); var b = lines.get(1);
        assert a.text().equals("Hello world") && a.words().size() == 2 : a;
        near(a.time(), 9.5F, .001F); near(a.words().get(0).startSec(), 9.5F, .001F);
        near(a.words().get(0).endSec(), 10.5F, .001F); near(a.words().get(1).endSec(), 12.5F, .001F);
        near(b.time(), 19.5F, .001F); near(b.words().get(1).startSec(), 20.5F, .001F);
        near(b.words().get(1).endSec(), 22.5F, .001F);
        assert b.text().substring(b.words().get(1).charStart()).equals("world");
        var later = LyricsFetcher.parseLrc("[offset:-250]\n[00:01.00]<00:01.00>Go <00:02.00>on");
        near(later.get(0).time(), 1.25F, .001F); near(later.get(0).words().get(1).startSec(), 2.25F, .001F);
        var bad = LyricsFetcher.parseLrc("[00:01.00]<00:02.00>One <00:01.00>two\n[00:05.00]Next");
        assert bad.get(0).text().equals("One two") && bad.get(0).words().isEmpty();
        near(Karaoke.sungChars(bad.get(0), .5F, 3), 3.5F, .001F);
    }
    private static void ttml() {
        String xml = """
            <t:tt xmlns:t="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <t:body><t:div><t:p begin="10s" end="15s">
                <t:span begin="10s" end="14s">
                  <t:span begin="10s" end="11s">Hello</t:span>
                  <t:span begin="12s">world</t:span>
                  <t:span ttm:role="x-translation">Bonjour monde</t:span>
                </t:span>
              </t:p>
              <t:p begin="16s" end="20s"><t:span begin="16s" end="17s">Now </t:span><t:span begin="17s" end="19s">again</t:span></t:p>
              </t:div></t:body>
            </t:tt>
            """;
        var lines = BetterLyricsProvider.parseTtml(xml); assert lines.size() == 2 : lines;
        var first = lines.get(0);
        assert first.text().equals("Hello world") : "Normalized text: '" + first.text() + "'";
        assert first.translation().equals("Bonjour monde") && first.words().size() == 2 : first;
        var second = first.words().get(1);
        assert first.text().substring(second.charStart(), second.charEnd()).equals("world") : second;
        near(second.startSec(), 12F, .001F); near(second.endSec(), 14F, .001F);
        near(Karaoke.sungChars(first, .1F, 11.5F), first.words().get(0).charEnd(), .001F);
        assert BetterLyricsProvider.parseTtml("<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///etc/passwd'>]><tt><body><p begin='0'>&e;</p></body></tt>").isEmpty();
    }
    private static void mux() {
        String good = """
            {"track":{"title":"Example","artist":"Singer","duration":100},"meta":{"source":{"id":"kugou"},"level":"word"},"lines":[
            {"start":1000,"text":"A blue sky","words":[{"text":"A ","start":1000,"end":1200},{"text":"blue ","start":1200,"end":2000},{"text":"sky","start":2000,"end":3000}]},
            {"start":4000,"text":"Next line"},{"start":7000,"text":"Final line"}]}
            """;
        var hit = LrcmuxLyrics.parseJsonResponse(good, "Singer", "Example", 100);
        assert hit.quality() == LyricsFetcher.SyncQuality.WORD_SYNCED && hit.synced().get(0).words().size() == 3;
        var damaged = LrcmuxLyrics.parseJsonResponse(good.replace("\"text\":\"blue \"", "\"text\":\"missing \""), "Singer", "Example", 100);
        assert damaged.synced().get(0).text().equals("A blue sky") && damaged.synced().get(0).words().isEmpty();
        assert damaged.quality() == LyricsFetcher.SyncQuality.LINE_SYNCED : damaged.quality();
        assert !LrcmuxLyrics.parseJsonResponse(good.replace("kugou", "genius"), "Singer", "Example", 100).hasSynced();
    }
    private static void karaoke() {
        var line = new LyricsFetcher.LyricLine(1, "Go home", "", List.of(
            new LyricsFetcher.WordSpan(1, 2, 0, 2), new LyricsFetcher.WordSpan(3, 4, 3, 7)));
        var words = Karaoke.words(line.text());
        near(Karaoke.sungChars(line, .5F, 2.5F), 2, .001F);
        assert Karaoke.activeTimedWord(words, line, 2, 2.5F) == 0;
        assert Karaoke.activeTimedWord(words, line, 0, .8F) == -1;
        assert Karaoke.activeTimedWord(words, line, 3, 3F) == 1;
        var spaced = new LyricsFetcher.LyricLine(1, "Go home", "", List.of(
            new LyricsFetcher.WordSpan(1, 2, 0, 3), new LyricsFetcher.WordSpan(3, 4, 3, 7)));
        assert Karaoke.activeTimedWord(words, spaced, 3, 2.5F) == 0 : "trailing space must not trigger next word";
        var phrase = new LyricsFetcher.LyricLine(1, "Go home now", "", List.of(
            new LyricsFetcher.WordSpan(1, 3, 0, 8), new LyricsFetcher.WordSpan(4, 5, 8, 11)));
        assert Karaoke.activeTimedWord(Karaoke.words(phrase.text()), phrase, 4, 2.5F) == 1 : "multi-word segment can sweep internally";
        int upcoming = 0xFF111111, lit = 0xFFFFFFFF, hi = 0xFFFF0000;
        int[] cols = new int[line.text().length()];
        Karaoke.fillColors(cols, cols.length, 2, lit, hi, upcoming, 1);
        Karaoke.maskFutureTimedWords(cols, cols.length, 0, line, 2.5F, upcoming);
        for (int i=3; i<7; i++) assert cols[i] == upcoming : "future world/sky word lit early at " + i;
        int[] hud = new int[4]; Karaoke.fillColors(hud, 4, -1, lit, hi, upcoming, 1);
        Karaoke.maskFutureTimedWords(hud, 4, 3, line, 2.5F, upcoming);
        for (int color : hud) assert color == upcoming : "future HUD word lit early";
        var estimated = new LyricsFetcher.LyricLine(1, "Go home");
        near(Karaoke.sungChars(estimated, .5F, 2), 3.5F, .001F);
        int[] noTimes = new int[7], copy = new int[7];
        Karaoke.fillColors(noTimes, 7, 3, lit, hi, upcoming, 1); System.arraycopy(noTimes, 0, copy, 0, 7);
        Karaoke.maskFutureTimedWords(noTimes, 7, 0, estimated, 2, upcoming);
        assert java.util.Arrays.equals(noTimes, copy) : "estimated moving sweep must be retained";
        var overlap = new LyricsFetcher.LyricLine(0, "Go home", "", List.of(
            new LyricsFetcher.WordSpan(0, 3, 0, 2), new LyricsFetcher.WordSpan(1, 2, 3, 7)));
        assert Karaoke.sungChars(overlap, .1F, 1.2F) > 3.0F : "later overlapping segment started";
    }
    private static void clock() {
        MusicTracker.PlaybackClock c = new MusicTracker.PlaybackClock();
        c.update(0, 200, true, 1_000_000);
        c.update(0, 200, true, 1_000_150);
        near(c.positionAt(true, 1_000_150), 0.15F, .01F);
        c.reset();
        long now = 1_000_000; double real = 17.237; double max = 0, sum = 0; int samples = 0;
        for (int i=0; i<400; i++) {
            c.update((long)real, 200, true, now);
            if (i > 10) { double err = Math.abs(c.positionAt(true, now) - real); max = Math.max(max, err); sum += err; samples++; }
            long dt = (i % 3 == 0) ? 79 : (i % 3 == 1 ? 113 : 137);
            now += dt; real += dt / 1000.0;
        }
        assert max < .16 : "1-second clock boundaries should be sub-poll estimates; max=" + max;
        assert sum/samples < .06 : "clock mean error=" + sum/samples;
        c.update((long)real, 200, false, now); float paused = c.positionAt(false, now);
        for (int i=0; i<40; i++) { now+=100; c.update((long)real, 200, false, now); }
        near(c.positionAt(false, now), paused, .001F);
        now += 100; c.update((long)real, 200, true, now); near(c.positionAt(true, now), paused, .001F);
        now += 100; c.update(123, 200, true, now); near(c.positionAt(true, now), 123.5F, .01F);
        now += 100; c.update(0, 200, true, now); near(c.positionAt(true, now), 0, .01F);
    }
    private static void selectionAndSettings() throws Exception {
        var a=List.of(new LyricsFetcher.LyricLine(10,"Hello there","Bonjour",List.of()),
                      new LyricsFetcher.LyricLine(13,"Good night"));
        var b=List.of(new LyricsFetcher.LyricLine(10.4F,"Hello there","",List.of(
                          new LyricsFetcher.WordSpan(10.4F,11,0,5),new LyricsFetcher.WordSpan(11,12,6,11))),
                      new LyricsFetcher.LyricLine(13.2F,"Good night"));
        assert LyricsFetcher.comparableTimedLyrics(a,b);
        assert !LyricsFetcher.comparableTimedLyrics(a,List.of(new LyricsFetcher.LyricLine(10,"Another recording"),new LyricsFetcher.LyricLine(13,"Completely different")));
        var oldHit=new LyricsFetcher.ProviderHit(a,List.of(),LyricsFetcher.SyncQuality.LINE_SYNCED,"old");
        var newHit=new LyricsFetcher.ProviderHit(b,List.of(),LyricsFetcher.SyncQuality.WORD_SYNCED,"new");
        assert LyricsFetcher.preserveTranslations(oldHit,newHit).synced().get(0).translation().equals("Bonjour");
        Path f=Files.createTempFile("lt5-word-setting", ".json");
        try {
            IslandSettings.preferWordTiming=false; IslandSettings.saveToFile(f); IslandSettings.preferWordTiming=true;
            IslandSettings.loadFromFile(f); assert !IslandSettings.preferWordTiming;
            var style=new JsonObject();style.addProperty("format","lt5lyrics-style-1");style.addProperty("name","Test");
            var settings=new JsonObject();settings.addProperty("preferWordTiming",true);style.add("settings",settings);style.add("extras",new JsonObject());
            assert !StylePresets.sanitize(style).getAsJsonObject("settings").has("preferWordTiming");
            assert SettingsPanel.matchingSearchTitles("word timing").contains("Prefer Word Timings");
            assert SettingsPanel.matchingSearchTitles("unison").contains("Unison");
            assert SettingsPanel.matchingSearchTitles("amll").contains("AMLL TTML DB");
        } finally { Files.deleteIfExists(f); IslandSettings.preferWordTiming=true; }
    }
}
