package com.lt5lyrics.client.island;

import org.lwjgl.sdl.SDLMouse;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.lt5lyrics.client.island.font.Font;
import com.lt5lyrics.client.island.font.DynamicSdfFont;
import com.lt5lyrics.client.island.font.Fonts;
import com.lt5lyrics.client.island.font.MsdfFont;
import com.lt5lyrics.client.island.render.IslandRender;
import com.lt5lyrics.client.music.LyricsFetcher;
import com.lt5lyrics.client.music.MusicTracker;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;


public final class WorldLyrics {
	private static final float DISTANCE = 5.0F;
	private static final float SCALE = 0.035F;
	private static final long FADE = 420L;
	private static final long OUT = 450L;
	private static final long GRAB = 120L;

	private static final java.util.Random RANDOM = new java.util.Random();

	private static Matrix4f modelView;
	private static GpuBufferSlice projection;
	private static Vec3 cameraPos;
	private static float cameraYaw;
	private static ClientLevel level;
	private static List<LyricsFetcher.LyricLine> activeLines;
	private static int activeIndex = -1;
	private static long nextPlace;
	private static long lastFrame;

	private static final List<Placed> recent = new ArrayList<>();

	private static Placed current;
	private static Placed previous;
	private static final List<Placed> retiring = new ArrayList<>();
	private static Placed pinned;
    private static boolean previewWasActive;
    private static Placed previewSavedPin;

	private static boolean pressed;
	private static boolean dragging;
	private static long pressedAt;
	private static double dragDistance;
	private static double gripX;
	private static double gripY;

	// Sky Lyrics & player-following 3D circular boundary state (applies ONLY when IslandSettings.skyLyrics is true)
	private static Vec3 skyAnchor;
	private static Vec3 skyRight;
	private static Vec3 skyUp;
	private static double skyAngle;
	private static boolean skyAngleInitialized;
	private static int skySpawnSeq;
	private static int normalAlternateSeq;
	private static double lastAppliedSkyBoundary = -1.0;
	// One shared Sky slot; following rotates it only around the vertical world axis.
	private static boolean skyFollowActive;
	private static Quaternionf skyFollowRotation;
	private static double skyFollowDistance;
	private static double skyAbsoluteY = Double.NaN;
	private static double lastAppliedSkyHeight = Double.NaN;

	static final class Placed {
		final String text;
		final String translation;
		Vec3 anchor;
		Vec3 right;
		Vec3 up;
		float scale;
		Direction face;
		final float width;
		long placedAt;
		long leftAt;
		long spawnedAtNanos;
		long leftAtNanos;
		float lastAnimLift;
		float lastAnimScale = 1.0F;

		// Per-group Normal 3D spawn snapshot: settings changes cannot flip an existing group.
		boolean normalManaged;
		int normalSideSign = 1;
		double normalForwardDistance = IslandSettings.DEFAULT_NORMAL_DISTANCE;
		double normalSignedOffset = IslandSettings.DEFAULT_NORMAL_OFFSET;
		double normalHeight = 0.45;
        // Distance reference is measured once at group birth; the anchor never moves.
        double normalReferenceDistance;
		// Only used while Follow is active. Null means there is no prior eye sample.
		Vec3 normalFollowEye;
		int wrappedWordLimit = -1;
		boolean wrappedTranslations;
		List<NormalLyricsLayout.Span> mainSpans = List.of();
		List<NormalLyricsLayout.Span> translationSpans = List.of();
		// Sky wraps are cached as source spans, never as atlas UVs.
		float skyWrapWidth = -1.0F;
		boolean skyWrapTranslations;
		IslandSettings.FontPreset skyWrapPreset;
		String skyWrapCustom = "";
		List<SkyLineWrap.Span> skyMainSpans = List.of();
		List<SkyLineWrap.Span> skyTranslationSpans = List.of();
		float skyHalfWidth, skyHalfHeight;
		// Latest rendered dimensions for drag picking; in local world units, not HUD pixels.
		float normalHalfWidth, normalTop, normalBottom;
		Vec3 normalRenderRight, normalRenderUp;

		// Sky Lyrics 3D world-space horizon state (unused in normal 3D world lyrics)
		boolean skyManaged;
		int skySeqIndex;

		Placed(
				String text,
				Vec3 anchor,
				Vec3 right,
				Vec3 up,
				float scale,
				Direction face,
				float width
		) {
			this(text, "", anchor, right, up, scale, face, width);
		}

		Placed(
				String text,
				String translation,
				Vec3 anchor,
				Vec3 right,
				Vec3 up,
				float scale,
				Direction face,
				float width
		) {
			this.text = text;
			this.translation = translation == null ? "" : translation;
			this.anchor = anchor;
			this.right = right;
			this.up = up;
			this.scale = scale;
			this.face = face;
			this.width = width;
			this.placedAt = System.currentTimeMillis();
			this.spawnedAtNanos = System.nanoTime();
		}

		Placed withText(String line, float width) {
			return new Placed(
					line,
					"",
					anchor,
					right,
					up,
					scale,
					face,
					width
			);
		}

		Placed withTextAndTranslation(String line, String translation, float width) {
			Placed copy = new Placed(
					line,
					translation,
					anchor,
					right,
					up,
					scale,
					face,
					width
			);
			copy.normalManaged = this.normalManaged;
			copy.normalSideSign = this.normalSideSign;
			copy.normalForwardDistance = this.normalForwardDistance;
			copy.normalSignedOffset = this.normalSignedOffset;
			copy.normalHeight = this.normalHeight;
            copy.normalReferenceDistance = this.normalReferenceDistance;
			copy.skyManaged = this.skyManaged;
			copy.skySeqIndex = this.skySeqIndex;
			return copy;
		}
	}

	public static void capture() {
		Minecraft mc = Minecraft.getInstance();

		if (level != mc.level) {
			reset();
			level = mc.level;
		}

		Camera camera = mc.gameRenderer.mainCamera();

		if (!camera.isInitialized()) {
			return;
		}

		modelView = new Matrix4f(RenderSystem.getModelViewMatrixCopy());
		projection = RenderSystem.getProjectionMatrixBuffer();
		cameraPos = camera.position();
		cameraYaw = camera.yRot();
	}

	public static void reset() {
		level = null;
		cameraPos = null;
		modelView = null;
		projection = null;

		current = null;
		previous = null;
		retiring.clear();
		pinned = null;
        previewWasActive = false; previewSavedPin = null;

		pressed = false;
		dragging = false;
		pressedAt = 0L;

		activeLines = null;
		activeIndex = -1;
		nextPlace = 0L;
		lastFrame = 0L;

		recent.clear();

		skyAnchor = null;
		skyRight = null;
		skyUp = null;
		skyAngle = 0.0;
		skyAngleInitialized = false;
		skySpawnSeq = 0;
		normalAlternateSeq = 0;
		lastAppliedSkyBoundary = -1.0;
		skyFollowActive = false;
		skyFollowRotation = null;
		skyFollowDistance = 0.0;
		skyAbsoluteY = Double.NaN;
		lastAppliedSkyHeight = Double.NaN;
	}

	/**
	 * Live-updates Sky Lyrics scale & boundary placement when sliders change in Settings,
	 * without resetting or blanking active lyrics. Radius controls the boundary and the Text Size
	 * control sets the preferred glyph scale; oversized wrapped groups can be reduced to fit.
	 * Does nothing when Sky Lyrics is off.
	 */
	public static void onSkySettingsChanged() {
		if (!IslandSettings.skyLyrics) return;
		float independentScale = computeSkyScale(IslandSettings.skyTextSize);
		if (current != null) current.scale = independentScale;
		if (previous != null) previous.scale = independentScale;
		for (Placed r : retiring) r.scale = independentScale;
		// Radius changes are eased in render(), not applied instantaneously here.
		// This avoids teleporting a visible line while dragging the distance slider.
	}

	/** Frame-rate-independent transition for the invisible boundary. */
	static double easeSkyBoundary(double currentRadius, double targetRadius, float dt) {
		double target = clampSkyBoundaryRadius(targetRadius);
		if (!Double.isFinite(currentRadius) || currentRadius <= 0.0) return target;
		double amount = 1.0 - Math.exp(-2.8 * Math.max(0.0, Math.min(0.10, dt)));
		double next = currentRadius + (target - currentRadius) * amount;
		return Math.abs(next - target) < 0.02 ? target : next;
	}

	/** Equal list copies are not a new Sky lyric or a new track. */
	static boolean equivalentSkyLyrics(List<LyricsFetcher.LyricLine> oldLines, List<LyricsFetcher.LyricLine> newLines) {
		return oldLines != null && newLines != null && oldLines.equals(newLines);
	}

	static boolean skyEchoFinished(boolean contextEnabled, float alpha) {
		return !contextEnabled || alpha <= 0.02F;
	}

	/** Reject only tiny backwards clock jitter across a line boundary; real seeks remain immediate. */
	static int stabilizeSkyIndex(List<LyricsFetcher.LyricLine> lines, int active, int candidate, float position) {
		if (active >= 0 && active < lines.size() && candidate < active && Float.isFinite(position)) {
			float start = lines.get(active).time();
			if (start >= 0.0F && position >= start - 0.15F) return active;
		}
		return candidate;
	}

	private static void retire(Placed placed) {
		if (placed == null) {
			return;
		}

		placed.leftAt = System.currentTimeMillis();
		placed.leftAtNanos = System.nanoTime();
		previous = placed;
		retiring.add(placed);
		int maxRetiring = IslandSettings.skyLyrics ? 5 : 3;
		while (retiring.size() > maxRetiring) {
			retiring.removeFirst();
		}
	}

	public static void render(MusicTracker tracker) {
		Minecraft mc = Minecraft.getInstance();

		if (modelView == null || projection == null || cameraPos == null) {
			return;
		}

		if (mc.player == null || mc.level == null || mc.gui.hud.isHidden()) {
			release();
			return;
		}

		if (pressed && !canGrab(mc)) {
			release();
		}

        boolean testing = TestLyrics.active(tracker);
        if (testing != previewWasActive) {
            if(testing) previewSavedPin=pinned; else {pinned=previewSavedPin;previewSavedPin=null;}
            current=null; previous=null; retiring.clear(); activeLines=null; activeIndex=-1; nextPlace=0L; recent.clear();
            previewWasActive=testing;
        }
		List<LyricsFetcher.LyricLine> lines = testing ? TestLyrics.lines() : tracker.getLyrics();

		if (lines != activeLines) {
			// A provider may replace a list with equal content. It is still the same
			// lyric: do not respawn it (and move a fixed Normal anchor) on a copy.
			// This preserves the existing Sky equal-content behavior as well.
			boolean unchangedRefresh = activeLines != null && activeLines.equals(lines);
			activeLines = lines;
			if (!unchangedRefresh) {
				activeIndex = -1;
				nextPlace = 0L;
				recent.clear();
			}
		}

		float position = TestLyrics.position(tracker);

		int index = lines.isEmpty()
				? -1
				: LyricsFetcher.currentIndex(lines, position);
		if (IslandSettings.skyLyrics) index = stabilizeSkyIndex(lines, activeIndex, index, position);

		String line = index >= 0 ? lines.get(index).text() : "";
		String translation = index >= 0 ? lines.get(index).translation() : "";
		// LRC supplies the next line's timestamp, but generally no explicit final
		// line end. Use actual media duration if it is available; do not guess one.
		if (!IslandSettings.skyLyrics && index >= 0) {
			var session = tracker.getSession();
			var media = session == null ? null : session.getMedia();
			long duration = media == null ? 0L : media.getDuration();
			if (normalLastLineFinished(index, lines.size(), tracker.rawPosition(), duration)) line = "";
		}

		long now = System.currentTimeMillis();
		Font font = Fonts.lyric(10.0F);

		if (!line.isEmpty()
				&& (
				index != activeIndex
						|| current == null && now >= nextPlace
		)) {
			// A new Normal group gets a new side; Sky reuses its single persistent slot.
			Placed at = pressed ? current
					: (IslandSettings.locked && !IslandSettings.skyLyrics) ? pinned : null;

			retire(current);

			float lineWidth = font.width(line);
			if (at == null) {
				Placed spawned = place(mc, line);
				current = spawned == null ? null : spawned.withTextAndTranslation(line, translation, lineWidth);
			} else {
				current = at.withTextAndTranslation(line, translation, lineWidth);
			}

			// Ground placement happens exactly once, when this fixed Normal group is
			// born. Translation and wrapping are known now; active groups never move.
			if (!IslandSettings.skyLyrics && current != null && current.normalManaged
					&& !normalFollowingEnabled()) {
				groundNewFixedNormalGroup(mc, current, font, at != null);
                current.normalReferenceDistance = Math.max(.01, current.anchor.distanceTo(cameraPos));
			}

			if (IslandSettings.skyLyrics && current != null) {
				current.scale = computeSkyScale(IslandSettings.skyTextSize);
			}

			if (IslandSettings.locked && !IslandSettings.skyLyrics && current != null
					&& (pinned == null || !normalFollowingEnabled())) {
				// Keep a locked X/Z. If a later group had to be raised above the
				// player's feet, retain that new height for subsequent groups.
				pinned = current;
			}

			nextPlace = now + 1000L;

		} else if (line.isEmpty() && current != null && !pressed) {
			retire(current);
			current = null;
		}

		activeIndex = index;

		long frame = System.nanoTime();

		float frameDt = lastFrame == 0L
				? 0.016F
				: Math.max(0.0F, (frame - lastFrame) / 1.0E9F);
		float dt = Math.min(0.05F, frameDt); // retain existing animation and Sky behavior
		float followDt = Math.min(0.20F, frameDt); // Normal Follow can recover at low frame rates

		lastFrame = frame;

		updateDrag(mc, dt);

		Vec3 skyPlayerCenter = null;
		double skyBoundary = IslandSettings.DEFAULT_SKY_BOUNDARY_RADIUS;

		if (IslandSettings.skyLyrics) {
			float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
			skyPlayerCenter = mc.player.getPosition(partialTick);
			skyBoundary = easeSkyBoundary(lastAppliedSkyBoundary, effectiveBoundaryForClient(mc), dt);
			if (skyAnchor == null && IslandSettings.skySongHeader && tracker.hasSession()) {
				initSkyBasis(mc, skyPlayerCenter, skyBoundary);
			}
			updateSkyPlacement(mc, skyPlayerCenter, skyBoundary, dt);
		} else if (normalFollowingEnabled()) {
			// Read the interpolated eye and view once per frame, not only at a
			// lyric change. Visibility never gates this position update.
			float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
			Vec3 eye = mc.player.getEyePosition(partial);
			Vec3 view = new Vec3(mc.gameRenderer.mainCamera().forwardVector()).normalize();
			follow(mc, current, eye, view, followDt);
			for (Placed r : retiring) follow(mc, r, eye, view, followDt);
		} else {
			// Fixed means absolutely no anchor updates. Clearing the prior eye also
			// avoids applying motion that happened while Follow was switched off.
			advanceNormalFollow(current, false, null, null, dt);
			for (Placed r : retiring) advanceNormalFollow(r, false, null, null, dt);
		}

        // Still advance playback/follow while hidden; simply do not submit draw calls.
        // In particular this does not reset or re-anchor fixed or Sky lyrics.
        if (ScreenHiding.shouldHide(mc.gui.screen())) return;

		if (IslandSettings.skyLyrics && IslandSettings.skySongHeader && skyPlayerCenter != null) {
			drawSkySongHeaderIfActive(font, tracker, lines, index, skyPlayerCenter, skyBoundary);
		}

		IslandSettings.LyricAnimation anim = IslandSettings.lyricAnimation;
		float animSpeed = IslandSettings.animationSpeed;

		// Retire Normal groups normally; Sky shows at most one optional previous group above the slot.
		for (int i = 0; i < retiring.size(); ) {
			Placed r = retiring.get(i);
			long exitMs = Math.max(0L, (frame - r.leftAtNanos) / 1_000_000L);
			float exitAgeSec = exitMs / 1000.0F;
			if (IslandSettings.skyLyrics) {
				float skyEchoAlpha = computeSkyEchoAlpha(exitMs, animSpeed);
				if (skyEchoFinished(IslandSettings.skyContextLines, skyEchoAlpha) || r != retiring.getLast()) {
					retiring.remove(i);
					if (previous == r) previous = retiring.isEmpty() ? null : retiring.getLast();
					continue;
				}
				float userScale = Math.clamp(IslandSettings.lyricTextScale, 0.50F, 2.00F);
				float optionalBase = r.scale * userScale;
				SkyRows optionalRows = buildSkyRows(font, r, skyWrapWidth(font, r.anchor, optionalBase));
				float optionalScale = skyFitScale(font, optionalRows, r.anchor, optionalBase * r.lastAnimScale, false);
				Vec3 previousAnchor = r.anchor;
				float visible = 1.0F;
				if (current != null && current.skyManaged) {
					float mainBase = current.scale * userScale;
					SkyRows mainRows = buildSkyRows(font, current, skyWrapWidth(font, current.anchor, mainBase));
					float mainScale = skyFitScale(font, mainRows, current.anchor,
							mainBase * current.lastAnimScale, IslandSettings.skyContextLines);
					previousAnchor = skyContextAnchor(current.anchor, mainRows, mainScale, optionalRows,
							optionalScale, true, font.getSize(), skyPlayerCenter, skyBoundary);
					visible = skyContextVisibility(font, mainRows, current.anchor, current.right, current.up, mainScale,
							optionalRows, previousAnchor, r.right, r.up, optionalScale);
				}
				int outAlpha = Math.round(185.0F * skyEchoAlpha * visible);
				drawSky(font, r, tracker, lines, outAlpha, false, r.lastAnimLift * 4.0F,
						r.lastAnimScale, 1.0F, anim, exitAgeSec, skyPlayerCenter, skyBoundary, previousAnchor);
				i++;
				continue;
			}
			LyricAnimator.MotionState state = LyricAnimator.computeExiting(
					anim,
					exitMs,
					animSpeed,
					r.lastAnimLift,
					r.lastAnimScale
			);
			if (state.alpha() <= 0.02F) {
				retiring.remove(i);
				if (previous == r) {
					previous = retiring.isEmpty() ? null : retiring.getLast();
				}
				continue;
			}
			int outAlpha = (int) (215.0F * state.alpha());
			if (current != null && r.normalManaged && current.normalManaged
					&& r.normalSideSign == current.normalSideSign && r.anchor.distanceToSqr(current.anchor) < 4.0) {
				i++;
				continue; // no stale text stacked over the current group
			}
			drawHologram(
					mc,
					font,
					r,
					tracker,
					lines,
					outAlpha,
					false,
					state.liftUnits() * 0.48F,
					state.scaleFactor(),
					state.progress(),
					anim,
					exitAgeSec
			);
			i++;
		}

		if (current != null) {
			long elapsedMs = Math.max(0L, (frame - current.spawnedAtNanos) / 1_000_000L);
			float ageSec = elapsedMs / 1000.0F;

			LyricAnimator.MotionState state = LyricAnimator.computeActive(
					anim,
					elapsedMs,
					animSpeed
			);
			current.lastAnimLift = state.liftUnits();
			current.lastAnimScale = state.scaleFactor();

			int inAlpha = (int) (255.0F * state.alpha());
			if (IslandSettings.skyLyrics) {
				drawSky(
						font,
						current,
						tracker,
						lines,
						inAlpha,
						true,
						state.liftUnits() * 6.5F,
						state.scaleFactor(),
						state.progress(),
						anim,
						ageSec,
						skyPlayerCenter,
						skyBoundary,
						null
				);
			} else {
				drawHologram(
						mc,
						font,
						current,
						tracker,
						lines,
						inAlpha,
						true,
						state.liftUnits() * 0.48F,
						state.scaleFactor(),
						state.progress(),
						anim,
						ageSec
				);
			}
		}
	}

