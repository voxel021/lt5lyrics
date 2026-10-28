package com.lt5lyrics.client.music;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalizes and parses media session metadata across desktop music players (Spotify, iTunes,
 * MusicBee, etc.) and web browsers playing YouTube, YouTube Music, SoundCloud, Bilibili, and other
 * streams — with first-class support for Khmer, Chinese, Japanese, Korean, Thai, Vietnamese,
 * Arabic, Cyrillic, and Latin scripts.
 */
public final class TrackMetadata {
	public record QueryPair(String artist, String title) {}

	public record CleanedChannel(String name, boolean topicChannel, boolean curatorChannel) {}

	private static final Pattern BROWSER_TAB_COUNTER = Pattern.compile("^\\(\\d+\\)\\s*");

	private static final Pattern PLATFORM_SUFFIX = Pattern.compile(
		"(?i)\\s*[-\\u2013\\u2014|\\u2022\\u00B7]\\s*(?:youtube(?:\\s+music)?|soundcloud|spotify|twitch|bilibili|\\u54d4\\u54e9\\u54d4\\u54e9|\\u7f51\\u6613\\u4e91\\u97f3\\u4e50|qq\\u97f3\\u4e50)\\s*$"
	);

	private static final Pattern NOISE_BRACKET = Pattern.compile(
		"(?i)[\\(\\[\\{\\u3010\\u3016\\uff08][^\\)\\]\\}\\u3011\\u3017\\uff09]*(?:"
			+ "\\b(?:official|music\\s*video|lyric|lyrics|video|audio|visualizer|visualiser|"
			+ "mv|m/v|pv|hd|hq|4k|8k|1080p|60fps|remaster(?:ed)?|remix|live|version|radio\\s*edit|"
			+ "explicit|clean|prod\\.?|produced|directed|dir\\.?|feat\\.?|ft\\.?|featuring|"
			+ "ost|soundtrack|from\\s+|slowed|reverb|sped\\s*up|nightcore|bass\\s*boosted|"
			+ "color\\s*coded|sub\\s+|subs|vietsub|engsub|khmer\\s*sub|espa\\u00f1ol|espanol|karaoke|"
			+ "performance|dance\\s*practice|studio\\s*choom|colors\\s*show|tiny\\s*desk|l\\u1eddi\\s*b\\u00e0i\\s*h\\u00e1t)\\b"
			+ "|\\u5b98\\u65b9(?:mv|\\u7248|\\u97f3\\u6e90|\\u8996\\u983b|\\u89c6\\u9891)?|\\u6b4c\\u8a5e\\u7248|\\u6b4c\\u8bcd\\u7248|\\u52a8\\u6001\\u6b4c\\u8bcd|\\u5b8c\\u6574\\u7248|\\u9ad8\\u6e05|\\u9ad8\\u97f3\\u8d28|\\u7eaf\\u4eab\\u7248|\\u73b0\\u573a\\u7248|\\u62fc\\u97f3\\u6b4c\\u8bcd"
			+ "|\\u516c\\u5f0fmv|\\u30ea\\u30ea\\u30c3\\u30af\\u30d3\\u30c7\\u30aa|\\u30df\\u30e5\\u30fc\\u30b8\\u30c3\\u30af\\u30d3\\u30c7\\u30aa|\\uac00\\uc0ac|\\ubba4\\uc9c1\\ube44\\ub514\\uc624"
			+ "|\\u179c\\u17b8\\u178a\\u17c1\\u17a2\\u17bc\\u1795\\u17d2\\u179b\\u17bc\\u179c\\u1780\\u17b6\\u179a|\\u1785\\u1798\\u17d2\\u179a\\u17c0\\u1784\\u17a2\\u1780\\u17d2\\u179f\\u179a\\u179a\\u178f\\u17cb|\\u1794\\u1791\\u1790\\u17d2\\u1798\\u17b8|\\u1797\\u17d2\\u179b\\u17c1\\u1784\\u179f\\u17bb\\u1791\\u17d2\\u1792"
			+ "|\\u0e40\\u0e19\\u0e37\\u0e49\\u0e2d\\u0e40\\u0e1e\\u0e25\\u0e07"
			+ ")[^\\)\\]\\}\\u3011\\u3017\\uff09]*[\\)\\]\\}\\u3011\\u3017\\uff09]"
	);

