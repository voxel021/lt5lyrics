package com.lt5lyrics.client.island;

import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** Deterministic regression checks for the single-slot, horizontal-only Sky follow. */
final class SkyFollowCheck {
    static void run() throws Exception {
        horizontalYawAndFixedHeight();
        smoothAndBounded();
        sameSpotForEveryLyric();
        boundaryAndWidth();
        wrappedGroupFitsView();
        settingsAndIsolation();
        System.out.println("PASS: Sky single centered slot, yaw-only follow, fixed Y, wrapped-group fit, smoothing, boundary, settings and Normal isolation");
    }

    private static void horizontalYawAndFixedHeight() {
        Vec3 player = new Vec3(1432, 64, -810), camera = player.add(0, 1.62, 0);
        double y = 118;
        for (float yaw : new float[] { -180, -90, -30, 0, 30, 90, 180 }) {
            Quaternionf rot = SkyStableLayout.cameraYaw(yaw);
            Vec3 forward = SkyFollowLayout.rotate(rot, new Vec3(0, 0, 1));
            double a = Math.toRadians(yaw);
            assert forward.distanceTo(new Vec3(-Math.sin(a), 0, Math.cos(a))) < 2e-6;
            Vec3 anchor = SkyStableLayout.followSlot(camera, player, rot, 100, y, 360);
            assert Math.abs(anchor.y - y) < 1e-9 : "camera pitch/yaw must not change Sky Y";
            Vec3 horizontal = new Vec3(anchor.x - camera.x, 0, anchor.z - camera.z).normalize();
            assert horizontal.dot(forward) > .99999 : "slot must be centered left/right in camera direction";
            // Even a changed camera/player altitude does not move the world Y while inside the boundary.
            Vec3 liftedPlayer = player.add(0, 20, 0), liftedCamera = camera.add(0, 20, 0);
            Vec3 lifted = SkyStableLayout.followSlot(liftedCamera, liftedPlayer, rot, 100, y, 360);
            assert Math.abs(lifted.y - y) < 1e-9;
            assert WorldLyrics.isInsideSkyBoundary(lifted, liftedPlayer, 360);
        }
        // A distant third-person camera uses its real horizontal position and yaw.
        Vec3 thirdPerson = camera.add(3, 4, -4);
        Vec3 third = SkyStableLayout.followSlot(thirdPerson, player, SkyStableLayout.cameraYaw(90), 120, y, 360);
        assert Math.abs(third.x - (thirdPerson.x - 120)) < .001;
        assert Math.abs(third.z - thirdPerson.z) < .001 && third.y == y;
    }

    private static double angle(Quaternionf a, Quaternionf b) {
        double dot = Math.clamp(Math.abs(new Quaternionf(a).normalize().dot(new Quaternionf(b).normalize())), 0.0, 1.0);
        return 2 * Math.acos(dot);
    }

    private static void smoothAndBounded() {
        Quaternionf current = SkyStableLayout.cameraYaw(0), target = SkyStableLayout.cameraYaw(180);
        double previous = angle(current, target);
        for (int i = 0; i < 120; i++) {
            Quaternionf next = SkyFollowLayout.smoothRotation(current, target, 1.0F / 60);
            assert angle(current, next) <= SkyFollowLayout.MAX_TURN_RATE / 60 + .002;
            double error = angle(next, target);
            assert error <= previous + .001 : "yaw overshot";
            Vec3 forward = SkyFollowLayout.rotate(next, new Vec3(0, 0, 1));
            assert Math.abs(forward.y) < 1e-6 : "smoothing cannot introduce pitch";
            current = next; previous = error;
        }
        assert previous < .003;
        Quaternionf at60 = SkyStableLayout.cameraYaw(0), at120 = SkyStableLayout.cameraYaw(0);
        Quaternionf small = SkyStableLayout.cameraYaw(50);
        for (int i = 0; i < 30; i++) at60 = SkyFollowLayout.smoothRotation(at60, small, 1.0F / 60);
        for (int i = 0; i < 60; i++) at120 = SkyFollowLayout.smoothRotation(at120, small, 1.0F / 120);
        assert angle(at60, at120) < .003 : "smoothing depends on frame rate";
        assert SkyStableLayout.easeDistance(100, 200, .016F) > 100;
        assert SkyStableLayout.easeDistance(100, 200, .016F) < 200;
    }

    private static void sameSpotForEveryLyric() {
        Vec3 player = new Vec3(100, 70, -90);
        WorldLyrics.Placed first = WorldLyrics.spawnSkyHorizonNode("First line", 60, player, 360, .8, 0, 54, .5F);
        for (int seq = 1; seq < 200; seq++) {
            WorldLyrics.Placed next = WorldLyrics.spawnSkyHorizonNode("Different words", 100, player, 360, .8, seq, 54, .5F);
            assert first.anchor.distanceTo(next.anchor) < 1e-8 : "every sequence shares a slot";
            assert first.right.distanceTo(next.right) < 1e-8;
        }
        long originalTime = first.spawnedAtNanos;
        WorldLyrics.Placed withTranslation = first.withTextAndTranslation("First line", "Translation", 80);
        assert withTranslation.anchor.equals(first.anchor);
        assert first.spawnedAtNanos == originalTime;
        assert WorldLyrics.computeSkyScale(.6F, 100, 80) == WorldLyrics.computeSkyScale(.6F, 100, 1000);
    }

