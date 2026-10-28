package com.lt5lyrics.client.island;

/**
 * Pure, deterministic motion & transition curves for all LT5Lyrics lyric surfaces
 * (Dynamic Island HUD, Normal 3D World TextDisplay Holograms, and 3D Sky Lyrics).
 */
public final class LyricAnimator {
	public record MotionState(
		float alpha,
		float liftUnits,
		float slideXUnits,
		float scaleFactor,
		float progress
	) {}

	private LyricAnimator() {
	}

	public static long enterDurationMs(IslandSettings.LyricAnimation anim, float speed) {
		float s = clampSpeed(speed);
		long base = switch (anim) {
			case RISE_FLOW -> 460L;
			case CLASSIC_GLIDE -> 420L;
			case CASCADE_DROP -> 460L;
			case POP_SPRING -> 500L;
			case WAVE_RIPPLE -> 430L;
			case CINEMA_REVEAL -> 480L;
		};
		return Math.max(120L, Math.round(base / s));
	}

	public static long exitDurationMs(IslandSettings.LyricAnimation anim, float speed) {
		float s = clampSpeed(speed);
		long base = switch (anim) {
			case RISE_FLOW -> 760L;
			case CLASSIC_GLIDE -> 450L;
			case CASCADE_DROP -> 720L;
			case POP_SPRING -> 420L;
			case WAVE_RIPPLE -> 520L;
			case CINEMA_REVEAL -> 460L;
		};
		return Math.max(140L, Math.round(base / s));
	}

	/**
	 * Computes motion state for the currently active lyric line at elapsedMs since spawn.
	 * Positive liftUnits = upward in 3D world/sky (and negative Y in 2D screen coordinates).
	 */
	public static MotionState computeActive(IslandSettings.LyricAnimation anim, long elapsedMs, float speed) {
		float s = clampSpeed(speed);
		long dur = enterDurationMs(anim, s);
		float t = Math.clamp(elapsedMs / (float) dur, 0.0F, 1.0F);
		float ageSec = Math.max(0.0F, elapsedMs / 1000.0F);
		float easeOut3 = easeOutCubic(t);

		return switch (anim) {
			case RISE_FLOW -> {
				// Rises smoothly from below (-1.15 -> 0) AND continues gently floating upward while active
				float entranceLift = (1.0F - easeOut3) * -1.15F;
				float liveDrift = Math.min(0.32F, ageSec * 0.055F * s);
				float alpha = easeOutCubic(Math.min(1.0F, t * 1.15F));
				yield new MotionState(alpha, entranceLift + liveDrift, 0.0F, 0.96F + 0.04F * easeOut3, t);
			}
			case CLASSIC_GLIDE -> new MotionState(
				easeOut3,
				(1.0F - easeOut3) * -0.50F,
				0.0F,
				1.0F,
				t
			);
			case CASCADE_DROP -> {
				float entranceLift = (1.0F - easeOut3) * 1.15F;
				float liveDrift = -Math.min(0.26F, ageSec * 0.045F * s);
				float alpha = easeOutCubic(Math.min(1.0F, t * 1.15F));
				yield new MotionState(alpha, entranceLift + liveDrift, 0.0F, 1.04F - 0.04F * easeOut3, t);
			}
			case POP_SPRING -> {
				float spring = 1.0F - (float) (Math.exp(-5.8 * t) * Math.cos(9.8 * t) * 0.34);
				float alpha = Math.min(1.0F, t * 2.1F);
				alpha = alpha * alpha * (3.0F - 2.0F * alpha);
				float lift = (1.0F - easeOut3) * -0.22F;
				yield new MotionState(alpha, lift, 0.0F, spring, t);
			}
			case WAVE_RIPPLE -> new MotionState(
				easeOut3,
				(1.0F - easeOut3) * -0.42F,
				0.0F,
				1.0F,
				t
			);
			case CINEMA_REVEAL -> new MotionState(
				Math.min(1.0F, t * 1.6F),
				(1.0F - easeOut3) * -0.18F,
				(1.0F - easeOut3) * -0.45F,
				1.0F,
				t
			);
		};
	}