	private static final Pattern STANDALONE_BRACKET_TAG = Pattern.compile(
		"(?i)^\\s*[\\(\\[\\u3010\\uff08](?:mv|m/v|pv|4k|8k|hd|hq|audio|video|lyrics?|official|\\u5b98\\u65b9|\\u6b4c\\u8bcd|\\u6b4c\\u8a5e)[\\)\\]\\u3011\\uff09]\\s*"
			+ "|\\s*[\\(\\[\\u3010\\uff08](?:mv|m/v|pv|4k|8k|hd|hq|audio|video|lyrics?|official|\\u5b98\\u65b9|\\u6b4c\\u8bcd|\\u6b4c\\u8a5e)[\\)\\]\\u3011\\uff09]\\s*$"
	);

	private static final Pattern INLINE_NOISE_SUFFIX = Pattern.compile(
		"(?i)\\s+[-\\u2013\\u2014|]\\s*(?:"
			+ "official\\s*(?:music\\s*)?(?:video|audio|lyric.*|visualizer|visualiser|mv)|"
			+ "lyrics?(?:\\s+video)?|music\\s*video|audio|visualizer|visualiser|"
			+ "remaster(?:ed)?(?:\\s+\\d{4})?|\\d{4}\\s+remaster(?:ed)?|"
			+ "hd|4k|hq|slowed.*|sped\\s*up.*|nightcore.*|"
			+ "\\u5b98\\u65b9(?:mv|\\u7248)|\\u6b4c\\u8a5e\\u7248|\\u6b4c\\u8bcd\\u7248"
			+ ")\\s*$"
	);

	private static final Pattern UNBRACKETED_VIDEO_SUFFIX = Pattern.compile(
		"(?i)\\s+(?:official\\s+(?:music\\s+)?(?:video|audio|lyric\\s*video|visualizer|visualiser|mv)|"
			+ "music\\s+video|lyric\\s+video)\\s*$"
	);

	private static final Pattern INLINE_FEAT_OR_PROD = Pattern.compile(
		"(?i)\\s+(?:feat\\.?|ft\\.?|featuring|prod\\.?\\s*by|produced\\s*by)\\s+[^-\\u2013\\u2014|]+$"
	);

	private static final Pattern TRAILING_PARENS = Pattern.compile(
		"\\s*[\\(\\[\\uff08][^\\)\\]\\uff09]*[\\)\\]\\uff09]\\s*$"
	);

	private static final Pattern PARENS_EXTRACT = Pattern.compile(
		"^(.+?)\\s*[\\(\\uff08]([^\\)\\uff09]{1,48})[\\)\\uff09]\\s*$"
	);

	private static final Pattern TOPIC_SUFFIX = Pattern.compile(
		"(?i)\\s*[-\\u2013\\u2014]\\s*(?:topic|temat|tema|th\\u00e8me|theme|thema|\\u30c8\\u30d4\\u30c3\\u30af|\\uc8fc\\uc81c)\\s*$"
	);

	private static final Pattern VEVO_SUFFIX = Pattern.compile("(?i)vevo$");

	private static final Pattern OFFICIAL_AFFIX = Pattern.compile(
		"(?i)^official\\s+|\\s+official$|^the\\s+official\\s+|\\s+channel$"
	);

	private static final Pattern CURATOR_CHANNEL = Pattern.compile(
		"(?i)^(?:7clouds|vibe\\s*music|shadow\\s*music|unique\\s*vibes|cakes\\s*&\\s*eclairs|"
			+ "syrebralvibes|latinhype|taj\\s*tracks|dan\\s*music|royal\\s*music|dopelyrics|"
			+ "pillow|aqua\\s*lyrics|cloudxmusic|lyrical\\s*lemonade|genius|colors|vevo|"
			+ "trap\\s*nation|bass\\s*nation|chill\\s*nation|proximity|mrsuicidesheep|"
			+ "nocopyrightsounds|ncs|monstercat.*|spinnin'?\\s*records|fueled\\s*by\\s*ramen|"
			+ "atlantic\\s*records|interscope.*|republic\\s*records|columbia\\s*records|"
			+ "hybe\\s*labels|smtown|jyp\\s*entertainment|yg\\s*entertainment|1thek.*|"
			+ "rasmey\\s*hang\\s*meas.*|rhm\\s*production.*|town\\s*production.*|sunday\\s*production.*|"
			+ "galaxy\\s*navatra.*|baramey\\s*production.*|klapyahandz.*|pleng(?:\\s*by\\s*smart)?|"
			+ "rock\\s*records.*|\\u6efe\\u77f3\\u5531\\u7247.*|\\u6770\\u5a01\\u723e.*|jvr\\s*music.*|"
			+ "\\u76f8\\u4fe1\\u97f3\\u6a02.*|bin\\s*music.*|avex.*|ehpmusicchannel.*)$"
			+ "|\\b(?:lyrics|vibes|records|entertainment|labels|recordings|soundtrack|production|productions)\\s*$"
	);

