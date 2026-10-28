package com.lt5lyrics.client.music;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lt5lyrics.client.island.IslandSettings;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Locale;

/** Documented, keyless reads from the community Unison database. */
public final class UnisonLyrics {
    public static final String SOURCE = "Unison";
    public static final String CREDIT = "Lyrics from Unison (https://unison.boidu.dev)";
    private static final CommunitySourceSupport.Cooldown COOLDOWN = new CommunitySourceSupport.Cooldown();
    private UnisonLyrics() {}

    static LyricsFetcher.ProviderHit fetch(String artist, String title, String originalTitle, long duration) {
        if (!IslandSettings.enableUnison || !CommunitySourceSupport.validQuery(artist, title)) return LyricsFetcher.ProviderHit.EMPTY;
        if (COOLDOWN.active()) { LyricsFetcher.reportProviderProblem(); return LyricsFetcher.ProviderHit.EMPTY; }
        try {
            String url = "https://unison.boidu.dev/lyrics?song=" + CommunitySourceSupport.enc(title)
                + "&artist=" + CommunitySourceSupport.enc(artist) + (duration > 0 ? "&duration=" + duration : "");
            HttpResponse<String> resp = CommunitySourceSupport.get(url);
            if (resp.statusCode() != 200) { COOLDOWN.failed(resp); return LyricsFetcher.ProviderHit.EMPTY; }
            JsonElement root = JsonParser.parseString(resp.body());
            if (!root.isJsonObject() || !root.getAsJsonObject().has("success")) {
                COOLDOWN.malformed(); return LyricsFetcher.ProviderHit.EMPTY;
            }
            return parseResponse(root.getAsJsonObject(), artist, title, originalTitle, duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); COOLDOWN.failed(null); return LyricsFetcher.ProviderHit.EMPTY;
        } catch (Exception e) {
            COOLDOWN.failed(null); return LyricsFetcher.ProviderHit.EMPTY;
        }
    }

    /** Validates metadata and classifies the actual parsed timestamps, never just the advertised syncType. */
    public static LyricsFetcher.ProviderHit parseResponse(String body, String artist, String title, String originalTitle, long duration) {
        try {
            if (body == null || body.length() > CommunitySourceSupport.MAX_RESPONSE_BYTES) return LyricsFetcher.ProviderHit.EMPTY;
            JsonElement root = JsonParser.parseString(body);
            return root.isJsonObject() ? parseResponse(root.getAsJsonObject(), artist, title, originalTitle, duration) : LyricsFetcher.ProviderHit.EMPTY;
        } catch (RuntimeException e) { return LyricsFetcher.ProviderHit.EMPTY; }
    }
    private static LyricsFetcher.ProviderHit parseResponse(JsonObject root, String artist, String title, String originalTitle, long duration) {
        try {
            if (!root.has("success") || !root.get("success").getAsBoolean() || !root.has("data") || !root.get("data").isJsonObject())
                return LyricsFetcher.ProviderHit.EMPTY;
            JsonObject data = root.getAsJsonObject("data");
            if (!CommunitySourceSupport.metadataMatches(artist, title, originalTitle,
                    CommunitySourceSupport.str(data, "artist"), CommunitySourceSupport.str(data, "song")))
                return LyricsFetcher.ProviderHit.EMPTY;
            if (!CommunitySourceSupport.durationCompatible(duration, CommunitySourceSupport.number(data, "duration")))
                return LyricsFetcher.ProviderHit.EMPTY;
            String format = CommunitySourceSupport.str(data, "format").toLowerCase(Locale.ROOT);
            String content = CommunitySourceSupport.str(data, "lyrics");
            if (content.isBlank() || content.length() > CommunitySourceSupport.MAX_LYRIC_CHARS) return LyricsFetcher.ProviderHit.EMPTY;
            if ("plain".equals(format)) {
                List<LyricsFetcher.LyricLine> plain = LyricsFetcher.parsePlain(content);
                if (plain.size() < 2) return LyricsFetcher.ProviderHit.EMPTY;
                return new LyricsFetcher.ProviderHit(List.of(), List.copyOf(plain), LyricsFetcher.SyncQuality.PLAIN_UNSYNCED, SOURCE);
            }
            if ("lrc".equals(format)) {
                List<LyricsFetcher.LyricLine> lines = LyricsFetcher.parseLrc(content);
                return CommunitySourceSupport.timelineFits(duration, -1, lines)
                    ? CommunitySourceSupport.timedHit(lines, SOURCE) : LyricsFetcher.ProviderHit.EMPTY;
            }
            if ("ttml".equals(format)) {
                BetterLyricsProvider.TtmlDocument doc = BetterLyricsProvider.parseTtmlDocument(content);
                return CommunitySourceSupport.timelineFits(duration, doc.durationSeconds(), doc.lines())
                    ? CommunitySourceSupport.timedHit(doc.lines(), SOURCE) : LyricsFetcher.ProviderHit.EMPTY;
            }
            return LyricsFetcher.ProviderHit.EMPTY;
        } catch (RuntimeException e) { return LyricsFetcher.ProviderHit.EMPTY; }
    }
}
