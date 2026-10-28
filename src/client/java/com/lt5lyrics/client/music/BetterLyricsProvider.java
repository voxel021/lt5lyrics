package com.lt5lyrics.client.music;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lt5lyrics.client.island.IslandSettings;
import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * Official Better Lyrics public REST API client (https://lyrics-api-docs.boidu.dev/).
 *
 * - Serves cached TTML syllable-synced lyrics completely keyless (HTTP 200).
 * - Optionally accepts a user-configured BetterLyrics X-API-Key header for uncached tracks,
 *   and degrades gracefully on HTTP 401 when no API key is configured.
 * - Parses <p begin="..." end="..."> lines, <span begin="..." end="..."> syllable WordSpans,
 *   and <translations> / <transliterations> / ttm:role="x-translation" secondary subtitles
 *   without ever overwriting primary sung lyrics.
 */
public final class BetterLyricsProvider {
	private static final Logger LOGGER = LoggerFactory.getLogger("lt5lyrics");

	private static final HttpClient HTTP = HttpClient.newBuilder()
		.connectTimeout(Duration.ofMillis(3500))
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();

	private static final Duration REQUEST_TIMEOUT = Duration.ofMillis(5000);
	private static volatile long cooldownUntilMs = 0L;

	private BetterLyricsProvider() {
	}

	public static void resetCooldown() {
		cooldownUntilMs = 0L;
	}

	static LyricsFetcher.ProviderHit fetch(String artist, String title, long durationSeconds) {
		if (!IslandSettings.enableBetterLyrics) {
			return LyricsFetcher.ProviderHit.EMPTY;
		}
		if (title == null || title.isBlank() || artist == null || artist.isBlank()) {
			return LyricsFetcher.ProviderHit.EMPTY;
		}
		long now = System.currentTimeMillis();
		if (now < cooldownUntilMs) {
			LyricsFetcher.reportProviderProblem();
			return LyricsFetcher.ProviderHit.EMPTY;
		}

		try {
			StringBuilder q = new StringBuilder("https://api.betterlyrics.org/getLyrics?s=")
				.append(URLEncoder.encode(title.trim(), StandardCharsets.UTF_8))
				.append("&a=")
				.append(URLEncoder.encode(artist.trim(), StandardCharsets.UTF_8))
				.append("&al=");
			if (durationSeconds > 0L) {
				q.append("&d=").append(durationSeconds);
			}

			HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
				.uri(URI.create(q.toString()))
				.timeout(REQUEST_TIMEOUT)
				.header("User-Agent", "LT5Lyrics/" + com.lt5lyrics.client.VersionCheck.VERSION + " (Minecraft 1.21.11 Fabric)")
				.header("Accept", "application/json");

			String userKey = IslandSettings.betterLyricsApiKey;
			if (userKey != null && !userKey.isBlank()) {
				reqBuilder.header("X-API-Key", userKey.trim());
			}

			HttpResponse<String> resp = sendWithSingleTransientRetry(reqBuilder.GET().build());
			if (resp == null) {
				LyricsFetcher.reportProviderProblem();
				return LyricsFetcher.ProviderHit.EMPTY;
			}
			int code = resp.statusCode();
			if (code == 429) {
				LyricsFetcher.reportProviderProblem();
				long waitSec = parseRetryAfterSeconds(resp, 30L);
				cooldownUntilMs = System.currentTimeMillis() + waitSec * 1000L;
				return LyricsFetcher.ProviderHit.EMPTY;
			}
			if (code != 200) {
				if (code != 404) LyricsFetcher.reportProviderProblem();
				// 401 is expected for uncached songs when no X-API-Key is configured — fail fast cleanly
				return LyricsFetcher.ProviderHit.EMPTY;
			}

			JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
			if (json.has("score") && !json.get("score").isJsonNull()) {
				double score = json.get("score").getAsDouble();
				if (score > 0.0 && score < 0.55) {
					return LyricsFetcher.ProviderHit.EMPTY;
				}
			}
			if (!json.has("ttml") || json.get("ttml").isJsonNull()) {
				return LyricsFetcher.ProviderHit.EMPTY;
			}

			String ttml = json.get("ttml").getAsString();
			List<LyricsFetcher.LyricLine> parsed = parseTtml(ttml);
			if (parsed.size() < 2) {
				return LyricsFetcher.ProviderHit.EMPTY;
			}
			boolean hasWordSync = LyricsFetcher.hasWordTiming(parsed);
			return new LyricsFetcher.ProviderHit(
				parsed,
				List.of(),
				hasWordSync ? LyricsFetcher.SyncQuality.WORD_SYNCED : LyricsFetcher.SyncQuality.LINE_SYNCED,
				"BetterLyrics"
			);
		} catch (Exception e) {
			LyricsFetcher.reportProviderProblem();
			LOGGER.debug("betterlyrics lookup skipped: {}", LyricsFetcher.redactSecrets(e.toString()));
			return LyricsFetcher.ProviderHit.EMPTY;
		}
	}

	/**
	 * Parses W3C / Apple-style TTML XML into timed LyricLine items with word-level spans and
	 * non-destructive translations / transliterations.
	 */
	/** Parsed content plus the optional audio duration declared by the TTML body. */
	public record TtmlDocument(List<LyricsFetcher.LyricLine> lines, float durationSeconds) {}

	public static List<LyricsFetcher.LyricLine> parseTtml(String ttmlXml) {
		return parseTtmlDocument(ttmlXml).lines();
	}

	public static TtmlDocument parseTtmlDocument(String ttmlXml) {
		if (ttmlXml == null || ttmlXml.isBlank() || ttmlXml.length() > 2_097_152)
			return new TtmlDocument(List.of(), -1.0F);
		try {
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			dbf.setNamespaceAware(true);
			dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
			dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			dbf.setExpandEntityReferences(false);

			DocumentBuilder builder = dbf.newDocumentBuilder();
			Document doc = builder.parse(new InputSource(new StringReader(ttmlXml)));
			Element root = doc.getDocumentElement();
			if (root == null || !("tt".equals(root.getLocalName()) || "tt".equals(root.getTagName())))
				return new TtmlDocument(List.of(), -1.0F);
			float duration = -1.0F;
			NodeList bodies = doc.getElementsByTagNameNS("*", "body");
			if (bodies.getLength() > 0 && bodies.item(0) instanceof Element body)
				duration = parseTtmlTimeSeconds(attr(body, "dur"));

			// 1. Collect <head><metadata><translations> and <transliterations> keyed by itunes:key ("L1", "L2", ...)
			Map<String, String> lineSecondaryByKey = new HashMap<>();
			extractHeadSecondaryMap(doc.getElementsByTagNameNS("*", "translation"), lineSecondaryByKey, true);
			extractHeadSecondaryMap(doc.getElementsByTagNameNS("*", "transliteration"), lineSecondaryByKey, false);

			// 2. Parse <p> lyric lines preserving exact inter-span whitespace
			NodeList paragraphs = doc.getElementsByTagNameNS("*", "p");
			List<LyricsFetcher.LyricLine> out = new ArrayList<>(paragraphs.getLength());

			for (int i = 0; i < paragraphs.getLength(); i++) {
				Node node = paragraphs.item(i);
				if (!(node instanceof Element p)) continue;

				String beginAttr = attr(p, "begin");
				float lineStart = parseTtmlTimeSeconds(beginAttr);
				String lineKey = attr(p, "itunes:key");
				if (lineKey.isEmpty()) lineKey = attr(p, "key");

				StringBuilder mainText = new StringBuilder();
				StringBuilder inlineTrans = new StringBuilder();
				List<PendingSpan> pending = new ArrayList<>();
				float lineEnd = parseTtmlTimeSeconds(attr(p, "end"));
				collectParagraphContent(p, mainText, inlineTrans, pending, lineEnd);

				NormalizedTimedText normalized = normalizeTimedText(mainText.toString(), pending, lineEnd);
				String cleanMain = normalized.text();
				List<LyricsFetcher.WordSpan> wordSpans = normalized.spans();
				if (!LyricsFetcher.isRenderableLine(cleanMain)) {
					continue;
				}
				if (lineStart < 0.0F && !wordSpans.isEmpty()) {
					lineStart = wordSpans.get(0).startSec();
				}
				if (lineStart < 0.0F) {
					continue;
				}

				String secondary = normalizeLineSpaces(inlineTrans.toString());
				if (secondary.isEmpty() && !lineKey.isEmpty()) {
					secondary = lineSecondaryByKey.getOrDefault(lineKey, "");
				}
				if (secondary.equals(cleanMain)) {
					secondary = "";
				}

				out.add(new LyricsFetcher.LyricLine(
					lineStart,
					cleanMain,
					secondary,
					wordSpans.isEmpty() ? List.of() : List.copyOf(wordSpans)
				));
			}

			out.sort(Comparator.comparingDouble(LyricsFetcher.LyricLine::time));
			return new TtmlDocument(List.copyOf(out), duration);
		} catch (Exception e) {
			LOGGER.debug("TTML parse failed: {}", e.toString());
			return new TtmlDocument(List.of(), -1.0F);
		}
	}

	private static void extractHeadSecondaryMap(NodeList containers, Map<String, String> target, boolean overwrite) {
		if (containers == null) return;
		for (int i = 0; i < containers.getLength(); i++) {
			Node c = containers.item(i);
			if (!(c instanceof Element el)) continue;
			NodeList texts = el.getElementsByTagNameNS("*", "text");
			for (int j = 0; j < texts.getLength(); j++) {
				Node tn = texts.item(j);
				if (!(tn instanceof Element te)) continue;
				String forKey = attr(te, "for");
				if (forKey.isEmpty()) continue;
				if (!overwrite && target.containsKey(forKey)) continue;
				String content = normalizeLineSpaces(te.getTextContent());
				if (!content.isEmpty()) {
					target.put(forKey, content);
				}
			}
		}
	}

	private record PendingSpan(float startSec, float endSec, int charStart, int charEnd, float inheritedEnd) {}

	private static void collectParagraphContent(
		Element parent,
		StringBuilder mainText,
		StringBuilder inlineTrans,
		List<PendingSpan> wordSpans,
		float inheritedEnd
	) {
		NodeList children = parent.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			Node child = children.item(i);
			short type = child.getNodeType();
			if (type == Node.TEXT_NODE || type == Node.CDATA_SECTION_NODE) {
				String val = child.getNodeValue();
				if (val != null && !val.isEmpty()) mainText.append(val);
			} else if (type == Node.ELEMENT_NODE && child instanceof Element span) {
				String role = attr(span, "ttm:role");
				if (role.isEmpty()) role = attr(span, "role");
				if ("x-translation".equalsIgnoreCase(role) || "x-roman".equalsIgnoreCase(role)
						|| "x-transliteration".equalsIgnoreCase(role)) {
					String tr = span.getTextContent();
					if (tr != null && !tr.isBlank()) {
						if (!inlineTrans.isEmpty()) inlineTrans.append(' ');
						inlineTrans.append(tr.trim());
					}
					continue;
				}

				String beginStr = attr(span, "begin"), endStr = attr(span, "end");
				float begin = parseTtmlTimeSeconds(beginStr), end = parseTtmlTimeSeconds(endStr);
				boolean timed = !beginStr.isEmpty() && begin >= 0.0F
					&& (endStr.isEmpty() || end >= begin);
				int before = mainText.length(), priorSpans = wordSpans.size();
				// Recurse even through timed containers: inner syllable times and inline
				// translations must not be flattened into the parent's text/timing.
				collectParagraphContent(span, mainText, inlineTrans, wordSpans,
						timed && !endStr.isEmpty() ? end : inheritedEnd);
				if (timed && mainText.length() > before && wordSpans.size() == priorSpans) {
					// An absent end will be inferred from the next timed span or paragraph end.
					wordSpans.add(new PendingSpan(begin, endStr.isEmpty() ? -1.0F : end,
							before, mainText.length(), inheritedEnd));
				}
			}
		}
	}

	private record NormalizedTimedText(String text, List<LyricsFetcher.WordSpan> spans) {}

	/** Normalize pretty-printed TTML whitespace AND map every original UTF-16 boundary. */
	private static NormalizedTimedText normalizeTimedText(String raw, List<PendingSpan> input, float lineEnd) {
		StringBuilder out = new StringBuilder(raw.length());
		int[] map = new int[raw.length() + 1];
		for (int i = 0; i < raw.length(); i++) {
			map[i] = out.length();
			char c = raw.charAt(i);
			if (c == '\uFEFF') continue;
			if (c == '\n' || c == '\r' || c == '\t') c = ' ';
			if (c == ' ' && (out.isEmpty() || out.charAt(out.length() - 1) == ' ')) continue;
			if (out.isEmpty() && c <= ' ') continue;
			out.append(c);
		}
		map[raw.length()] = out.length();
		while (!out.isEmpty() && out.charAt(out.length() - 1) <= ' ') out.setLength(out.length() - 1);
		String clean = out.toString();
		List<LyricsFetcher.WordSpan> aligned = new ArrayList<>();
		float previousStart = -1.0F;
		for (int i = 0; i < input.size(); i++) {
			PendingSpan sp = input.get(i);
			if (!Float.isFinite(sp.startSec()) || sp.startSec() < previousStart || sp.startSec() < 0.0F) continue;
			int a = Math.clamp(map[Math.clamp(sp.charStart(), 0, raw.length())], 0, clean.length());
			int b = Math.clamp(map[Math.clamp(sp.charEnd(), 0, raw.length())], 0, clean.length());
			if (b <= a || clean.substring(a, b).isBlank()) continue;
			float end = sp.endSec();
			if (end < 0.0F) {
				float limit = sp.inheritedEnd() >= sp.startSec() ? sp.inheritedEnd()
					: lineEnd >= sp.startSec() ? lineEnd : sp.startSec() + 0.45F;
				end = i + 1 < input.size() && input.get(i + 1).startSec() >= sp.startSec()
					? Math.min(limit, input.get(i + 1).startSec()) : limit;
			}
			if (!Float.isFinite(end) || end < sp.startSec()) continue;
			aligned.add(new LyricsFetcher.WordSpan(sp.startSec(), end, a, b));
			previousStart = sp.startSec();
		}
		return new NormalizedTimedText(clean, List.copyOf(aligned));
	}

	private static String normalizeLineSpaces(String s) {
		if (s == null) return "";
		return s.replace("\uFEFF", "")
			.replaceAll("[\\t\\r\\n]+", " ")
			.replaceAll(" {2,}", " ")
			.trim();
	}

	static float parseTtmlTimeSeconds(String raw) {
		if (raw == null || raw.isBlank()) return -1.0F;
		String s = raw.trim();
		if (s.endsWith("ms")) {
			try {
				return safeTime(Float.parseFloat(s.substring(0, s.length() - 2).trim()) / 1000.0F);
			} catch (NumberFormatException e) {
				return -1.0F;
			}
		}
		if (s.endsWith("s")) {
			s = s.substring(0, s.length() - 1).trim();
		}
		String[] parts = s.split(":");
		try {
			if (parts.length == 3) {
				return safeTime(Integer.parseInt(parts[0]) * 3600.0F
					+ Integer.parseInt(parts[1]) * 60.0F
					+ Float.parseFloat(parts[2]));
			} else if (parts.length == 2) {
				return safeTime(Integer.parseInt(parts[0]) * 60.0F + Float.parseFloat(parts[1]));
			} else if (parts.length == 1) {
				return safeTime(Float.parseFloat(parts[0]));
			}
			return -1.0F;
		} catch (NumberFormatException e) {
			return -1.0F;
		}
	}

	private static float safeTime(float value) {
		return Float.isFinite(value) && value >= 0.0F && value <= 86_400.0F ? value : -1.0F;
	}

	private static String attr(Element el, String name) {
		if (el.hasAttribute(name)) {
			return el.getAttribute(name).trim();
		}
		return "";
	}

	private static long parseRetryAfterSeconds(HttpResponse<?> resp, long fallbackSec) {
		Optional<String> header = resp.headers().firstValue("Retry-After");
		if (header.isPresent()) {
			try {
				return Math.max(5L, Math.min(300L, Long.parseLong(header.get().trim())));
			} catch (NumberFormatException ignored) {
			}
		}
		return fallbackSec;
	}

	private static HttpResponse<String> sendWithSingleTransientRetry(HttpRequest req) {
		for (int attempt = 0; attempt < 2; attempt++) {
			try {
				HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
				if (resp.statusCode() < 500 || attempt == 1) {
					return resp;
				}
			} catch (Exception e) {
				if (attempt == 1) {
					LOGGER.debug("betterlyrics transport failed: {}", LyricsFetcher.redactSecrets(e.toString()));
					return null;
				}
			}
		}
		return null;
	}
}