	private static final Pattern TITLE_SPLITTER = Pattern.compile("\\s+[-\\u2013\\u2014|]\\s+");

	private static final Pattern QUOTED_SONG_AFTER_ARTIST = Pattern.compile(
		"^(.+?)\\s*[\"'\\u300c\\u300e\\u300a\\u3010]([^\"'\\u300d\\u300f\\u300b\\u3011]{1,})[\"'\\u300d\\u300f\\u300b\\u3011]$"
	);

	private static final Pattern QUOTED_SONG_BY_ARTIST = Pattern.compile(
		"(?i)^[\"'\\u300c\\u300e\\u300a]([^\"'\\u300d\\u300f\\u300b]{1,})[\"'\\u300d\\u300f\\u300b]\\s+by\\s+(.+)$"
	);

	private static final Pattern ARTIST_SEPARATOR = Pattern.compile(
		"(?i)\\s*(?:,|&|;|\\u3001|\\s+/\\s+|/|\\bx\\b|\\band\\b|\\bwith\\b|\\bfeat\\.?\\b|\\bft\\.?\\b|\\bfeaturing\\b|\\bvs\\.?\\b)\\s*"
	);

	private TrackMetadata() {
	}

	/**
	 * Script-aware Unicode folding:
	 * - Folds Latin/Cyrillic/Greek diacritics (both NFC "ROSÉ" and NFD "ROSE\u0301" -> "rose").
	 * - Preserves Khmer dependent vowels/Coeng subscripts, Thai/Lao/Indic vowel & tone signs,
	 *   Hangul syllables (NFC), and Chinese/Japanese CJK characters intact!
	 * - Strips zero-width format characters (U+200B..U+200D, U+FEFF) so Khmer titles with or
	 *   without keyboard zero-width spaces match identically.
	 */
	public static String fold(String value) {
		if (value == null || value.isEmpty()) return "";
		String nfc = Normalizer.normalize(value, Normalizer.Form.NFC);
		StringBuilder sb = new StringBuilder(nfc.length());
		for (int i = 0; i < nfc.length(); ) {
			int cp = Character.codePointAt(nfc, i);
			i += Character.charCount(cp);

			// Ignore zero-width spaces / format controls
			if (cp == 0x200B || cp == 0x200C || cp == 0x200D || cp == 0xFEFF) {
				continue;
			}

			// Latin / Greek / Cyrillic: strip accents via NFKD decomposition
			if (cp <= 0x052F || (cp >= 0x1E00 && cp <= 0x1EFF)) {
				String sub = Normalizer.normalize(Character.toString(cp), Normalizer.Form.NFKD);
				for (int j = 0; j < sub.length(); j++) {
					char c = sub.charAt(j);
					if (Character.getType(c) == Character.NON_SPACING_MARK) continue;
					char low = mapSpecialLatin(Character.toLowerCase(c));
					if ((low >= 'a' && low <= 'z') || (low >= '0' && low <= '9') || Character.isLetterOrDigit(low)) {
						sb.append(low);
					}
				}
				continue;
			}

			int type = Character.getType(cp);
			if (Character.isLetterOrDigit(cp)
				|| type == Character.NON_SPACING_MARK
				|| type == Character.COMBINING_SPACING_MARK) {
				sb.appendCodePoint(Character.toLowerCase(cp));
			}
		}
		return sb.toString();
	}

