package com.lt5lyrics.client.island.font;

import com.lt5lyrics.client.island.IslandSettings;

public final class Fonts {
	public static MsdfFont MEDIUM;
	public static MsdfFont REGULAR;

	public static void init() {
		MEDIUM = MsdfFont.of("medium");
		REGULAR = MsdfFont.of("regular");
		DynamicSdfFont.ensureInitialized();
		org.slf4j.LoggerFactory.getLogger("lt5lyrics").info("LT5Lyrics multi-font & Unicode SDF engine loaded");
	}

	/**
	 * UI chrome medium font (Inter Modern + automatic Unicode/Khmer/CJK fallback).
	 */
	public static Font medium(float size) {
		return new Font(MEDIUM, size, null, null, false);
	}

	/**
	 * UI chrome regular font (Inter Modern + automatic Unicode/Khmer/CJK fallback).
	 */
	public static Font regular(float size) {
		return new Font(REGULAR, size, null, null, false);
	}

	/**
	 * Lyric & song display font that follows the user's selected FontPreset / custom font in Settings.
	 */
	public static Font lyric(float size) {
		return new Font(MEDIUM, size, null, null, true);
	}

	/**
	 * Secondary / idle lyric display font that follows the user's selected FontPreset / custom font.
	 */
	public static Font lyricSubtle(float size) {
		return new Font(REGULAR, size, null, null, true);
	}

	/**
	 * Specific preset font (used for live typeface preview cards in the Font Switcher UI).
	 */
	public static Font forPreset(IslandSettings.FontPreset preset, float size) {
		return new Font(MEDIUM, size, preset, "", false);
	}

	private Fonts() {
	}
}
