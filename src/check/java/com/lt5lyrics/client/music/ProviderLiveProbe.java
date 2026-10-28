package com.lt5lyrics.client.music;

import com.lt5lyrics.client.island.IslandSettings;
import java.util.List;
import java.util.function.Supplier;

/** Optional network smoke test. Provider availability and coverage change over time. */
public final class ProviderLiveProbe {
    private static void probe(String name, Supplier<LyricsFetcher.ProviderHit> lookup) {
        long start = System.currentTimeMillis();
        try {
            LyricsFetcher.ProviderHit hit = lookup.get();
            System.out.printf("%s: %s; synced=%d plain=%d; %d ms%n", name, hit.quality(),
                hit.synced().size(), hit.plain().size(), System.currentTimeMillis() - start);
        } catch (Throwable e) {
            System.out.printf("%s: ERROR %s; %d ms%n", name, e.getClass().getSimpleName(), System.currentTimeMillis() - start);
        }
    }

    private static void noSources() {
        for (String id : IslandSettings.DEFAULT_PROVIDER_PRIORITY) IslandSettings.setProviderEnabled(id, false);
        LyricsFetcher.clearCache();
    }

    public static void main(String[] args) {
        IslandSettings.resetAllSettingsToFactory();
        IslandSettings.musixmatchApiKey = "";
        IslandSettings.betterLyricsApiKey = "";
        probe("BetterLyrics", () -> BetterLyricsProvider.fetch("The Weeknd", "Blinding Lights", 200));
        probe("NetEase", () -> {
            List<LyricsFetcher.LyricLine> lines = NeteaseLyrics.fetch("The Weeknd", "Blinding Lights", 200);
            return new LyricsFetcher.ProviderHit(lines, List.of(), lines.isEmpty() ? LyricsFetcher.SyncQuality.NOT_FOUND : LyricsFetcher.SyncQuality.LINE_SYNCED, "NetEase");
        });
        probe("KuGou", () -> {
            List<LyricsFetcher.LyricLine> lines = KugouLyrics.fetch("The Weeknd", "Blinding Lights", 200);
            return new LyricsFetcher.ProviderHit(lines, List.of(), lines.isEmpty() ? LyricsFetcher.SyncQuality.NOT_FOUND : LyricsFetcher.SyncQuality.LINE_SYNCED, "KuGou");
        });
        probe("Lrcmux (safe cached LRCLIB/KuGou)", () -> LrcmuxLyrics.fetch("VannDa", "Time to Rise", 302));
        probe("Lyrics.ovh", () -> LyricsOvhProvider.fetchPlain("Coldplay", "Yellow"));
        noSources();
        IslandSettings.enableLrclib = true;
        long start = System.currentTimeMillis();
        LyricsFetcher.Result lrclib = LyricsFetcher.fetch("spotify.exe", "The Weeknd", "Blinding Lights", 200, true);
        System.out.printf("LRCLIB cascade: %s; %d lines; %d ms%n", lrclib.syncQuality(), lrclib.rawLines().size(), System.currentTimeMillis() - start);
        long cachedStart = System.currentTimeMillis();
        LyricsFetcher.Result cached = LyricsFetcher.fetch("spotify.exe", "The Weeknd", "Blinding Lights", 200, false);
        System.out.printf("LRCLIB cache: %s; %d ms%n", cached.syncQuality(), System.currentTimeMillis() - cachedStart);
        noSources();
        IslandSettings.enableLyricsOvh = true;
        IslandSettings.enableLrclib = true;
        IslandSettings.providerPriority = new java.util.ArrayList<>(List.of("lyricsovh", "lrclib"));
        LyricsFetcher.Result preferred = LyricsFetcher.fetch("spotify.exe", "Coldplay", "Yellow", 266, true);
        System.out.printf("Plain-first priority still prefers synced: %s from %s%n", preferred.syncQuality(), preferred.source());
        noSources();
        LyricsFetcher.Result disabled = LyricsFetcher.fetch("spotify.exe", "Test Artist", "Example Song", 180, true);
        System.out.println("All providers disabled status: " + disabled.syncQuality());
        assert disabled.syncQuality() == LyricsFetcher.SyncQuality.PROVIDER_UNAVAILABLE;
        System.out.println("Musixmatch: not probed (no personal key configured)");
        IslandSettings.resetAllSettingsToFactory();
    }
    private ProviderLiveProbe() {}
}
