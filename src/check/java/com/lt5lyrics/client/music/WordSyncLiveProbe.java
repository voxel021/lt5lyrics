package com.lt5lyrics.client.music;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lt5lyrics.client.island.IslandSettings;
import com.lt5lyrics.client.island.Karaoke;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Optional live-service check; provider data/availability and licensing can change. */
public final class WordSyncLiveProbe {
    private static void none() {
        for (String id : IslandSettings.DEFAULT_PROVIDER_PRIORITY) IslandSettings.setProviderEnabled(id, false);
        LyricsFetcher.clearCache();
    }
    private static int spans(List<LyricsFetcher.LyricLine> lines) {
        return lines.stream().mapToInt(l -> l.words().size()).sum();
    }
    public static void main(String[] args) throws Exception {
        String liveLrcFile = System.getenv("LT5_LIVE_LRCLIB_JSON");
        String liveTtmlFile = System.getenv("LT5_LIVE_BETTER_JSON");
        if (liveLrcFile != null && !liveLrcFile.isBlank()) {
            JsonObject record = JsonParser.parseString(Files.readString(Path.of(liveLrcFile))).getAsJsonObject();
            var lines = LyricsFetcher.parseLrclibTimed(record);
            System.out.printf("LRCLIB captured public record: %d lines, %d timed segments, detailed=%s%n", lines.size(), spans(lines), LyricsFetcher.hasWordTiming(lines));
            assert LyricsFetcher.hasWordTiming(lines) && spans(lines) > 50;
            var first = lines.get(0);
            near(first.time(), .290F, .002F); near(first.words().get(0).startSec(), .290F, .002F);
            near(first.words().get(1).startSec(), .536F, .002F);
        }
        if (liveTtmlFile != null && !liveTtmlFile.isBlank()) {
            JsonObject response = JsonParser.parseString(Files.readString(Path.of(liveTtmlFile))).getAsJsonObject();
            var lines = BetterLyricsProvider.parseTtml(response.get("ttml").getAsString());
            System.out.printf("BetterLyrics captured public TTML: %d lines, %d timed segments, detailed=%s%n", lines.size(), spans(lines), LyricsFetcher.hasWordTiming(lines));
            assert LyricsFetcher.hasWordTiming(lines) && spans(lines) > 50;
            var first = lines.get(0); assert first.words().size() == 4;
            near(first.time(), 33.522F, .002F); near(first.words().get(1).startSec(), 33.855F, .002F);
            assert first.text().substring(first.words().get(1).charStart(), first.words().get(1).charEnd()).equals("at");
            near(Karaoke.sungChars(first, 0, 33.855F), first.words().get(1).charStart(), .002F);
        }
        IslandSettings.resetAllSettingsToFactory(); none();
        IslandSettings.enableLrclib = true; IslandSettings.enableBetterLyrics = true; IslandSettings.preferWordTiming = true;
        long start = System.nanoTime();
        var finalResult = LyricsFetcher.fetch("spotify.exe", "Coldplay", "Yellow", 268, true,
            r -> System.out.printf("Provisional enabled-source match: %s (%d lines) after %.2fs%n", r.syncQuality(), r.lines().size(), (System.nanoTime()-start)/1e9));
        System.out.printf("Live default preference: %s, source %s, %d lines, %d timed segments, %.2fs%n",
            finalResult.syncQuality(), finalResult.source(), finalResult.lines().size(), spans(finalResult.lines()), (System.nanoTime()-start)/1e9);
        none(); IslandSettings.enableLrcmux = true;
        LyricsFetcher.ProviderHit mux = LrcmuxLyrics.fetch("Coldplay", "Yellow", 266);
        System.out.printf("Live Lrcmux restricted cache: %s, %d lines, %d timed segments%n", mux.quality(), mux.synced().size(), spans(mux.synced()));
        none(); IslandSettings.enableLrclib = true; IslandSettings.enableBetterLyrics = true; IslandSettings.preferWordTiming = false;
        var firstTimed = LyricsFetcher.fetch("spotify.exe", "Coldplay", "Yellow", 268, true);
        System.out.printf("Live first-timed preference OFF: %s, source %s%n", firstTimed.syncQuality(), firstTimed.source());
        IslandSettings.resetAllSettingsToFactory();
    }
    private static void near(float a, float b, float tolerance) { assert Math.abs(a-b) <= tolerance : a+" vs "+b; }
    private WordSyncLiveProbe() {}
}
