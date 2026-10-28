package com.lt5lyrics.client.island;

/** Shared draw/hit-test geometry for the expanded island's temporary timing controls. */
final class QuickTimingLayout {
    static final float WIDTH = 168.0F;
    private static final float INSET = 10.0F;
    private static final float GAP = 3.0F;
    private static final float BUTTON_W = (WIDTH - INSET * 2.0F - GAP * 4.0F) / 5.0F;
    private static final float BUTTON_TOP = 7.0F;

    private QuickTimingLayout() {}

    /** Preserve the old lyric-list height where there is room; keep controls on-screen when short. */
    static float expandedHeight(boolean hasLyrics, float screenHeight) {
        float preferred = hasLyrics ? 153.0F : 105.0F;
        return Math.min(preferred, Math.max(1.0F, screenHeight - 4.0F));
    }

    static float top(float height) {
        // Compact windows omit the progress strip. Leave a few pixels after the
        // picture because text glyphs can extend beyond their nominal origin.
        return Math.max(44.0F, height - 49.0F);
    }

    static float lyricHeight(float height) {
        return Math.max(0.0F, top(height) - 60.0F);
    }

    static boolean showProgress(float height) {
        return top(height) >= 54.0F;
    }

    static float buttonHeight(float height) {
        // Keep a gap before the playback row even at very small GUI sizes.
        return Math.max(0.0F, Math.min(11.0F, height - 26.0F - top(height) - BUTTON_TOP));
    }

    static float[] button(float x, float y, float height, int index) {
        if (index < 0 || index >= 5) throw new IllegalArgumentException("button index");
        return new float[] {x + INSET + index * (BUTTON_W + GAP), y + top(height) + BUTTON_TOP,
            BUTTON_W, buttonHeight(height)};
    }

    static int hitButton(float x, float y, float height, double mouseX, double mouseY) {
        for (int i = 0; i < 5; i++) {
            float[] b = button(x, y, height, i);
            if (b[3] > 0 && mouseX >= b[0] && mouseX < b[0] + b[2]
                    && mouseY >= b[1] && mouseY < b[1] + b[3]) return i;
        }
        return -1;
    }

    static boolean inArea(float x, float y, float height, double mouseX, double mouseY) {
        float top = y + top(height);
        return mouseX >= x + INSET && mouseX <= x + WIDTH - INSET
            && mouseY >= top && mouseY <= top + BUTTON_TOP + buttonHeight(height);
    }
}