	// =========================================================================
	// Sky Lyrics 3D Circular Boundary Math & Placement (ONLY for Sky Lyrics)
	// =========================================================================

	static double clampSkyBoundaryRadius(double radius) {
		if (!Double.isFinite(radius)) {
			return IslandSettings.DEFAULT_SKY_BOUNDARY_RADIUS;
		}
		return Math.clamp(
				radius,
				IslandSettings.MIN_SKY_BOUNDARY_RADIUS,
				IslandSettings.MAX_SKY_BOUNDARY_RADIUS
		);
	}

	static double computeEffectiveBoundaryRadius(double configuredRadius, double depthFar) {
		// Sky Lyrics use far-plane-safe projection in island_msdf.vsh (Extra.z > 0.5),
		// so the invisible 3D sky boundary is NEVER artificially capped by low chunk render distance.
		return clampSkyBoundaryRadius(configuredRadius);
	}

	private static double effectiveBoundaryForClient(Minecraft mc) {
		return clampSkyBoundaryRadius(IslandSettings.skyBoundaryRadius);
	}

	public static double maxVerticalSkyExtent(double boundaryRadius) {
		double safe = Math.max(16.0, boundaryRadius);
		return Math.max(140.0, safe * 0.78);
	}

	static double effectiveSkyHeight(double requestedHeight, double boundaryRadius, double halfGlyphHeight) {
		double safeHeight = Double.isFinite(requestedHeight) ? requestedHeight : IslandSettings.DEFAULT_SKY_HEIGHT;
		double maxVertical = Math.max(12.0, maxVerticalSkyExtent(boundaryRadius) - halfGlyphHeight - 2.0);
		double minVertical = Math.min(12.0, maxVertical);
		return Math.clamp(safeHeight, minVertical, maxVertical);
	}

	static double maxHorizontalRadiusAtHeight(double boundaryRadius, double verticalExtent) {
		return Math.max(8.0, boundaryRadius - 0.5);
	}

	/**
	 * Preferred Sky glyph scale from Sky Lyrics Text Size, independent of radius.
	 * The wrapped group may be rendered smaller when required to fit the view.
	 */
	public static float computeSkyScale(float skyTextSize) {
		float t = Float.isFinite(skyTextSize)
				? Math.clamp(skyTextSize, IslandSettings.MIN_SKY_TEXT_SIZE, IslandSettings.MAX_SKY_TEXT_SIZE)
				: IslandSettings.DEFAULT_SKY_TEXT_SIZE;
		return 0.28F + t * 1.64F;
	}

	static float computeSkyScale(float skyTextSize, float rawTextWidth, double maxHorizRadius) {
		return computeSkyScale(skyTextSize);
	}

	/**
	 * Subtle atmospheric horizon alpha attenuation (aerial perspective) exclusively for Sky Lyrics.
	 * Preserves crisp mid-sky presence while giving far-horizon lyrics natural atmospheric depth
	 * and softly feathering any lyric that approaches the very outer rim of the invisible boundary.
	 */
	public static float computeSkyHorizonFade(double worldDistance, double boundaryRadius) {
		double safeDist = Math.max(0.0, worldDistance);
		double safeRadius = Math.max(80.0, boundaryRadius);
		double distFadeT = smoothstep(110.0, 960.0, safeDist);
		float aerialFade = (float) (1.0 - 0.26 * distFadeT);
		double rimRatio = safeDist / safeRadius;
		float rimFeather = (float) (1.0 - 0.55 * smoothstep(0.93, 0.998, rimRatio));
		return Math.clamp(aerialFade * rimFeather, 0.25F, 1.0F);
	}

	private static double smoothstep(double edge0, double edge1, double x) {
		if (edge1 <= edge0) return x >= edge1 ? 1.0 : 0.0;
		double t = Math.clamp((x - edge0) / (edge1 - edge0), 0.0, 1.0);
		return t * t * (3.0 - 2.0 * t);
	}

	private static float computeSkyEchoAlpha(long exitMs, float animSpeed) {
		float speed = Math.clamp(animSpeed, 0.5F, 2.0F);
		float durationMs = 2850.0F / (0.65F + 0.35F * speed);
		float u = Math.clamp(exitMs / durationMs, 0.0F, 1.0F);
		return (float) (1.0 - smoothstep(0.0, 1.0, u));
	}

	/**
	 * Constrains a Sky Lyric anchor so that the anchor remains strictly inside the
	 * player-centered horizontal circular sky boundary of radius boundaryRadius.
	 */
	static Vec3 constrainSkyAnchor(
			Vec3 anchor,
			Vec3 playerPos,
			double boundaryRadius,
			double requestedHeight,
			float scale,
			double fallbackAngle
	) {
		double safeBoundary = Math.max(16.0, boundaryRadius);
		// Anchor position must not depend on glyph size; individual vertices are bounded separately.
		double maxSkyY = playerPos.y + Math.max(16.0, maxVerticalSkyExtent(safeBoundary) - 8.0);
		double minSkyY = playerPos.y + 10.0;
		double fallbackDy = effectiveSkyHeight(requestedHeight, safeBoundary, 0.0);
		double rawY = Double.isFinite(anchor.y) ? anchor.y : (playerPos.y + fallbackDy);
		double targetY = Math.clamp(rawY, minSkyY, maxSkyY);

		double maxHoriz = Math.max(8.0, safeBoundary * 0.94);
		double minHoriz = Math.min(20.0, maxHoriz * 0.28);

		double dx = anchor.x - playerPos.x;
		double dz = anchor.z - playerPos.z;
		double horizDist = Math.hypot(dx, dz);

		double angle = horizDist > 1.0E-5 ? Math.atan2(dx, dz) : fallbackAngle;
		double clampedHoriz = Math.clamp(horizDist, minHoriz, maxHoriz);

		return new Vec3(
				playerPos.x + Math.sin(angle) * clampedHoriz,
				targetY,
				playerPos.z + Math.cos(angle) * clampedHoriz
		);
	}

	/**
	 * Computes a 3D world position for a Sky Lyric glyph vertex wrapped smoothly along the
	 * circular arc centered on the player, then strictly clamps to the 3D circular boundary.
	 */
	static Vec3 computeSkyVertexWorld(
			Vec3 anchor,
			Vec3 playerPos,
			double boundaryRadius,
			float offsetX,
			float offsetY
	) {
		return computeSkyVertexWorld(anchor, playerPos, boundaryRadius, offsetX, offsetY, 1.0F);
	}

	/**
	 * Computes a 3D world position for a Sky Lyric glyph vertex with configurable curvature
	 * (curveFactor = 0.0 -> flat tangent plane, 1.0 -> natural player-centered dome circle,
	 * 2.0 -> deep amphitheater wrap) while keeping the lyric center pinned at anchor and strictly
	 * clamped inside the 3D circular sky boundary.
	 */
	static Vec3 computeSkyVertexWorld(
			Vec3 anchor,
			Vec3 playerPos,
			double boundaryRadius,
			float offsetX,
			float offsetY,
			float curveFactor
	) {
		double dx = anchor.x - playerPos.x;
		double dz = anchor.z - playerPos.z;
		double horizRadius = Math.max(4.0, Math.hypot(dx, dz));
		double baseAngle = Math.atan2(dx, dz);

		float c = Float.isFinite(curveFactor)
				? Math.clamp(curveFactor, IslandSettings.MIN_SKY_CURVE, IslandSettings.MAX_SKY_CURVE)
				: IslandSettings.DEFAULT_SKY_CURVE;

		double wx;
		double wz;
		if (c <= 1.0E-4F) {
			// Flat tangent plane (0% curve) facing inward toward the player
			wx = anchor.x - Math.cos(baseAngle) * offsetX;
			wz = anchor.z + Math.sin(baseAngle) * offsetX;
		} else {
			// Smooth curvature circle of radius (horizRadius / c) tangent to anchor
			double arcRadius = horizRadius / c;
			double centerX = anchor.x - Math.sin(baseAngle) * arcRadius;
			double centerZ = anchor.z - Math.cos(baseAngle) * arcRadius;
			double angularOffset = offsetX / arcRadius;
			double vertexAngle = baseAngle - angularOffset;
			wx = centerX + Math.sin(vertexAngle) * arcRadius;
			wz = centerZ + Math.cos(vertexAngle) * arcRadius;
		}
		double wy = anchor.y + offsetY;

		return clampPointToSkyBoundary(wx, wy, wz, playerPos, boundaryRadius);
	}

	/**
	 * Ironclad per-vertex 3D horizontal circular sky boundary clamp around playerPos.
	 * Enforces horizontal distance hypot(dx, dz) <= boundaryRadius with generous vertical sky ceiling.
	 */
	static Vec3 clampPointToSkyBoundary(
			double wx,
			double wy,
			double wz,
			Vec3 playerPos,
			double boundaryRadius
	) {
		double maxHoriz = Math.max(1.0, boundaryRadius - 0.02);
		double dx = wx - playerPos.x;
		double dy = wy - playerPos.y;
		double dz = wz - playerPos.z;

		double maxVert = maxVerticalSkyExtent(boundaryRadius) - 0.02;
		if (dy > maxVert) {
			dy = maxVert;
		} else if (dy < -maxVert) {
			dy = -maxVert;
		}

		double horizSq = dx * dx + dz * dz;
		double maxHorizSq = maxHoriz * maxHoriz;
		if (horizSq > maxHorizSq && horizSq > 1.0E-12) {
			double factor = maxHoriz / Math.sqrt(horizSq);
			dx *= factor;
			dz *= factor;
		}

		return new Vec3(playerPos.x + dx, playerPos.y + dy, playerPos.z + dz);
	}

	static boolean isInsideSkyBoundary(Vec3 point, Vec3 playerPos, double boundaryRadius) {
		double dx = point.x - playerPos.x;
		double dy = point.y - playerPos.y;
		double dz = point.z - playerPos.z;
		double eps = 1.0E-3;
		double horizLimitSq = (boundaryRadius + eps) * (boundaryRadius + eps);
		double maxVert = maxVerticalSkyExtent(boundaryRadius) + eps;
		double horizSq = dx * dx + dz * dz;
		return horizSq <= horizLimitSq && Math.abs(dy) <= maxVert;
	}

