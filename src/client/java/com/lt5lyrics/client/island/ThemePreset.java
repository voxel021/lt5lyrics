package com.lt5lyrics.client.island;

import java.util.Locale;

/**
 * Curated one-click aesthetic themes for LT5Lyrics that style 3D World Lyrics,
 * Sky Lyrics, translations, glow, outlines, and the Dynamic Island HUD together.
 */
public enum ThemePreset {
	CLASSIC(
		"classic",
		"Classic Ice",
		0xFFFFFFFF, // primary
		0xFFBFE9FF, // highlight
		0xFFDCE8FA, // translation
		0xFF8FC7E3, // glow
		0xFF0E131D, // outline
		0xFF9A9A9A, // hudUpcoming
		0xFF8FC7E3, // hudAccent
		0xFF101010, // hudBg
		true
	),
	SPOTIFY(
		"spotify",
		"Spotify Neon",
		0xFFEFFFF5,
		0xFF1ED760,
		0xFFC4F5D6,
		0xFF1ED760,
		0xFF07160D,
		0xFF8BA896,
		0xFF1ED760,
		0xFF0A100D,
		false
	),
	SAKURA(
		"sakura",
		"Sakura",
		0xFFFFEEF5,
		0xFFFF85B3,
		0xFFFFD6E6,
		0xFFFF7AA8,
		0xFF1E0D16,
		0xFFC49AB0,
		0xFFFF85B3,
		0xFF181018,
		false
	),
	CYBERPUNK(
		"cyberpunk",
		"Cyberpunk",
		0xFFFCEE0A,
		0xFF00F0FF,
		0xFFA8F9FF,
		0xFF00F0FF,
		0xFF0C091E,
		0xFF7AD7E8,
		0xFF00F0FF,
		0xFF0B0918,
		false
	),
	GOLDEN(
		"golden",
		"Golden Hour",
		0xFFFFF4DC,
		0xFFFFB703,
		0xFFFFE5B4,
		0xFFFF9E00,
		0xFF1D1308,
		0xFFBFA584,
		0xFFFFB703,
		0xFF17120C,
		false
	),
	AMETHYST(
		"amethyst",
		"Amethyst",
		0xFFF5ECFF,
		0xFFC77DFF,
		0xFFE2CEFF,
		0xFF9D4EDD,
		0xFF130B24,
		0xFFA590C4,
		0xFFC77DFF,
		0xFF0E0A1B,
		false
	);

	public final String id;
	public final String label;
	public final int hudActiveColor;
	public final int lyricHighlightColor;
	public final int lyricTranslationColor;
	public final int lyricGlowColor;
	public final int lyricOutlineColor;
	public final int hudUpcomingColor;
	public final int hudAccentColor;
	public final int hudBgColor;
	public final int skyActiveColor;
	public final int skyGlowColor;
	public final boolean autoAccent;

	ThemePreset(
		String id,
		String label,
		int primaryColor,
		int highlightColor,
		int translationColor,
		int glowColor,
		int outlineColor,
		int hudUpcomingColor,
		int hudAccentColor,
		int hudBgColor,
		boolean autoAccent
	) {
		this.id = id;
		this.label = label;
		this.hudActiveColor = primaryColor;
		this.lyricHighlightColor = highlightColor;
		this.lyricTranslationColor = translationColor;
		this.lyricGlowColor = glowColor;
		this.lyricOutlineColor = outlineColor;
		this.hudUpcomingColor = hudUpcomingColor;
		this.hudAccentColor = hudAccentColor;
		this.hudBgColor = hudBgColor;
		this.skyActiveColor = primaryColor;
		this.skyGlowColor = glowColor;
		this.autoAccent = autoAccent;
	}

	public void apply() {
		IslandSettings.lyricPrimaryColor = hudActiveColor;
		IslandSettings.lyricHighlightColor = lyricHighlightColor;
		IslandSettings.lyricTranslationColor = lyricTranslationColor;
		IslandSettings.lyricGlowColor = lyricGlowColor;
		IslandSettings.lyricOutlineColor = lyricOutlineColor;
		IslandSettings.separateGlowColor = true;
		IslandSettings.separateHighlightColor = true;
		IslandSettings.separateSkyColors = false;

		IslandSettings.hudActiveColor = hudActiveColor;
		IslandSettings.hudUpcomingColor = hudUpcomingColor;
		IslandSettings.hudAccentColor = hudAccentColor;
		IslandSettings.hudBgColor = hudBgColor;
		IslandSettings.skyActiveColor = skyActiveColor;
		IslandSettings.skyGlowColor = skyGlowColor;
		IslandSettings.autoAccentColor = autoAccent;
		IslandSettings.save();
	}

	public boolean matchesCurrent() {
		return IslandSettings.lyricPrimaryColor == hudActiveColor
			&& IslandSettings.lyricGlowColor == lyricGlowColor
			&& IslandSettings.hudAccentColor == hudAccentColor
			&& IslandSettings.hudBgColor == hudBgColor;
	}

	public static ThemePreset byId(String name) {
		if (name == null) return null;
		String clean = name.trim().toLowerCase(Locale.ROOT);
		for (ThemePreset preset : values()) {
			if (preset.id.equals(clean) || preset.label.toLowerCase(Locale.ROOT).replace(" ", "").equals(clean)) {
				return preset;
			}
		}
		return null;
	}
}
