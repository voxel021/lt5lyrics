package com.lt5lyrics.client.island;

import java.util.Locale;

public final class ColorUtil {
	private static final float TWO_PI = (float) (Math.PI * 2.0);

	private ColorUtil() {
	}

	public static int clampChannel(int v) {
		return Math.max(0, Math.min(255, v));
	}

	public static float clamp01(float v) {
		if (!Float.isFinite(v)) return 0.0F;
		return Math.max(0.0F, Math.min(1.0F, v));
	}

	public static int opaque(int rgb) {
		return 0xFF000000 | (rgb & 0xFFFFFF);
	}

	public static int red(int rgb) {
		return (rgb >> 16) & 0xFF;
	}

	public static int green(int rgb) {
		return (rgb >> 8) & 0xFF;
	}

	public static int blue(int rgb) {
		return rgb & 0xFF;
	}

	public static int rgb(int r, int g, int b) {
		return 0xFF000000
			| (clampChannel(r) << 16)
			| (clampChannel(g) << 8)
			| clampChannel(b);
	}

	public static int withAlpha(int rgb, float alpha0to255) {
		int a = Math.max(0, Math.min(255, Math.round(alpha0to255)));
		return (a << 24) | (rgb & 0xFFFFFF);
	}

	public static int hsvToRgb(float h, float s, float v) {
		float hue = h - (float) Math.floor(h);
		float sat = clamp01(s);
		float val = clamp01(v);

		float kr = Math.abs(fract(hue + 1.0F) * 6.0F - 3.0F);
		float kg = Math.abs(fract(hue + 2.0F / 3.0F) * 6.0F - 3.0F);
		float kb = Math.abs(fract(hue + 1.0F / 3.0F) * 6.0F - 3.0F);

		float r = val * lerp(1.0F, clamp01(kr - 1.0F), sat);
		float g = val * lerp(1.0F, clamp01(kg - 1.0F), sat);
		float b = val * lerp(1.0F, clamp01(kb - 1.0F), sat);

		int ri = clampChannel(Math.round(r * 255.0F));
		int gi = clampChannel(Math.round(g * 255.0F));
		int bi = clampChannel(Math.round(b * 255.0F));
		return 0xFF000000 | (ri << 16) | (gi << 8) | bi;
	}

	public static float[] rgbToHsv(int rgb) {
		float r = ((rgb >> 16) & 0xFF) / 255.0F;
		float g = ((rgb >> 8) & 0xFF) / 255.0F;
		float b = (rgb & 0xFF) / 255.0F;

		float max = Math.max(r, Math.max(g, b));
		float min = Math.min(r, Math.min(g, b));
		float delta = max - min;

		float h;
		if (delta < 1.0E-6F) {
			h = 0.0F;
		} else if (max == r) {
			h = ((g - b) / delta) / 6.0F;
			if (h < 0.0F) h += 1.0F;
		} else if (max == g) {
			h = (((b - r) / delta) + 2.0F) / 6.0F;
		} else {
			h = (((r - g) / delta) + 4.0F) / 6.0F;
		}

		float s = max < 1.0E-6F ? 0.0F : delta / max;
		return new float[] { h, clamp01(s), clamp01(max) };
	}

	/**
	 * Maps normalized wheel coordinates nx, ny in [-1, 1] (centered at wheel center)
	 * to { hue in [0, 1), saturation in [0, 1] }. Matches island_wheel.fsh exactly.
	 */
	public static float[] wheelToHueSat(float nx, float ny) {
		float dist = (float) Math.hypot(nx, ny);
		float sat = clamp01(dist);
		float angle = (float) Math.atan2(ny, nx);
		float hue = fract(angle / TWO_PI + 1.0F);
		return new float[] { hue, sat };
	}

	/**
	 * Inverse of wheelToHueSat: returns normalized { nx, ny } in [-1, 1].
	 */
	public static float[] hueSatToWheel(float hue, float sat) {
		float s = clamp01(sat);
		float angle = hue * TWO_PI;
		return new float[] {
			(float) Math.cos(angle) * s,
			(float) Math.sin(angle) * s
		};
	}

	public static String toHex(int rgb) {
		return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
	}