	private static void updateSkyPlacement(Minecraft mc, Vec3 playerCenter, double boundaryRadius, float dt) {
		if (!IslandSettings.skyLyrics || mc.player == null || cameraPos == null) return;
		if (skyAnchor == null || skyRight == null || skyUp == null) {
			initSkyBasis(mc, playerCenter, boundaryRadius);
		}
		if (!Double.isFinite(skyAbsoluteY)) skyAbsoluteY = skyAnchor.y;
		if (lastAppliedSkyBoundary <= 0.0) {
			lastAppliedSkyBoundary = boundaryRadius;
		} else if (Math.abs(boundaryRadius - lastAppliedSkyBoundary) > 0.00001) {
			if (!IslandSettings.skyCrosshairFollow) {
				skyAnchor = SkyStableLayout.rescaleHorizontal(skyAnchor, playerCenter,
						lastAppliedSkyBoundary, boundaryRadius, skyAbsoluteY);
			}
			lastAppliedSkyBoundary = boundaryRadius;
		}
		// Only an explicit height-setting change moves the intended Y; looking up/down,
		// jumping and changing camera perspective do not. The boundary may clamp Y.
		if (!Double.isFinite(lastAppliedSkyHeight)
				|| Math.abs(IslandSettings.skyHeight - lastAppliedSkyHeight) > 0.001) {
			skyAbsoluteY = playerCenter.y + effectiveSkyHeight(IslandSettings.skyHeight, boundaryRadius, 0.0);
			lastAppliedSkyHeight = IslandSettings.skyHeight;
		}
		float independentScale = computeSkyScale(IslandSettings.skyTextSize);
		Camera camera = mc.gameRenderer.mainCamera();
		if (pressed && dragging && current != null) {
			// A deliberate manual drag is allowed to choose a new slot, including height.
			skyAnchor = constrainSkyAnchor(current.anchor, playerCenter, boundaryRadius,
					IslandSettings.skyHeight, independentScale, skyAngle);
			skyAbsoluteY = skyAnchor.y;
			double dx = skyAnchor.x - cameraPos.x, dz = skyAnchor.z - cameraPos.z;
			skyAngle = Math.atan2(dx, dz);
			IslandSettings.skyAngleDegrees = (float) Math.toDegrees(skyAngle);
			skyFollowRotation = SkyFollowLayout.yawRotation(skyAngle);
			skyFollowDistance = Math.hypot(dx, dz);
			skyFollowActive = IslandSettings.skyCrosshairFollow;
		} else if (IslandSettings.skyCrosshairFollow && camera.isInitialized()) {
			if (!skyFollowActive || skyFollowRotation == null) {
				double dx = skyAnchor.x - cameraPos.x, dz = skyAnchor.z - cameraPos.z;
				skyAngle = Math.atan2(dx, dz);
				skyFollowRotation = SkyFollowLayout.yawRotation(skyAngle);
				skyFollowDistance = Math.hypot(dx, dz);
				skyFollowActive = true;
			}
			// Use the actual camera yaw (including front-facing third person), not pitch/roll.
			Quaternionf targetYaw = SkyStableLayout.cameraYaw(camera.yRot());
			skyFollowRotation = SkyFollowLayout.smoothRotation(skyFollowRotation, targetYaw, dt);
			skyFollowDistance = SkyStableLayout.easeDistance(skyFollowDistance,
					SkyStableLayout.slotDistance(boundaryRadius), dt);
			skyAnchor = SkyStableLayout.followSlot(cameraPos, playerCenter, skyFollowRotation,
					skyFollowDistance, skyAbsoluteY, boundaryRadius);
			Vec3 forward = SkyFollowLayout.rotate(skyFollowRotation, new Vec3(0, 0, 1));
			skyAngle = Math.atan2(forward.x, forward.z);
		} else {
			if (skyFollowActive) {
				// Switching OFF freezes the current world slot, rather than respawning it.
				skyFollowActive = false;
				IslandSettings.skyAngleDegrees = (float) Math.toDegrees(skyAngle);
			}
			skyAnchor = clampPointToSkyBoundary(skyAnchor.x, skyAbsoluteY, skyAnchor.z, playerCenter, boundaryRadius);
			double dx = skyAnchor.x - playerCenter.x, dz = skyAnchor.z - playerCenter.z;
			if (Math.hypot(dx, dz) > 1.0E-6) skyAngle = Math.atan2(dx, dz);
		}
		skyAngleInitialized = true;
		skyRight = new Vec3(-Math.cos(skyAngle), 0.0, Math.sin(skyAngle)).normalize();
		skyUp = new Vec3(0.0, 1.0, 0.0);
		applySkySlot(current, independentScale);
		for (Placed r : retiring) applySkySlot(r, independentScale);
	}

	private static void applySkySlot(Placed node, float scale) {
		if (node == null || !node.skyManaged) return;
		node.anchor = skyAnchor;
		node.right = skyRight;
		node.up = skyUp;
		node.scale = scale;
		node.face = null;
	}

	private static void initSkyBasis(Minecraft mc, Vec3 playerCenter, double boundaryRadius) {
		Camera camera = mc.gameRenderer.mainCamera();
		if (IslandSettings.skyCrosshairFollow && camera.isInitialized()) {
			skyAngle = -Math.toRadians(camera.yRot());
		} else if (!skyAngleInitialized && Math.abs(IslandSettings.skyAngleDegrees) < 0.01F) {
			skyAngle = camera.isInitialized() ? -Math.toRadians(camera.yRot())
					: -Math.toRadians(mc.player.getYRot());
			IslandSettings.skyAngleDegrees = (float) Math.toDegrees(skyAngle);
		} else {
			skyAngle = Math.toRadians(IslandSettings.skyAngleDegrees);
		}
		skyAngleInitialized = true;
		lastAppliedSkyBoundary = boundaryRadius;
		skyAnchor = SkyStableLayout.initialSlot(playerCenter, boundaryRadius, skyAngle, IslandSettings.skyHeight);
		skyAbsoluteY = skyAnchor.y;
		lastAppliedSkyHeight = IslandSettings.skyHeight;
		skyRight = new Vec3(-Math.cos(skyAngle), 0.0, Math.sin(skyAngle)).normalize();
		skyUp = new Vec3(0.0, 1.0, 0.0);
	}

	static Placed spawnSkyHorizonNode(
			String line, float rawWidth, Vec3 playerCenter, double boundaryRadius,
			double baseAngleRad, int seqIndex, double baseSkyHeight, float skyTextSize
	) {
		double safeBoundary = clampSkyBoundaryRadius(boundaryRadius);
		Vec3 anchor = SkyStableLayout.initialSlot(playerCenter, safeBoundary, baseAngleRad, baseSkyHeight);
		Vec3 right = new Vec3(-Math.cos(baseAngleRad), 0.0, Math.sin(baseAngleRad)).normalize();
		Placed node = new Placed(line, anchor, right, new Vec3(0, 1, 0),
				computeSkyScale(skyTextSize), null, rawWidth);
		node.skyManaged = true;
		node.skySeqIndex = seqIndex;
		// Every verse uses this slot; no independent per-line drift.
		return node;
	}

	private static Placed place(Minecraft mc, String line) {
		if (IslandSettings.skyLyrics && mc.player != null) {
			float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
			Vec3 playerCenter = mc.player.getPosition(partialTick);
			double effectiveBoundary = lastAppliedSkyBoundary > 0.0
					? lastAppliedSkyBoundary : effectiveBoundaryForClient(mc);

			if (skyAnchor == null || skyRight == null || skyUp == null || !skyAngleInitialized) {
				initSkyBasis(mc, playerCenter, effectiveBoundary);
			}

			float rawWidth = Fonts.lyric(10.0F).width(line);
			int seq = skySpawnSeq++;
			Placed node = spawnSkyHorizonNode(
					line,
					rawWidth,
					playerCenter,
					effectiveBoundary,
					skyAngle,
					seq,
					IslandSettings.skyHeight,
					IslandSettings.skyTextSize
			);
			// Every lyric uses the same persistent slot, independent of sequence number.
			node.anchor = skyAnchor;
			node.right = skyRight;
			node.up = skyUp;
			return node;
		}

		return spawnNormalNode(mc, line);
	}

	/** Horizontal right remains perpendicular to the full 3D look direction, even at steep pitch. */
	static Vec3 normalHorizontalRight(Vec3 view, float yawDegrees) {
		double h = Math.hypot(view.x, view.z);
		if (h > 1.0E-8) return new Vec3(-view.z / h, 0.0, view.x / h);
		double yaw = Math.toRadians(yawDegrees);
		return new Vec3(-Math.cos(yaw), 0.0, -Math.sin(yaw));
	}

	static Vec3 computeNormalAnchor(Vec3 eye, Vec3 look, float yawDegrees, double distance, double signedOffset, double height) {
		Vec3 forward = look.normalize();
		Vec3 right = normalHorizontalRight(forward, yawDegrees);
		Vec3 up = right.cross(forward).normalize();
		return eye.add(forward.scale(distance)).add(right.scale(signedOffset)).add(up.scale(height));
	}

	/** Fixed placement uses the player's feet and horizontal yaw, not look pitch. */
	static Vec3 computeFixedNormalAnchor(Vec3 feet, Vec3 look, float yawDegrees, double distance, double signedOffset) {
		Vec3 right = normalHorizontalRight(look, yawDegrees);
		Vec3 forward = new Vec3(right.z, 0.0, -right.x);
		return feet.add(forward.scale(distance)).add(right.scale(signedOffset));
	}

	static int chooseNormalSide(IslandSettings.NormalSide side, double requestedOffset, int alternateIndex, boolean randomRight) {
		int start = requestedOffset < 0.0 ? -1 : 1;
		return switch (side == null ? IslandSettings.NormalSide.RIGHT : side) {
			case LEFT -> -1;
			case RIGHT -> 1;
			case ALTERNATE -> (alternateIndex & 1) == 0 ? start : -start;
			case RANDOM -> randomRight ? 1 : -1;
		};
	}

	static double signedNormalOffset(double requestedOffset, int sideSign) {
		double magnitude = Double.isFinite(requestedOffset) ? Math.abs(requestedOffset) : IslandSettings.DEFAULT_NORMAL_OFFSET;
		return (sideSign < 0 ? -1.0 : 1.0) * Math.clamp(magnitude, IslandSettings.MIN_NORMAL_ABS_OFFSET, IslandSettings.MAX_NORMAL_OFFSET);
	}

	static boolean normalInFront(Vec3 eye, Vec3 cameraForward, Vec3 anchor) {
		return eye != null && cameraForward != null && anchor != null
				&& anchor.subtract(eye).dot(cameraForward) > 1.0E-8;
	}

	static boolean normalDirectionVisible(boolean hideBehind, Vec3 eye, Vec3 cameraForward, Vec3 anchor) {
		return !hideBehind || normalInFront(eye, cameraForward, anchor);
	}

	private static Placed spawnNormalNode(Minecraft mc, String line) {
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		Vec3 eye = mc.player.getEyePosition(partial);
		double distance = Math.clamp(IslandSettings.normalDistance, IslandSettings.MIN_NORMAL_DISTANCE, IslandSettings.MAX_NORMAL_DISTANCE);
		int alternate = IslandSettings.normalSide == IslandSettings.NormalSide.ALTERNATE ? normalAlternateSeq++ : 0;
		int sign = chooseNormalSide(IslandSettings.normalSide, IslandSettings.normalOffset, alternate, RANDOM.nextBoolean());
		double offset = signedNormalOffset(IslandSettings.normalOffset, sign);
		Vec3 view = new Vec3(mc.gameRenderer.mainCamera().forwardVector()).normalize();
		boolean following = normalFollowingEnabled();
		Vec3 nominal = following
				? computeNormalAnchor(eye, view, cameraYaw, distance, offset, 0.45)
				: computeFixedNormalAnchor(mc.player.getPosition(partial), view, cameraYaw, distance, offset);
		Vec3 anchor = following ? avoidSolidNormalAnchor(mc, nominal) : nominal;
		Vec3 right = normalHorizontalRight(view, cameraYaw);
		Vec3 up = following ? right.cross(view).normalize() : new Vec3(0.0, 1.0, 0.0);
		Placed node = new Placed(line, anchor, right, up, SCALE, null,
				Fonts.lyric(10.0F).width(line));
		node.normalManaged = true;
		node.normalSideSign = sign;
		node.normalForwardDistance = distance;
		node.normalSignedOffset = offset;
		node.normalHeight = 0.45;
		return node;
	}

	/** Collision top at one X/Z column, honoring partial shapes such as slabs. */
	static double fixedNormalCollisionTop(BlockPos pos, double x, double y, double z, List<AABB> shapes) {
		double lx = x - pos.getX(), ly = y - pos.getY(), lz = z - pos.getZ();
		double top = y;
		for (AABB box : shapes) {
			if (lx >= box.minX - 1.0E-8 && lx <= box.maxX + 1.0E-8
					&& lz >= box.minZ - 1.0E-8 && lz <= box.maxZ + 1.0E-8
					&& ly >= box.minY - 1.0E-8 && ly < box.maxY - 1.0E-8) {
				top = Math.max(top, pos.getY() + box.maxY);
			}
		}
		return top;
	}

	// A wall at the requested spawn column is not a floor. In particular, never
	// climb the column all the way to the top of a building/tree to place lyrics.
	static final double FIXED_NORMAL_MAX_STEP_UP = 1.0;
	static final double FIXED_NORMAL_SEARCH_RADIUS = 3.0;

	@FunctionalInterface
	interface FixedNormalCollisionLookup {
		/** Collision boxes in block-local coordinates, or null for an unloaded chunk. */
		List<AABB> boxes(BlockPos pos);
	}

	/**
	 * Probe only a one-block step above the player's feet. NaN means this column
	 * is too obstructed; positive infinity means it is unloaded. Never load chunks
	 * or search below the feet. The epsilon places the result above partial shapes.
	 */
	static double fixedNormalFloor(double x, double z, double feetY, FixedNormalCollisionLookup lookup) {
		double y = feetY;
		for (int i = 0; i < 8; i++) {
			BlockPos pos = BlockPos.containing(x, y, z);
			List<AABB> shapes = lookup.boxes(pos);
			if (shapes == null) return Double.POSITIVE_INFINITY;
			if (shapes.isEmpty()) return y;
			double top = fixedNormalCollisionTop(pos, x, y, z, shapes);
			if (top <= y + 1.0E-8) return y;
			if (top > feetY + FIXED_NORMAL_MAX_STEP_UP + 1.0E-8) return Double.NaN;
			y = top + 1.0E-5;
		}
		return Double.NaN; // Repeated/overlapping shapes: don't climb a wall.
	}

	/**
	 * Pick a site for a NEW fixed group only. Keep the configured X/Z whenever
	 * it is clear, otherwise try nearby loaded columns at the same elevation.
	 * Keep the original side and a positive forward distance. A reused/locked
	 * position is never moved horizontally. If no candidate is open, retain the
	 * requested X/Z and feet-level floor; walls may then obscure the lyric.
	 */
	static Vec3 fixedNormalSpawnSite(Vec3 nominal, Vec3 feet, Vec3 savedRight, int sideSign,
			boolean reusedPosition, FixedNormalCollisionLookup lookup) {
		double floor = fixedNormalFloor(nominal.x, nominal.z, feet.y, lookup);
		if (Double.isFinite(floor)) return new Vec3(nominal.x, floor, nominal.z);
		if (reusedPosition || floor == Double.POSITIVE_INFINITY) {
			return new Vec3(nominal.x, feet.y, nominal.z);
		}
		Vec3 right = new Vec3(savedRight.x, 0.0, savedRight.z);
		if (right.lengthSqr() < 1.0E-8) return new Vec3(nominal.x, feet.y, nominal.z);
		right = right.normalize();
		Vec3 forward = new Vec3(right.z, 0.0, -right.x);
		for (double radius = 0.5; radius <= FIXED_NORMAL_SEARCH_RADIUS + 1.0E-8; radius += 0.5) {
			for (int i = 0; i < 16; i++) {
				double angle = Math.PI * 2.0 * i / 16.0;
				double x = nominal.x + Math.cos(angle) * radius;
				double z = nominal.z + Math.sin(angle) * radius;
				Vec3 relative = new Vec3(x - feet.x, 0.0, z - feet.z);
				if ((sideSign < 0 ? -1 : 1) * relative.dot(right) < IslandSettings.MIN_NORMAL_ABS_OFFSET - 1.0E-8
						|| relative.dot(forward) < IslandSettings.MIN_NORMAL_DISTANCE - 1.0E-8) continue;
				double candidateFloor = fixedNormalFloor(x, z, feet.y, lookup);
				if (Double.isFinite(candidateFloor)) return new Vec3(x, candidateFloor, z);
			}
		}
		return new Vec3(nominal.x, feet.y, nominal.z);
	}

	/** Extra room for the centered multiline group, backdrop, shadow and wave. */
	record FixedGroundBounds(double halfHeight, double halfWidth, double curveDepth, double backDepth) {}

