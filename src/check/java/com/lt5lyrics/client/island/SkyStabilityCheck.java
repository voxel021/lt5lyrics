package com.lt5lyrics.client.island;

import com.lt5lyrics.client.music.LyricsFetcher;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/** Deterministic regression checks for Sky duplicate/context handling. */
final class SkyStabilityCheck {
    static void run() {
        equivalentListRefreshDoesNotRestart();
        jitterAndSeeking();
        contextToggleAndOverlap();
        System.out.println("PASS: Sky duplicate refresh, clock jitter/seeks, context toggle and projected overlap");
    }

    private static List<LyricsFetcher.LyricLine> lyrics() {
        return List.of(
                new LyricsFetcher.LyricLine(0, "First", "Translation one"),
                new LyricsFetcher.LyricLine(5, "Second", "Translation two"),
                new LyricsFetcher.LyricLine(10, "Third", "Translation three"));
    }

    private static void equivalentListRefreshDoesNotRestart() {
        List<LyricsFetcher.LyricLine> active = null;
        int activeIndex = -1, skySpawns = 0, oldSpawns = 0;
        for (int frame = 0; frame < 300; frame++) {
            // Reproduce a producer supplying a new-but-identical list every frame.
            List<LyricsFetcher.LyricLine> copy = new ArrayList<>(lyrics());
            boolean identityChanged = copy != active;
            if (identityChanged) {
                if (!WorldLyrics.equivalentSkyLyrics(active, copy)) activeIndex = -1;
                active = copy;
                oldSpawns++; // the old identity-only renderer reset activeIndex every frame
            }
            int candidate = LyricsFetcher.currentIndex(copy, 1.0F);
            if (candidate != activeIndex) skySpawns++;
            activeIndex = candidate;
        }
        assert oldSpawns == 300 && skySpawns == 1 : "equivalent refresh must not respawn/echo the same line";
        List<LyricsFetcher.LyricLine> changed = new ArrayList<>(lyrics());
        changed.set(0, new LyricsFetcher.LyricLine(0, "Different", "Translation one"));
        assert !WorldLyrics.equivalentSkyLyrics(active, changed) : "changed lyric content must refresh";
        assert !WorldLyrics.equivalentSkyLyrics(active, List.of()) : "empty/track reset must not be suppressed";
        assert !WorldLyrics.equivalentSkyLyrics(null, active);
        changed = new ArrayList<>(lyrics());
        changed.set(0, new LyricsFetcher.LyricLine(0, "First", "New translation"));
        assert !WorldLyrics.equivalentSkyLyrics(active, changed) : "translation changes must refresh";
        changed = new ArrayList<>(lyrics());
        changed.set(0, new LyricsFetcher.LyricLine(0.2F, "First", "Translation one"));
        assert !WorldLyrics.equivalentSkyLyrics(active, changed) : "timing changes must refresh";
    }

    private static void jitterAndSeeking() {
        List<LyricsFetcher.LyricLine> lines = lyrics();
        int active = 1;
        for (int n = 0; n < 100; n++) {
            float pos = (n & 1) == 0 ? 4.98F : 5.02F;
            int candidate = LyricsFetcher.currentIndex(lines, pos);
            int stable = WorldLyrics.stabilizeSkyIndex(lines, active, candidate, pos);
            assert stable == 1 : "small clock corrections must not create repeated line transitions";
            active = stable;
        }
        int back = WorldLyrics.stabilizeSkyIndex(lines, active, LyricsFetcher.currentIndex(lines, 4.70F), 4.70F);
        assert back == 0 : "a real backward seek must change the lyric";
        int forward = WorldLyrics.stabilizeSkyIndex(lines, back, LyricsFetcher.currentIndex(lines, 10.1F), 10.1F);
        assert forward == 2 : "forward seek must change immediately";
        assert WorldLyrics.stabilizeSkyIndex(lines, -1, 0, 0.1F) == 0;
        assert WorldLyrics.stabilizeSkyIndex(lines, 99, 0, 0.1F) == 0;
        List<LyricsFetcher.LyricLine> unsynced = List.of(new LyricsFetcher.LyricLine(-1, "Unsynced"));
        assert WorldLyrics.stabilizeSkyIndex(unsynced, 0, -1, -1) == -1;
    }

    private static void contextToggleAndOverlap() {
        assert WorldLyrics.skyEchoFinished(false, 1.0F) : "context OFF hides previous echoes";
        assert !WorldLyrics.skyEchoFinished(true, 1.0F) : "context ON retains normal fading echoes";
        assert WorldLyrics.skyEchoFinished(true, 0.01F) : "expired echoes are removed";

        SkyContextLayout.View view = new SkyContextLayout.View(Vec3.ZERO,
                new Vec3(0, 0, 1), new Vec3(1, 0, 0), new Vec3(0, 1, 0));
        Vec3 right = new Vec3(1, 0, 0), up = new Vec3(0, 1, 0);
        SkyContextLayout.Box active = SkyContextLayout.project(view, new Vec3(0, 0, 20), right, up, 5, 2, -3);
        SkyContextLayout.Box overlap = SkyContextLayout.project(view, new Vec3(0, 0, 40), right, up, 8, 3, -4);
        SkyContextLayout.Box separate = SkyContextLayout.project(view, new Vec3(30, 0, 40), right, up, 3, 2, -2);
        SkyContextLayout.Box behind = SkyContextLayout.project(view, new Vec3(0, 0, -20), right, up, 3, 2, -2);
        assert SkyContextLayout.visibility(active, overlap) == 0.0F : "preview must not obscure the current lyric";
        assert SkyContextLayout.visibility(active, separate) > 0.99F : "separate context remains visible";
        assert SkyContextLayout.visibility(active, behind) == 1.0F : "behind-camera context does not overlap";
        SkyContextLayout.Box near = new SkyContextLayout.Box(active.right() + 0.035, active.right() + 0.1, -0.1, 0.1);
        float fade = SkyContextLayout.visibility(active, near);
        assert fade > 0.1F && fade < 0.9F : "transition is softly faded, not abruptly toggled";
        SkyContextLayout.Box insideView = new SkyContextLayout.Box(-.2, .2, -.1, .1);
        SkyContextLayout.Box clipped = new SkyContextLayout.Box(-.2, .2, -.1, 1.0);
        SkyContextLayout.Box nearEdge = new SkyContextLayout.Box(-.2, .2, -.1, .63);
        assert SkyContextLayout.viewportVisibility(insideView, 70, 16.0 / 9.0) > .99F;
        assert SkyContextLayout.viewportVisibility(clipped, 70, 16.0 / 9.0) == 0.0F;
        float edgeFade = SkyContextLayout.viewportVisibility(nearEdge, 70, 16.0 / 9.0);
        assert edgeFade > 0 && edgeFade < 1 : "clipped context fades before it reaches the view edge";
        assert SkyContextLayout.viewportVisibility(null, 70, 1) == 0.0F;
    }

    private SkyStabilityCheck() {}
}
