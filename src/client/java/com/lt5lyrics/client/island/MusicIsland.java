package com.lt5lyrics.client.island;

import com.lt5lyrics.client.island.font.Font;
import com.lt5lyrics.client.island.font.Fonts;
import com.lt5lyrics.client.island.render.IslandRender;
import com.lt5lyrics.client.music.LyricsFetcher;
import com.lt5lyrics.client.music.MusicTracker;
import com.lt5lyrics.client.music.TrackMetadata;
import dev.redstones.mediaplayerinfo.IMediaSession;
import dev.redstones.mediaplayerinfo.MediaInfo;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public class MusicIsland {
	private static final float PILL_HEIGHT = 15.0F;
	private static final float PILL_MAX_WIDTH = 114.0F;
	private static final float SHOWCASE_WIDTH = 136.0F;
	private static final float SHOWCASE_HEIGHT = 27.0F;
	private static final float EXPANDED_WIDTH = 168.0F;
	private static final float SHARE_WIDTH = 28.0F;
	private static final float SHARE_HEIGHT = 10.0F;
	private static final float PICTURE_SIZE = 16.0F; // supplied PNG is 32 x 32
	private static final Identifier ISLAND_PICTURE = IslandRender.id("textures/island/picture.png");
	private static final float LYRIC_STEP = 9.0F;
	private static final int WHITE = 0xFFFFFFFF;
	private static final DateTimeFormatter CLOCK_24 = DateTimeFormatter.ofPattern("HH:mm");
	private static final DateTimeFormatter CLOCK_12 = DateTimeFormatter.ofPattern("h:mm a", Locale.ROOT);
	private static final float TEXT_TAIL = 10.0F;
	private static final float TEXT_LEAD = 6.0F;
	private static final float SETTINGS_PANEL_WIDTH = 316.0F;
	private static final float SETTINGS_PANEL_HEIGHT = 248.0F;

	private final MusicTracker tracker;
	private final IslandSize size = new IslandSize(54.0F, PILL_HEIGHT);
	private boolean expanded;
	private final Anim extendAnim = new Anim(300L);
	private final Anim showcaseAnim = new Anim(340L);
	private final Anim widthAnim = new Anim(620L);
	private final Anim heightAnim = new Anim(620L);
	private final Anim pausingAnim = new Anim(400L);
	private final float[] waveValues = new float[6];
	private long waveFrame;
	private final Anim lyricScroll = new Anim(420L);
	private final Anim pillScroll = new Anim(520L);
	private final Anim expandedScroll = new Anim(520L);
	private final WordScales pillScales = new WordScales();
	private final WordScales expandedScales = new WordScales();
	private boolean settingsOpen;
	private final Anim settingsAnim = new Anim(280L);
	private final SettingsPanel settingsPanel;
	private final CommunityPanel communityPanel = new CommunityPanel();
	private float manualScroll;
	private long manualScrollAt;
	private String currentLine = "";
	private String previousLine = "";
	private boolean lyricLine;
	private long lineChanged = -10000L;

	// Action status toast shown briefly inside the Dynamic Island pill
	private String statusToast = "";
	private long statusToastUntil = 0L;

	public MusicIsland(MusicTracker tracker) {
		this.tracker = tracker;
		this.settingsPanel = new SettingsPanel(tracker, new SettingsPanel.HostCallbacks() {
			@Override
			public void onOpenMapper() {
				openMapper();
			}

			@Override
			public void onStartDragHud() {
				settingsOpen = false;
				expanded = false;
				Minecraft.getInstance().gui.setScreen(new DragScreen(MusicIsland.this));
			}

			@Override
			public void onOpenFullScreen() {
				openFullSettingsScreen();
			}

			@Override
			public void onClosePanel() {
				settingsOpen = false;
			}
		});
	}

	public MusicTracker tracker() {
		return tracker;
	}

	public void showToast(String message) {
		if (message == null || message.isBlank()) return;
		this.statusToast = message;
		this.statusToastUntil = System.currentTimeMillis() + 1850L;
	}

	public void openFullSettingsScreen() {
		expanded = false;
		settingsOpen = false;
		Minecraft.getInstance().gui.setScreen(new LT5SettingsScreen(this, tracker));
	}

	private int effectiveAccent() {
		if (IslandSettings.autoAccentColor && tracker.hasSession() && tracker.getMediaColor() != 0xFFFFFFFF) {
			return tracker.getMediaColor();
		}
		return IslandSettings.hudAccentColor;
	}

	private boolean isTrackShowcaseActive(MediaInfo media, boolean dragScreen, boolean fullSettings) {
		if (!IslandSettings.trackToast || expanded || dragScreen || fullSettings || media == null) return false;
		if (System.currentTimeMillis() < statusToastUntil) return false;
		long elapsed = System.currentTimeMillis() - tracker.getTrackChangedAt();
		return tracker.getTrackChangedAt() > 0L && elapsed >= 0L && elapsed < 3600L;
	}

	public void render() {
		IMediaSession session = tracker.getSession();
		MediaInfo media = session != null ? session.getMedia() : null;
		tracker.beatReactor().update(tracker.getPeak(), media != null && media.isPlaying());

		Minecraft mc = Minecraft.getInstance();
		if (!isChat(mc) || mc.player == null || mc.gui.hud.isHidden() || !IslandSettings.showHud
				|| ScreenHiding.shouldHide(mc.gui.screen())) communityPanel.hide();
		if (mc.player == null || mc.gui.hud.isHidden() || ScreenHiding.shouldHide(mc.gui.screen())) return;
		boolean dragScreen = mc.gui.screen() instanceof DragScreen;
		boolean fullSettings = mc.gui.screen() instanceof LT5SettingsScreen;
		if (mc.gui.screen() != null && !isChat(mc) && !dragScreen && !fullSettings) return;
		if (!IslandSettings.showHud && !dragScreen && !fullSettings && System.currentTimeMillis() >= statusToastUntil) {
			expanded = false;
			settingsOpen = false;
			return;
		}
		if (mc.gui.screen() == null || fullSettings) {
			expanded = false;
			settingsOpen = false;
		}

		boolean showcase = isTrackShowcaseActive(media, dragScreen, fullSettings);
		showcaseAnim.update(showcase ? 1.0F : 0.0F);
		float showAmt = showcaseAnim.get();

		float x = islandX(mc);
		float y = islandY(mc);
		float ext = extendAnim.get();
		float radius = 7.0F + 2.5F * showAmt + 11.0F * ext;
		int accent = effectiveAccent();

		float pulse = IslandSettings.hudBeatPulse ? tracker.beatReactor().pulse() : 0.0F;
		float auraPad = 1.0F + pulse * 1.4F;
		float auraAlpha = 36.0F + pulse * 58.0F;
        long changeAge = System.currentTimeMillis() - tracker.getTrackChangedAt();
        if (PlusSettings.songAnimation == PlusSettings.SongAnimation.GLOW && media != null
            && tracker.getTrackChangedAt() > 0L && changeAge >= 0 && changeAge < 900L) {
            float flash = 1.0F - changeAge / 900.0F;
            auraPad += 2.0F * flash; auraAlpha += 85.0F * flash;
        }

		IslandRender.drawSquircle(x - auraPad, y - auraPad, size.width + auraPad * 2.0F, size.height + auraPad * 2.0F,
				2.0F + 5.0F * ext, radius + auraPad, radius + auraPad, radius + auraPad, radius + auraPad,
				ColorUtil.withAlpha(accent, auraAlpha));
		IslandRender.drawBlur(x, y, size.width, size.height, 45.0F, 2.0F + 2.0F * ext,
				radius, radius, radius, radius, WHITE);
		IslandRender.drawSquircle(x, y, size.width, size.height, 2.0F + 5.0F * ext,
				radius, radius, radius, radius,
				ColorUtil.withAlpha(IslandSettings.hudBgColor, 255.0F * IslandSettings.hudOpacity));

		drawClock(x, y, ext);
		if (media != null) drawVisualizer(x, y, media, ext, accent, pulse);

		boolean toastActive = !expanded && System.currentTimeMillis() < statusToastUntil && !statusToast.isEmpty();

		IslandRender.pushScissor(x, y, size.width, size.height);
		if (toastActive) {
			Font toastFont = Fonts.medium(6.8F);
			IslandRender.drawCenteredText(toastFont, statusToast, x + size.width / 2.0F, y + (size.height - 6.0F) / 2.0F,
					color(accent, 245.0F));
		} else if (media == null) {
			if (ext < 0.995F) {
				Font font = Fonts.medium(7.0F);
				IslandRender.drawCenteredText(font, "LT5Lyrics", x + size.width / 2.0F, y + 5.0F,
						color(IslandSettings.hudActiveColor, 210.0F * (1.0F - ext)));
			}
			if (ext > 0.005F) {
				drawExpandedIdle(x, y, ext, accent);
			}
		} else {
			drawCover(x, y, ext, showAmt, pulse);
			if (ext < 0.995F) {
				if (showAmt > 0.35F) {
					drawTrackShowcase(x, y, media, showAmt * (1.0F - ext), accent);
				} else {
					drawPill(x, y, media, ext);
				}
			}
			if (ext > 0.005F) drawExpanded(x, y, session, media, ext, accent);
		}
		// This is a passive resource texture, drawn only in the open Music Island,
		// above its background and inside the same animated clipping rectangle.
		if (pictureVisible(expanded, ext, dragScreen, fullSettings)) drawIslandPicture(x, y, ext);
		IslandRender.popScissor();

		settingsAnim.update(settingsOpen && ext > 0.5F ? 1.0F : 0.0F);
		if (settingsAnim.get() > 0.01F) {
			float[] bounds = settingsBounds(mc, x, y);
			double mx = mc.mouseHandler.getScaledXPos(mc.getWindow());
			double my = mc.mouseHandler.getScaledYPos(mc.getWindow());
			float scale = embeddedSettingsScale(mc);
			float slide = (1.0F - settingsAnim.get()) * 5.0F * scale;
			IslandRender.setUiScale(scale);
			try {
				settingsPanel.render(bounds[0] / scale, (bounds[1] - slide) / scale, bounds[2] / scale, bounds[3] / scale,
						255.0F * ext, settingsAnim.get(), mx / scale, my / scale, false);
			} finally {
				IslandRender.setUiScale(1.0F);
			}
		}

		if (isChat(mc) && IslandSettings.showHud) {
			communityPanel.render(mc, x, y, size.width, size.height, communityObstacle(mc, x, y), accent);
		}

		extendAnim.update(expanded ? 1.0F : 0.0F);
		widthAnim.update(targetWidth(session, showcase, toastActive));
		heightAnim.update(targetHeight(session, showcase));
		size.width = widthAnim.get();
		size.height = heightAnim.get();
	}

	private void drawTrackShowcase(float x, float y, MediaInfo media, float vis, int accent) {
		Font badge = Fonts.medium(4.8F);
		Font title = Fonts.lyric(6.6F);
		Font artist = Fonts.lyricSubtle(5.6F);
		float tx = x + 28.0F;
		float tw = Math.max(30.0F, size.width - 34.0F);
		float a = 255.0F * vis;
        if (PlusSettings.songAnimation == PlusSettings.SongAnimation.SLIDE) {
            long age = Math.max(0L, System.currentTimeMillis() - tracker.getTrackChangedAt());
            y += 5.0F * (1.0F - Math.clamp(age / 500.0F, 0.0F, 1.0F));
        }

		IslandRender.drawText(badge, "NOW PLAYING", tx, y + 3.5F, color(accent, a * 0.95F));
		IslandRender.drawWindowedText(title, displayTitle(media), tx, y + 10.0F,
				color(IslandSettings.hudActiveColor, a), tx - 1.0F, tx + tw, 6.0F, 0.0F);
		IslandRender.drawWindowedText(artist, artistName(media), tx, y + 18.2F,
				color(IslandSettings.hudUpcomingColor, a * 0.85F), tx - 1.0F, tx + tw, 6.0F, 0.0F);
	}

	/** Enlarge the embedded panel only when Minecraft's own GUI scale is 1. */
	static float embeddedScaleFor(int width, int height, int guiScale) {
		if (guiScale > 1) return 1.0F;
		return Math.clamp(Math.min(width / 332.0F, height / 264.0F), 1.0F, 1.5F);
	}

	private static float embeddedSettingsScale(Minecraft mc) {
		return embeddedScaleFor(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(),
			mc.getWindow().getGuiScale());
	}

	private float[] settingsBounds(Minecraft mc, float islandX, float islandY) {
		float sw = mc.getWindow().getGuiScaledWidth();
		float sh = mc.getWindow().getGuiScaledHeight();
		float scale = embeddedSettingsScale(mc);
		float pw = Math.min(SETTINGS_PANEL_WIDTH * scale, sw - 8.0F);
		float ph = Math.min(SETTINGS_PANEL_HEIGHT * scale, sh - 8.0F);

		float belowY = islandY + size.height + 4.0F;
		float aboveY = islandY - ph - 4.0F;

		float px;
		float py;
		if (belowY + ph <= sh - 2.0F) {
			px = islandX + (size.width - pw) / 2.0F;
			py = belowY;
		} else if (aboveY >= 2.0F) {
			px = islandX + (size.width - pw) / 2.0F;
			py = aboveY;
		} else if (islandX + size.width + 6.0F + pw <= sw - 2.0F) {
			px = islandX + size.width + 6.0F;
			py = Math.max(4.0F, Math.min(islandY, sh - ph - 4.0F));
		} else if (islandX - 6.0F - pw >= 2.0F) {
			px = islandX - 6.0F - pw;
			py = Math.max(4.0F, Math.min(islandY, sh - ph - 4.0F));
		} else {
			px = (sw - pw) / 2.0F;
			py = Math.max(4.0F, sh - ph - 4.0F);
		}

		px = Math.max(2.0F, Math.min(px, sw - pw - 2.0F));
		py = Math.max(2.0F, Math.min(py, sh - ph - 2.0F));
		return new float[] { px, py, pw, ph };
	}

	// Reserve the embedded settings card's whole slide path and its outer stroke.
	private float[] communityObstacle(Minecraft mc, float x, float y) {
		if (!settingsOpen && settingsAnim.get() <= 0.01F) return null;
		float[] bounds = settingsBounds(mc, x, y);
		float scale = embeddedSettingsScale(mc);
		float pad = 1.0F * scale;
		float slide = 5.0F * scale;
		return new float[] {bounds[0] - pad, bounds[1] - slide - pad,
				bounds[2] + 2.0F * pad, bounds[3] + slide + 2.0F * pad};
	}

	private float islandX(Minecraft mc) {
		float sw = mc.getWindow().getGuiScaledWidth();
		float center = IslandSettings.isMoved() ? IslandSettings.posX : sw / 2.0F;
		return center - size.width / 2.0F;
	}

	private float islandY(Minecraft mc) {
		float base = IslandSettings.isMoved() ? IslandSettings.posY : 7.0F;
		float maxY = mc.getWindow().getGuiScaledHeight() - size.height - 2.0F;
		return Math.max(2.0F, Math.min(base, maxY));
	}

	public float centerX() {
		Minecraft mc = Minecraft.getInstance();
		return IslandSettings.isMoved()
				? IslandSettings.posX : mc.getWindow().getGuiScaledWidth() / 2.0F;
	}

	public float topY() {
		return islandY(Minecraft.getInstance());
	}

	public float width() {
		return size.width;
	}

	public float height() {
		return size.height;
	}

	public boolean containsPoint(double mouseX, double mouseY) {
		float x = centerX() - size.width / 2.0F;
		return hovered(x, topY(), size.width, size.height, mouseX, mouseY);
	}

	private void drawClock(float x, float y, float ext) {
		if (IslandSettings.clockMode == IslandSettings.ClockMode.OFF) return;
		Font font = Fonts.medium(7.0F);
		DateTimeFormatter fmt = IslandSettings.clockMode == IslandSettings.ClockMode.CLOCK_12H ? CLOCK_12 : CLOCK_24;
		String time = LocalTime.now().format(fmt);
		int alpha = (int) (255.0F * (1.0F - ext));
		if (alpha <= 2) return;
		IslandRender.drawText(font, time, x - font.width(time) - 6.0F, y + 5.0F, color(WHITE, alpha));
	}

	private void drawCover(float x, float y, float ext, float showAmt, float pulse) {
		float margin = 4.0F + 0.5F * showAmt + 8.0F * ext;
		float baseSize = 7.0F + 12.0F * showAmt * (1.0F - ext) + 19.0F * ext;
		float bounce = baseSize * pulse * 0.055F;
		float imageSize = baseSize + bounce;
		float imageY = y + (ext > 0.01F || showAmt > 0.1F ? margin : (PILL_HEIGHT - baseSize) / 2.0F) - bounce * 0.5F;
		Identifier cover = tracker.getImage();
		Identifier image = cover != null ? cover : IslandRender.id("icons/music/no_image.png");
		float r = 1.5F + 2.0F * showAmt + 4.5F * ext;
		IslandRender.drawRoundedTexture(image, x + margin - bounce * 0.5F, imageY, imageSize, imageSize, r, r, r, r, WHITE);
	}

	private void drawPill(float x, float y, MediaInfo media, float ext) {
		Font font = Fonts.lyric(7.0F);
		Font bigFont = Fonts.lyric(7.8F);
		String line = pillLine(media);
		boolean lyric = lyricLine;
		float x1 = x + 15.0F;
		float x2 = x + size.width - 4.0F;
		float textY = y + 5.0F;
		int alpha = (int) (255.0F * (1.0F - ext));
		if (alpha <= 2) return;

		long elapsedMs = Math.max(0L, System.currentTimeMillis() - lineChanged);
		IslandSettings.LyricAnimation anim = IslandSettings.lyricAnimation;
		float speed = IslandSettings.animationSpeed;
		LyricAnimator.MotionState inState = LyricAnimator.computeActive(anim, elapsedMs, speed);
		LyricAnimator.MotionState outState = LyricAnimator.computeExiting(anim, elapsedMs, speed, 0.0F, 1.0F);

		// Convert world lift (+up) into screen Y (-up)
		float inYOffset = -inState.liftUnits() * 11.0F;
		float outYOffset = -outState.liftUnits() * 11.0F;

		if (IslandSettings.lyricsEnabled && outState.alpha() > 0.02F && !previousLine.isEmpty()) {
			drawGhostedLine(font, previousLine, x1, textY + outYOffset,
					x1, x2, alpha * outState.alpha(), 1.0F - outState.alpha());
		}
		drawKaraokeLine(font, bigFont, line, x1, textY + inYOffset,
				x1, x2, alpha * inState.alpha(), media, pillScroll, pillScales, lyric);
	}

	private int[] scratchWordCols = new int[64];
	private int[] scratchGhostCols = new int[64];

	private int[] ensureWordBuf(int minLen, boolean ghost) {
		int[] cur = ghost ? scratchGhostCols : scratchWordCols;
		if (cur.length < minLen) {
			cur = new int[Math.max(minLen, cur.length * 2)];
			if (ghost) scratchGhostCols = cur;
			else scratchWordCols = cur;
		}
		return cur;
	}

	private void drawKaraokeLine(Font font, Font bigFont, String line, float x, float y,
								 float x1, float x2, float alpha, MediaInfo media, Anim scrollAnim,
								 WordScales scales, boolean lyric) {
		if (line.isEmpty()) return;
		List<LyricsFetcher.LyricLine> lines = tracker.getLyrics();
		boolean synced = !lines.isEmpty() && lines.get(0).time() >= 0.0F;
		float position = tracker.smoothPosition();
		int index = synced ? LyricsFetcher.currentIndex(lines, position) : -1;

		int primaryBase = IslandSettings.resolveHudPrimaryColor();
		int highlightBase = IslandSettings.resolveHighlightColor();
		int upcomingBase = ColorUtil.upcomingForPrimary(primaryBase, IslandSettings.hudUpcomingColor);
		float glowAmt = IslandSettings.effectiveWorldGlow() * 0.65F;
		int glowBase = color(IslandSettings.resolveGlowColor(), alpha * 0.55F * glowAmt);
		float outlineAmt = Math.clamp(IslandSettings.lyricOutlineThickness, 0.0F, 1.0F) * 0.75F;
		int outlineCol = color(IslandSettings.resolveOutlineColor(), alpha * 0.90F);

		if (!synced || index < 0 || !lyric || !line.equals(lines.get(index).text())) {
			float window = x2 - x1;
			float lineWidth = font.width(line);
			float offset = marquee(lineWidth, window, System.currentTimeMillis() - lineChanged);
			boolean overflow = lineWidth > window;
			float left = overflow ? Math.max(0.0F, Math.min(7.0F, offset * 1.6F)) : 0.0F;
			float right = overflow
					? Math.max(0.0F, Math.min(7.0F, (lineWidth - window + 2.0F - offset) * 1.6F))
					: 0.0F;
			IslandRender.drawStyledLyric2D(font, line, x - offset, y,
					color(lyric ? primaryBase : IslandSettings.hudActiveColor, alpha),
					null, -1,
					lyric ? glowBase : 0, lyric ? glowAmt : 0.0F,
					lyric ? outlineCol : 0, lyric ? outlineAmt : 0.0F,
					false, true, x1 - 1.0F, x2, right, left);
			return;
		}

		LyricsFetcher.LyricLine activeLine = lines.get(index);
		float progress = Karaoke.lineProgress(lines, index, position);
		float sung = Karaoke.sungChars(activeLine, progress, position);
		List<Karaoke.Word> words = scales.words(line);
		if (words.isEmpty()) return;
		int curWord = Karaoke.activeTimedWord(words, activeLine, sung, position);
		scales.update(curWord);
		int scrollWord = Math.max(0, curWord);

		float baseSize = font.getSize();
		float bigSize = bigFont.getSize();
		float window = x2 - x1;
		float lineWidth = font.width(line);
		float target = 0.0F;
		if (lineWidth > window - TEXT_TAIL) {
			float wordStartX = font.width(line.substring(0, words.get(scrollWord).start()));
			float wordWidth = font.width(line.substring(words.get(scrollWord).start(), words.get(scrollWord).end()));
			target = wordStartX + wordWidth / 2.0F - window / 2.0F + TEXT_LEAD;
			target = Math.max(0.0F, Math.min(lineWidth - window + TEXT_TAIL, target));
		}
		float entry = scales.entry();
		if (entry < 1.0F) scrollAnim.snap(target);
		scrollAnim.update(target);
		float scroll = scrollAnim.get();
		boolean scrolling = lineWidth > window - TEXT_TAIL;
		float fadeLeft = scrolling ? Math.max(0.0F, Math.min(7.0F, scroll * 1.6F)) : 0.0F;
		float fadeRight = scrolling
				? Math.max(0.0F, Math.min(7.0F, (lineWidth - window + TEXT_TAIL - scroll) * 1.6F))
				: 0.0F;
		float blur = Math.min(3.5F, Math.abs(scrollAnim.delta()) * 1.4F);
		float drawX = x - scroll;

		alpha *= entry;
		y += (1.0F - entry) * 5.0F;
		int litColor = color(primaryBase, alpha);
		int hiColor = color(highlightBase, alpha);
		int grayColor = color(upcomingBase, alpha * 0.65F);
		int wordGlowCol = color(IslandSettings.resolveGlowColor(), alpha * 0.55F * glowAmt);
		int wordOutlineCol = color(IslandSettings.resolveOutlineColor(), alpha * 0.90F);

		float shift = 0.0F;
		for (int i = 0; i < words.size(); i++) {
			Karaoke.Word word = words.get(i);
			String text = line.substring(word.start(), word.end());
			int wLen = text.length();
			float wordX = drawX + font.width(line.substring(0, word.start()));
			float scale = scales.scale(i);
			float wordSize = baseSize + (bigSize - baseSize) * scale;
			Font wordFont = Fonts.lyric(wordSize);
			float baseWidth = font.width(text);
			float bigWidth = wordFont.width(text);
			float renderX = wordX + shift;
			shift += bigWidth - baseWidth;
			float renderY = y - scale * 0.5F;

			float ghostPower = Math.min(1.0F, blur / 2.5F);
			if (ghostPower > 0.02F) {
				int[] ghost = ensureWordBuf(wLen, true);
				Karaoke.fillColors(ghost, wLen, sung - word.start(),
						Karaoke.withAlpha(litColor, 0.22F * ghostPower),
						Karaoke.withAlpha(hiColor, 0.22F * ghostPower),
						Karaoke.withAlpha(grayColor, 0.22F * ghostPower),
						IslandSettings.lyricSmoothness);
				Karaoke.maskFutureTimedWords(ghost, wLen, word.start(), activeLine, position,
						Karaoke.withAlpha(grayColor, 0.22F * ghostPower));
				IslandRender.drawStyledLyric2D(wordFont, text, renderX + blur, renderY,
						0xFFFFFFFF, ghost, wLen, 0, 0.0F, 0, 0.0F, false,
						true, x1 - 1.0F, x2, fadeRight, fadeLeft);
				Karaoke.fillColors(ghost, wLen, sung - word.start(),
						Karaoke.withAlpha(litColor, 0.35F * ghostPower),
						Karaoke.withAlpha(hiColor, 0.35F * ghostPower),
						Karaoke.withAlpha(grayColor, 0.35F * ghostPower),
						IslandSettings.lyricSmoothness);
				Karaoke.maskFutureTimedWords(ghost, wLen, word.start(), activeLine, position,
						Karaoke.withAlpha(grayColor, 0.35F * ghostPower));
				IslandRender.drawStyledLyric2D(wordFont, text, renderX + blur * 0.5F, renderY,
						0xFFFFFFFF, ghost, wLen, 0, 0.0F, 0, 0.0F, false,
						true, x1 - 1.0F, x2, fadeRight, fadeLeft);
			}
			int[] colors = ensureWordBuf(wLen, false);
			Karaoke.fillColors(colors, wLen, sung - word.start(),
					litColor, hiColor, grayColor, IslandSettings.lyricSmoothness);
			Karaoke.maskFutureTimedWords(colors, wLen, word.start(), activeLine, position, grayColor);
			IslandRender.drawStyledLyric2D(wordFont, text, renderX, renderY,
					0xFFFFFFFF, colors, wLen,
					wordGlowCol, glowAmt,
					wordOutlineCol, outlineAmt,
					false, true, x1 - 1.0F, x2, fadeRight, fadeLeft);
		}
	}

	private static float marquee(float contentWidth, float window, long elapsed) {
		float overflow = contentWidth - window + 2.0F;
		if (overflow <= 0.0F) return 0.0F;
		float travel = 2400.0F + overflow * 90.0F;
		float hold = 1600.0F;
		float cycle = (travel + hold) * 2.0F;
		float t = Math.max(0L, elapsed) % (long) cycle;
		float phase;
		if (t < hold) phase = 0.0F;
		else if (t < hold + travel) phase = (t - hold) / travel;
		else if (t < hold * 2.0F + travel) phase = 1.0F;
		else phase = 1.0F - (t - hold * 2.0F - travel) / travel;
		phase = phase * phase * (3.0F - 2.0F * phase);
		return overflow * phase;
	}

	private void drawGhostedLine(Font font, String line, float x, float y,
								 float x1, float x2, float alpha, float slide) {
		if (line.isEmpty() || alpha <= 2.0F) return;
		float travel = (1.0F - slide) * 6.0F;
		if (travel > 0.4F) {
			IslandRender.drawWindowedText(font, line, x, y - travel,
					color(IslandSettings.hudActiveColor, alpha * 0.25F),
					x1 - 1.0F, x2, 7.0F, 0.0F);
		}
		IslandRender.drawWindowedText(font, line, x, y,
				color(IslandSettings.hudActiveColor, alpha),
				x1 - 1.0F, x2, 7.0F, 0.0F);
	}

	static float[] shareBounds(float x, float y) {
		return new float[] { x + EXPANDED_WIDTH - SHARE_WIDTH - 10.0F, y + 11.0F, SHARE_WIDTH, SHARE_HEIGHT };
	}

	static float[] pictureBounds(float x, float y) {
		float[] share = shareBounds(x, y);
		return new float[] { share[0] + (share[2] - PICTURE_SIZE) / 2.0F,
				share[1] + share[3] + 3.0F, PICTURE_SIZE, PICTURE_SIZE };
	}

	static boolean pictureVisible(boolean expanded, float extension, boolean dragScreen, boolean fullSettings) {
		return expanded && extension > 0.005F && !dragScreen && !fullSettings;
	}

	static int pictureColor(float extension) {
		return color(WHITE, 128.0F * Math.clamp(extension, 0.0F, 1.0F));
	}

	private static void drawIslandPicture(float x, float y, float ext) {
		float[] picture = pictureBounds(x, y);
		IslandRender.drawTexture(ISLAND_PICTURE, picture[0], picture[1], picture[2], picture[3], pictureColor(ext));
	}

	private static void drawShareButton(float x, float y, int alpha, int accent, boolean enabled) {
		float[] share = shareBounds(x, y);
		Font micro = Fonts.medium(4.8F);
		float strength = enabled ? 1.0F : 0.42F;
		IslandRender.drawRoundedRect(share[0], share[1], share[2], share[3], 3.5F, 3.5F, 3.5F, 3.5F,
				color(accent, alpha * 0.22F * strength));
		IslandRender.drawCenteredText(micro, "SHARE", share[0] + share[2] / 2.0F, share[1] + 2.8F,
				color(accent, alpha * 0.95F * strength));
	}

	private void drawExpandedIdle(float x, float y, float ext, int accent) {
		Font title = Fonts.medium(7.2F);
		Font hint = Fonts.regular(6.0F);
		Font btnFont = Fonts.medium(6.0F);
		int alpha = (int) (255.0F * ext);
		float height = targetHeight(null, false);

		// The disabled Share label makes the picture's placement consistent even
		// without a session. The idle text is clipped before the reserved column.
		drawShareButton(x, y, alpha, accent, false);
		float idleLeft = x + 9.0F;
		float idleRight = shareBounds(x, y)[0] - 4.0F;
		String idleTitle = "LT5Lyrics - No Active Song";
		String idleHint = "Play music in Spotify or your browser";
		IslandRender.drawWindowedText(title, idleTitle, idleLeft, y + 14.0F,
				color(IslandSettings.hudActiveColor, alpha * 0.92F), idleLeft, idleRight,
				title.width(idleTitle) > idleRight - idleLeft ? 6.0F : 0.0F, 0.0F);
		IslandRender.drawWindowedText(hint, idleHint, idleLeft, y + 27.0F,
				color(WHITE, alpha * 0.55F), idleLeft, idleRight,
				hint.width(idleHint) > idleRight - idleLeft ? 6.0F : 0.0F, 0.0F);

		float btnW = 78.0F;
		float btnX = x + (EXPANDED_WIDTH - btnW) / 2.0F;
		float btnY = y + height - 24.0F;
		IslandRender.drawRoundedRect(btnX, btnY, btnW, 14.0F, 5.0F, 5.0F, 5.0F, 5.0F,
				color(accent, alpha * (settingsOpen ? 0.42F : 0.24F)));
		IslandRender.drawCenteredText(btnFont, settingsOpen ? "CLOSE SETTINGS" : "OPEN SETTINGS",
				btnX + btnW / 2.0F, btnY + 4.2F, color(WHITE, alpha * 0.95F));

		float dotsX = x + EXPANDED_WIDTH - 20.0F;
		float dotsY = y + height - 15.0F;
		int dotsColor = color(accent, alpha * (settingsOpen ? 0.95F : 0.55F));
		for (int i = 0; i < 3; i++) {
			IslandRender.drawRoundedRect(dotsX + i * 4.0F, dotsY, 2.4F, 2.4F,
					1.2F, 1.2F, 1.2F, 1.2F, dotsColor);
		}
	}

	private void drawExpanded(float x, float y, IMediaSession session, MediaInfo media, float ext, int accent) {
		Font title = Fonts.lyric(7.0F);
		Font regular = Fonts.lyricSubtle(6.5F);
		Font small = Fonts.regular(5.0F);
		Font micro = Fonts.medium(4.8F);
		int alpha = (int) (255.0F * ext);
		float textX = x + 46.0F;
		float textWidth = EXPANDED_WIDTH - 58.0F;
		float height = targetHeight(session, false);

		drawShareButton(x, y, alpha, accent, true);

		float headerAvail = Math.max(40.0F, textWidth - SHARE_WIDTH - 4.0F);
		float artistAvail = Math.max(40.0F, pictureBounds(x, y)[0] - textX - 4.0F);
		String shownTitle = displayTitle(media);
		String shownArtist = artistName(media);
		boolean titleOverflow = title.width(shownTitle) > headerAvail;
		boolean artistOverflow = regular.width(shownArtist) > artistAvail;
		IslandRender.drawWindowedText(title, shownTitle, textX, y + 13.0F,
				color(IslandSettings.hudActiveColor, alpha), textX - 1.0F, textX + headerAvail,
				titleOverflow ? 8.0F : 0.0F, 0.0F);

		IslandRender.drawWindowedText(regular, shownArtist, textX, y + 23.0F,
				color(IslandSettings.hudUpcomingColor, alpha * 0.85F), textX - 1.0F, textX + artistAvail,
				artistOverflow ? 8.0F : 0.0F, 0.0F);

		float barWidth = 120.0F;
		float barX = x + (EXPANDED_WIDTH - barWidth) / 2.0F;
		float barY = y + 42.0F;
		if (QuickTimingLayout.showProgress(height)) {
			// Timing changes affect lyrics only, never the song's displayed playback clock.
			IslandRender.drawText(small, MusicTracker.formatTime((long) tracker.rawPosition()),
					barX, barY + 5.0F, color(WHITE, alpha * 0.7F));
			String duration = MusicTracker.formatTime(media.getDuration());
			IslandRender.drawText(small, duration,
					barX + barWidth - small.width(duration), barY + 5.0F, color(WHITE, alpha * 0.7F));
		}
        if (PlusSettings.playerBadge) {
            String player = PlayerBadge.detect(session.getOwner());
            if (player != null) {
                float bw = Math.min(artistAvail, micro.width(player) + 8.0F);
                IslandRender.drawRoundedRect(textX, y + 33.0F, bw, 7.0F, 2,2,2,2, color(accent, alpha*.22F));
                IslandRender.drawWindowedText(micro, player, textX+4, y+34.0F, color(accent, alpha*.95F), textX, textX+bw, 0,0);
            }
        }
		if (QuickTimingLayout.showProgress(height)) {
			float progress = media.getDuration() > 0
					? Math.clamp(tracker.rawPosition() / (float) media.getDuration(), 0.0F, 1.0F) : 0.0F;
			drawProgressStyle(barX, barY, barWidth, progress, alpha, accent);
		}

		drawLyrics(x, y, media, ext, height);
		drawQuickTiming(x, y, height, ext, accent);

		float controlY = y + height - 24.0F;
		float mid = x + EXPANDED_WIDTH / 2.0F;
		pausingAnim.update(media.isPlaying() ? 1.0F : 0.0F);
		float pause = pausingAnim.get();

		// Previous / Play-Pause / Next / Repeat
		IslandRender.drawTexture(IslandRender.id("icons/music/previous.png"), mid - 44.0F, controlY, 15.0F, 15.0F, color(WHITE, alpha));
		IslandRender.drawTexture(IslandRender.id("icons/music/play.png"), mid - 15.0F, controlY - 0.5F, 16.0F, 16.0F,
				color(WHITE, alpha * (1.0F - pause)));
		IslandRender.drawTexture(IslandRender.id("icons/music/pause.png"), mid - 15.0F, controlY - 0.5F, 16.0F, 16.0F,
				color(WHITE, alpha * pause));
		IslandRender.drawTexture(IslandRender.id("icons/music/next.png"), mid + 14.0F, controlY, 15.0F, 15.0F, color(WHITE, alpha));

		int cycleType = safeCycleType(session);
		Identifier repeatIcon = cycleType == 2
				? IslandRender.id("icons/music/repeat1.png")
				: IslandRender.id("icons/music/repeat.png");
		int repeatCol = cycleType > 0 ? color(accent, alpha * 0.96F) : color(WHITE, alpha * 0.45F);
		IslandRender.drawTexture(repeatIcon, mid + 38.0F, controlY + 2.0F, 11.0F, 11.0F, repeatCol);

		// Bottom-left Player Source Icon (clickable if 2+ players are open)
		String owner = ownerIcon(session);
		if (owner != null) {
			IslandRender.drawTexture(IslandRender.id("icons/media/" + owner + ".png"),
					x + 12.0F, y + height - 20.0F, 8.5F, 8.5F, color(WHITE, alpha * 0.86F));
		}
		if (tracker.getSessionCount() > 1) {
			IslandRender.drawText(micro, "+" + (tracker.getSessionCount() - 1),
					x + 22.0F, y + height - 18.0F, color(accent, alpha * 0.85F));
		}

		float dotsX = x + EXPANDED_WIDTH - 20.0F;
		float dotsY = y + height - 15.0F;
		int dotsColor = color(settingsOpen ? accent : WHITE, alpha * (settingsOpen ? 0.95F : 0.48F));
		for (int i = 0; i < 3; i++) {
			IslandRender.drawRoundedRect(dotsX + i * 4.0F, dotsY, 2.4F, 2.4F,
					1.2F, 1.2F, 1.2F, 1.2F, dotsColor);
		}
	}

	private static int safeCycleType(IMediaSession session) {
		try {
			return session.getCycleType();
		} catch (Throwable ignored) {
			return 0;
		}
	}

	private void drawQuickTiming(float x, float y, float height, float ext, int accent) {
		float top = y + QuickTimingLayout.top(height);
		Font label = Fonts.medium(5.2F);
		Font buttonFont = Fonts.medium(6.0F);
		float a = 255.0F * ext;
		String total = String.format(Locale.ROOT, "LYRIC %+.1fs", tracker.getLyricsOffset());
		String temporary = String.format(Locale.ROOT, "TEMP %+.1f", tracker.getQuickLyricsOffset());
		String direction = "- LATER  + EARLIER";
		IslandRender.drawText(label, total, x + 10.0F, top + 0.5F, color(WHITE, a * 0.88F));
		IslandRender.drawText(label, temporary, x + 60.0F, top + 0.5F, color(accent, a * 0.88F));
		IslandRender.drawText(label, direction, x + 158.0F - label.width(direction), top + 0.5F,
				color(WHITE, a * 0.65F));

		Minecraft mc = Minecraft.getInstance();
		double mx = mc.mouseHandler.getScaledXPos(mc.getWindow());
		double my = mc.mouseHandler.getScaledYPos(mc.getWindow());
		int hoveredButton = QuickTimingLayout.hitButton(x, y, height, mx, my);
		String[] labels = {"-1.0", "-0.1", "RESET", "+0.1", "+1.0"};
		for (int i = 0; i < labels.length; i++) {
			float[] b = QuickTimingLayout.button(x, y, height, i);
			if (b[3] <= 0.0F) continue;
			boolean resetActive = i == 2 && Math.abs(tracker.getQuickLyricsOffset()) >= 0.05F;
			float fill = hoveredButton == i ? 0.30F : resetActive ? 0.20F : 0.11F;
			IslandRender.drawRoundedRect(b[0], b[1], b[2], b[3], 3.5F, 3.5F, 3.5F, 3.5F,
					color(hoveredButton == i || resetActive ? accent : WHITE, a * fill));
			IslandRender.drawCenteredText(buttonFont, labels[i], b[0] + b[2] / 2.0F,
					b[1] + Math.max(0.5F, (b[3] - buttonFont.height()) / 2.0F), color(WHITE, a * 0.96F));
		}
	}

	private void drawLyrics(float x, float y, MediaInfo media, float ext, float height) {
		if (!IslandSettings.lyricsEnabled) return;
		List<LyricsFetcher.LyricLine> lines = tracker.getLyrics();
		if (lines.isEmpty()) return;
		Font active = Fonts.lyric(6.5F);
		Font activeBig = Fonts.lyric(7.2F);
		Font idle = Fonts.lyricSubtle(6.5F);
		Font subTransFont = Fonts.lyricSubtle(5.2F);
		float position = tracker.smoothPosition();
		int index = LyricsFetcher.currentIndex(lines, position);
		boolean synced = lines.get(0).time() >= 0.0F;

		float ax = x + 12.0F;
		float aw = EXPANDED_WIDTH - 24.0F;
		float ay = y + 56.0F;
		float ah = QuickTimingLayout.lyricHeight(height);
		if (ah <= LYRIC_STEP) return;

		boolean showSubTrans = IslandSettings.showTranslations
				&& index >= 0
				&& lines.get(index).hasTranslation();
		float listHeight = showSubTrans ? (ah - 8.5F) : ah;

		float center = listHeight / 2.0F - LYRIC_STEP / 2.0F;
		float autoScroll = synced ? Math.max(0, index) * LYRIC_STEP - center : -center + LYRIC_STEP;
		float maxScroll = Math.max(0.0F, lines.size() * LYRIC_STEP - listHeight);
		if (System.currentTimeMillis() - manualScrollAt < 4000L) {
			autoScroll = Math.max(-center, Math.min(maxScroll, manualScroll));
		} else {
			manualScroll = autoScroll;
		}
		lyricScroll.update(autoScroll);
		float scroll = lyricScroll.get();

		IslandRender.pushScissor(ax - 2.0F, ay, aw + 4.0F, listHeight);
		for (int i = 0; i < lines.size(); i++) {
			float lineY = ay + i * LYRIC_STEP - scroll;
			if (lineY < ay - LYRIC_STEP || lineY > ay + listHeight) continue;
			float fromTop = lineY - ay;
			float edge = 1.0F;
			if (fromTop < LYRIC_STEP) edge = Math.max(0.0F, (fromTop + LYRIC_STEP) / (LYRIC_STEP * 2.0F));
			float fromBottom = ay + listHeight - lineY;
			if (fromBottom < LYRIC_STEP * 2.0F) edge = Math.min(edge, Math.max(0.0F, fromBottom / (LYRIC_STEP * 2.0F)));
			String text = lines.get(i).text();
			float alpha = 255.0F * ext * edge;
			if (i == index && synced) {
				drawKaraokeLine(active, activeBig, text, ax, lineY,
						ax, ax + aw, alpha, media, expandedScroll, expandedScales, true);
			} else {
				boolean overflow = idle.width(text) > aw;
				IslandRender.drawWindowedText(idle, text, ax, lineY,
						color(IslandSettings.hudUpcomingColor, alpha * 0.58F), ax - 1.0F, ax + aw,
						overflow ? 7.0F : 0.0F, 0.0F);
			}
		}
		IslandRender.popScissor();

		if (showSubTrans) {
			String trans = lines.get(index).translation();
			float transY = ay + listHeight + 1.2F;
			boolean overflow = subTransFont.width(trans) > aw;
			IslandRender.drawWindowedText(
					subTransFont,
					trans,
					ax,
					transY,
					color(IslandSettings.resolveTranslationColor(), 220.0F * ext),
					ax - 1.0F,
					ax + aw,
					overflow ? 7.0F : 0.0F,
					0.0F
			);
		}
	}

	private void drawVisualizer(float x, float y, MediaInfo media, float ext, int barColor, float pulse) {
		IslandSettings.VisualizerStyle style = IslandSettings.visualizerStyle;
		if (style == IslandSettings.VisualizerStyle.OFF) return;

		long now = System.nanoTime();
		float dt = waveFrame == 0L ? 0.016F : Math.min(0.1F, (now - waveFrame) / 1.0E9F);
		waveFrame = now;

		float time = now / 1.0E9F;
		float barsX = x + size.width + 6.0F;
		float centerY = y + Math.min(size.height, PILL_HEIGHT + 4.0F) / 2.0F;
		float level = tracker.beatReactor().level();
		if (level <= 0.01F && media.isPlaying()) level = 0.52F;

		if (style == IslandSettings.VisualizerStyle.ORB) {
			float active = media.isPlaying() ? 1.0F : 0.25F;
			float outerR = 3.2F + (level * 3.2F + pulse * 2.2F) * active;
			float innerR = 2.0F + level * 1.4F * active;
			float cx = barsX + 5.5F;
			IslandRender.drawRoundedRect(cx - outerR, centerY - outerR, outerR * 2.0F, outerR * 2.0F,
					outerR, outerR, outerR, outerR, ColorUtil.withAlpha(barColor, 72.0F + pulse * 90.0F));
			IslandRender.drawRoundedRect(cx - innerR, centerY - innerR, innerR * 2.0F, innerR * 2.0F,
					innerR, innerR, innerR, innerR, ColorUtil.opaque(barColor));
			return;
		}

		int barCount = style == IslandSettings.VisualizerStyle.MIRROR ? 6 : 4;
		float spacing = style == IslandSettings.VisualizerStyle.MIRROR ? 2.5F : 3.0F;
		float barW = style == IslandSettings.VisualizerStyle.MIRROR ? 1.4F : 1.6F;

		for (int i = 0; i < barCount; i++) {
			float target;
			if (!media.isPlaying()) {
				target = 2.0F;
			} else if (style == IslandSettings.VisualizerStyle.MIRROR) {
				float distFromMid = Math.abs(i - 2.5F) / 2.5F;
				float wave = Math.abs((float) Math.sin(time * 5.6F + distFromMid * 2.4F));
				target = 2.2F + (1.0F - distFromMid * 0.45F) * wave * (4.0F + level * 7.2F + pulse * 2.5F);
			} else {
				float slow = (float) Math.sin(time * 3.3F + i * 1.3F);
				float fast = (float) Math.sin(time * 7.9F + i * 2.4F);
				float shape = Math.abs(slow * 0.6F + fast * 0.4F);
				target = 2.5F + shape * (3.5F + level * 6.0F + pulse * 1.8F);
			}
			float rate = target > waveValues[i] ? 18.0F : 7.0F;
			waveValues[i] += (target - waveValues[i]) * Math.min(1.0F, dt * rate);
			float value = Math.max(1.6F, waveValues[i]);
			int col = (style == IslandSettings.VisualizerStyle.MIRROR && (i == 2 || i == 3))
					? Karaoke.blend(ColorUtil.opaque(barColor), IslandSettings.hudActiveColor, 0.35F)
					: ColorUtil.opaque(barColor);
			IslandRender.drawRoundedRect(barsX + i * spacing, centerY - value / 2.0F, barW, value,
					barW * 0.5F, barW * 0.5F, barW * 0.5F, barW * 0.5F, col);
		}
	}

	private String pillLine(MediaInfo media) {
		List<LyricsFetcher.LyricLine> lines = tracker.getLyrics();
		String shownTitle = displayTitle(media);
		String shownArtist = artistName(media);
		String line = shownArtist.isEmpty() ? shownTitle : (shownTitle + "  |  " + shownArtist);
		lyricLine = false;
		if (IslandSettings.lyricsEnabled && !lines.isEmpty() && lines.get(0).time() >= 0.0F) {
			float position = tracker.smoothPosition();
			int index = LyricsFetcher.currentIndex(lines, position);
			if (index >= 0 && !inGap(lines, index, position)) {
				line = lines.get(index).text();
				lyricLine = true;
			}
		}
		if (!line.equals(currentLine)) {
			previousLine = currentLine;
			currentLine = line;
			lineChanged = System.currentTimeMillis();
		}
		return line;
	}

	private static String displayTitle(MediaInfo media) {
		return TrackMetadata.displayPair(media.getArtist(), media.getTitle()).title();
	}

	private static String artistName(MediaInfo media) {
		String artist = TrackMetadata.displayPair(media.getArtist(), media.getTitle()).artist();
		return artist == null ? "" : artist;
	}

	private static boolean inGap(List<LyricsFetcher.LyricLine> lines, int index, float position) {
		float next = index + 1 < lines.size() ? lines.get(index + 1).time() : Float.MAX_VALUE;
		float since = position - lines.get(index).time();
		float until = next - position;
		return since > 6.0F && until > 3.0F;
	}

	private float targetWidth(IMediaSession session, boolean showcase, boolean toastActive) {
		if (expanded) return EXPANDED_WIDTH;
		if (toastActive) {
			float tw = Fonts.medium(6.8F).width(statusToast) + 22.0F;
			return Math.max(66.0F, Math.min(132.0F, tw));
		}
		if (showcase) return SHOWCASE_WIDTH;
		if (session == null) return 54.0F;
		float text = Fonts.lyric(7.0F).width(pillLine(session.getMedia()));
		return Math.min(19.0F + text + 6.0F, PILL_MAX_WIDTH);
	}

	private float targetHeight(IMediaSession session, boolean showcase) {
		if (!expanded) return showcase ? SHOWCASE_HEIGHT : PILL_HEIGHT;
		if (session == null) return 64.0F;
		boolean hasLyrics = IslandSettings.lyricsEnabled && !tracker.getLyrics().isEmpty();
		return QuickTimingLayout.expandedHeight(hasLyrics, Minecraft.getInstance().getWindow().getGuiScaledHeight());
	}

    private static void drawProgressStyle(float x, float y, float width, float progress, int alpha, int accent) {
        if (PlusSettings.progressStyle == PlusSettings.ProgressStyle.CLASSIC) {
            IslandRender.drawRoundedRect(x, y, width, 3, 1.5F,1.5F,1.5F,1.5F, color(WHITE, alpha*.20F));
            if (progress > 0) IslandRender.drawRoundedRect(x, y, Math.max(3, width*progress), 3,
                1.5F,1.5F,1.5F,1.5F, color(accent, alpha*.92F));
            return;
        }
        int count = PlusSettings.progressStyle == PlusSettings.ProgressStyle.DOTS ? 20 : 24;
        float pitch = width / count;
        for (int i=0; i<count; i++) {
            float filled = Math.clamp(progress * count - i, 0.0F, 1.0F);
            float segW = PlusSettings.progressStyle == PlusSettings.ProgressStyle.DOTS ? 3 : Math.max(1, pitch-1.5F);
            float cx = x + i * pitch;
            IslandRender.drawRoundedRect(cx,y,segW,3,1.5F,1.5F,1.5F,1.5F,color(WHITE,alpha*.20F));
            if (filled > 0) IslandRender.drawRoundedRect(cx,y,Math.max(.5F,segW*filled),3,
                1.5F,1.5F,1.5F,1.5F,color(accent,alpha*.92F));
        }
    }

	private static String ownerIcon(IMediaSession session) {
		String owner = session.getOwner() == null ? "" : session.getOwner().toLowerCase(Locale.ROOT);
		if (owner.contains("spotify")) return "spotify";
		if (owner.contains("edge")) return "edge";
		if (owner.contains("chrome") || owner.contains("youtube") || owner.contains("firefox")
			|| owner.contains("brave") || owner.contains("opera") || owner.contains("vivaldi")
			|| owner.contains("arc") || owner.contains("zen") || owner.contains("waterfox")
			|| owner.contains("floorp") || owner.contains("308046b0af4a39cb")) {
			return "youtube";
		}
		if (owner.contains("yandex")) return "yandex_music";
		if (owner.contains("soundcloud")) return "soundcloud";
		if (owner.contains("telegram")) return "telegram";
		return null;
	}

	private static int color(int argb, float alpha) {
		int a = (int) Math.max(0.0F, Math.min(255.0F, alpha));
		return (a << 24) | (argb & 0xFFFFFF);
	}

	private static boolean isChat(Minecraft mc) {
		return mc.gui.screen() instanceof net.minecraft.client.gui.screens.ChatScreen;
	}

	public void shareNowPlaying() {
		Minecraft mc = Minecraft.getInstance();
		IMediaSession session = tracker.getSession();
		if (session == null || session.getMedia() == null) {
			showToast("NO ACTIVE SONG");
			return;
		}
		MediaInfo media = session.getMedia();
		TrackMetadata.QueryPair shown = TrackMetadata.displayPair(media.getArtist(), media.getTitle());
		String nowTime = MusicTracker.formatTime((long) tracker.rawPosition());
		String totTime = media.getDuration() > 0 ? (" / " + MusicTracker.formatTime(media.getDuration())) : "";
		String trackLabel = shown.artist().isEmpty() ? shown.title() : (shown.artist() + " - " + shown.title());
		String msg = "[LT5Lyrics] Now Playing: " + trackLabel
				+ " [" + nowTime + totTime + "]";
		if (mc.player != null && mc.player.connection != null) {
			mc.player.connection.sendChat(msg);
			showToast("SHARED IN CHAT");
		} else {
			mc.keyboardHandler.setClipboard(msg);
			showToast("COPIED SONG");
		}
	}

	public boolean handleScroll(double mouseX, double mouseY, double amount) {
		Minecraft mc = Minecraft.getInstance();
		if (ScreenHiding.shouldHide(mc.gui.screen()) || !expanded || mc.gui.screen() == null || !isChat(mc)) return false;

		float x = islandX(mc);
		float islandTop = islandY(mc);

		if (settingsOpen) {
			float[] bounds = settingsBounds(mc, x, islandTop);
			float scale = embeddedSettingsScale(mc);
			if (settingsPanel.handleScroll(bounds[0] / scale, bounds[1] / scale, bounds[2] / scale, bounds[3] / scale,
					mouseX / scale, mouseY / scale, amount)) {
				return true;
			}
		}

		IMediaSession session = tracker.getSession();
		if (session != null && QuickTimingLayout.inArea(x, islandTop, targetHeight(session, false), mouseX, mouseY)) {
			return true; // The wheel here must not scroll lyrics or alter timing accidentally.
		}
		if (!IslandSettings.lyricsEnabled || tracker.getLyrics().isEmpty()) return false;
		if (!hovered(x, islandTop, size.width, size.height, mouseX, mouseY)) return false;

		manualScroll = lyricScroll.get() - (float) amount * LYRIC_STEP;
		manualScrollAt = System.currentTimeMillis();
		return true;
	}

	public boolean handleMouseMove(double mouseX, double mouseY) {
		Minecraft mc = Minecraft.getInstance();
		if (ScreenHiding.shouldHide(mc.gui.screen()) || !expanded || !settingsOpen || mc.gui.screen() == null || !isChat(mc)) return false;
		float scale = embeddedSettingsScale(mc);
		return settingsPanel.handleMouseMove(mouseX / scale, mouseY / scale);
	}

	public boolean handleMouseRelease(double mouseX, double mouseY, int button) {
		return settingsPanel.handleMouseRelease(button);
	}

	public boolean handleClick(double mouseX, double mouseY, int button) {
		Minecraft mc = Minecraft.getInstance();
		if (button != org.lwjgl.sdl.SDLMouse.SDL_BUTTON_LEFT || mc.player == null || ScreenHiding.shouldHide(mc.gui.screen())) return false;
		if (!IslandSettings.showHud) return false;
		if (mc.gui.screen() != null && !isChat(mc)) return false;

		float x = islandX(mc);
		float y = islandY(mc);
		if (isChat(mc) && !mc.gui.hud.isHidden() && communityPanel.handleClick(mc, x, y, size.width, size.height,
				communityObstacle(mc, x, y), mouseX, mouseY)) return true;
		boolean inside = hovered(x, y, size.width, size.height, mouseX, mouseY);
		// Every click on the island reveals the community card in chat, including
		// the click that expands the island. Existing controls still receive the click.
		if (inside && isChat(mc) && !mc.gui.hud.isHidden()) communityPanel.showForIsland();

		if (expanded && settingsOpen) {
			float[] bounds = settingsBounds(mc, x, y);
			float scale = embeddedSettingsScale(mc);
			if (settingsPanel.handleMousePress(bounds[0] / scale, bounds[1] / scale, bounds[2] / scale, bounds[3] / scale,
					mouseX / scale, mouseY / scale, button, false)) {
				return true;
			}
		}

		if (!expanded) {
			if (inside) {
				expanded = true;
				return true;
			}
			return false;
		}

		if (!inside) {
			expanded = false;
			settingsOpen = false;
			communityPanel.closeForIsland();
			return true;
		}

		float dotsX = x + size.width - 24.0F;
		float dotsY = y + size.height - 20.0F;
		if (hovered(dotsX, dotsY, 20.0F, 14.0F, mouseX, mouseY)) {
			settingsOpen = !settingsOpen;
			if (settingsOpen) settingsPanel.onOpened();
			return true;
		}

		IMediaSession session = tracker.getSession();
		if (session == null) {
			float btnW = 78.0F;
			float btnX = x + (EXPANDED_WIDTH - btnW) / 2.0F;
			float btnY = y + size.height - 24.0F;
			if (hovered(btnX, btnY, btnW, 14.0F, mouseX, mouseY)) {
				settingsOpen = !settingsOpen;
				if (settingsOpen) settingsPanel.onOpened();
			}
			return true;
		}

		// SHARE button in top-right of expanded card
		float[] share = shareBounds(x, y);
		if (hovered(share[0], share[1], share[2], share[3], mouseX, mouseY)) {
			shareNowPlaying();
			return true;
		}

		// The temporary controls use the same geometry as drawing and are separate from lyric-copy hits.
		float timingHeight = targetHeight(session, false);
		if (Math.abs(size.height - timingHeight) < 2.0F && size.width >= EXPANDED_WIDTH - 2.0F) {
			int timingButton = QuickTimingLayout.hitButton(x, y, timingHeight, mouseX, mouseY);
			if (timingButton >= 0) {
				float[] changes = {-1.0F, -0.1F, 0.0F, 0.1F, 1.0F};
				if (timingButton == 2) tracker.resetQuickLyricsOffset();
				else tracker.adjustQuickLyricsOffset(changes[timingButton]);
				manualScrollAt = 0L; // Immediately return to the current timed line.
				return true;
			}
		}

		// Click lyric line in expanded view to copy lyric text to clipboard
		List<LyricsFetcher.LyricLine> lines = tracker.getLyrics();
		if (!lines.isEmpty()) {
			float ax = x + 12.0F;
			float aw = EXPANDED_WIDTH - 24.0F;
			float ay = y + 56.0F;
			float ah = QuickTimingLayout.lyricHeight(timingHeight);
			if (ah > LYRIC_STEP && hovered(ax, ay, aw, ah, mouseX, mouseY)) {
				int clickedIdx = (int) Math.floor((mouseY - ay + lyricScroll.get()) / LYRIC_STEP);
				if (clickedIdx >= 0 && clickedIdx < lines.size()) {
					mc.keyboardHandler.setClipboard(lines.get(clickedIdx).text());
					showToast("COPIED LYRIC");
					return true;
				}
			}
		}

		// Bottom-left Player Source Switcher
		if (hovered(x + 10.0F, y + size.height - 22.0F, 24.0F, 14.0F, mouseX, mouseY)) {
			String switched = tracker.cyclePreferredSource();
			if (switched != null) {
				showToast("SOURCE: " + switched.toUpperCase(Locale.ROOT));
			}
			return true;
		}

		float mid = x + size.width / 2.0F;
		float controlY = y + size.height - 24.0F;

		if (hovered(mid - 44.0F, controlY, 15.0F, 15.0F, mouseX, mouseY)) {
			session.previous();
		} else if (hovered(mid - 15.0F, controlY - 0.5F, 16.0F, 16.0F, mouseX, mouseY)) {
			session.playPause();
		} else if (hovered(mid + 14.0F, controlY, 15.0F, 15.0F, mouseX, mouseY)) {
			session.next();
		} else if (hovered(mid + 36.0F, controlY, 14.0F, 14.0F, mouseX, mouseY)) {
			try {
				session.swapCycle();
			} catch (Throwable ignored) {
			}
		}
		return true;
	}

	public boolean isWorldTextEnabled() {
		return IslandSettings.lyricsEnabled && IslandSettings.worldText;
	}

	public boolean shouldHideBossbar() {
		return IslandSettings.hideBossbar && tracker.hasSession();
	}

	private void openMapper() {
		MapperScreen screen = MapperScreen.create(tracker);
		if (screen == null) return;
		expanded = false;
		settingsOpen = false;
		Minecraft.getInstance().gui.setScreen(screen);
	}

	private static boolean hovered(float x, float y, float w, float h, double mx, double my) {
		return mx >= x && mx <= x + w && my >= y && my <= y + h;
	}

	private static final class IslandSize {
		float width;
		float height;

		IslandSize(float width, float height) {
			this.width = width;
			this.height = height;
		}
	}
}