	private static char mapSpecialLatin(char c) {
		return switch (c) {
			case '\u0142' -> 'l';
			case '\u00f8' -> 'o';
			case '\u00df' -> 's';
			case '\u0111' -> 'd';
			default -> c;
		};
	}

	public static boolean isStructuredMusicPlayer(String owner) {
		if (owner == null || owner.isEmpty()) return false;
		String lower = owner.toLowerCase(Locale.ROOT);
		return lower.contains("spotify")
			|| lower.contains("itunes")
			|| lower.contains("applemusic")
			|| lower.contains("musicbee")
			|| lower.contains("tidal")
			|| lower.contains("deezer")
			|| lower.contains("qobuz")
			|| lower.contains("foobar")
			|| lower.contains("aimp")
			|| lower.contains("dopamine")
			|| lower.contains("yandex")
			|| lower.contains("cloudmusic")
			|| lower.contains("qqmusic")
			|| lower.contains("kugou");
	}

	public static String normalizePunctuation(String input) {
		if (input == null) return "";
		return Normalizer.normalize(input, Normalizer.Form.NFC)
			.replace('\u2018', '\'')
			.replace('\u2019', '\'')
			.replace('\u02BC', '\'')
			.replace('\u201C', '"')
			.replace('\u201D', '"')
			.replace('\u00A0', ' ')
			.replace('\u3000', ' ')
			.replace("\u200B", "")
			.replace("\uFEFF", "")
			.trim();
	}

	public static String cleanTitle(String rawTitle) {
		if (rawTitle == null || rawTitle.isBlank()) return "";
		String t = normalizePunctuation(rawTitle);
		t = BROWSER_TAB_COUNTER.matcher(t).replaceFirst("").trim();
		t = PLATFORM_SUFFIX.matcher(t).replaceFirst("").trim();

		String prev;
		do {
			prev = t;
			t = NOISE_BRACKET.matcher(t).replaceAll("").trim();
			t = STANDALONE_BRACKET_TAG.matcher(t).replaceAll("").trim();
			t = INLINE_NOISE_SUFFIX.matcher(t).replaceFirst("").trim();
			t = UNBRACKETED_VIDEO_SUFFIX.matcher(t).replaceFirst("").trim();
		} while (!t.equals(prev));

		t = INLINE_FEAT_OR_PROD.matcher(t).replaceFirst("").trim();
		t = t.replaceAll("\\s{2,}", " ").trim();
		return stripOuterQuotes(t);
	}

	public static CleanedChannel cleanChannel(String rawArtist) {
		if (rawArtist == null || rawArtist.isBlank()) {
			return new CleanedChannel("", false, true);
		}
		String a = normalizePunctuation(rawArtist);
		boolean topic = TOPIC_SUFFIX.matcher(a).find();
		if (topic) {
			a = TOPIC_SUFFIX.matcher(a).replaceFirst("").trim();
		}
		if (VEVO_SUFFIX.matcher(a).find()) {
			a = VEVO_SUFFIX.matcher(a).replaceFirst("").trim();
			if (!a.contains(" ")) {
				a = a.replaceAll("([a-z])([A-Z])", "$1 $2")
					.replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2");
			}
		}
		a = OFFICIAL_AFFIX.matcher(a).replaceAll("").trim();
		a = a.replaceAll("\\s{2,}", " ").trim();
		boolean curator = !topic && (a.isEmpty() || CURATOR_CHANNEL.matcher(a).find());
		return new CleanedChannel(a, topic, curator);
	}

	public static List<String> artistVariants(String artist) {
		List<String> variants = new ArrayList<>();
		if (artist == null) return variants;
		String cleaned = INLINE_FEAT_OR_PROD.matcher(normalizePunctuation(artist)).replaceFirst("").trim();
		if (!cleaned.isEmpty()) {
			addUniqueVariant(variants, cleaned);
		}
		// Extract parenthetical alias e.g. "VannDa (វណ្ណដា)" or "周杰伦 (Jay Chou)"
		Matcher pm = PARENS_EXTRACT.matcher(cleaned);
		if (pm.matches()) {
			addUniqueVariant(variants, stripOuterQuotes(pm.group(1).trim()));
			addUniqueVariant(variants, stripOuterQuotes(pm.group(2).trim()));
		}
		// Split bilingual adjacent names e.g. "វណ្ណដា VannDa" or "周杰伦 Jay Chou"
		for (String scriptPart : splitDualScriptParts(cleaned)) {
			addUniqueVariant(variants, scriptPart);
		}
		String[] parts = ARTIST_SEPARATOR.split(cleaned);
		for (String part : parts) {
			String candidate = stripOuterQuotes(part.trim());
			if (!candidate.isEmpty()) {
				addUniqueVariant(variants, candidate);
				for (String sp : splitDualScriptParts(candidate)) {
					addUniqueVariant(variants, sp);
				}
			}
		}
		return variants;
	}