	static FixedGroundBounds fixedNormalGroundBounds(Font font, Placed placed, NormalRows layout) {
		// 1.16 covers the largest current entrance-scale overshoot plus beat pulse.
		double scale = placed.scale * Math.clamp(IslandSettings.hologramScale, 0.50F, 2.00F)
				* Math.clamp(IslandSettings.lyricTextScale, 0.50F, 2.00F)
				* Math.clamp(IslandSettings.normalTextSize, IslandSettings.MIN_NORMAL_SIZE, IslandSettings.MAX_NORMAL_SIZE)
				* 1.16;
		double halfH = (layout.halfHeightLocal() + 2.4 + font.getSize() * 0.14 + 0.58) * scale;
		double halfW = (layout.halfWidthLocal() + 3.4 + 0.58) * scale;
		double radius = normalCurveRadius(IslandSettings.normalCurveEnabled, IslandSettings.normalCurve, halfW);
		double curveDepth = 0.0;
		double tangentDepth = 0.0;
		if (Double.isFinite(radius)) {
			double angle = Math.min(1.05, halfW / radius);
			// Each letter is a tangent quad; its tangent is at most its own half
			// width, not twice the width of the WHOLE sentence. The old bound could
			// overestimate curvature by many blocks and push a fixed lyric overhead.
			double maxGlyphTangent = 0.0;
			for (NormalRow row : layout.rows()) for (float[] quad : row.glyphs) {
				double center = (quad[0] + quad[10]) * 0.5;
				for (int v = 0; v < 4; v++) {
					maxGlyphTangent = Math.max(maxGlyphTangent,
							Math.abs(quad[v * 5] - center) * row.scaleFactor * scale);
				}
			}
			tangentDepth = (maxGlyphTangent + 0.58 * scale) * Math.sin(angle); // includes the shadow shift
			curveDepth = radius * (1.0 - Math.cos(angle)) + tangentDepth;
		}
		double back = (Double.isFinite(radius) ? 0.08 : 0.012) * Math.max(0.025, scale * 24.0);
		back += 0.004 * Math.max(0.025, scale * 24.0) + tangentDepth; // back/shadow, incl. tangent crossing zero
		return new FixedGroundBounds(halfH, halfW, curveDepth, back);
	}

	static double fixedNormalBottomBound(double centerY, FixedGroundBounds bounds, Vec3 right, Vec3 up, Vec3 facing) {
		return centerY - Math.abs(right.y) * bounds.halfWidth()
				- Math.abs(up.y) * bounds.halfHeight()
				- Math.max(0.0, -facing.y) * bounds.curveDepth()
				- Math.max(0.0, facing.y) * bounds.backDepth();
	}

	/**
	 * Find a safe center for the initial camera orientation without changing the
	 * saved anchor. Existing groups are never re-grounded on a later frame.
	 */
	static double fixedNormalGroundCenter(Placed placed, double floor, double minimumCenter,
			FixedGroundBounds bounds, Quaternionf cameraRotation, Vec3 cameraPosition,
			IslandSettings.HologramBillboard mode) {
		final double margin = 0.025;
		Placed probe = new Placed(placed.text, placed.translation, placed.anchor, placed.right,
				placed.up, placed.scale, placed.face, placed.width);
		probe.normalManaged = placed.normalManaged;
		probe.normalRenderRight = placed.normalRenderRight;
		double low = Math.max(minimumCenter, floor + bounds.halfHeight() + margin);
		if (fixedNormalGroundBottomAt(probe, low, bounds, cameraRotation, cameraPosition, mode)
				>= floor + margin - 1.0E-8) return low;
		// The sum is a guaranteed safe upper bracket, not the spawn height. The
		// old 12-iteration fallback returned this sum directly and could put a
		// wide curved lyric many blocks overhead after slow convergence.
		double high = Math.max(low, floor + margin + bounds.halfHeight() + bounds.halfWidth()
				+ Math.max(bounds.curveDepth(), bounds.backDepth()) + 1.0E-7);
		for (int i = 0; i < 52; i++) {
			double mid = low + (high - low) * 0.5;
			if (fixedNormalGroundBottomAt(probe, mid, bounds, cameraRotation, cameraPosition, mode)
					>= floor + margin) high = mid;
			else low = mid;
		}
		return high;
	}

	private static double fixedNormalGroundBottomAt(Placed probe, double y, FixedGroundBounds bounds,
			Quaternionf cameraRotation, Vec3 cameraPosition, IslandSettings.HologramBillboard mode) {
		probe.anchor = new Vec3(probe.anchor.x, y, probe.anchor.z);
		Vec3[] basis = computeHologramBasis(probe, cameraRotation, cameraPosition, mode);
		float flip = textFlip(basis[0], basis[1], cameraPosition.subtract(probe.anchor));
		Vec3 facing = basis[0].cross(basis[1]).normalize().scale(flip);
		return fixedNormalBottomBound(y, bounds, basis[0], basis[1], facing);
	}

	private static void groundNewFixedNormalGroup(Minecraft mc, Placed placed, Font font, boolean reusedPosition) {
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		Vec3 feet = mc.player.getPosition(partial);
		FixedNormalCollisionLookup lookup = pos -> {
			if (mc.level == null || !mc.level.hasChunkAt(pos)) return null;
			return mc.level.getBlockState(pos).getCollisionShape(mc.level, pos).toAabbs();
		};
		Vec3 site = fixedNormalSpawnSite(placed.anchor, feet, placed.right,
				placed.normalSideSign, reusedPosition, lookup);
		// Only a newly born group can be adjusted. Locked X/Z stays unchanged.
		placed.anchor = new Vec3(site.x, placed.anchor.y, site.z);
		double floor = site.y;
		NormalRows rows = buildNormalRows(font, placed);
		FixedGroundBounds bounds = fixedNormalGroundBounds(font, placed, rows);
		Camera cam = mc.gameRenderer.mainCamera();
		double minimumCenter = reusedPosition ? placed.anchor.y : Double.NEGATIVE_INFINITY;
		double y = fixedNormalGroundCenter(placed, floor, minimumCenter, bounds,
				cam.isInitialized() ? cam.rotation() : null, cameraPos, IslandSettings.hologramBillboard);
		placed.anchor = new Vec3(placed.anchor.x, y, placed.anchor.z);
	}

	/** Best effort: retain exact horizontal coordinates, searching upward only in loaded chunks. */
	private static Vec3 avoidSolidNormalAnchor(Minecraft mc, Vec3 nominal) {
		if (mc.level == null) return nominal;
		BlockPos block = BlockPos.containing(nominal);
		if (!mc.level.hasChunkAt(block)) return nominal;
		for (int dy = 0; dy <= 6; dy++) {
			Vec3 candidate = nominal.add(0.0, dy, 0.0);
			BlockPos pos = BlockPos.containing(candidate);
			if (mc.level.getBlockState(pos).getCollisionShape(mc.level, pos).isEmpty()) return candidate;
		}
		return nominal; // No free anchor found; never silently alter the requested horizontal placement.
	}

	private static Placed betterPlace(Minecraft mc, String line) {
		Camera camera = mc.gameRenderer.mainCamera();

		Vec3 forward = new Vec3(camera.forwardVector());

		float yaw = (float) Math.toRadians(cameraYaw);

		Vec3 right = new Vec3(
				-Math.cos(yaw),
				0.0,
				-Math.sin(yaw)
		);

		Vec3 up = right.cross(forward).normalize();

		float width = Math.max(
				1.0F,
				Fonts.lyric(10.0F).width(line)
		);

		boolean preferWall = recent.isEmpty()
				|| RANDOM.nextDouble() < 0.8;

		Placed best = null;
		double bestScore = -Double.MAX_VALUE;

		for (int i = 0; i < 46; i++) {
			double x = i == 0 ? 0.0 : ((i - 1) % 9 - 4) * 0.18;
			double y = i == 0 ? 0.0 : ((i - 1) / 9 - 1) * 0.2;

			Vec3 ray = forward
					.add(right.scale(x))
					.add(up.scale(y))
					.normalize();

			BlockHitResult hit = trace(
					mc,
					cameraPos,
					cameraPos.add(ray.scale(12.0))
			);

			boolean surface = hit.getType() == HitResult.Type.BLOCK
					&& !hit.isInside();

			double distance = cameraPos.distanceTo(hit.getLocation());

			for (int kind = 0; kind < 2; kind++) {
				Direction face = kind == 0 && surface
						? hit.getDirection()
						: null;

				if (kind == 0 && !wallFace(face)) {
					continue;
				}

				Vec3 anchor;
				Vec3 along = right;
				Vec3 top = up;

				if (face != null) {
					Vec3 normal = face.getUnitVec3();

					if (normal.dot(ray.scale(-1.0)) < 0.24) {
						continue;
					}

					Vec3 center = Vec3.atCenterOf(hit.getBlockPos());

					anchor = new Vec3(
							normal.x == 0.0
									? center.x
									: hit.getLocation().x,
							center.y,
							normal.z == 0.0
									? center.z
									: hit.getLocation().z
					).add(normal.scale(0.08));

					top = new Vec3(0.0, 1.0, 0.0);
					along = top.cross(normal);
				} else {
					double air = surface
							? Math.min(8.0, distance - 0.5)
							: 4.5 + RANDOM.nextDouble() * 4.0;

					if (air < 1.5) {
						continue;
					}

					anchor = cameraPos.add(ray.scale(air));
				}

				double range = cameraPos.distanceTo(anchor);

				if (range < 1.5) {
					continue;
				}

				float scale = SCALE * (float) Math.clamp(
						range / DISTANCE,
						0.8,
						1.8
				);

				for (int fit = 0; fit < 4; fit++) {
					Placed placed = new Placed(
							line,
							anchor,
							along,
							top,
							scale,
							face,
							width
					);

					double score = score(placed, preferWall, forward)
							- fit * 0.7;

					if (score > bestScore
							&& fits(mc, placed, width, forward, right, up)) {
						best = placed;
						bestScore = score;
						break;
					}

					scale *= 0.75F;
				}
			}
		}

		return best;
	}

	static boolean wallFace(Direction face) {
		return face != null && face.getAxis() != Direction.Axis.Y;
	}

	static float textFlip(Vec3 right, Vec3 up, Vec3 toCamera) {
		return toCamera.dot(right.cross(up)) < 0.0 ? -1.0F : 1.0F;
	}

	private static double score(
			Placed placed,
			boolean preferWall,
			Vec3 forward
	) {
		boolean wall = placed.face != null;

		Vec3 delta = placed.anchor.subtract(cameraPos);

		double score = RANDOM.nextDouble() * 0.7
				+ (wall == preferWall ? 3.0 : 0.0)
				+ (wall ? 1.0 : 0.0)
				+ forward.dot(delta.normalize()) * 2.0
				- Math.abs(delta.length() - 6.5) * 0.12;

		for (int i = 0; i < recent.size(); i++) {
			Placed last = recent.get(i);

			score -= 5.0
					/ (1.0 + last.anchor.distanceToSqr(placed.anchor));
		}

		return score;
	}

	private static boolean fits(
			Minecraft mc,
			Placed placed,
			float width,
			Vec3 forward,
			Vec3 right,
			Vec3 up
	) {
		double halfWidth = width * placed.scale / 2.0 + 0.15;
		double halfHeight = 6.5 * placed.scale + 0.1;

		double tanY = Math.tan(
				Math.toRadians(mc.options.fov().get() * 0.5)
		) * 0.85;

		double tanX = tanY * mc.getWindow().getWidth()
				/ Math.max(1, mc.getWindow().getHeight());

		Vec3 normal = placed.right.cross(placed.up);

		int steps = Math.max(
				2,
				(int) Math.ceil(halfWidth * 4.0)
		);

		for (int x = 0; x <= steps; x++) {
			for (int y = -1; y <= 1; y++) {
				Vec3 point = placed.anchor
						.add(placed.right.scale(
								halfWidth * (2.0 * x / steps - 1.0)
						))
						.add(placed.up.scale(halfHeight * y));

				BlockPos block = BlockPos.containing(point);

				if (mc.level.isOutsideBuildHeight(block)
						|| !mc.level.hasChunkAt(block)
						|| !mc.level.getFluidState(block).isEmpty()) {
					return false;
				}

				Vec3 delta = point.subtract(cameraPos);

				double depth = delta.dot(forward);

				if (depth < 1.0
						|| Math.abs(delta.dot(right)) > depth * tanX
						|| Math.abs(delta.dot(up)) > depth * tanY) {
					return false;
				}

				if (trace(mc, cameraPos, point).getType()
						!= HitResult.Type.MISS) {
					return false;
				}

				if (placed.face != null) {
					BlockHitResult support = trace(
							mc,
							point,
							point.subtract(normal.scale(0.16))
					);

					if (support.getType() != HitResult.Type.BLOCK
							|| support.getDirection() != placed.face
							|| Math.abs(
							point.distanceTo(support.getLocation()) - 0.08
					) > 0.025) {
						return false;
					}
				}
			}
		}

		return true;
	}

	private static BlockHitResult trace(
			Minecraft mc,
			Vec3 from,
			Vec3 to
	) {
		return mc.level.clip(new ClipContext(
				from,
				to,
				ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE,
				mc.player
		));
	}

	static boolean normalFollowingEnabled() {
		return IslandSettings.follow && !IslandSettings.locked && !pressed;
	}

	static boolean normalLastLineFinished(int index, int count, float rawPosition, long duration) {
		return count > 0 && index == count - 1 && duration > 0L
				&& Float.isFinite(rawPosition) && rawPosition >= duration;
	}

	private static void follow(Minecraft mc, Placed placed, Vec3 eye, Vec3 view, float dt) {
		if (placed == null || !placed.normalManaged) return;
		Vec3 target = avoidSolidNormalAnchor(mc, computeNormalAnchor(eye, view, cameraYaw,
				placed.normalForwardDistance, placed.normalSignedOffset, placed.normalHeight));
		advanceNormalFollow(placed, true, eye, target, dt);
	}

	/**
	 * Translate by the interpolated eye's actual motion, then ease only the
	 * relative offset toward the current view. The anchor never depends on
	 * whether it happens to be visible in this frame. No rotation is written here.
	 */
	static void advanceNormalFollow(Placed placed, boolean following, Vec3 eye, Vec3 target, float dt) {
		if (placed == null || !placed.normalManaged) return;
		if (!following) {
			placed.normalFollowEye = null;
			return;
		}
		if (eye == null || target == null) return;
		Vec3 oldEye = placed.normalFollowEye == null ? eye : placed.normalFollowEye;
		Vec3 relative = placed.anchor.subtract(oldEye);
		Vec3 desired = target.subtract(eye);
		float safeDt = Float.isFinite(dt) ? Math.clamp(dt, 0.0F, 0.20F) : 0.0F;
		float amount = (float) (1.0 - Math.exp(-8.0 * safeDt));
		Vec3 eased = relative.distanceToSqr(desired) < 1.0E-12
				? relative : normalFollowOffset(relative, desired, amount);
		placed.anchor = eye.add(eased);
		placed.normalFollowEye = eye;
	}

	/** Smooth yaw around the player, rather than sending 180-degree turns overhead. */
	static Vec3 normalFollowOffset(Vec3 from, Vec3 to, float amount) {
		double t = Float.isFinite(amount) ? Math.clamp(amount, 0.0F, 1.0F) : 0.0;
		if (t == 0.0) return from;
		double fromH = Math.hypot(from.x, from.z), toH = Math.hypot(to.x, to.z);
		if (fromH < 1.0E-8 || toH < 1.0E-8) {
			Vec3 delta = to.subtract(from).scale(t);
			if (delta.lengthSqr() > 16.0) delta = delta.normalize().scale(4.0);
			return from.add(delta);
		}
		double angleFrom = Math.atan2(from.x, from.z);
		double angleTo = Math.atan2(to.x, to.z);
		double shortest = Math.atan2(Math.sin(angleTo - angleFrom), Math.cos(angleTo - angleFrom));
		// A long frame or a fresh mode toggle must not create a single-frame snap.
		double angle = angleFrom + Math.clamp(shortest * t, -Math.PI / 4.0, Math.PI / 4.0);
		double horizontal = fromH + Math.clamp((toH - fromH) * t, -4.0, 4.0);
		double vertical = from.y + Math.clamp((to.y - from.y) * t, -2.0, 2.0);
		return new Vec3(Math.sin(angle) * horizontal, vertical, Math.cos(angle) * horizontal);
	}

