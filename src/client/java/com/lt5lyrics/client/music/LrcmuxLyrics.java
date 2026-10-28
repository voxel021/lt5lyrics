package com.lt5lyrics.client.music;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lt5lyrics.client.island.IslandSettings;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Open-source Lrcmux public REST API client (https://lrcmux.dev/docs).
 *
 * - Keyless public API (60 req/min window; cache hits are free).
 * - Strong international coverage across Khmer/SEA, Arabic, Cyrillic, Latin, and indie YouTube Music tracks.
 * - Verifies matched track.artist, track.title, and track.duration against TrackMetadata guards so
 *   wrong-song collisions are rejected.
 * - Preserves exact distinction between word-synced ("word"), line-synced ("line"), and plain ("none") lyrics.
 */
public final class LrcmuxLyrics {
	private static final Logger LOGGER = LoggerFactory.getLogger("lt5lyrics");

	private static final HttpClient HTTP = HttpClient.newBuilder()
		.connectTimeout(Duration.ofMillis(3500))
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();

	private static final Duration REQUEST_TIMEOUT = Duration.ofMillis(6000);
	private static volatile long cooldownUntilMs = 0L;

	private LrcmuxLyrics() {
	}

	public static void resetCooldown() {
		cooldownUntilMs = 0L;
	}

	static LyricsFetcher.ProviderHit fetch(String artist, String title, long durationSeconds) {
		if (!IslandSettings.enableLrcmux) {
			return LyricsFetcher.ProviderHit.EMPTY;
		}
		if (title == null || title.isBlank()) {
			return LyricsFetcher.ProviderHit.EMPTY;
		}
		if (System.currentTimeMillis() < cooldownUntilMs) {
			LyricsFetcher.reportProviderProblem();
			return LyricsFetcher.ProviderHit.EMPTY;
		}

		try {
			StringBuilder url = new StringBuilder("https://api.lrcmux.dev/get?title=")
				.append(URLEncoder.encode(title.trim(), StandardCharsets.UTF_8));
			if (artist != null && !artist.isBlank()) {
				url.append("&artist=").append(URLEncoder.encode(artist.trim(), StandardCharsets.UTF_8));
			}
			if (durationSeconds > 0L) {
				url.append("&duration=").append(durationSeconds);
			}
			// Only the documented LRCLIB/KuGou sources, and only cached results.
			// Do not ask the aggregator to fetch Genius, YouTube Music, or unvetted sources.
			url.append("&sources=lrclib%2Ckugou&fetch=cache");

			HttpRequest req = HttpRequest.newBuilder()
				.uri(URI.create(url.toString()))
				.timeout(REQUEST_TIMEOUT)
				.header("User-Agent", "LT5Lyrics/" + com.lt5lyrics.client.VersionCheck.VERSION + " (Minecraft 1.21.11 Fabric)")
				.header("Accept", "application/json")
				.GET()
				.build();

			HttpResponse<String> resp = sendOnceOrRetryTransient(req);
			if (resp == null) {
				LyricsFetcher.reportProviderProblem();
				return LyricsFetcher.ProviderHit.EMPTY;
			}
			int status = resp.statusCode();
			if (status == 429) {
				LyricsFetcher.reportProviderProblem();
				Optional<String> retryAfter = resp.headers().firstValue("Retry-After");
				long sec = 30L;
				if (retryAfter.isPresent()) {
					try {
						sec = Math.max(5L, Math.min(300L, Long.parseLong(retryAfter.get().trim())));
					} catch (NumberFormatException ignored) {
					}
				}
				cooldownUntilMs = System.currentTimeMillis() + sec * 1000L;
				return LyricsFetcher.ProviderHit.EMPTY;
			}
			if (status != 200) {
				if (status != 404) LyricsFetcher.reportProviderProblem();
				return LyricsFetcher.ProviderHit.EMPTY;
			}

			return parseJsonResponse(resp.body(), artist, title, durationSeconds);
		} catch (Exception e) {
			LyricsFetcher.reportProviderProblem();
			LOGGER.debug("lrcmux lookup skipped: {}", e.toString());
			return LyricsFetcher.ProviderHit.EMPTY;
		}
	}

	public static LyricsFetcher.ProviderHit parseJsonResponse(
		String body,
		String wantedArtist,
		String wantedTitle,
		long wantedDurationSec
	) {
		if (body == null || body.isBlank()) return LyricsFetcher.ProviderHit.EMPTY;
		try {
			JsonElement rootEl = JsonParser.parseString(body);
			if (!rootEl.isJsonObject()) return LyricsFetcher.ProviderHit.EMPTY;
			JsonObject root = rootEl.getAsJsonObject();

			// 1. Validate matched track metadata against artist/title/duration collision guard
			if (!root.has("track") || !root.get("track").isJsonObject()) return LyricsFetcher.ProviderHit.EMPTY;
			JsonObject track = root.getAsJsonObject("track");
			String candTitle = str(track, "title");
			String candArtist = str(track, "artist");
			long candDur = track.has("duration") && !track.get("duration").isJsonNull()
				? Math.round(track.get("duration").getAsDouble()) : 0L;
			if (!LyricsFetcher.passesTrackGuard(wantedArtist, wantedTitle, wantedDurationSec,
				candArtist, candTitle, candDur)) return LyricsFetcher.ProviderHit.EMPTY;

			String level = "line";
			if (!root.has("meta") || !root.get("meta").isJsonObject()) return LyricsFetcher.ProviderHit.EMPTY;
			if (root.has("meta") && root.get("meta").isJsonObject()) {
				JsonObject meta = root.getAsJsonObject("meta");
				String upstream = str(meta, "source");
				if (meta.has("source") && meta.get("source").isJsonObject()) {
					JsonObject source = meta.getAsJsonObject("source");
					upstream = str(source, "id");
					if (upstream.isBlank()) upstream = str(source, "name");
				}
				if (!"lrclib".equalsIgnoreCase(upstream) && !"kugou".equalsIgnoreCase(upstream)) {
					return LyricsFetcher.ProviderHit.EMPTY;
				}
				String l = str(meta, "level");
				if (!l.isEmpty()) level = l.toLowerCase(java.util.Locale.ROOT);
			}

			if (!root.has("lines") || !root.get("lines").isJsonArray()) {
				return LyricsFetcher.ProviderHit.EMPTY;
			}
			JsonArray linesArr = root.getAsJsonArray("lines");
			if (linesArr.isEmpty()) {
				return LyricsFetcher.ProviderHit.EMPTY;
			}

			if ("none".equals(level)) {
				List<LyricsFetcher.LyricLine> plain = new ArrayList<>(linesArr.size());
				for (JsonElement el : linesArr) {
					if (!el.isJsonObject()) continue;
					String text = str(el.getAsJsonObject(), "text");
					if (!text.isEmpty() && LyricsFetcher.isRenderableLine(text)) {
						plain.add(new LyricsFetcher.LyricLine(-1.0F, text));
					}
				}
				if (plain.size() < 2) return LyricsFetcher.ProviderHit.EMPTY;
				return new LyricsFetcher.ProviderHit(
					List.of(),
					plain,
					LyricsFetcher.SyncQuality.PLAIN_UNSYNCED,
					"Lrcmux"
				);
			}

			List<LyricsFetcher.LyricLine> synced = new ArrayList<>(linesArr.size());
			for (JsonElement el : linesArr) {
				if (!el.isJsonObject()) continue;
				JsonObject item = el.getAsJsonObject();
				String text = str(item, "text");
				if (text.isEmpty() || !LyricsFetcher.isRenderableLine(text)) continue;
				if (!item.has("start") || item.get("start").isJsonNull()) continue;

				float startSec = item.get("start").getAsFloat() / 1000.0F;
				if (!Float.isFinite(startSec) || startSec < 0.0F) continue;

				List<LyricsFetcher.WordSpan> wordSpans = List.of();
				if (item.has("words") && item.get("words").isJsonArray()) {
					JsonArray wArr = item.getAsJsonArray("words");
					List<LyricsFetcher.WordSpan> built = new ArrayList<>(Math.min(wArr.size(), 2000));
					StringBuilder reconstructed = new StringBuilder();
					boolean valid = wArr.size() <= 2000;
					float previousStart = -1.0F;
					for (JsonElement we : wArr) {
						if (!valid || !we.isJsonObject()) { valid = false; break; }
						JsonObject wo = we.getAsJsonObject();
						if (!wo.has("text") || !wo.get("text").isJsonPrimitive()
								|| !wo.has("start") || wo.get("start").isJsonNull()
								|| !wo.has("end") || wo.get("end").isJsonNull()) { valid = false; break; }
						String wt = wo.get("text").getAsString();
						float ws = wo.get("start").getAsFloat() / 1000.0F;
						float wend = wo.get("end").getAsFloat() / 1000.0F;
						if (!Float.isFinite(ws) || !Float.isFinite(wend) || ws < 0.0F
								|| ws < previousStart || wend < ws || reconstructed.length() + wt.length() > 4608) {
							valid = false; break;
						}
						int cs = reconstructed.length();
						reconstructed.append(wt);
						int ce = reconstructed.length();
						if (ce > cs) built.add(new LyricsFetcher.WordSpan(ws, wend, cs, ce));
						previousStart = ws;
					}
					String rawWords = reconstructed.toString();
					String recTrim = rawWords.trim();
					// Never replace the provider's full lyric with incomplete/mismatched words.
					if (valid && !built.isEmpty() && !recTrim.isEmpty() && recTrim.equals(text)) {
						int leading = rawWords.indexOf(recTrim);
						List<LyricsFetcher.WordSpan> aligned = new ArrayList<>();
						for (LyricsFetcher.WordSpan span : built) {
							int begin = Math.clamp(span.charStart() - leading, 0, text.length());
							int end = Math.clamp(span.charEnd() - leading, 0, text.length());
							if (end > begin) aligned.add(new LyricsFetcher.WordSpan(span.startSec(), span.endSec(), begin, end));
						}
						wordSpans = List.copyOf(aligned);
					}
				}

				String trans = str(item, "translation");
				if (trans.equals(text)) trans = "";

				if (!synced.isEmpty()) {
					LyricsFetcher.LyricLine prev = synced.get(synced.size() - 1);
					if (Math.abs(prev.time() - startSec) <= 0.04F && !prev.text().equals(text) && !prev.hasTranslation()) {
						synced.set(synced.size() - 1, new LyricsFetcher.LyricLine(
							prev.time(), prev.text(), text, prev.words()
						));
						continue;
					}
				}
				synced.add(new LyricsFetcher.LyricLine(startSec, text, trans, wordSpans));
			}

			if (synced.size() < 3) {
				return LyricsFetcher.ProviderHit.EMPTY;
			}
			synced.sort(Comparator.comparingDouble(LyricsFetcher.LyricLine::time));
			LyricsFetcher.SyncQuality quality = LyricsFetcher.hasWordTiming(synced)
				? LyricsFetcher.SyncQuality.WORD_SYNCED
				: LyricsFetcher.SyncQuality.LINE_SYNCED;
			return new LyricsFetcher.ProviderHit(synced, List.of(), quality, "Lrcmux");
		} catch (Exception e) {
			return LyricsFetcher.ProviderHit.EMPTY;
		}
	}

	private static String str(JsonObject obj, String key) {
		if (obj != null && obj.has(key) && !obj.get(key).isJsonNull() && obj.get(key).isJsonPrimitive()) {
			return obj.get(key).getAsString().trim();
		}
		return "";
	}

	private static HttpResponse<String> sendOnceOrRetryTransient(HttpRequest req) {
		for (int attempt = 0; attempt < 2; attempt++) {
			try {
				HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
				if (resp.statusCode() < 500 || attempt == 1) {
					return resp;
				}
			} catch (Exception e) {
				if (attempt == 1) return null;
			}
		}
		return null;
	}
}