	/** Artist compatibility respects explicit collaborator/alias splits, not arbitrary one-character substrings. */
	public static boolean artistCompatible(String wanted, String candidate) {
		if (wanted == null || wanted.isBlank()) return true;
		if (candidate == null || candidate.isBlank()) return false;
		List<String> left = new ArrayList<>(artistVariants(wanted));
		List<String> right = new ArrayList<>(artistVariants(candidate));
		left.add(wanted); right.add(candidate);
		for (String a : left) for (String b : right) {
			String fa = fold(a), fb = fold(b);
			if (fa.isEmpty() || fb.isEmpty()) continue;
			if (fa.equals(fb)) return true;
			int small = Math.min(fa.length(), fb.length()), big = Math.max(fa.length(), fb.length());
			if (small >= 3 && small * 2 >= big && (fa.contains(fb) || fb.contains(fa))) return true;
		}
		return false;
	}

	public static List<String> titleVariants(String title) {
		List<String> variants = new ArrayList<>();
		if (title == null) return variants;
		String clean = stripOuterQuotes(title.trim());
		if (clean.isEmpty()) return variants;
		addUniqueVariant(variants, clean);

		Matcher pm = PARENS_EXTRACT.matcher(clean);
		if (pm.matches()) {
			String outer = stripOuterQuotes(pm.group(1).trim());
			String inner = stripOuterQuotes(pm.group(2).trim());
			addUniqueVariant(variants, outer);
			if (inner.length() >= 2 && !NOISE_BRACKET.matcher("(" + inner + ")").matches()) {
				addUniqueVariant(variants, inner);
			}
		} else {
			String stripped = stripOuterQuotes(stripTrailingParens(clean));
			if (!stripped.isEmpty()) {
				addUniqueVariant(variants, stripped);
			}
		}

		for (String scriptPart : splitDualScriptParts(stripTrailingParens(clean))) {
			addUniqueVariant(variants, scriptPart);
		}
		return variants;
	}

	/**
	 * Splits strings that combine a non-Latin Asian script (Khmer, CJK, Hangul, Kana, Thai) and Latin
	 * title/name side-by-side (e.g. "សារជាតិ Unfaithful", "វណ្ណដា VannDa", "周杰伦 Jay Chou") into
	 * [nonLatinPart, latinPart] so lookups succeed regardless of which script the lyric database indexed.
	 */
	static List<String> splitDualScriptParts(String value) {
		List<String> res = new ArrayList<>();
		if (value == null || value.length() < 3) return res;

		StringBuilder nonLatin = new StringBuilder();
		StringBuilder latin = new StringBuilder();
		boolean sawAsian = false;
		boolean sawLatin = false;

		for (int i = 0; i < value.length(); ) {
			int cp = Character.codePointAt(value, i);
			i += Character.charCount(cp);
			if (isAsianScript(cp)) {
				sawAsian = true;
				nonLatin.appendCodePoint(cp);
			} else if ((cp >= 'A' && cp <= 'Z') || (cp >= 'a' && cp <= 'z') || (cp >= 0x00C0 && cp <= 0x024F)) {
				sawLatin = true;
				latin.appendCodePoint(cp);
			} else if (cp == ' ' || cp == '\'' || cp == '-') {
				if (!nonLatin.isEmpty() && nonLatin.charAt(nonLatin.length() - 1) != ' ') nonLatin.append(' ');
				if (!latin.isEmpty() && latin.charAt(latin.length() - 1) != ' ') latin.append(' ');
			}
		}

		if (sawAsian && sawLatin) {
			String nl = nonLatin.toString().replaceAll("^[\\s'-]+|[\\s'-]+$", "").trim();
			String lt = latin.toString().replaceAll("^[\\s'-]+|[\\s'-]+$", "").trim();
			if (!nl.isEmpty() && !lt.isEmpty() && lt.length() >= 2) {
				res.add(nl);
				res.add(lt);
			}
		}
		return res;
	}

