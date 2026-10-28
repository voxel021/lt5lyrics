package com.lt5lyrics.client.island;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** Headless, deterministic position/orientation/lifetime regression checks. */
final class NormalFollowAndFacingCheck {
    private static WorldLyrics.Placed node(Vec3 position) {
        WorldLyrics.Placed p = new WorldLyrics.Placed("One two three four five six", "Translated row", position,
            new Vec3(-1, 0, 0), new Vec3(0, 1, 0), .035F, null, 80F);
        p.normalManaged = true;
        p.normalForwardDistance = 6;
        p.normalSignedOffset = -2.5;
        p.normalHeight = .45;
        return p;
    }

    static void run() {
        fixedAnchorsAndRotation();
        constantSpeedAndVerticalMovement();
        turnWhileBehindAndResumeFromFixed();
        frameRateAndSidePreservation();
        lastLineDuration();
        System.out.println("PASS: fixed 3D anchors, 360-degree billboard basis, fast/vertical Follow, behind-camera recovery, side/distance, frame rate and final-line end");
    }

    private static void fixedAnchorsAndRotation() {
        Vec3 spawn = new Vec3(11.5, 72.3, -25.25);
        WorldLyrics.Placed p = node(spawn);
        long started = p.spawnedAtNanos;
        for (int degree = 0; degree <= 720; degree += 2) {
            double a = Math.toRadians(degree);
            Vec3 camera = spawn.add(Math.sin(a) * 8, 1.5 + Math.sin(a * 2) * 2, Math.cos(a) * 8);
            Vec3 view = spawn.subtract(camera).normalize();
            Vec3 target = WorldLyrics.computeNormalAnchor(camera, view, degree, 6, -2.5, .45);
            WorldLyrics.advanceNormalFollow(p, false, camera, target, 1F / 60F);
            assert p.anchor.equals(spawn) && p.normalFollowEye == null : "fixed anchors never translate or interpolate";
            assert WorldLyrics.normalGroupOrigin(p, -2.0F, false).equals(spawn);
            assert WorldLyrics.normalGroupOrigin(p, 5.0F, false).equals(spawn) : "no group lift in fixed mode";
            Vec3 toCam = camera.subtract(spawn);
            for (var mode : IslandSettings.HologramBillboard.values()) {
                Quaternionf rotation = new Quaternionf().rotateY((float) -a);
                Vec3[] basis = WorldLyrics.computeHologramBasis(p, rotation, camera, mode);
                assert Math.abs(basis[0].length() - 1) < 1e-5 && Math.abs(basis[1].length() - 1) < 1e-5;
                assert Math.abs(basis[0].dot(basis[1])) < 1e-5 : mode;
                float flip = WorldLyrics.textFlip(basis[0], basis[1], toCam);
                assert toCam.dot(basis[0].cross(basis[1]).scale(flip)) >= -1e-8 : "front side toward viewer";
                assert basis[1].y >= -1e-5 : "text is not upside down";
                if (mode != IslandSettings.HologramBillboard.CENTER) {
                    assert flip == 1 : "natural/upright text is not mirrored";
                    assert basis[0].cross(basis[1]).dot(toCam) > 0;
                }
                if (mode == IslandSettings.HologramBillboard.SMART_BTP) {
                    assert basis[0].cross(basis[1]).dot(toCam.normalize()) > .999999 : "natural faces camera position";
                }
                if (mode == IslandSettings.HologramBillboard.VERTICAL) assert basis[1].equals(new Vec3(0,1,0));
                assert p.anchor.equals(spawn) && p.spawnedAtNanos == started : "rotation does not recreate or re-anchor the group";
            }
        }
        for (double dy : new double[] {-5, 0, 5}) {
            Vec3[] pole = WorldLyrics.computeHologramBasis(p, null, spawn.add(0, dy, 0), IslandSettings.HologramBillboard.SMART_BTP);
            assert Double.isFinite(pole[0].x) && Double.isFinite(pole[1].z);
            assert Math.abs(pole[0].dot(pole[1])) < 1e-5;
        }
    }

    private static void constantSpeedAndVerticalMovement() {
        Vec3 view = new Vec3(0, 0, 1), eye = new Vec3(10, 70, -400);
        for (double distance : new double[] {2, 6, 24}) for (double side : new double[] {-9, -1.25, 1.25, 9}) {
            Vec3 expected = WorldLyrics.computeNormalAnchor(eye, view, 0, distance, side, .45);
            WorldLyrics.Placed p = node(expected);
            p.normalForwardDistance = distance; p.normalSignedOffset = side;
            WorldLyrics.advanceNormalFollow(p, true, eye, expected, 1F/60F);
            Vec3 last = p.anchor;
            for (int frame = 0; frame < 600; frame++) {
                // 40+ blocks/second is well beyond sprinting; vertical movement varies too.
                Vec3 delta = new Vec3(.52, (frame % 30 < 15 ? .09 : -.09), .75);
                eye = eye.add(delta);
                expected = WorldLyrics.computeNormalAnchor(eye, view, 0, distance, side, .45);
                WorldLyrics.advanceNormalFollow(p, true, eye, expected, 1F / 60F);
                assert p.anchor.distanceTo(expected) < .00005 : "no accumulating translation lag " + p.anchor + " vs " + expected;
                assert p.anchor.subtract(last).distanceTo(delta) < .00005 : "continuous transport";
                last = p.anchor;
            }
        }
    }

