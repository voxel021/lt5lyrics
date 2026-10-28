package com.lt5lyrics.client.island;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** Sky-only, one-slot placement. The slot has a fixed world Y except boundary/height changes. */
final class SkyStableLayout {
    static final double DEPTH_FRACTION = 0.72;
    static final double DISTANCE_RESPONSE = 5.0;

    static double slotDistance(double radius) {
        double r = WorldLyrics.clampSkyBoundaryRadius(radius);
        return Math.clamp(r * DEPTH_FRACTION, 28.0, r * 0.90);
    }

    static Vec3 initialSlot(Vec3 player, double radius, double angle, double height) {
        double d = slotDistance(radius);
        double dy = WorldLyrics.effectiveSkyHeight(height, radius, 0.0);
        return WorldLyrics.clampPointToSkyBoundary(player.x + Math.sin(angle) * d,
                player.y + dy, player.z + Math.cos(angle) * d, player, radius);
    }

    /** Camera yaw in Minecraft convention; pitch and roll are intentionally excluded. */
    static Quaternionf cameraYaw(float yawDegrees) {
        double yaw = Double.isFinite(yawDegrees) ? yawDegrees : 0.0;
        return SkyFollowLayout.yawRotation(-Math.toRadians(yaw));
    }

    static Vec3 followSlot(Vec3 camera, Vec3 player, Quaternionf yaw, double distance,
            double absoluteY, double radius) {
        Vec3 forward = SkyFollowLayout.rotate(yaw, new Vec3(0, 0, 1));
        return WorldLyrics.clampPointToSkyBoundary(camera.x + forward.x * distance,
                absoluteY, camera.z + forward.z * distance, player, radius);
    }

    static Vec3 rescaleHorizontal(Vec3 anchor, Vec3 player, double oldRadius, double newRadius,
            double absoluteY) {
        if (anchor == null) return null;
        double factor = oldRadius > 1.0 ? newRadius / oldRadius : 1.0;
        return WorldLyrics.clampPointToSkyBoundary(player.x + (anchor.x - player.x) * factor,
                absoluteY, player.z + (anchor.z - player.z) * factor, player, newRadius);
    }

    static double easeDistance(double value, double target, float dt) {
        if (!Double.isFinite(value) || value <= 0) return target;
        double amount = 1.0 - Math.exp(-DISTANCE_RESPONSE * Math.clamp((double) dt, 0.0, 0.05));
        return value + (target - value) * amount;
    }

    /** Width in font units, constrained by FOV and a readable maximum line length. */
    static float wrapWidth(double horizontalDistance, double verticalFovDegrees, double aspect,
            float worldTextScale, float fontSize) {
        double distance = Math.max(1.0, Double.isFinite(horizontalDistance) ? horizontalDistance : 1.0);
        double fov = Math.clamp(Double.isFinite(verticalFovDegrees) ? verticalFovDegrees : 70.0, 30.0, 110.0);
        double safeAspect = Math.clamp(Double.isFinite(aspect) ? aspect : 1.0, 0.35, 4.0);
        double scale = Math.max(0.01, Float.isFinite(worldTextScale) ? worldTextScale : 1.0);
        double available = 2.0 * distance * Math.tan(Math.toRadians(fov * 0.5)) * safeAspect * 0.67 / scale;
        double naturalMax = Math.max(24.0, fontSize * 25.0);
        // Quantize to avoid visible rewrapping from tiny camera-position jitters.
        double width = Math.max(fontSize * 1.5, Math.min(naturalMax, available));
        return (float) (Math.floor(width / 2.0) * 2.0);
    }

    /**
     * Fit an entire wrapped group into a conservative field around its anchor, assuming
     * the player looks toward that anchor. This changes size only, never its world Y.
     * Keeping the camera pitch out of the calculation also avoids size changes on looking up/down.
     */
    static float fitScale(double horizontalDistance, double verticalOffset, double verticalFovDegrees,
            double aspect, float halfWidth, float halfHeight, float padding, float requestedScale,
            boolean reserveContext) {
        double distance = Math.max(1.0, Double.isFinite(horizontalDistance) ? horizontalDistance : 1.0);
        double dy = Double.isFinite(verticalOffset) ? verticalOffset : 0.0;
        double fov = Math.clamp(Double.isFinite(verticalFovDegrees) ? verticalFovDegrees : 70.0, 30.0, 110.0);
        double safeAspect = Math.clamp(Double.isFinite(aspect) ? aspect : 1.0, 0.35, 4.0);
        double width = Math.max(0.01, Float.isFinite(halfWidth) ? halfWidth : 0.01);
        double height = Math.max(0.01, Float.isFinite(halfHeight) ? halfHeight : 0.01);
        double margin = Math.max(0.0, Float.isFinite(padding) ? padding : 0.0);
        double desired = Math.max(0.001, Float.isFinite(requestedScale) ? requestedScale : 1.0);
        double centerAngle = Math.atan2(dy, distance);
        double halfAngle = Math.toRadians(fov * 0.5) * (reserveContext ? 0.60 : 0.78);
        double topAngle = centerAngle + halfAngle, bottomAngle = centerAngle - halfAngle;
        double maxUp = topAngle >= Math.PI / 2 ? Double.POSITIVE_INFINITY
                : distance * Math.tan(topAngle) - dy;
        double maxDown = bottomAngle <= -Math.PI / 2 ? Double.POSITIVE_INFINITY
                : dy - distance * Math.tan(bottomAngle);
        double verticalScale = Math.max(0.001, Math.min(maxUp, maxDown) / (height + margin));
        double halfScreenWidth = distance * Math.tan(Math.toRadians(fov * 0.5)) * safeAspect * 0.67;
        double horizontalScale = Math.max(0.001, halfScreenWidth / (width + margin));
        return (float) Math.min(desired, Math.min(verticalScale, horizontalScale));
    }

    private SkyStableLayout() {}
}