	static Vec3 followTarget(
			Placed placed,
			Vec3 eye,
			Vec3 forward,
			Vec3 right,
			Vec3 up,
			double tanX,
			double tanY
	) {
		Vec3 delta = placed.anchor.subtract(eye);

		double width = placed.width * placed.scale * 0.5 + 0.15;
		double height = 10.0 * placed.scale + 0.1;

		double x = delta.dot(right);
		double y = delta.dot(up);
		double z = delta.dot(forward);

		double distance = Math.max(
				Math.clamp(delta.length(), 6.0, 10.0),
				Math.max(
						width / (tanX * 0.5),
						height / (tanY * 0.5)
				)
		);

		double maxX = Math.max(
				0.0,
				tanX * 0.6 - width / distance
		);

		double maxY = Math.max(
				0.0,
				tanY * 0.6 - height / distance
		);

		x = z > 0.1
				? Math.clamp(x / z, -maxX, maxX)
				: 0.0;

		y = z > 0.1
				? Math.clamp(y / z, -maxY, maxY)
				: maxY * 0.5;

		Vec3 direction = forward
				.add(right.scale(x))
				.add(up.scale(y))
				.normalize();

		return direction.scale(distance);
	}

	private static void turn(
			Placed placed,
			Quaternionf target,
			float amount
	) {
		Quaternionf rotation = new Quaternionf().setFromNormalized(
				new Matrix3f()
						.setColumn(0, placed.right.toVector3f())
						.setColumn(1, placed.up.toVector3f())
						.setColumn(
								2,
								placed.right.cross(placed.up).toVector3f()
						)
		);

		rotation.slerp(target, amount);

		placed.right = new Vec3(
				new Vector3f(1.0F, 0.0F, 0.0F).rotate(rotation)
		);

		placed.up = new Vec3(
				new Vector3f(0.0F, 1.0F, 0.0F).rotate(rotation)
		);
	}

	static Vec3 followPoint(
			Vec3 from,
			Vec3 to,
			float amount
	) {
		if (from.lengthSqr() < 0.000001
				|| to.lengthSqr() < 0.000001) {
			return from.lerp(to, amount);
		}

		Vector3f start = from.normalize().toVector3f();

		Quaternionf turn = new Quaternionf().rotationTo(
				start,
				to.normalize().toVector3f()
		);

		new Quaternionf().slerp(turn, amount).transform(start);

		return new Vec3(start).normalize().scale(
				from.length()
						+ (to.length() - from.length()) * amount
		);
	}

	public static void setLocked(boolean locked) {
		IslandSettings.locked = locked;

		pinned = locked ? current : null;

		if (locked) {
			IslandSettings.follow = false;
		}
	}

	private static boolean canGrab(Minecraft mc) {
		return IslandSettings.worldText
				&& mc.player != null
				&& mc.level != null
				&& !mc.gui.hud.isHidden()
				&& mc.gui.screen() == null
				&& mc.isWindowActive()
				&& mc.mouseHandler.isMouseGrabbed();
	}

	/**
	 * Legacy entry points retained for compatibility. World and Sky lyrics are
	 * strictly visual: no click, release or wheel input is consumed or used to
	 * drag a lyric. Display placement remains configurable in Settings.
	 */
	public static boolean handleButton(int button, int action) {
		return false;
	}

	public static boolean handleScroll(double amount) {
		return false;
	}

	private static void release() {
		if (dragging && current != null) {
			current.placedAt = Math.max(
					current.placedAt,
					System.currentTimeMillis() - FADE
			);

			if (IslandSettings.locked) {
				pinned = current;
			}
		}

		pressed = false;
		dragging = false;
		pressedAt = 0L;
	}

	private static double pick(Minecraft mc) {
		if (current == null || mc.level != level) {
			return -1.0;
		}

		Camera camera = mc.gameRenderer.mainCamera();

		Vec3 eye = camera.position();
		Vec3 ray = new Vec3(camera.forwardVector());

		boolean normal = !IslandSettings.skyLyrics && current.normalManaged && current.normalRenderRight != null;
		Vec3 pickRight = normal ? current.normalRenderRight : current.right;
		Vec3 pickUp = normal ? current.normalRenderUp : current.up;
		boolean sky = IslandSettings.skyLyrics && current.skyManaged;
		Vec3 center = (normal || sky) ? current.anchor : current.anchor.add(
				current.up.scale(IslandSettings.btp ? 0.0 : current.scale * 3.5));

		double maxPickDist = IslandSettings.skyLyrics
				? clampSkyBoundaryRadius(IslandSettings.skyBoundaryRadius) + 32.0
				: 64.0;

		double distance = hitDistanceWithLimit(
				eye,
				ray,
				center,
				pickRight,
				pickUp,
				normal ? current.normalHalfWidth + 0.1 : (sky && current.skyHalfWidth > 0
						? current.skyHalfWidth + 0.1 : current.width * current.scale * 0.5 + 0.1),
				normal ? (current.normalTop - current.normalBottom) * 0.5 + 0.08 : (sky && current.skyHalfHeight > 0
						? current.skyHalfHeight + 0.08 : current.scale * 6.5 + 0.08),
				maxPickDist
		);

		if (distance >= 0.0
				&& !(sky ? IslandSettings.skyThroughBlocks : IslandSettings.throughBlocks)
				&& trace(
				mc,
				eye,
				eye.add(ray.scale(distance))
		).getType() != HitResult.Type.MISS) {
			return -1.0;
		}

		return distance;
	}

	static double hitDistance(
			Vec3 eye,
			Vec3 ray,
			Vec3 anchor,
			Vec3 right,
			Vec3 up,
			double width,
			double height
	) {
		return hitDistanceWithLimit(eye, ray, anchor, right, up, width, height, 64.0);
	}

	private static double hitDistanceWithLimit(
			Vec3 eye,
			Vec3 ray,
			Vec3 anchor,
			Vec3 right,
			Vec3 up,
			double width,
			double height,
			double maxDistance
	) {
		Vec3 normal = right.cross(up);

		double angle = ray.dot(normal);

		if (Math.abs(angle) < 0.000001) {
			return -1.0;
		}

		double distance = anchor.subtract(eye).dot(normal) / angle;

		if (distance < 0.1 || distance > maxDistance) {
			return -1.0;
		}

		Vec3 point = eye.add(ray.scale(distance)).subtract(anchor);

		return Math.abs(point.dot(right)) <= width
				&& Math.abs(point.dot(up)) <= height
				? distance
				: -1.0;
	}

	private static void updateDrag(Minecraft mc, float dt) {
		if (!pressed) {
			return;
		}

		if (current == null
				|| !canGrab(mc)
				|| (SDLMouse.SDL_GetMouseState(null, null) & SDLMouse.SDL_BUTTON_RMASK) == 0) {
			release();
			return;
		}

		Camera camera = mc.gameRenderer.mainCamera();

		Vec3 ray = new Vec3(camera.forwardVector());

		if (!dragging) {
			double distance = pick(mc);

			long now = System.currentTimeMillis();

			if (distance < 0.0) {
				pressedAt = 0L;
				return;
			}

			if (pressedAt == 0L) {
				pressedAt = now;
			}

			if (now - pressedAt < GRAB) {
				return;
			}

			dragging = true;
			dragDistance = distance;

			Vec3 grip = cameraPos
					.add(ray.scale(distance))
					.subtract(current.anchor);

			gripX = grip.dot(current.right);
			gripY = grip.dot(current.up);
		}

		float amount = (float) (1.0 - Math.exp(-9.0 * dt));

		turn(current, camera.rotation(), amount);

		double distance = dragDistance;

		if (!(IslandSettings.skyLyrics ? IslandSettings.skyThroughBlocks : IslandSettings.throughBlocks)) {
			BlockHitResult hit = trace(
					mc,
					cameraPos,
					cameraPos.add(ray.scale(distance))
			);

			if (hit.getType() == HitResult.Type.BLOCK) {
				distance = Math.max(
						0.3,
						cameraPos.distanceTo(hit.getLocation()) - 0.15
				);
			}
		}

		Vec3 target = ray.scale(distance)
				.subtract(current.right.scale(gripX))
				.subtract(current.up.scale(gripY));

		current.anchor = cameraPos.add(
				followPoint(
						current.anchor.subtract(cameraPos),
						target,
						amount
				)
		);

		current.face = null;

		if (IslandSettings.locked) {
			pinned = current;
		}
	}

	// =========================================================================
	// Normal 3D World Lyrics — Minecraft TextDisplay Hologram Rendering
	// =========================================================================

	/**
	 * Draw-time orientation only: no branch changes the world anchor. CENTER
	 * aligns with the camera's view; VERTICAL is upright; Natural 3D faces the
	 * actual camera position with the world's upward direction kept positive.
	 * Legacy non-Normal/wall placements retain their former basis.
	 */
	static Vec3[] computeHologramBasis(
			Placed placed,
			Quaternionf cameraRotation,
			Vec3 cameraPosition,
			IslandSettings.HologramBillboard mode
	) {
		if (placed.face != null || (!placed.normalManaged && mode == IslandSettings.HologramBillboard.SMART_BTP)) {
			return new Vec3[] { placed.right, placed.up };
		}
		if (mode == IslandSettings.HologramBillboard.CENTER && cameraRotation != null) {
			Vector3f r = cameraRotation.transform(new Vector3f(-1.0F, 0.0F, 0.0F));
			Vector3f u = cameraRotation.transform(new Vector3f(0.0F, 1.0F, 0.0F));
			return new Vec3[] { new Vec3(r.x, r.y, r.z).normalize(), new Vec3(u.x, u.y, u.z).normalize() };
		}
		Vec3 toCam = cameraPosition.subtract(placed.anchor);
		double hLen = Math.hypot(toCam.x, toCam.z);
		Vec3 worldUp = new Vec3(0.0, 1.0, 0.0);
		Vec3 right;
		if (hLen < 1.0E-5) {
			// Directly above/below has no unique horizontal facing. Reuse the
			// most recent horizontal axis instead of choosing a random flip.
			Vec3 prior = placed.normalRenderRight != null ? placed.normalRenderRight : placed.right;
			right = new Vec3(prior.x, 0.0, prior.z);
			if (right.lengthSqr() < 1.0E-8) right = new Vec3(1.0, 0.0, 0.0);
			right = right.normalize();
		} else {
			Vec3 horizontalToCam = new Vec3(toCam.x / hLen, 0.0, toCam.z / hLen);
			right = worldUp.cross(horizontalToCam).normalize();
		}
		if (placed.normalManaged && mode == IslandSettings.HologramBillboard.SMART_BTP
				&& toCam.lengthSqr() > 1.0E-10) {
			Vec3 facing = toCam.normalize();
			Vec3 up = facing.cross(right).normalize();
			return new Vec3[] { right, up };
		}
		return new Vec3[] { right, worldUp };
	}

	private static int[] scratchCharCols = new int[256];
	private static int[] scratchGlowCols = new int[256];

	private static int[] ensureScratchCols(int[] buf, int len, boolean glow) {
		if (buf.length < len) {
			buf = new int[Math.max(len, buf.length * 2)];
			if (glow) scratchGlowCols = buf;
			else scratchCharCols = buf;
		}
		return buf;
	}

	static final class NormalRow {
		final String text;
		final List<float[]> glyphs;
		final List<Integer> originalIndices;
		final float scaleFactor;
		final boolean translation;
		final int glyphStart;
		float baselineDown;
		NormalRow(String text, List<float[]> glyphs, List<Integer> indices, float scaleFactor,
				boolean translation, int glyphStart, float baselineDown) {
			this.text = text; this.glyphs = glyphs; this.originalIndices = indices;
			this.scaleFactor = scaleFactor; this.translation = translation;
			this.glyphStart = glyphStart; this.baselineDown = baselineDown;
		}
	}

	record NormalRows(List<NormalRow> rows, float halfWidthLocal, float halfHeightLocal, int mainGlyphCount) {}

	/** Cache only source spans, not atlas UVs: the dynamic Unicode atlas can be repacked at any frame. */
	static NormalRows buildNormalRows(Font font, Placed placed) {
		int limit = Math.clamp(IslandSettings.normalWordsPerLine, IslandSettings.MIN_NORMAL_WORDS, IslandSettings.MAX_NORMAL_WORDS);
		boolean withTranslation = IslandSettings.showTranslations && !placed.translation.isBlank();
		if (placed.wrappedWordLimit != limit || placed.wrappedTranslations != withTranslation) {
			placed.mainSpans = NormalLyricsLayout.wrap(placed.text, limit);
			placed.translationSpans = withTranslation ? NormalLyricsLayout.wrap(placed.translation, limit) : List.of();
			placed.wrappedWordLimit = limit;
			placed.wrappedTranslations = withTranslation;
		}
		NormalRows candidate = null;
		for (int attempt = 0; attempt < 3; attempt++) {
			long revision = DynamicSdfFont.atlasRevision();
			candidate = buildNormalRowsOnce(font, placed);
			if (revision == DynamicSdfFont.atlasRevision()) return candidate;
		}
		return candidate;
	}

	private static NormalRows buildNormalRowsOnce(Font font, Placed placed) {
		List<NormalRow> result = new ArrayList<>();
		float cursor = 0.0F, halfWidth = 0.0F;
		float gap = NormalLyricsLayout.rowGap(font.getSize(), IslandSettings.normalLineSpacing);
		int mainGlyphs = 0;
		for (int group = 0; group < 2; group++) {
			boolean translated = group == 1;
			float rowScale = translated ? 0.62F : 1.0F;
			List<NormalLyricsLayout.Span> spans = translated ? placed.translationSpans : placed.mainSpans;
			for (NormalLyricsLayout.Span span : spans) {
				String text = span.text();
				float width = font.width(text);
				List<Integer> indices = translated ? null : new ArrayList<>();
				List<float[]> glyphs = font.buildGlyphs(text, -width * 0.5F, 0.0F, indices);
				if (indices != null) {
					for (int i = 0; i < indices.size(); i++) indices.set(i, span.originalIndex(indices.get(i)));
				}
				float minY = Float.POSITIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
				halfWidth = Math.max(halfWidth, width * rowScale * 0.5F);
				for (float[] quad : glyphs) for (int v = 0; v < 4; v++) {
					float gx = quad[v * 5] * rowScale, gy = quad[v * 5 + 1];
					halfWidth = Math.max(halfWidth, Math.abs(gx));
					minY = Math.min(minY, gy); maxY = Math.max(maxY, gy);
				}
				if (!Float.isFinite(minY) || !Float.isFinite(maxY)) { minY = 0.0F; maxY = font.getSize() * 0.8F; }
				float rowHeight = Math.max(font.getSize() * 0.80F * rowScale, (maxY - minY) * rowScale);
				float baselineDown = cursor - minY * rowScale;
				result.add(new NormalRow(text, glyphs, indices, rowScale, translated, mainGlyphs, baselineDown));
				if (!translated) mainGlyphs += glyphs.size();
				cursor += rowHeight + gap;
			}
		}
		float totalHeight = Math.max(0.0F, cursor - (result.isEmpty() ? 0.0F : gap));
		// Stack as one centered group, so replacing a long verse with a short one leaves no old rows.
		for (NormalRow row : result) row.baselineDown -= totalHeight * 0.5F;
		return new NormalRows(result, halfWidth, totalHeight * 0.5F, mainGlyphs);
	}

	/** Radius of a real cylinder, restricted to keep every glyph facing the camera. */
	static double normalCurveRadius(boolean enabled, float amount, double halfWidthWorld) {
		if (!enabled || !Float.isFinite(amount) || amount <= 0.0001F) return Double.POSITIVE_INFINITY;
		double strength = Math.clamp(amount, IslandSettings.MIN_NORMAL_CURVE, IslandSettings.MAX_NORMAL_CURVE);
		return Math.max(2.5 / strength, Math.max(0.0, halfWidthWorld) / 1.05);
	}

	/** Rigid tangent for each glyph: x and depth are world units; y remains unchanged. */
	static double[] normalCurveOffsets(double x, double glyphCenter, double radius) {
		if (!Double.isFinite(radius) || radius <= 0.0) return new double[] { x, 0.0 };
		double angle = Math.clamp(glyphCenter / radius, -1.05, 1.05);
		double tangent = x - glyphCenter;
		return new double[] { radius * Math.sin(angle) + tangent * Math.cos(angle),
				radius * (1.0 - Math.cos(angle)) + tangent * Math.sin(angle) };
	}

	static Vec3 normalGroupOrigin(Placed placed, float lift, boolean following) {
		if (placed.face != null || (placed.normalManaged && !following)) return placed.anchor;
		return placed.anchor.add(0.0, lift, 0.0);
	}