    private static void turnWhileBehindAndResumeFromFixed() {
        Vec3 eye = new Vec3(0, 65, 0), forward = new Vec3(0,0,1), backward = new Vec3(0,0,-1);
        Vec3 first = WorldLyrics.computeNormalAnchor(eye, forward, 0, 6, -2.5, .45);
        WorldLyrics.Placed p = node(first);
        long start = p.spawnedAtNanos;
        WorldLyrics.advanceNormalFollow(p, true, eye, first, 1F/60F);
        Vec3 target = WorldLyrics.computeNormalAnchor(eye, backward, 180, 6, -2.5, .45);
        assert !WorldLyrics.normalDirectionVisible(true, eye, backward, p.anchor) : "reproduce the old stale-position guard";
        Vec3 prior = p.anchor;
        for (int i = 0; i < 180; i++) {
            WorldLyrics.advanceNormalFollow(p, true, eye, target, 1F/60F);
            assert p.anchor.distanceTo(prior) < 4.0 : "no one-frame teleport on turn";
            assert p.anchor.subtract(eye).length() > 4.0 : "do not cut through the player";
            assert Math.abs(p.anchor.y - first.y) < 1e-8 : "a flat 180-degree turn must not send the lyric overhead";
            prior = p.anchor;
        }
        assert p.anchor.distanceTo(target) < .001 : "same lyric recovers without another timestamp";
        assert p.spawnedAtNanos == start;

        WorldLyrics.Placed lowFps = node(first);
        WorldLyrics.advanceNormalFollow(lowFps, true, eye, first, .2F);
        Vec3 last = lowFps.anchor;
        for (int i=0; i<20; i++) {
            WorldLyrics.advanceNormalFollow(lowFps, true, eye, target, .2F);
            Vec3 oldRel = last.subtract(eye), newRel = lowFps.anchor.subtract(eye);
            double oldAngle = Math.atan2(oldRel.x, oldRel.z), newAngle = Math.atan2(newRel.x, newRel.z);
            double step = Math.abs(Math.atan2(Math.sin(newAngle-oldAngle), Math.cos(newAngle-oldAngle)));
            assert step <= Math.PI/4 + 1e-8 : "long frames do not snap the group";
            assert Math.abs(lowFps.anchor.y - first.y) < 1e-8;
            last = lowFps.anchor;
        }
        assert lowFps.anchor.distanceTo(target) < .001 : "low-frame-rate Follow catches up";

        WorldLyrics.advanceNormalFollow(p, false, null, null, 1F/60F);
        Vec3 frozen = p.anchor;
        for (int i=0; i<60; i++) {
            eye = eye.add(.5, .1, -.2);
            WorldLyrics.advanceNormalFollow(p, false, eye, target, 1F/60F);
            assert p.anchor.equals(frozen);
        }
        target = WorldLyrics.computeNormalAnchor(eye, backward, 180, 6, -2.5, .45);
        WorldLyrics.advanceNormalFollow(p, true, eye, target, 1F/60F);
        assert p.anchor.distanceTo(frozen) < frozen.distanceTo(target) * .5 : "enabling Follow eases, not a forced teleport";
        for (int i=0; i<240; i++) WorldLyrics.advanceNormalFollow(p, true, eye, target, 1F/60F);
        assert p.anchor.distanceTo(target) < .001;
    }

    private static void frameRateAndSidePreservation() {
        Vec3 eye = new Vec3(300, 80, -99);
        for (double signed : new double[] {-9, -1.25, 1.25, 9}) {
            Vec3 from = WorldLyrics.computeNormalAnchor(eye, new Vec3(0,0,1), 0, 10, signed, .45);
            Vec3 target = WorldLyrics.computeNormalAnchor(eye, new Vec3(1,0,0), -90, 10, signed, .45);
            WorldLyrics.Placed a = node(from), b = node(from);
            WorldLyrics.advanceNormalFollow(a, true, eye, from, 1F/60F);
            WorldLyrics.advanceNormalFollow(b, true, eye, from, 1F/120F);
            for (int i=0; i<60; i++) WorldLyrics.advanceNormalFollow(a, true, eye, target, 1F/60F);
            for (int i=0; i<120; i++) WorldLyrics.advanceNormalFollow(b, true, eye, target, 1F/120F);
            assert a.anchor.distanceTo(b.anchor) < .002 : "frame-rate independent";
            for (int i=0; i<180; i++) WorldLyrics.advanceNormalFollow(a, true, eye, target, 1F/60F);
            Vec3 view = new Vec3(1,0,0), right = WorldLyrics.normalHorizontalRight(view, -90);
            Vec3 delta = a.anchor.subtract(eye);
            assert Math.abs(delta.dot(view) - 10) < .001;
            assert Math.abs(delta.dot(right) - signed) < .001 : "configured side and minimum offset remain";
        }
    }

    private static void lastLineDuration() {
        assert !WorldLyrics.normalLastLineFinished(0, 2, 179.9F, 180);
        assert !WorldLyrics.normalLastLineFinished(0, 2, 180F, 180) : "only final line uses track end";
        assert !WorldLyrics.normalLastLineFinished(1, 2, 179.99F, 180);
        assert WorldLyrics.normalLastLineFinished(1, 2, 180F, 180);
        assert WorldLyrics.normalLastLineFinished(1, 2, 181F, 180);
        assert !WorldLyrics.normalLastLineFinished(1, 2, 10000F, 0) : "unknown duration is not guessed";
        assert !WorldLyrics.normalLastLineFinished(1, 2, Float.NaN, 180);
        assert !WorldLyrics.normalLastLineFinished(-1, 0, 180F, 180);
        assert !WorldLyrics.normalLastLineFinished(1, 2, 120F, 180) : "seek back revives timing";
    }
}
