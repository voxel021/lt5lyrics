package com.lt5lyrics.client.music;

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
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Official Musixmatch Developer REST v1.1 integration (https://developer.musixmatch.com/).
 *
 * - Strictly optional: only runs when the user supplies their own Musixmatch API key in Settings.
 * - Never uses scraped/unofficial desktop tokens and never logs the user's API key.
 * - Queries matcher.subtitle.get (LRC format) first, then matcher.lyrics.get as fallback.
 * - Handles restricted tracks and Musixmatch free-tier "...******* This Lyrics is NOT for Commercial use *******"
 *   preview truncation transparently without disguising a truncated preview as full synced lyrics.
 */
public final class MusixmatchLyrics {
	private static final Logger LOGGER = LoggerFactory.getLogger("lt5lyrics");

	private static final HttpClient HTTP = HttpClient.newBuilder()
		.connectTimeout(Duration.ofMillis(3500))
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();

	private static final Duration REQUEST_TIMEOUT = Duration.ofMillis(5000);
	private static volatile long cooldownUntilMs = 0L;
	private static volatile String cooldownKeyHash = "";

	private MusixmatchLyrics() {
	}

	public static void resetCooldown() {
		cooldownUntilMs = 0L;
		cooldownKeyHash = "";
	}

	public static boolean isConfiguredAndEnabled() {
		return IslandSettings.enableMusixmatch
			&& IslandSettings.musixmatchApiKey != null
			&& !IslandSettings.musixmatchApiKey.isBlank();
	}

	static LyricsFetcher.ProviderHit fetch(String artist, String title, long durationSeconds) {
		if (!isConfiguredAndEnabled()) {
			return LyricsFetcher.ProviderHit.EMPTY;
		}
		if (title == null || title.isBlank()) {
			return LyricsFetcher.ProviderHit.EMPTY;
		}
		String apiKey = IslandSettings.musixmatchApiKey.trim();
		String keyTag = Integer.toHexString(apiKey.hashCode());
		if (System.currentTimeMillis() < cooldownUntilMs && keyTag.equals(cooldownKeyHash)) {
			LyricsFetcher.reportProviderProblem();
			return LyricsFetcher.ProviderHit.EMPTY;
		}

		try {
			// 1. Try official synced LRC subtitles (available on licensed / full Musixmatch keys)
			StringBuilder subUrl = new StringBuilder("https://api.musixmatch.com/ws/1.1/matcher.subtitle.get?format=json&subtitle_format=lrc")
				.append("&q_track=").append(URLEncoder.encode(title.trim(), StandardCharsets.UTF_8));
			if (artist != null && !artist.isBlank()) {
				subUrl.append("&q_artist=").append(URLEncoder.encode(artist.trim(), StandardCharsets.UTF_8));
			}
			if (durationSeconds > 0L) {
				subUrl.append("&f_subtitle_length=").append(durationSeconds)
					.append("&f_subtitle_length_max_deviation=15");
			}
			subUrl.append("&apikey=").append(URLEncoder.encode(apiKey, StandardCharsets.UTF_8));

			JsonObject subBody = callMusixmatch(subUrl.toString(), keyTag);
			if (subBody != null && subBody.has("subtitle") && subBody.get("subtitle").isJsonObject()) {
				JsonObject sub = subBody.getAsJsonObject("subtitle");
				int restricted = sub.has("restricted") && !sub.get("restricted").isJsonNull()
					? sub.get("restricted").getAsInt() : 0;
				String lrcRaw = sub.has("subtitle_body") && !sub.get("subtitle_body").isJsonNull()
					? sub.get("subtitle_body").getAsString() : "";
				if (restricted == 0 && !lrcRaw.isBlank()) {
					String cleaned = stripCommercialDisclaimer(lrcRaw);
					List<LyricsFetcher.LyricLine> synced = LyricsFetcher.parseLrc(cleaned);
					if (synced.size() >= 3) {
						return new LyricsFetcher.ProviderHit(
							synced,
							List.of(),
							LyricsFetcher.SyncQuality.LINE_SYNCED,
							"Musixmatch"
						);
					}
				}
			}

			// If auth/quota failed on subtitle call, cooldown is already active — don't send second call
			if (System.currentTimeMillis() < cooldownUntilMs && keyTag.equals(cooldownKeyHash)) {
				return LyricsFetcher.ProviderHit.EMPTY;
			}

			// 2. Fallback to matcher.lyrics.get (plain text; free developer tier returns 30% preview)
			StringBuilder lyrUrl = new StringBuilder("https://api.musixmatch.com/ws/1.1/matcher.lyrics.get?format=json")
				.append("&q_track=").append(URLEncoder.encode(title.trim(), StandardCharsets.UTF_8));
			if (artist != null && !artist.isBlank()) {
				lyrUrl.append("&q_artist=").append(URLEncoder.encode(artist.trim(), StandardCharsets.UTF_8));
			}
			lyrUrl.append("&apikey=").append(URLEncoder.encode(apiKey, StandardCharsets.UTF_8));

			JsonObject lyrBody = callMusixmatch(lyrUrl.toString(), keyTag);
			if (lyrBody != null && lyrBody.has("lyrics") && lyrBody.get("lyrics").isJsonObject()) {
				JsonObject lyr = lyrBody.getAsJsonObject("lyrics");
				int restricted = lyr.has("restricted") && !lyr.get("restricted").isJsonNull()
					? lyr.get("restricted").getAsInt() : 0;
				String bodyText = lyr.has("lyrics_body") && !lyr.get("lyrics_body").isJsonNull()
					? lyr.get("lyrics_body").getAsString() : "";
				if (restricted == 0 && !bodyText.isBlank()) {
					boolean truncated = isTruncatedCommercialPreview(bodyText);
					if (truncated) { LyricsFetcher.reportProviderProblem(); return LyricsFetcher.ProviderHit.EMPTY; }
					String cleaned = stripCommercialDisclaimer(bodyText);
					List<LyricsFetcher.LyricLine> plain = LyricsFetcher.parsePlain(cleaned);
					if (plain.size() >= 2) {
						return new LyricsFetcher.ProviderHit(
							List.of(),
							plain,
							LyricsFetcher.SyncQuality.PLAIN_UNSYNCED,
							truncated ? "Musixmatch (Preview)" : "Musixmatch"
						);
					}
				}
			}
		} catch (Exception e) {
			LyricsFetcher.reportProviderProblem();
			LOGGER.debug("musixmatch lookup skipped: {}", LyricsFetcher.redactSecrets(e.toString()));
		}
		return LyricsFetcher.ProviderHit.EMPTY;
	}

	private static JsonObject callMusixmatch(String url, String keyTag) throws Exception {
		HttpRequest req = HttpRequest.newBuilder()
			.uri(URI.create(url))
			.timeout(REQUEST_TIMEOUT)
			.header("User-Agent", "LT5Lyrics/" + com.lt5lyrics.client.VersionCheck.VERSION + " (Minecraft 1.21.11 Fabric)")
			.header("Accept", "application/json")
			.GET()
			.build();
		HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
		if (resp.statusCode() != 200) {
			if (resp.statusCode() != 404) LyricsFetcher.reportProviderProblem();
			if (resp.statusCode() == 401 || resp.statusCode() == 402 || resp.statusCode() == 429) {
				cooldownKeyHash = keyTag;
				cooldownUntilMs = System.currentTimeMillis() + 180_000L;
			}
			return null;
		}
		JsonElement rootEl = JsonParser.parseString(resp.body());
		if (!rootEl.isJsonObject()) return null;
		JsonObject msg = rootEl.getAsJsonObject().getAsJsonObject("message");
		if (msg == null) return null;
		JsonObject header = msg.has("header") && msg.get("header").isJsonObject()
			? msg.getAsJsonObject("header") : null;
		int statusCode = header != null && header.has("status_code")
			? header.get("status_code").getAsInt() : 500;
		if (statusCode == 401 || statusCode == 402 || statusCode == 429) {
			LyricsFetcher.reportProviderProblem();
			// Invalid API key or daily limit reached — pause calls for 3 minutes until key changes or user retries
			cooldownKeyHash = keyTag;
			cooldownUntilMs = System.currentTimeMillis() + 180_000L;
			return null;
		}
		if (statusCode != 200 || !msg.has("body") || !msg.get("body").isJsonObject()) {
			return null;
		}
		return msg.getAsJsonObject("body");
	}

	public static boolean isTruncatedCommercialPreview(String raw) {
		return raw != null && (raw.contains("NOT for Commercial use") || raw.contains("...\n") || raw.trim().endsWith("..."));
	}

	public static String stripCommercialDisclaimer(String text) {
		if (text == null) return "";
		List<String> kept = new ArrayList<>();
		for (String line : text.split("\\R")) {
			String trimmed = line.trim();
			if (trimmed.contains("This Lyrics is NOT for Commercial use")) continue;
			if (trimmed.matches("^\\*+.*\\*+$")) continue;
			if (trimmed.matches("^\\(\\d{8,}\\)$")) continue;
			if (trimmed.equals("...")) continue;
			kept.add(line);
		}
		return String.join("\n", kept).trim();
	}
}
