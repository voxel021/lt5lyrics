package com.lt5lyrics.client.island;

import net.minecraft.world.phys.Vec3;

/** Perspective-space overlap test for optional Sky lines; it never moves or hides the active line. */
final class SkyContextLayout {
    record View(Vec3 eye, Vec3 forward, Vec3 right, Vec3 up) {}
    record Box(double left, double right, double bottom, double top) {}

    /**
     * Conservative projected box of an upright or camera-tilted lyric group. Coordinates are
     * tangent-of-view angles rather than screen pixels, so this is independent of window size
     * and field of view. A point behind the eye cannot overlap a visible group.
     */
    static Box project(View view, Vec3 anchor, Vec3 planeRight, Vec3 planeUp,
            double halfWidth, double top, double bottom) {
        if (view == null || anchor == null || planeRight == null || planeUp == null) return null;
        Vec3 center = anchor.subtract(view.eye());
        if (center.dot(view.forward()) <= 0.05) return null;
        double left = Double.POSITIVE_INFINITY, right = Double.NEGATIVE_INFINITY;
        double low = Double.POSITIVE_INFINITY, high = Double.NEGATIVE_INFINITY;
        // Including the center covers an arc whose edges have been clamped at the Sky boundary.
        for (int xi = -1; xi <= 1; xi++) {
            double x = xi * halfWidth;
            for (int yi = 0; yi < 2; yi++) {
                double y = yi == 0 ? bottom : top;
                Vec3 p = center.add(planeRight.scale(x)).add(planeUp.scale(y));
                // If a corner crosses the eye plane use a small positive depth: hiding an
                // optional line is safer than allowing it to obscure the current lyric.
                double depth = Math.max(0.05, p.dot(view.forward()));
                double sx = p.dot(view.right()) / depth;
                double sy = p.dot(view.up()) / depth;
                left = Math.min(left, sx); right = Math.max(right, sx);
                low = Math.min(low, sy); high = Math.max(high, sy);
            }
        }
        return new Box(left, right, low, high);
    }

    /** Fade optional context to zero if it touches the active group, with a soft safety margin. */
    static float visibility(Box active, Box optional) {
        if (active == null || optional == null) return 1.0F;
        double gapX = Math.max(optional.left() - active.right(), active.left() - optional.right());
        double gapY = Math.max(optional.bottom() - active.top(), active.bottom() - optional.top());
        double gap = Math.max(gapX, gapY);
        double t = Math.clamp(gap / 0.07, 0.0, 1.0);
        return (float) (t * t * (3.0 - 2.0 * t));
    }

    /** Hide a partially clipped optional row instead of leaving fragments around the active lyric. */
    static float viewportVisibility(Box optional, double verticalFovDegrees, double aspect) {
        if (optional == null) return 0.0F;
        double fov = Math.clamp(Double.isFinite(verticalFovDegrees) ? verticalFovDegrees : 70.0, 30.0, 110.0);
        double safeAspect = Math.clamp(Double.isFinite(aspect) ? aspect : 1.0, 0.35, 4.0);
        double halfY = Math.tan(Math.toRadians(fov * 0.5)) * 0.95;
        double halfX = halfY * safeAspect;
        double margin = Math.min(Math.min(optional.left() + halfX, halfX - optional.right()),
                Math.min(optional.bottom() + halfY, halfY - optional.top()));
        double fadeDistance = Math.max(0.001, halfY * 0.12);
        double t = Math.clamp(margin / fadeDistance, 0.0, 1.0);
        return (float) (t * t * (3.0 - 2.0 * t));
    }

    private SkyContextLayout() {}
}
