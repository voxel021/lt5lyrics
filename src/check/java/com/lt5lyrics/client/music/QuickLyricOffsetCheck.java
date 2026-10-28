package com.lt5lyrics.client.music;

import com.lt5lyrics.client.island.IslandSettings;
import dev.redstones.mediaplayerinfo.IMediaSession;
import dev.redstones.mediaplayerinfo.MediaInfo;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Regression coverage for quick timing, saved-delay isolation and media lifecycle. */
public final class QuickLyricOffsetCheck {
    private static final long S = 1_000_000_000L;
    private QuickLyricOffsetCheck() {}

    private static void eq(float actual, float expected) {
        assert Math.abs(actual - expected) < 0.0005F : actual + " != " + expected;
    }

    private static IMediaSession session() {
        MediaInfo info = new MediaInfo("Quick timing check only", "LT5 test artist", new byte[0], 5, 120, false);
        return new IMediaSession() {
            public MediaInfo getMedia() { return info; }
            public String getOwner() { return "Spotify.exe"; }
            public void play() {} public void pause() {} public void playPause() {} public void stop() {}
            public void next() {} public void previous() {} public void swapCycle() {} public int getCycleType() { return 0; }
        };
    }

    public static void run() throws Exception {
        QuickLyricOffset q = new QuickLyricOffset();
        q.observe("A", 10, 120, 10, true, S);
        q.adjust(1.7F, 1); eq(q.value(), 1);
        q.observe("A", 11, 120, 11, false, 2*S); eq(q.value(), 1); // pause
        q.observe("A", 50, 120, 50, true, 3*S);
        q.observe("A", 25, 120, 25, true, 4*S); eq(q.value(), 1); // ordinary seek
        q.observe("A", 0, 120, 0, true, 5*S);
        q.observe("A", 25, 120, 25, true, 6*S); eq(q.value(), 1); // one-poll zero jitter
        q.observe("A", 0, 120, 0, false, 7*S); // paused seek to beginning
        q.observe("A", 1, 120, 1, true, 8*S); eq(q.value(), 1);
        q.observe("A", 119, 120, 119.9F, true, 9*S); eq(q.value(), 1);
        q.observe("A", 120, 120, 120, false, 10*S); eq(q.value(), 0); // reported end

        QuickLyricOffset shortClip = new QuickLyricOffset();
        shortClip.observe("short", 2, 5, 2, true, S);
        shortClip.adjust(0, 1);
        shortClip.observe("short", 4, 5, 4, true, 2*S);
        shortClip.observe("short", 0, 5, 0, true, 3*S); eq(shortClip.value(), 1);
        shortClip.observe("short", 1, 5, 1, true, 4*S); eq(shortClip.value(), 0);

        q.adjust(1.7F, -0.5F); eq(q.value(), -0.5F);
        q.observe("A", 80, 120, 80, true, 11*S);
        q.observe("A", 119, 120, 120, true, 12*S); eq(q.value(), 0); // clock reaches end
        q.adjust(1.7F, 0.1F);
        q.observe("B", 8, 120, 8, true, 13*S); eq(q.value(), 0); // track change

        q.adjust(0, 1);
        q.observe("B", 45, 0, 45, true, 14*S);
        q.observe("B", 0, 0, 0, true, 15*S); eq(q.value(), 1);
        q.observe("B", 1, 0, 1, true, 16*S); eq(q.value(), 0); // same-song restart, no duration
        q.adjust(0, 1);
        q.observe("B", 20, 30, 20, true, 17*S);
        q.observe("B", 0, 120, 0, true, 18*S);
        q.observe("B", 1, 120, 1, true, 19*S); eq(q.value(), 1); // corrected duration isn't a loop

        q.missing(20*S); q.observe("B", 2, 120, 2, true, 21*S); eq(q.value(), 1); // brief dropout
        q.missing(22*S); q.missing(24*S); eq(q.value(), 0); // session truly gone
        q.observe("B", 3, 120, 3, true, 25*S);
        q.adjust(9.9F, 1); eq(q.value(), .1F);
        q.adjust(9.9F, .1F); eq(q.value(), .1F); // combined +10 s limit
        q.clearAdjustment();
        q.adjust(-9.9F, -1); eq(q.value(), -.1F);
        q.clearAdjustment();
        for (int i=0; i<37; i++) q.adjust(0, .1F);
        eq(q.value(), 3.7F); // no accumulated float drift
        q.adjust(0, Float.NaN); eq(q.value(), 3.7F);

        MusicTracker t = new MusicTracker(false);
        Field sess = MusicTracker.class.getDeclaredField("session"); sess.setAccessible(true);
        Field saved = MusicTracker.class.getDeclaredField("lyricsOffset"); saved.setAccessible(true);
        Field clockField = MusicTracker.class.getDeclaredField("playbackClock"); clockField.setAccessible(true);
        // Headless Fabric has no game/config directory. Give this test an isolated one,
        // then restore the loader field; no real user configuration is touched.
        Object loader = net.fabricmc.loader.api.FabricLoader.getInstance();
        Field configDir = loader.getClass().getDeclaredField("configDir"); configDir.setAccessible(true);
        Object oldConfigDir = configDir.get(loader);
        Path temp = Files.createTempDirectory("lt5-quick-timing-");
        configDir.set(loader, temp.resolve("config"));
        Path delays = IslandSettings.configDir().resolve("delays.json");
        byte[] before = Files.exists(delays) ? Files.readAllBytes(delays) : null;
        try {
            sess.set(t, session()); saved.setFloat(t, 1.7F);
            ((MusicTracker.PlaybackClock) clockField.get(t)).update(5, 120, false, 1_000);
            t.adjustQuickLyricsOffset(-.5F);
            eq(t.getSavedLyricsOffset(), 1.7F); eq(t.getQuickLyricsOffset(), -.5F); eq(t.getLyricsOffset(), 1.2F);
            eq(t.rawPosition(), 5.5F); eq(t.smoothPosition(), 6.7F);
            t.resetQuickLyricsOffset(); eq(t.getLyricsOffset(), 1.7F); eq(t.rawPosition(), 5.5F);
            t.adjustQuickLyricsOffset(.1F);
            byte[] after = Files.exists(delays) ? Files.readAllBytes(delays) : null;
            assert Arrays.equals(before, after) : "quick buttons must not write saved delays";
            // Explicit persistent buttons save the effective total and clear the quick portion.
            t.adjustLyricsOffset(.1F);
            eq(t.getLyricsOffset(), 1.9F); eq(t.getSavedLyricsOffset(), 1.9F); eq(t.getQuickLyricsOffset(), 0);
            eq(LyricDelays.get("LT5 test artist", "Quick timing check only"), 1.9F);
            byte[] savedFile = Files.readAllBytes(delays);
            t.adjustQuickLyricsOffset(1);
            assert Arrays.equals(savedFile, Files.readAllBytes(delays));
            eq(LyricDelays.get("LT5 test artist", "Quick timing check only"), 1.9F);
            float tap = SyncTools.offsetAtTap(7.2F, t.rawPosition());
            t.setLyricsOffset(tap); // tap uses raw clock; must not double-count the quick nudge
            eq(tap, 1.7F); eq(t.getLyricsOffset(), 1.7F); eq(t.getQuickLyricsOffset(), 0);
            sess.set(t, null);
            t.adjustQuickLyricsOffset(1); eq(t.getQuickLyricsOffset(), 0);
        } finally {
            LyricDelays.set("LT5 test artist", "Quick timing check only", 0);
            t.shutdown();
            configDir.set(loader, oldConfigDir);
            try (var paths = Files.walk(temp)) {
                for (Path p : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
            }
        }
        System.out.println("PASS: quick offset, saved isolation, end/repeat/session lifecycle, jitter/seeks, clamping and tap sync");
    }
}
