package com.lt5lyrics.client.island;

import com.lt5lyrics.client.island.font.UnicodeClusterer;
import com.lt5lyrics.client.music.LyricsFetcher;
import java.util.ArrayList;
import java.util.List;

public final class Karaoke {
	public static final int SUNG = 0xFFFFFFFF;
	public static final int UPCOMING = 0x66FFFFFF;

	public record Word(int start, int end) {
	}

	public static float lineProgress(List<LyricsFetcher.LyricLine> lines, int index, float position) {
		if (index < 0 || index >= lines.size()) return 0.0F;
		float start = lines.get(index).time();
		if (start < 0.0F) return 1.0F;
		float end = index + 1 < lines.size() ? lines.get(index + 1).time() : start + 4.0F;
		if (end <= start) return 1.0F;
		return Math.max(0.0F, Math.min(1.0F, (position - start) / (end - start)));
	}

	public static float sungChars(String text, float progress) {
		return text.length() * progress;
	}

	/**
	 * Uses genuine syllable/word timestamps when available (BetterLyrics TTML, Lrcmux word-sync,
	 * or Enhanced LRC <mm:ss.xx> tags), falling back smoothly to line-level progress otherwise.
	 */
	public static float sungChars(LyricsFetcher.LyricLine line, float lineProgress, float positionSeconds) {
		if (line == null) return 0.0F;
		String text = line.text();
		if (text.isEmpty()) return 0.0F;
		List<LyricsFetcher.WordSpan> spans = line.words();
		if (spans == null || spans.isEmpty()) {
			return text.length() * Math.clamp(lineProgress, 0.0F, 1.0F);
		}

		int maxChars = text.length();
		float reached = 0.0F;
		for (int i = 0; i < spans.size(); i++) {
			LyricsFetcher.WordSpan sp = spans.get(i);
			int cStart = Math.max(0, Math.min(maxChars, sp.charStart()));
			int cEnd = Math.max(cStart, Math.min(maxChars, sp.charEnd()));
			if (positionSeconds < sp.startSec()) {
				break;
			}
			if (positionSeconds >= sp.endSec() || sp.endSec() <= sp.startSec()) {
				reached = Math.max(reached, cEnd);
			} else {
				float frac = (positionSeconds - sp.startSec()) / (sp.endSec() - sp.startSec());
				reached = Math.max(reached, cStart + (cEnd - cStart) * Math.clamp(frac, 0.0F, 1.0F));
				// A later segment may already have started while this one overlaps.
			}
		}
		return Math.clamp(reached, 0.0F, (float) maxChars);
	}

	public static Word wordAt(String text, float charIndex) {
		if (text.isEmpty()) return new Word(0, 0);
		List<Word> all = words(text);
		if (all.isEmpty()) return new Word(0, text.length());
		return all.get(activeWord(all, charIndex));
	}

	/**
	 * Script-aware karaoke word splitter:
	 * - Splits on spaces / zero-width spaces for space-delimited scripts.
	 * - Splits long unspaced CJK / Khmer / Thai runs cleanly at grapheme/syllable cluster boundaries
	 *   so HUD karaoke scrolling and word bounce work smoothly without breaking Khmer Coeng subscripts.
	 */
	public static List<Word> words(String text) {
		List<Word> words = new ArrayList<>();
		if (text == null || text.isEmpty()) return words;

		int n = text.length();
		int i = 0;
		while (i < n) {
			while (i < n && isWordSeparator(text.charAt(i))) i++;
			if (i >= n) break;
			int runStart = i;
			while (i < n && !isWordSeparator(text.charAt(i))) i++;
			appendSegmentedRun(words, text, runStart, i);
		}
		return words;
	}

	private static boolean isWordSeparator(char c) {
		return c == ' ' || c == '\u00A0' || c == '\u3000' || c == '\u200B';
	}

	private static void appendSegmentedRun(List<Word> out, String fullText, int start, int end) {
		String sub = fullText.substring(start, end);
		boolean hasUnspacedScript = false;
		for (int j = 0; j < sub.length(); ) {
			int cp = Character.codePointAt(sub, j);
			j += Character.charCount(cp);
			if (UnicodeClusterer.isKhmerCodePoint(cp)
				|| UnicodeClusterer.isCjkOrKanaOrHangul(cp)
				|| (cp >= 0x0E00 && cp <= 0x0EFF)) {
				hasUnspacedScript = true;
				break;
			}
		}

		if (!hasUnspacedScript || sub.length() <= 4) {
			out.add(new Word(start, end));
			return;
		}

		List<UnicodeClusterer.Cluster> clusters = UnicodeClusterer.segment(sub);
		if (clusters.size() <= 2) {
			out.add(new Word(start, end));
			return;
		}

		int groupStart = start + clusters.get(0).start();
		int countInGroup = 0;
		for (int k = 0; k < clusters.size(); k++) {
			UnicodeClusterer.Cluster cl = clusters.get(k);
			int firstCp = cl.text().codePointAt(0);
			boolean cjk = UnicodeClusterer.isCjkOrKanaOrHangul(firstCp);
			int targetGroupSize = cjk ? 2 : 3;
			countInGroup++;
			boolean isPunct = !Character.isLetterOrDigit(firstCp);
			if (countInGroup >= targetGroupSize || isPunct || k == clusters.size() - 1) {
				int groupEnd = start + cl.end();
				if (groupEnd > groupStart) {
					out.add(new Word(groupStart, groupEnd));
				}
				if (k + 1 < clusters.size()) {
					groupStart = start + clusters.get(k + 1).start();
				}
				countInGroup = 0;
			}
		}
	}