	private static void drawHologram(
			Minecraft mc, Font font, Placed placed, MusicTracker tracker, List<LyricsFetcher.LyricLine> lines,
			int alpha, boolean karaoke, float lift, float animScale, float wavePhase,
			IslandSettings.LyricAnimation anim, float ageSec
	) {
		if (alpha <= 3 || placed.text.isEmpty()) return;
		Camera cam = mc.gameRenderer.mainCamera();
		Vec3 view = new Vec3(cam.forwardVector());
		if (!normalDirectionVisible(IslandSettings.normalHideBehind, cameraPos, view, placed.anchor)) return;
		NormalRows layout = buildNormalRows(font, placed);
		if (layout.mainGlyphCount() == 0) return;

		float beatPulse = (IslandSettings.hudBeatPulse && tracker != null && karaoke) ? tracker.beatReactor().pulse() : 0.0F;
		float effectiveScale = placed.scale * Math.clamp(IslandSettings.hologramScale, 0.50F, 2.00F)
				* Math.clamp(IslandSettings.lyricTextScale, 0.50F, 2.00F)
				* Math.clamp(IslandSettings.normalTextSize, IslandSettings.MIN_NORMAL_SIZE, IslandSettings.MAX_NORMAL_SIZE)
				* animScale * (1.0F + beatPulse * 0.035F);
        if (placed.normalManaged) effectiveScale *= WorldIndicator.distanceMultiplier(
            PlusSettings.distanceSize, normalFollowingEnabled(), placed.normalReferenceDistance,
            placed.anchor.distanceTo(cameraPos));
		Vec3[] basis = computeHologramBasis(placed, cam.isInitialized() ? cam.rotation() : null,
				cameraPos, IslandSettings.hologramBillboard);
		Vec3 right = basis[0], up = basis[1];
		float flip = textFlip(right, up, cameraPos.subtract(placed.anchor));
		Vec3 normalTowardCam = right.cross(up).normalize().scale(flip);
		Vec3 groupOrigin = normalGroupOrigin(placed, pressed ? 0.0F : lift, normalFollowingEnabled());
		float halfW = (layout.halfWidthLocal() + 3.4F) * effectiveScale;
		float halfH = (layout.halfHeightLocal() + 2.4F) * effectiveScale;
		placed.normalHalfWidth = halfW; placed.normalTop = halfH; placed.normalBottom = -halfH;
		placed.normalRenderRight = right; placed.normalRenderUp = up;
		double radius = normalCurveRadius(IslandSettings.normalCurveEnabled, IslandSettings.normalCurve, halfW);
		// Submit the whole group with no screen-center clipping.

		int primaryArgb = IslandSettings.resolveWorldPrimaryColor();
		int activeRgb = (pressed && placed == current) ? 0x9BD8FF : (primaryArgb & 0xFFFFFF);
		int highlightArgb = IslandSettings.resolveHighlightColor();
		int upcomingRgb = ColorUtil.upcomingForPrimary(activeRgb, IslandSettings.hudUpcomingColor) & 0xFFFFFF;
		int litColor = (Math.min(255, alpha) << 24) | activeRgb;
		int hiColor = (Math.min(255, alpha) << 24) | (highlightArgb & 0xFFFFFF);
		int grayColor = (Math.min(255, (int) (alpha * 0.48F)) << 24) | upcomingRgb;
		float glowStrength = Math.clamp(IslandSettings.effectiveWorldGlow()
				+ (IslandSettings.lyricGlowEnabled ? beatPulse * 0.16F : 0.0F), 0.0F, 1.0F);
		int glowRgb = IslandSettings.resolveGlowColor() & 0xFFFFFF;
		int litGlowColor = (Math.min(255, (int) (alpha * (0.58F + beatPulse * 0.15F) * glowStrength)) << 24) | glowRgb;
		int idleGlowColor = (Math.min(255, (int) (alpha * 0.22F * glowStrength)) << 24) | glowRgb;
		float outlineThickness = Math.clamp(IslandSettings.lyricOutlineThickness, 0.0F, 1.0F);
		int outlineRgb = IslandSettings.resolveOutlineColor() & 0xFFFFFF;
		int[] cols = null, glowCols = null;
		int colsLen = placed.text.length();
		if (karaoke && tracker != null && !lines.isEmpty() && lines.get(0).time() >= 0.0F) {
			float smoothPos = TestLyrics.position(tracker);
			int index = activeIndex;
			if (index >= 0 && index < lines.size() && placed.text.equals(lines.get(index).text())) {
				float progress = Karaoke.lineProgress(lines, index, smoothPos);
				float sung = Karaoke.sungChars(lines.get(index), progress, smoothPos);
				cols = ensureScratchCols(scratchCharCols, colsLen, false);
				Karaoke.fillColors(cols, colsLen, sung, litColor, hiColor, grayColor, IslandSettings.lyricSmoothness);
				Karaoke.maskFutureTimedWords(cols, colsLen, 0, lines.get(index), smoothPos, grayColor);
				if (glowStrength > 0.01F) {
					glowCols = ensureScratchCols(scratchGlowCols, colsLen, true);
					Karaoke.fillColors(glowCols, colsLen, sung, litGlowColor, litGlowColor, idleGlowColor, IslandSettings.lyricSmoothness);
					Karaoke.maskFutureTimedWords(glowCols, colsLen, 0, lines.get(index), smoothPos, idleGlowColor);
				}
			}
		}
		double originX = groupOrigin.x - cameraPos.x, originY = groupOrigin.y - cameraPos.y, originZ = groupOrigin.z - cameraPos.z;
		boolean seeThrough = IslandSettings.throughBlocks;
		if (IslandSettings.hologramBackground && IslandSettings.hologramBgOpacity > 0.01F) {
			int bgAlpha = Math.clamp(Math.round(alpha * IslandSettings.hologramBgOpacity), 0, 235);
			int bgColor = (bgAlpha << 24) | 0x0A0C12;
			float pixelW = Math.max(16.0F, (layout.halfWidthLocal() + 3.4F) * 8.0F);
			float pixelH = Math.max(12.0F, (layout.halfHeightLocal() + 2.4F) * 8.0F);
			if (Double.isFinite(radius)) {
				// The plate follows the same cylinder as the glyphs. A dedicated shader uses
				// global UVs across all strips, preserving one rounded outline without seams.
				int strips = 24;
				float[] plate = buildNormalCurvedPlate(originX, originY, originZ, right, up, normalTowardCam,
						halfW, halfH, flip, radius, -0.08 * Math.max(0.025F, effectiveScale * 24.0F), strips);
				IslandRender.submitNormalCurvedPlate(plate, strips, bgColor, pixelW, pixelH, 4.5F, seeThrough, modelView, projection);
			} else {
				double back = -0.012 * Math.max(0.025F, effectiveScale * 24.0F);
				float[] plate = buildWorldQuad(originX + normalTowardCam.x * back, originY + normalTowardCam.y * back,
						originZ + normalTowardCam.z * back, right, up, -halfW * flip, halfH, halfW * flip, -halfH);
				IslandRender.submitNormalCurvedPlate(plate, 1, bgColor, pixelW, pixelH, 4.5F, seeThrough, modelView, projection);
			}
		}
        if (karaoke && (PlusSettings.worldDecorations || PlusSettings.weatherTheme)) {
            drawWorldDecorations(mc, originX, originY, originZ, right, up, halfW, halfH, flip,
                alpha, ageSec, seeThrough, activeRgb);
        }
		int subRgb = IslandSettings.resolveTranslationColor() & 0xFFFFFF;
		int subAlpha = Math.min(255, Math.round(alpha * 0.86F));
		int subColor = (subAlpha << 24) | subRgb;
		int subGlow = (Math.min(255, (int) (subAlpha * 0.42F * glowStrength)) << 24) | glowRgb;
		for (NormalRow row : layout.rows()) {
			if (row.glyphs.isEmpty()) continue;
			boolean main = !row.translation;
			submitHologramGlyphList(font, row.text, row.glyphs, row.originalIndices,
					main ? cols : null, main ? colsLen : 0, main ? litColor : subColor,
					main ? glowCols : null, main ? litGlowColor : subGlow, main ? glowStrength : glowStrength * 0.65F,
					outlineRgb, main ? outlineThickness : outlineThickness * 0.85F, main ? alpha : subAlpha,
					main && karaoke, anim, ageSec, wavePhase, IslandSettings.animationSpeed,
					originX, originY, originZ, right, up, normalTowardCam, flip,
					effectiveScale * row.scaleFactor, 0.0F, -row.baselineDown * effectiveScale,
					seeThrough, row.glyphStart, layout.mainGlyphCount(), radius);
		}
	}

    /** Small local-only quads; no particles/entities and no input interception. */
    private static void drawWorldDecorations(Minecraft mc, double ox, double oy, double oz,
            Vec3 right, Vec3 up, float halfW, float halfH, float flip, int alpha, float age,
            boolean seeThrough, int accentRgb) {
        if (PlusSettings.worldDecorations) {
            for (int i = 0; i < 4; i++) {
                float side = (i % 2 == 0) ? -1.0F : 1.0F;
                float cx = side * (halfW + .12F + .04F * (float)Math.sin(age * 1.4F + i));
                float cy = halfH * (.15F + .55F * ((i / 2) % 2)) + .035F * (float)Math.sin(age * 2.0F + i);
                float r = .025F + .007F * (float)(1.0 + Math.sin(age * 2.2 + i));
                float[] quad = buildWorldQuad(ox, oy, oz, right, up, (cx-r)*flip, cy+r, (cx+r)*flip, cy-r);
                IslandRender.submitNormalCurvedPlate(quad, 1, (Math.min(255,(int)(alpha*.7F))<<24)|(accentRgb&0xffffff),
                    8, 8, 4, seeThrough, modelView, projection);
            }
        }
        if (PlusSettings.weatherTheme && mc.level != null && mc.player != null) {
            var precipitation = mc.level.precipitationAt(net.minecraft.core.BlockPos.containing(mc.player.position()));
            boolean snow = mc.level.isRaining() && precipitation == net.minecraft.world.level.biome.Biome.Precipitation.SNOW;
            boolean rain = mc.level.isRaining() && precipitation == net.minecraft.world.level.biome.Biome.Precipitation.RAIN;
            int rgb = mc.level.isThundering() ? 0xBCA8FF : snow ? 0xEAF7FF : rain ? 0x77B9FF : 0xFFD991;
            for (int i=0; i<4; i++) {
                float cx = -halfW * .8F + (halfW * 1.6F) * i / 3.0F;
                float cy = halfH + .13F + (rain || snow ? .07F * (float)Math.sin(age * 1.7F + i) : .025F * (float)Math.sin(age * 1.2F + i));
                float rw = .025F, rh = rain ? .07F : .025F;
                float[] quad = buildWorldQuad(ox, oy, oz, right, up, (cx-rw)*flip, cy+rh, (cx+rw)*flip, cy-rh);
                IslandRender.submitNormalCurvedPlate(quad, 1, (Math.min(255,(int)(alpha*.65F))<<24)|rgb,
                    8, rain ? 18 : 8, 4, seeThrough, modelView, projection);
            }
        }
    }

    /** Passive screen-edge marker for the current fixed Normal lyric, never an input target. */
    public static void renderFinderIndicator() {
        Minecraft mc = Minecraft.getInstance();
        if (!PlusSettings.finderArrow || mc.player == null || mc.level == null || mc.gui.hud.isHidden() || mc.gui.screen() != null
            || !IslandSettings.lyricsEnabled || !IslandSettings.worldText || IslandSettings.skyLyrics
            || normalFollowingEnabled() || current == null || current.anchor == null || cameraPos == null) return;
        Camera cam = mc.gameRenderer.mainCamera();
        if (!cam.isInitialized()) return;
        Vec3 d = current.anchor.subtract(cameraPos);
        Vec3 f = new Vec3(cam.forwardVector()).normalize();
        Vec3 r = SkyFollowLayout.rotate(cam.rotation(), new Vec3(1.0, 0.0, 0.0)).normalize();
        Vec3 u = SkyFollowLayout.rotate(cam.rotation(), new Vec3(0.0, 1.0, 0.0)).normalize();
        float w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        WorldIndicator.Edge edge = WorldIndicator.edge(d.dot(r), d.dot(u), d.dot(f), mc.options.fov().get(), w / Math.max(1, h));
        if (edge == null) return;
        float x = w*.5F + (float)edge.x() * Math.max(1, w*.5F-19);
        float y = h*.5F + (float)edge.y() * Math.max(1, h*.5F-13);
        String arrow = Math.abs(edge.x()) > Math.abs(edge.y()) ? (edge.x() < 0 ? "<" : ">") : (edge.y() < 0 ? "^" : "v");
        IslandRender.drawRoundedRect(x-10, y-7, 20, 14, 5,5,5,5, 0xC51A1D2A);
        IslandRender.drawCenteredText(Fonts.medium(8.0F), arrow, x, y-4.0F, 0xFFEAF3FF);
    }

	/** Continuous rounded plate on the same world-space cylinder, behind the letters. */
	static float[] buildNormalCurvedPlate(double ox, double oy, double oz, Vec3 right, Vec3 up, Vec3 normal,
			float halfWidth, float halfHeight, float flip, double radius, double back, int strips) {
		float[] out = IslandRender.acquireWorldFloats(strips * 20);
		int o = 0;
		for (int i = 0; i < strips; i++) {
			double u0 = i / (double) strips, u1 = (i + 1) / (double) strips;
			double x0 = (-halfWidth + 2.0 * halfWidth * u0) * flip;
			double x1 = (-halfWidth + 2.0 * halfWidth * u1) * flip;
			double angle0 = Math.clamp(x0 / radius, -1.05, 1.05), angle1 = Math.clamp(x1 / radius, -1.05, 1.05);
			double along0 = radius * Math.sin(angle0), along1 = radius * Math.sin(angle1);
			double depth0 = radius * (1.0 - Math.cos(angle0)) + back;
			double depth1 = radius * (1.0 - Math.cos(angle1)) + back;
			for (int v = 0; v < 4; v++) {
				boolean end = v >= 2;
				boolean bottom = v == 1 || v == 2;
				double along = end ? along1 : along0;
				double y = bottom ? -halfHeight : halfHeight;
				double depth = end ? depth1 : depth0;
				out[o++] = (float) (ox + right.x * along + up.x * y + normal.x * depth);
				out[o++] = (float) (oy + right.y * along + up.y * y + normal.y * depth);
				out[o++] = (float) (oz + right.z * along + up.z * y + normal.z * depth);
				out[o++] = (float) (end ? u1 : u0);
				out[o++] = bottom ? 1.0F : 0.0F;
			}
		}
		return out;
	}

