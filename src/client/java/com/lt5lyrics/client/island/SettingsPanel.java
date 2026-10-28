package com.lt5lyrics.client.island;

import org.lwjgl.sdl.SDLKeycode;
import com.mojang.blaze3d.platform.InputConstants;
import com.lt5lyrics.client.island.font.Font;
import com.lt5lyrics.client.island.font.Fonts;
import com.lt5lyrics.client.island.render.IslandRender;
import com.lt5lyrics.client.music.LyricsFetcher;
import com.lt5lyrics.client.music.MusicTracker;
import com.lt5lyrics.client.music.TrackMetadata;
import dev.redstones.mediaplayerinfo.IMediaSession;
import dev.redstones.mediaplayerinfo.MediaInfo;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;


/**
 * LT5Lyrics 1.9 settings: a separate beginner-friendly Simple view and the
 * complete searchable Advanced Studio, both backed by the same configuration.
 */
public class SettingsPanel {
	static final int BG_STUDIO = 0xF20D1017;
	static final int BG_CARD = 0xCC161B26;
	static final int BG_PREVIEW = 0xE6080B11;
	static final int STROKE_SUBTLE = 0x26FFFFFF;
	static final int TEXT_MAIN = 0xFFF4F6FB;
	static final int TEXT_SUB = 0xFFAAB4C8;
	static final int TEXT_DIM = 0xFF788299;

	private static final String[] TAB_NAMES = {
		"Appearance", "Lyrics", "Sky Lyrics", "Music & Lyrics Sources", "Advanced"
	};

	private record SampleLyric(String label, String text, String translation) {}

	private static final SampleLyric[] SAMPLES = {
		new SampleLyric("EN / JP", "Dancing underneath the starlit sky tonight", "今夜、星明かりの空の下で踊ろう"),
		new SampleLyric("Khmer", "អូនស្រឡាញ់បងជារៀងរហូត", "Loving you forever under the moonlight"),
		new SampleLyric("CN / EN", "这一路上走走停停 顺着少年漂流的痕迹", "Walking and pausing along youth's drifting trail"),
		new SampleLyric("KR / EN", "밤하늘의 별을 따서 너에게 줄래", "I'll pluck the stars from the night sky for you")
	};

	public enum ColorChannel {
		UNIFIED("All Lyrics", "Updates world, sky, and HUD lyrics together"),
		PRIMARY("Primary", "Main sung lyric text color"),
		HIGHLIGHT("Highlight", "Active karaoke word sweep color"),
		TRANSLATION("Translation", "Translated or romanized subtitle color"),
		GLOW("Glow Halo", "Soft outer glow halo color"),
		OUTLINE("Outline", "Crisp letter border color"),
		ACCENT("HUD Accent", "Music bar buttons & visualizer color");

		public final String label;
		public final String hint;

		ColorChannel(String label, String hint) {
			this.label = label;
			this.hint = hint;
		}
	}

	public enum EditTarget {
		NONE,
		SEARCH,
		HEX,
		RGB,
		SKY_RADIUS,
		MUSIXMATCH_KEY,
		BETTERLYRICS_KEY
	}

	public interface HostCallbacks {
		void onOpenMapper();
		void onStartDragHud();
		void onOpenFullScreen();
		void onClosePanel();
	}

	private record SearchEntry(String title, String keywords, int tab, String target, boolean advanced) {}

	// Search results navigate to real controls; nothing here is a fake setting.
	private static final List<SearchEntry> SEARCH_INDEX = List.of(
		new SearchEntry("Lyrics Enabled", "hide show lyrics on off visibility", 1, "Lyrics Enabled", false),
		new SearchEntry("Lyrics Color / Color Wheel", "colour hue brightness hex rgb", 0, "Lyric Color", false),
		new SearchEntry("Quick Color Themes", "palette preset color", 0, "Quick Color Themes", false),
		new SearchEntry("Text Size", "font scale smaller larger", 0, "Text Size (Smaller ⟷ Larger)", false),
		new SearchEntry("Text Glow", "halo brightness light", 0, "Text Glow — Make lyrics glow", false),
		new SearchEntry("Restore Appearance Defaults", "reset color style", 0, "Restore Appearance Defaults", false),
		new SearchEntry("Lyric Display Style", "hud world 3d sky mode", 1, "Lyric Display", false),
		new SearchEntry("Top Music Bar", "hud island", 1, "Top Music Bar (Dynamic Island)", false),
		new SearchEntry("Show Translations", "subtitles foreign romanization", 1, "Translation — Show translations", false),
		new SearchEntry("Animation Style", "fade rise cascade", 1, "Animation Style", false),
		new SearchEntry("Animation Speed", "timing motion speed", 1, "Animation Speed", false),
		new SearchEntry("Song Lyric Timing", "delay offset sync", 1, "Song Lyric Timing", false),
		new SearchEntry("Normal 3D Distance", "world forward spawn blocks", 1, "Normal 3D Distance", false),
		new SearchEntry("Normal 3D Offset", "left right position side blocks", 1, "Normal 3D Offset", false),
		new SearchEntry("Normal 3D Side", "left right alternate random spawn", 1, "Normal 3D Side", false),
		new SearchEntry("Normal 3D Text Size", "world physical scale", 1, "Normal 3D Text Size", false),
		new SearchEntry("Normal 3D Words Per Line", "word wrap length", 1, "Normal 3D Words Per Line", false),
		new SearchEntry("Normal 3D Line Spacing", "wrapped translation gap", 1, "Normal 3D Line Spacing", false),
		new SearchEntry("World Background Opacity", "hologram backdrop plate alpha", 1, "World Background Opacity", false),
		new SearchEntry("Normal 3D Curvature", "bend curved arc", 1, "Normal 3D Curvature", false),
		new SearchEntry("Hide Lyrics Behind You", "normal camera view visibility", 1, "Hide Lyrics Behind You", false),
		new SearchEntry("Follow Camera Gaze", "normal fixed moving anchors", 1, "Follow Camera Gaze", false),
		new SearchEntry("Follow Look Direction", "sky camera gaze view yaw pitch follow", 2, "Follow Look Direction", false),
		new SearchEntry("Sky Lyrics Distance", "radius boundary horizon far near blocks", 2, "Sky Lyrics Distance", false),
		new SearchEntry("Sky Lyrics Text Size", "font independent scale smaller larger", 2, "Sky Lyrics Text Size", false),
		new SearchEntry("Sky Height", "elevation altitude", 2, "Sky Height", false),
		new SearchEntry("Show Previous & Next Lines", "context nearby upcoming", 2, "Show Previous & Next Lines", false),
		new SearchEntry("Show Song Header", "song title artist header sky", 2, "Show Song Header", false),
		new SearchEntry("Sky Arc Curvature", "curve shape", 2, "Sky Arc Curvature", true),
		new SearchEntry("Sky Block Occlusion", "walls mountains roofs through", 2, "Visible Through Mountains & Roofs", true),
		new SearchEntry("Music Status and Retry", "searching no match unavailable", 3, "Music Status", false),
		new SearchEntry("Provider Priority", "source reorder lyrics websites", 3, "Provider Priority", false),
		new SearchEntry("Prefer Word Timings", "karaoke syllable sync provider quality", 3, "Prefer Word Timings", false),
		new SearchEntry("LRCLIB", "source provider timed", 3, "LRCLIB", false),
		new SearchEntry("BetterLyrics", "source provider karaoke", 3, "BetterLyrics", false),
		new SearchEntry("NetEase", "source provider translations", 3, "NetEase", false),
		new SearchEntry("KuGou", "source provider", 3, "KuGou", false),
		new SearchEntry("Unison", "source provider community word timing", 3, "Unison", false),
		new SearchEntry("AMLL TTML DB", "source provider community word timing", 3, "AMLL TTML DB", false),
		new SearchEntry("Lrcmux", "source provider", 3, "Lrcmux", false),
		new SearchEntry("Musixmatch", "source provider key", 3, "Musixmatch", false),
		new SearchEntry("Lyrics.ovh", "source provider plain text", 3, "Lyrics.ovh", false),
		new SearchEntry("Auto-Pace Plain Text Lyrics", "estimated unsynced", 3, "Auto-Pace Plain Text Lyrics (Estimated Pace)", false),
		new SearchEntry("Separate Lyric Element Colors", "highlight translation glow outline hex rgb", 4, "Separate Lyric Element Colors", false),
		new SearchEntry("Glow Intensity", "halo strength", 4, "Glow Intensity", false),
		new SearchEntry("Outline Thickness", "stroke border", 4, "Outline Thickness", false),
		new SearchEntry("HUD Opacity", "transparency background", 4, "HUD Opacity", false),
		new SearchEntry("Line & Translation Spacing", "gap height", 4, "Line & Translation Spacing", false),
		new SearchEntry("Fonts", "typography custom", 4, "Typography & Dynamic Island HUD", false),
		new SearchEntry("API Keys", "credentials musixmatch betterlyrics", 4, "Optional Personal Provider API Keys", false),
		new SearchEntry("Reset All Settings", "factory defaults erase", 4, "Reset All Settings", false)
	);

	private record PendingConfirmation(String title, String description, Runnable action) {}
	private record FocusItem(float x, float y, float w, float h, HitZone hit, SliderBinding slider, boolean wheel) {}

	private record HitZone(float x, float y, float w, float h, String tooltip, Runnable onClick) {
		boolean contains(double mx, double my) {
			return mx >= x && mx <= x + w && my >= y && my <= y + h;
		}
	}

	private interface FloatGetter { float get(); }
	private interface FloatSetter { void set(float v); }

	private record SliderBinding(
		String id,
		float x, float y, float w, float h,
		float min, float max,
		FloatGetter getter,
		FloatSetter setter,
		String tooltip
	) {
		boolean contains(double mx, double my) {
			return mx >= x - 2.0F && mx <= x + w + 2.0F && my >= y - 3.0F && my <= y + h + 3.0F;
		}

		void applyMouse(double mx) {
			float t = (float) Math.clamp((mx - x) / Math.max(1.0F, w), 0.0, 1.0);
			setter.set(min + t * (max - min));
		}
	}

	private final MusicTracker tracker;
	private final HostCallbacks callbacks;

	private int activeTab = 0;
	private int simplePage = 0;
	private final float[] simpleScroll = new float[4];
	private final Deque<Integer> tabHistory = new ArrayDeque<>();
	private String searchQuery = "";
	private float searchScroll = 0.0F;
	private String pendingJump = null;
	private float jumpY = Float.NaN;
	private float bodyTopLast, bodyBottomLast, bodyHeightLast;
	private int bodyHitStart;
	private boolean wheelVisible;
	private PendingConfirmation confirmation;
	private final List<FocusItem> focusItems = new ArrayList<>();
	private int focusIndex = -1;
	private boolean lastStandalone;
	private boolean suppressNextSlash;
	private final float[] tabScroll = new float[TAB_NAMES.length];
	private float maxContentScroll = 0.0F;

	private ColorChannel selectedChannel = ColorChannel.UNIFIED;
	private float wheelHue = 0.0F; // Normalized [0, 1) matching ColorUtil & island_wheel.fsh
	private float wheelSat = 0.0F; // Normalized [0, 1]
	private float wheelVal = 1.0F; // Normalized [0, 1]
	private boolean draggingWheel = false;
	private float lastWheelCx, lastWheelCy, lastWheelRadius;

	private String activeSliderId = null;
	private final List<HitZone> hitZones = new ArrayList<>();
	private final List<SliderBinding> sliderBindings = new ArrayList<>();

	private IslandSettings.StyleSnapshot openingSnapshot = IslandSettings.captureStyleSnapshot();
	private boolean compareBeforeAfter = false;
	private int sampleIndex = 0;

	private EditTarget editTarget = EditTarget.NONE;
	private String editBuffer = "";
	private boolean editAllSelected;
	private boolean revealApiKeys = false;

	private String statusBanner = "";
	private long statusUntilMs = 0L;

	private int[] previewScratchCols = new int[128];

	public SettingsPanel(MusicTracker tracker, HostCallbacks callbacks) {
		this.tracker = tracker;
		this.callbacks = callbacks;
		this.activeTab = Math.clamp(IslandSettings.lastStudioTab, 0, TAB_NAMES.length - 1);
		syncWheelFromChannel();
	}

	public void onOpened() {
        if (tracker != null) {
            try { IslandSettings.save(); PlusSettings.save(); SettingsHistory.checkpoint(IslandSettings.configFile(),PlusSettings.configFile()); }
            catch (Throwable e) { org.slf4j.LoggerFactory.getLogger("lt5lyrics").warn("Could not create Studio settings backup: {}",e.toString()); }
        }
		openingSnapshot = IslandSettings.captureStyleSnapshot();
		activeTab = Math.clamp(IslandSettings.lastStudioTab, 0, TAB_NAMES.length - 1);
		simplePage = 0;
		tabHistory.clear();
		searchQuery = "";
		confirmation = null;
		focusIndex = -1;
		editTarget = EditTarget.NONE;
		draggingWheel = false;
		activeSliderId = null;
		syncWheelFromChannel();
	}

	public boolean isEditingText() {
		return editTarget != EditTarget.NONE;
	}

	private void flashStatus(String msg) {
		statusBanner = msg;
		statusUntilMs = System.currentTimeMillis() + 2600L;
	}

	private int currentChannelColor() {
		if (!IslandSettings.advancedSettingsMode && activeTab != 4) {
			return IslandSettings.lyricPrimaryColor;
		}
		return switch (selectedChannel) {
			case UNIFIED, PRIMARY -> IslandSettings.lyricPrimaryColor;
			case HIGHLIGHT -> IslandSettings.resolveHighlightColor();
			case TRANSLATION -> IslandSettings.resolveTranslationColor();
			case GLOW -> IslandSettings.resolveGlowColor();
			case OUTLINE -> IslandSettings.resolveOutlineColor();
			case ACCENT -> IslandSettings.hudAccentColor;
		};
	}

	private void applyColorToActiveChannel(int rgb) {
		int opaque = ColorUtil.opaque(rgb);
		boolean unifiedMode = (!IslandSettings.advancedSettingsMode && activeTab != 4)
			|| selectedChannel == ColorChannel.UNIFIED;

		if (unifiedMode) {
			IslandSettings.applyUnifiedLyricColor(opaque);
		} else {
			switch (selectedChannel) {
				case PRIMARY -> {
					IslandSettings.lyricPrimaryColor = opaque;
					IslandSettings.hudActiveColor = opaque;
					if (!IslandSettings.separateSkyColors) {
						IslandSettings.skyActiveColor = opaque;
					}
				}
				case HIGHLIGHT -> {
					IslandSettings.separateHighlightColor = true;
					IslandSettings.lyricHighlightColor = opaque;
				}
				case TRANSLATION -> {
					IslandSettings.lyricTranslationColor = opaque;
				}
				case GLOW -> {
					IslandSettings.separateGlowColor = true;
					IslandSettings.lyricGlowColor = opaque;
					IslandSettings.skyGlowColor = opaque;
				}
				case OUTLINE -> {
					IslandSettings.lyricOutlineColor = opaque;
				}
				case ACCENT -> {
					IslandSettings.autoAccentColor = false;
					IslandSettings.hudAccentColor = opaque;
				}
				default -> IslandSettings.applyUnifiedLyricColor(opaque);
			}
		}
	}

	public void syncWheelFromChannel() {
		float[] hsv = ColorUtil.rgbToHsv(currentChannelColor());
		wheelHue = hsv[0];
		wheelSat = hsv[1];
		wheelVal = hsv[2];
	}

	private void commitEditBuffer() {
		if (editTarget == EditTarget.NONE) return;
		String raw = editBuffer.trim();
		switch (editTarget) {
			case HEX -> {
				Integer parsed = ColorUtil.parseHexStrict(raw);
				if (parsed != null) {
					applyColorToActiveChannel(parsed);
					syncWheelFromChannel();
					IslandSettings.save();
					flashStatus("Applied HEX " + ColorUtil.toHex(parsed));
				} else {
					flashStatus("Invalid HEX — use #RRGGBB (example: #FF79C6)");
				}
			}
			case RGB -> {
				Integer parsed = ColorUtil.parseRgbTriplet(raw);
				if (parsed != null) {
					applyColorToActiveChannel(parsed);
					syncWheelFromChannel();
					IslandSettings.save();
					flashStatus("Applied RGB (" + ColorUtil.toRgbString(parsed) + ")");
				} else {
					flashStatus("Invalid RGB — enter 0-255, 0-255, 0-255");
				}
			}
			case SEARCH -> searchQuery = raw;
			case SKY_RADIUS -> {
				try {
					double val = Double.parseDouble(raw.replaceAll("[^0-9.]", ""));
					IslandSettings.skyBoundaryRadius = Math.clamp(
						val, IslandSettings.MIN_SKY_BOUNDARY_RADIUS, IslandSettings.MAX_SKY_BOUNDARY_RADIUS
					);
					WorldLyrics.onSkySettingsChanged();
					IslandSettings.save();
					flashStatus("Sky Lyrics radius set to " + Math.round(IslandSettings.skyBoundaryRadius) + " blocks");
				} catch (NumberFormatException e) {
					flashStatus("Enter a radius between 80 and 1000 blocks");
				}
			}
			case MUSIXMATCH_KEY -> {
				IslandSettings.musixmatchApiKey = raw;
				if (!raw.isEmpty()) {
					IslandSettings.enableMusixmatch = true;
				}
				IslandSettings.save();
				LyricsFetcher.clearCache();
				flashStatus(raw.isEmpty() ? "Cleared Musixmatch API key" : "Saved Musixmatch API key");
			}
			case BETTERLYRICS_KEY -> {
				IslandSettings.betterLyricsApiKey = raw;
				IslandSettings.save();
				LyricsFetcher.clearCache();
				flashStatus(raw.isEmpty() ? "Cleared BetterLyrics API key" : "Saved BetterLyrics API key");
			}
			default -> {}
		}
		editTarget = EditTarget.NONE;
		editAllSelected = false;
	}

	private void beginEdit(EditTarget target, String initialValue, boolean standalone) {
		if (!standalone) {
			callbacks.onOpenFullScreen();
			return;
		}
		if (editTarget != EditTarget.NONE && editTarget != target) {
			commitEditBuffer();
		}
		editTarget = target;
		editAllSelected = false;
		if (target == EditTarget.SEARCH) {
			editBuffer = searchQuery;
		} else if (target == EditTarget.MUSIXMATCH_KEY) {
			editBuffer = IslandSettings.musixmatchApiKey == null ? "" : IslandSettings.musixmatchApiKey;
		} else if (target == EditTarget.BETTERLYRICS_KEY) {
			editBuffer = IslandSettings.betterLyricsApiKey == null ? "" : IslandSettings.betterLyricsApiKey;
		} else {
			editBuffer = initialValue == null ? "" : initialValue;
		}
	}

