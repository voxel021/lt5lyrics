package com.lt5lyrics.client.music;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * KuGou Music synced LRC provider — adds extensive coverage for Chinese (Mandarin/Cantonese),
 * Khmer / Southeast Asian, Japanese, Korean, and international releases.
 */
final class KugouLyrics {
	private static final Logger LOGGER = LoggerFactory.getLogger("lt5lyrics");

	private static final HttpClient HTTP = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(4))
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();

	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
	private static volatile long cooldownUntilMs = 0L;

	static List<LyricsFetcher.LyricLine> fetch(String artist, String title, long durationSeconds) {
		if (System.currentTimeMillis() < cooldownUntilMs) { LyricsFetcher.reportProviderProblem(); return List.of(); }
		try {
			if (title == null || title.isBlank()) return List.of();
			String searchRaw = (artist == null || artist.isBlank())
				? title.trim()
				: (artist.trim() + " " + title.trim());
			String encoded = URLEncoder.encode(searchRaw, StandardCharsets.UTF_8);
			JsonObject search = request(
				"http://mobilecdn.kugou.com/api/v3/search/song?format=json&keyword=" + encoded + "&page=1&pagesize=8"
			);
			if (search == null || !search.has("data") || !search.get("data").isJsonObject()) {
				return List.of();
			}
			JsonObject data = search.getAsJsonObject("data");
			if (!data.has("info") || !data.get("info").isJsonArray()) {
				return List.of();
			}

			String wantedTitle = TrackMetadata.fold(title);
			String wantedArtist = TrackMetadata.fold(artist);
			if (wantedTitle.isEmpty()) return List.of();

			String bestHash = null;
			long bestDurMs = 0L;
			int bestScore = 0;

			JsonArray songs = data.getAsJsonArray("info");
			for (JsonElement elem : songs) {
				if (!elem.isJsonObject()) continue;
				JsonObject song = elem.getAsJsonObject();
				String songName = str(song, "songname");
				String singerName = str(song, "singername");
				String hash = str(song, "hash");
				long durSec = song.has("duration") && !song.get("duration").isJsonNull()
					? song.get("duration").getAsLong() : 0L;
				if (hash.isEmpty() || songName.isEmpty()) continue;

				String normSong = TrackMetadata.fold(TrackMetadata.cleanTitle(songName));
				String normSongRaw = TrackMetadata.fold(songName);
				String normSinger = TrackMetadata.fold(singerName);
				if (normSong.isEmpty() && normSongRaw.isEmpty()) continue;

				int score = 0;
				if (normSong.equals(wantedTitle) || normSongRaw.equals(wantedTitle)) {
					score += 6;
				} else if ((!normSong.isEmpty() && (normSong.contains(wantedTitle) || wantedTitle.contains(normSong)))
					|| (!normSongRaw.isEmpty() && (normSongRaw.contains(wantedTitle) || wantedTitle.contains(normSongRaw)))) {
					score += 3;
				} else {
					continue;
				}

				boolean artistMatched = false;
				if (!wantedArtist.isEmpty() && TrackMetadata.artistCompatible(artist, singerName)) {
					score += 5;
					artistMatched = true;
				}

				if (!wantedArtist.isEmpty() && !artistMatched) continue;
				if (durationSeconds > 0L && durSec > 0L) {
					long diff = Math.abs(durSec - durationSeconds);
					long maxDiff = Math.max(15L, Math.min(40L, Math.round(durationSeconds * 0.12)));
					if (diff > maxDiff) continue;
					if (diff <= 3L) score += 4;
					else if (diff <= 12L) score += 2;
					else if (diff <= 28L) score += 1;
				}

				if (score > bestScore) {
					bestScore = score;
					bestHash = hash;
					bestDurMs = Math.max(1000L, durSec * 1000L);
				}
			}

			if (bestHash == null || bestScore < 7) {
				return List.of();
			}

			JsonObject krcSearch = request(
				"https://krcs.kugou.com/search?ver=1&man=yes&client=mobi&keyword=&duration="
					+ bestDurMs + "&hash=" + URLEncoder.encode(bestHash, StandardCharsets.UTF_8) + "&album_audio_id="
			);
			if (krcSearch == null || !krcSearch.has("candidates") || !krcSearch.get("candidates").isJsonArray()) {
				return List.of();
			}
			JsonArray candidates = krcSearch.getAsJsonArray("candidates");
			if (candidates.isEmpty() || !candidates.get(0).isJsonObject()) {
				return List.of();
			}
			JsonObject cand = candidates.get(0).getAsJsonObject();
			String id = str(cand, "id");
			String accessKey = str(cand, "accesskey");
			if (id.isEmpty() || accessKey.isEmpty()) {
				return List.of();
			}

			JsonObject dl = request(
				"https://lyrics.kugou.com/download?ver=1&client=pc&id="
					+ URLEncoder.encode(id, StandardCharsets.UTF_8)
					+ "&accesskey=" + URLEncoder.encode(accessKey, StandardCharsets.UTF_8)
					+ "&fmt=lrc&charset=utf8"
			);
			if (dl == null) return List.of();
			String contentB64 = str(dl, "content");
			if (contentB64.isEmpty()) return List.of();

			String rawLrc = new String(Base64.getDecoder().decode(contentB64.trim()), StandardCharsets.UTF_8);
			List<LyricsFetcher.LyricLine> parsed = LyricsFetcher.parseLrc(cleanCredits(rawLrc));
			return parsed.size() >= 4 ? parsed : List.of();
		} catch (Exception e) {
			LyricsFetcher.reportProviderProblem();
			LOGGER.debug("kugou lookup failed: {}", e.toString());
			return List.of();
		}
	}

	private static String cleanCredits(String lrc) {
		StringBuilder sb = new StringBuilder();
		for (String line : lrc.split("\\R")) {
			String body = line.replaceAll("^(\\[[^\\]]*])+", "").trim();
			if (body.isEmpty()) continue;
			if (NeteaseLyrics.isCreditLine(body)) continue;
			sb.append(line).append('\n');
		}
		return sb.toString();
	}

	private static String str(JsonObject obj, String key) {
		if (obj.has(key) && !obj.get(key).isJsonNull()) {
			return obj.get(key).getAsString().trim();
		}
		return "";
	}

	private static JsonObject request(String url) throws Exception {
		HttpRequest req = HttpRequest.newBuilder()
			.uri(URI.create(url))
			.timeout(REQUEST_TIMEOUT)
			.header("User-Agent", "LT5Lyrics/" + com.lt5lyrics.client.VersionCheck.VERSION + " (Minecraft Fabric)")
			.GET()
			.build();
		HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
		if (resp.statusCode() != 200) {
			if (resp.statusCode() != 404) LyricsFetcher.reportProviderProblem();
			if (resp.statusCode() == 429) cooldownUntilMs = System.currentTimeMillis() + LyricsFetcher.retryAfterMs(resp, 30_000L);
			return null;
		}
		return JsonParser.parseString(resp.body()).getAsJsonObject();
	}

	private KugouLyrics() {
	}
}