	/** Do not animate/scroll to a timed word before its own source timestamp. */
	public static int activeTimedWord(List<Word> words, LyricsFetcher.LyricLine line, float sung, float position) {
		if (line == null || line.words().isEmpty()) return activeWord(words, sung);
		LyricsFetcher.WordSpan latest = null;
		for (LyricsFetcher.WordSpan span : line.words()) {
			if (position < span.startSec()) break;
			latest = span;
		}
		if (latest == null) return -1;
		// A segment can contain a whole phrase. Track its internal sweep, but do
		// not scroll/bounce to the next word during a gap before its timestamp.
		int end = Math.clamp(latest.charEnd(), 0, line.text().length());
		while (end > latest.charStart() && isWordSeparator(line.text().charAt(end - 1))) end--;
		float edge = Math.max(latest.charStart(), end - 0.001F);
		return activeWord(words, Math.clamp(sung, (float) latest.charStart(), edge));
	}

	/** Suppress the soft crest on a future timed word, in both whole-line and per-word buffers. */
	public static void maskFutureTimedWords(int[] dest, int length, int charOffset,
			LyricsFetcher.LyricLine line, float position, int upcomingColor) {
		if (line == null || line.words().isEmpty()) return;
		for (LyricsFetcher.WordSpan span : line.words()) {
			if (position >= span.startSec()) continue;
			int from = Math.clamp(span.charStart() - charOffset, 0, length);
			int to = Math.clamp(span.charEnd() - charOffset, 0, length);
			for (int i = from; i < to; i++) dest[i] = upcomingColor;
		}
	}

	public static int activeWord(List<Word> words, float sung) {
		for (int i = 0; i < words.size(); i++) {
			if (sung < words.get(i).end()) return i;
		}
		return words.size() - 1;
	}

	public static int[] colors(String text, float sung, int sungColor, int upcomingColor) {
		int[] colors = new int[text.length()];
		fillColors(colors, text.length(), sung, sungColor, sungColor, upcomingColor, 1.0F);
		return colors;
	}

	public static int[] colorsWithHighlight(
		String text,
		float sung,
		int sungColor,
		int highlightColor,
		int upcomingColor,
		float smoothness
	) {
		int[] colors = new int[text.length()];
		fillColors(colors, text.length(), sung, sungColor, highlightColor, upcomingColor, smoothness);
		return colors;
	}

	/**
	 * Zero-allocation karaoke sweep writer into a caller-reused int[] buffer.
	 * Smoothly sweeps upcomingColor -> highlightColor (at active word/edge) -> sungColor.
	 */
	public static void fillColors(
		int[] dest,
		int length,
		float sung,
		int sungColor,
		int highlightColor,
		int upcomingColor,
		float smoothness
	) {
		float smooth = Math.max(0.45F, 2.2F * Math.clamp(smoothness, 0.25F, 2.0F));
		boolean distinctHighlight = (sungColor & 0xFFFFFF) != (highlightColor & 0xFFFFFF);
		float crestWidth = Math.max(2.2F, 3.6F * Math.clamp(smoothness, 0.4F, 1.8F));

		for (int i = 0; i < length; i++) {
			float t = Math.max(0.0F, Math.min(1.0F, (sung - i) / smooth + 0.5F));
			t = t * t * (3.0F - 2.0F * t);
			int base = blend(upcomingColor, sungColor, t);
			if (distinctHighlight && sung > 0.05F && sung < length + crestWidth) {
				float dist = Math.abs((sung - 0.8F) - i) / crestWidth;
				if (dist < 1.0F) {
					float hAmt = 1.0F - dist;
					hAmt = hAmt * hAmt * (3.0F - 2.0F * hAmt);
					int hlWithBaseAlpha = ((base >>> 24) << 24) | (highlightColor & 0xFFFFFF);
					base = blend(base, hlWithBaseAlpha, hAmt * 0.90F);
				}
			}
			dest[i] = base;
		}
	}

	public static int blend(int from, int to, float t) {
		int a = (int) (((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * t);
		int r = (int) (((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
		int g = (int) (((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
		int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	public static int withAlpha(int argb, float factor) {
		int a = (int) Math.max(0.0F, Math.min(255.0F, (argb >>> 24) * factor));
		return (a << 24) | (argb & 0xFFFFFF);
	}

	private Karaoke() {
	}
}