	public static String toRgbString(int rgb) {
		return red(rgb) + ", " + green(rgb) + ", " + blue(rgb);
	}

	/**
	 * Strictly parses #RGB, RGB, #RRGGBB, RRGGBB, 0xRRGGBB, or #AARRGGBB.
	 * Returns null when the input is not a valid hex color.
	 */
	public static Integer parseHexStrict(String raw) {
		if (raw == null) return null;
		String clean = raw.trim();
		if (clean.startsWith("#")) clean = clean.substring(1).trim();
		else if (clean.startsWith("0x") || clean.startsWith("0X")) clean = clean.substring(2).trim();
		if (clean.length() == 3) {
			char r = clean.charAt(0);
			char g = clean.charAt(1);
			char b = clean.charAt(2);
			clean = "" + r + r + g + g + b + b;
		} else if (clean.length() == 8) {
			clean = clean.substring(2);
		}
		if (clean.length() != 6) return null;
		for (int i = 0; i < 6; i++) {
			char c = clean.charAt(i);
			boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
			if (!hex) return null;
		}
		try {
			int parsed = Integer.parseInt(clean, 16);
			return 0xFF000000 | (parsed & 0xFFFFFF);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	public static int parseHexOrDefault(String raw, int fallback) {
		Integer strict = parseHexStrict(raw);
		return strict != null ? strict : opaque(fallback);
	}

	/**
	 * Parses an RGB triplet such as "255, 133, 179" or "255 133 179".
	 * Returns null when invalid or out of [0..255].
	 */
	public static Integer parseRgbTriplet(String raw) {
		if (raw == null || raw.isBlank()) return null;
		String cleaned = raw.trim()
			.replace("rgb(", "")
			.replace("RGB(", "")
			.replace(")", "")
			.trim();
		String[] parts = cleaned.split("[,\\s;/]+");
		if (parts.length != 3) return null;
		try {
			int r = Integer.parseInt(parts[0].trim());
			int g = Integer.parseInt(parts[1].trim());
			int b = Integer.parseInt(parts[2].trim());
			if (r < 0 || r > 255 || g < 0 || g > 255 || b < 0 || b > 255) return null;
			return rgb(r, g, b);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/**
	 * Computes a luminous, matching soft glow color for a chosen primary lyric color
	 * in Simple Mode (when the user has not set a separate custom glow color).
	 */
	public static int harmonizedGlowFor(int primaryRgb) {
		float[] hsv = rgbToHsv(primaryRgb);
		if (hsv[1] < 0.06F) {
			return 0xFF8FC7E3;
		}
		float sat = Math.max(0.52F, Math.min(0.92F, hsv[1] * 1.12F));
		float val = Math.max(0.78F, Math.min(1.00F, hsv[2] + 0.14F));
		return hsvToRgb(hsv[0], sat, val);
	}

	/**
	 * Computes a crisp active-word karaoke highlight color harmonized to primaryRgb
	 * when the user has not locked a separate highlight color in Advanced Mode.
	 */
	public static int harmonizedHighlightFor(int primaryRgb) {
		float[] hsv = rgbToHsv(primaryRgb);
		if (hsv[1] < 0.06F) {
			return 0xFFFFFFFF;
		}
		float sat = Math.max(0.18F, hsv[1] * 0.68F);
		float val = Math.min(1.00F, Math.max(0.92F, hsv[2] + 0.22F));
		return hsvToRgb(hsv[0], sat, val);
	}

	/**
	 * Derives a readable unsung/upcoming karaoke color that preserves the user's chosen hue
	 * while maintaining clear contrast against sung words.
	 */
	public static int upcomingForPrimary(int primaryRgb, int baseUpcomingRgb) {
		float[] hsv = rgbToHsv(primaryRgb);
		if (hsv[1] < 0.06F) {
			return opaque(baseUpcomingRgb);
		}
		float sat = Math.min(0.58F, hsv[1] * 0.65F);
		float val = Math.max(0.56F, Math.min(0.84F, hsv[2] * 0.78F));
		return hsvToRgb(hsv[0], sat, val);
	}

	private static float fract(float x) {
		return x - (float) Math.floor(x);
	}

	private static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}
}
