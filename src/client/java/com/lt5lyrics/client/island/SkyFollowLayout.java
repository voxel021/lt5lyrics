package com.lt5lyrics.client.island;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Sky-only yaw smoothing and curved world-space glyph geometry. */
final class SkyFollowLayout {
    static final double MAX_TURN_RATE = Math.toRadians(360.0);
    static final double ROTATION_RESPONSE = 8.0;

    record Basis(Vec3 right, Vec3 up) {}

    static Quaternionf yawRotation(double yawRadians) {
        return new Quaternionf().rotationY((float) yawRadians);
    }

    /** Full camera basis is used only to project optional-context overlap, not to move the slot. */
    static Quaternionf cameraFieldRotation(Quaternionf cameraRotation) {
        return new Quaternionf(cameraRotation).mul(new Quaternionf().rotationY((float) Math.PI)).normalize();
    }

    /** Shortest-path, frame-rate-independent yaw with an upper angular-speed bound. */
    static Quaternionf smoothRotation(Quaternionf current, Quaternionf target, float dt) {
        Quaternionf from = new Quaternionf(current).normalize();
        Quaternionf to = new Quaternionf(target).normalize();
        double dot = Math.clamp(Math.abs(from.dot(to)), 0.0, 1.0);
        double angle = 2.0 * Math.acos(dot);
        double seconds = Math.clamp((double) dt, 0.0, 0.05);
        if (angle < 1.0E-7 || seconds <= 0.0) return from;
        double exponential = 1.0 - Math.exp(-ROTATION_RESPONSE * seconds);
        double speedLimit = MAX_TURN_RATE * seconds / angle;
        return from.slerp(to, (float) Math.min(exponential, speedLimit)).normalize();
    }

    static Vec3 rotate(Quaternionf rotation, Vec3 vector) {
        Vector3f v = rotation.transform(new Vector3f((float) vector.x, (float) vector.y, (float) vector.z));
        return new Vec3(v.x, v.y, v.z);
    }

    /** Curved real-world glyph vertex, with the center fixed at the slot and every vertex bounded. */
    static Vec3 vertex(Vec3 anchor, Vec3 cameraPosition, Vec3 playerCenter, double boundaryRadius,
            float offsetX, float offsetY, float curveFactor, float lineHalfWidth, Basis basis) {
        float curve = Float.isFinite(curveFactor)
                ? Math.clamp(curveFactor, IslandSettings.MIN_SKY_CURVE, IslandSettings.MAX_SKY_CURVE)
                : IslandSettings.DEFAULT_SKY_CURVE;
        double along = offsetX, inward = 0.0;
        if (curve > 1.0E-4F) {
            // Prevent an exceptionally wide row from folding its edge behind the viewer.
            double arcRadius = Math.max(Math.max(4.0, anchor.distanceTo(cameraPosition)) / curve,
                    Math.max(0.0, lineHalfWidth) / 1.05);
            double a = offsetX / arcRadius;
            along = arcRadius * Math.sin(a);
            inward = arcRadius * (1.0 - Math.cos(a));
        }
        Vec3 towardCamera = basis.right().cross(basis.up()).normalize();
        Vec3 p = anchor.add(basis.right().scale(along)).add(basis.up().scale(offsetY))
                .add(towardCamera.scale(inward));
        return WorldLyrics.clampPointToSkyBoundary(p.x, p.y, p.z, playerCenter, boundaryRadius);
    }

    private SkyFollowLayout() {}
}