    private static void boundaryAndWidth() {
        Vec3 player = new Vec3(0, 64, 0), eye = player.add(0, 1.62, 0);
        for (double radius : new double[] { 80, 360, 1000 }) {
            for (float yaw : new float[] { -180, -90, 0, 90, 180 }) {
                Quaternionf rot = SkyStableLayout.cameraYaw(yaw);
                Vec3 anchor = SkyStableLayout.followSlot(eye, player, rot,
                        SkyStableLayout.slotDistance(radius), 118, radius);
                assert WorldLyrics.isInsideSkyBoundary(anchor, player, radius);
                Vec3 right = SkyFollowLayout.rotate(rot, new Vec3(-1, 0, 0)).normalize();
                SkyFollowLayout.Basis basis = new SkyFollowLayout.Basis(right, new Vec3(0, 1, 0));
                for (int x : new int[] { -150, -30, 0, 30, 150 }) {
                    for (int yy : new int[] { -50, 0, 50 }) {
                        Vec3 vertex = SkyFollowLayout.vertex(anchor, eye, player, radius, x, yy, 1, 150, basis);
                        assert WorldLyrics.isInsideSkyBoundary(vertex, player, radius);
                    }
                }
            }
        }
        Vec3 fixed = SkyStableLayout.initialSlot(player, 360, .2, 54);
        Vec3 rescaled = SkyStableLayout.rescaleHorizontal(fixed, player, 360, 500, fixed.y);
        assert Math.abs(rescaled.y - fixed.y) < 1e-9 : "distance slider must not change Y";
        assert WorldLyrics.isInsideSkyBoundary(rescaled, player, 500);
        float narrow = SkyStableLayout.wrapWidth(50, 70, 1, 1, 10);
        float wide = SkyStableLayout.wrapWidth(250, 70, 16.0 / 9.0, 1, 10);
        assert narrow < wide && wide <= 250;
        assert SkyStableLayout.wrapWidth(50, 50, 1, 1, 10) < narrow;
        assert SkyStableLayout.wrapWidth(50, 70, 1, 2, 10) < narrow;
    }

    private static void wrappedGroupFitsView() {
        // Reproduces a visually overflowing five-row main lyric plus three translations.
        double distance = 86.4, dy = 28.38, fov = 70.0;
        float desired = 1.46F, halfWidth = 48.31F, halfHeight = 44.43F, padding = 3.5F;
        float fitted = SkyStableLayout.fitScale(distance, dy, fov, 16.0 / 9.0,
                halfWidth, halfHeight, padding, desired, false);
        assert fitted < desired && fitted > 0.1F : "multi-row group should shrink, not disappear";
        double centerAngle = Math.atan2(dy, distance), maxAngle = Math.toRadians(fov * .5 * .78);
        double topAngle = Math.atan2(dy + (halfHeight + padding) * fitted, distance);
        double bottomAngle = Math.atan2(dy - (halfHeight + padding) * fitted, distance);
        assert topAngle - centerAngle <= maxAngle + 1e-6;
        assert centerAngle - bottomAngle <= maxAngle + 1e-6;
        float reserve = SkyStableLayout.fitScale(distance, dy, fov, 16.0 / 9.0,
                halfWidth, halfHeight, padding, desired, true);
        assert reserve < fitted : "context needs room above and below";
        float shortLine = SkyStableLayout.fitScale(distance, dy, fov, 16.0 / 9.0,
                10, 5, padding, desired, false);
        assert shortLine == desired : "short lines keep the preferred size";
        float longCluster = SkyStableLayout.fitScale(distance, dy, fov, 1.0,
                1000, 5, padding, desired, false);
        assert longCluster > 0 && longCluster < desired : "unsplittable wide scripts also fit";
        float fallback = SkyStableLayout.fitScale(Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                Float.NaN, Float.NaN, Float.NaN, Float.NaN, false);
        assert Float.isFinite(fallback) && fallback > 0;
    }

    private static void settingsAndIsolation() throws Exception {
        Path config = Files.createTempFile("lt5-sky-follow-", ".json");
        try {
            IslandSettings.resetSkyDefaults();
            assert !IslandSettings.skyCrosshairFollow;
            IslandSettings.skyCrosshairFollow = true;
            IslandSettings.saveToFile(config);
            assert Files.readString(config).contains("\"skyCrosshairFollow\": true");
            IslandSettings.skyCrosshairFollow = false;
            IslandSettings.loadFromFile(config);
            assert IslandSettings.skyCrosshairFollow;
            IslandSettings.skyCrosshairFollow = false;
            IslandSettings.saveToFile(config);
            IslandSettings.skyCrosshairFollow = true;
            IslandSettings.loadFromFile(config);
            assert !IslandSettings.skyCrosshairFollow;
            Files.writeString(config, "{}"); IslandSettings.skyCrosshairFollow = true;
            IslandSettings.loadFromFile(config); assert !IslandSettings.skyCrosshairFollow;
            Files.writeString(config, "{\"skyCrosshairFollow\": []}"); IslandSettings.skyCrosshairFollow = true;
            IslandSettings.loadFromFile(config); assert !IslandSettings.skyCrosshairFollow;
            String world = Files.readString(Path.of("src/client/java/com/lt5lyrics/client/island/WorldLyrics.java"));
            String normal = world.substring(world.indexOf("private static void drawHologram("), world.indexOf("Continuous rounded plate"));
            assert !normal.contains("skyCrosshairFollow") && !normal.contains("SkyStableLayout");
            String ui = Files.readString(Path.of("src/client/java/com/lt5lyrics/client/island/SettingsPanel.java"));
            assert ui.contains("Follow Look Direction") && ui.contains("left/right look only");
            assert ui.contains("Show Song Header") && ui.contains("skySongHeader = !IslandSettings.skySongHeader");
            assert !ui.contains("Sky Drift Speed") : "do not offer a control for removed scattering";
        } finally { Files.deleteIfExists(config); IslandSettings.resetAllSettingsToFactory(); }
    }
    private SkyFollowCheck() {}
}