	/**
	 * Computes motion state for a retiring (fading-out) lyric line at exitElapsedMs after retirement,
	 * preserving exact continuity from its liftAtRetire / scaleAtRetire when the next line started.
	 */
	public static MotionState computeExiting(
		IslandSettings.LyricAnimation anim,
		long exitElapsedMs,
		float speed,
		float liftAtRetire,
		float scaleAtRetire
	) {
		float s = clampSpeed(speed);
		long dur = exitDurationMs(anim, s);
		float t = Math.clamp(exitElapsedMs / (float) dur, 0.0F, 1.0F);
		float inv = 1.0F - t;
		float fadeQuad = inv * inv;

		return switch (anim) {
			case RISE_FLOW -> {
				// Continues rising smoothly upward while fading out as the next line rises up!
				float riseOut = 0.55F * t + 0.95F * easeOutQuad(t);
				yield new MotionState(fadeQuad, liftAtRetire + riseOut, 0.0F, scaleAtRetire * (1.0F - 0.05F * t), t);
			}
			case CLASSIC_GLIDE -> new MotionState(
				fadeQuad,
				liftAtRetire + (1.0F - fadeQuad) * 0.90F,
				0.0F,
				scaleAtRetire,
				t
			);
			case CASCADE_DROP -> {
				float dropOut = 0.50F * t + 0.90F * easeOutQuad(t);
				yield new MotionState(fadeQuad, liftAtRetire - dropOut, 0.0F, scaleAtRetire * (1.0F - 0.06F * t), t);
			}
			case POP_SPRING -> new MotionState(
				fadeQuad,
				liftAtRetire + 0.28F * easeOutQuad(t),
				0.0F,
				scaleAtRetire * (1.0F - 0.16F * easeOutQuad(t)),
				t
			);
			case WAVE_RIPPLE -> new MotionState(
				fadeQuad,
				liftAtRetire + 0.72F * easeOutQuad(t),
				0.0F,
				scaleAtRetire,
				t
			);
			case CINEMA_REVEAL -> new MotionState(
				fadeQuad,
				liftAtRetire + 0.30F * easeOutQuad(t),
				0.65F * easeOutQuad(t),
				scaleAtRetire,
				t
			);
		};
	}

	/**
	 * Per-glyph / per-cluster vertical wave offset (in font-height fractions) for WAVE_RIPPLE.
	 */
	public static float glyphWaveOffset(
		IslandSettings.LyricAnimation anim,
		int glyphIndex,
		float timeSeconds,
		float speed,
		boolean active
	) {
		if (anim != IslandSettings.LyricAnimation.WAVE_RIPPLE) {
			return 0.0F;
		}
		float s = clampSpeed(speed);
		float amp = active ? 0.14F : 0.07F;
		return (float) Math.sin(timeSeconds * 4.8F * s - glyphIndex * 0.44F) * amp;
	}

	/**
	 * Per-glyph staggered alpha multiplier [0..1] for CINEMA_REVEAL entrance.
	 */
	public static float glyphRevealAlpha(
		IslandSettings.LyricAnimation anim,
		int glyphIndex,
		int totalGlyphs,
		float enterProgress,
		boolean exiting
	) {
		if (anim != IslandSettings.LyricAnimation.CINEMA_REVEAL || totalGlyphs <= 1) {
			return 1.0F;
		}
		float frac = glyphIndex / (float) Math.max(1, totalGlyphs - 1);
		if (!exiting) {
			float local = (enterProgress * 1.45F) - frac * 0.45F;
			local = Math.clamp(local, 0.0F, 1.0F);
			return local * local * (3.0F - 2.0F * local);
		}
		return 1.0F;
	}

	private static float clampSpeed(float speed) {
		if (!Float.isFinite(speed)) return IslandSettings.DEFAULT_ANIM_SPEED;
		return Math.clamp(speed, IslandSettings.MIN_ANIM_SPEED, IslandSettings.MAX_ANIM_SPEED);
	}

	private static float easeOutCubic(float t) {
		float u = 1.0F - Math.clamp(t, 0.0F, 1.0F);
		return 1.0F - u * u * u;
	}

	private static float easeOutQuad(float t) {
		float c = Math.clamp(t, 0.0F, 1.0F);
		return 1.0F - (1.0F - c) * (1.0F - c);
	}
}
