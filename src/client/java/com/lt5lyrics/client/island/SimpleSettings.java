package com.lt5lyrics.client.island;

/**
 * The beginner-facing choices are views of the existing settings, not a
 * second configuration. This model is independent of rendering and save IO.
 */
public final class SimpleSettings {
    public enum Size { SMALL, MEDIUM, LARGE, CUSTOM }
    public enum Distance { NEAR, MEDIUM, FAR, CUSTOM }
    public enum Background { OFF, SOFT, STRONG, CUSTOM }
    public enum Color {
        WHITE("White", 0xFFFFFFFF), BLUE("Blue", 0xFF8FC7E3),
        PINK("Pink", 0xFFFF91BF), GOLD("Gold", 0xFFFFD77A),
        CUSTOM("Custom", 0);

        public final String label;
        public final int rgb;
        Color(String label, int rgb) { this.label = label; this.rgb = rgb; }
    }

    private static final float EPSILON = 0.0001F;
    private SimpleSettings() {}
    private static boolean same(double a, double b) { return Math.abs(a - b) < EPSILON; }

    /** HUD text has no real size control; return CUSTOM and do not offer a selector. */
    public static Size size(IslandSettings.DisplayMode mode) {
        if (mode == IslandSettings.DisplayMode.HUD_ONLY) return Size.CUSTOM;
        float value = mode == IslandSettings.DisplayMode.SKY_LYRICS
                ? IslandSettings.skyTextSize : IslandSettings.normalTextSize;
        float small = mode == IslandSettings.DisplayMode.SKY_LYRICS ? .30F : .70F;
        float medium = mode == IslandSettings.DisplayMode.SKY_LYRICS
                ? IslandSettings.DEFAULT_SKY_TEXT_SIZE : IslandSettings.DEFAULT_NORMAL_SIZE;
        float large = mode == IslandSettings.DisplayMode.SKY_LYRICS ? .80F : 1.50F;
        if (same(value, small)) return Size.SMALL;
        if (same(value, medium)) return Size.MEDIUM;
        if (same(value, large)) return Size.LARGE;
        return Size.CUSTOM;
    }

    public static void setSize(IslandSettings.DisplayMode mode, Size choice) {
        if (mode == null || mode == IslandSettings.DisplayMode.HUD_ONLY || choice == null || choice == Size.CUSTOM) return;
        if (mode == IslandSettings.DisplayMode.SKY_LYRICS) {
            IslandSettings.skyTextSize = switch (choice) {
                case SMALL -> .30F;
                case MEDIUM -> IslandSettings.DEFAULT_SKY_TEXT_SIZE;
                case LARGE -> .80F;
                case CUSTOM -> IslandSettings.skyTextSize;
            };
        } else {
            IslandSettings.normalTextSize = switch (choice) {
                case SMALL -> .70F;
                case MEDIUM -> IslandSettings.DEFAULT_NORMAL_SIZE;
                case LARGE -> 1.50F;
                case CUSTOM -> IslandSettings.normalTextSize;
            };
        }
    }

    public static Distance distance() {
        double value = IslandSettings.skyBoundaryRadius;
        if (same(value, 120.0)) return Distance.NEAR;
        if (same(value, IslandSettings.DEFAULT_SKY_BOUNDARY_RADIUS)) return Distance.MEDIUM;
        if (same(value, 720.0)) return Distance.FAR;
        return Distance.CUSTOM;
    }

    public static void setDistance(Distance choice) {
        if (choice == null || choice == Distance.CUSTOM) return;
        IslandSettings.skyBoundaryRadius = switch (choice) {
            case NEAR -> 120.0;
            case MEDIUM -> IslandSettings.DEFAULT_SKY_BOUNDARY_RADIUS;
            case FAR -> 720.0;
            case CUSTOM -> IslandSettings.skyBoundaryRadius;
        };
    }

    /** RANDOM remains a supported Advanced value but is not a Simple preset. */
    public static String position() {
        return IslandSettings.normalSide == IslandSettings.NormalSide.RANDOM
                ? "Custom" : IslandSettings.normalSide.label;
    }

    public static Background background() {
        if (!IslandSettings.hologramBackground) return Background.OFF;
        if (same(IslandSettings.hologramBgOpacity, IslandSettings.DEFAULT_HOLOGRAM_BG_OPACITY)) return Background.SOFT;
        if (same(IslandSettings.hologramBgOpacity, .65F)) return Background.STRONG;
        return Background.CUSTOM;
    }

    public static void setBackground(Background choice) {
        if (choice == null || choice == Background.CUSTOM) return;
        if (choice == Background.OFF) {
            IslandSettings.hologramBackground = false; // Keep the previously chosen opacity.
        } else {
            IslandSettings.hologramBackground = true;
            IslandSettings.hologramBgOpacity = choice == Background.SOFT
                    ? IslandSettings.DEFAULT_HOLOGRAM_BG_OPACITY : .65F;
        }
    }

    /** Uses precisely the same fields that the active surface resolves. */
    public static int activeColor(IslandSettings.DisplayMode mode) {
        return mode == IslandSettings.DisplayMode.SKY_LYRICS && IslandSettings.separateSkyColors
                ? IslandSettings.resolveSkyPrimaryColor() : IslandSettings.resolveWorldPrimaryColor();
    }

    public static Color color(IslandSettings.DisplayMode mode) {
        int rgb = activeColor(mode);
        for (Color color : Color.values()) {
            if (color != Color.CUSTOM && color.rgb == rgb) return color;
        }
        return Color.CUSTOM;
    }

    public static void setColor(IslandSettings.DisplayMode mode, Color color) {
        if (mode == null || color == null || color == Color.CUSTOM) return;
        if (mode == IslandSettings.DisplayMode.SKY_LYRICS && IslandSettings.separateSkyColors) {
            IslandSettings.skyActiveColor = color.rgb;
        } else {
            IslandSettings.lyricPrimaryColor = color.rgb;
        }
    }

    /** Only fields exposed in Simple, including its conditional World/Sky pages. */
    public static void resetSimpleSettings() {
        IslandSettings.lyricsEnabled = true;
        IslandSettings.worldText = true;
        IslandSettings.skyLyrics = false;
        IslandSettings.showTranslations = true;
        IslandSettings.setNormalSide(IslandSettings.NormalSide.RIGHT); // Retain offset magnitude.
        IslandSettings.normalTextSize = IslandSettings.DEFAULT_NORMAL_SIZE;
        IslandSettings.skyTextSize = IslandSettings.DEFAULT_SKY_TEXT_SIZE;
        IslandSettings.skyCrosshairFollow = false;
        IslandSettings.skyBoundaryRadius = IslandSettings.DEFAULT_SKY_BOUNDARY_RADIUS;
        IslandSettings.skyContextLines = true;
        IslandSettings.skySongHeader = true;
        IslandSettings.lyricPrimaryColor = IslandSettings.DEFAULT_LYRIC_PRIMARY_COLOR;
        if (IslandSettings.separateSkyColors) IslandSettings.skyActiveColor = IslandSettings.DEFAULT_SKY_ACTIVE_COLOR;
        IslandSettings.hologramBackground = true;
        IslandSettings.hologramBgOpacity = IslandSettings.DEFAULT_HOLOGRAM_BG_OPACITY;
    }
}