	private static void submitHologramGlyphList(
			Font font,
			String text,
			List<float[]> glyphs,
			List<Integer> idx,
			int[] cols,
			int colsLen,
			int defaultColor,
			int[] glowCols,
			int defaultGlowColor,
			float glowStrength,
			int outlineRgb,
			float outlineThickness,
			int baseAlpha,
			boolean activeLine,
			IslandSettings.LyricAnimation anim,
			float ageSec,
			float wavePhase,
			float animSpeed,
			double originX,
			double originY,
			double originZ,
			Vec3 right,
			Vec3 up,
			Vec3 normalTowardCam,
			float flip,
			float scale,
			float baselineShift,
			float extraWorldUpOffset,
			boolean seeThrough,
			int glyphStart,
			int glyphTotal,
			double curveRadius
	) {
		int count = glyphs.size();
		if (count == 0) return;

		float[] verts = IslandRender.acquireWorldFloats(count * 20);
		int[] colors = IslandRender.acquireWorldInts(count * 4);
		boolean hasGlow = glowStrength > 0.01F;
		boolean hasOutline = outlineThickness > 0.01F;
		int[] glowColors = hasGlow ? IslandRender.acquireWorldInts(count * 4) : null;
		int[] outlineColors = hasOutline ? IslandRender.acquireWorldInts(count * 4) : null;

		boolean drawShadow = IslandSettings.hologramShadow && baseAlpha > 14;
		float[] shadowVerts = drawShadow ? IslandRender.acquireWorldFloats(count * 20) : null;
		int[] shadowColors = drawShadow ? IslandRender.acquireWorldInts(count * 4) : null;
		double shadowBack = -0.004 * Math.max(0.025F, scale * 24.0F);
		float shadowShift = 0.58F * scale;

		int o = 0;
		for (int g = 0; g < count; g++) {
			float[] quad = glyphs.get(g);
			int color = defaultColor;
			int gColor = defaultGlowColor;
			if (cols != null && idx != null && g < idx.size()) {
				int charIndex = idx.get(g);
				if (charIndex >= 0 && charIndex < colsLen) {
					color = cols[charIndex];
					if (glowCols != null) {
						gColor = glowCols[charIndex];
					}
				}
			}

			if (activeLine) {
				float reveal = LyricAnimator.glyphRevealAlpha(anim, glyphStart + g, Math.max(1, glyphTotal), wavePhase, false);
				if (reveal < 0.999F) {
					color = Karaoke.withAlpha(color, reveal);
					gColor = Karaoke.withAlpha(gColor, reveal);
				}
			}
			float waveY = activeLine
					? LyricAnimator.glyphWaveOffset(anim, glyphStart + g, ageSec, animSpeed, true) * font.getSize() * scale
					: 0.0F;

			int cAlpha = (color >>> 24) & 0xFF;
			int oColor = hasOutline ? ((Math.min(255, Math.round(cAlpha * 0.92F)) << 24) | outlineRgb) : 0;

			int sColor = 0;
			if (drawShadow) {
				int sAlpha = Math.min(255, Math.round(cAlpha * 0.78F));
				int sRgb = (((color >> 16) & 0xFF) >> 2 << 16)
						| (((color >> 8) & 0xFF) >> 2 << 8)
						| ((color & 0xFF) >> 2);
				sColor = (sAlpha << 24) | sRgb;
			}

			double glyphCenterX = (quad[0] + quad[10]) * 0.5 * scale * flip;
			double angle = Double.isFinite(curveRadius) ? Math.clamp(glyphCenterX / curveRadius, -1.05, 1.05) : 0.0;
			double cos = Math.cos(angle), sin = Math.sin(angle);
			double arcX = Double.isFinite(curveRadius) ? curveRadius * sin : glyphCenterX;
			double arcDepth = Double.isFinite(curveRadius) ? curveRadius * (1.0 - cos) : 0.0;
			for (int v = 0; v < 4; v++) {
				float offsetX = quad[v * 5] * scale * flip;
				float offsetY = -(quad[v * 5 + 1] + baselineShift) * scale + extraWorldUpOffset + waveY;
				double curvedX = arcX + (offsetX - glyphCenterX) * cos;
				double curvedDepth = arcDepth + (offsetX - glyphCenterX) * sin;
				double vx = originX + right.x * curvedX + up.x * offsetY + normalTowardCam.x * curvedDepth;
				double vy = originY + right.y * curvedX + up.y * offsetY + normalTowardCam.y * curvedDepth;
				double vz = originZ + right.z * curvedX + up.z * offsetY + normalTowardCam.z * curvedDepth;

				verts[o] = (float) vx;
				verts[o + 1] = (float) vy;
				verts[o + 2] = (float) vz;
				verts[o + 3] = quad[v * 5 + 3];
				verts[o + 4] = quad[v * 5 + 4];
				int vi = o / 5;
				colors[vi] = color;
				if (glowColors != null) glowColors[vi] = gColor;
				if (outlineColors != null) outlineColors[vi] = oColor;

				if (drawShadow) {
					float sx = offsetX + shadowShift * flip;
					float sy = offsetY - shadowShift;
					double shadowX = arcX + (sx - glyphCenterX) * cos;
					double shadowDepth = arcDepth + (sx - glyphCenterX) * sin;
					shadowVerts[o] = (float) (originX + normalTowardCam.x * (shadowDepth + shadowBack) + right.x * shadowX + up.x * sy);
					shadowVerts[o + 1] = (float) (originY + normalTowardCam.y * (shadowDepth + shadowBack) + right.y * shadowX + up.y * sy);
					shadowVerts[o + 2] = (float) (originZ + normalTowardCam.z * (shadowDepth + shadowBack) + right.z * shadowX + up.z * sy);
					shadowVerts[o + 3] = quad[v * 5 + 3];
					shadowVerts[o + 4] = quad[v * 5 + 4];
					shadowColors[vi] = sColor;
				}

				o += 5;
			}
		}

		var texView = font.textureView(text);
		var sampler = font.sampler(text);
		float sdfRange = font.sdfRange(text);

		if (drawShadow) {
			IslandRender.submitNormalGlyphs(texView, sampler, sdfRange, shadowVerts, count, shadowColors,
					seeThrough, modelView, projection);
		}
		IslandRender.submitNormalStyledGlyphs(
				texView,
				sampler,
				sdfRange,
				verts,
				count,
				colors,
				glowColors,
				glowStrength,
				outlineColors,
				outlineThickness,
				seeThrough,
				modelView,
				projection
		);
	}

	private static float[] buildWorldQuad(
			double ox,
			double oy,
			double oz,
			Vec3 right,
			Vec3 up,
			float leftX,
			float topY,
			float rightX,
			float botY
	) {
		float[] q = IslandRender.acquireWorldFloats(20);
		q[0] = (float) (ox + right.x * leftX + up.x * topY);
		q[1] = (float) (oy + right.y * leftX + up.y * topY);
		q[2] = (float) (oz + right.z * leftX + up.z * topY);
		q[3] = 0.0F; q[4] = 0.0F;

		q[5] = (float) (ox + right.x * leftX + up.x * botY);
		q[6] = (float) (oy + right.y * leftX + up.y * botY);
		q[7] = (float) (oz + right.z * leftX + up.z * botY);
		q[8] = 0.0F; q[9] = 1.0F;

		q[10] = (float) (ox + right.x * rightX + up.x * botY);
		q[11] = (float) (oy + right.y * rightX + up.y * botY);
		q[12] = (float) (oz + right.z * rightX + up.z * botY);
		q[13] = 1.0F; q[14] = 1.0F;

		q[15] = (float) (ox + right.x * rightX + up.x * topY);
		q[16] = (float) (oy + right.y * rightX + up.y * topY);
		q[17] = (float) (oz + right.z * rightX + up.z * topY);
		q[18] = 1.0F; q[19] = 0.0F;
		return q;
	}

	// =========================================================================
	// Sky Lyrics Rendering — Curved Inside Player-Following 3D Boundary + Glow
	// =========================================================================
	static final class SkyRow {
		final String text;
		final List<float[]> glyphs;
		final List<Integer> originalIndices;
		final float rowScale;
		final boolean translation;
		final int glyphStart;
		float baselineDown;
		SkyRow(String text, List<float[]> glyphs, List<Integer> indices, float rowScale,
				boolean translation, int glyphStart, float baselineDown) {
			this.text = text; this.glyphs = glyphs; this.originalIndices = indices;
			this.rowScale = rowScale; this.translation = translation;
			this.glyphStart = glyphStart; this.baselineDown = baselineDown;
		}
	}

	record SkyRows(List<SkyRow> rows, float halfWidthLocal, float halfHeightLocal, int mainGlyphCount) {}

	private static float skyWrapWidth(Font font, Vec3 anchor, float baseScale) {
		Minecraft mc = Minecraft.getInstance();
		double dist = cameraPos == null || anchor == null ? 80.0
				: Math.hypot(anchor.x - cameraPos.x, anchor.z - cameraPos.z);
		double aspect = mc == null || mc.getWindow() == null ? 16.0 / 9.0
				: (double) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
		double fov = mc == null || mc.options == null ? 70.0 : mc.options.fov().get();
		return SkyStableLayout.wrapWidth(dist, fov, aspect, baseScale, font.getSize());
	}

	/** Shrink a wrapped group only when necessary to fit around its fixed-height slot. */
	private static float skyFitScale(Font font, SkyRows rows, Vec3 anchor, float desired, boolean reserveContext) {
		if (cameraPos == null || anchor == null || rows == null) return desired;
		Minecraft mc = Minecraft.getInstance();
		double distance = Math.hypot(anchor.x - cameraPos.x, anchor.z - cameraPos.z);
		double aspect = mc == null || mc.getWindow() == null ? 16.0 / 9.0
				: (double) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
		double fov = mc == null || mc.options == null ? 70.0 : mc.options.fov().get();
		return SkyStableLayout.fitScale(distance, anchor.y - cameraPos.y, fov, aspect,
				rows.halfWidthLocal(), rows.halfHeightLocal(), font.getSize() * 0.35F, desired, reserveContext);
	}

	/** Rebuild source spans only when the FOV, size, font or text visibility changes. */
	static SkyRows buildSkyRows(Font font, Placed placed, float widthLimit) {
		boolean withTranslation = IslandSettings.showTranslations && !placed.translation.isBlank();
		IslandSettings.FontPreset preset = font.effectivePreset();
		String custom = font.effectiveCustomFont();
		if (placed.skyWrapWidth != widthLimit || placed.skyWrapTranslations != withTranslation
				|| placed.skyWrapPreset != preset || !java.util.Objects.equals(placed.skyWrapCustom, custom)) {
			placed.skyMainSpans = SkyLineWrap.wrap(placed.text, widthLimit, font::width);
			placed.skyTranslationSpans = withTranslation
					? SkyLineWrap.wrap(placed.translation, widthLimit / 0.58F, font::width) : List.of();
			placed.skyWrapWidth = widthLimit;
			placed.skyWrapTranslations = withTranslation;
			placed.skyWrapPreset = preset;
			placed.skyWrapCustom = custom;
		}
		SkyRows candidate = null;
		for (int attempt = 0; attempt < 3; attempt++) {
			long revision = DynamicSdfFont.atlasRevision();
			candidate = buildSkyRowsOnce(font, placed);
			if (revision == DynamicSdfFont.atlasRevision()) return candidate;
		}
		return candidate;
	}

	private static SkyRows buildSkyRowsOnce(Font font, Placed placed) {
		List<SkyRow> rows = new ArrayList<>();
		float cursor = 0.0F, halfWidth = 0.0F;
		float gap = font.getSize() * 0.25F * Math.clamp(IslandSettings.lyricLineSpacing, 0.70F, 1.80F);
		int mainGlyphs = 0;
		for (int group = 0; group < 2; group++) {
			boolean translated = group == 1;
			float rowScale = translated ? 0.58F : 1.0F;
			List<SkyLineWrap.Span> spans = translated ? placed.skyTranslationSpans : placed.skyMainSpans;
			for (SkyLineWrap.Span span : spans) {
				String text = span.text();
				float width = font.width(text);
				List<Integer> indices = translated ? null : new ArrayList<>();
				List<float[]> glyphs = font.buildGlyphs(text, -width * 0.5F, 0.0F, indices);
				if (indices != null) for (int i = 0; i < indices.size(); i++) {
					indices.set(i, span.originalIndex(indices.get(i)));
				}
				float minY = Float.POSITIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
				halfWidth = Math.max(halfWidth, width * rowScale * 0.5F);
				for (float[] quad : glyphs) for (int v = 0; v < 4; v++) {
					halfWidth = Math.max(halfWidth, Math.abs(quad[v * 5] * rowScale));
					minY = Math.min(minY, quad[v * 5 + 1]);
					maxY = Math.max(maxY, quad[v * 5 + 1]);
				}
				if (!Float.isFinite(minY) || !Float.isFinite(maxY)) { minY = 0.0F; maxY = font.getSize() * 0.8F; }
				float height = Math.max(font.getSize() * 0.8F * rowScale, (maxY - minY) * rowScale);
				rows.add(new SkyRow(text, glyphs, indices, rowScale, translated, mainGlyphs, cursor - minY * rowScale));
				if (!translated) mainGlyphs += glyphs.size();
				cursor += height + gap;
			}
		}
		float totalHeight = Math.max(0.0F, cursor - (rows.isEmpty() ? 0.0F : gap));
		for (SkyRow row : rows) row.baselineDown -= totalHeight * 0.5F;
		return new SkyRows(rows, halfWidth, totalHeight * 0.5F, mainGlyphs);
	}

	private static Placed skyTextForMeasure(String text, Vec3 anchor, Vec3 right, Vec3 up, float scale) {
		return new Placed(text, "", anchor, right, up, scale, null, 0.0F);
	}

	/** Fade only optional context if a boundary clamp would make wrapped groups overlap. */
	private static float skyContextVisibility(Font font, SkyRows active, Vec3 activeAnchor,
			Vec3 activeRight, Vec3 activeUp, float activeScale, SkyRows optional,
			Vec3 optionalAnchor, Vec3 optionalRight, Vec3 optionalUp, float optionalScale) {
		if (active == null || optional == null || activeAnchor == null || optionalAnchor == null || cameraPos == null) return 1.0F;
		Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
		Quaternionf field = SkyFollowLayout.cameraFieldRotation(camera.rotation());
		SkyContextLayout.View view = new SkyContextLayout.View(cameraPos,
				new Vec3(camera.forwardVector()).normalize(),
				SkyFollowLayout.rotate(field, new Vec3(-1.0, 0.0, 0.0)).normalize(),
				SkyFollowLayout.rotate(field, new Vec3(0.0, 1.0, 0.0)).normalize());
		double padding = font.getSize() * 0.45;
		SkyContextLayout.Box a = SkyContextLayout.project(view, activeAnchor, activeRight, activeUp,
				(active.halfWidthLocal() + padding) * activeScale,
				(active.halfHeightLocal() + padding) * activeScale,
				-(active.halfHeightLocal() + padding) * activeScale);
		SkyContextLayout.Box o = SkyContextLayout.project(view, optionalAnchor, optionalRight, optionalUp,
				(optional.halfWidthLocal() + padding) * optionalScale,
				(optional.halfHeightLocal() + padding) * optionalScale,
				-(optional.halfHeightLocal() + padding) * optionalScale);
		Minecraft mc = Minecraft.getInstance();
		double fov = mc.options == null ? 70.0 : mc.options.fov().get();
		double aspect = mc.getWindow() == null ? 16.0 / 9.0
				: (double) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
		return SkyContextLayout.visibility(a, o) * SkyContextLayout.viewportVisibility(o, fov, aspect);
	}

	/** Previous and next are optional rows around the same slot, not scattered Sky nodes. */
	private static Vec3 skyContextAnchor(Vec3 activeAnchor, SkyRows active, float activeScale,
			SkyRows optional, float optionalScale, boolean above, float fontSize,
			Vec3 playerCenter, double boundaryRadius) {
		double separation = active.halfHeightLocal() * activeScale
				+ optional.halfHeightLocal() * optionalScale + fontSize * activeScale * 1.5;
		Vec3 candidate = activeAnchor.add(0, above ? separation : -separation, 0);
		return clampPointToSkyBoundary(candidate.x, candidate.y, candidate.z, playerCenter, boundaryRadius);
	}

