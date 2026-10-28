package com.lt5lyrics.client.island;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class IslandSettings {
	private static final Logger LOGGER = LoggerFactory.getLogger("lt5lyrics");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public enum VisualizerStyle {
		WAVES("Waves"),
		MIRROR("Mirror"),
		ORB("Pulse Orb"),
		OFF("Off");

		public final String label;

		VisualizerStyle(String label) {
			this.label = label;
		}

		public VisualizerStyle next() {
			VisualizerStyle[] vals = values();
			return vals[(ordinal() + 1) % vals.length];
		}

		public static VisualizerStyle fromString(String raw, VisualizerStyle fallback) {
			if (raw == null) return fallback;
			for (VisualizerStyle s : values()) {
				if (s.name().equalsIgnoreCase(raw.trim())) return s;
			}
			return fallback;
		}
	}

	public enum ClockMode {
		CLOCK_24H("24h"),
		CLOCK_12H("12h"),
		OFF("Hidden");

		public final String label;

		ClockMode(String label) {
			this.label = label;
		}

		public ClockMode next() {
			ClockMode[] vals = values();
			return vals[(ordinal() + 1) % vals.length];
		}

		public static ClockMode fromString(String raw, ClockMode fallback) {
			if (raw == null) return fallback;
			for (ClockMode m : values()) {
				if (m.name().equalsIgnoreCase(raw.trim()) || m.label.equalsIgnoreCase(raw.trim())) return m;
			}
			return fallback;
		}
	}

	public enum FontPreset {
		INTER_MODERN("inter", "Inter Modern", "Modern", "Clean easy-to-read modern sans", null, false),
		MINECRAFT_PIXEL("minecraft", "Minecraft Pixel", "MC Pixel", "Crisp classic Minecraft pixel text", "minecraft_pixel.ttf", true),
		ROUNDED_POP("rounded", "Rounded Pop", "Rounded", "Warm friendly rounded karaoke", "rounded_pop.ttf", false),
		EDITORIAL_SERIF("serif", "Editorial Serif", "Serif", "Classic book-style serif", "editorial_serif.ttf", false),
		MONO_TECH("mono", "Mono Tech", "Mono", "Sleek monospaced studio text", "mono_tech.ttf", false),
		STAGE_BOLD("bold", "Stage Bold", "Bold", "Big bold concert display letters", "stage_bold.ttf", false),
		KHMER_CLASSIC("khmer", "Khmer Classic", "Khmer", "Native Kantumruy Khmer + English", "khmer_classic.ttf", false);

		public final String id;
		public final String label;
		public final String shortLabel;
		public final String subtitle;
		public final String resourceFile;
		public final boolean pixelCrisp;

		FontPreset(String id, String label, String shortLabel, String subtitle, String resourceFile, boolean pixelCrisp) {
			this.id = id;
			this.label = label;
			this.shortLabel = shortLabel;
			this.subtitle = subtitle;
			this.resourceFile = resourceFile;
			this.pixelCrisp = pixelCrisp;
		}

		public FontPreset next() {
			FontPreset[] vals = values();
			return vals[(ordinal() + 1) % vals.length];
		}

		public static FontPreset byId(String raw) {
			return fromString(raw, INTER_MODERN);
		}

		public static FontPreset fromString(String raw, FontPreset fallback) {
			if (raw == null || raw.isBlank()) return fallback;
			String norm = raw.trim();
			for (FontPreset p : values()) {
				if (p.id.equalsIgnoreCase(norm)
					|| p.name().equalsIgnoreCase(norm)
					|| p.label.equalsIgnoreCase(norm)
					|| p.shortLabel.equalsIgnoreCase(norm)) {
					return p;
				}
			}
			return fallback;
		}
	}

	public enum LyricAnimation {
		RISE_FLOW("rise_flow", "Rise & Fade", "Rise Up", "Smoothly rises upward and fades out"),
		CLASSIC_GLIDE("classic_glide", "Classic Glide", "Glide", "Gentle upward glide into place"),
		CASCADE_DROP("cascade_drop", "Cascade Drop", "Cascade", "Drops smoothly down from above"),
		POP_SPRING("pop_spring", "Spring Pop", "Spring", "Playful bouncy pop entrance"),
		WAVE_RIPPLE("wave_ripple", "Wave Ripple", "Wave", "Flowing musical wave across letters"),
		CINEMA_REVEAL("cinema_reveal", "Cinema Reveal", "Reveal", "Left-to-right cinema sweep");

		public final String id;
		public final String label;
		public final String shortLabel;
		public final String description;

		LyricAnimation(String id, String label, String shortLabel, String description) {
			this.id = id;
			this.label = label;
			this.shortLabel = shortLabel;
			this.description = description;
		}

		public LyricAnimation next() {
			LyricAnimation[] vals = values();
			return vals[(ordinal() + 1) % vals.length];
		}

		public static LyricAnimation byId(String raw) {
			return fromString(raw, RISE_FLOW);
		}

		public static LyricAnimation fromString(String raw, LyricAnimation fallback) {
			if (raw == null || raw.isBlank()) return fallback;
			String norm = raw.trim();
			for (LyricAnimation a : values()) {
				if (a.id.equalsIgnoreCase(norm)
					|| a.name().equalsIgnoreCase(norm)
					|| a.label.equalsIgnoreCase(norm)
					|| a.shortLabel.equalsIgnoreCase(norm)) {
					return a;
				}
			}
			return fallback;
		}
	}

	public enum HologramBillboard {
		SMART_BTP("Natural 3D"),
		CENTER("Face Camera"),
		VERTICAL("Upright");

		public final String label;

		HologramBillboard(String label) {
			this.label = label;
		}

		public HologramBillboard next() {
			HologramBillboard[] vals = values();
			return vals[(ordinal() + 1) % vals.length];
		}

		public static HologramBillboard fromString(String raw, HologramBillboard fallback) {
			if (raw == null || raw.isBlank()) return fallback;
			String norm = raw.trim();
			if ("Smart BTP".equalsIgnoreCase(norm)) return SMART_BTP;
			if ("Center Face".equalsIgnoreCase(norm)) return CENTER;
			for (HologramBillboard b : values()) {
				if (b.name().equalsIgnoreCase(norm) || b.label.equalsIgnoreCase(norm)) {
					return b;
				}
			}
			return fallback;
		}
	}

	/** Side is selected once for each newly spawned Normal 3D group. */
	public enum NormalSide {
		LEFT("Left"), RIGHT("Right"), ALTERNATE("Alternate"), RANDOM("Random");
		public final String label;
		NormalSide(String label) { this.label = label; }
		public static NormalSide fromString(String raw, NormalSide fallback) {
			if (raw != null) for (NormalSide side : values()) {
				if (side.name().equalsIgnoreCase(raw.trim()) || side.label.equalsIgnoreCase(raw.trim())) return side;
			}
			return fallback;
		}
	}

	public enum DisplayMode {
		WORLD_3D("3D World Lyrics", "Floating lyrics placed naturally in the world around you"),
		SKY_LYRICS("Sky Lyrics", "Centered lyrics at one steady 3D location in the sky"),
		HUD_ONLY("Top Bar Only", "Show lyrics only inside the compact top music bar");

		public final String label;
		public final String subtitle;

		DisplayMode(String label, String subtitle) {
			this.label = label;
			this.subtitle = subtitle;
		}
	}

	/**
	 * Immutable snapshot of appearance settings for live Before / After preview comparison.
	 */
	public record StyleSnapshot(
		int primaryColor,
		int highlightColor,
		int translationColor,
		int glowColor,
		int outlineColor,
		int hudBgColor,
		int hudAccentColor,
		boolean glowEnabled,
		float glowIntensity,
		float outlineThickness,
		float textScale,
		float smoothness,
		float lineSpacing,
		boolean shadow,
		boolean translations,
		FontPreset fontPreset,
		String customFontName,
		LyricAnimation animation,
		float animationSpeed
	) {
	}

	// Normal 3D only. Distance and offset are in Minecraft world blocks.
	public static final double MIN_NORMAL_DISTANCE = 2.0, MAX_NORMAL_DISTANCE = 24.0, DEFAULT_NORMAL_DISTANCE = 6.0;
	public static final double MAX_NORMAL_OFFSET = 16.0, MIN_NORMAL_ABS_OFFSET = 1.25, DEFAULT_NORMAL_OFFSET = 3.5;
	public static final float MIN_NORMAL_SIZE = 0.50F, MAX_NORMAL_SIZE = 2.00F, DEFAULT_NORMAL_SIZE = 1.00F;
	public static final float MIN_NORMAL_SPACING = 0.75F, MAX_NORMAL_SPACING = 2.00F, DEFAULT_NORMAL_SPACING = 1.00F;
	public static final float MIN_NORMAL_CURVE = 0.0F, MAX_NORMAL_CURVE = 2.0F, DEFAULT_NORMAL_CURVE = 0.8F;
	public static final int MIN_NORMAL_WORDS = 1, MAX_NORMAL_WORDS = 12, DEFAULT_NORMAL_WORDS = 5;

	public static final double MIN_SKY_BOUNDARY_RADIUS = 80.0;
	public static final double MAX_SKY_BOUNDARY_RADIUS = 1000.0;
	public static final double DEFAULT_SKY_BOUNDARY_RADIUS = 360.0;

	public static final double MIN_SKY_HEIGHT = 12.0;
	public static final double MAX_SKY_HEIGHT = 260.0;
	public static final double DEFAULT_SKY_HEIGHT = 54.0;

	public static final float MIN_SKY_TEXT_SIZE = 0.10F;
	public static final float MAX_SKY_TEXT_SIZE = 1.00F;
	public static final float DEFAULT_SKY_TEXT_SIZE = 0.50F;

	public static final float MIN_SKY_DRIFT_SPEED = 0.00F;
	public static final float MAX_SKY_DRIFT_SPEED = 2.00F;
	public static final float DEFAULT_SKY_DRIFT_SPEED = 1.00F;

	public static final float MIN_SKY_GLOW = 0.00F;
	public static final float MAX_SKY_GLOW = 1.00F;
	public static final float DEFAULT_SKY_GLOW = 0.65F;

	public static final float MIN_SKY_ANGLE = -180.0F;
	public static final float MAX_SKY_ANGLE = 180.0F;
	public static final float DEFAULT_SKY_ANGLE = 0.0F;

	public static final float MIN_SKY_CURVE = 0.00F;
	public static final float MAX_SKY_CURVE = 2.00F;
	public static final float DEFAULT_SKY_CURVE = 1.00F;

	public static final float MIN_HUD_OPACITY = 0.30F;
	public static final float MAX_HUD_OPACITY = 1.00F;
	public static final float DEFAULT_HUD_OPACITY = 0.85F;

	public static final float MIN_ANIM_SPEED = 0.50F;
	public static final float MAX_ANIM_SPEED = 2.00F;
	public static final float DEFAULT_ANIM_SPEED = 1.00F;

	public static final float MIN_HOLOGRAM_BG_OPACITY = 0.00F;
	public static final float MAX_HOLOGRAM_BG_OPACITY = 0.85F;
	public static final float DEFAULT_HOLOGRAM_BG_OPACITY = 0.32F;
	public static final float MIN_HOLO_BG_OPACITY = MIN_HOLOGRAM_BG_OPACITY;
	public static final float MAX_HOLO_BG_OPACITY = MAX_HOLOGRAM_BG_OPACITY;
	public static final float DEFAULT_HOLO_BG_OPACITY = DEFAULT_HOLOGRAM_BG_OPACITY;

	public static final float MIN_HOLOGRAM_SCALE = 0.50F;
	public static final float MAX_HOLOGRAM_SCALE = 2.00F;
	public static final float DEFAULT_HOLOGRAM_SCALE = 1.00F;
	public static final float MIN_HOLO_SCALE = MIN_HOLOGRAM_SCALE;
	public static final float MAX_HOLO_SCALE = MAX_HOLOGRAM_SCALE;
	public static final float DEFAULT_HOLO_SCALE = DEFAULT_HOLOGRAM_SCALE;

	public static final float MIN_LYRIC_TEXT_SCALE = 0.50F;
	public static final float MAX_LYRIC_TEXT_SCALE = 2.00F;
	public static final float DEFAULT_LYRIC_TEXT_SCALE = 1.00F;

	public static final float MIN_GLOW_INTENSITY = 0.00F;
	public static final float MAX_GLOW_INTENSITY = 1.00F;
	public static final float DEFAULT_GLOW_INTENSITY = 0.60F;

	public static final float MIN_OUTLINE_THICKNESS = 0.00F;
	public static final float MAX_OUTLINE_THICKNESS = 1.00F;
	public static final float DEFAULT_OUTLINE_THICKNESS = 0.25F;

	public static final float MIN_SMOOTHNESS = 0.25F;
	public static final float MAX_SMOOTHNESS = 2.00F;
	public static final float DEFAULT_SMOOTHNESS = 1.00F;

	public static final float MIN_LINE_SPACING = 0.75F;
	public static final float MAX_LINE_SPACING = 1.60F;
	public static final float DEFAULT_LINE_SPACING = 1.00F;

	public static final int DEFAULT_HUD_ACTIVE_COLOR = 0xFFFFFFFF;
	public static final int DEFAULT_HUD_UPCOMING_COLOR = 0xFF9A9A9A;
	public static final int DEFAULT_HUD_ACCENT_COLOR = 0xFF8FC7E3;
	public static final int DEFAULT_HUD_BG_COLOR = 0xFF101010;
	public static final int DEFAULT_SKY_ACTIVE_COLOR = 0xFFFFFFFF;
	public static final int DEFAULT_SKY_GLOW_COLOR = 0xFF8FC7E3;

	public static final int DEFAULT_LYRIC_PRIMARY_COLOR = 0xFFFFFFFF;
	public static final int DEFAULT_LYRIC_HIGHLIGHT_COLOR = 0xFFFFFFFF;
	public static final int DEFAULT_LYRIC_TRANSLATION_COLOR = 0xFFDCE8FA;
	public static final int DEFAULT_LYRIC_GLOW_COLOR = 0xFF8FC7E3;
	public static final int DEFAULT_LYRIC_OUTLINE_COLOR = 0xFF0E131D;

	// Customization Studio UI Mode & Remembered Category (false = Beginner-Friendly Simple Mode by default)
	public static boolean advancedSettingsMode = false;
	public static int lastStudioTab = 0;

	public static final List<String> DEFAULT_PROVIDER_PRIORITY = List.of(
		"lrclib", "unison", "amll", "betterlyrics", "netease", "kugou", "lrcmux", "musixmatch", "lyricsovh"
	);

	// This is lyric visibility only; the music bar and the selected display style stay intact.
	// Missing legacy configurations default to on, preserving pre-1.9 behavior.
	public static boolean lyricsEnabled = true;

	// Normal 3D World Lyrics (independent of Sky and the HUD).
	public static boolean worldText = true;
	public static boolean btp = true;
	public static boolean throughBlocks = false;
	public static boolean follow = false;
	public static boolean locked = false;
	public static boolean hideBossbar = false;
	public static boolean hologramBackground = true;
	public static float hologramBgOpacity = DEFAULT_HOLOGRAM_BG_OPACITY;
	public static boolean hologramShadow = true;
	public static float hologramScale = DEFAULT_HOLOGRAM_SCALE;
	public static HologramBillboard hologramBillboard = HologramBillboard.SMART_BTP;
	public static double normalDistance = DEFAULT_NORMAL_DISTANCE;
	public static double normalOffset = DEFAULT_NORMAL_OFFSET;
	public static NormalSide normalSide = NormalSide.RIGHT;
	public static boolean normalCurveEnabled = false;
	public static float normalCurve = DEFAULT_NORMAL_CURVE;
	public static int normalWordsPerLine = DEFAULT_NORMAL_WORDS;
	public static float normalLineSpacing = DEFAULT_NORMAL_SPACING;
	public static float normalTextSize = DEFAULT_NORMAL_SIZE;
	public static boolean normalHideBehind = true;

	/** Slider/load helper: zero and near-zero are kept on a nonzero side. */
	public static void setNormalOffset(double requested) {
		if (!Double.isFinite(requested)) requested = DEFAULT_NORMAL_OFFSET;
		int sign = requested < 0 ? -1 : requested > 0 ? 1 : (normalOffset < 0 ? -1 : 1);
		double magnitude = Math.clamp(Math.abs(requested), MIN_NORMAL_ABS_OFFSET, MAX_NORMAL_OFFSET);
		normalOffset = sign * magnitude;
		// In fixed-side modes, moving the signed slider across zero selects that side.
		if (normalSide == NormalSide.LEFT || normalSide == NormalSide.RIGHT) {
			normalSide = sign < 0 ? NormalSide.LEFT : NormalSide.RIGHT;
		}
	}
	public static void setNormalSide(NormalSide side) {
		if (side == null) return;
		normalSide = side;
		if (side == NormalSide.LEFT) normalOffset = -Math.max(MIN_NORMAL_ABS_OFFSET, Math.abs(normalOffset));
		if (side == NormalSide.RIGHT) normalOffset = Math.max(MIN_NORMAL_ABS_OFFSET, Math.abs(normalOffset));
	}

	// Typography, Sizing, Glow, Outline, Animation & International Lyrics settings
	public static FontPreset fontPreset = FontPreset.INTER_MODERN;
	public static String customFontName = "";
	public static LyricAnimation lyricAnimation = LyricAnimation.RISE_FLOW;
	public static float animationSpeed = DEFAULT_ANIM_SPEED;
	public static float lyricSmoothness = DEFAULT_SMOOTHNESS;
	public static float lyricTextScale = DEFAULT_LYRIC_TEXT_SCALE;
	public static float lyricLineSpacing = DEFAULT_LINE_SPACING;
	public static boolean lyricGlowEnabled = true;
	public static float lyricGlowIntensity = DEFAULT_GLOW_INTENSITY;
	public static float lyricOutlineThickness = DEFAULT_OUTLINE_THICKNESS;
	public static boolean showTranslations = true;

	// HUD settings (-1 means default centered position)
	public static boolean showHud = true;
	public static float posX = -1.0F;
	public static float posY = -1.0F;
	public static float hudOpacity = DEFAULT_HUD_OPACITY;
	public static boolean hudBeatPulse = true;
	public static boolean trackToast = true;
	public static VisualizerStyle visualizerStyle = VisualizerStyle.WAVES;
	public static ClockMode clockMode = ClockMode.CLOCK_24H;

	// Sky Lyrics & player-following 3D circular boundary settings
	public static boolean skyLyrics = false;
	public static boolean skyThroughBlocks = false;
	// Retains the legacy JSON key; the Sky Studio names this Follow Look Direction.
	public static boolean skyCrosshairFollow = false;
	public static double skyBoundaryRadius = DEFAULT_SKY_BOUNDARY_RADIUS;
	public static double skyHeight = DEFAULT_SKY_HEIGHT;
	public static float skyTextSize = DEFAULT_SKY_TEXT_SIZE;
	public static float skyDriftSpeed = DEFAULT_SKY_DRIFT_SPEED;
	public static float skyGlow = DEFAULT_SKY_GLOW;
	public static float skyAngleDegrees = DEFAULT_SKY_ANGLE;
	public static float skyCurve = DEFAULT_SKY_CURVE;
	public static boolean skyContextLines = true;
	public static boolean skySongHeader = true;
	public static boolean skyBeatPulse = true;

	// Unified & Per-Channel Lyric Colors (live across 3D World Holograms, Sky Lyrics, and HUD)
	public static int lyricPrimaryColor = DEFAULT_LYRIC_PRIMARY_COLOR;
	public static int lyricHighlightColor = DEFAULT_LYRIC_HIGHLIGHT_COLOR;
	public static int lyricTranslationColor = DEFAULT_LYRIC_TRANSLATION_COLOR;
	public static int lyricGlowColor = DEFAULT_LYRIC_GLOW_COLOR;
	public static int lyricOutlineColor = DEFAULT_LYRIC_OUTLINE_COLOR;
	public static boolean separateGlowColor = false;
	public static boolean separateHighlightColor = false;
	public static boolean separateSkyColors = false;

	// Legacy & Surface-Specific Color Fields (kept synchronized for compatibility)
	public static int hudActiveColor = DEFAULT_HUD_ACTIVE_COLOR;
	public static int hudUpcomingColor = DEFAULT_HUD_UPCOMING_COLOR;
	public static boolean autoAccentColor = true;
	public static int hudAccentColor = DEFAULT_HUD_ACCENT_COLOR;
	public static int hudBgColor = DEFAULT_HUD_BG_COLOR;
	public static int skyActiveColor = DEFAULT_SKY_ACTIVE_COLOR;
	public static int skyGlowColor = DEFAULT_SKY_GLOW_COLOR;

	// Lyric Sources, Sync Policy, Priority Order & Optional User-Configured API Keys (never hard-coded, never logged)
	public static boolean autoPaceUnsyncedLyrics = true;
	// Prefer real word/syllable timing across enabled sources; order breaks ties.
	public static boolean preferWordTiming = true;
	public static boolean enableLrclib = true;
	public static boolean enableUnison = true;
	public static boolean enableAmll = true;
	public static boolean enableBetterLyrics = true;
	public static boolean enableNetease = true;
	public static boolean enableKugou = true;
	public static boolean enableLrcmux = true;
	public static boolean enableMusixmatch = true;
	public static boolean enableLyricsOvh = true;
	public static List<String> providerPriority = new ArrayList<>(DEFAULT_PROVIDER_PRIORITY);
	public static String musixmatchApiKey = "";
	public static String betterLyricsApiKey = "";

	private IslandSettings() {
	}

	/**
	 * Applies a unified Lyric Color from the Simple Mode color wheel (or primary picker),
	 * updating 3D World Lyrics, Sky Lyrics, and HUD lyrics immediately in real time.
	 */
	public static void applyUnifiedLyricColor(int rgb) {
		int c = ColorUtil.opaque(rgb);
		separateSkyColors = false;
		lyricPrimaryColor = c;
		hudActiveColor = c;
		skyActiveColor = c;
		if (!separateHighlightColor) {
			lyricHighlightColor = ColorUtil.harmonizedHighlightFor(c);
		}
		if (!separateGlowColor) {
			int glow = ColorUtil.harmonizedGlowFor(c);
			lyricGlowColor = glow;
			skyGlowColor = glow;
		}
	}

	public static int resolveWorldPrimaryColor() {
		return ColorUtil.opaque(lyricPrimaryColor);
	}

	public static int resolveSkyPrimaryColor() {
		return ColorUtil.opaque(separateSkyColors ? skyActiveColor : lyricPrimaryColor);
	}

	public static int resolveHudPrimaryColor() {
		return ColorUtil.opaque(lyricPrimaryColor);
	}

	public static int resolveHighlightColor() {
		return ColorUtil.opaque(separateHighlightColor
			? lyricHighlightColor
			: ColorUtil.harmonizedHighlightFor(lyricPrimaryColor));
	}

	public static int resolveTranslationColor() {
		return ColorUtil.opaque(lyricTranslationColor);
	}

	public static int resolveGlowColor() {
		return ColorUtil.opaque(lyricGlowColor);
	}

	public static int resolveOutlineColor() {
		return ColorUtil.opaque(lyricOutlineColor);
	}

	public static float effectiveWorldGlow() {
		return lyricGlowEnabled ? clampFloat(lyricGlowIntensity, MIN_GLOW_INTENSITY, MAX_GLOW_INTENSITY) : 0.0F;
	}

	public static float effectiveSkyGlow() {
		if (!lyricGlowEnabled) return 0.0F;
		return clampFloat(skyGlow, MIN_SKY_GLOW, MAX_SKY_GLOW);
	}

	public static DisplayMode currentDisplayMode() {
		if (worldText && skyLyrics) return DisplayMode.SKY_LYRICS;
		if (worldText) return DisplayMode.WORLD_3D;
		return DisplayMode.HUD_ONLY;
	}

	public static void setDisplayMode(DisplayMode mode) {
		switch (mode) {
			case WORLD_3D -> {
				worldText = true;
				skyLyrics = false;
			}
			case SKY_LYRICS -> {
				worldText = true;
				skyLyrics = true;
			}
			case HUD_ONLY -> {
				worldText = false;
				skyLyrics = false;
				showHud = true;
			}
		}
	}

	public static StyleSnapshot captureStyleSnapshot() {
		return new StyleSnapshot(
			resolveWorldPrimaryColor(),
			resolveHighlightColor(),
			resolveTranslationColor(),
			resolveGlowColor(),
			resolveOutlineColor(),
			ColorUtil.opaque(hudBgColor),
			ColorUtil.opaque(hudAccentColor),
			lyricGlowEnabled,
			lyricGlowIntensity,
			lyricOutlineThickness,
			lyricTextScale,
			lyricSmoothness,
			lyricLineSpacing,
			hologramShadow,
			showTranslations,
			fontPreset,
			customFontName == null ? "" : customFontName,
			lyricAnimation,
			animationSpeed
		);
	}

	public static Path configDir() {
		return FabricLoader.getInstance().getConfigDir().resolve("lt5lyrics");
	}

	public static Path customFontsDir() {
		return configDir().resolve("fonts");
	}

	public static Path ensureCustomFontsDir() {
		Path dir = customFontsDir();
		try {
			Files.createDirectories(dir);
		} catch (IOException ignored) {
		}
		return dir;
	}

	public static String activeFontLabel() {
		return activeFontDisplayLabel();
	}

	public static int resolveAccentColor(int mediaColor) {
		if (autoAccentColor && mediaColor != 0xFFFFFFFF && mediaColor != 0) {
			return mediaColor;
		}
		return hudAccentColor;
	}

	public static List<String> listCustomFontFiles() {
		List<String> out = new ArrayList<>();
		try {
			Path dir = customFontsDir();
			if (!Files.isDirectory(dir)) {
				return out;
			}
			try (Stream<Path> files = Files.list(dir)) {
				files.filter(Files::isRegularFile)
					.map(p -> p.getFileName().toString())
					.filter(n -> {
						String low = n.toLowerCase(Locale.ROOT);
						return low.endsWith(".ttf") || low.endsWith(".otf");
					})
					.sorted(Comparator.comparing(s -> s.toLowerCase(Locale.ROOT)))
					.forEach(out::add);
			}
		} catch (Throwable ignored) {
		}
		return out;
	}

	public static String activeFontDisplayLabel() {
		if (customFontName != null && !customFontName.isBlank()) {
			String base = customFontName.trim();
			int dot = base.lastIndexOf('.');
			if (dot > 0) base = base.substring(0, dot);
			return base.length() > 16 ? base.substring(0, 16) : base;
		}
		return fontPreset.label;
	}

	public static String cycleFontChoice(int step) {
		cycleFontChoice();
		save();
		return activeFontDisplayLabel();
	}

	public static void cycleFontChoice() {
		List<String> customs = listCustomFontFiles();
		if (customFontName != null && !customFontName.isBlank()) {
			int idx = customs.indexOf(customFontName);
			if (idx >= 0 && idx + 1 < customs.size()) {
				customFontName = customs.get(idx + 1);
				return;
			}
			customFontName = "";
			fontPreset = FontPreset.INTER_MODERN;
			return;
		}
		FontPreset[] presets = FontPreset.values();
		if (fontPreset.ordinal() + 1 < presets.length) {
			fontPreset = presets[fontPreset.ordinal() + 1];
		} else if (!customs.isEmpty()) {
			customFontName = customs.get(0);
		} else {
			fontPreset = FontPreset.INTER_MODERN;
		}
	}

	private static Path legacyConfigDir() {
		return FabricLoader.getInstance().getConfigDir().resolve("song-island");
	}

	public static Path configFile() {
		return configDir().resolve("settings.json");
	}

	public static void migrateLegacyIfNeeded() {
		try {
			Path newDir = configDir();
			Path oldDir = legacyConfigDir();
			Files.createDirectories(customFontsDir());
			if (Files.exists(newDir.resolve("settings.json")) || !Files.isDirectory(oldDir)) {
				return;
			}
			Files.createDirectories(newDir);
			copyIfMissing(oldDir.resolve("settings.json"), newDir.resolve("settings.json"));
			copyIfMissing(oldDir.resolve("delays.json"), newDir.resolve("delays.json"));
			Path oldLyrics = oldDir.resolve("lyrics");
			if (Files.isDirectory(oldLyrics)) {
				Path newLyrics = newDir.resolve("lyrics");
				Files.createDirectories(newLyrics);
				try (Stream<Path> files = Files.list(oldLyrics)) {
					files.filter(Files::isRegularFile).forEach(src ->
						copyIfMissing(src, newLyrics.resolve(src.getFileName())));
				}
			}
			LOGGER.info("Migrated legacy configuration into {}", newDir);
		} catch (Throwable e) {
			LOGGER.warn("Could not migrate legacy settings: {}", e.toString());
		}
	}

	private static void copyIfMissing(Path source, Path target) {
		try {
			if (Files.isRegularFile(source) && !Files.exists(target)) {
				Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
			}
		} catch (IOException ignored) {
		}
	}

	public static void load() {
		migrateLegacyIfNeeded();
		loadFromFile(configFile());
	}

	public static void loadFromFile(Path file) {
		try {
			if (!Files.exists(file)) {
				saveToFile(file);
				return;
			}
			JsonObject json = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), JsonObject.class);
			if (json == null) {
				LOGGER.warn("LT5Lyrics settings file is empty or invalid; keeping defaults.");
				return;
			}

			applyFromJson(json);
		} catch (Exception e) {
			LOGGER.warn("Could not load LT5Lyrics settings: {}", e.toString());
		}
	}

	/** Apply a complete validated settings object without a temporary credentials file. */
	public static void applyFromJson(JsonObject json) {
		if (json == null) return;
		try {
			advancedSettingsMode = getBoolean(json, "advancedSettingsMode", false);
			lastStudioTab = Math.clamp((int) Math.round(getDouble(json, "lastStudioTab", 0)), 0, 4);
			lyricsEnabled = getBoolean(json, "lyricsEnabled", true);

			worldText = getBoolean(json, "worldText", worldText);
			btp = getBoolean(json, "btp", btp);
			throughBlocks = getBoolean(json, "throughBlocks", throughBlocks);
			// Version 1.5 followed by default. The 1.6 normal renderer defaults to stable,
			// world-fixed anchors; migrate old configs once, while retaining future choices.
			follow = json.has("normalDistance") && getBoolean(json, "follow", false);
			locked = getBoolean(json, "locked", locked);
			hideBossbar = getBoolean(json, "hideBossbar", hideBossbar);
			hologramBackground = getBoolean(json, "hologramBackground", hologramBackground);
			hologramBgOpacity = clampFloat(
				(float) getDouble(json, "hologramBgOpacity", hologramBgOpacity),
				MIN_HOLOGRAM_BG_OPACITY, MAX_HOLOGRAM_BG_OPACITY
			);
			hologramShadow = getBoolean(json, "hologramShadow", hologramShadow);
			hologramScale = clampFloat(
				(float) getDouble(json, "hologramScale", hologramScale),
				MIN_HOLOGRAM_SCALE, MAX_HOLOGRAM_SCALE
			);
			hologramBillboard = HologramBillboard.fromString(
				getString(json, "hologramBillboard", null), hologramBillboard
			);
			normalDistance = clampDouble(getDouble(json, "normalDistance", normalDistance), MIN_NORMAL_DISTANCE, MAX_NORMAL_DISTANCE);
			// Load offset first; the explicit side choice is authoritative for fixed-side modes.
			setNormalOffset(getDouble(json, "normalOffset", normalOffset));
			setNormalSide(NormalSide.fromString(getString(json, "normalSide", null), normalSide));
			normalCurveEnabled = getBoolean(json, "normalCurveEnabled", normalCurveEnabled);
			normalCurve = clampFloat((float) getDouble(json, "normalCurve", normalCurve), MIN_NORMAL_CURVE, MAX_NORMAL_CURVE);
			normalWordsPerLine = Math.clamp((int) getDouble(json, "normalWordsPerLine", normalWordsPerLine), MIN_NORMAL_WORDS, MAX_NORMAL_WORDS);
			normalLineSpacing = clampFloat((float) getDouble(json, "normalLineSpacing", normalLineSpacing), MIN_NORMAL_SPACING, MAX_NORMAL_SPACING);
			normalTextSize = clampFloat((float) getDouble(json, "normalTextSize", normalTextSize), MIN_NORMAL_SIZE, MAX_NORMAL_SIZE);
			normalHideBehind = getBoolean(json, "normalHideBehind", normalHideBehind);

			fontPreset = FontPreset.fromString(getString(json, "fontPreset", null), fontPreset);
			String loadedCustomFont = getString(json, "customFontName", "");
			customFontName = loadedCustomFont == null ? "" : loadedCustomFont.trim();
			lyricAnimation = LyricAnimation.fromString(getString(json, "lyricAnimation", null), lyricAnimation);
			animationSpeed = clampFloat(
				(float) getDouble(json, "animationSpeed", animationSpeed),
				MIN_ANIM_SPEED, MAX_ANIM_SPEED
			);
			lyricSmoothness = clampFloat(
				(float) getDouble(json, "lyricSmoothness", lyricSmoothness),
				MIN_SMOOTHNESS, MAX_SMOOTHNESS
			);
			lyricTextScale = clampFloat(
				(float) getDouble(json, "lyricTextScale", lyricTextScale),
				MIN_LYRIC_TEXT_SCALE, MAX_LYRIC_TEXT_SCALE
			);
			lyricLineSpacing = clampFloat(
				(float) getDouble(json, "lyricLineSpacing", lyricLineSpacing),
				MIN_LINE_SPACING, MAX_LINE_SPACING
			);
			lyricGlowEnabled = getBoolean(json, "lyricGlowEnabled", lyricGlowEnabled);
			lyricGlowIntensity = clampFloat(
				(float) getDouble(json, "lyricGlowIntensity", lyricGlowIntensity),
				MIN_GLOW_INTENSITY, MAX_GLOW_INTENSITY
			);
			lyricOutlineThickness = clampFloat(
				(float) getDouble(json, "lyricOutlineThickness", lyricOutlineThickness),
				MIN_OUTLINE_THICKNESS, MAX_OUTLINE_THICKNESS
			);
			showTranslations = getBoolean(json, "showTranslations", showTranslations);

			showHud = getBoolean(json, "showHud", showHud);
			float loadedX = (float) getDouble(json, "posX", posX);
			float loadedY = (float) getDouble(json, "posY", posY);
			if (loadedX <= 0.0F || loadedY <= 0.0F) {
				posX = -1.0F;
				posY = -1.0F;
			} else {
				posX = loadedX;
				posY = loadedY;
			}
			hudOpacity = clampFloat((float) getDouble(json, "hudOpacity", hudOpacity), MIN_HUD_OPACITY, MAX_HUD_OPACITY);
			hudBeatPulse = getBoolean(json, "hudBeatPulse", hudBeatPulse);
			trackToast = getBoolean(json, "trackToast", trackToast);
			visualizerStyle = VisualizerStyle.fromString(getString(json, "visualizerStyle", null), visualizerStyle);
			clockMode = ClockMode.fromString(getString(json, "clockMode", null), clockMode);

			skyLyrics = getBoolean(json, "skyLyrics", skyLyrics);
			skyThroughBlocks = getBoolean(json, "skyThroughBlocks", skyThroughBlocks);
			skyCrosshairFollow = getBoolean(json, "skyCrosshairFollow", false);
			skyBoundaryRadius = clampDouble(
				getDouble(json, "skyBoundaryRadius", skyBoundaryRadius),
				MIN_SKY_BOUNDARY_RADIUS, MAX_SKY_BOUNDARY_RADIUS
			);
			skyHeight = clampDouble(
				getDouble(json, "skyHeight", skyHeight),
				MIN_SKY_HEIGHT, MAX_SKY_HEIGHT
			);
			skyTextSize = clampFloat(
				(float) getDouble(json, "skyTextSize", skyTextSize),
				MIN_SKY_TEXT_SIZE, MAX_SKY_TEXT_SIZE
			);
			skyDriftSpeed = clampFloat(
				(float) getDouble(json, "skyDriftSpeed", skyDriftSpeed),
				MIN_SKY_DRIFT_SPEED, MAX_SKY_DRIFT_SPEED
			);
			skyGlow = clampFloat(
				(float) getDouble(json, "skyGlow", skyGlow),
				MIN_SKY_GLOW, MAX_SKY_GLOW
			);
			skyAngleDegrees = clampFloat(
				(float) getDouble(json, "skyAngleDegrees", skyAngleDegrees),
				MIN_SKY_ANGLE, MAX_SKY_ANGLE
			);
			skyCurve = clampFloat(
				(float) getDouble(json, "skyCurve", skyCurve),
				MIN_SKY_CURVE, MAX_SKY_CURVE
			);
			skyContextLines = getBoolean(json, "skyContextLines", skyContextLines);
			skySongHeader = getBoolean(json, "skySongHeader", skySongHeader);
			skyBeatPulse = getBoolean(json, "skyBeatPulse", skyBeatPulse);

			hudActiveColor = getColor(json, "hudActiveColor", hudActiveColor);
			hudUpcomingColor = getColor(json, "hudUpcomingColor", hudUpcomingColor);
			autoAccentColor = getBoolean(json, "autoAccentColor", autoAccentColor);
			hudAccentColor = getColor(json, "hudAccentColor", hudAccentColor);
			hudBgColor = getColor(json, "hudBgColor", hudBgColor);
			skyActiveColor = getColor(json, "skyActiveColor", skyActiveColor);
			skyGlowColor = getColor(json, "skyGlowColor", skyGlowColor);

			if (json.has("lyricPrimaryColor")) {
				lyricPrimaryColor = getColor(json, "lyricPrimaryColor", lyricPrimaryColor);
			} else if (hudActiveColor != DEFAULT_HUD_ACTIVE_COLOR) {
				lyricPrimaryColor = hudActiveColor;
			} else if (skyActiveColor != DEFAULT_SKY_ACTIVE_COLOR) {
				lyricPrimaryColor = skyActiveColor;
			} else {
				lyricPrimaryColor = DEFAULT_LYRIC_PRIMARY_COLOR;
			}

			lyricHighlightColor = getColor(json, "lyricHighlightColor",
				ColorUtil.harmonizedHighlightFor(lyricPrimaryColor));
			lyricTranslationColor = getColor(json, "lyricTranslationColor", lyricTranslationColor);
			lyricGlowColor = getColor(json, "lyricGlowColor", skyGlowColor);
			lyricOutlineColor = getColor(json, "lyricOutlineColor", lyricOutlineColor);
			separateGlowColor = getBoolean(json, "separateGlowColor", separateGlowColor);
			separateHighlightColor = getBoolean(json, "separateHighlightColor", separateHighlightColor);
			// Very old files may lack this flag; infer it only in that case. An
			// explicit false must remain false even if a dormant Sky color differs.
			boolean defaultSeparateSky = (skyActiveColor != lyricPrimaryColor);
			separateSkyColors = getBoolean(json, "separateSkyColors", defaultSeparateSky);
			// Keep a saved HUD title color; old files without one use the shared color.
			if (!json.has("hudActiveColor") || json.get("hudActiveColor").isJsonNull()) {
				hudActiveColor = lyricPrimaryColor;
			}

			autoPaceUnsyncedLyrics = getBoolean(json, "autoPaceUnsyncedLyrics", autoPaceUnsyncedLyrics);
			preferWordTiming = getBoolean(json, "preferWordTiming", true);
			enableLrclib = getBoolean(json, "enableLrclib", enableLrclib);
			enableUnison = getBoolean(json, "enableUnison", true);
			enableAmll = getBoolean(json, "enableAmll", true);
			enableBetterLyrics = getBoolean(json, "enableBetterLyrics", enableBetterLyrics);
			enableNetease = getBoolean(json, "enableNetease", enableNetease);
			enableKugou = getBoolean(json, "enableKugou", enableKugou);
			enableLrcmux = getBoolean(json, "enableLrcmux", enableLrcmux);
			enableMusixmatch = getBoolean(json, "enableMusixmatch", enableMusixmatch);
			enableLyricsOvh = getBoolean(json, "enableLyricsOvh", enableLyricsOvh);
			if (json.has("providerPriority") && json.get("providerPriority").isJsonArray()) {
				List<String> loadedOrder = new ArrayList<>();
				for (JsonElement elem : json.getAsJsonArray("providerPriority")) {
					if (elem != null && elem.isJsonPrimitive()) {
						String id = elem.getAsString().trim().toLowerCase(Locale.ROOT);
						if (DEFAULT_PROVIDER_PRIORITY.contains(id) && !loadedOrder.contains(id)) {
							loadedOrder.add(id);
						}
					}
				}
				for (String defId : DEFAULT_PROVIDER_PRIORITY) {
					if (!loadedOrder.contains(defId)) {
						loadedOrder.add(defId);
					}
				}
				providerPriority = loadedOrder;
			} else {
				providerPriority = new ArrayList<>(DEFAULT_PROVIDER_PRIORITY);
			}

			String mxKey = getString(json, "musixmatchApiKey", "");
			musixmatchApiKey = mxKey == null ? "" : mxKey.trim();
			String blKey = getString(json, "betterLyricsApiKey", "");
			betterLyricsApiKey = blKey == null ? "" : blKey.trim();
		} catch (Exception e) {
			LOGGER.warn("Could not apply LT5Lyrics settings: {}", e.toString());
		}
	}

	public static void save() {
		try {
			saveToFile(configFile());
		} catch (Throwable ignored) {
		}
	}

	public static void saveToFile(Path file) {
		try {
			if (file.getParent() != null) {
				Files.createDirectories(file.getParent());
			}
			JsonObject json = existingSettingsForSave(file);

			json.addProperty("advancedSettingsMode", advancedSettingsMode);
			json.addProperty("lastStudioTab", Math.clamp(lastStudioTab, 0, 4));
			json.addProperty("lyricsEnabled", lyricsEnabled);

			json.addProperty("worldText", worldText);
			json.addProperty("btp", btp);
			json.addProperty("throughBlocks", throughBlocks);
			json.addProperty("follow", follow);
			json.addProperty("locked", locked);
			json.addProperty("hideBossbar", hideBossbar);
			json.addProperty("hologramBackground", hologramBackground);
			json.addProperty("hologramBgOpacity", hologramBgOpacity);
			json.addProperty("hologramShadow", hologramShadow);
			json.addProperty("hologramScale", hologramScale);
			json.addProperty("hologramBillboard", hologramBillboard.name());
			json.addProperty("normalDistance", normalDistance);
			json.addProperty("normalOffset", normalOffset);
			json.addProperty("normalSide", normalSide.name());
			json.addProperty("normalCurveEnabled", normalCurveEnabled);
			json.addProperty("normalCurve", normalCurve);
			json.addProperty("normalWordsPerLine", normalWordsPerLine);
			json.addProperty("normalLineSpacing", normalLineSpacing);
			json.addProperty("normalTextSize", normalTextSize);
			json.addProperty("normalHideBehind", normalHideBehind);

			json.addProperty("fontPreset", fontPreset.name());
			json.addProperty("customFontName", customFontName == null ? "" : customFontName);
			json.addProperty("lyricAnimation", lyricAnimation.name());
			json.addProperty("animationSpeed", animationSpeed);
			json.addProperty("lyricSmoothness", lyricSmoothness);
			json.addProperty("lyricTextScale", lyricTextScale);
			json.addProperty("lyricLineSpacing", lyricLineSpacing);
			json.addProperty("lyricGlowEnabled", lyricGlowEnabled);
			json.addProperty("lyricGlowIntensity", lyricGlowIntensity);
			json.addProperty("lyricOutlineThickness", lyricOutlineThickness);
			json.addProperty("showTranslations", showTranslations);

			json.addProperty("showHud", showHud);
			json.addProperty("posX", posX);
			json.addProperty("posY", posY);
			json.addProperty("hudOpacity", hudOpacity);
			json.addProperty("hudBeatPulse", hudBeatPulse);
			json.addProperty("trackToast", trackToast);
			json.addProperty("visualizerStyle", visualizerStyle.name());
			json.addProperty("clockMode", clockMode.name());

			json.addProperty("skyLyrics", skyLyrics);
			json.addProperty("skyThroughBlocks", skyThroughBlocks);
			json.addProperty("skyCrosshairFollow", skyCrosshairFollow);
			json.addProperty("skyBoundaryRadius", skyBoundaryRadius);
			json.addProperty("skyHeight", skyHeight);
			json.addProperty("skyTextSize", skyTextSize);
			json.addProperty("skyDriftSpeed", skyDriftSpeed);
			json.addProperty("skyGlow", skyGlow);
			json.addProperty("skyAngleDegrees", skyAngleDegrees);
			json.addProperty("skyCurve", skyCurve);
			json.addProperty("skyContextLines", skyContextLines);
			json.addProperty("skySongHeader", skySongHeader);
			json.addProperty("skyBeatPulse", skyBeatPulse);

			json.addProperty("lyricPrimaryColor", ColorUtil.toHex(lyricPrimaryColor));
			json.addProperty("lyricHighlightColor", ColorUtil.toHex(lyricHighlightColor));
			json.addProperty("lyricTranslationColor", ColorUtil.toHex(lyricTranslationColor));
			json.addProperty("lyricGlowColor", ColorUtil.toHex(lyricGlowColor));
			json.addProperty("lyricOutlineColor", ColorUtil.toHex(lyricOutlineColor));
			json.addProperty("separateGlowColor", separateGlowColor);
			json.addProperty("separateHighlightColor", separateHighlightColor);
			json.addProperty("separateSkyColors", separateSkyColors);

			json.addProperty("hudActiveColor", ColorUtil.toHex(hudActiveColor));
			json.addProperty("hudUpcomingColor", ColorUtil.toHex(hudUpcomingColor));
			json.addProperty("autoAccentColor", autoAccentColor);
			json.addProperty("hudAccentColor", ColorUtil.toHex(hudAccentColor));
			json.addProperty("hudBgColor", ColorUtil.toHex(hudBgColor));
			json.addProperty("skyActiveColor", ColorUtil.toHex(skyActiveColor));
			json.addProperty("skyGlowColor", ColorUtil.toHex(skyGlowColor));

			json.addProperty("autoPaceUnsyncedLyrics", autoPaceUnsyncedLyrics);
			json.addProperty("preferWordTiming", preferWordTiming);
			json.addProperty("enableLrclib", enableLrclib);
			json.addProperty("enableUnison", enableUnison);
			json.addProperty("enableAmll", enableAmll);
			json.addProperty("enableBetterLyrics", enableBetterLyrics);
			json.addProperty("enableNetease", enableNetease);
			json.addProperty("enableKugou", enableKugou);
			json.addProperty("enableLrcmux", enableLrcmux);
			json.addProperty("enableMusixmatch", enableMusixmatch);
			json.addProperty("enableLyricsOvh", enableLyricsOvh);
			JsonArray priorityJson = new JsonArray();
			for (String id : normalizedProviderPriority()) {
				priorityJson.add(id);
			}
			json.add("providerPriority", priorityJson);
			json.addProperty("musixmatchApiKey", musixmatchApiKey == null ? "" : musixmatchApiKey);
			json.addProperty("betterLyricsApiKey", betterLyricsApiKey == null ? "" : betterLyricsApiKey);

			SettingsHistory.write(file, GSON.toJson(json));
		} catch (IOException e) {
			LOGGER.warn("Could not save LT5Lyrics settings: {}", e.toString());
		}
	}

	/** Preserve unknown/future keys; keep a copy before replacing malformed JSON. */
	private static JsonObject existingSettingsForSave(Path file) throws IOException {
		if (!Files.exists(file)) return new JsonObject();
		String original = Files.readString(file, StandardCharsets.UTF_8);
		if (original.isBlank()) return new JsonObject();
		try {
			JsonElement parsed = JsonParser.parseString(original);
			if (parsed.isJsonObject()) return parsed.getAsJsonObject();
		} catch (RuntimeException ignored) {
			// Back up below, before any replacement.
		}
		Path backup = file.resolveSibling(file.getFileName() + ".invalid-backup");
		for (int n = 1; Files.exists(backup); n++) {
			backup = file.resolveSibling(file.getFileName() + ".invalid-backup-" + n);
		}
		Files.copy(file, backup); // Failure propagates: never overwrite if the backup failed.
		LOGGER.warn("Backed up invalid LT5Lyrics settings to {} before saving.", backup);
		return new JsonObject();
	}

	public static boolean isMoved() {
		return posX > 0.0F && posY > 0.0F;
	}

	public static void resetPosition() {
		posX = -1.0F;
		posY = -1.0F;
		save();
	}

	public static void resetSkyDefaults() {
		skyThroughBlocks = false;
		skyBoundaryRadius = DEFAULT_SKY_BOUNDARY_RADIUS;
		skyHeight = DEFAULT_SKY_HEIGHT;
		skyTextSize = DEFAULT_SKY_TEXT_SIZE;
		skyDriftSpeed = DEFAULT_SKY_DRIFT_SPEED;
		skyGlow = DEFAULT_SKY_GLOW;
		skyAngleDegrees = DEFAULT_SKY_ANGLE;
		skyCurve = DEFAULT_SKY_CURVE;
		skyCrosshairFollow = false;
		skyContextLines = true;
		skySongHeader = true;
		skyBeatPulse = true;
		save();
	}

	public static List<String> normalizedProviderPriority() {
		List<String> out = new ArrayList<>(DEFAULT_PROVIDER_PRIORITY.size());
		if (providerPriority != null) {
			for (String raw : providerPriority) {
				if (raw == null) continue;
				String id = raw.trim().toLowerCase(Locale.ROOT);
				if (DEFAULT_PROVIDER_PRIORITY.contains(id) && !out.contains(id)) {
					out.add(id);
				}
			}
		}
		for (String defId : DEFAULT_PROVIDER_PRIORITY) {
			if (!out.contains(defId)) {
				out.add(defId);
			}
		}
		return out;
	}

	public static void moveProviderPriority(String providerId, int delta) {
		if (providerId == null || delta == 0) return;
		List<String> order = new ArrayList<>(normalizedProviderPriority());
		String norm = providerId.trim().toLowerCase(Locale.ROOT);
		int idx = order.indexOf(norm);
		if (idx < 0) return;
		int target = Math.clamp(idx + delta, 0, order.size() - 1);
		if (target == idx) return;
		order.remove(idx);
		order.add(target, norm);
		providerPriority = order;
		save();
	}

	public static String providerDisplayName(String id) {
		if (id == null) return "Unknown";
		return switch (id.trim().toLowerCase(Locale.ROOT)) {
			case "lrclib" -> "LRCLIB";
			case "unison" -> "Unison";
			case "amll" -> "AMLL TTML DB";
			case "betterlyrics" -> "BetterLyrics";
			case "netease" -> "NetEase";
			case "kugou" -> "KuGou";
			case "lrcmux" -> "Lrcmux";
			case "musixmatch" -> "Musixmatch";
			case "lyricsovh" -> "Lyrics.ovh";
			default -> id;
		};
	}

	public static String providerSubtitle(String id) {
		if (id == null) return "";
		return switch (id.trim().toLowerCase(Locale.ROOT)) {
			case "lrclib" -> "Global timed LRC database (free, no key)";
			case "unison" -> "Community word/line lyrics · unison.boidu.dev";
			case "amll" -> "Community TTML with word timings (no key)";
			case "betterlyrics" -> "Syllable & word-by-word karaoke TTML";
			case "netease" -> "Strong Asian, Khmer, CJK & bilingual translations";
			case "kugou" -> "Broad Asian & international timed lyrics";
			case "lrcmux" -> "Cached LRCLIB/KuGou aggregation (Genius excluded)";
			case "musixmatch" -> "Official REST API (optional personal key)";
			case "lyricsovh" -> "Plain-text fallback when no timed LRC exists";
			default -> "";
		};
	}

	public static boolean isProviderEnabled(String id) {
		if (id == null) return false;
		return switch (id.trim().toLowerCase(Locale.ROOT)) {
			case "lrclib" -> enableLrclib;
			case "unison" -> enableUnison;
			case "amll" -> enableAmll;
			case "betterlyrics" -> enableBetterLyrics;
			case "netease" -> enableNetease;
			case "kugou" -> enableKugou;
			case "lrcmux" -> enableLrcmux;
			case "musixmatch" -> enableMusixmatch;
			case "lyricsovh" -> enableLyricsOvh;
			default -> false;
		};
	}

	public static void setProviderEnabled(String id, boolean enabled) {
		if (id == null) return;
		switch (id.trim().toLowerCase(Locale.ROOT)) {
			case "lrclib" -> enableLrclib = enabled;
			case "unison" -> enableUnison = enabled;
			case "amll" -> enableAmll = enabled;
			case "betterlyrics" -> enableBetterLyrics = enabled;
			case "netease" -> enableNetease = enabled;
			case "kugou" -> enableKugou = enabled;
			case "lrcmux" -> enableLrcmux = enabled;
			case "musixmatch" -> enableMusixmatch = enabled;
			case "lyricsovh" -> enableLyricsOvh = enabled;
			default -> {
			}
		}
	}

	public static void resetAppearanceCategory() {
		resetColorsNoSave();
		lyricTextScale = DEFAULT_LYRIC_TEXT_SCALE;
		lyricGlowEnabled = true;
		lyricGlowIntensity = DEFAULT_GLOW_INTENSITY;
		lyricOutlineThickness = DEFAULT_OUTLINE_THICKNESS;
		hologramShadow = true;
		save();
	}

	public static void resetNormalDefaults() {
		normalDistance = DEFAULT_NORMAL_DISTANCE;
		normalOffset = DEFAULT_NORMAL_OFFSET;
		normalSide = NormalSide.RIGHT;
		normalCurveEnabled = false;
		normalCurve = DEFAULT_NORMAL_CURVE;
		normalWordsPerLine = DEFAULT_NORMAL_WORDS;
		normalLineSpacing = DEFAULT_NORMAL_SPACING;
		normalTextSize = DEFAULT_NORMAL_SIZE;
		normalHideBehind = true;
	}

	public static void resetLyricsCategory() {
		lyricsEnabled = true;
		resetNormalDefaults();
		worldText = true;
		skyLyrics = false;
		showHud = true;
		fontPreset = FontPreset.INTER_MODERN;
		customFontName = "";
		lyricAnimation = LyricAnimation.RISE_FLOW;
		animationSpeed = DEFAULT_ANIM_SPEED;
		lyricSmoothness = DEFAULT_SMOOTHNESS;
		lyricLineSpacing = DEFAULT_LINE_SPACING;
		showTranslations = true;
		save();
	}

	public static void resetAdvancedCategory() {
		separateGlowColor = false;
		separateHighlightColor = false;
		separateSkyColors = false;
		hologramBackground = true;
		hologramBgOpacity = DEFAULT_HOLOGRAM_BG_OPACITY;
		hologramScale = DEFAULT_HOLOGRAM_SCALE;
		hologramBillboard = HologramBillboard.SMART_BTP;
		throughBlocks = false;
		follow = false;
		btp = true;
		locked = false;
		hudOpacity = DEFAULT_HUD_OPACITY;
		posX = -1.0F;
		posY = -1.0F;
		visualizerStyle = VisualizerStyle.WAVES;
		clockMode = ClockMode.CLOCK_24H;
		hudBeatPulse = true;
		trackToast = true;
		hideBossbar = false;
		save();
	}

	public static void resetAllSettingsToFactory() {
		advancedSettingsMode = false;
		lastStudioTab = 0;
		musixmatchApiKey = "";
		betterLyricsApiKey = "";
		hologramShadow = true;
		resetColorsNoSave();
		worldText = true;
		skyLyrics = false;
		showHud = true;
		resetLyricsCategory();
		resetSkyDefaults();
		resetSourceDefaults();
		resetAdvancedCategory();
		lyricTextScale = DEFAULT_LYRIC_TEXT_SCALE;
		lyricGlowEnabled = true;
		lyricGlowIntensity = DEFAULT_GLOW_INTENSITY;
		lyricOutlineThickness = DEFAULT_OUTLINE_THICKNESS;
		save();
	}

	public static void resetStyleDefaults() {
		fontPreset = FontPreset.INTER_MODERN;
		customFontName = "";
		lyricAnimation = LyricAnimation.RISE_FLOW;
		animationSpeed = DEFAULT_ANIM_SPEED;
		lyricSmoothness = DEFAULT_SMOOTHNESS;
		lyricTextScale = DEFAULT_LYRIC_TEXT_SCALE;
		lyricLineSpacing = DEFAULT_LINE_SPACING;
		lyricGlowEnabled = true;
		lyricGlowIntensity = DEFAULT_GLOW_INTENSITY;
		lyricOutlineThickness = DEFAULT_OUTLINE_THICKNESS;
		showTranslations = true;
		hologramBackground = true;
		hologramBgOpacity = DEFAULT_HOLOGRAM_BG_OPACITY;
		hologramShadow = true;
		hologramScale = DEFAULT_HOLOGRAM_SCALE;
		hologramBillboard = HologramBillboard.SMART_BTP;
		save();
	}

	/**
	 * Restores visual appearance (colors, font, size, glow, outline, animation) to defaults
	 * WITHOUT erasing music sources, API keys, lyric sync delays, display mode, or HUD position.
	 */
	public static void restoreAppearanceDefaults() {
		resetColorsNoSave();
		fontPreset = FontPreset.INTER_MODERN;
		customFontName = "";
		lyricAnimation = LyricAnimation.RISE_FLOW;
		animationSpeed = DEFAULT_ANIM_SPEED;
		lyricSmoothness = DEFAULT_SMOOTHNESS;
		lyricTextScale = DEFAULT_LYRIC_TEXT_SCALE;
		lyricLineSpacing = DEFAULT_LINE_SPACING;
		lyricGlowEnabled = true;
		lyricGlowIntensity = DEFAULT_GLOW_INTENSITY;
		lyricOutlineThickness = DEFAULT_OUTLINE_THICKNESS;
		skyGlow = DEFAULT_SKY_GLOW;
		hologramScale = DEFAULT_HOLOGRAM_SCALE;
		hologramBackground = true;
		hologramBgOpacity = DEFAULT_HOLOGRAM_BG_OPACITY;
		hologramShadow = true;
		hudOpacity = DEFAULT_HUD_OPACITY;
		save();
	}

	private static void resetColorsNoSave() {
		lyricPrimaryColor = DEFAULT_LYRIC_PRIMARY_COLOR;
		lyricHighlightColor = DEFAULT_LYRIC_HIGHLIGHT_COLOR;
		lyricTranslationColor = DEFAULT_LYRIC_TRANSLATION_COLOR;
		lyricGlowColor = DEFAULT_LYRIC_GLOW_COLOR;
		lyricOutlineColor = DEFAULT_LYRIC_OUTLINE_COLOR;
		separateGlowColor = false;
		separateHighlightColor = false;
		separateSkyColors = false;

		hudActiveColor = DEFAULT_HUD_ACTIVE_COLOR;
		hudUpcomingColor = DEFAULT_HUD_UPCOMING_COLOR;
		autoAccentColor = true;
		hudAccentColor = DEFAULT_HUD_ACCENT_COLOR;
		hudBgColor = DEFAULT_HUD_BG_COLOR;
		skyActiveColor = DEFAULT_SKY_ACTIVE_COLOR;
		skyGlowColor = DEFAULT_SKY_GLOW_COLOR;
	}

	public static void resetColors() {
		resetColorsNoSave();
		save();
	}

	public static void resetSourceDefaults() {
		autoPaceUnsyncedLyrics = true;
		preferWordTiming = true;
		enableLrclib = true;
		enableUnison = true;
		enableAmll = true;
		enableBetterLyrics = true;
		enableNetease = true;
		enableKugou = true;
		enableLrcmux = true;
		enableMusixmatch = true;
		enableLyricsOvh = true;
		providerPriority = new ArrayList<>(DEFAULT_PROVIDER_PRIORITY);
		save();
	}

	public static String compassLabel(float degrees) {
		float norm = ((degrees % 360.0F) + 360.0F) % 360.0F;
		if (norm < 22.5F || norm >= 337.5F) return String.format(Locale.ROOT, "South (%+.0f°)", degrees);
		if (norm < 67.5F) return String.format(Locale.ROOT, "SE (%+.0f°)", degrees);
		if (norm < 112.5F) return String.format(Locale.ROOT, "East (%+.0f°)", degrees);
		if (norm < 157.5F) return String.format(Locale.ROOT, "NE (%+.0f°)", degrees);
		if (norm < 202.5F) return String.format(Locale.ROOT, "North (%+.0f°)", degrees);
		if (norm < 247.5F) return String.format(Locale.ROOT, "NW (%+.0f°)", degrees);
		if (norm < 292.5F) return String.format(Locale.ROOT, "West (%+.0f°)", degrees);
		return String.format(Locale.ROOT, "SW (%+.0f°)", degrees);
	}

	public static String curveLabel(float curve) {
		int pct = Math.round(curve * 100.0F);
		if (pct <= 2) return "Flat (0%)";
		if (pct == 100) return "Natural (100%)";
		if (pct >= 180) return "Dome (" + pct + "%)";
		return pct + "%";
	}

	private static float clampFloat(float val, float min, float max) {
		if (!Float.isFinite(val)) return min;
		return Math.max(min, Math.min(max, val));
	}

	private static double clampDouble(double val, double min, double max) {
		if (!Double.isFinite(val)) return min;
		return Math.max(min, Math.min(max, val));
	}

	private static String getString(JsonObject json, String key, String fallback) {
		try {
			if (json.has(key) && !json.get(key).isJsonNull()) {
				return json.get(key).getAsString();
			}
		} catch (RuntimeException e) {
			LOGGER.warn("Invalid string setting '{}'; using default.", key);
		}
		return fallback;
	}

	private static boolean getBoolean(JsonObject json, String key, boolean fallback) {
		try {
			if (json.has(key) && !json.get(key).isJsonNull()) {
				JsonElement value = json.get(key);
				if (value.isJsonPrimitive()) {
					String raw = value.getAsString().trim();
					if ("true".equalsIgnoreCase(raw)) return true;
					if ("false".equalsIgnoreCase(raw)) return false;
				}
				LOGGER.warn("Invalid boolean setting '{}'; using default.", key);
			}
		} catch (RuntimeException e) {
			LOGGER.warn("Invalid boolean setting '{}'; using default.", key);
		}
		return fallback;
	}

	private static double getDouble(JsonObject json, String key, double fallback) {
		try {
			if (json.has(key) && !json.get(key).isJsonNull()) {
				double value = json.get(key).getAsDouble();
				if (Double.isFinite(value)) {
					return value;
				}
			}
		} catch (RuntimeException e) {
			LOGGER.warn("Invalid numeric setting '{}'; using default.", key);
		}
		return fallback;
	}

	private static int getColor(JsonObject json, String key, int fallback) {
		try {
			if (json.has(key) && !json.get(key).isJsonNull()) {
				JsonElement elem = json.get(key);
				if (elem.isJsonPrimitive()) {
					if (elem.getAsJsonPrimitive().isNumber()) {
						return ColorUtil.opaque(elem.getAsInt());
					}
					return ColorUtil.parseHexOrDefault(elem.getAsString(), fallback);
				}
			}
		} catch (RuntimeException e) {
			LOGGER.warn("Invalid color setting '{}'; using default.", key);
		}
		return ColorUtil.opaque(fallback);
	}
}
