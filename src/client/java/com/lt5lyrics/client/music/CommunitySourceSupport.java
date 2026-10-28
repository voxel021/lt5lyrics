package com.lt5lyrics.client.music;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shared bounded transport and conservative matching for the two public community APIs. */
final class CommunitySourceSupport {
    static final int MAX_RESPONSE_BYTES = 2_097_152;
    static final int MAX_LYRIC_CHARS = 1_500_000;
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(3500)).followRedirects(HttpClient.Redirect.NEVER).build();
    private static final Duration TIMEOUT = Duration.ofMillis(6000);
    private static final Pattern ANNOTATION = Pattern.compile("\\(([^()]*)\\)|\\[([^\\[\\]]*)\\]|(?:\\s[-–—:]\\s)([^\\n]+)$");
    private static final Pattern LIVE = Pattern.compile("\\blive\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern REMIX = Pattern.compile("\\bremix\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern NIGHTCORE = Pattern.compile("\\bnightcore\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SPED = Pattern.compile("\\bsped[ -]?up\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SLOWED = Pattern.compile("\\bslowed(?:[ -]?down)?\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern OTHER_VERSION = Pattern.compile("\\b(acoustic|instrumental|karaoke|cover|radio edit|extended(?: version| mix)?|short(?: version| edit)?)\\b", Pattern.CASE_INSENSITIVE);

    private CommunitySourceSupport() {}

    static boolean validQuery(String artist, String title) {
        return artist != null && title != null && !artist.isBlank() && !title.isBlank()
            && artist.length() <= 256 && title.length() <= 256;
    }
    static String enc(String value) { return URLEncoder.encode(value.trim(), StandardCharsets.UTF_8); }
    static String str(JsonObject o, String key) {
        if (o == null || !o.has(key)) return "";
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() ? e.getAsString().trim() : "";
    }
    static float number(JsonObject o, String key) {
        try {
            if (o == null || !o.has(key) || !o.get(key).isJsonPrimitive()) return -1;
            float n = o.get(key).getAsFloat();
            return Float.isFinite(n) && n > 0 && n <= 86_400 ? n : -1;
        } catch (RuntimeException e) { return -1; }
    }

    /** Preserve version qualifiers even when the lookup candidate is cleaned for search. */
    static boolean versionCompatible(String originalTitle, String candidateTitle) {
        return versionFlags(originalTitle) == versionFlags(candidateTitle);
    }
    private static int versionFlags(String raw) {
        if (raw == null || raw.isBlank()) return 0;
        String s = TrackMetadata.normalizePunctuation(raw).toLowerCase(Locale.ROOT);
        int flags = 0;
        Matcher m = ANNOTATION.matcher(s);
        while (m.find()) {
            String annotation = m.group(1) != null ? m.group(1) : m.group(2) != null ? m.group(2) : m.group(3);
            flags |= flagsInAnnotation(annotation);
        }
        // Common unbracketed suffixes, without mistaking titles such as "Live and Let Die" for live recordings.
        Matcher suffix = Pattern.compile("(?i)\\s(live|acoustic|instrumental|karaoke|remix|radio edit|nightcore|sped[ -]?up|slowed)\\s*$").matcher(s);
        if (suffix.find()) flags |= flagsInAnnotation(suffix.group(1));
        return flags;
    }
    private static int flagsInAnnotation(String s) {
        if (s == null) return 0;
        int result = LIVE.matcher(s).find() ? 1 : 0;
        if (REMIX.matcher(s).find()) result |= 2;
        if (NIGHTCORE.matcher(s).find()) result |= 512;
        if (SPED.matcher(s).find()) result |= 1024;
        if (SLOWED.matcher(s).find()) result |= 2048;
        Matcher other = OTHER_VERSION.matcher(s);
        while (other.find()) {
            String token = other.group(1).toLowerCase(Locale.ROOT);
            if (token.startsWith("acoustic")) result |= 4;
            else if (token.startsWith("instrumental")) result |= 8;
            else if (token.startsWith("karaoke")) result |= 16;
            else if (token.startsWith("cover")) result |= 32;
            else if (token.startsWith("radio")) result |= 64;
            else if (token.startsWith("extended")) result |= 128;
            else if (token.startsWith("short")) result |= 256;
        }
        return result;
    }

    /** Require a real artist and a strong cleaned-title match, not a substring collision. */
    static boolean metadataMatches(String wantedArtist, String wantedTitle, String originalTitle,
            String candidateArtist, String candidateTitle) {
        if (!validQuery(wantedArtist, wantedTitle) || candidateArtist == null || candidateArtist.isBlank()
                || candidateTitle == null || candidateTitle.isBlank()) return false;
        if (!TrackMetadata.artistCompatible(wantedArtist, candidateArtist)) return false;
        String wanted = TrackMetadata.fold(TrackMetadata.cleanTitle(wantedTitle));
        String candidate = TrackMetadata.fold(TrackMetadata.cleanTitle(candidateTitle));
        return !wanted.isEmpty() && wanted.equals(candidate) && versionCompatible(originalTitle, candidateTitle);
    }

    /** The APIs may omit duration. When present, prefer a close recording, not a different edit. */
    static boolean durationCompatible(long expectedSeconds, float providerSeconds) {
        if (!Float.isFinite(providerSeconds)) return false;
        if (expectedSeconds <= 0 || providerSeconds <= 0) return true;
        float tolerance = Math.max(8.0F, Math.min(20.0F, expectedSeconds * .04F));
        return Math.abs(expectedSeconds - providerSeconds) <= tolerance;
    }
    static boolean timelineFits(long expectedSeconds, float documentDuration, List<LyricsFetcher.LyricLine> lines) {
        if (lines == null || !durationCompatible(expectedSeconds, documentDuration)) return false;
        float last = 0;
        for (LyricsFetcher.LyricLine line : lines) {
            if (line == null || !Float.isFinite(line.time()) || line.time() < 0 || line.time() > 86_400) return false;
            last = Math.max(last, line.time());
            for (LyricsFetcher.WordSpan span : line.words()) {
                if (span == null || !Float.isFinite(span.startSec()) || !Float.isFinite(span.endSec())
                        || span.startSec() < 0 || span.endSec() < span.startSec() || span.endSec() > 86_400
                        || span.charStart() < 0 || span.charEnd() <= span.charStart() || span.charEnd() > line.text().length()) return false;
                last = Math.max(last, span.endSec());
            }
        }
        if (documentDuration > 0 && last > documentDuration + 3.0F) return false;
        return expectedSeconds <= 0 || last <= expectedSeconds + 8.0F;
    }
    static LyricsFetcher.ProviderHit timedHit(List<LyricsFetcher.LyricLine> lines, String source) {
        if (lines == null || lines.size() < 2) return LyricsFetcher.ProviderHit.EMPTY;
        return new LyricsFetcher.ProviderHit(List.copyOf(lines), List.of(),
            LyricsFetcher.hasWordTiming(lines) ? LyricsFetcher.SyncQuality.WORD_SYNCED : LyricsFetcher.SyncQuality.LINE_SYNCED, source);
    }

    /** Bytes are capped while they arrive; timeout covers the full response, and redirects are not followed. */
    static HttpResponse<String> get(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT)
            .header("User-Agent", "LT5Lyrics/" + com.lt5lyrics.client.VersionCheck.VERSION + " (Minecraft Fabric)")
            .header("Accept", "application/json").GET().build();
        return HTTP.send(req, info -> {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            HttpResponse.BodySubscriber<Void> collecting = HttpResponse.BodySubscribers.ofByteArrayConsumer((Optional<byte[]> part) -> {
                if (part.isEmpty()) return;
                byte[] chunk = part.get();
                if (chunk.length > MAX_RESPONSE_BYTES - bytes.size()) throw new IllegalArgumentException("Response too large");
                bytes.writeBytes(chunk);
            });
            return HttpResponse.BodySubscribers.mapping(collecting, ignored -> bytes.toString(StandardCharsets.UTF_8));
        });
    }

    /** Each provider keeps its own cooldown. Explicit retries never clear a server's 429 wait. */
    static final class Cooldown {
        private volatile long until;
        boolean active() { return System.currentTimeMillis() < until; }
        void failed(HttpResponse<?> response) {
            int status = response == null ? 0 : response.statusCode();
            if (status == 404 || status == 200) return;
            long delay = status == 429 ? LyricsFetcher.retryAfterMs(response, 30_000L) : 15_000L;
            until = Math.max(until, System.currentTimeMillis() + delay);
            LyricsFetcher.reportProviderProblem();
        }
        void malformed() {
            until = Math.max(until, System.currentTimeMillis() + 15_000L);
            LyricsFetcher.reportProviderProblem();
        }
    }
}