	private static void drawSky(
			Font font, Placed placed, MusicTracker tracker, List<LyricsFetcher.LyricLine> lines,
			int alpha, boolean karaoke, float lift, float animScale, float wavePhase,
			IslandSettings.LyricAnimation anim, float ageSec, Vec3 playerCenter,
			double boundaryRadius, Vec3 renderAnchor
	) {
		if (alpha <= 3 || placed.text.isEmpty() || playerCenter == null) return;
		Vec3 anchor = renderAnchor == null ? placed.anchor : renderAnchor;
		if (anchor == null) return;
		float size = font.getSize();
		float userScale = Math.clamp(IslandSettings.lyricTextScale, 0.50F, 2.00F);
		float baseScale = placed.scale * userScale;
		float beatPulse = (IslandSettings.skyBeatPulse && tracker != null && karaoke)
				? tracker.beatReactor().pulse() : 0.0F;
		float requestedScale = baseScale * animScale * (1.0F + beatPulse * 0.032F);
		SkyRows layout = buildSkyRows(font, placed, skyWrapWidth(font, anchor, baseScale));
		if (layout.mainGlyphCount() == 0) return;
		float effectiveScale = skyFitScale(font, layout, placed.anchor, requestedScale,
				placed == current && IslandSettings.skyContextLines);
		placed.skyHalfWidth = layout.halfWidthLocal() * effectiveScale;
		placed.skyHalfHeight = layout.halfHeightLocal() * effectiveScale;
		Vec3 up = new Vec3(0, 1, 0);
		Vec3 liftedAnchor = anchor.add(up.scale(pressed ? 0.0F : lift * effectiveScale * 0.38F));
		SkyFollowLayout.Basis basis = new SkyFollowLayout.Basis(placed.right, up);
		double horizWorldDist = Math.hypot(liftedAnchor.x - playerCenter.x, liftedAnchor.z - playerCenter.z);
		int atmAlpha = Math.clamp(Math.round(alpha * computeSkyHorizonFade(horizWorldDist, boundaryRadius)), 0, 255);
		if (atmAlpha <= 3) return;

		int primaryArgb = IslandSettings.resolveSkyPrimaryColor();
		int activeRgb = (pressed && placed == current) ? 0x9BD8FF : (primaryArgb & 0xFFFFFF);
		int highlightArgb = IslandSettings.resolveHighlightColor();
		int glowRgb = IslandSettings.resolveGlowColor() & 0xFFFFFF;
		int upcomingRgb = ColorUtil.upcomingForPrimary(activeRgb, glowRgb) & 0xFFFFFF;
		int transRgb = IslandSettings.resolveTranslationColor() & 0xFFFFFF;
		int outlineRgb = IslandSettings.resolveOutlineColor() & 0xFFFFFF;
		float outlineThickness = Math.clamp(IslandSettings.lyricOutlineThickness, 0.0F, 1.0F);
		float glowStrength = Math.clamp(IslandSettings.effectiveSkyGlow()
				+ (IslandSettings.lyricGlowEnabled ? beatPulse * 0.22F : 0.0F), 0.0F, 1.0F);
		int litColor = (atmAlpha << 24) | activeRgb;
		int hiColor = (atmAlpha << 24) | (highlightArgb & 0xFFFFFF);
		int grayColor = (Math.min(255, (int) (atmAlpha * 0.46F)) << 24) | upcomingRgb;
		int litGlow = (Math.min(255, (int) (atmAlpha * (0.62F + beatPulse * 0.18F) * glowStrength)) << 24) | glowRgb;
		int idleGlow = (Math.min(255, (int) (atmAlpha * 0.24F * glowStrength)) << 24) | glowRgb;
		int subAlpha = Math.min(255, Math.round(atmAlpha * 0.82F));
		int subColor = (subAlpha << 24) | transRgb;
		int subGlow = (Math.min(255, (int) (subAlpha * 0.42F * glowStrength)) << 24) | glowRgb;

		int[] cols = null, glowCols = null;
		int currentLineIdx = -1, colsLen = placed.text.length();
		if (karaoke && !lines.isEmpty() && lines.get(0).time() >= 0.0F) {
			float smoothPos = TestLyrics.position(tracker);
			currentLineIdx = activeIndex;
			if (currentLineIdx >= 0 && currentLineIdx < lines.size()
					&& placed.text.equals(lines.get(currentLineIdx).text())) {
				float progress = Karaoke.lineProgress(lines, currentLineIdx, smoothPos);
				float sung = Karaoke.sungChars(lines.get(currentLineIdx), progress, smoothPos);
				cols = ensureScratchCols(scratchCharCols, colsLen, false);
				Karaoke.fillColors(cols, colsLen, sung, litColor, hiColor, grayColor, IslandSettings.lyricSmoothness);
				Karaoke.maskFutureTimedWords(cols, colsLen, 0, lines.get(currentLineIdx), smoothPos, grayColor);
				if (glowStrength > 0.01F) {
					glowCols = ensureScratchCols(scratchGlowCols, colsLen, true);
					Karaoke.fillColors(glowCols, colsLen, sung, litGlow, litGlow, idleGlow, IslandSettings.lyricSmoothness);
					Karaoke.maskFutureTimedWords(glowCols, colsLen, 0, lines.get(currentLineIdx), smoothPos, idleGlow);
				}
			}
		}

		// Draw one optional preview below the full wrapped/translated active group.
		if (karaoke && placed == current && IslandSettings.skyContextLines
				&& currentLineIdx >= 0 && currentLineIdx + 1 < lines.size()) {
			String next = lines.get(currentLineIdx + 1).text();
			if (next != null && !next.isBlank()) {
				float nextScale = effectiveScale * 0.72F;
				Placed candidate = skyTextForMeasure(next, liftedAnchor, placed.right, up, nextScale);
				SkyRows nextRows = buildSkyRows(font, candidate, skyWrapWidth(font, liftedAnchor, nextScale));
				nextScale = skyFitScale(font, nextRows, liftedAnchor, nextScale, false);
				Vec3 nextAnchor = skyContextAnchor(liftedAnchor, layout, effectiveScale, nextRows,
						nextScale, false, size, playerCenter, boundaryRadius);
				float visible = skyContextVisibility(font, layout, liftedAnchor, placed.right, up, effectiveScale,
						nextRows, nextAnchor, placed.right, up, nextScale);
				submitSkyPlainRows(font, nextRows, nextAnchor, nextScale,
						Math.round(atmAlpha * 0.36F * visible), upcomingRgb, glowRgb, glowStrength * 0.48F,
						outlineRgb, outlineThickness * 0.72F, playerCenter, boundaryRadius, basis);
			}
		}

		for (SkyRow row : layout.rows()) {
			int count = row.glyphs.size();
			if (count == 0) continue;
			boolean main = !row.translation;
			float rowGlow = main ? glowStrength : glowStrength * 0.65F;
			float rowOutline = main ? outlineThickness : outlineThickness * 0.85F;
			float[] verts = IslandRender.acquireWorldFloats(count * 20);
			int[] colors = IslandRender.acquireWorldInts(count * 4);
			int[] glowColors = rowGlow > 0.01F ? IslandRender.acquireWorldInts(count * 4) : null;
			int[] outlines = rowOutline > 0.01F ? IslandRender.acquireWorldInts(count * 4) : null;
			int o = 0;
			for (int g = 0; g < count; g++) {
				float[] quad = row.glyphs.get(g);
				int color = main ? litColor : subColor;
				int gColor = main ? litGlow : subGlow;
				if (main && cols != null && row.originalIndices != null && g < row.originalIndices.size()) {
					int charIndex = row.originalIndices.get(g);
					if (charIndex >= 0 && charIndex < colsLen) {
						color = cols[charIndex];
						if (glowCols != null) gColor = glowCols[charIndex];
					}
				}
				int globalGlyph = row.glyphStart + g;
				if (main && karaoke) {
					float reveal = LyricAnimator.glyphRevealAlpha(anim, globalGlyph,
							Math.max(1, layout.mainGlyphCount()), wavePhase, false);
					if (reveal < 0.999F) {
						color = Karaoke.withAlpha(color, reveal);
						gColor = Karaoke.withAlpha(gColor, reveal);
					}
				}
				float waveY = main && karaoke ? LyricAnimator.glyphWaveOffset(anim, globalGlyph, ageSec,
						IslandSettings.animationSpeed, true) * size * effectiveScale : 0.0F;
				int cAlpha = (color >>> 24) & 0xFF;
				int oColor = outlines != null ? ((Math.min(255, Math.round(cAlpha * 0.92F)) << 24) | outlineRgb) : 0;
				for (int v = 0; v < 4; v++) {
					float offsetX = quad[v * 5] * row.rowScale * effectiveScale;
					float offsetY = -(quad[v * 5 + 1] * row.rowScale + row.baselineDown) * effectiveScale + waveY;
					Vec3 world = SkyFollowLayout.vertex(liftedAnchor, cameraPos, playerCenter, boundaryRadius,
							offsetX, offsetY, IslandSettings.skyCurve,
							(layout.halfWidthLocal() + size) * effectiveScale, basis);
					verts[o] = (float) (world.x - cameraPos.x);
					verts[o + 1] = (float) (world.y - cameraPos.y);
					verts[o + 2] = (float) (world.z - cameraPos.z);
					verts[o + 3] = quad[v * 5 + 3]; verts[o + 4] = quad[v * 5 + 4];
					int vi = o / 5; colors[vi] = color;
					if (glowColors != null) glowColors[vi] = gColor;
					if (outlines != null) outlines[vi] = oColor;
					o += 5;
				}
			}
			IslandRender.submitWorldStyledGlyphs(font.textureView(row.text), font.sampler(row.text),
					font.sdfRange(row.text), verts, count, colors, glowColors, rowGlow, outlines, rowOutline,
					true, IslandSettings.skyThroughBlocks, modelView, projection);
		}
	}

	private static void drawSkySongHeaderIfActive(
			Font font,
			MusicTracker tracker,
			List<LyricsFetcher.LyricLine> lines,
			int currentIndex,
			Vec3 playerCenter,
			double boundaryRadius
	) {
		var session = tracker.getSession();
		if (session == null || skyAnchor == null) return;
		var media = session.getMedia();
		if (media == null || media.getTitle() == null || media.getTitle().isBlank()) return;

		float pos = tracker.rawPosition();
		boolean introBeforeLyrics = !lines.isEmpty() && currentIndex < 0;
		boolean earlyTrack = pos >= 0.0F && pos < 6.5F;
		boolean unsyncedDisplay = lines.isEmpty() && media.isPlaying();
		if (!introBeforeLyrics && !earlyTrack && !unsyncedDisplay) return;

		float fade = 1.0F;
		if (!introBeforeLyrics && !unsyncedDisplay && pos > 4.8F) {
			fade = Math.clamp((6.5F - pos) / 1.7F, 0.0F, 1.0F);
		}
		if (fade <= 0.03F) return;

		String artist = media.getArtist() == null ? "" : media.getArtist().trim();
		String banner = artist.isEmpty() ? media.getTitle() : (media.getTitle() + "  -  " + artist);

		float baseScale = computeSkyScale(IslandSettings.skyTextSize) * Math.clamp(IslandSettings.lyricTextScale, 0.50F, 2.00F);
		float headerScale = current == null ? baseScale * 0.88F : baseScale * 0.58F;
		Placed header = skyTextForMeasure(banner, skyAnchor, skyRight, skyUp, headerScale);
		SkyRows headerRows = buildSkyRows(font, header, skyWrapWidth(font, skyAnchor, headerScale));
		headerScale = skyFitScale(font, headerRows, skyAnchor, headerScale, false);
		Vec3 headerAnchor = skyAnchor;
		float visible = 1.0F;
		if (current != null && current.skyManaged) {
			float mainBase = current.scale * Math.clamp(IslandSettings.lyricTextScale, 0.50F, 2.00F);
			SkyRows mainRows = buildSkyRows(font, current, skyWrapWidth(font, current.anchor, mainBase));
			float mainScale = skyFitScale(font, mainRows, current.anchor, mainBase * current.lastAnimScale,
					IslandSettings.skyContextLines);
			Vec3 topAnchor = current.anchor;
			SkyRows topRows = mainRows;
			float topScale = mainScale;
			if (IslandSettings.skyContextLines && !retiring.isEmpty()) {
				Placed prev = retiring.getLast();
				float prevBase = prev.scale * Math.clamp(IslandSettings.lyricTextScale, 0.50F, 2.00F);
				SkyRows prevRows = buildSkyRows(font, prev, skyWrapWidth(font, prev.anchor, prevBase));
				float prevScale = skyFitScale(font, prevRows, prev.anchor, prevBase * prev.lastAnimScale, false);
				topAnchor = skyContextAnchor(current.anchor, mainRows, mainScale, prevRows, prevScale,
						true, font.getSize(), playerCenter, boundaryRadius);
				topRows = prevRows; topScale = prevScale;
			}
			headerAnchor = skyContextAnchor(topAnchor, topRows, topScale, headerRows, headerScale,
					true, font.getSize(), playerCenter, boundaryRadius);
			visible = skyContextVisibility(font, mainRows, current.anchor, current.right, current.up, mainScale,
					headerRows, headerAnchor, skyRight, skyUp, headerScale);
			visible *= skyContextVisibility(font, topRows, topAnchor, skyRight, skyUp, topScale,
					headerRows, headerAnchor, skyRight, skyUp, headerScale);
		}
		double headerDist = Math.hypot(headerAnchor.x - playerCenter.x, headerAnchor.z - playerCenter.z);
		float headerFade = computeSkyHorizonFade(headerDist, boundaryRadius) * visible;
		int glowRgb = IslandSettings.resolveGlowColor() & 0xFFFFFF;
		int textRgb = current == null ? (IslandSettings.resolveSkyPrimaryColor() & 0xFFFFFF) : glowRgb;
		int outlineRgb = IslandSettings.resolveOutlineColor() & 0xFFFFFF;
		submitSkyPlainRows(font, headerRows, headerAnchor, headerScale,
				Math.round(215.0F * fade * headerFade), textRgb, glowRgb,
				IslandSettings.effectiveSkyGlow() * 0.80F, outlineRgb,
				Math.clamp(IslandSettings.lyricOutlineThickness, 0.0F, 1.0F) * 0.80F,
				playerCenter, boundaryRadius, new SkyFollowLayout.Basis(skyRight, skyUp));
	}

	private static void submitSkyPlainRows(Font font, SkyRows layout, Vec3 anchor, float scale, int alpha,
			int textRgb, int glowRgb, float glowStrength, int outlineRgb, float outlineThickness,
			Vec3 playerCenter, double boundaryRadius, SkyFollowLayout.Basis basis) {
		if (alpha <= 4) return;
		int mainColor = (Math.min(255, alpha) << 24) | (textRgb & 0xFFFFFF);
		int haloColor = (Math.min(255, (int) (alpha * 0.55F * glowStrength)) << 24) | (glowRgb & 0xFFFFFF);
		int outColor = (Math.min(255, Math.round(alpha * 0.90F)) << 24) | (outlineRgb & 0xFFFFFF);
		for (SkyRow row : layout.rows()) {
			int count = row.glyphs.size();
			if (count == 0) continue;
			float[] verts = IslandRender.acquireWorldFloats(count * 20);
			int[] colors = IslandRender.acquireWorldInts(count * 4);
			int[] glowColors = glowStrength > 0.01F ? IslandRender.acquireWorldInts(count * 4) : null;
			int[] outlineColors = outlineThickness > 0.01F ? IslandRender.acquireWorldInts(count * 4) : null;
			int o = 0;
			for (float[] quad : row.glyphs) for (int v = 0; v < 4; v++) {
				float offsetX = quad[v * 5] * row.rowScale * scale;
				float offsetY = -(quad[v * 5 + 1] * row.rowScale + row.baselineDown) * scale;
				Vec3 world = SkyFollowLayout.vertex(anchor, cameraPos, playerCenter, boundaryRadius,
						offsetX, offsetY, IslandSettings.skyCurve,
						(layout.halfWidthLocal() + font.getSize()) * scale, basis);
				verts[o] = (float) (world.x - cameraPos.x);
				verts[o + 1] = (float) (world.y - cameraPos.y);
				verts[o + 2] = (float) (world.z - cameraPos.z);
				verts[o + 3] = quad[v * 5 + 3]; verts[o + 4] = quad[v * 5 + 4];
				int vi = o / 5; colors[vi] = mainColor;
				if (glowColors != null) glowColors[vi] = haloColor;
				if (outlineColors != null) outlineColors[vi] = outColor;
				o += 5;
			}
			IslandRender.submitWorldStyledGlyphs(font.textureView(row.text), font.sampler(row.text),
					font.sdfRange(row.text), verts, count, colors, glowColors, glowStrength, outlineColors,
					outlineThickness, true, IslandSettings.skyThroughBlocks, modelView, projection);
		}
	}

	private WorldLyrics() {
	}
}