	public boolean handleKeyPress(int keyCode, int modifiers, boolean controlDown) {
		if (confirmation != null) {
			if (keyCode == InputConstants.KEY_ESCAPE) { confirmation = null; return true; }
			if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
				confirmPending(); return true;
			}
			return true;
		}
		if (controlDown && keyCode == InputConstants.KEY_F) {
			if (!IslandSettings.advancedSettingsMode) return false;
			if (!lastStandalone) { callbacks.onOpenFullScreen(); return true; }
			if (editTarget != EditTarget.NONE && editTarget != EditTarget.SEARCH) commitEditBuffer();
			beginEdit(EditTarget.SEARCH, searchQuery, true); return true;
		}
		if (controlDown && keyCode == InputConstants.KEY_TAB) {
			if (editTarget != EditTarget.NONE) commitEditBuffer();
			int dir = (modifiers & SDLKeycode.SDL_KMOD_SHIFT) != 0 ? -1 : 1;
			if (IslandSettings.advancedSettingsMode) selectTab(Math.floorMod(activeTab + dir, TAB_NAMES.length), true);
			else nextSimplePage(dir);
			return true;
		}
		if (editTarget != EditTarget.NONE) {
			if (controlDown && keyCode == InputConstants.KEY_A) {
				editAllSelected = true;
				return true;
			}
			if (keyCode == InputConstants.KEY_ESCAPE) {
				if (editTarget == EditTarget.SEARCH) searchQuery = editBuffer;
				editTarget = EditTarget.NONE; return true;
			}
			if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER || keyCode == InputConstants.KEY_TAB) {
				commitEditBuffer(); return true;
			}
			if (keyCode == InputConstants.KEY_BACKSPACE) {
				if (controlDown || editAllSelected) editBuffer = "";
				else if (!editBuffer.isEmpty()) editBuffer = editBuffer.substring(0, editBuffer.offsetByCodePoints(editBuffer.length(), -1));
				editAllSelected = false;
				if (editTarget == EditTarget.SEARCH) { searchQuery = editBuffer; searchScroll = 0.0F; }
				return true;
			}
			if (controlDown && keyCode == InputConstants.KEY_V) {
				String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
				if (clip != null && !clip.isBlank()) {
					String clean = clip.replace("\r", "").replace("\n", "").trim();
					editBuffer = (editAllSelected || editTarget == EditTarget.HEX || editTarget == EditTarget.RGB || editTarget == EditTarget.SKY_RADIUS)
						? clean : (editBuffer + clean);
					editAllSelected = false;
					if (editBuffer.length() > 96) editBuffer = editBuffer.substring(0, 96);
					if (editTarget == EditTarget.SEARCH) { searchQuery = editBuffer; searchScroll = 0.0F; }
				}
				return true;
			}
			if (controlDown && keyCode == InputConstants.KEY_C) {
				Minecraft.getInstance().keyboardHandler.setClipboard(editBuffer); flashStatus("Copied to clipboard"); return true;
			}
			return true;
		}
		if (keyCode == InputConstants.KEY_SLASH && !controlDown) {
			if (!IslandSettings.advancedSettingsMode) return false;
			if (!lastStandalone) callbacks.onOpenFullScreen();
			else { beginEdit(EditTarget.SEARCH, searchQuery, true); suppressNextSlash = true; }
			return true;
		}
		if (keyCode == InputConstants.KEY_ESCAPE && !searchQuery.isEmpty()) {
			searchQuery = ""; searchScroll = 0.0F; return true;
		}
		if (keyCode == InputConstants.KEY_TAB) {
			if (!focusItems.isEmpty()) {
				int dir = (modifiers & SDLKeycode.SDL_KMOD_SHIFT) != 0 ? -1 : 1;
				focusIndex = focusIndex < 0 ? (dir > 0 ? 0 : focusItems.size() - 1)
					: Math.floorMod(focusIndex + dir, focusItems.size());
			}
			return true;
		}
		FocusItem focused = focusIndex >= 0 && focusIndex < focusItems.size() ? focusItems.get(focusIndex) : null;
		if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER || keyCode == InputConstants.KEY_SPACE) {
			if (focused != null && focused.hit() != null) focused.hit().onClick().run();
			return focused != null;
		}
		if (keyCode == InputConstants.KEY_LEFT || keyCode == InputConstants.KEY_RIGHT) {
			int dir = keyCode == InputConstants.KEY_RIGHT ? 1 : -1;
			if (focused != null && focused.slider() != null) {
				SliderBinding sb = focused.slider();
				sb.setter().set(Math.clamp(sb.getter().get() + dir * (sb.max() - sb.min()) / 40.0F, sb.min(), sb.max()));
				IslandSettings.save();
			} else if (focused != null && focused.wheel()) {
				wheelHue = (wheelHue + dir * 0.025F + 1.0F) % 1.0F;
				applyColorToActiveChannel(ColorUtil.hsvToRgb(wheelHue, wheelSat, wheelVal)); IslandSettings.save();
			} else if (IslandSettings.advancedSettingsMode) selectTab(Math.floorMod(activeTab + dir, TAB_NAMES.length), true);
			else nextSimplePage(dir);
			return true;
		}
		if (keyCode == InputConstants.KEY_UP || keyCode == InputConstants.KEY_DOWN || keyCode == InputConstants.KEY_PAGEUP || keyCode == InputConstants.KEY_PAGEDOWN) {
			int dir = (keyCode == InputConstants.KEY_DOWN || keyCode == InputConstants.KEY_PAGEDOWN) ? 1 : -1;
			if (focused != null && focused.wheel() && (keyCode == InputConstants.KEY_UP || keyCode == InputConstants.KEY_DOWN)) {
				wheelSat = Math.clamp(wheelSat - dir * 0.05F, 0.0F, 1.0F);
				applyColorToActiveChannel(ColorUtil.hsvToRgb(wheelHue, wheelSat, wheelVal)); IslandSettings.save();
			} else {
				float step = (keyCode == InputConstants.KEY_PAGEUP || keyCode == InputConstants.KEY_PAGEDOWN)
					? Math.max(25.0F, bodyHeightLast * 0.80F) : 18.0F;
				scrollBy(dir * step); focusIndex = -1;
			}
			return true;
		}
		return false;
	}

	public boolean handleCharTyped(String typed) {
		if (editTarget == EditTarget.NONE || typed == null || typed.isEmpty()) {
			return false;
		}
		if (suppressNextSlash) {
			suppressNextSlash = false;
			if (editTarget == EditTarget.SEARCH && typed.equals("/")) return true;
		}
		if (editAllSelected) {
			editBuffer = "";
			editAllSelected = false;
		}
		for (int i = 0; i < typed.length(); i++) {
			char c = typed.charAt(i);
			if (c < 32 || c == 127) continue;
			int maxLen = switch (editTarget) {
				case HEX -> 7;
				case RGB -> 15;
				case SKY_RADIUS -> 5;
				default -> 96;
			};
			if (editBuffer.length() < maxLen) {
				editBuffer += c;
			}
		}
		if (editTarget == EditTarget.SEARCH) { searchQuery = editBuffer; searchScroll = 0.0F; }
		return true;
	}

	// =========================================================================
	// Main Studio Render
	// =========================================================================

	public void render(
		float x,
		float y,
		float w,
		float h,
		float baseAlpha,
		float openFactor,
		double mouseX,
		double mouseY,
		boolean standalone
	) {
		hitZones.clear();
		sliderBindings.clear();
		wheelVisible = false;
		lastStandalone = standalone;

		float alpha = Math.clamp(baseAlpha * openFactor, 0.0F, 255.0F);
		if (alpha <= 4.0F) return;

		int accent = IslandSettings.resolveAccentColor(tracker.getMediaColor());

		// Outer studio glass card
		if (IslandSettings.hudOpacity > 0.35F) {
			IslandRender.drawBlur(x, y, w, h, 18.0F * openFactor, 2.0F, 10.0F, 10.0F, 10.0F, 10.0F, col(0xFFFFFFFF, alpha));
		}
		IslandRender.drawRoundedRect(x - 0.8F, y - 0.8F, w + 1.6F, h + 1.6F, 10.5F, 10.5F, 10.5F, 10.5F, col(STROKE_SUBTLE, alpha * 0.25F));
		IslandRender.drawRoundedRect(x, y, w, h, 10.0F, 10.0F, 10.0F, 10.0F, col(BG_STUDIO, alpha * 0.96F));

		IslandRender.pushScissor(x, y, w, h);

		// 1. Header Bar (Simple vs Advanced Mode Switch + Title + Close)
		float curY = renderHeader(x, y, w, alpha, accent, mouseX, mouseY, standalone);
		if (!IslandSettings.advancedSettingsMode) {
			renderSimple(x, y, w, h, curY, alpha, accent, mouseX, mouseY);
			IslandRender.popScissor();
			return;
		}

		// 2. Always-Visible Live Preview Panel (Sample Fallback + Before/After Split)
		float previewH = h >= 245.0F ? 50.0F : (h >= 185.0F ? 39.0F : 30.0F);
		curY = renderLivePreview(x + 8.0F, curY, w - 16.0F, previewH, alpha, accent, mouseX, mouseY);

		// Search is shared across all categories; Back returns to the previous category.
		curY = renderSearchBar(x + 8.0F, curY + 3.0F, w - 16.0F, alpha, accent, mouseX, mouseY, standalone);
		curY = renderCategoryTabs(x + 8.0F, curY + 2.5F, w - 16.0F, alpha, accent, mouseX, mouseY);

		// 4. Scrollable Category Body
		float footerH = 15.0F;
		float bodyTop = curY + 3.0F;
		float bodyH = Math.max(40.0F, (y + h - footerH) - bodyTop - 2.0F);
		float contentX = x + 8.0F;
		float contentW = w - 18.0F;

		bodyTopLast = bodyTop; bodyBottomLast = bodyTop + bodyH; bodyHeightLast = bodyH;
		bodyHitStart = hitZones.size();
		IslandRender.pushScissor(x + 4.0F, bodyTop, w - 8.0F, bodyH);
		float scroll = searchQuery.isBlank() ? tabScroll[activeTab] : searchScroll;
		float startY = bodyTop - scroll;
		jumpY = Float.NaN;
		float contentStart = startY;
		if (searchQuery.isBlank()) {
			IslandRender.drawText(Fonts.medium(5.6F), TAB_NAMES[activeTab], contentX + 2.0F, startY + 1.0F, col(accent, alpha));
			contentStart += 9.0F;
		}
		float endY = !searchQuery.isBlank()
			? renderSearchResults(contentX, startY, contentW, alpha, accent, mouseX, mouseY, bodyTop, bodyTop + bodyH)
			: switch (activeTab) {
			case 0 -> renderAppearanceTab(contentX, contentStart, contentW, alpha, accent, mouseX, mouseY, standalone, bodyTop, bodyTop + bodyH);
			case 1 -> renderLyricsTab(contentX, contentStart, contentW, alpha, accent, mouseX, mouseY, bodyTop, bodyTop + bodyH);
			case 2 -> renderSkyTab(contentX, contentStart, contentW, alpha, accent, mouseX, mouseY, standalone, bodyTop, bodyTop + bodyH);
			case 3 -> renderMusicSourcesTab(contentX, contentStart, contentW, alpha, accent, mouseX, mouseY, bodyTop, bodyTop + bodyH);
			default -> renderAdvancedTab(contentX, contentStart, contentW, alpha, accent, mouseX, mouseY, standalone, bodyTop, bodyTop + bodyH);
		};
		if (searchQuery.isBlank()) {
			endY = renderCategoryResets(contentX, endY + 5.0F, contentW, alpha, accent, mouseX, mouseY, bodyTop, bodyTop + bodyH);
		}
		IslandRender.popScissor();

		float totalContentH = (endY - startY) + 4.0F;
		maxContentScroll = Math.max(0.0F, totalContentH - bodyH);
		if (searchQuery.isBlank()) {
			if (!Float.isNaN(jumpY) && pendingJump != null) {
				tabScroll[activeTab] = Math.clamp(jumpY - startY - 3.0F, 0.0F, maxContentScroll);
				pendingJump = null;
			}
			tabScroll[activeTab] = Math.clamp(tabScroll[activeTab], 0.0F, maxContentScroll);
		} else searchScroll = Math.clamp(searchScroll, 0.0F, maxContentScroll);
		if (maxContentScroll > 1.0F) {
			float trackX = x + w - 5.5F;
			IslandRender.drawRoundedRect(trackX, bodyTop + 2.0F, 2.2F, bodyH - 4.0F, 1.1F, 1.1F, 1.1F, 1.1F, col(0xFFFFFFFF, alpha * 0.10F));
			float thumbH = Math.max(14.0F, (bodyH / totalContentH) * (bodyH - 4.0F));
			float currentScroll = searchQuery.isBlank() ? tabScroll[activeTab] : searchScroll;
			float thumbY = bodyTop + 2.0F + (currentScroll / maxContentScroll) * ((bodyH - 4.0F) - thumbH);
			IslandRender.drawRoundedRect(trackX, thumbY, 2.2F, thumbH, 1.1F, 1.1F, 1.1F, 1.1F, col(accent, alpha * 0.72F));
		}

		// 5. Bottom Tooltip & Status Helper Bar
		renderFooter(x, y + h - footerH, w, footerH, alpha, accent, mouseX, mouseY);
		if (confirmation != null) renderConfirmation(x, y, w, h, alpha, accent, mouseX, mouseY);
		else rebuildKeyboardFocus(alpha, accent);

		IslandRender.popScissor();
	}

	private float renderHeader(
		float x, float y, float w, float alpha, int accent,
		double mx, double my, boolean standalone
	) {
		Font titleFont = Fonts.medium(7.2F);
		Font subFont = Fonts.medium(5.4F);

		float h = 30.0F;
		IslandRender.drawText(titleFont, "LT5Lyrics Studio", x + 9.0F, y + 6.2F, col(TEXT_MAIN, alpha));
		float badgeX = x + 9.0F + titleFont.width("LT5Lyrics Studio") + 4.5F;
		IslandRender.drawRoundedRect(badgeX, y + 6.0F, 22.0F, 8.5F, 3.0F, 3.0F, 3.0F, 3.0F, col(accent, alpha * 0.20F));
		IslandRender.drawCenteredText(subFont, "v" + com.lt5lyrics.client.VersionCheck.VERSION, badgeX + 11.0F, y + 7.4F, col(accent, alpha * 0.95F));

		float rightCursor = x + w - 8.0F;

		// Close button
		float closeW = 14.0F;
		rightCursor -= closeW;
		boolean closeHov = hovered(rightCursor, y + 4.0F, closeW, 13.0F, mx, my);
		IslandRender.drawRoundedRect(rightCursor, y + 4.0F, closeW, 13.0F, 4.0F, 4.0F, 4.0F, 4.0F,
			col(closeHov ? 0xFFFF5D6C : 0xFFFFFFFF, alpha * (closeHov ? 0.30F : 0.10F)));
		IslandRender.drawCenteredText(titleFont, "X", rightCursor + closeW / 2.0F, y + 5.6F, col(TEXT_MAIN, alpha));
		hitZones.add(new HitZone(rightCursor, y + 4.0F, closeW, 13.0F,
			"Save settings and close LT5Lyrics Studio.", callbacks::onClosePanel));

		// Expand button when embedded beside HUD in ChatScreen
		if (!standalone) {
			rightCursor -= 38.0F;
			boolean expHov = hovered(rightCursor, y + 4.0F, 35.0F, 13.0F, mx, my);
			IslandRender.drawRoundedRect(rightCursor, y + 4.0F, 35.0F, 13.0F, 4.0F, 4.0F, 4.0F, 4.0F,
				col(accent, alpha * (expHov ? 0.34F : 0.20F)));
			IslandRender.drawCenteredText(subFont, "Full Studio", rightCursor + 17.5F, y + 7.6F, col(TEXT_MAIN, alpha));
			hitZones.add(new HitZone(rightCursor, y + 4.0F, 35.0F, 13.0F,
				"Open the full-size Customization Studio with keyboard HEX/RGB input.", callbacks::onOpenFullScreen));
		}

		// Simple Mode / Advanced Mode pill switch (never resets settings!)
		float switchW = 86.0F;
		rightCursor -= (switchW + 4.0F);
		float sy = y + 4.0F;
		IslandRender.drawRoundedRect(rightCursor, sy, switchW, 13.0F, 4.5F, 4.5F, 4.5F, 4.5F, col(0xFF07090E, alpha * 0.85F));
		float half = (switchW - 2.0F) / 2.0F;
		boolean adv = IslandSettings.advancedSettingsMode;
		IslandRender.drawRoundedRect(
			adv ? (rightCursor + 1.0F + half) : (rightCursor + 1.0F),
			sy + 1.0F, half, 11.0F, 3.8F, 3.8F, 3.8F, 3.8F,
			col(accent, alpha * 0.85F)
		);
		IslandRender.drawCenteredText(subFont, "Simple", rightCursor + 1.0F + half * 0.5F, sy + 3.6F,
			col(!adv ? 0xFF090C12 : TEXT_SUB, alpha));
		IslandRender.drawCenteredText(subFont, "Advanced", rightCursor + 1.0F + half * 1.5F, sy + 3.6F,
			col(adv ? 0xFF090C12 : TEXT_SUB, alpha));

		hitZones.add(new HitZone(rightCursor, sy, half + 1.0F, 13.0F,
			"Simple: a few easy choices. Switching never resets your settings.", () -> switchSettingsMode(false)));
		hitZones.add(new HitZone(rightCursor + half + 1.0F, sy, half + 1.0F, 13.0F,
			"Advanced: every detailed control. Switching never resets your settings.", () -> switchSettingsMode(true)));

        float extraW=57.0F, extraX=x+w-extraW-8.0F, extraY=y+19.0F;
        boolean extraHover=hovered(extraX,extraY,extraW,10.0F,mx,my);
        IslandRender.drawRoundedRect(extraX,extraY,extraW,10.0F,3.0F,3.0F,3.0F,3.0F,col(accent,alpha*(extraHover?0.40F:0.24F)));
        IslandRender.drawCenteredText(subFont,"Extras & Tools",extraX+extraW/2.0F,extraY+2.3F,col(TEXT_MAIN,alpha));
        hitZones.add(new HitZone(extraX,extraY,extraW,10.0F,"Styles, timing, test lyrics and more",()->{
            if(tracker!=null) Minecraft.getInstance().gui.setScreen(new LT5PlusScreen(tracker,Minecraft.getInstance().gui.screen()));
        }));
		String modeHint = adv ? "Advanced: every detailed setting. Your choices are shared."
			: "Simple: easy choices. Advanced has every detailed setting.";
		IslandRender.drawWindowedText(Fonts.regular(5.2F), modeHint, x + 9.0F, y + 21.0F,
			col(TEXT_SUB, alpha), x + 8.0F, extraX - 3.0F, 4.0F, 0.0F);

		return y + h;
	}

	// =========================================================================
	// Simple: separate, short beginner-facing pages backed by real settings.
	// =========================================================================

	private void switchSettingsMode(boolean advanced) {
		if (IslandSettings.advancedSettingsMode == advanced) return;
		if (editTarget != EditTarget.NONE) commitEditBuffer();
		IslandSettings.advancedSettingsMode = advanced;
		searchQuery = ""; editBuffer = ""; searchScroll = 0.0F; pendingJump = null;
		draggingWheel = false; activeSliderId = null; focusIndex = -1;
		syncWheelFromChannel();
		IslandSettings.save();
	}

	private void selectSimplePage(int page) {
		if (page == 1 && IslandSettings.currentDisplayMode() != IslandSettings.DisplayMode.SKY_LYRICS) page = 0;
		simplePage = Math.clamp(page, 0, 3);
		focusIndex = -1;
	}

	private void nextSimplePage(int direction) {
		int[] pages = IslandSettings.currentDisplayMode() == IslandSettings.DisplayMode.SKY_LYRICS
			? new int[] {0, 1, 2, 3} : new int[] {0, 2, 3};
		int current = 0;
		for (int i = 0; i < pages.length; i++) if (pages[i] == simplePage) current = i;
		selectSimplePage(pages[Math.floorMod(current + direction, pages.length)]);
	}

	private float renderSimpleTabs(float x, float y, float w, float alpha, int accent, double mx, double my) {
		boolean sky = IslandSettings.currentDisplayMode() == IslandSettings.DisplayMode.SKY_LYRICS;
		if (!sky && simplePage == 1) simplePage = 0;
		int[] pages = sky ? new int[] {0, 1, 2, 3} : new int[] {0, 2, 3};
		String[] labels = {"Lyrics", "Sky", "Appearance", "Reset"};
		float h = 20.0F, gap = 3.0F, itemW = (w - gap * (pages.length - 1)) / pages.length;
		for (int i = 0; i < pages.length; i++) {
			int page = pages[i];
			renderSmallButton(x + i * (itemW + gap), y, itemW, h, labels[page], simplePage == page,
				alpha, accent, mx, my, y, y + h, "Open " + labels[page] + " settings", () -> selectSimplePage(page));
		}
		return y + h;
	}

	private void renderSimple(float x, float y, float w, float h, float headerBottom,
			float alpha, int accent, double mx, double my) {
		float curY = renderSimpleTabs(x + 8.0F, headerBottom + 3.0F, w - 16.0F, alpha, accent, mx, my);
		float footerH = 15.0F;
		float bodyTop = curY + 5.0F;
		float bodyH = Math.max(1.0F, y + h - footerH - bodyTop - 2.0F);
		float cx = x + 8.0F, cw = w - 18.0F;
		bodyTopLast = bodyTop; bodyBottomLast = bodyTop + bodyH; bodyHeightLast = bodyH;
		bodyHitStart = hitZones.size();
		IslandRender.pushScissor(x + 4.0F, bodyTop, w - 8.0F, bodyH);
		float startY = bodyTop - simpleScroll[simplePage];
		float endY = switch (simplePage) {
			case 0 -> renderSimpleLyrics(cx, startY, cw, alpha, accent, mx, my, bodyTop, bodyTop + bodyH);
			case 1 -> renderSimpleSky(cx, startY, cw, alpha, accent, mx, my, bodyTop, bodyTop + bodyH);
			case 2 -> renderSimpleAppearance(cx, startY, cw, alpha, accent, mx, my, bodyTop, bodyTop + bodyH);
			default -> renderSimpleReset(cx, startY, cw, alpha, accent, mx, my, bodyTop, bodyTop + bodyH);
		};
		IslandRender.popScissor();
		float contentH = endY - startY + 4.0F;
		maxContentScroll = Math.max(0.0F, contentH - bodyH);
		simpleScroll[simplePage] = Math.clamp(simpleScroll[simplePage], 0.0F, maxContentScroll);
		if (maxContentScroll > 1.0F) {
			float tx = x + w - 5.5F;
			IslandRender.drawRoundedRect(tx, bodyTop + 2.0F, 2.2F, Math.max(1.0F, bodyH - 4.0F),
				1.1F, 1.1F, 1.1F, 1.1F, col(0xFFFFFFFF, alpha * 0.10F));
			float thumbH = Math.min(bodyH - 4.0F, Math.max(14.0F, (bodyH / contentH) * (bodyH - 4.0F)));
			float ty = bodyTop + 2.0F + simpleScroll[simplePage] / maxContentScroll * (bodyH - 4.0F - thumbH);
			if (thumbH > 0.0F) IslandRender.drawRoundedRect(tx, ty, 2.2F, thumbH,
				1.1F, 1.1F, 1.1F, 1.1F, col(accent, alpha * 0.72F));
		}
		renderFooter(x, y + h - footerH, w, footerH, alpha, accent, mx, my);
		if (confirmation != null) renderConfirmation(x, y, w, h, alpha, accent, mx, my);
		else rebuildKeyboardFocus(alpha, accent);
	}

	private float simpleIntro(float x, float y, String title, String subtitle, float alpha, int accent) {
		IslandRender.drawText(Fonts.medium(6.9F), title, x + 2.0F, y + 1.0F, col(TEXT_MAIN, alpha));
		IslandRender.drawText(Fonts.regular(5.0F), subtitle, x + 2.0F, y + 10.0F, col(TEXT_SUB, alpha));
		return y + 20.0F;
	}

	private float simpleToggle(float x, float y, float w, String title, String description, boolean on,
			float alpha, int accent, double mx, double my, float top, float bottom, Runnable action) {
		float h = 27.0F;
		boolean hover = isVisible(y, h, top, bottom) && hovered(x, y, w, h, mx, my);
		IslandRender.drawRoundedRect(x, y, w, h, 5.5F, 5.5F, 5.5F, 5.5F,
			col(BG_CARD, alpha * (hover ? 0.98F : 0.86F)));
		IslandRender.drawText(Fonts.medium(6.3F), title, x + 8.0F, y + 5.0F, col(TEXT_MAIN, alpha));
		IslandRender.drawWindowedText(Fonts.regular(5.0F), description, x + 8.0F, y + 15.0F,
			col(TEXT_SUB, alpha), x + 7.0F, x + w - 51.0F, 5.0F, 0.0F);
		float bw = 38.0F, bx = x + w - bw - 7.0F;
		IslandRender.drawRoundedRect(bx, y + 6.0F, bw, 15.0F, 5.0F, 5.0F, 5.0F, 5.0F,
			col(on ? accent : 0xFF3B4556, alpha * 0.90F));
		IslandRender.drawCenteredText(Fonts.medium(5.4F), on ? "On" : "Off", bx + bw / 2, y + 10.4F,
			col(on ? 0xFF080B10 : TEXT_MAIN, alpha));
		if (isVisible(y, h, top, bottom)) hitZones.add(new HitZone(x, y, w, h, description, action));
		return y + h + 4.0F;
	}

	private float simpleChoices(float x, float y, float w, String title, String hint, String current,
			String[] labels, int selected, Runnable[] actions,
			float alpha, int accent, double mx, double my, float top, float bottom) {
		float h = 45.0F;
		IslandRender.drawRoundedRect(x, y, w, h, 5.5F, 5.5F, 5.5F, 5.5F, col(BG_CARD, alpha * 0.86F));
		Font titleFont = Fonts.medium(6.2F), valueFont = Fonts.medium(5.5F);
		IslandRender.drawText(titleFont, title, x + 8.0F, y + 4.2F, col(TEXT_MAIN, alpha));
		String state = "Current: " + current;
		IslandRender.drawText(valueFont, state, x + w - 8.0F - valueFont.width(state), y + 5.0F, col(accent, alpha));
		IslandRender.drawWindowedText(Fonts.regular(4.9F), hint, x + 8.0F, y + 13.0F,
			col(TEXT_SUB, alpha), x + 7.0F, x + w - 8.0F, 5.0F, 0.0F);
		float gap = 3.0F, inner = w - 14.0F, bw = (inner - gap * (labels.length - 1)) / labels.length;
		for (int i = 0; i < labels.length; i++) {
			renderSmallButton(x + 7.0F + i * (bw + gap), y + 22.0F, bw, 18.0F, labels[i], i == selected,
				alpha, accent, mx, my, top, bottom, hint, actions[i]);
		}
		return y + h + 4.0F;
	}

	private void setLyricsVisible(boolean visible) {
		if (IslandSettings.lyricsEnabled == visible) return;
		IslandSettings.lyricsEnabled = visible;
		WorldLyrics.reset();
		IslandSettings.save();
	}

	private void setSimpleDisplay(IslandSettings.DisplayMode mode) {
		if (IslandSettings.currentDisplayMode() == mode) return;
		IslandSettings.setDisplayMode(mode);
		WorldLyrics.reset();
		IslandSettings.save();
	}

	private static String displayLabel(IslandSettings.DisplayMode mode) {
		return switch (mode) {
			case WORLD_3D -> "World";
			case SKY_LYRICS -> "Sky";
			case HUD_ONLY -> "Top Bar";
		};
	}

	private static String pretty(Enum<?> value) {
		String lower = value.name().toLowerCase(Locale.ROOT);
		return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	}

	private float renderSimpleLyrics(float x, float y, float w, float alpha, int accent,
			double mx, double my, float top, float bottom) {
		y = simpleIntro(x, y, "Make lyrics yours", "Choose where lyrics appear and what they show.", alpha, accent);
		y = simpleToggle(x, y, w, "Lyrics Enabled", "Hide or show lyrics; your music bar stays.",
			IslandSettings.lyricsEnabled, alpha, accent, mx, my, top, bottom,
			() -> setLyricsVisible(!IslandSettings.lyricsEnabled));
		IslandSettings.DisplayMode mode = IslandSettings.currentDisplayMode();
		String[] labels = {"World", "Sky", "Top Bar"};
		Runnable[] actions = {
			() -> setSimpleDisplay(IslandSettings.DisplayMode.WORLD_3D),
			() -> setSimpleDisplay(IslandSettings.DisplayMode.SKY_LYRICS),
			() -> setSimpleDisplay(IslandSettings.DisplayMode.HUD_ONLY)
		};
		y = simpleChoices(x, y, w, "Where to show lyrics", "Top Bar also turns the music bar on.", displayLabel(mode),
			labels, mode.ordinal(), actions, alpha, accent, mx, my, top, bottom);
		if (mode == IslandSettings.DisplayMode.WORLD_3D) {
			IslandSettings.NormalSide side = IslandSettings.normalSide;
			int selected = switch (side) { case LEFT -> 0; case RIGHT -> 1; case ALTERNATE -> 2; default -> -1; };
			String[] positions = {"Left", "Right", "Alternate"};
			Runnable[] positionActions = new Runnable[3];
			IslandSettings.NormalSide[] sides = {IslandSettings.NormalSide.LEFT, IslandSettings.NormalSide.RIGHT, IslandSettings.NormalSide.ALTERNATE};
			for (int i = 0; i < sides.length; i++) {
				IslandSettings.NormalSide chosen = sides[i];
				positionActions[i] = () -> { IslandSettings.setNormalSide(chosen); IslandSettings.save(); };
			}
			y = simpleChoices(x, y, w, "World position", "Applies to new lyrics; existing lines stay in place.", SimpleSettings.position(),
				positions, selected, positionActions, alpha, accent, mx, my, top, bottom);
		}
		y = simpleToggle(x, y, w, "Show Translations", "Show a translation when one is available.",
			IslandSettings.showTranslations, alpha, accent, mx, my, top, bottom,
			() -> { IslandSettings.showTranslations = !IslandSettings.showTranslations; IslandSettings.save(); });
		return y;
	}

	private float renderSimpleSky(float x, float y, float w, float alpha, int accent,
			double mx, double my, float top, float bottom) {
		y = simpleIntro(x, y, "Sky lyrics", "These settings only affect lyrics in the sky.", alpha, accent);
		y = simpleToggle(x, y, w, "Follow Look Direction", "Follows left and right, not up and down.",
			IslandSettings.skyCrosshairFollow, alpha, accent, mx, my, top, bottom,
			() -> { IslandSettings.skyCrosshairFollow = !IslandSettings.skyCrosshairFollow; IslandSettings.save(); });
		SimpleSettings.Distance dist = SimpleSettings.distance();
		SimpleSettings.Distance[] values = {SimpleSettings.Distance.NEAR, SimpleSettings.Distance.MEDIUM, SimpleSettings.Distance.FAR};
		Runnable[] distanceActions = new Runnable[values.length];
		for (int i = 0; i < values.length; i++) {
			SimpleSettings.Distance choice = values[i];
			distanceActions[i] = () -> { SimpleSettings.setDistance(choice); WorldLyrics.onSkySettingsChanged(); IslandSettings.save(); };
		}
		y = simpleChoices(x, y, w, "Sky distance", "How far away the sky lyrics appear.", pretty(dist),
			new String[] {"Near", "Medium", "Far"}, dist == SimpleSettings.Distance.CUSTOM ? -1 : dist.ordinal(),
			distanceActions, alpha, accent, mx, my, top, bottom);
		y = simpleToggle(x, y, w, "Previous & Next Lines", "Show the nearby lyric lines too.",
			IslandSettings.skyContextLines, alpha, accent, mx, my, top, bottom,
			() -> { IslandSettings.skyContextLines = !IslandSettings.skyContextLines; IslandSettings.save(); });
		y = simpleToggle(x, y, w, "Song Title", "Show the song title and artist at the start.",
			IslandSettings.skySongHeader, alpha, accent, mx, my, top, bottom,
			() -> { IslandSettings.skySongHeader = !IslandSettings.skySongHeader; IslandSettings.save(); });
		return y;
	}

	private float renderSimpleAppearance(float x, float y, float w, float alpha, int accent,
			double mx, double my, float top, float bottom) {
		IslandSettings.DisplayMode mode = IslandSettings.currentDisplayMode();
		y = simpleIntro(x, y, "Appearance", "Simple choices for the selected lyric style.", alpha, accent);
		if (mode != IslandSettings.DisplayMode.HUD_ONLY) {
			SimpleSettings.Size size = SimpleSettings.size(mode);
			SimpleSettings.Size[] values = {SimpleSettings.Size.SMALL, SimpleSettings.Size.MEDIUM, SimpleSettings.Size.LARGE};
			Runnable[] sizeActions = new Runnable[values.length];
			for (int i = 0; i < values.length; i++) {
				SimpleSettings.Size choice = values[i];
				sizeActions[i] = () -> { SimpleSettings.setSize(mode, choice); WorldLyrics.onSkySettingsChanged(); IslandSettings.save(); };
			}
			String hint = mode == IslandSettings.DisplayMode.SKY_LYRICS
				? "Sky size; long lyrics may shrink to fit."
				: "World size; advanced size adjustments are kept.";
			y = simpleChoices(x, y, w, "Lyric size", hint, pretty(size), new String[] {"Small", "Medium", "Large"},
				size == SimpleSettings.Size.CUSTOM ? -1 : size.ordinal(), sizeActions, alpha, accent, mx, my, top, bottom);
		}
		SimpleSettings.Color color = SimpleSettings.color(mode);
		SimpleSettings.Color[] colors = {SimpleSettings.Color.WHITE, SimpleSettings.Color.BLUE, SimpleSettings.Color.PINK, SimpleSettings.Color.GOLD};
		Runnable[] colorActions = new Runnable[colors.length];
		for (int i = 0; i < colors.length; i++) {
			SimpleSettings.Color choice = colors[i];
			colorActions[i] = () -> { SimpleSettings.setColor(mode, choice); IslandSettings.save(); };
		}
		String colorHint = IslandSettings.separateSkyColors
			? (mode == IslandSettings.DisplayMode.SKY_LYRICS ? "Changes your separate Sky text color." : "Changes World and Top Bar lyric text.")
			: "Changes the shared lyric text color.";
		y = simpleChoices(x, y, w, "Text color", colorHint, color.label, new String[] {"White", "Blue", "Pink", "Gold"},
			color == SimpleSettings.Color.CUSTOM ? -1 : color.ordinal(), colorActions, alpha, accent, mx, my, top, bottom);
		if (mode == IslandSettings.DisplayMode.WORLD_3D) {
			SimpleSettings.Background bg = SimpleSettings.background();
			SimpleSettings.Background[] values = {SimpleSettings.Background.OFF, SimpleSettings.Background.SOFT, SimpleSettings.Background.STRONG};
			Runnable[] bgActions = new Runnable[values.length];
			for (int i = 0; i < values.length; i++) {
				SimpleSettings.Background choice = values[i];
				bgActions[i] = () -> { SimpleSettings.setBackground(choice); IslandSettings.save(); };
			}
			y = simpleChoices(x, y, w, "World background", "A dark plate behind World lyrics.", pretty(bg),
				new String[] {"Off", "Soft", "Strong"}, bg == SimpleSettings.Background.CUSTOM ? -1 : bg.ordinal(),
				bgActions, alpha, accent, mx, my, top, bottom);
		}
		if (mode == IslandSettings.DisplayMode.HUD_ONLY) {
			IslandRender.drawWindowedText(Fonts.regular(5.0F), "Top Bar text size is fixed.", x + 3.0F, y + 2.0F,
				col(TEXT_SUB, alpha), x + 2.0F, x + w - 2.0F, 4.0F, 0.0F);
			y += 14.0F;
		}
		return y;
	}

	private float renderSimpleReset(float x, float y, float w, float alpha, int accent,
			double mx, double my, float top, float bottom) {
		y = simpleIntro(x, y, "Reset Simple Settings", "Start the easy settings over, without resetting everything.", alpha, accent);
		IslandRender.drawRoundedRect(x, y, w, 49.0F, 5.5F, 5.5F, 5.5F, 5.5F, col(BG_CARD, alpha * 0.86F));
		IslandRender.drawWindowedText(Fonts.medium(6.0F), "This resets all Simple choices, including World and Sky.", x + 8.0F, y + 7.0F,
			col(TEXT_MAIN, alpha), x + 7.0F, x + w - 8.0F, 5.0F, 0.0F);
		IslandRender.drawWindowedText(Fonts.regular(5.1F), "Detailed settings, sources, keys and song timing are kept.", x + 8.0F, y + 20.0F,
			col(TEXT_SUB, alpha), x + 7.0F, x + w - 8.0F, 5.0F, 0.0F);
		IslandRender.drawText(Fonts.regular(5.1F), "You'll be asked to confirm first.", x + 8.0F, y + 32.0F, col(TEXT_SUB, alpha));
		y += 55.0F;
		renderSmallButton(x, y, w, 21.0F, "Reset Simple Settings", false, alpha, accent, mx, my, top, bottom,
			"Only the easy choices are reset, after confirmation.", () ->
			requestConfirmation("Reset Simple Settings?", "Simple choices only. Detailed settings and song timing are kept.", () -> {
				SimpleSettings.resetSimpleSettings();
				WorldLyrics.reset();
				IslandSettings.save();
				selectSimplePage(0);
				flashStatus("Simple settings reset");
			}));
		return y + 26.0F;
	}

	// =========================================================================
	// Top Live Preview Panel (Shared Style Logic + Sample Fallback + Before/After)
	// =========================================================================

	private float renderLivePreview(
		float x, float y, float w, float h, float alpha, int accent, double mx, double my
	) {
		IslandRender.drawRoundedRect(x, y, w, h, 7.0F, 7.0F, 7.0F, 7.0F, col(BG_PREVIEW, alpha * 0.95F));
		IslandRender.drawRoundedRect(x, y, w, h, 7.0F, 7.0F, 7.0F, 7.0F, col(STROKE_SUBTLE, alpha * 0.14F));

		Font badgeFont = Fonts.medium(4.9F);
		List<LyricsFetcher.LyricLine> activeLines = tracker.getLyrics();
		boolean usingLiveSong = !activeLines.isEmpty() && tracker.isPlaying();

		String mainLine;
		String transLine;
		float karaokeProg;
		long animElapsedMs;

		if (usingLiveSong) {
			float pos = tracker.smoothPosition();
			int idx = Math.max(0, LyricsFetcher.currentIndex(activeLines, pos));
			LyricsFetcher.LyricLine ll = activeLines.get(idx);
			mainLine = ll.text();
			transLine = ll.hasTranslation() ? ll.translation() : SAMPLES[sampleIndex].translation();
			karaokeProg = Karaoke.lineProgress(activeLines, idx, pos);
			animElapsedMs = (long) (Math.max(0.0F, pos - Math.max(0.0F, ll.time())) * 1000.0F);
		} else {
			SampleLyric sample = SAMPLES[sampleIndex];
			mainLine = sample.text();
			transLine = sample.translation();
			long cycleMs = (long) (3800.0F / Math.clamp(IslandSettings.animationSpeed, 0.5F, 2.0F));
			long t = System.currentTimeMillis() % Math.max(1200L, cycleMs);
			karaokeProg = Math.clamp((float) t / (cycleMs * 0.82F), 0.0F, 1.0F);
			animElapsedMs = t;
		}

		// Top badge clearly marking Preview
		String badgeLabel = usingLiveSong
			? "LIVE PREVIEW • NOW PLAYING"
			: ("LIVE PREVIEW • SAMPLE (" + SAMPLES[sampleIndex].label() + ")");
		IslandRender.drawText(badgeFont, badgeLabel, x + 7.0F, y + 4.2F, col(accent, alpha * 0.92F));

		// Top-right mini buttons: Sample language switcher + Before/After split toggle
		float btnRight = x + w - 5.0F;
		float cmpW = 54.0F;
		btnRight -= cmpW;
		boolean cmpHov = hovered(btnRight, y + 2.8F, cmpW, 10.0F, mx, my);
		IslandRender.drawRoundedRect(btnRight, y + 2.8F, cmpW, 10.0F, 3.2F, 3.2F, 3.2F, 3.2F,
			col(compareBeforeAfter ? accent : 0xFFFFFFFF, alpha * (compareBeforeAfter ? 0.30F : (cmpHov ? 0.18F : 0.10F))));
		IslandRender.drawCenteredText(badgeFont, compareBeforeAfter ? "Before / After: ON" : "Before / After",
			btnRight + cmpW * 0.5F, y + 4.3F, col(TEXT_MAIN, alpha * 0.92F));
		hitZones.add(new HitZone(btnRight, y + 2.8F, cmpW, 10.0F,
			"Compare how lyrics looked before opening Studio vs your live changes right now.",
			() -> compareBeforeAfter = !compareBeforeAfter));

		if (!usingLiveSong) {
			float smpW = 48.0F;
			btnRight -= (smpW + 4.0F);
			boolean smpHov = hovered(btnRight, y + 2.8F, smpW, 10.0F, mx, my);
			IslandRender.drawRoundedRect(btnRight, y + 2.8F, smpW, 10.0F, 3.2F, 3.2F, 3.2F, 3.2F,
				col(0xFFFFFFFF, alpha * (smpHov ? 0.18F : 0.10F)));
			IslandRender.drawCenteredText(badgeFont, "Sample: " + SAMPLES[sampleIndex].label(),
				btnRight + smpW * 0.5F, y + 4.3F, col(TEXT_SUB, alpha * 0.92F));
			hitZones.add(new HitZone(btnRight, y + 2.8F, smpW, 10.0F,
				"Cycle sample lyrics between English/Japanese, Khmer, Chinese, and Korean.",
				() -> sampleIndex = (sampleIndex + 1) % SAMPLES.length));
		}

		float stageTop = y + 13.5F;
		float stageH = h - 15.0F;

		if (compareBeforeAfter && openingSnapshot != null) {
			float halfW = (w - 10.0F) * 0.5F;
			// Left: BEFORE
			IslandRender.pushScissor(x + 3.0F, stageTop, halfW, stageH);
			IslandRender.drawText(badgeFont, "BEFORE", x + 7.0F, stageTop + 1.0F, col(TEXT_DIM, alpha * 0.85F));
			renderPreviewSampleStage(
				x + 4.0F, stageTop + 3.0F, halfW - 2.0F, stageH - 3.0F,
				mainLine, transLine, karaokeProg, animElapsedMs, alpha,
				openingSnapshot.primaryColor(),
				openingSnapshot.highlightColor(),
				openingSnapshot.translationColor(),
				openingSnapshot.glowColor(),
				openingSnapshot.glowEnabled() ? openingSnapshot.glowIntensity() : 0.0F,
				openingSnapshot.outlineColor(),
				openingSnapshot.outlineThickness(),
				openingSnapshot.textScale(),
				openingSnapshot.lineSpacing(),
				openingSnapshot.translations(),
				openingSnapshot.animation(),
				openingSnapshot.animationSpeed(),
				openingSnapshot.smoothness()
			);
			IslandRender.popScissor();

			// Center divider
			IslandRender.drawRoundedRect(x + w * 0.5F - 0.5F, stageTop + 2.0F, 1.0F, stageH - 4.0F,
				0.5F, 0.5F, 0.5F, 0.5F, col(0xFFFFFFFF, alpha * 0.18F));

			// Right: AFTER (LIVE)
			float rightX = x + w * 0.5F + 2.0F;
			IslandRender.pushScissor(rightX, stageTop, halfW, stageH);
			IslandRender.drawText(badgeFont, "NOW (LIVE)", rightX + 4.0F, stageTop + 1.0F, col(accent, alpha * 0.90F));
			renderLiveCurrentStage(rightX, stageTop + 3.0F, halfW - 2.0F, stageH - 3.0F,
				mainLine, transLine, karaokeProg, animElapsedMs, alpha);
			IslandRender.popScissor();
		} else {
			IslandRender.pushScissor(x + 3.0F, stageTop, w - 6.0F, stageH);
			renderLiveCurrentStage(x + 4.0F, stageTop, w - 8.0F, stageH,
				mainLine, transLine, karaokeProg, animElapsedMs, alpha);
			IslandRender.popScissor();
		}

		return y + h;
	}

	private void renderLiveCurrentStage(
		float sx, float sy, float sw, float sh,
		String mainLine, String transLine, float karaokeProg, long animElapsedMs, float alpha
	) {
		boolean skySelected = IslandSettings.skyLyrics && IslandSettings.worldText;
		int primary = skySelected ? IslandSettings.resolveSkyPrimaryColor() : IslandSettings.resolveWorldPrimaryColor();
		float glow = skySelected ? IslandSettings.effectiveSkyGlow() : IslandSettings.effectiveWorldGlow();
		renderPreviewSampleStage(
			sx, sy, sw, sh, mainLine, transLine, karaokeProg, animElapsedMs, alpha,
			primary,
			IslandSettings.resolveHighlightColor(),
			IslandSettings.resolveTranslationColor(),
			IslandSettings.resolveGlowColor(),
			glow,
			IslandSettings.resolveOutlineColor(),
			IslandSettings.lyricOutlineThickness,
			IslandSettings.lyricTextScale,
			IslandSettings.lyricLineSpacing,
			IslandSettings.showTranslations,
			IslandSettings.lyricAnimation,
			IslandSettings.animationSpeed,
			IslandSettings.lyricSmoothness
		);
	}

	private void renderPreviewSampleStage(
		float sx, float sy, float sw, float sh,
		String mainLine, String transLine, float karaokeProg, long animElapsedMs, float alpha,
		int primaryArgb, int highlightArgb, int translationArgb,
		int glowArgb, float glowStrength,
		int outlineArgb, float outlineThickness,
		float textScale, float lineSpacing, boolean showTrans,
		IslandSettings.LyricAnimation anim, float animSpeed, float smoothness
	) {
		LyricAnimator.MotionState motion = LyricAnimator.computeActive(anim, animElapsedMs, animSpeed);
		float clampedScale = Math.clamp(textScale, 0.55F, 1.65F) * motion.scaleFactor();
		float basePt = sh >= 28.0F ? 8.2F : 7.0F;
		Font mainFont = Fonts.lyric(Math.clamp(basePt * clampedScale, 5.2F, 13.8F));
		Font subFont = Fonts.lyricSubtle(Math.clamp(basePt * 0.66F * clampedScale, 4.6F, 9.2F));

		float lineAlpha = alpha * motion.alpha();
		int lit = col(primaryArgb, lineAlpha);
		int hi = col(highlightArgb, lineAlpha);
		int upcoming = col(ColorUtil.upcomingForPrimary(primaryArgb, glowArgb), lineAlpha * 0.48F);

		int len = mainLine.length();
		if (previewScratchCols.length < len) {
			previewScratchCols = new int[Math.max(len, previewScratchCols.length * 2)];
		}
		Karaoke.fillColors(previewScratchCols, len, len * karaokeProg, lit, hi, upcoming, smoothness);

		float mainW = mainFont.width(mainLine);
		float mainX = sx + Math.max(3.0F, (sw - mainW) * 0.5F);
		boolean hasTrans = showTrans && transLine != null && !transLine.isBlank();
		float centerY = sy + sh * (hasTrans ? 0.34F : 0.46F) - mainFont.getSize() * 0.45F - motion.liftUnits() * 4.5F;

		int glowCol = col(glowArgb, lineAlpha * 0.62F * glowStrength);
		int outCol = col(outlineArgb, lineAlpha * 0.92F);

		IslandRender.drawStyledLyric2D(
			mainFont, mainLine, mainX, centerY,
			lit, previewScratchCols, len,
			glowCol, glowStrength,
			outCol, outlineThickness,
			IslandSettings.hologramShadow,
			true, sx, sx + sw, mainW > sw - 4.0F ? 8.0F : 0.0F, 0.0F
		);

		if (hasTrans) {
			float subW = subFont.width(transLine);
			float subX = sx + Math.max(3.0F, (sw - subW) * 0.5F);
			float subY = centerY + mainFont.getSize() * 0.88F * Math.clamp(lineSpacing, 0.75F, 1.50F);
			int subCol = col(translationArgb, lineAlpha * 0.86F);
			int subGlow = col(glowArgb, lineAlpha * 0.38F * glowStrength);
			IslandRender.drawStyledLyric2D(
				subFont, transLine, subX, subY,
				subCol, null, -1,
				subGlow, glowStrength * 0.6F,
				outCol, outlineThickness * 0.75F,
				IslandSettings.hologramShadow,
				true, sx, sx + sw, subW > sw - 4.0F ? 8.0F : 0.0F, 0.0F
			);
		}
	}

	// =========================================================================
	// 5 Organized Category Tabs
	// =========================================================================

	private float renderCategoryTabs(
		float x, float y, float w, float alpha, int accent, double mx, double my
	) {
		float h = 16.5F;
		IslandRender.drawRoundedRect(x, y, w, h, 5.5F, 5.5F, 5.5F, 5.5F, col(0xFF090C12, alpha * 0.85F));
		Font tabFont = Fonts.medium(w >= 320.0F ? 5.6F : 5.0F);
		float gap = 2.0F;
		float tabW = (w - 4.0F - gap * (TAB_NAMES.length - 1)) / TAB_NAMES.length;

		for (int i = 0; i < TAB_NAMES.length; i++) {
			float tx = x + 2.0F + i * (tabW + gap);
			boolean selected = (activeTab == i);
			boolean hov = hovered(tx, y + 1.5F, tabW, h - 3.0F, mx, my);
			if (selected || hov) {
				IslandRender.drawRoundedRect(tx, y + 1.5F, tabW, h - 3.0F, 4.2F, 4.2F, 4.2F, 4.2F,
					col(selected ? accent : 0xFFFFFFFF, alpha * (selected ? 0.88F : 0.10F)));
			}
			String label = (w < 360.0F && i == 3) ? "Sources" : TAB_NAMES[i];
			if (tabFont.width(label) > tabW - 3.0F && i == 3) label = "Sources";
			IslandRender.drawCenteredText(tabFont, label, tx + tabW * 0.5F, y + 4.8F,
				col(selected ? 0xFF080B10 : TEXT_SUB, alpha));
			final int tabIdx = i;
			String tip = switch (i) {
				case 0 -> "Appearance: choose lyric color, theme presets, text size, glow, and restore defaults.";
				case 1 -> "Lyrics: choose where lyrics appear, toggle translations, animation speed, and song sync.";
				case 2 -> "Sky Lyrics: adjust Sky Lyrics spread radius, height, and sky arc options.";
				case 3 -> "Music & Sources: see active song status, retry lyrics, and enable lyric websites.";
				default -> "Advanced: separate element colors, exact HEX/RGB, outlines, fonts, HUD, and API keys.";
			};
			hitZones.add(new HitZone(tx, y, tabW, h, tip, () -> {
				if (editTarget != EditTarget.NONE) commitEditBuffer();
				selectTab(tabIdx, true);
			}));
		}
		return y + h;
	}

	// =========================================================================
	// Tab 0: Appearance (Color Wheel + Swatch + HEX/RGB + Size + Glow + Restore)
	// =========================================================================

	private float renderAppearanceTab(
		float x, float y, float w, float alpha, int accent,
		double mx, double my, boolean standalone, float visTop, float visBot
	) {
		boolean adv = IslandSettings.advancedSettingsMode;

		// 1. Theme Presets Row
		y = sectionTitle(x, y, "Quick Color Themes", "One-click color palettes you can customize further below.", alpha);
		ThemePreset[] presets = ThemePreset.values();
		float gap = 3.0F;
		float pw = (w - gap * (presets.length - 1)) / presets.length;
		Font pFont = Fonts.medium(4.9F);
		for (int i = 0; i < presets.length; i++) {
			ThemePreset p = presets[i];
			float px = x + i * (pw + gap);
			boolean activePreset = p.matchesCurrent();
			boolean hov = isVisible(y, 15.0F, visTop, visBot) && hovered(px, y, pw, 15.0F, mx, my);
			IslandRender.drawRoundedRect(px, y, pw, 15.0F, 4.2F, 4.2F, 4.2F, 4.2F,
				col(activePreset ? accent : BG_CARD, alpha * (activePreset ? 0.28F : (hov ? 0.98F : 0.85F))));
			IslandRender.drawRoundedRect(px + 3.0F, y + 4.0F, 7.0F, 7.0F, 3.5F, 3.5F, 3.5F, 3.5F,
				col(p.lyricHighlightColor, alpha));
			IslandRender.drawWindowedText(pFont, p.label, px + 11.5F, y + 4.8F, col(TEXT_MAIN, alpha * 0.92F),
				px + 11.0F, px + pw - 2.0F, 4.0F, 0.0F);
			if (isVisible(y, 15.0F, visTop, visBot)) {
				hitZones.add(new HitZone(px, y, pw, 15.0F,
					"Apply the " + p.label + " lyric color palette across world, sky, and HUD.",
					() -> {
						p.apply();
						syncWheelFromChannel();
						flashStatus("Applied " + p.label + " theme");
					}));
			}
		}
		y += 19.0F;

		// 2. Advanced Channel Selector (shown in Advanced Mode)
		if (adv) {
			y = renderChannelSelectorBar(x, y, w, alpha, accent, mx, my, visTop, visBot);
		}

		markSearchTarget("Lyric Color", y);
		// 3. Lyric Color Studio Card (HSV Wheel + Brightness + Large Swatch + HEX/RGB + Reset)
		float wheelCardH = adv ? 96.0F : 86.0F;
		IslandRender.drawRoundedRect(x, y, w, wheelCardH, 6.5F, 6.5F, 6.5F, 6.5F, col(BG_CARD, alpha * 0.88F));
		Font cardHead = Fonts.medium(6.0F);
		Font smallFont = Fonts.regular(5.1F);
		String colorHeading = adv ? ("Color: " + selectedChannel.label) : "Lyric Color";
		String colorDesc = adv ? selectedChannel.hint : "Changes the color of your lyrics live in the world, sky, and HUD.";
		IslandRender.drawText(cardHead, colorHeading, x + 7.0F, y + 5.0F, col(TEXT_MAIN, alpha));
		IslandRender.drawText(smallFont, colorDesc, x + 7.0F, y + 13.0F, col(TEXT_SUB, alpha * 0.88F));

		float wheelD = 52.0F;
		float wheelX = x + 10.0F;
		float wheelY = y + 22.0F;
		lastWheelCx = wheelX + wheelD * 0.5F;
		lastWheelCy = wheelY + wheelD * 0.5F;
		lastWheelRadius = wheelD * 0.5F;
		wheelVisible = isVisible(wheelY, wheelD, visTop, visBot);

		IslandRender.drawColorWheel(wheelX, wheelY, wheelD, wheelVal, col(0xFFFFFFFF, alpha));

		// Wheel thumb indicator (matches island_wheel.fsh via ColorUtil.hueSatToWheel)
		float[] normPos = ColorUtil.hueSatToWheel(wheelHue, wheelSat);
		float tx = lastWheelCx + normPos[0] * (lastWheelRadius - 2.5F);
		float ty = lastWheelCy + normPos[1] * (lastWheelRadius - 2.5F);
		IslandRender.drawRoundedRect(tx - 3.5F, ty - 3.5F, 7.0F, 7.0F, 3.5F, 3.5F, 3.5F, 3.5F, col(0xFF000000, alpha * 0.85F));
		int activeRgb = currentChannelColor();
		IslandRender.drawRoundedRect(tx - 2.5F, ty - 2.5F, 5.0F, 5.0F, 2.5F, 2.5F, 2.5F, 2.5F, col(activeRgb, alpha));

		float rightX = wheelX + wheelD + 12.0F;
		float rightW = Math.max(90.0F, (x + w - 8.0F) - rightX);

		// Large Color Preview Swatch + HEX + RGB + Reset on the right side
		float swatchW = 36.0F;
		float swatchH = 24.0F;
		IslandRender.drawRoundedRect(rightX - 1.0F, wheelY - 1.0F, swatchW + 2.0F, swatchH + 2.0F, 5.0F, 5.0F, 5.0F, 5.0F, col(0xFFFFFFFF, alpha * 0.25F));
		IslandRender.drawRoundedRect(rightX, wheelY, swatchW, swatchH, 4.5F, 4.5F, 4.5F, 4.5F, col(activeRgb, alpha));

		float fieldsX = rightX + swatchW + 6.0F;
		float fieldsW = Math.max(54.0F, (x + w - 8.0F) - fieldsX);

		// Editable HEX Box
		renderEditableBox(
			fieldsX, wheelY, fieldsW, 11.5F,
			"HEX", ColorUtil.toHex(activeRgb), EditTarget.HEX,
			alpha, accent, mx, my, standalone, visTop, visBot,
			"Click to type or paste a #HEX color code (example: #58E1FF), then press Enter."
		);

		// Editable RGB Box in Advanced Mode, clean formatted RGB readout in Simple Mode
		if (adv) {
			renderEditableBox(
				fieldsX, wheelY + 13.0F, fieldsW, 11.5F,
				"RGB", ColorUtil.toRgbString(activeRgb), EditTarget.RGB,
				alpha, accent, mx, my, standalone, visTop, visBot,
				"Click to type exact Red, Green, Blue values (0-255, 0-255, 0-255), then press Enter."
			);
		} else {
			IslandRender.drawText(smallFont, "RGB: " + ColorUtil.toRgbString(activeRgb), fieldsX + 1.0F, wheelY + 15.2F, col(TEXT_SUB, alpha * 0.90F));
		}

		// Brightness Slider inside Color Card
		float briY = wheelY + 28.0F;
		renderInlineSlider(
			"wheel_val", rightX, briY, rightW,
			"Brightness", Math.round(wheelVal * 100.0F) + "%",
			0.08F, 1.0F,
			() -> wheelVal,
			v -> {
				wheelVal = v;
				applyColorToActiveChannel(ColorUtil.hsvToRgb(wheelHue, wheelSat, wheelVal));
			},
			alpha, accent, visTop, visBot,
			"Adjusts how bright or rich the selected lyric color looks."
		);

		// Copy #HEX & Reset Color buttons at bottom of Color Card
		float btnRowY = wheelY + (adv ? 47.0F : 45.0F);
		float halfBtnW = (rightW - 4.0F) * 0.5F;
		renderSmallButton(rightX, btnRowY, halfBtnW, 12.5F, "Copy #HEX", false, alpha, accent, mx, my, visTop, visBot,
			"Copy the current #HEX color code to your clipboard.",
			() -> {
				String hex = ColorUtil.toHex(currentChannelColor());
				Minecraft.getInstance().keyboardHandler.setClipboard(hex);
				flashStatus("Copied " + hex);
			});
		renderSmallButton(rightX + halfBtnW + 4.0F, btnRowY, halfBtnW, 12.5F, "Reset Color", false, alpha, accent, mx, my, visTop, visBot,
			"Reset lyric color back to clean pure white (#FFFFFF).",
			() -> {
				applyColorToActiveChannel(0xFFFFFFFF);
				syncWheelFromChannel();
				IslandSettings.save();
				flashStatus("Reset color to #FFFFFF");
			});

		y += wheelCardH + 5.0F;

		// 4. Text Size ("Smaller" <-> "Larger")
		y = renderLabeledSliderCard(
			"text_scale", x, y, w,
			"Text Size (Smaller ⟷ Larger)",
			"Makes in-game lyrics smaller or larger across World and Sky Lyrics.",
			Math.round(IslandSettings.lyricTextScale * 100.0F) + "%",
			adv ? IslandSettings.MIN_LYRIC_TEXT_SCALE : 0.60F,
			adv ? IslandSettings.MAX_LYRIC_TEXT_SCALE : 1.75F,
			() -> IslandSettings.lyricTextScale,
			v -> IslandSettings.lyricTextScale = Math.round(v * 20.0F) / 20.0F,
			alpha, accent, visTop, visBot
		);

		// 5. Text Glow ("Make lyrics glow") + Advanced Glow/Outline sliders
		y = renderToggleCard(
			x, y, w,
			"Text Glow — Make lyrics glow",
			"Adds a soft luminous halo around lyrics so they stand out against dark or busy scenes.",
			IslandSettings.lyricGlowEnabled,
			alpha, accent, mx, my, visTop, visBot,
			() -> {
				IslandSettings.lyricGlowEnabled = !IslandSettings.lyricGlowEnabled;
				if (IslandSettings.lyricGlowEnabled && IslandSettings.lyricGlowIntensity < 0.15F) {
					IslandSettings.lyricGlowIntensity = 0.60F;
				}
				IslandSettings.save();
			}
		);

		if (adv) {
			y = renderLabeledSliderCard(
				"glow_intensity", x, y, w,
				"Glow Intensity",
				"Controls how strong the soft outer glow halo looks around letters.",
				Math.round(IslandSettings.lyricGlowIntensity * 100.0F) + "%",
				0.0F, 1.0F,
				() -> IslandSettings.lyricGlowIntensity,
				v -> {
					IslandSettings.lyricGlowIntensity = v;
					IslandSettings.skyGlow = v;
				},
				alpha, accent, visTop, visBot
			);
			y = renderLabeledSliderCard(
				"outline_thick", x, y, w,
				"Outline Thickness",
				"Draws a crisp border around each letter for high contrast against bright skies or snow.",
				Math.round(IslandSettings.lyricOutlineThickness * 100.0F) + "%",
				0.0F, 1.0F,
				() -> IslandSettings.lyricOutlineThickness,
				v -> IslandSettings.lyricOutlineThickness = v,
				alpha, accent, visTop, visBot
			);
		}

		// 6. Restore Appearance Defaults Button (never erases music/provider/timing settings!)
		markSearchTarget("Restore Appearance Defaults", y);
		boolean rstHov = isVisible(y, 18.0F, visTop, visBot) && hovered(x, y, w, 18.0F, mx, my);
		IslandRender.drawRoundedRect(x, y, w, 18.0F, 5.5F, 5.5F, 5.5F, 5.5F,
			col(rstHov ? 0xFFFF6B7A : BG_CARD, alpha * (rstHov ? 0.28F : 0.88F)));
		IslandRender.drawCenteredText(Fonts.medium(5.8F), "Restore Appearance Defaults",
			x + w * 0.5F, y + 5.6F, col(TEXT_MAIN, alpha));
		if (isVisible(y, 18.0F, visTop, visBot)) {
			hitZones.add(new HitZone(x, y, w, 18.0F,
				"Resets lyric colors, text size, glow, outline, and animation without erasing music or provider settings.",
				() -> requestConfirmation("Restore appearance?", "Colors and visual styling return to defaults; providers and keys stay.", () -> {
					IslandSettings.restoreAppearanceDefaults();
					WorldLyrics.onSkySettingsChanged();
					syncWheelFromChannel();
					flashStatus("Restored default appearance");
				})));
		}
		return y + 22.0F;
	}

	private float renderChannelSelectorBar(
		float x, float y, float w, float alpha, int accent, double mx, double my, float visTop, float visBot
	) {
		ColorChannel[] channels = ColorChannel.values();
		float gap = 2.5F;
		float cw = (w - gap * (channels.length - 1)) / channels.length;
		Font cf = Fonts.medium(4.8F);
		for (int i = 0; i < channels.length; i++) {
			ColorChannel ch = channels[i];
			float cx = x + i * (cw + gap);
			boolean sel = (selectedChannel == ch);
			boolean hov = isVisible(y, 14.0F, visTop, visBot) && hovered(cx, y, cw, 14.0F, mx, my);
			IslandRender.drawRoundedRect(cx, y, cw, 14.0F, 4.0F, 4.0F, 4.0F, 4.0F,
				col(sel ? accent : BG_CARD, alpha * (sel ? 0.85F : (hov ? 0.95F : 0.80F))));
			IslandRender.drawCenteredText(cf, ch.label, cx + cw * 0.5F, y + 4.4F,
				col(sel ? 0xFF080B10 : TEXT_SUB, alpha));
			if (isVisible(y, 14.0F, visTop, visBot)) {
				hitZones.add(new HitZone(cx, y, cw, 14.0F, ch.hint, () -> {
					selectedChannel = ch;
					syncWheelFromChannel();
				}));
			}
		}
		return y + 17.5F;
	}

	// =========================================================================
	// Tab 1: Lyrics (Display Mode, Translations, Animation Speed, Sync Offset)
	// =========================================================================

	private float renderLyricsTab(
		float x, float y, float w, float alpha, int accent,
		double mx, double my, float visTop, float visBot
	) {
		boolean adv = IslandSettings.advancedSettingsMode;

		y = renderToggleCard(x, y, w, "Lyrics Enabled",
			"Show lyrics in World, Sky and the music bar; the music bar and display style stay intact.",
			IslandSettings.lyricsEnabled, alpha, accent, mx, my, visTop, visBot,
			() -> setLyricsVisible(!IslandSettings.lyricsEnabled));

		// 1. Lyric Display Mode Selector
		IslandSettings.DisplayMode curMode = IslandSettings.currentDisplayMode();
		y = sectionTitle(x, y, "Lyric Display", curMode.subtitle, alpha);

		IslandSettings.DisplayMode[] modes = IslandSettings.DisplayMode.values();
		float gap = 3.5F;
		float mw = (w - gap * (modes.length - 1)) / modes.length;
		Font mFont = Fonts.medium(5.4F);
		for (int i = 0; i < modes.length; i++) {
			IslandSettings.DisplayMode m = modes[i];
			float bx = x + i * (mw + gap);
			boolean sel = (curMode == m);
			boolean hov = isVisible(y, 17.0F, visTop, visBot) && hovered(bx, y, mw, 17.0F, mx, my);
			IslandRender.drawRoundedRect(bx, y, mw, 17.0F, 4.5F, 4.5F, 4.5F, 4.5F,
				col(sel ? accent : BG_CARD, alpha * (sel ? 0.88F : (hov ? 0.95F : 0.85F))));
			IslandRender.drawCenteredText(mFont, m.label, bx + mw * 0.5F, y + 5.5F,
				col(sel ? 0xFF080B10 : TEXT_MAIN, alpha));
			if (isVisible(y, 17.0F, visTop, visBot)) {
				hitZones.add(new HitZone(bx, y, mw, 17.0F, m.subtitle, () -> {
					IslandSettings.setDisplayMode(m);
					WorldLyrics.reset();
					IslandSettings.save();
				}));
			}
		}
		y += 21.0F;

		// Top Music Bar toggle
		y = renderToggleCard(
			x, y, w,
			"Top Music Bar (Dynamic Island)",
			"Shows the compact song bar at the top of your screen alongside World or Sky Lyrics.",
			IslandSettings.showHud,
			alpha, accent, mx, my, visTop, visBot,
			() -> {
				IslandSettings.showHud = !IslandSettings.showHud;
				IslandSettings.save();
			}
		);

		// 2. Show Translations
		y = renderToggleCard(
			x, y, w,
			"Translation — Show translations",
			"Displays translated or romanized lines underneath foreign-language songs when available.",
			IslandSettings.showTranslations,
			alpha, accent, mx, my, visTop, visBot,
			() -> {
				IslandSettings.showTranslations = !IslandSettings.showTranslations;
				IslandSettings.save();
			}
		);

		// These controls affect normal 3D world lyrics only, never the sky or HUD.
		y = sectionTitle(x, y, "Normal 3D World Lyrics", "These controls do not change Sky Lyrics or the top bar.", alpha);
		y = renderLabeledSliderCard("normal_distance", x, y, w, "Normal 3D Distance",
			"Forward blocks. Fixed groups use horizontal direction; changes apply to new groups.",
			String.format(Locale.ROOT, "%.2f blocks", IslandSettings.normalDistance),
			(float) IslandSettings.MIN_NORMAL_DISTANCE, (float) IslandSettings.MAX_NORMAL_DISTANCE,
			() -> (float) IslandSettings.normalDistance,
			v -> IslandSettings.normalDistance = Math.round(v * 4.0F) / 4.0,
			alpha, accent, visTop, visBot);
		y = renderLabeledSliderCard("normal_offset", x, y, w, "Normal 3D Offset",
			"Minus is left, plus is right; minimum 1.25 blocks. Side modes choose the sign.",
			String.format(Locale.ROOT, "%+.2f blocks", IslandSettings.normalOffset),
			(float) -IslandSettings.MAX_NORMAL_OFFSET, (float) IslandSettings.MAX_NORMAL_OFFSET,
			() -> (float) IslandSettings.normalOffset,
			v -> IslandSettings.setNormalOffset(Math.round(v * 4.0F) / 4.0),
			alpha, accent, visTop, visBot);
		markSearchTarget("Normal 3D Side", y);
		IslandRender.drawText(Fonts.medium(5.6F), "Normal 3D Side", x + 3.0F, y + 1.0F, col(TEXT_MAIN, alpha));
		y += 9.0F;
		IslandSettings.NormalSide[] sides = IslandSettings.NormalSide.values();
		float sideGap = 3.0F, sideWidth = (w - sideGap * 3.0F) / 4.0F;
		for (int i = 0; i < sides.length; i++) {
			IslandSettings.NormalSide side = sides[i];
			renderSmallButton(x + i * (sideWidth + sideGap), y, sideWidth, 15.0F, side.label,
				IslandSettings.normalSide == side, alpha, accent, mx, my, visTop, visBot,
				"Chosen once per new group. Alternate starts on the offset's sign; Random chooses either side. Locked lyrics keep their pin.",
				() -> { IslandSettings.setNormalSide(side); IslandSettings.save(); });
		}
		y += 19.0F;
		y = renderLabeledSliderCard("normal_size", x, y, w, "Normal 3D Text Size",
			"Physical letter size in the world. Does not change spawn distance or offset.",
			Math.round(IslandSettings.normalTextSize * 100.0F) + "%",
			IslandSettings.MIN_NORMAL_SIZE, IslandSettings.MAX_NORMAL_SIZE,
			() -> IslandSettings.normalTextSize, v -> IslandSettings.normalTextSize = Math.round(v * 20.0F) / 20.0F,
			alpha, accent, visTop, visBot);
		y = renderLabeledSliderCard("normal_words", x, y, w, "Normal 3D Words Per Line",
			"Wrap at word boundaries; long unbroken words stay whole.",
			IslandSettings.normalWordsPerLine + " words", IslandSettings.MIN_NORMAL_WORDS, IslandSettings.MAX_NORMAL_WORDS,
			() -> IslandSettings.normalWordsPerLine, v -> IslandSettings.normalWordsPerLine = Math.round(v),
			alpha, accent, visTop, visBot);
		y = renderLabeledSliderCard("normal_spacing", x, y, w, "Normal 3D Line Spacing",
			"Vertical gap between wrapped rows and translations, without overlapping letters.",
			Math.round(IslandSettings.normalLineSpacing * 100.0F) + "%",
			IslandSettings.MIN_NORMAL_SPACING, IslandSettings.MAX_NORMAL_SPACING,
			() -> IslandSettings.normalLineSpacing, v -> IslandSettings.normalLineSpacing = Math.round(v * 20.0F) / 20.0F,
			alpha, accent, visTop, visBot);
		y = renderToggleCard(x, y, w, "Normal 3D Curvature",
			"Bend the real 3D letters gently toward you. Off keeps the line flat.",
			IslandSettings.normalCurveEnabled, alpha, accent, mx, my, visTop, visBot,
			() -> { IslandSettings.normalCurveEnabled = !IslandSettings.normalCurveEnabled; IslandSettings.save(); });
		y = renderLabeledSliderCard("normal_curve", x, y, w, "Normal 3D Curve Amount",
			"Controls bending when curvature is on. Zero is flat; does not move the anchor.",
			Math.round(IslandSettings.normalCurve * 100.0F) + "%",
			IslandSettings.MIN_NORMAL_CURVE, IslandSettings.MAX_NORMAL_CURVE,
			() -> IslandSettings.normalCurve, v -> IslandSettings.normalCurve = Math.round(v * 20.0F) / 20.0F,
			alpha, accent, visTop, visBot);
		y = renderToggleCard(x, y, w, "Follow Camera Gaze",
			"Off: new groups start near your feet and stay fixed. On: follow your view.",
			IslandSettings.follow, alpha, accent, mx, my, visTop, visBot,
			() -> { IslandSettings.follow = !IslandSettings.follow; IslandSettings.save(); });
		y = renderToggleCard(x, y, w, "Hide Lyrics Behind You",
			"Normal 3D only: skip groups behind your view; they stay where they were.",
			IslandSettings.normalHideBehind, alpha, accent, mx, my, visTop, visBot,
			() -> { IslandSettings.normalHideBehind = !IslandSettings.normalHideBehind; IslandSettings.save(); });
		renderSmallButton(x, y, Math.min(w, 124.0F), 15.0F, "Reset Normal 3D", false,
			alpha, accent, mx, my, visTop, visBot,
			"Restore just the normal 3D distance, side, size, wrapping, curve and visibility defaults.",
			() -> { IslandSettings.resetNormalDefaults(); IslandSettings.save(); });
		y += 20.0F;

		// 3. Animation Style + Animation Speed
		y = sectionTitle(x, y, "Animation Style", "Choose how each new lyric line enters and moves.", alpha);
		IslandSettings.LyricAnimation[] anims = IslandSettings.LyricAnimation.values();
		float animGap = 2.5F;
		float aw = (w - animGap * (anims.length - 1)) / anims.length;
		Font af = Fonts.medium(4.8F);
		for (int i = 0; i < anims.length; i++) {
			IslandSettings.LyricAnimation an = anims[i];
			float ax = x + i * (aw + animGap);
			boolean sel = (IslandSettings.lyricAnimation == an);
			IslandRender.drawRoundedRect(ax, y, aw, 15.0F, 4.0F, 4.0F, 4.0F, 4.0F,
				col(sel ? accent : BG_CARD, alpha * (sel ? 0.86F : 0.85F)));
			IslandRender.drawCenteredText(af, an.shortLabel, ax + aw * 0.5F, y + 4.8F,
				col(sel ? 0xFF080B10 : TEXT_MAIN, alpha));
			if (isVisible(y, 15.0F, visTop, visBot)) {
				hitZones.add(new HitZone(ax, y, aw, 15.0F,
					an.label + ": " + an.description + ".",
					() -> {
						IslandSettings.lyricAnimation = an;
						IslandSettings.save();
					}));
			}
		}
		y += 19.0F;

		y = renderLabeledSliderCard(
			"anim_speed", x, y, w,
			"Animation Speed",
			"Controls how fast lyrics fade, rise, or pop when a new line starts.",
			String.format(Locale.ROOT, "%.2fx", IslandSettings.animationSpeed),
			IslandSettings.MIN_ANIM_SPEED, IslandSettings.MAX_ANIM_SPEED,
			() -> IslandSettings.animationSpeed,
			v -> IslandSettings.animationSpeed = Math.round(v * 20.0F) / 20.0F,
			alpha, accent, visTop, visBot
		);

		if (adv) {
			y = renderLabeledSliderCard(
				"smoothness", x, y, w,
				"Transition Smoothness",
				"Controls how softly the active karaoke color sweeps across each word.",
				String.format(Locale.ROOT, "%.2fx", IslandSettings.lyricSmoothness),
				IslandSettings.MIN_SMOOTHNESS, IslandSettings.MAX_SMOOTHNESS,
				() -> IslandSettings.lyricSmoothness,
				v -> IslandSettings.lyricSmoothness = Math.round(v * 20.0F) / 20.0F,
				alpha, accent, visTop, visBot
			);
			y = renderLabeledSliderCard(
				"line_spacing", x, y, w,
				"Line & Translation Spacing",
				"Sky and preview spacing. Normal 3D uses its own Line Spacing control above.",
				Math.round(IslandSettings.lyricLineSpacing * 100.0F) + "%",
				IslandSettings.MIN_LINE_SPACING, IslandSettings.MAX_LINE_SPACING,
				() -> IslandSettings.lyricLineSpacing,
				v -> IslandSettings.lyricLineSpacing = Math.round(v * 20.0F) / 20.0F,
				alpha, accent, visTop, visBot
			);
		}

		// 4. Per-Song Timing Sync Offset
		float off = tracker.getLyricsOffset();
		String offLabel = (off >= 0.0F ? "+" : "") + String.format(Locale.ROOT, "%.1fs", off);
		y = sectionTitle(x, y, "Song Lyric Timing (total " + offLabel + ")",
			"Saved here for this song; the island's quick nudge is temporary.", alpha);
		String[] nudges = { "-0.5s", "-0.1s", "Reset 0.0s", "+0.1s", "+0.5s" };
		float[] deltas = { -0.5F, -0.1F, 0.0F, 0.1F, 0.5F };
		float nw = (w - gap * 4.0F) / 5.0F;
		for (int i = 0; i < 5; i++) {
			float nx = x + i * (nw + gap);
			final int idx = i;
			renderSmallButton(nx, y, nw, 14.5F, nudges[i], i == 2 && Math.abs(off) < 0.05F,
				alpha, accent, mx, my, visTop, visBot,
				idx == 2 ? "Reset both saved and temporary timing for this song to 0.0 seconds."
					: ("Shift and save the total timing by " + nudges[idx] + " for this song."),
				() -> {
					if (idx == 2) tracker.setLyricsOffset(0.0F);
					else tracker.adjustLyricsOffset(deltas[idx]);
				});
		}
		y += 19.0F;

		// 5. Existing World Hologram Readability Controls (expanded in Advanced Mode)
		if (adv) {
			y = sectionTitle(x, y, "3D World Hologram Options", "Readability and facing options for floating world lyrics.", alpha);
			y = renderToggleCard(x, y, w, "Translucent Dark Backdrop Plate",
				"Draws a subtle Minecraft TextDisplay plate behind world lyrics for readability.",
				IslandSettings.hologramBackground, alpha, accent, mx, my, visTop, visBot,
				() -> { IslandSettings.hologramBackground = !IslandSettings.hologramBackground; IslandSettings.save(); });
			y = renderLabeledSliderCard("holo_bg_opacity", x, y, w, "World Background Opacity",
				"Strength of the dark backdrop plate when it is enabled.",
				Math.round(IslandSettings.hologramBgOpacity * 100.0F) + "%",
				IslandSettings.MIN_HOLOGRAM_BG_OPACITY, IslandSettings.MAX_HOLOGRAM_BG_OPACITY,
				() -> IslandSettings.hologramBgOpacity, v -> IslandSettings.hologramBgOpacity = v,
				alpha, accent, visTop, visBot);
			y = renderToggleCard(x, y, w, "Text Drop Shadow",
				"Adds a subtle 3D shadow behind world hologram letters.",
				IslandSettings.hologramShadow, alpha, accent, mx, my, visTop, visBot,
				() -> { IslandSettings.hologramShadow = !IslandSettings.hologramShadow; IslandSettings.save(); });
			y = renderToggleCard(x, y, w, "Visible Through Walls",
				"Allows normal 3D world lyrics to remain visible through blocks.",
				IslandSettings.throughBlocks, alpha, accent, mx, my, visTop, visBot,
				() -> { IslandSettings.throughBlocks = !IslandSettings.throughBlocks; IslandSettings.save(); });
		}

		return y;
	}

	// =========================================================================
	// Tab 2: Sky Lyrics (Plain-Language Radius + Height + Context Lines)
	// =========================================================================

	private float renderSkyTab(
		float x, float y, float w, float alpha, int accent,
		double mx, double my, boolean standalone, float visTop, float visBot
	) {
		boolean adv = IslandSettings.advancedSettingsMode;
		boolean skyOn = IslandSettings.worldText && IslandSettings.skyLyrics;

		// Quick Sky Mode Banner
		IslandRender.drawRoundedRect(x, y, w, 24.0F, 6.0F, 6.0F, 6.0F, 6.0F, col(BG_CARD, alpha * 0.90F));
		IslandRender.drawText(Fonts.medium(5.8F),
			skyOn ? "Sky Lyrics Mode is Active" : "Sky Lyrics Mode is Currently Off",
			x + 8.0F, y + 5.0F, col(skyOn ? accent : TEXT_MAIN, alpha));
		IslandRender.drawText(Fonts.regular(5.0F),
			"Lyrics stay together in one centered world-space location.",
			x + 8.0F, y + 13.5F, col(TEXT_SUB, alpha * 0.88F));
		float toggleBtnW = 82.0F;
		renderSmallButton(x + w - toggleBtnW - 6.0F, y + 5.0F, toggleBtnW, 14.0F,
			skyOn ? "Use World Lyrics" : "Enable Sky Lyrics", skyOn,
			alpha, accent, mx, my, visTop, visBot,
			"Switch between distant Sky Lyrics and normal 3D floating World Lyrics.",
			() -> {
				if (skyOn) {
					IslandSettings.setDisplayMode(IslandSettings.DisplayMode.WORLD_3D);
				} else {
					IslandSettings.setDisplayMode(IslandSettings.DisplayMode.SKY_LYRICS);
				}
				WorldLyrics.reset();
				IslandSettings.save();
			});
		y += 28.5F;
		y = renderSkyHorizonPreview(x, y, w, alpha);

		y = renderToggleCard(x, y, w, "Follow Look Direction",
			"Follows your left/right look only; looking up/down does not move the sky height.",
			IslandSettings.skyCrosshairFollow, alpha, accent, mx, my, visTop, visBot,
			() -> { IslandSettings.skyCrosshairFollow = !IslandSettings.skyCrosshairFollow; IslandSettings.save(); });

		// Distance changes reach; oversized wrapped groups may auto-fit the view.
		y = renderLabeledSliderCard(
			"sky_radius", x, y, w,
			"Sky Lyrics Distance",
			"How far lyrics can float: radius of an invisible boundary around you.",
			Math.round(IslandSettings.skyBoundaryRadius) + " blocks",
			(float) IslandSettings.MIN_SKY_BOUNDARY_RADIUS,
			(float) IslandSettings.MAX_SKY_BOUNDARY_RADIUS,
			() -> (float) IslandSettings.skyBoundaryRadius,
			v -> { IslandSettings.skyBoundaryRadius = Math.round(v); WorldLyrics.onSkySettingsChanged(); },
			alpha, accent, visTop, visBot
		);

		if (adv) {
			IslandRender.drawRoundedRect(x, y, w, 18.0F, 5.0F, 5.0F, 5.0F, 5.0F, col(BG_CARD, alpha * 0.85F));
			IslandRender.drawText(Fonts.medium(5.4F), "Exact Sky Radius (80–1000 blocks):", x + 8.0F, y + 6.0F, col(TEXT_SUB, alpha));
			renderEditableBox(
				x + w - 88.0F, y + 3.2F, 82.0F, 11.5F,
				"Blocks", String.valueOf(Math.round(IslandSettings.skyBoundaryRadius)), EditTarget.SKY_RADIUS,
				alpha, accent, mx, my, standalone, visTop, visBot,
				"Click to type an exact Sky Lyrics radius in blocks (80 to 1000), then press Enter."
			);
			y += 22.0F;
		}

		y = renderLabeledSliderCard(
			"sky_size", x, y, w, "Sky Lyrics Text Size",
			"Preferred letter size; long wrapped lyrics may shrink to fit the view.",
			Math.round(IslandSettings.skyTextSize * 100.0F) + "%",
			IslandSettings.MIN_SKY_TEXT_SIZE, IslandSettings.MAX_SKY_TEXT_SIZE,
			() -> IslandSettings.skyTextSize,
			v -> { IslandSettings.skyTextSize = v; WorldLyrics.onSkySettingsChanged(); },
			alpha, accent, visTop, visBot);


		// Sky Height
		y = renderLabeledSliderCard(
			"sky_height", x, y, w,
			"Sky Height",
			"Sets the fixed sky height; up/down camera movement does not change it.",
			Math.round(IslandSettings.skyHeight) + " blocks",
			(float) IslandSettings.MIN_SKY_HEIGHT, (float) IslandSettings.MAX_SKY_HEIGHT,
			() -> (float) IslandSettings.skyHeight,
			v -> IslandSettings.skyHeight = Math.round(v),
			alpha, accent, visTop, visBot
		);

		// Show Surrounding Lines
		y = renderToggleCard(
			x, y, w,
			"Show Previous & Next Lines",
			"Shows one fading previous line above and a next preview below the current lyric.",
			IslandSettings.skyContextLines,
			alpha, accent, mx, my, visTop, visBot,
			() -> {
				IslandSettings.skyContextLines = !IslandSettings.skyContextLines;
				IslandSettings.save();
			}
		);

		y = renderToggleCard(x, y, w, "Show Song Header",
			"Shows the song title and artist above the current Sky lyric at the start.",
			IslandSettings.skySongHeader, alpha, accent, mx, my, visTop, visBot,
			() -> { IslandSettings.skySongHeader = !IslandSettings.skySongHeader; IslandSettings.save(); });

		if (adv) {
			y = renderLabeledSliderCard(
				"sky_curve", x, y, w,
				"Sky Arc Curvature",
				"Controls how strongly Sky Lyrics wrap in a circle around your view (100% = full horizon arc).",
				Math.round(IslandSettings.skyCurve * 100.0F) + "%",
				0.0F, 1.5F,
				() -> IslandSettings.skyCurve,
				v -> IslandSettings.skyCurve = v,
				alpha, accent, visTop, visBot
			);
			y = renderToggleCard(
				x, y, w,
				"Beat Pulse Reaction",
				"Subtly pulses Sky Lyrics scale and glow on musical beats.",
				IslandSettings.skyBeatPulse,
				alpha, accent, mx, my, visTop, visBot,
				() -> { IslandSettings.skyBeatPulse = !IslandSettings.skyBeatPulse; IslandSettings.save(); }
			);
			y = renderToggleCard(
				x, y, w,
				"Visible Through Mountains & Roofs",
				"Renders Sky Lyrics cleanly even when blocks or roofs are overhead.",
				IslandSettings.skyThroughBlocks,
				alpha, accent, mx, my, visTop, visBot,
				() -> { IslandSettings.skyThroughBlocks = !IslandSettings.skyThroughBlocks; IslandSettings.save(); }
			);
		}

		return y;
	}

	// =========================================================================
	// Tab 3: Music & Sources (Live Status, Retry, Multi-Provider Toggles)
	// =========================================================================

	private float renderMusicSourcesTab(
		float x, float y, float w, float alpha, int accent,
		double mx, double my, float visTop, float visBot
	) {
		// 1. Live Now Playing & Sync Status Card
		markSearchTarget("Music Status", y);
		IslandRender.drawRoundedRect(x, y, w, 44.0F, 6.0F, 6.0F, 6.0F, 6.0F, col(BG_CARD, alpha * 0.92F));
		IMediaSession session = tracker.getSession();
		Font bold = Fonts.medium(5.8F);
		Font reg = Fonts.regular(5.1F);

		if (session != null && session.getMedia() != null) {
			MediaInfo media = session.getMedia();
			TrackMetadata.QueryPair disp = TrackMetadata.displayPair(media.getArtist(), media.getTitle());
			String songLine = disp.artist().isEmpty() ? disp.title() : (disp.title() + " — " + disp.artist());
			IslandRender.drawWindowedText(bold, songLine, x + 8.0F, y + 5.0F, col(TEXT_MAIN, alpha),
				x + 7.0F, x + w - 8.0F, 8.0F, 0.0F);

			LyricsFetcher.SyncQuality sq = tracker.getSyncQuality();
			int badgeCol = switch (sq) {
				case WORD_SYNCED -> 0xFF3EF3A8;
				case LINE_SYNCED -> 0xFF58C7FF;
				case ESTIMATED_PACE -> 0xFFFFB84D;
				case PLAIN_UNSYNCED -> 0xFFAAB4C8;
				case NOT_FOUND, PROVIDER_UNAVAILABLE -> 0xFFFF6B7A;
			};
			String statusText = tracker.getStatusHeadline();
			IslandRender.drawWindowedText(reg, statusText, x + 8.0F, y + 14.5F, col(badgeCol, alpha * 0.95F),
				x + 7.0F, x + w - 8.0F, 6.0F, 0.0F);
		} else {
			IslandRender.drawText(bold, "No Active Music Player Detected", x + 8.0F, y + 5.2F, col(TEXT_MAIN, alpha));
			IslandRender.drawText(reg, "Play a song in Spotify or YouTube in your browser — LT5Lyrics connects automatically.",
				x + 8.0F, y + 14.5F, col(TEXT_SUB, alpha * 0.90F));
		}

		float btnY = y + 26.0F;
		float bw = (w - 16.0F - 6.0F) / 3.0F;
		renderSmallButton(x + 8.0F, btnY, bw, 13.5F, "Retry Lyrics", false, alpha, accent, mx, my, visTop, visBot,
			"Clear cached result for this song and search all enabled lyric providers again.",
			() -> {
				tracker.reloadLyrics();
				flashStatus("Retrying lyric search...");
			});
		renderSmallButton(x + 8.0F + bw + 3.0F, btnY, bw, 13.5F, "Switch Player", false, alpha, accent, mx, my, visTop, visBot,
			Component.translatable("lt5lyrics.player.switch_hint").getString(),
			() -> {
				String next = tracker.cyclePreferredSource();
				flashStatus(next != null ? ("Switched to " + next) : "Only 1 active player found");
			});
		renderSmallButton(x + 8.0F + (bw + 3.0F) * 2.0F, btnY, bw, 13.5F, "Lyric Mapper", false, alpha, accent, mx, my, visTop, visBot,
			"Open the manual Lyric Mapper to tap-sync custom .lrc timestamps.",
			callbacks::onOpenMapper);
		y += 49.0F;

		y = renderToggleCard(x, y, w, "Prefer Word Timings",
			"Use real word timing from enabled sources; order decides ties. OFF keeps first timed result.",
			IslandSettings.preferWordTiming, alpha, accent, mx, my, visTop, visBot,
			() -> { IslandSettings.preferWordTiming = !IslandSettings.preferWordTiming;
				IslandSettings.save(); LyricsFetcher.clearCache(); tracker.reloadLyrics(); });
		// Priority breaks ties in sync quality; the preference can restore the old first-timed behavior.
		y = sectionTitle(x, y, "Provider Priority",
			"Order decides ties; timed results beat plain text.", alpha);
		for (String id : IslandSettings.normalizedProviderPriority()) {
			String name = IslandSettings.providerDisplayName(id);
			markSearchTarget(name, y);
			boolean enabled = IslandSettings.isProviderEnabled(id);
			String detail = IslandSettings.providerSubtitle(id);
			if ("musixmatch".equals(id) && (IslandSettings.musixmatchApiKey == null || IslandSettings.musixmatchApiKey.isBlank())) {
				detail = "Optional personal key required (Advanced tab)";
			}
			IslandRender.drawRoundedRect(x, y, w, 23.0F, 5.5F, 5.5F, 5.5F, 5.5F, col(BG_CARD, alpha * 0.86F));
			IslandRender.drawText(Fonts.medium(5.6F), name, x + 8.0F, y + 4.0F, col(TEXT_MAIN, alpha));
			IslandRender.drawWindowedText(Fonts.regular(4.8F), detail, x + 8.0F, y + 13.0F,
				col(TEXT_SUB, alpha * 0.86F), x + 7.0F, x + w - 76.0F, 4.0F, 0.0F);
			float controls = x + w - 73.0F;
			renderSmallButton(controls, y + 5.0F, 34.0F, 13.0F, enabled ? "ON" : "OFF", enabled,
				alpha, accent, mx, my, visTop, visBot, "Enable or disable " + name, () -> {
					IslandSettings.setProviderEnabled(id, !IslandSettings.isProviderEnabled(id));
					IslandSettings.save(); LyricsFetcher.clearCache(); tracker.reloadLyrics();
				});
			renderSmallButton(controls + 37.0F, y + 5.0F, 14.0F, 13.0F, "Up", false,
				alpha, accent, mx, my, visTop, visBot, "Move " + name + " earlier in the search order", () -> {
					IslandSettings.moveProviderPriority(id, -1); LyricsFetcher.clearCache(); tracker.reloadLyrics();
				});
			renderSmallButton(controls + 53.0F, y + 5.0F, 14.0F, 13.0F, "Down", false,
				alpha, accent, mx, my, visTop, visBot, "Move " + name + " later in the search order", () -> {
					IslandSettings.moveProviderPriority(id, 1); LyricsFetcher.clearCache(); tracker.reloadLyrics();
				});
			y += 27.0F;
		}

		// 3. Honest Auto-Pace Toggle
		y = renderToggleCard(x, y, w, "Auto-Pace Plain Text Lyrics (Estimated Pace)",
			"Estimates steady line pacing when only unsynced text exists. Clearly labeled as Estimated Pace.",
			IslandSettings.autoPaceUnsyncedLyrics, alpha, accent, mx, my, visTop, visBot,
			() -> {
				IslandSettings.autoPaceUnsyncedLyrics = !IslandSettings.autoPaceUnsyncedLyrics;
				IslandSettings.save();
				tracker.reloadLyrics();
			});

		return y;
	}

	// =========================================================================
	// Tab 4: Advanced (Per-Channel Colors, Outlines, Fonts, HUD, Optional Keys)
	// =========================================================================

	private float renderAdvancedTab(
		float x, float y, float w, float alpha, int accent,
		double mx, double my, boolean standalone, float visTop, float visBot
	) {
		y = sectionTitle(x, y, "Separate Lyric Element Colors",
			"Customize primary, karaoke highlight, translation, glow, and outline colors independently.", alpha);

		y = renderToggleCard(x, y, w, "Distinct Karaoke Word Highlight Sweep",
			"Sweeps a separate highlight color across the currently sung word.",
			IslandSettings.separateHighlightColor, alpha, accent, mx, my, visTop, visBot,
			() -> {
				IslandSettings.separateHighlightColor = !IslandSettings.separateHighlightColor;
				IslandSettings.save();
			});

		y = renderToggleCard(x, y, w, "Separate Custom Glow Color",
			"Keeps a custom glow halo color instead of auto-matching your primary lyric color.",
			IslandSettings.separateGlowColor, alpha, accent, mx, my, visTop, visBot,
			() -> {
				IslandSettings.separateGlowColor = !IslandSettings.separateGlowColor;
				IslandSettings.save();
			});

		y = renderChannelSelectorBar(x, y, w, alpha, accent, mx, my, visTop, visBot);

		int chCol = currentChannelColor();
		IslandRender.drawRoundedRect(x, y, w, 20.0F, 5.5F, 5.5F, 5.5F, 5.5F, col(BG_CARD, alpha * 0.88F));
		IslandRender.drawRoundedRect(x + 6.0F, y + 4.0F, 12.0F, 12.0F, 3.5F, 3.5F, 3.5F, 3.5F, col(chCol, alpha));
		float boxW = (w - 30.0F) * 0.5F;
		renderEditableBox(x + 22.0F, y + 4.2F, boxW, 11.5F, "HEX", ColorUtil.toHex(chCol), EditTarget.HEX,
			alpha, accent, mx, my, standalone, visTop, visBot,
			"Click to enter an exact #RRGGBB color for " + selectedChannel.label + ".");
		renderEditableBox(x + 26.0F + boxW, y + 4.2F, boxW, 11.5F, "RGB", ColorUtil.toRgbString(chCol), EditTarget.RGB,
			alpha, accent, mx, my, standalone, visTop, visBot,
			"Click to enter exact R, G, B (0-255) values for " + selectedChannel.label + ".");
		y += 24.5F;

		// Precision Glow / Outline / Scale / Spacing
		y = renderLabeledSliderCard("adv_glow", x, y, w, "Glow Intensity",
			"Strength of the soft outer glow halo behind lyrics.",
			Math.round(IslandSettings.lyricGlowIntensity * 100.0F) + "%", 0.0F, 1.0F,
			() -> IslandSettings.lyricGlowIntensity,
			v -> { IslandSettings.lyricGlowIntensity = v; IslandSettings.skyGlow = v; },
			alpha, accent, visTop, visBot);

		y = renderLabeledSliderCard("adv_outline", x, y, w, "Outline Thickness",
			"Crisp stroke border thickness around every character.",
			Math.round(IslandSettings.lyricOutlineThickness * 100.0F) + "%", 0.0F, 1.0F,
			() -> IslandSettings.lyricOutlineThickness,
			v -> IslandSettings.lyricOutlineThickness = v,
			alpha, accent, visTop, visBot);

		y = renderLabeledSliderCard("adv_scale", x, y, w, "Exact Text Scale",
			"Fine-grained multiplier for lyric text size (0.50x to 2.00x).",
			String.format(Locale.ROOT, "%.2fx", IslandSettings.lyricTextScale),
			IslandSettings.MIN_LYRIC_TEXT_SCALE, IslandSettings.MAX_LYRIC_TEXT_SCALE,
			() -> IslandSettings.lyricTextScale,
			v -> IslandSettings.lyricTextScale = Math.round(v * 20.0F) / 20.0F,
			alpha, accent, visTop, visBot);

		y = renderLabeledSliderCard("adv_spacing", x, y, w, "Line & Translation Spacing",
			"Sky and preview spacing. Normal 3D uses its own Line Spacing in Lyrics.",
			Math.round(IslandSettings.lyricLineSpacing * 100.0F) + "%",
			IslandSettings.MIN_LINE_SPACING, IslandSettings.MAX_LINE_SPACING,
			() -> IslandSettings.lyricLineSpacing,
			v -> IslandSettings.lyricLineSpacing = Math.round(v * 20.0F) / 20.0F,
			alpha, accent, visTop, visBot);

		y = renderLabeledSliderCard("hud_opacity", x, y, w, "HUD Opacity",
			"Transparency of the top music bar background.", Math.round(IslandSettings.hudOpacity * 100.0F) + "%",
			IslandSettings.MIN_HUD_OPACITY, IslandSettings.MAX_HUD_OPACITY,
			() -> IslandSettings.hudOpacity, v -> IslandSettings.hudOpacity = v,
			alpha, accent, visTop, visBot);

		// Typography & Fonts
		y = sectionTitle(x, y, "Typography & Dynamic Island HUD",
			"Active Font: " + IslandSettings.activeFontLabel() + " (drop custom .ttf/.otf in config/lt5lyrics/fonts)", alpha);

		float halfW = (w - 4.0F) * 0.5F;
		renderSmallButton(x, y, halfW, 15.0F, "Font: " + IslandSettings.activeFontLabel(), false, alpha, accent, mx, my, visTop, visBot,
			"Cycle through built-in typefaces and any custom .ttf/.otf fonts in config/lt5lyrics/fonts.",
			() -> {
				String next = IslandSettings.cycleFontChoice(1);
				WorldLyrics.onSkySettingsChanged();
				flashStatus("Font: " + next);
			});
		renderSmallButton(x + halfW + 4.0F, y, halfW, 15.0F, "Billboard: " + IslandSettings.hologramBillboard.label, false, alpha, accent, mx, my, visTop, visBot,
			"Cycle how 3D world lyric holograms face your camera.",
			() -> {
				IslandSettings.hologramBillboard = IslandSettings.hologramBillboard.next();
				IslandSettings.save();
			});
		y += 19.0F;

		// HUD Controls Row
		float hw = (w - 6.0F) / 3.0F;
		renderSmallButton(x, y, hw, 14.5F, "Move Top Bar", false, alpha, accent, mx, my, visTop, visBot,
			"Drag the Dynamic Island music bar anywhere on your screen.", callbacks::onStartDragHud);
		renderSmallButton(x + hw + 3.0F, y, hw, 14.5F, "Viz: " + IslandSettings.visualizerStyle.label, false, alpha, accent, mx, my, visTop, visBot,
			"Cycle the Dynamic Island audio visualizer style.",
			() -> { IslandSettings.visualizerStyle = IslandSettings.visualizerStyle.next(); IslandSettings.save(); });
		renderSmallButton(x + (hw + 3.0F) * 2.0F, y, hw, 14.5F, "Clock: " + IslandSettings.clockMode.label, false, alpha, accent, mx, my, visTop, visBot,
			"Cycle the optional clock next to the Dynamic Island.",
			() -> { IslandSettings.clockMode = IslandSettings.clockMode.next(); IslandSettings.save(); });
		y += 19.0F;

		// Optional Personal Provider API Keys
		y = sectionTitle(x, y, "Optional Personal Provider API Keys",
			"Many sources work without keys; optional keys unlock additional API access.", alpha);

		y = renderToggleCard(x, y, w, "Musixmatch Official Developer API",
			"Uses your personal Musixmatch key when configured. Automatically disabled when key is blank.",
			IslandSettings.enableMusixmatch, alpha, accent, mx, my, visTop, visBot,
			() -> { IslandSettings.enableMusixmatch = !IslandSettings.enableMusixmatch; IslandSettings.save(); LyricsFetcher.clearCache(); });

		y = renderApiKeyCard(x, y, w, "Musixmatch Key", IslandSettings.musixmatchApiKey, EditTarget.MUSIXMATCH_KEY,
			alpha, accent, mx, my, standalone, visTop, visBot,
			"Optional personal key from developer.musixmatch.com (never logged).");

		y = renderApiKeyCard(x, y, w, "BetterLyrics Key", IslandSettings.betterLyricsApiKey, EditTarget.BETTERLYRICS_KEY,
			alpha, accent, mx, my, standalone, visTop, visBot,
			"Optional key for uncached BetterLyrics tracks (cached tracks already work free without a key).");

		renderSmallButton(x, y, w, 16.0F, "Clear Lyric Cache & Retry Current Song", false, alpha, accent, mx, my, visTop, visBot,
			"Clears positive & negative lyric caches and immediately re-fetches the active song.",
			() -> {
				tracker.reloadLyrics();
				flashStatus("Cleared lyric cache");
			});
		return y + 20.0F;
	}

	private void selectTab(int next, boolean rememberHistory) {
		next = Math.clamp(next, 0, TAB_NAMES.length - 1);
		if (next != activeTab && rememberHistory) {
			tabHistory.push(activeTab);
			while (tabHistory.size() > 20) tabHistory.removeLast();
		}
		activeTab = next;
		IslandSettings.lastStudioTab = next;
		IslandSettings.save();
		focusIndex = -1;
		syncWheelFromChannel();
	}

	private void backTab() {
		if (!searchQuery.isBlank()) { searchQuery = ""; searchScroll = 0.0F; editTarget = EditTarget.NONE; return; }
		if (!tabHistory.isEmpty()) selectTab(tabHistory.pop(), false);
		else flashStatus("No earlier category");
	}

	private float renderSearchBar(float x, float y, float w, float alpha, int accent,
			double mx, double my, boolean standalone) {
		float backW = 34.0F, clearW = 30.0F, gap = 3.0F, h = 13.0F;
		renderSmallButton(x, y, backW, h, "Back", false, alpha, accent, mx, my, y, y + h,
			"Return to your previous category", this::backTab);
		float fieldX = x + backW + gap, fieldW = Math.max(30.0F, w - backW - clearW - 2.0F * gap);
		renderEditableBox(fieldX, y, fieldW, h, "Search", searchQuery.isBlank() ? "Ctrl+F or /" : searchQuery,
			EditTarget.SEARCH, alpha, accent, mx, my, standalone, y, y + h,
			"Search settings across all five categories as you type.");
		renderSmallButton(x + w - clearW, y, clearW, h, "Clear", false, alpha, accent, mx, my, y, y + h,
			"Clear the settings search", () -> { searchQuery = ""; editBuffer = ""; searchScroll = 0.0F; });
		return y + h;
	}

	private static boolean searchMatches(SearchEntry e, String query) {
		String hay = (e.title() + " " + e.keywords() + " " + TAB_NAMES[e.tab()]).toLowerCase(Locale.ROOT);
		for (String token : query.toLowerCase(Locale.ROOT).trim().split("\\s+")) {
			if (!token.isEmpty() && !hay.contains(token)) return false;
		}
		return true;
	}

	static List<String> matchingSearchTitles(String query) {
		if (query == null || query.isBlank()) return List.of();
		return SEARCH_INDEX.stream().filter(e -> searchMatches(e, query)).map(SearchEntry::title).toList();
	}

	private float renderSearchResults(float x, float y, float w, float alpha, int accent,
			double mx, double my, float visTop, float visBot) {
		IslandRender.drawText(Fonts.medium(5.6F), "Search all settings", x + 2.0F, y + 1.0F, col(accent, alpha));
		y += 11.0F;
		int found = 0;
		for (SearchEntry entry : SEARCH_INDEX) {
			if (!searchMatches(entry, searchQuery)) continue;
			found++;
			boolean hov = isVisible(y, 20.0F, visTop, visBot) && hovered(x, y, w, 20.0F, mx, my);
			IslandRender.drawRoundedRect(x, y, w, 20.0F, 4.0F, 4.0F, 4.0F, 4.0F, col(BG_CARD, alpha * (hov ? 0.98F : 0.85F)));
			IslandRender.drawText(Fonts.medium(5.5F), entry.title(), x + 7.0F, y + 3.0F, col(TEXT_MAIN, alpha));
			IslandRender.drawText(Fonts.regular(4.8F), TAB_NAMES[entry.tab()] + (entry.advanced() ? " • Advanced" : ""),
				x + 7.0F, y + 11.5F, col(TEXT_SUB, alpha));
			if (isVisible(y, 20.0F, visTop, visBot)) {
				hitZones.add(new HitZone(x, y, w, 20.0F, "Open " + entry.title(), () -> {
					searchQuery = ""; editBuffer = ""; editTarget = EditTarget.NONE; searchScroll = 0.0F;
					if (entry.advanced()) IslandSettings.advancedSettingsMode = true;
					selectTab(entry.tab(), true); pendingJump = entry.target();
				}));
			}
			y += 23.0F;
		}
		if (found == 0) {
			IslandRender.drawText(Fonts.regular(5.3F), "No settings found. Try another word.", x + 3.0F, y + 2.0F, col(TEXT_SUB, alpha));
			y += 15.0F;
		}
		return y;
	}

	private void markSearchTarget(String title, float y) {
		if (pendingJump == null || !Float.isNaN(jumpY)) return;
		if (title.equalsIgnoreCase(pendingJump) || title.toLowerCase(Locale.ROOT).startsWith(pendingJump.toLowerCase(Locale.ROOT))) jumpY = y;
	}

	private void scrollBy(float delta) {
		if (!IslandSettings.advancedSettingsMode) {
			simpleScroll[simplePage] = Math.clamp(simpleScroll[simplePage] + delta, 0.0F, maxContentScroll);
			return;
		}
		if (searchQuery.isBlank()) tabScroll[activeTab] = Math.clamp(tabScroll[activeTab] + delta, 0.0F, maxContentScroll);
		else searchScroll = Math.clamp(searchScroll + delta, 0.0F, maxContentScroll);
	}

	private float renderCategoryResets(float x, float y, float w, float alpha, int accent,
			double mx, double my, float visTop, float visBot) {
		if (activeTab != 0) {
			String name = TAB_NAMES[activeTab];
			renderSmallButton(x, y, w, 16.0F, "Reset " + name + " Defaults", false,
				alpha, accent, mx, my, visTop, visBot, "Reset just this category after confirmation", () ->
				requestConfirmation("Reset " + name + "?", "Only this category is reset. Personal API keys are kept.", () -> {
					switch (activeTab) {
						case 1 -> { IslandSettings.resetLyricsCategory(); tracker.setLyricsOffset(0.0F); WorldLyrics.reset(); }
						case 2 -> { IslandSettings.resetSkyDefaults(); WorldLyrics.onSkySettingsChanged(); }
						case 3 -> { IslandSettings.resetSourceDefaults(); LyricsFetcher.clearCache(); tracker.reloadLyrics(); }
						default -> IslandSettings.resetAdvancedCategory();
					}
					syncWheelFromChannel(); flashStatus(name + " defaults restored");
				}));
			y += 20.0F;
		}
		if (activeTab == 4) {
			markSearchTarget("Reset All Settings", y);
			renderSmallButton(x, y, w, 16.0F, "Reset All Settings", false, alpha, accent, mx, my, visTop, visBot,
				"Reset all settings, including saved API keys, after confirmation", () ->
				requestConfirmation("Reset ALL settings?", "This also removes saved API keys and all song timing offsets.", () -> {
					IslandSettings.resetAllSettingsToFactory(); WorldLyrics.reset(); LyricsFetcher.clearCache();
					tracker.resetAllLyricOffsets(); tracker.reloadLyrics(); selectTab(0, false); tabHistory.clear(); syncWheelFromChannel();
					flashStatus("All settings reset");
				}));
			y += 20.0F;
		}
		return y;
	}

	private void requestConfirmation(String title, String description, Runnable action) {
		if (editTarget != EditTarget.NONE) commitEditBuffer();
		confirmation = new PendingConfirmation(title, description, action);
		focusIndex = -1;
	}

	private void confirmPending() {
		PendingConfirmation pending = confirmation;
		confirmation = null;
		if (pending != null) pending.action().run();
	}

	private void renderConfirmation(float x, float y, float w, float h, float alpha, int accent, double mx, double my) {
		hitZones.clear(); sliderBindings.clear();
		IslandRender.drawRoundedRect(x, y, w, h, 8.0F, 8.0F, 8.0F, 8.0F, col(0xFF05070C, alpha * 0.82F));
		float cw = Math.min(w - 20.0F, 270.0F), ch = 69.0F;
		float cx = x + (w - cw) * 0.5F, cy = y + (h - ch) * 0.5F;
		IslandRender.drawRoundedRect(cx, cy, cw, ch, 6.5F, 6.5F, 6.5F, 6.5F, col(0xFF202737, alpha));
		IslandRender.drawText(Fonts.medium(7.0F), confirmation.title(), cx + 9.0F, cy + 8.0F, col(TEXT_MAIN, alpha));
		IslandRender.drawWindowedText(Fonts.regular(5.1F), confirmation.description(), cx + 9.0F, cy + 22.0F,
			col(TEXT_SUB, alpha), cx + 8.0F, cx + cw - 8.0F, 4.0F, 0.0F);
		IslandRender.drawText(Fonts.regular(4.8F), "Enter = confirm · Esc = cancel", cx + 9.0F, cy + 32.0F, col(TEXT_DIM, alpha));
		float bw = (cw - 22.0F) * 0.5F;
		renderSmallButton(cx + 8.0F, cy + 45.0F, bw, 16.0F, "Cancel", false, alpha, accent, mx, my, cy, cy + ch,
			"Cancel without changing settings", () -> confirmation = null);
		renderSmallButton(cx + 14.0F + bw, cy + 45.0F, bw, 16.0F, "Confirm Reset", true, alpha, accent, mx, my, cy, cy + ch,
			"Confirm reset", this::confirmPending);
	}

	private void rebuildKeyboardFocus(float alpha, int accent) {
		focusItems.clear();
		for (int i = 0; i < hitZones.size(); i++) {
			HitZone hz = hitZones.get(i);
			float top = hz.y(), bottom = hz.y() + hz.h();
			if (i >= bodyHitStart) { top = Math.max(top, bodyTopLast); bottom = Math.min(bottom, bodyBottomLast); }
			if (bottom > top) focusItems.add(new FocusItem(hz.x(), top, hz.w(), bottom - top, hz, null, false));
		}
		for (SliderBinding sb : sliderBindings) {
			float top = Math.max(sb.y() - 3.0F, bodyTopLast), bottom = Math.min(sb.y() + sb.h() + 3.0F, bodyBottomLast);
			if (bottom > top) focusItems.add(new FocusItem(sb.x(), top, sb.w(), bottom - top, null, sb, false));
		}
		if (wheelVisible && searchQuery.isBlank() && activeTab == 0) {
			float top = Math.max(lastWheelCy - lastWheelRadius, bodyTopLast), bottom = Math.min(lastWheelCy + lastWheelRadius, bodyBottomLast);
			if (bottom > top) focusItems.add(new FocusItem(lastWheelCx - lastWheelRadius, top, lastWheelRadius * 2, bottom - top, null, null, true));
		}
		focusItems.sort(Comparator.comparingDouble(FocusItem::y).thenComparingDouble(FocusItem::x));
		if (focusIndex >= focusItems.size()) focusIndex = -1;
		if (focusIndex >= 0) {
			FocusItem f = focusItems.get(focusIndex);
			float r = 0.8F;
			IslandRender.drawRoundedRect(f.x(), f.y(), f.w(), r, 0, 0, 0, 0, col(accent, alpha));
			IslandRender.drawRoundedRect(f.x(), f.y() + f.h() - r, f.w(), r, 0, 0, 0, 0, col(accent, alpha));
			IslandRender.drawRoundedRect(f.x(), f.y(), r, f.h(), 0, 0, 0, 0, col(accent, alpha));
			IslandRender.drawRoundedRect(f.x() + f.w() - r, f.y(), r, f.h(), 0, 0, 0, 0, col(accent, alpha));
		}
	}

	/** A simple 2D illustration of the single centered Sky slot. */
	private float renderSkyHorizonPreview(float x, float y, float w, float alpha) {
		float h = 57.0F;
		IslandRender.drawRoundedRect(x, y, w, h, 6.0F, 6.0F, 6.0F, 6.0F, col(0xFF101B2A, alpha * 0.97F));
		IslandRender.drawText(Fonts.medium(5.2F), "CENTERED SKY PREVIEW", x + 7.0F, y + 4.0F, col(TEXT_MAIN, alpha));
		String distance = Math.round(IslandSettings.skyBoundaryRadius) + " blocks | text " + Math.round(IslandSettings.skyTextSize * 100.0F) + "%";
		IslandRender.drawText(Fonts.regular(4.8F), distance, x + w - 7.0F - Fonts.regular(4.8F).width(distance), y + 4.0F, col(TEXT_SUB, alpha));
		float horizonY = y + 40.0F;
		IslandRender.drawRoundedRect(x + 5.0F, horizonY, w - 10.0F, 0.8F, 0, 0, 0, 0, col(0xFF8BA9C6, alpha * 0.35F));
		double radius = WorldLyrics.clampSkyBoundaryRadius(IslandSettings.skyBoundaryRadius);
		double depth = SkyStableLayout.slotDistance(radius);
		float scale = WorldLyrics.computeSkyScale(IslandSettings.skyTextSize) * IslandSettings.lyricTextScale;
		float point = Math.clamp((float) (1100.0 * scale / depth), 3.7F, 9.0F);
		Font font = Fonts.lyric(point);
		float fade = WorldLyrics.computeSkyHorizonFade(depth, radius);
		int fill = col(IslandSettings.resolveSkyPrimaryColor(), alpha * fade);
		int glow = col(IslandSettings.resolveGlowColor(), alpha * fade * 0.6F * IslandSettings.effectiveSkyGlow());
		int outline = col(IslandSettings.resolveOutlineColor(), alpha * fade);
		IslandRender.pushScissor(x + 4.0F, y + 11.0F, w - 8.0F, 30.0F);
		String first = "Current lyric stays here", second = "long lines wrap below";
		IslandRender.drawStyledLyric2D(font, first, x + w * 0.5F - font.width(first) * 0.5F, y + 17.0F,
				fill, null, -1, glow, IslandSettings.effectiveSkyGlow(), outline, IslandSettings.lyricOutlineThickness,
				IslandSettings.hologramShadow, true, x + 4.0F, x + w - 4.0F, 0.0F, 0.0F);
		IslandRender.drawStyledLyric2D(font, second, x + w * 0.5F - font.width(second) * 0.5F, y + 19.5F + point,
				fill, null, -1, glow, IslandSettings.effectiveSkyGlow(), outline, IslandSettings.lyricOutlineThickness,
				IslandSettings.hologramShadow, true, x + 4.0F, x + w - 4.0F, 0.0F, 0.0F);
		IslandRender.popScissor();
		IslandRender.drawText(Fonts.regular(4.8F), "2D illustration, not the game camera. Verify in-game.", x + 7.0F, y + 46.0F, col(TEXT_SUB, alpha));
		return y + h + 5.0F;
	}

	// =========================================================================
	// Reusable Clean Studio Widgets
	// =========================================================================

	private float sectionTitle(float x, float y, String title, String subtitle, float alpha) {
		markSearchTarget(title, y);
		IslandRender.drawText(Fonts.medium(5.8F), title, x + 2.0F, y + 1.0F, col(TEXT_MAIN, alpha * 0.96F));
		IslandRender.drawText(Fonts.regular(4.9F), subtitle, x + 2.0F, y + 8.5F, col(TEXT_DIM, alpha * 0.92F));
		return y + 16.5F;
	}

	private float renderToggleCard(
		float x, float y, float w, String title, String description, boolean value,
		float alpha, int accent, double mx, double my, float visTop, float visBot, Runnable onToggle
	) {
		markSearchTarget(title, y);
		float h = 23.0F;
		boolean hov = isVisible(y, h, visTop, visBot) && hovered(x, y, w, h, mx, my);
		IslandRender.drawRoundedRect(x, y, w, h, 5.5F, 5.5F, 5.5F, 5.5F,
			col(BG_CARD, alpha * (hov ? 0.96F : 0.86F)));
		IslandRender.drawText(Fonts.medium(5.6F), title, x + 8.0F, y + 4.4F, col(TEXT_MAIN, alpha));
		IslandRender.drawWindowedText(Fonts.regular(4.9F), description, x + 8.0F, y + 13.0F,
			col(TEXT_SUB, alpha * 0.86F), x + 7.0F, x + w - 42.0F, 6.0F, 0.0F);

		float swW = 28.0F;
		float swH = 12.0F;
		float swX = x + w - swW - 7.0F;
		float swY = y + (h - swH) * 0.5F;
		IslandRender.drawRoundedRect(swX, swY, swW, swH, 6.0F, 6.0F, 6.0F, 6.0F,
			col(value ? accent : 0xFF2A3142, alpha * (value ? 0.90F : 0.85F)));
		float knobX = value ? (swX + swW - 10.5F) : (swX + 1.5F);
		IslandRender.drawRoundedRect(knobX, swY + 1.5F, 9.0F, 9.0F, 4.5F, 4.5F, 4.5F, 4.5F, col(0xFFFFFFFF, alpha));

		if (isVisible(y, h, visTop, visBot)) {
			hitZones.add(new HitZone(x, y, w, h, description, onToggle));
		}
		return y + h + 4.0F;
	}

	private float renderLabeledSliderCard(
		String id, float x, float y, float w,
		String title, String description, String formattedValue,
		float min, float max, FloatGetter getter, FloatSetter setter,
		float alpha, int accent, float visTop, float visBot
	) {
		markSearchTarget(title, y);
		float h = 30.0F;
		IslandRender.drawRoundedRect(x, y, w, h, 5.5F, 5.5F, 5.5F, 5.5F, col(BG_CARD, alpha * 0.86F));
		Font bold = Fonts.medium(5.6F);
		Font valFont = Fonts.medium(5.5F);
		IslandRender.drawText(bold, title, x + 8.0F, y + 4.2F, col(TEXT_MAIN, alpha));
		IslandRender.drawText(valFont, formattedValue, x + w - valFont.width(formattedValue) - 8.0F, y + 4.2F, col(accent, alpha));
		IslandRender.drawWindowedText(Fonts.regular(4.8F), description, x + 8.0F, y + 12.0F,
			col(TEXT_SUB, alpha * 0.85F), x + 7.0F, x + w - 8.0F, 6.0F, 0.0F);

		float trackX = x + 8.0F;
		float trackW = w - 16.0F;
		float trackY = y + 22.0F;
		drawSliderBar(id, trackX, trackY, trackW, min, max, getter, setter, alpha, accent, visTop, visBot, description);

		return y + h + 4.0F;
	}

	private void renderInlineSlider(
		String id, float x, float y, float w,
		String label, String formattedValue,
		float min, float max, FloatGetter getter, FloatSetter setter,
		float alpha, int accent, float visTop, float visBot, String tooltip
	) {
		Font f = Fonts.medium(5.1F);
		IslandRender.drawText(f, label, x, y, col(TEXT_SUB, alpha));
		IslandRender.drawText(f, formattedValue, x + w - f.width(formattedValue), y, col(TEXT_MAIN, alpha));
		drawSliderBar(id, x, y + 9.0F, w, min, max, getter, setter, alpha, accent, visTop, visBot, tooltip);
	}

	private void drawSliderBar(
		String id, float trackX, float trackY, float trackW,
		float min, float max, FloatGetter getter, FloatSetter setter,
		float alpha, int accent, float visTop, float visBot, String tooltip
	) {
		float t = Math.clamp((getter.get() - min) / Math.max(0.0001F, max - min), 0.0F, 1.0F);
		IslandRender.drawRoundedRect(trackX, trackY, trackW, 3.6F, 1.8F, 1.8F, 1.8F, 1.8F, col(0xFF080A10, alpha * 0.90F));
		if (t > 0.005F) {
			IslandRender.drawRoundedRect(trackX, trackY, Math.max(3.6F, trackW * t), 3.6F,
				1.8F, 1.8F, 1.8F, 1.8F, col(accent, alpha * 0.92F));
		}
		float kx = trackX + trackW * t;
		IslandRender.drawRoundedRect(kx - 3.6F, trackY - 1.8F, 7.2F, 7.2F, 3.6F, 3.6F, 3.6F, 3.6F, col(0xFFFFFFFF, alpha));

		if (isVisible(trackY - 4.0F, 10.0F, visTop, visBot)) {
			sliderBindings.add(new SliderBinding(id, trackX, trackY, trackW, 4.0F, min, max, getter, setter, tooltip));
		}
	}

	private void renderEditableBox(
		float x, float y, float w, float h,
		String prefix, String currentVal, EditTarget target,
		float alpha, int accent, double mx, double my, boolean standalone,
		float visTop, float visBot, String tooltip
	) {
		boolean active = (editTarget == target);
		boolean hov = isVisible(y, h, visTop, visBot) && hovered(x, y, w, h, mx, my);
		IslandRender.drawRoundedRect(x, y, w, h, 3.6F, 3.6F, 3.6F, 3.6F,
			col(active ? accent : 0xFFFFFFFF, alpha * (active ? 0.35F : (hov ? 0.16F : 0.08F))));
		IslandRender.drawRoundedRect(x + 0.8F, y + 0.8F, w - 1.6F, h - 1.6F, 3.2F, 3.2F, 3.2F, 3.2F,
			col(0xFF090C12, alpha * 0.94F));

		Font lf = Fonts.medium(4.9F);
		if (active && editAllSelected && !editBuffer.isEmpty()) {
			float highlightX = x + 4.0F + lf.width(prefix + ": ");
			float highlightW = Math.max(0.0F, Math.min(lf.width(editBuffer), x + w - 4.0F - highlightX));
			if (highlightW > 0.0F) IslandRender.drawRoundedRect(highlightX, y + 2.0F, highlightW, h - 4.0F,
				1.0F, 1.0F, 1.0F, 1.0F, col(accent, alpha * 0.32F));
		}
		String shown = active
			? (editBuffer + ((System.currentTimeMillis() / 450L) % 2L == 0L ? "|" : ""))
			: currentVal;
		String full = prefix + ": " + shown;
		IslandRender.drawWindowedText(lf, full, x + 4.0F, y + (h - lf.getSize()) * 0.5F + 0.5F,
			col(active ? accent : TEXT_MAIN, alpha), x + 3.0F, x + w - 3.0F, 5.0F, 0.0F);

		if (isVisible(y, h, visTop, visBot)) {
			hitZones.add(new HitZone(x, y, w, h, tooltip, () -> beginEdit(target, currentVal, standalone)));
		}
	}

	private float renderApiKeyCard(
		float x, float y, float w, String label, String currentKey, EditTarget target,
		float alpha, int accent, double mx, double my, boolean standalone,
		float visTop, float visBot, String tooltip
	) {
		float h = 20.0F;
		IslandRender.drawRoundedRect(x, y, w, h, 5.0F, 5.0F, 5.0F, 5.0F, col(BG_CARD, alpha * 0.86F));
		String masked = (currentKey == null || currentKey.isBlank())
			? "(Not set)"
			: (revealApiKeys ? currentKey : ("••••" + currentKey.substring(Math.max(0, currentKey.length() - 4))));

		float pasteW = 34.0F;
		float clearW = 30.0F;
		float boxW = w - pasteW - clearW - 18.0F;
		renderEditableBox(x + 5.0F, y + 4.2F, boxW, 11.5F, label, masked, target,
			alpha, accent, mx, my, standalone, visTop, visBot, tooltip);

		renderSmallButton(x + 8.0F + boxW, y + 4.2F, pasteW, 11.5F, "Paste", false, alpha, accent, mx, my, visTop, visBot,
			"Paste API key directly from your clipboard.",
			() -> {
				String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
				if (clip != null && !clip.isBlank()) {
					String clean = clip.trim();
					if (target == EditTarget.MUSIXMATCH_KEY) {
						IslandSettings.musixmatchApiKey = clean;
						IslandSettings.enableMusixmatch = true;
					} else {
						IslandSettings.betterLyricsApiKey = clean;
					}
					IslandSettings.save();
					LyricsFetcher.clearCache();
					flashStatus("Pasted " + label);
				}
			});
		renderSmallButton(x + 11.0F + boxW + pasteW, y + 4.2F, clearW, 11.5F, "Clear", false, alpha, accent, mx, my, visTop, visBot,
			"Clear saved " + label + ".",
			() -> {
				if (target == EditTarget.MUSIXMATCH_KEY) {
					IslandSettings.musixmatchApiKey = "";
					IslandSettings.enableMusixmatch = false;
				} else {
					IslandSettings.betterLyricsApiKey = "";
				}
				IslandSettings.save();
				LyricsFetcher.clearCache();
				flashStatus("Cleared " + label);
			});

		return y + h + 4.0F;
	}

	private void renderSmallButton(
		float x, float y, float w, float h, String text, boolean highlighted,
		float alpha, int accent, double mx, double my, float visTop, float visBot,
		String tooltip, Runnable onClick
	) {
		boolean hov = isVisible(y, h, visTop, visBot) && hovered(x, y, w, h, mx, my);
		IslandRender.drawRoundedRect(x, y, w, h, 4.0F, 4.0F, 4.0F, 4.0F,
			col(highlighted ? accent : (hov ? 0xFFFFFFFF : 0xFF2C3543),
				alpha * (highlighted ? 0.85F : (hov ? 0.20F : 0.88F))));
		Font bf = Fonts.medium(5.1F);
		IslandRender.drawCenteredText(bf, text, x + w * 0.5F, y + (h - bf.getSize()) * 0.5F + 0.5F,
			col(highlighted ? 0xFF080B10 : TEXT_MAIN, alpha));
		if (isVisible(y, h, visTop, visBot)) {
			hitZones.add(new HitZone(x, y, w, h, tooltip, onClick));
		}
	}

	private void renderFooter(float x, float y, float w, float h, float alpha, int accent, double mx, double my) {
		IslandRender.drawRoundedRect(x + 4.0F, y, w - 8.0F, h - 2.0F, 4.0F, 4.0F, 4.0F, 4.0F, col(0xFF07090E, alpha * 0.82F));
		Font f = Fonts.regular(4.9F);

		String msg = null;
		int textCol = TEXT_SUB;
		if (System.currentTimeMillis() < statusUntilMs && !statusBanner.isEmpty()) {
			msg = statusBanner;
			textCol = accent;
		} else {
			for (SliderBinding sb : sliderBindings) {
				if (sb.contains(mx, my)) {
					msg = sb.tooltip();
					break;
				}
			}
			if (msg == null) {
				for (int i = hitZones.size() - 1; i >= 0; i--) {
					HitZone hz = hitZones.get(i);
					if (hz.contains(mx, my) && hz.tooltip() != null && !hz.tooltip().isEmpty()) {
						msg = hz.tooltip();
						break;
					}
				}
			}
			if (msg == null) {
				msg = IslandSettings.advancedSettingsMode
					? "Advanced Mode • Scroll for more controls • All changes apply live and save automatically."
					: "Simple Mode • Changes save automatically • Scroll for more • Advanced has every setting.";
				textCol = TEXT_DIM;
			}
		}

		IslandRender.drawWindowedText(f, msg, x + 9.0F, y + 3.6F, col(textCol, alpha * 0.95F),
			x + 8.0F, x + w - 9.0F, 8.0F, 0.0F);
	}

	// =========================================================================
	// Mouse & Scroll Interaction
	// =========================================================================

	public boolean handleMousePress(
		float x, float y, float w, float h,
		double mouseX, double mouseY, int button, boolean standalone
	) {
		if (confirmation != null) {
			if (button == org.lwjgl.sdl.SDLMouse.SDL_BUTTON_LEFT) {
				for (int i = hitZones.size() - 1; i >= 0; i--) {
					HitZone hz = hitZones.get(i);
					if (hz.contains(mouseX, mouseY)) { hz.onClick().run(); break; }
				}
			}
			return true;
		}
		if (!hovered(x, y, w, h, mouseX, mouseY)) {
			if (editTarget != EditTarget.NONE) {
				commitEditBuffer();
			}
			return false;
		}
		if (button != org.lwjgl.sdl.SDLMouse.SDL_BUTTON_LEFT) return true;

		EditTarget prevEdit = editTarget;

		// Check Color Wheel on Appearance tab
		if (searchQuery.isBlank() && activeTab == 0 && wheelVisible && mouseY >= bodyTopLast && mouseY <= bodyBottomLast && lastWheelRadius > 5.0F) {
			double dx = mouseX - lastWheelCx;
			double dy = mouseY - lastWheelCy;
			if (Math.hypot(dx, dy) <= lastWheelRadius + 2.0) {
				if (prevEdit != EditTarget.NONE) commitEditBuffer();
				draggingWheel = true;
				updateWheelFromMouse(mouseX, mouseY);
				return true;
			}
		}

		// Check Sliders
		for (SliderBinding sb : sliderBindings) {
			if (mouseY >= bodyTopLast && mouseY <= bodyBottomLast && sb.contains(mouseX, mouseY)) {
				if (prevEdit != EditTarget.NONE) commitEditBuffer();
				activeSliderId = sb.id();
				sb.applyMouse(mouseX);
				return true;
			}
		}

		// Check Buttons / Toggles / Editable Boxes
		for (int i = hitZones.size() - 1; i >= 0; i--) {
			HitZone hz = hitZones.get(i);
			if (i >= bodyHitStart && (mouseY < bodyTopLast || mouseY > bodyBottomLast)) continue;
			if (hz.contains(mouseX, mouseY)) {
				hz.onClick().run();
				if (prevEdit != EditTarget.NONE && editTarget == prevEdit) {
					commitEditBuffer();
				}
				return true;
			}
		}

		if (prevEdit != EditTarget.NONE) {
			commitEditBuffer();
		}
		return true;
	}

	public boolean handleMouseMove(double mouseX, double mouseY) {
		if (draggingWheel) {
			updateWheelFromMouse(mouseX, mouseY);
			return true;
		}
		if (activeSliderId != null) {
			for (SliderBinding sb : sliderBindings) {
				if (sb.id().equals(activeSliderId)) {
					sb.applyMouse(mouseX);
					return true;
				}
			}
		}
		return false;
	}

	public boolean handleMouseRelease(int button) {
		if (draggingWheel || activeSliderId != null) {
			draggingWheel = false;
			activeSliderId = null;
			IslandSettings.save();
			return true;
		}
		return false;
	}

	public boolean handleScroll(
		float x, float y, float w, float h,
		double mouseX, double mouseY, double scrollAmount
	) {
		if (!hovered(x, y, w, h, mouseX, mouseY)) return false;
		if (confirmation != null) return true;
		scrollBy(-(float) scrollAmount * 18.0F);
		focusIndex = -1;
		return true;
	}

	private void updateWheelFromMouse(double mouseX, double mouseY) {
		float nx = (float) ((mouseX - lastWheelCx) / Math.max(1.0F, lastWheelRadius));
		float ny = (float) ((mouseY - lastWheelCy) / Math.max(1.0F, lastWheelRadius));
		float[] hs = ColorUtil.wheelToHueSat(nx, ny);
		wheelHue = hs[0];
		wheelSat = hs[1];
		if (wheelVal < 0.08F) wheelVal = 1.0F;
		int rgb = ColorUtil.hsvToRgb(wheelHue, wheelSat, wheelVal);
		applyColorToActiveChannel(rgb);
	}

	private static boolean isVisible(float itemY, float itemH, float visTop, float visBot) {
		return itemY + itemH >= visTop && itemY <= visBot;
	}

	private static boolean hovered(float x, float y, float w, float h, double mx, double my) {
		return mx >= x && mx <= x + w && my >= y && my <= y + h;
	}

	private static int col(int argb, float alpha) {
		int a = Math.clamp(Math.round(alpha), 0, 255);
		return (a << 24) | (argb & 0xFFFFFF);
	}
}
