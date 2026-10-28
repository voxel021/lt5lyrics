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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class NeteaseLyrics {
	private static final Logger LOGGER = LoggerFactory.getLogger("lt5lyrics");

	private static final HttpClient HTTP = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(4))
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();

	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
	private static volatile long cooldownUntilMs = 0L;

	private static final Pattern CREDIT_LINE = Pattern.compile(
		"(?i)^(?:\\u4f5c\\u8bcd|\\u4f5c\\u66f2|\\u7de8\\u66f2|\\u7f16\\u66f2|\\u8a5e|\\u66f2|\\u5236\\u4f5c\\u4eba|\\u88fd\\u4f5c\\u4eba|"
			+ "\\u6df7\\u97f3|\\u6bcd\\u5e26|\\u76e3\\u88fd|\\u76d1\\u5236|\\u6f14\\u5531|\\u548c\\u58f0|\\u5f55\\u97f3|\\u51fa\\u54c1|"
			+ "lyricist|composer|arranger|producer|mixed\\s*by|mastered\\s*by|op|sp)\\s*[:\\uff1a].*"
	);

	static List<LyricsFetcher.LyricLine> fetch(String artist, String title, long durationSeconds) {
		if (System.currentTimeMillis() < cooldownUntilMs) { LyricsFetcher.reportProviderProblem(); return List.of(); }
		try {
			if (title == null || title.isBlank()) return List.of();
			String searchRaw = (artist == null || artist.isBlank()) ? title.trim() : (artist.trim() + " " + title.trim());
			String query = URLEncoder.encode(searchRaw, StandardCharsets.UTF_8);
			JsonObject search = request("https://music.163.com/api/search/get?s=" + query + "&type=1&limit=10");
			if (search == null || !search.has("result") || search.get("result").isJsonNull()) return List.of();
			JsonObject result = search.getAsJsonObject("result");
			if (!result.has("songs") || result.get("songs").isJsonNull()) return List.of();

			String wantedTitle = TrackMetadata.fold(title);
			String wantedArtist = TrackMetadata.fold(artist);
			if (wantedTitle.isEmpty()) return List.of();

			long bestId = -1L;
			int bestScore = 0;

			JsonArray songs = result.getAsJsonArray("songs");
			for (JsonElement entry : songs) {
				JsonObject song = entry.getAsJsonObject();
				String name = song.has("name") && !song.get("name").isJsonNull() ? song.get("name").getAsString() : "";
				List<String> artistNames = extractArtistNames(song);
				long length = song.has("duration") && !song.get("duration").isJsonNull()
					? song.get("duration").getAsLong() / 1000L : 0L;

				int score = 0;
				String normName = TrackMetadata.fold(name);
				if (normName.isEmpty()) continue;

				if (normName.equals(wantedTitle)) score += 6;
				else if (normName.contains(wantedTitle) || wantedTitle.contains(normName)) score += 3;
				else continue;

				boolean artistMatched = false;
				if (!wantedArtist.isEmpty()) {
					for (String perf : artistNames) {
						if (TrackMetadata.artistCompatible(artist, perf)) {
							score += 5;
							artistMatched = true;
							break;
						}
					}
				}

				if (!wantedArtist.isEmpty() && !artistMatched) continue;
				if (durationSeconds > 0 && length > 0) {
					long diff = Math.abs(length - durationSeconds);
					long maxDiff = Math.max(15L, Math.min(40L, Math.round(durationSeconds * 0.12)));
					if (diff > maxDiff) continue;
					if (diff <= 3) score += 4;
					else if (diff <= 10) score += 2;
					else if (diff <= 25) score += 1;
				}
				if (score > bestScore) {
					bestScore = score;
					bestId = song.get("id").getAsLong();
				}
			}

			int minRequired = 8;
			if (bestId < 0 || bestScore < minRequired) {
				return List.of();
			}

			JsonObject lyric = request("https://music.163.com/api/song/lyric?id=" + bestId + "&lv=1&kv=1&tv=1&yv=1");
			if (lyric == null || !lyric.has("lrc") || lyric.get("lrc").isJsonNull()) return List.of();
			JsonObject lrc = lyric.getAsJsonObject("lrc");
			if (!lrc.has("lyric") || lrc.get("lyric").isJsonNull()) return List.of();

			String mainLrc = clean(lrc.get("lyric").getAsString());
			String transLrc = "";
			if (lyric.has("tlyric") && lyric.get("tlyric").isJsonObject()) {
				JsonObject tObj = lyric.getAsJsonObject("tlyric");
				if (tObj.has("lyric") && !tObj.get("lyric").isJsonNull()) {
					transLrc = clean(tObj.get("lyric").getAsString());
				}
			}

			List<LyricsFetcher.LyricLine> lines = LyricsFetcher.parseLrcWithTranslation(mainLrc, transLrc);
			if (lines.size() < 4) return List.of();
			return lines;
		} catch (Exception e) {
			LyricsFetcher.reportProviderProblem();
			LOGGER.debug("netease lookup failed: {}", e.toString());
			return List.of();
		}
	}

	private static List<String> extractArtistNames(JsonObject song) {
		List<String> out = new ArrayList<>();
		if (!song.has("artists") || !song.get("artists").isJsonArray()) return out;
		for (JsonElement el : song.getAsJsonArray("artists")) {
			if (!el.isJsonObject()) continue;
			JsonObject a = el.getAsJsonObject();
			if (a.has("name") && !a.get("name").isJsonNull()) {
				out.add(a.get("name").getAsString());
			}
			if (a.has("trans") && !a.get("trans").isJsonNull()) {
				String tr = a.get("trans").getAsString();
				if (!tr.isBlank()) out.add(tr);
			}
			if (a.has("alias") && a.get("alias").isJsonArray()) {
				for (JsonElement al : a.getAsJsonArray("alias")) {
					if (!al.isJsonNull()) out.add(al.getAsString());
				}
			}
		}
		return out;
	}

	static boolean isCreditLine(String text) {
		return text != null && CREDIT_LINE.matcher(text.trim()).matches();
	}

	private static String clean(String lrc) {
		StringBuilder builder = new StringBuilder();
		for (String line : lrc.split("\\R")) {
			String text = line.replaceAll("^(\\[[^\\]]*])+", "").trim();
			if (text.isEmpty()) continue;
			if (isCreditLine(text)) continue;
			builder.append(line).append('\n');
		}
		return builder.toString();
	}

	private static JsonObject request(String url) throws Exception {
		HttpRequest request = HttpRequest.newBuilder()
			.uri(URI.create(url))
			.timeout(REQUEST_TIMEOUT)
			.header("User-Agent", "LT5Lyrics/" + com.lt5lyrics.client.VersionCheck.VERSION + " (Minecraft Fabric)")
			.header("Referer", "https://music.163.com")
			.GET()
			.build();
		HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() != 200) {
			if (response.statusCode() != 404) LyricsFetcher.reportProviderProblem();
			if (response.statusCode() == 429) cooldownUntilMs = System.currentTimeMillis() + LyricsFetcher.retryAfterMs(response, 30_000L);
			return null;
		}
		return JsonParser.parseString(response.body()).getAsJsonObject();
	}

	private NeteaseLyrics() {
	}
}
