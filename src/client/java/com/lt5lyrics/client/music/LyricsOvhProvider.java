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
 * Keyless Lyrics.ovh public REST API client (https://api.lyrics.ovh/v1/{artist}/{title}).
 *
 * Used strictly as a final plain-lyrics fallback when no synced lyrics exist on any
 * timed provider (LRCLIB, BetterLyrics, NetEase, KuGou, Lrcmux, or Musixmatch).
 */
public final class LyricsOvhProvider {
	private static final Logger LOGGER = LoggerFactory.getLogger("lt5lyrics");

	private static final HttpClient HTTP = HttpClient.newBuilder()
		.connectTimeout(Duration.ofMillis(3500))
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();

	private static final Duration REQUEST_TIMEOUT = Duration.ofMillis(4500);
	private static volatile long cooldownUntilMs = 0L;

	private LyricsOvhProvider() {
	}

	public static void resetCooldown() {
		cooldownUntilMs = 0L;
	}

	static LyricsFetcher.ProviderHit fetchPlain(String artist, String title) {
		if (!IslandSettings.enableLyricsOvh) {
			return LyricsFetcher.ProviderHit.EMPTY;
		}
		if (artist == null || artist.isBlank() || title == null || title.isBlank()) {
			return LyricsFetcher.ProviderHit.EMPTY;
		}
		if (System.currentTimeMillis() < cooldownUntilMs) {
			LyricsFetcher.reportProviderProblem();
			return LyricsFetcher.ProviderHit.EMPTY;
		}

		try {
			String encodedArtist = encodePathSegment(artist.trim());
			String encodedTitle = encodePathSegment(title.trim());
			String url = "https://api.lyrics.ovh/v1/" + encodedArtist + "/" + encodedTitle;

			HttpRequest req = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.timeout(REQUEST_TIMEOUT)
				.header("User-Agent", "LT5Lyrics/" + com.lt5lyrics.client.VersionCheck.VERSION + " (Minecraft 1.21.11 Fabric)")
				.header("Accept", "application/json")
				.GET()
				.build();

			HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
			if (resp.statusCode() == 429) {
				LyricsFetcher.reportProviderProblem();
				cooldownUntilMs = System.currentTimeMillis() + LyricsFetcher.retryAfterMs(resp, 45_000L);
				return LyricsFetcher.ProviderHit.EMPTY;
			}
			if (resp.statusCode() != 200) {
				if (resp.statusCode() != 404) LyricsFetcher.reportProviderProblem();
				return LyricsFetcher.ProviderHit.EMPTY;
			}

			JsonElement root = JsonParser.parseString(resp.body());
			if (!root.isJsonObject()) return LyricsFetcher.ProviderHit.EMPTY;
			JsonObject obj = root.getAsJsonObject();
			if (!obj.has("lyrics") || obj.get("lyrics").isJsonNull()) {
				return LyricsFetcher.ProviderHit.EMPTY;
			}
			String cleaned = cleanLyricsOvhText(obj.get("lyrics").getAsString());
			List<LyricsFetcher.LyricLine> plain = LyricsFetcher.parsePlain(cleaned);
			if (plain.size() < 2) {
				return LyricsFetcher.ProviderHit.EMPTY;
			}
			return new LyricsFetcher.ProviderHit(
				List.of(),
				plain,
				LyricsFetcher.SyncQuality.PLAIN_UNSYNCED,
				"Lyrics.ovh"
			);
		} catch (Exception e) {
			LyricsFetcher.reportProviderProblem();
			LOGGER.debug("lyrics.ovh lookup skipped: {}", e.toString());
			return LyricsFetcher.ProviderHit.EMPTY;
		}
	}

	static String cleanLyricsOvhText(String raw) {
		if (raw == null) return "";
		String[] split = raw.replace("\r\n", "\n").replace('\r', '\n').split("\n");
		List<String> out = new ArrayList<>(split.length);
		for (int i = 0; i < split.length; i++) {
			String line = split[i].trim();
			// Lyrics.ovh frequently prepends "Paroles de la chanson <Song> par <Artist>" on line 0
			if (i == 0 && line.toLowerCase(java.util.Locale.ROOT).startsWith("paroles de la chanson ")) {
				continue;
			}
			out.add(line);
		}
		return String.join("\n", out).trim();
	}

	private static String encodePathSegment(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
	}
}
