package com.lt5lyrics.client.music;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lt5lyrics.client.island.IslandSettings;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Native official AMLL TTML DB search + get API, not its line-only LRCLIB compatibility endpoint. */
public final class AmllLyrics {
    public static final String SOURCE = "AMLL TTML DB";
    private static final int MAX_CANDIDATES = 3;
    private static final CommunitySourceSupport.Cooldown COOLDOWN = new CommunitySourceSupport.Cooldown();
    private AmllLyrics() {}

    private record Candidate(long id, int score, int order) {}
    private record Parsed(LyricsFetcher.ProviderHit hit, float duration) {}

    static LyricsFetcher.ProviderHit fetch(String artist, String title, String originalTitle, long duration) {
        if (!IslandSettings.enableAmll || !CommunitySourceSupport.validQuery(artist, title)) return LyricsFetcher.ProviderHit.EMPTY;
        if (COOLDOWN.active()) { LyricsFetcher.reportProviderProblem(); return LyricsFetcher.ProviderHit.EMPTY; }
        try {
            String url = "https://api.amll.dev/v1/lyrics/search?musicName=" + CommunitySourceSupport.enc(title)
                + "&artistName=" + CommunitySourceSupport.enc(artist) + "&pageSize=10";
            HttpResponse<String> response = CommunitySourceSupport.get(url);
            if (response.statusCode() != 200) { COOLDOWN.failed(response); return LyricsFetcher.ProviderHit.EMPTY; }
            JsonElement root = JsonParser.parseString(response.body());
            if (!root.isJsonObject() || !root.getAsJsonObject().has("data")) {
                COOLDOWN.malformed(); return LyricsFetcher.ProviderHit.EMPTY;
            }
            List<Candidate> candidates = parseCandidates(root.getAsJsonObject(), artist, title, originalTitle);
            Parsed best = null;
            int examined = 0;
            for (Candidate candidate : candidates) {
                if (examined++ >= MAX_CANDIDATES || COOLDOWN.active()) break;
                HttpResponse<String> detail = CommunitySourceSupport.get("https://api.amll.dev/v1/lyrics/get?id=" + candidate.id());
                if (detail.statusCode() != 200) {
                    COOLDOWN.failed(detail);
                    if (detail.statusCode() != 404) break;
                    continue;
                }
                JsonElement doc = JsonParser.parseString(detail.body());
                if (!doc.isJsonObject() || !doc.getAsJsonObject().has("data")) {
                    COOLDOWN.malformed(); break;
                }
                Parsed next = parseDetail(doc.getAsJsonObject(), candidate.id(), artist, title, originalTitle, duration);
                if (next == null) continue;
                if (better(next, best, duration)) best = next;
                // Exact-duration word timing is already a strong match; avoid unnecessary requests.
                if (LyricsFetcher.hasWordTiming(next.hit().synced()) && duration > 0 && next.duration() > 0
                        && Math.abs(next.duration() - duration) <= 2.0F) break;
            }
            return best == null ? LyricsFetcher.ProviderHit.EMPTY : best.hit();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); COOLDOWN.failed(null); return LyricsFetcher.ProviderHit.EMPTY;
        } catch (Exception e) {
            COOLDOWN.failed(null); return LyricsFetcher.ProviderHit.EMPTY;
        }
    }

    private static boolean better(Parsed next, Parsed current, long expected) {
        if (current == null) return true;
        boolean a = LyricsFetcher.hasWordTiming(next.hit().synced());
        boolean b = LyricsFetcher.hasWordTiming(current.hit().synced());
        if (a != b) return a;
        if (expected > 0) {
            float d1 = next.duration() > 0 ? Math.abs(next.duration() - expected) : Float.MAX_VALUE;
            float d2 = current.duration() > 0 ? Math.abs(current.duration() - expected) : Float.MAX_VALUE;
            if (d1 != d2) return d1 < d2;
        }
        return false;
    }

    /** Exposed as pure parsing helpers for deterministic offline tests. */
    public static List<Long> parseCandidateIds(String body, String artist, String title, String originalTitle) {
        try {
            if (body == null || body.length() > CommunitySourceSupport.MAX_RESPONSE_BYTES) return List.of();
            JsonElement root = JsonParser.parseString(body);
            if (!root.isJsonObject()) return List.of();
            return parseCandidates(root.getAsJsonObject(), artist, title, originalTitle).stream().map(Candidate::id).toList();
        } catch (RuntimeException e) { return List.of(); }
    }
    private static List<Candidate> parseCandidates(JsonObject root, String artist, String title, String originalTitle) {
        List<Candidate> result = new ArrayList<>();
        try {
            if (root.get("status").getAsInt() != 200 || !root.has("data") || !root.get("data").isJsonObject()) return result;
            JsonObject data = root.getAsJsonObject("data");
            if (!data.has("items") || !data.get("items").isJsonArray()) return result;
            JsonArray items = data.getAsJsonArray("items");
            if (items.size() > 100) return result;
            Set<Long> seen = new HashSet<>();
            for (int i = 0; i < items.size(); i++) {
                if (!items.get(i).isJsonObject()) continue;
                JsonObject item = items.get(i).getAsJsonObject();
                long id = safeId(item);
                int score = matchScore(item, artist, title, originalTitle);
                if (id > 0 && score >= 0 && seen.add(id)) result.add(new Candidate(id, score, i));
            }
            result.sort(Comparator.comparingInt(Candidate::score).reversed().thenComparingInt(Candidate::order));
            return result;
        } catch (RuntimeException e) { return List.of(); }
    }
    private static int matchScore(JsonObject item, String artist, String title, String originalTitle) {
        List<String> titles = strings(item, "musicNames"), artists = strings(item, "artistNames");
        int best = -1;
        for (String t : titles) for (String a : artists) {
            if (!CommunitySourceSupport.metadataMatches(artist, title, originalTitle, a, t)) continue;
            int score = TrackMetadata.fold(title).equals(TrackMetadata.fold(t)) ? 4 : 2;
            if (TrackMetadata.fold(artist).equals(TrackMetadata.fold(a))) score += 2;
            best = Math.max(best, score);
        }
        return best;
    }
    private static List<String> strings(JsonObject o, String key) {
        if (!o.has(key) || !o.get(key).isJsonArray()) return List.of();
        JsonArray a = o.getAsJsonArray(key);
        if (a.size() > 100) return List.of();
        List<String> out = new ArrayList<>();
        for (JsonElement e : a) if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() && e.getAsString().length() <= 512)
            out.add(e.getAsString());
        return out;
    }
    private static long safeId(JsonObject item) {
        try {
            JsonElement id = item.get("id");
            if (id == null || !id.isJsonPrimitive()) return -1;
            String text = id.getAsString();
            if (!text.matches("[0-9]{1,16}")) return -1;
            long n = Long.parseLong(text);
            return n > 0 && n <= 9_007_199_254_740_991L ? n : -1;
        } catch (RuntimeException e) { return -1; }
    }
    public static LyricsFetcher.ProviderHit parseDetailResponse(String body, long expectedId, String artist, String title, String originalTitle, long duration) {
        try {
            if (body == null || body.length() > CommunitySourceSupport.MAX_RESPONSE_BYTES) return LyricsFetcher.ProviderHit.EMPTY;
            JsonElement root = JsonParser.parseString(body);
            Parsed p = root.isJsonObject() ? parseDetail(root.getAsJsonObject(), expectedId, artist, title, originalTitle, duration) : null;
            return p == null ? LyricsFetcher.ProviderHit.EMPTY : p.hit();
        } catch (RuntimeException e) { return LyricsFetcher.ProviderHit.EMPTY; }
    }
    private static Parsed parseDetail(JsonObject root, long expectedId, String artist, String title, String originalTitle, long duration) {
        try {
            if (root.get("status").getAsInt() != 200 || !root.has("data") || !root.get("data").isJsonObject()) return null;
            JsonObject data = root.getAsJsonObject("data");
            if (safeId(data) != expectedId || matchScore(data, artist, title, originalTitle) < 0
                    || !"ttml".equalsIgnoreCase(CommunitySourceSupport.str(data, "format"))) return null;
            String content = CommunitySourceSupport.str(data, "lyrics");
            if (content.isBlank() || content.length() > CommunitySourceSupport.MAX_LYRIC_CHARS) return null;
            BetterLyricsProvider.TtmlDocument doc = BetterLyricsProvider.parseTtmlDocument(content);
            if (!CommunitySourceSupport.timelineFits(duration, doc.durationSeconds(), doc.lines())) return null;
            LyricsFetcher.ProviderHit hit = CommunitySourceSupport.timedHit(doc.lines(), SOURCE);
            return hit.hasSynced() ? new Parsed(hit, doc.durationSeconds()) : null;
        } catch (RuntimeException e) { return null; }
    }
}
