package com.lt5lyrics.client.music;

import com.lt5lyrics.client.island.IslandSettings;
import java.util.List;

/** Optional live-only probe. Prints classifications/counts, never lyric text. */
public final class CommunityLiveProbe {
    private record Song(String artist, String title, long seconds) {}
    private static void clear() {
        for (String id : IslandSettings.DEFAULT_PROVIDER_PRIORITY) IslandSettings.setProviderEnabled(id, false);
        LyricsFetcher.clearCache();
    }
    public static void main(String[] args) {
        IslandSettings.resetAllSettingsToFactory();
        List<Song> songs = List.of(new Song("Coldplay", "Yellow", 267),
            new Song("Rick Astley", "Never Gonna Give You Up", 213),
            new Song("YOASOBI", "Idol", 213));
        for (Song song : songs) {
            clear();
            for (String id : List.of("lrclib", "unison", "amll", "betterlyrics", "lrcmux")) IslandSettings.setProviderEnabled(id, true);
            IslandSettings.providerPriority = new java.util.ArrayList<>(List.of("lrclib", "unison", "amll", "betterlyrics", "lrcmux"));
            System.out.printf("%n--- %s / %s (%ds) --- %n", song.artist(), song.title(), song.seconds());
            LyricsFetcher.compareSources("spotify.exe", song.artist(), song.title(), song.seconds(), () -> true,
                s -> { if (s.status() != LyricsFetcher.ComparisonStatus.DISABLED)
                    System.out.printf("%s: %s; %d/%d lines%n", s.name(), s.status(), s.wordLines(), s.totalLines()); });
            clear(); IslandSettings.enableLrclib = true; IslandSettings.enableUnison = true; IslandSettings.enableAmll = true;
            IslandSettings.providerPriority = new java.util.ArrayList<>(List.of("lrclib", "unison", "amll"));
            IslandSettings.preferWordTiming = true;
            long start = System.nanoTime();
            var finalResult = LyricsFetcher.fetch("spotify.exe", song.artist(), song.title(), song.seconds(), true,
                p -> System.out.printf("provisional: %s from %s%n", p.syncQuality(), p.source()));
            System.out.printf("auto: %s from %s; lines=%d; segments=%d; %.2fs%n", finalResult.syncQuality(),
                finalResult.source(), finalResult.lines().size(), finalResult.lines().stream().mapToInt(l -> l.words().size()).sum(),
                (System.nanoTime()-start)/1e9);
            IslandSettings.preferWordTiming = false;
            var old = LyricsFetcher.fetch("spotify.exe", song.artist(), song.title(), song.seconds(), true);
            System.out.printf("preference off: %s from %s%n", old.syncQuality(), old.source());
        }
        IslandSettings.resetAllSettingsToFactory();
    }
    private CommunityLiveProbe() {}
}