	private static boolean isAsianScript(int cp) {
		return (cp >= 0x1780 && cp <= 0x17FF) // Khmer
			|| (cp >= 0x19E0 && cp <= 0x19FF) // Khmer Symbols
			|| (cp >= 0x0E00 && cp <= 0x0EFF) // Thai + Lao
			|| (cp >= 0x3040 && cp <= 0x30FF) // Hiragana + Katakana
			|| (cp >= 0x3400 && cp <= 0x9FFF) // CJK
			|| (cp >= 0xAC00 && cp <= 0xD7AF) // Hangul
			|| (cp >= 0x0600 && cp <= 0x06FF) // Arabic
			|| (cp >= 0x0750 && cp <= 0x077F) // Arabic Supplement
			|| (cp >= 0x0590 && cp <= 0x05FF) // Hebrew
			|| (cp >= 0x0400 && cp <= 0x052F); // Cyrillic
	}

	private static void addUniqueVariant(List<String> list, String v) {
		if (v == null) return;
		String t = v.trim();
		if (t.isEmpty()) return;
		// Allow 1-char CJK/Khmer titles (e.g. "愛", "ម៉ែ") while requiring >=2 chars for pure ASCII
		boolean hasNonAscii = t.codePoints().anyMatch(cp -> cp > 127);
		if (!hasNonAscii && t.length() < 2) return;
		if (!containsFolded(list, t)) {
			list.add(t);
		}
	}

	/**
	 * Builds ordered (artist, title) search candidates tailored to whether the session comes from
	 * a structured player (Spotify, YouTube Topic) or a video/browser title ("Artist - Song (Official Video)").
	 */
	public static List<QueryPair> buildCandidates(String owner, String rawArtist, String rawTitle) {
		String ct = cleanTitle(rawTitle);
		if (ct.isEmpty()) return List.of();

		CleanedChannel ch = cleanChannel(rawArtist);
		boolean structured = isStructuredMusicPlayer(owner) || ch.topicChannel();

		Set<String> seen = new LinkedHashSet<>();
		List<QueryPair> out = new ArrayList<>();

		if (structured && !ch.name().isEmpty()) {
			// Keep an explicit remix/live/acoustic version as the first query; only then try a clean alternate.
			String raw = normalizePunctuation(rawTitle);
			if (raw.toLowerCase(Locale.ROOT).matches(".*\\b(remix|live|acoustic|instrumental|radio edit)\\b.*")) {
				addExpanded(out, seen, ch.name(), raw);
			}
			addExpanded(out, seen, ch.name(), ct);
		}

		// Split "Artist - Song" (or "Song - Artist") when present in the video/window title
		String[] split = TITLE_SPLITTER.split(ct, 2);
		if (split.length == 2) {
			String left = stripOuterQuotes(INLINE_FEAT_OR_PROD.matcher(split[0]).replaceFirst("").trim());
			String right = stripOuterQuotes(INLINE_FEAT_OR_PROD.matcher(split[1]).replaceFirst("").trim());

			if (!left.isEmpty() && !right.isEmpty()) {
				boolean channelMatchesRight = !ch.name().isEmpty() && !ch.curatorChannel()
					&& fold(right).contains(fold(ch.name())) && !fold(left).contains(fold(ch.name()));
				if (channelMatchesRight) {
					addExpanded(out, seen, right, left);
				}

				addExpanded(out, seen, left, right);

				if (!ch.name().isEmpty() && !ch.curatorChannel()) {
					addExpanded(out, seen, ch.name(), right);
				}

				if (!channelMatchesRight) {
					addExpanded(out, seen, right, left);
				}
			}
		}

		// Handle 'Song' by Artist
		Matcher byMatcher = QUOTED_SONG_BY_ARTIST.matcher(ct);
		if (byMatcher.matches()) {
			addExpanded(out, seen, byMatcher.group(2).trim(), byMatcher.group(1).trim());
		}

		// Handle Artist "Song" / Artist「Song」/ Artist《Song》
		Matcher quoteMatcher = QUOTED_SONG_AFTER_ARTIST.matcher(ct);
		if (quoteMatcher.matches()) {
			addExpanded(out, seen, quoteMatcher.group(1).trim(), quoteMatcher.group(2).trim());
		}

		if (!structured && !ch.name().isEmpty() && !ch.curatorChannel()) {
			addExpanded(out, seen, ch.name(), ct);
		}

		// Final track-only fallback when channel is empty (e.g. Firefox) or a curator/label channel
		if (out.isEmpty() || ch.name().isEmpty() || ch.curatorChannel()) {
			String trackOnly = split.length == 2 ? stripOuterQuotes(split[1].trim()) : ct;
			for (String tv : titleVariants(trackOnly)) {
				addPair(out, seen, "", tv);
			}
		}

		return out.size() > 10 ? out.subList(0, 10) : out;
	}

