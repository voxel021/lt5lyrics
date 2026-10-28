package com.lt5lyrics.client.island.font;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits international Unicode text into atomic OpenType shaping / grapheme clusters so complex
 * scripts (Khmer Coeng subscripts + split vowels, Thai/Lao leading vowels + tone marks, Indic
 * virama conjuncts, Arabic words, Vietnamese stacked diacritics, CJK ideographs, and emoji
 * surrogate pairs) are shaped intact by HarfBuzz while preserving exact UTF-16 source indices for
 * per-syllable / per-character Karaoke highlighting and 3D curved Sky Lyrics.
 */
public final class UnicodeClusterer {
	public record Cluster(int start, int end, String text, boolean rtl, boolean whitespace) {}

	private UnicodeClusterer() {
	}

	public static List<Cluster> segment(String input) {
		List<Cluster> out = new ArrayList<>();
		if (input == null || input.isEmpty()) {
			return out;
		}
		int len = input.length();
		int i = 0;
		while (i < len) {
			char c0 = input.charAt(i);
			// Skip Minecraft § formatting pairs cleanly while preserving indices
			if (c0 == '\u00A7') {
				i += (i + 1 < len) ? 2 : 1;
				continue;
			}
			// Zero-width space / format chars produce no visual glyph
			if (c0 == '\u200B' || c0 == '\uFEFF') {
				i++;
				continue;
			}

			int cp = Character.codePointAt(input, i);
			int step = Character.charCount(cp);

			if (Character.isWhitespace(cp) || cp == '\u00A0' || cp == '\u3000') {
				int end = i + step;
				out.add(new Cluster(i, end, " ", false, true));
				i = end;
				continue;
			}

			// Arabic / Hebrew RTL word run: keep contiguous cursive letters + marks together
			if (isRtlScript(cp)) {
				int start = i;
				i += step;
				while (i < len) {
					int nextCp = Character.codePointAt(input, i);
					if (isRtlScript(nextCp) || isCombiningOrJoiner(nextCp)) {
						i += Character.charCount(nextCp);
					} else {
						break;
					}
				}
				out.add(new Cluster(start, i, input.substring(start, i), true, false));
				continue;
			}

			// Thai / Lao leading vowels (U+0E40..0E44, U+0EC0..0EC4) attach to the following consonant
			if (isLeadingThaiLaoVowel(cp)) {
				int start = i;
				i += step;
				if (i < len) {
					int baseCp = Character.codePointAt(input, i);
					if (isThaiLaoConsonant(baseCp)) {
						i += Character.charCount(baseCp);
					}
				}
				i = consumeClusterTail(input, i, len);
				out.add(new Cluster(start, i, input.substring(start, i), false, false));
				continue;
			}

			int start = i;
			i += step;
			i = consumeClusterTail(input, i, len);
			out.add(new Cluster(start, i, input.substring(start, i), false, false));
		}
		return out;
	}

	private static int consumeClusterTail(String s, int i, int len) {
		while (i < len) {
			int cp = Character.codePointAt(s, i);
			int step = Character.charCount(cp);

			// Khmer Coeng (U+17D2) or Indic/Myanmar Virama/Subscript joiner absorbs the following consonant!
			if (isSubscriptJoiner(cp)) {
				i += step;
				if (i < len) {
					int subCp = Character.codePointAt(s, i);
					i += Character.charCount(subCp);
				}
				continue;
			}

			// Zero-Width Joiner (ZWJ) for emoji / Indic / Khmer ligatures absorbs next base char
			if (cp == 0x200D) {
				i += step;
				if (i < len) {
					int joined = Character.codePointAt(s, i);
					i += Character.charCount(joined);
				}
				continue;
			}

			if (isDependentMarkOrSign(cp)) {
				i += step;
				continue;
			}

			break;
		}
		return i;
	}

	public static boolean isSubscriptJoiner(int cp) {
		return cp == 0x17D2 // Khmer Sign Coeng (្)
			|| cp == 0x094D // Devanagari Virama
			|| cp == 0x09CD // Bengali Virama
			|| cp == 0x0A4D // Gurmukhi Virama
			|| cp == 0x0ACD // Gujarati Virama
			|| cp == 0x0B4D // Oriya Virama
			|| cp == 0x0BCD // Tamil Virama
			|| cp == 0x0C4D // Telugu Virama
			|| cp == 0x0CCD // Kannada Virama
			|| cp == 0x0D4D // Malayalam Virama
			|| cp == 0x1039 // Myanmar Virama
			|| cp == 0x103A;
	}

