package com.lt5lyrics.client.island;

/**
 * Smooths raw system audio peak samples into a responsive audio level envelope
 * and a transient beat-pulse signal for subtle HUD & Sky Lyrics reactivity.
 */
public final class BeatReactor {
	private float smoothLevel;
	private float rollingFloor;
	private float beatPulse;
	private boolean floorSeeded;
	private long lastUpdateNs;
	private long lastBeatNs;

	public void update(float rawPeak, boolean playing) {
		long now = System.nanoTime();
		float dt = lastUpdateNs == 0L ? 0.016F : Math.min(0.1F, Math.max(0.001F, (now - lastUpdateNs) / 1.0E9F));
		lastUpdateNs = now;

		updateStep(rawPeak, playing, dt, now);
	}

	public void updateStep(float rawPeak, boolean playing, float dt, long nowNs) {
		float clampedDt = Math.min(0.1F, Math.max(0.001F, dt));
		if (!playing || !Float.isFinite(rawPeak) || rawPeak < 0.0F) {
			smoothLevel += (0.0F - smoothLevel) * Math.min(1.0F, clampedDt * 8.0F);
			rollingFloor += (0.0F - rollingFloor) * Math.min(1.0F, clampedDt * 4.0F);
			beatPulse += (0.0F - beatPulse) * Math.min(1.0F, clampedDt * 10.0F);
			floorSeeded = false;
			return;
		}

		float shaped = ColorUtil.clamp01((float) Math.pow(Math.max(0.0F, rawPeak), 0.78) * 1.18F);
		if (!floorSeeded) {
			rollingFloor = shaped;
			floorSeeded = true;
		}

		float attackOrRelease = shaped > smoothLevel ? 24.0F : 7.5F;
		smoothLevel += (shaped - smoothLevel) * Math.min(1.0F, clampedDt * attackOrRelease);

		// Slow baseline tracker to isolate transient drum/bass hits
		float floorRate = shaped > rollingFloor ? 2.6F : 4.5F;
		rollingFloor += (shaped - rollingFloor) * Math.min(1.0F, clampedDt * floorRate);

		beatPulse += (0.0F - beatPulse) * Math.min(1.0F, clampedDt * 8.5F);

		float delta = shaped - rollingFloor;
		boolean cooldownReady = (nowNs - lastBeatNs) >= 135_000_000L; // ~440 BPM ceiling
		if (cooldownReady && rawPeak > 0.12F && shaped > 0.26F && delta > 0.11F) {
			float impulse = ColorUtil.clamp01(0.32F + delta * 2.2F);
			if (impulse > beatPulse) {
				beatPulse = impulse;
				lastBeatNs = nowNs;
			}
		}
	}

	public float level() {
		return ColorUtil.clamp01(smoothLevel);
	}

	public float pulse() {
		return ColorUtil.clamp01(beatPulse);
	}

	public void reset() {
		smoothLevel = 0.0F;
		rollingFloor = 0.0F;
		beatPulse = 0.0F;
		floorSeeded = false;
		lastUpdateNs = 0L;
		lastBeatNs = 0L;
	}
}
