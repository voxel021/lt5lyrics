package com.lt5lyrics.client.island;

/** Pure, bounded layout math shared by the Extras & Tools screen and its checks. */
final class PlusLayout {
    static final int PAGE_COUNT = 9;

    record Bounds(float scale, int panelX, int panelY, int panelWidth, int panelHeight,
                  int contentX, int contentWidth, int tabY, int tabHeight, int tabStep,
                  int sectionY, int bodyTop, int bodyBottom, int footerY,
                  int step, int buttonHeight, int gap) {
        int bodyHeight() { return Math.max(0, bodyBottom - bodyTop); }
    }

    static Bounds forScreen(int width, int height, int guiScale) {
        width = Math.max(1, width);
        height = Math.max(1, height);
        float studioScale = LT5SettingsScreen.studioScaleFor(width, height, guiScale);
        // Preserve the Studio's normal scale, but shrink a little in very small
        // GUI viewports so at least one control can be scrolled into view.
        float fit = Math.min(width / 220.0F, height / 140.0F);
        float scale = Math.max(0.5F, Math.min(studioScale, fit));
        int marginX = unit(scale, 8), marginY = unit(scale, 6);
        int pw = Math.max(1, Math.min(unit(scale, 416), width - 2 * marginX));
        int ph = Math.max(1, Math.min(unit(scale, 336), height - 2 * marginY));
        int px = (width - pw) / 2, py = (height - ph) / 2;
        int pad = unit(scale, 8);
        int cx = px + pad, cw = Math.max(1, pw - 2 * pad);
        int tabs = py + unit(scale, 32);
        int tabHeight = Math.max(1, unit(scale, 12));
        int tabStep = tabHeight + Math.max(1, unit(scale, 3));
        int section = tabs + 2 * tabStep + unit(scale, 1);
        int bodyTop = section + unit(scale, 9);
        int footer = py + ph - unit(scale, 15);
        int bodyBottom = Math.max(bodyTop, footer - unit(scale, 3));
        int step = Math.max(12, unit(scale, 16));
        int bh = Math.max(8, step - unit(scale, 2));
        return new Bounds(scale, px, py, pw, ph, cx, cw, tabs, tabHeight, tabStep,
                section, bodyTop, bodyBottom, footer, step, bh, Math.max(1, unit(scale, 4)));
    }

    /** Content height includes helper text, not only buttons, so it can all be scrolled into view. */
    static int contentHeight(int page, Bounds b) {
        int s = b.step(), u = unit(b.scale(), 10), line = unit(b.scale(), 12);
        return switch (page) {
            case 0 -> 3 * s + u;                  // World
            case 1 -> 2 * s + 2 * line;           // Island
            case 2 -> 3 * s + 2 * u;              // Timing
            case 3 -> 8 * s + line;               // Lyrics: search/import plus source comparison
            case 4 -> 4 * s + line;               // Styles
            case 5 -> 4 * s + unit(b.scale(), 2); // Pins
            case 6 -> Math.max(5 * s, 5 * u);     // Status
            case 7 -> 2 * s + line;               // Hide
            case 8 -> 2 * s + 2 * line;           // Backup
            default -> 0;
        };
    }

    static int maxScroll(int page, Bounds b) {
        return Math.max(0, contentHeight(page, b) - b.bodyHeight());
    }

    static int clampScroll(int scroll, int maxScroll) {
        return Math.clamp(scroll, 0, Math.max(0, maxScroll));
    }

    static boolean fullyVisible(int top, int height, int viewTop, int viewBottom) {
        return height > 0 && top >= viewTop && top + height <= viewBottom;
    }

    static int unit(float scale, float amount) { return Math.max(1, Math.round(scale * amount)); }
    private PlusLayout() {}
}