	/**
	 * Produces clean display (artist, title) strings for HUD/chat when browser sessions omit the artist field
	 * or append "- YouTube" / tab counters to the title.
	 */
	public static QueryPair displayPair(String rawArtist, String rawTitle) {
		String title = rawTitle == null ? "" : normalizePunctuation(rawTitle);
		title = BROWSER_TAB_COUNTER.matcher(title).replaceFirst("").trim();
		title = PLATFORM_SUFFIX.matcher(title).replaceFirst("").trim();

		String artist = rawArtist == null ? "" : normalizePunctuation(rawArtist);
		if (!artist.isBlank()) {
			if (TOPIC_SUFFIX.matcher(artist).find()) {
				artist = TOPIC_SUFFIX.matcher(artist).replaceFirst("").trim();
			}
			return new QueryPair(artist, title);
		}

		String cleaned = cleanTitle(title);
		String[] split = TITLE_SPLITTER.split(cleaned, 2);
		if (split.length == 2 && !split[0].isBlank() && !split[1].isBlank()) {
			return new QueryPair(split[0].trim(), split[1].trim());
		}
		return new QueryPair("", cleaned.isEmpty() ? title : cleaned);
	}

	private static void addExpanded(List<QueryPair> out, Set<String> seen, String artist, String title) {
		List<String> tVars = titleVariants(title);
		List<String> aVars = artistVariants(artist);
		if (tVars.isEmpty()) return;
		if (aVars.isEmpty()) {
			for (String tv : tVars) addPair(out, seen, "", tv);
			return;
		}
		for (String tv : tVars) {
			for (String av : aVars) {
				addPair(out, seen, av, tv);
			}
		}
	}

	private static void addPair(List<QueryPair> out, Set<String> seen, String artist, String title) {
		String a = artist == null ? "" : artist.trim();
		String t = title == null ? "" : title.trim();
		if (t.isEmpty()) return;
		String key = fold(a) + "|" + fold(t);
		if (seen.add(key)) {
			out.add(new QueryPair(a, t));
		}
	}

	private static String stripTrailingParens(String value) {
		String cur = value;
		String prev;
		do {
			prev = cur;
			cur = TRAILING_PARENS.matcher(cur).replaceFirst("").trim();
		} while (!cur.equals(prev));
		return cur.isEmpty() ? value : cur;
	}

	private static String stripOuterQuotes(String value) {
		if (value == null) return "";
		String s = value.trim();
		while (s.length() >= 2 && (
			(s.startsWith("\"") && s.endsWith("\""))
				|| (s.startsWith("'") && s.endsWith("'"))
				|| (s.startsWith("\u300c") && s.endsWith("\u300d"))
				|| (s.startsWith("\u300e") && s.endsWith("\u300f"))
				|| (s.startsWith("\u300a") && s.endsWith("\u300b"))
		)) {
			s = s.substring(1, s.length() - 1).trim();
		}
		return s;
	}

	private static boolean containsFolded(List<String> list, String target) {
		String ft = fold(target);
		for (String item : list) {
			if (item.equalsIgnoreCase(target) || (!ft.isEmpty() && fold(item).equals(ft))) {
				return true;
			}
		}
		return false;
	}
}