	public static boolean isDependentMarkOrSign(int cp) {
		int type = Character.getType(cp);
		if (type == Character.NON_SPACING_MARK
			|| type == Character.COMBINING_SPACING_MARK
			|| type == Character.ENCLOSING_MARK) {
			return true;
		}
		// Khmer dependent vowels, shifters, signs (U+17B4..U+17DD)
		if (cp >= 0x17B4 && cp <= 0x17DD) {
			return true;
		}
		// Thai / Lao / Myanmar vowel & tone signs
		if ((cp >= 0x0E30 && cp <= 0x0E3A) || (cp >= 0x0E47 && cp <= 0x0E4E)) {
			return true;
		}
		if ((cp >= 0x0EB0 && cp <= 0x0EBD) || (cp >= 0x0EC8 && cp <= 0x0ECD)) {
			return true;
		}
		// Variation selectors & ZWNJ
		return cp == 0x200C || (cp >= 0xFE00 && cp <= 0xFE0F);
	}

	private static boolean isCombiningOrJoiner(int cp) {
		return isDependentMarkOrSign(cp) || cp == 0x200D;
	}

	private static boolean isLeadingThaiLaoVowel(int cp) {
		return (cp >= 0x0E40 && cp <= 0x0E44) || (cp >= 0x0EC0 && cp <= 0x0EC4);
	}

	private static boolean isThaiLaoConsonant(int cp) {
		return (cp >= 0x0E01 && cp <= 0x0E2E) || (cp >= 0x0E81 && cp <= 0x0EAE);
	}

	private static boolean isRtlScript(int cp) {
		return (cp >= 0x0590 && cp <= 0x05FF) // Hebrew
			|| (cp >= 0x0600 && cp <= 0x06FF) // Arabic
			|| (cp >= 0x0750 && cp <= 0x077F) // Arabic Supplement
			|| (cp >= 0x08A0 && cp <= 0x08FF) // Arabic Extended-A
			|| (cp >= 0xFB50 && cp <= 0xFDFF)
			|| (cp >= 0xFE70 && cp <= 0xFEFF);
	}

	public static boolean isKhmerCodePoint(int cp) {
		return (cp >= 0x1780 && cp <= 0x17FF) || (cp >= 0x19E0 && cp <= 0x19FF);
	}

	public static boolean isCjkOrKanaOrHangul(int cp) {
		return (cp >= 0x3040 && cp <= 0x30FF) // Hiragana + Katakana
			|| (cp >= 0x31F0 && cp <= 0x31FF)
			|| (cp >= 0x3400 && cp <= 0x4DBF) // CJK Ext A
			|| (cp >= 0x4E00 && cp <= 0x9FFF) // CJK Unified Ideographs
			|| (cp >= 0xAC00 && cp <= 0xD7AF) // Hangul Syllables
			|| (cp >= 0x1100 && cp <= 0x11FF) // Hangul Jamo
			|| (cp >= 0x3130 && cp <= 0x318F) // Hangul Compat Jamo
			|| (cp >= 0xF900 && cp <= 0xFAFF) // CJK Compatibility
			|| (cp >= 0x20000 && cp <= 0x2FA1F); // CJK Ext B..F
	}

	/**
	 * Returns true if text contains any character outside the fast 183-glyph Latin/Cyrillic MSDF set
	 * (such as Khmer, Chinese, Japanese, Korean, Thai, Vietnamese multi-diacritics, Arabic, Greek, etc.).
	 */
	public static boolean containsExtendedUnicode(String text) {
		if (text == null || text.isEmpty()) return false;
		for (int i = 0; i < text.length(); ) {
			int cp = Character.codePointAt(text, i);
			i += Character.charCount(cp);
			if (cp == '\u00A7' || cp == ' ') continue;
			if (cp > 0xFFFF) return true;
			if (isKhmerCodePoint(cp) || isCjkOrKanaOrHangul(cp) || isRtlScript(cp)) return true;
			if (cp >= 0x0370 && cp <= 0x03FF) return true; // Greek
			if (cp >= 0x0900 && cp <= 0x109F) return true; // South & Southeast Asian scripts
			if (cp >= 0x1E00 && cp <= 0x1EFF) return true; // Vietnamese Latin Extended Additional
			if (cp >= 0x3000 && cp <= 0x303F) return true; // CJK punctuation
			if (cp >= 0xFF00 && cp <= 0xFFEF) return true; // Fullwidth forms
			int type = Character.getType(cp);
			if (type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK) return true;
		}
		return false;
	}
}
