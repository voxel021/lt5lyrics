package com.lt5lyrics.client.music;

import java.io.IOException;

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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LyricsFetcher {
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("lt5lyrics");

	private static final HttpClient HTTP = HttpClient.newBuilder()
		.connectTimeout(Duration.ofMillis(3800))
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();

	private static final Duration REQUEST_TIMEOUT = Duration.ofMillis(5000);

	private static final Pattern LRC_TIMESTAMP = Pattern.compile("\\[(\\d+):(\\d+)(?:[.:](\\d+))?]");
	private static final Pattern ENHANCED_WORD_TAG = Pattern.compile("<(\\d+):(\\d+)(?:[.:](\\d+))?>");
	private static final Pattern LRC_OFFSET = Pattern.compile("(?im)^\\s*\\[offset\\s*:\\s*([+-]?\\d{1,9})\\s*]\\s*$");
	private static final Pattern SECRET_REDACT = Pattern.compile("(?i)(apikey|api_key|x-api-key|token|key)=([^&\\s\"']+)");

	private static final long POSITIVE_CACHE_TTL_MS = 30L * 60L * 1000L;
	private static final long NEGATIVE_CACHE_TTL_MS = 90L * 1000L;
	private static final long UNAVAILABLE_CACHE_TTL_MS = 15L * 1000L;

	private record CachedResult(Result result, long expiresAtMs) {}

	private static final Map<String, CachedResult> POSITIVE_CACHE = new ConcurrentHashMap<>();
	private static final Map<String, CachedResult> NEGATIVE_CACHE = new ConcurrentHashMap<>();
	private static final Map<String, CompletableFuture<Result>> IN_FLIGHT = new ConcurrentHashMap<>();
	private static volatile long lrclibCooldownUntilMs = 0L;
	private static final ThreadLocal<Boolean> LRCLIB_IO_ERROR = ThreadLocal.withInitial(() -> Boolean.FALSE);
	private static final ThreadLocal<Boolean> PROVIDER_PROBLEM = ThreadLocal.withInitial(() -> Boolean.FALSE);

	static void reportProviderProblem() { PROVIDER_PROBLEM.set(Boolean.TRUE); }

	static long retryAfterMs(HttpResponse<?> response, long fallbackMs) {
		String value = response.headers().firstValue("Retry-After").orElse("").trim();
		try { return Math.clamp(Long.parseLong(value) * 1000L, 5000L, 300_000L); }
		catch (NumberFormatException ignored) {
			try {
				long wait = java.time.ZonedDateTime.parse(value, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
					.toInstant().toEpochMilli() - System.currentTimeMillis();
				return Math.clamp(wait, 5000L, 300_000L);
			} catch (Exception ignoredDate) { return fallbackMs; }
		}
	}

	public enum SyncQuality {
		WORD_SYNCED("Word-Synced", true, true),
		LINE_SYNCED("Timed Lyrics", true, true),
		ESTIMATED_PACE("Plain (Est. Pace)", false, true),
		PLAIN_UNSYNCED("Plain Lyrics", false, false),
		NOT_FOUND("No Match", false, false),
		PROVIDER_UNAVAILABLE("Provider Unavailable", false, false);

		public final String badge;
		public final boolean genuineSync;
		public final boolean playableInWorld;

		SyncQuality(String badge, boolean genuineSync, boolean playableInWorld) {
			this.badge = badge;
			this.genuineSync = genuineSync;
			this.playableInWorld = playableInWorld;
		}
	}

	/**
	 * Optional syllable/word timing span within a LyricLine (from BetterLyrics TTML, Lrcmux word-sync,
	 * or Enhanced LRC <mm:ss.xx> tags).
	 */
	public record WordSpan(float startSec, float endSec, int charStart, int charEnd) {
	}

	public record LyricLine(float time, String text, String translation, List<WordSpan> words) {
		public LyricLine(float time, String text) {
			this(time, text, "", List.of());
		}

		public LyricLine(float time, String text, String translation) {
			this(time, text, translation == null ? "" : translation, List.of());
		}

		public LyricLine {
			text = text == null ? "" : text;
			translation = translation == null ? "" : translation;
			words = words == null ? List.of() : words;
		}

		public boolean hasTranslation() {
			return !translation.isBlank();
		}

		public boolean hasWordSpans() {
			return !words.isEmpty();
		}
	}

	/** True only when at least one line has multiple, distinctly timed text segments. */
	public static boolean hasWordTiming(List<LyricLine> lines) {
		if (lines == null) return false;
		for (LyricLine line : lines) {
			float first = -1.0F;
			for (WordSpan span : line.words()) {
				if (span.charStart() < 0 || span.charEnd() <= span.charStart()
						|| span.charEnd() > line.text().length() || !Float.isFinite(span.startSec())
						|| !Float.isFinite(span.endSec()) || span.startSec() < 0.0F || span.endSec() < span.startSec()) continue;
				if (first < 0.0F) first = span.startSec();
				else if (span.startSec() > first + 0.001F) return true;
			}
		}
		return false;
	}

	public record ProviderHit(
		List<LyricLine> synced,
		List<LyricLine> plain,
		SyncQuality quality,
		String source
	) {
		public static final ProviderHit EMPTY = new ProviderHit(
			List.of(), List.of(), SyncQuality.NOT_FOUND, "None"
		);

		public boolean hasSynced() {
			return synced != null && !synced.isEmpty();
		}

		public boolean hasPlain() {
			return plain != null && !plain.isEmpty();
		}
	}

	public record Result(
		List<LyricLine> lines,
		List<LyricLine> rawLines,
		boolean synced,
		boolean playable,
		String source,
		SyncQuality syncQuality
	) {
		public static final Result EMPTY = new Result(
			List.of(), List.of(), false, false, "No match", SyncQuality.NOT_FOUND
		);

		public static final Result UNAVAILABLE = new Result(
			List.of(), List.of(), false, false, "Provider unavailable", SyncQuality.PROVIDER_UNAVAILABLE
		);

		public String statusMessage() {
			return switch (syncQuality) {
				case WORD_SYNCED -> "Word timing where available · " + source;
				case LINE_SYNCED -> "Line timed; words estimated · " + source;
				case ESTIMATED_PACE -> "Plain lyrics found (estimated pace) · " + source;
				case PLAIN_UNSYNCED -> "Plain lyrics found (unsynced) · " + source;
				case PROVIDER_UNAVAILABLE -> "No lyrics found; a provider is unavailable or disabled";
				case NOT_FOUND -> "No matching lyrics found";
			};
		}

		public Result(List<LyricLine> lines, boolean synced) {
			this(
				lines,
				lines,
				synced,
				synced && !lines.isEmpty(),
				lines.isEmpty() ? "Lyrics not found" : "LRCLIB",
				lines.isEmpty() ? SyncQuality.NOT_FOUND : (synced ? SyncQuality.LINE_SYNCED : SyncQuality.PLAIN_UNSYNCED)
			);
		}

		public Result(List<LyricLine> lines, boolean synced, String source) {
			this(
				lines,
				lines,
				synced,
				synced && !lines.isEmpty(),
				source,
				lines.isEmpty() ? SyncQuality.NOT_FOUND : (synced ? SyncQuality.LINE_SYNCED : SyncQuality.PLAIN_UNSYNCED)
			);
		}

		public Result(List<LyricLine> lines, List<LyricLine> rawLines, boolean synced, boolean playable, String source) {
			this(
				lines,
				rawLines,
				synced,
				playable,
				source,
				(lines.isEmpty() && rawLines.isEmpty()) ? SyncQuality.NOT_FOUND
					: (synced ? SyncQuality.LINE_SYNCED
						: (playable ? SyncQuality.ESTIMATED_PACE : SyncQuality.PLAIN_UNSYNCED))
			);
		}

		public boolean isEmpty() {
			return lines.isEmpty() && rawLines.isEmpty();
		}
	}

	/**
	 * Masks API keys or tokens before writing any diagnostic string to logs.
	 */
	public static String redactSecrets(String raw) {
		if (raw == null || raw.isEmpty()) return "";
		String out = SECRET_REDACT.matcher(raw).replaceAll("$1=***");
		String mx = IslandSettings.musixmatchApiKey;
		if (mx != null && mx.length() >= 4) {
			out = out.replace(mx, "***");
		}
		String bl = IslandSettings.betterLyricsApiKey;
		if (bl != null && bl.length() >= 4) {
			out = out.replace(bl, "***");
		}
		return out;
	}

	/**
	 * Clears positive and negative lyric caches and resets temporary provider cooldowns
	 * so a manual Retry / Reload immediately performs a fresh lookup.
	 */
	public static void clearCache() {
		POSITIVE_CACHE.clear();
		NEGATIVE_CACHE.clear();
		lrclibCooldownUntilMs = 0L;
		BetterLyricsProvider.resetCooldown();
		LrcmuxLyrics.resetCooldown();
		MusixmatchLyrics.resetCooldown();
		LyricsOvhProvider.resetCooldown();
	}

	private static String buildCacheKey(List<TrackMetadata.QueryPair> candidates, long durationSeconds, String originalTitle) {
		TrackMetadata.QueryPair primary = candidates.get(0);
		int providerMask = (IslandSettings.enableLrclib ? 1 : 0)
			| (IslandSettings.enableBetterLyrics ? 2 : 0)
			| (IslandSettings.enableNetease ? 4 : 0)
			| (IslandSettings.enableKugou ? 8 : 0)
			| (IslandSettings.enableLrcmux ? 16 : 0)
			| (MusixmatchLyrics.isConfiguredAndEnabled() ? 32 : 0)
			| (IslandSettings.enableLyricsOvh ? 64 : 0)
			| (IslandSettings.autoPaceUnsyncedLyrics ? 128 : 0)
			| (IslandSettings.preferWordTiming ? 256 : 0)
			| (IslandSettings.enableUnison ? 512 : 0)
			| (IslandSettings.enableAmll ? 1024 : 0);
		String priorityOrder = String.join(">", IslandSettings.normalizedProviderPriority());
		return TrackMetadata.fold(primary.artist()) + "|"
			+ TrackMetadata.fold(primary.title()) + "|"
			+ TrackMetadata.fold(originalTitle) + "|"
			+ (durationSeconds / 5L) + "|"
			+ providerMask + "|"
			+ priorityOrder;
	}

	public static Result fetch(String artist, String title, long durationSeconds) {
		return fetch("", artist, title, durationSeconds);
	}

	public static Result fetch(String owner, String artist, String title, long durationSeconds) {
		return fetch(owner, artist, title, durationSeconds, false);
	}

	public static Result fetch(String owner, String artist, String title, long durationSeconds, boolean bypassCache) {
		return fetch(owner, artist, title, durationSeconds, bypassCache, null);
	}

	/** Optional early result: show the best line-timed match while checking for word timings. */
	public static Result fetch(String owner, String artist, String title, long durationSeconds,
			boolean bypassCache, Consumer<Result> onProvisional) {
		if (title == null || title.isBlank()) {
			return Result.EMPTY;
		}

		List<TrackMetadata.QueryPair> candidates = TrackMetadata.buildCandidates(owner, artist, title);
		if (candidates.isEmpty()) {
			return Result.EMPTY;
		}

		String cacheKey = buildCacheKey(candidates, durationSeconds, title);
		long now = System.currentTimeMillis();

		if (!bypassCache) {
			CachedResult pos = POSITIVE_CACHE.get(cacheKey);
			if (pos != null) {
				if (now < pos.expiresAtMs()) {
					return pos.result();
				}
				POSITIVE_CACHE.remove(cacheKey);
			}
			CachedResult neg = NEGATIVE_CACHE.get(cacheKey);
			if (neg != null) {
				if (now < neg.expiresAtMs()) {
					return neg.result();
				}
				NEGATIVE_CACHE.remove(cacheKey);
			}
		} else {
			POSITIVE_CACHE.remove(cacheKey);
			NEGATIVE_CACHE.remove(cacheKey);
		}

		// Coalesce concurrent identical lookups so duplicate triggers never fire duplicate HTTP cascades
		CompletableFuture<Result> mine = new CompletableFuture<>();
		CompletableFuture<Result> existing = IN_FLIGHT.putIfAbsent(cacheKey, mine);
		if (existing != null) {
			try {
				return existing.get(25L, java.util.concurrent.TimeUnit.SECONDS);
			} catch (Exception e) {
				return Result.UNAVAILABLE;
			}
		}

		try {
			Result resolved = executeProviderCascade(candidates, durationSeconds, title, IslandSettings.preferWordTiming, onProvisional);
			long finishedAt = System.currentTimeMillis();
			if (!resolved.isEmpty()) {
				if (POSITIVE_CACHE.size() > 128) POSITIVE_CACHE.clear();
				POSITIVE_CACHE.put(cacheKey, new CachedResult(resolved, finishedAt + POSITIVE_CACHE_TTL_MS));
				NEGATIVE_CACHE.remove(cacheKey);
			} else {
				if (NEGATIVE_CACHE.size() > 128) NEGATIVE_CACHE.clear();
				long ttl = resolved.syncQuality() == SyncQuality.PROVIDER_UNAVAILABLE
					? UNAVAILABLE_CACHE_TTL_MS
					: NEGATIVE_CACHE_TTL_MS;
				NEGATIVE_CACHE.put(cacheKey, new CachedResult(resolved, finishedAt + ttl));
			}
			mine.complete(resolved);
			return resolved;
		} catch (Throwable t) {
			mine.complete(Result.UNAVAILABLE);
			return Result.UNAVAILABLE;
		} finally {
			IN_FLIGHT.remove(cacheKey, mine);
		}
	}

	private static Result executeProviderCascade(List<TrackMetadata.QueryPair> candidates, long durationSeconds,
			String originalTitle, boolean preferWords, Consumer<Result> onProvisional) {
		PROVIDER_PROBLEM.set(Boolean.FALSE);
		LRCLIB_IO_ERROR.set(Boolean.FALSE);
		List<LyricLine> bestPlain = List.of();
		String bestPlainSource = "LRCLIB";
		ProviderHit bestTimed = ProviderHit.EMPTY;
		int enabledSyncedOrHybridProviders = 0;
		int unavailableProviders = 0;

		List<String> priorityOrder = IslandSettings.normalizedProviderPriority();

		// Search enabled providers in the user's order. The first line-synced hit remains
		// the fallback; actual word timing wins only when the preference is enabled.
		// No disabled provider or user-supplied local/manual choice is bypassed.
		for (String providerId : priorityOrder) {
			switch (providerId) {
				case "lrclib" -> {
					if (!IslandSettings.enableLrclib) continue;
					enabledSyncedOrHybridProviders++;
					if (System.currentTimeMillis() < lrclibCooldownUntilMs) {
						unavailableProviders++;
						reportProviderProblem();
						continue;
					}
					LRCLIB_IO_ERROR.set(Boolean.FALSE);
					ProviderHit hit = queryLrclibPrimary(candidates, durationSeconds);
					if (hit.hasSynced()) {
						if (!preferWords) return toSyncedResult(hit);
						if (hasWordTiming(hit.synced()) && compatibleUpgrade(bestTimed, hit))
							return toSyncedResult(preserveTranslations(bestTimed, hit));
						if (!bestTimed.hasSynced()) { bestTimed = hit; announceProvisional(hit, onProvisional); }
					}
					if (bestPlain.isEmpty() && hit.hasPlain()) {
						bestPlain = hit.plain();
						bestPlainSource = hit.source();
					} else if (Boolean.TRUE.equals(LRCLIB_IO_ERROR.get())) {
						unavailableProviders++;
					}
				}
				case "unison", "amll" -> {
					if (!IslandSettings.isProviderEnabled(providerId)) continue;
					enabledSyncedOrHybridProviders++;
					int communityLimit = Math.min(2, candidates.size());
					for (int i = 0; i < communityLimit; i++) {
						TrackMetadata.QueryPair pair = candidates.get(i);
						if (pair.artist().isBlank()) continue;
						ProviderHit hit = "unison".equals(providerId)
							? UnisonLyrics.fetch(pair.artist(), pair.title(), originalTitle, durationSeconds)
							: AmllLyrics.fetch(pair.artist(), pair.title(), originalTitle, durationSeconds);
						if (hit.hasSynced()) {
							if (!preferWords) return toSyncedResult(hit);
							if (hasWordTiming(hit.synced()) && compatibleUpgrade(bestTimed, hit))
								return toSyncedResult(preserveTranslations(bestTimed, hit));
							if (!bestTimed.hasSynced()) { bestTimed = hit; announceProvisional(hit, onProvisional); }
							if (!hasWordTiming(hit.synced())) break;
						}
						if (bestPlain.isEmpty() && hit.hasPlain()) {
							bestPlain = hit.plain(); bestPlainSource = hit.source();
						}
					}
				}
				case "betterlyrics" -> {
					if (!IslandSettings.enableBetterLyrics) continue;
					enabledSyncedOrHybridProviders++;
					int blLimit = Math.min(2, candidates.size());
					for (int i = 0; i < blLimit; i++) {
						TrackMetadata.QueryPair pair = candidates.get(i);
						if (pair.artist().isBlank()) continue;
						ProviderHit hit = BetterLyricsProvider.fetch(pair.artist(), pair.title(), durationSeconds);
						if (hit.hasSynced()) {
							if (!preferWords) return toSyncedResult(hit);
							if (hasWordTiming(hit.synced()) && compatibleUpgrade(bestTimed, hit))
								return toSyncedResult(preserveTranslations(bestTimed, hit));
							if (!bestTimed.hasSynced()) { bestTimed = hit; announceProvisional(hit, onProvisional); }
							if (!hasWordTiming(hit.synced())) break;
						}
					}
				}
				case "netease" -> {
					if (!IslandSettings.enableNetease || bestTimed.hasSynced()) continue;
					enabledSyncedOrHybridProviders++;
					int neteaseLimit = Math.min(3, candidates.size());
					for (int i = 0; i < neteaseLimit; i++) {
						TrackMetadata.QueryPair pair = candidates.get(i);
						List<LyricLine> found = NeteaseLyrics.fetch(pair.artist(), pair.title(), durationSeconds);
						if (!found.isEmpty()) {
							ProviderHit hit = new ProviderHit(found, List.of(), SyncQuality.LINE_SYNCED, "NetEase");
							if (!preferWords || hasWordTiming(found)) return toSyncedResult(hit);
							if (!bestTimed.hasSynced()) { bestTimed = hit; announceProvisional(hit, onProvisional); }
							break;
						}
					}
				}
				case "kugou" -> {
					if (!IslandSettings.enableKugou || bestTimed.hasSynced()) continue;
					enabledSyncedOrHybridProviders++;
					int kugouLimit = Math.min(3, candidates.size());
					for (int i = 0; i < kugouLimit; i++) {
						TrackMetadata.QueryPair pair = candidates.get(i);
						List<LyricLine> found = KugouLyrics.fetch(pair.artist(), pair.title(), durationSeconds);
						if (!found.isEmpty()) {
							ProviderHit hit = new ProviderHit(found, List.of(), SyncQuality.LINE_SYNCED, "KuGou");
							if (!preferWords || hasWordTiming(found)) return toSyncedResult(hit);
							if (!bestTimed.hasSynced()) { bestTimed = hit; announceProvisional(hit, onProvisional); }
							break;
						}
					}
				}
				case "lrcmux" -> {
					if (!IslandSettings.enableLrcmux) continue;
					enabledSyncedOrHybridProviders++;
					int muxLimit = Math.min(3, candidates.size());
					for (int i = 0; i < muxLimit; i++) {
						TrackMetadata.QueryPair pair = candidates.get(i);
						ProviderHit hit = LrcmuxLyrics.fetch(pair.artist(), pair.title(), durationSeconds);
						if (hit.hasSynced()) {
							if (!preferWords) return toSyncedResult(hit);
							if (hasWordTiming(hit.synced()) && compatibleUpgrade(bestTimed, hit))
								return toSyncedResult(preserveTranslations(bestTimed, hit));
							if (!bestTimed.hasSynced()) { bestTimed = hit; announceProvisional(hit, onProvisional); }
							if (!hasWordTiming(hit.synced())) break;
						}
						if (bestPlain.isEmpty() && hit.hasPlain()) {
							bestPlain = hit.plain();
							bestPlainSource = hit.source();
						}
					}
				}
				case "musixmatch" -> {
					if (!MusixmatchLyrics.isConfiguredAndEnabled() || bestTimed.hasSynced()) continue;
					enabledSyncedOrHybridProviders++;
					int mxLimit = Math.min(2, candidates.size());
					for (int i = 0; i < mxLimit; i++) {
						TrackMetadata.QueryPair pair = candidates.get(i);
						ProviderHit hit = MusixmatchLyrics.fetch(pair.artist(), pair.title(), durationSeconds);
						if (hit.hasSynced()) {
							if (!preferWords) return toSyncedResult(hit);
							if (hasWordTiming(hit.synced()) && compatibleUpgrade(bestTimed, hit))
								return toSyncedResult(preserveTranslations(bestTimed, hit));
							if (!bestTimed.hasSynced()) { bestTimed = hit; announceProvisional(hit, onProvisional); }
							if (!hasWordTiming(hit.synced())) break;
						}
						if (bestPlain.isEmpty() && hit.hasPlain()) {
							bestPlain = hit.plain();
							bestPlainSource = hit.source();
						}
					}
				}
				case "lyricsovh" -> {
					// Plain-only provider: record fallback only if higher-priority plain wasn't already found,
					// while continuing Pass A so lower-ordered synced providers still get a chance to provide timed LRC!
					if (!IslandSettings.enableLyricsOvh || bestTimed.hasSynced()) continue;
					enabledSyncedOrHybridProviders++;
					if (bestPlain.isEmpty()) {
						int ovhLimit = Math.min(2, candidates.size());
						for (int i = 0; i < ovhLimit; i++) {
							TrackMetadata.QueryPair pair = candidates.get(i);
							if (pair.artist().isBlank()) continue;
							ProviderHit ovh = LyricsOvhProvider.fetchPlain(pair.artist(), pair.title());
							if (ovh.hasPlain()) {
								bestPlain = ovh.plain();
								bestPlainSource = ovh.source();
								break;
							}
						}
					}
				}
				default -> {
				}
			}
		}

		if (bestTimed.hasSynced()) {
			// Exact LRCLIB entries can be line-only while another very close matching
			// community entry includes a Lyricsfile. Try one bounded search, but keep
			// the already displayed match unless its word result is comparable.
			if (preferWords && IslandSettings.enableLrclib && !candidates.isEmpty()
					&& System.currentTimeMillis() >= lrclibCooldownUntilMs) {
				TrackMetadata.QueryPair first = candidates.get(0);
				if (!first.artist().isBlank()) {
					ProviderHit alternate = searchStructuredLrclib(first.artist(), first.title(), durationSeconds, false);
					if (alternate.hasSynced() && hasWordTiming(alternate.synced()) && compatibleUpgrade(bestTimed, alternate))
						return toSyncedResult(preserveTranslations(bestTimed, alternate));
				}
			}
			return toSyncedResult(bestTimed);
		}

		// Secondary LRCLIB free-text script-split search when LRCLIB is enabled and no synced lyrics matched yet
		if (IslandSettings.enableLrclib && System.currentTimeMillis() >= lrclibCooldownUntilMs) {
			int freeTextLimit = Math.min(3, candidates.size());
			for (int i = 0; i < freeTextLimit; i++) {
				TrackMetadata.QueryPair cand = candidates.get(i);
				ProviderHit qFallback = searchFreeTextLrclib(cand.artist(), cand.title(), durationSeconds);
				if (qFallback.hasSynced()) {
					return toSyncedResult(qFallback);
				}
				if (bestPlain.isEmpty() && qFallback.hasPlain()) {
					bestPlain = qFallback.plain();
					bestPlainSource = qFallback.source();
				}
			}
		}

		// Pass B: Honest handling of plain unsynced lyrics — never pretend plain text is genuinely synced!
		if (!bestPlain.isEmpty()) {
			if (IslandSettings.autoPaceUnsyncedLyrics) {
				List<LyricLine> paced = autoPacePlain(bestPlain, durationSeconds);
				if (!paced.isEmpty()) {
					return new Result(
						paced,
						bestPlain,
						false,
						true,
						bestPlainSource + " (Estimated Pace)",
						SyncQuality.ESTIMATED_PACE
					);
				}
			}
			return new Result(
				bestPlain,
				bestPlain,
				false,
				false,
				bestPlainSource + " (Plain Text)",
				SyncQuality.PLAIN_UNSYNCED
			);
		}

		if (enabledSyncedOrHybridProviders == 0 || unavailableProviders >= enabledSyncedOrHybridProviders
				|| Boolean.TRUE.equals(PROVIDER_PROBLEM.get()) || Boolean.TRUE.equals(LRCLIB_IO_ERROR.get())) {
			return Result.UNAVAILABLE;
		}
		return Result.EMPTY;
	}

	private static ProviderHit queryLrclibPrimary(List<TrackMetadata.QueryPair> candidates, long durationSeconds) {
		ProviderHit bestPlainHit = ProviderHit.EMPTY;
		int durExactLimit = Math.min(2, candidates.size());
		int exactLimit = Math.min(3, candidates.size());

		if (durationSeconds > 0L) {
			for (int i = 0; i < durExactLimit; i++) {
				TrackMetadata.QueryPair pair = candidates.get(i);
				if (pair.artist().isBlank()) continue;
				ProviderHit hit = exactLrclib(pair.artist(), pair.title(), durationSeconds);
				if (hit.hasSynced()) return hit;
				if (!bestPlainHit.hasPlain() && hit.hasPlain()) bestPlainHit = hit;
			}
		}

		for (int i = 0; i < exactLimit; i++) {
			TrackMetadata.QueryPair pair = candidates.get(i);
			if (pair.artist().isBlank()) continue;
			ProviderHit hit = exactLrclib(pair.artist(), pair.title(), 0L);
			if (hit.hasSynced()) return hit;
			if (!bestPlainHit.hasPlain() && hit.hasPlain()) bestPlainHit = hit;
		}

		int searchLimit = Math.min(4, candidates.size());
		Set<String> triedTrackOnly = new HashSet<>();
		for (int i = 0; i < searchLimit; i++) {
			TrackMetadata.QueryPair pair = candidates.get(i);
			boolean allowTrackFallback = triedTrackOnly.add(TrackMetadata.fold(pair.title()));
			ProviderHit found = searchStructuredLrclib(pair.artist(), pair.title(), durationSeconds, allowTrackFallback);
			if (found.hasSynced()) return found;
			if (!bestPlainHit.hasPlain() && found.hasPlain()) bestPlainHit = found;
		}
		return bestPlainHit;
	}

	/** A word upgrade should resemble the already matched song, not an unrelated edition. */
	public static boolean comparableTimedLyrics(List<LyricLine> reference, List<LyricLine> candidate) {
		if (reference == null || reference.isEmpty() || candidate == null || candidate.isEmpty()) return false;
		int needed = Math.min(2, Math.min(reference.size(), candidate.size()));
		int found = 0, left = 0;
		// Provider parsers return lines sorted by time. Limit each comparison to a
		// small time window rather than quadratic scans of large untrusted results.
		for (LyricLine c : candidate) {
			String a = TrackMetadata.fold(c.text());
			if (a.length() < 3) continue;
			while (left < reference.size() && reference.get(left).time() < c.time() - 12.0F) left++;
			for (int j = left; j < reference.size() && reference.get(j).time() <= c.time() + 12.0F; j++) {
				String b = TrackMetadata.fold(reference.get(j).text());
				int shorter = Math.min(a.length(), b.length()), longer = Math.max(a.length(), b.length());
				boolean similar = a.equals(b) || (shorter >= 8 && shorter * 2 >= longer && (a.contains(b) || b.contains(a)));
				if (similar) { found++; break; }
			}
			if (found >= needed) return true;
		}
		return false;
	}

	public static boolean compatibleUpgrade(ProviderHit previous, ProviderHit candidate) {
		if (!previous.hasSynced() || comparableTimedLyrics(previous.synced(), candidate.synced())) return true;
		// A single tiny timestamped placeholder at the start is not enough evidence to
		// veto a fuller, independently title/artist/duration-validated community hit.
		// Never use this exception for providers without those metadata guards.
		if ((UnisonLyrics.SOURCE.equals(candidate.source()) || AmllLyrics.SOURCE.equals(candidate.source()))
				&& previous.synced().size() == 1 && candidate.synced().size() >= 3) {
			LyricLine only = previous.synced().get(0);
			return only.time() <= 1.0F && only.text().strip().length() <= 12;
		}
		return false;
	}

	/** Keep an existing translation on a matching line when the word source has none. */
	public static ProviderHit preserveTranslations(ProviderHit previous, ProviderHit candidate) {
		if (!previous.hasSynced() || !candidate.hasSynced()) return candidate;
		List<LyricLine> result = new ArrayList<>(candidate.synced().size());
		boolean copiedUnisonTranslation = false;
		int left = 0;
		for (LyricLine line : candidate.synced()) {
			String translation = line.translation();
			if (translation.isBlank()) {
				String folded = TrackMetadata.fold(line.text());
				float nearest = 3.0F;
				while (left < previous.synced().size() && previous.synced().get(left).time() < line.time() - 3.0F) left++;
				for (int j = left; j < previous.synced().size() && previous.synced().get(j).time() <= line.time() + 3.0F; j++) {
					LyricLine prior = previous.synced().get(j);
					float diff = Math.abs(line.time() - prior.time());
					if (prior.hasTranslation() && !folded.isEmpty() && folded.equals(TrackMetadata.fold(prior.text())) && diff <= nearest) {
						translation = prior.translation(); nearest = diff;
						if (previous.source().contains(UnisonLyrics.SOURCE)) copiedUnisonTranslation = true;
					}
				}
			}
			result.add(new LyricLine(line.time(), line.text(), translation, line.words()));
		}
		String source = candidate.source();
		if (copiedUnisonTranslation && !source.contains(UnisonLyrics.SOURCE)) source += " + Unison translation";
		return new ProviderHit(List.copyOf(result), candidate.plain(), candidate.quality(), source);
	}

	private static void announceProvisional(ProviderHit hit, Consumer<Result> callback) {
		if (callback != null) {
			try { callback.accept(toSyncedResult(hit)); } catch (RuntimeException ignored) { }
		}
	}

	private static Result toSyncedResult(ProviderHit hit) {
		List<LyricLine> lines = hit.synced();
		boolean wordSync = hasWordTiming(lines);
		SyncQuality quality = wordSync ? SyncQuality.WORD_SYNCED : SyncQuality.LINE_SYNCED;
		String label = wordSync ? (hit.source() + " (Word-Sync)") : hit.source();
		return new Result(lines, lines, true, true, label, quality);
	}

	/**
	 * Shared track-matching guard preventing wrong-song or wrong-artist collisions on short/common titles.
	 */
	public static boolean passesTrackGuard(
		String wantedArtistRaw,
		String wantedTitleRaw,
		long wantedDurationSeconds,
		String candArtistRaw,
		String candTitleRaw,
		long candDurationSeconds
	) {
		String wantedTitle = TrackMetadata.fold(wantedTitleRaw);
		String wantedArtist = TrackMetadata.fold(wantedArtistRaw);
		String entryTrack = TrackMetadata.fold(candTitleRaw);
		String entryTrackClean = TrackMetadata.fold(TrackMetadata.cleanTitle(candTitleRaw));
		String entryArtist = TrackMetadata.fold(candArtistRaw);

		if (wantedTitle.isEmpty() || (entryTrack.isEmpty() && entryTrackClean.isEmpty())) {
			return false;
		}

		boolean exactTitle = entryTrack.equals(wantedTitle) || entryTrackClean.equals(wantedTitle);
		boolean partialTitle = !exactTitle
			&& ((entryTrack.contains(wantedTitle) || wantedTitle.contains(entryTrack))
			|| (!entryTrackClean.isEmpty()
				&& (entryTrackClean.contains(wantedTitle) || wantedTitle.contains(entryTrackClean))));
		if (!exactTitle && !partialTitle) {
			return false;
		}

		boolean artistMatched = !wantedArtist.isEmpty()
			&& TrackMetadata.artistCompatible(wantedArtistRaw, candArtistRaw);
		if (!wantedArtist.isEmpty() && !artistMatched) return false;
		if (wantedArtist.isEmpty() && !exactTitle) return false;
		if (partialTitle) {
			int shorter = Math.min(wantedTitle.length(), entryTrack.length());
			int longer = Math.max(wantedTitle.length(), entryTrack.length());
			if (shorter < 4 || shorter * 2 < longer) return false;
		}
		if (wantedDurationSeconds > 0L && candDurationSeconds > 0L) {
			long diff = Math.abs(candDurationSeconds - wantedDurationSeconds);
			long maxDifference = Math.max(15L, Math.min(40L, Math.round(wantedDurationSeconds * 0.12)));
			if (diff > maxDifference) return false;
		}

		return true;
	}

    /** A read-only inspection of one source. Counts, not lyric text, are retained. */
    public enum ComparisonStatus { WORD, LINE, PLAIN, NO_MATCH, UNAVAILABLE, DISABLED, NEEDS_KEY, NEEDS_METADATA }
    public record SourceComparison(String id, String name, ComparisonStatus status, int wordLines, int totalLines) {
        public String description() {
            return switch (status) {
                case WORD -> "Word timing on " + wordLines + "/" + totalLines + " lines";
                case LINE -> "Line timing; words estimated (" + totalLines + " lines)";
                case PLAIN -> "Plain text; no timing";
                case NO_MATCH -> "No matching result found";
                case UNAVAILABLE -> "Unavailable or rate-limited";
                case DISABLED -> "Off in Sources";
                case NEEDS_KEY -> "Needs your personal API key";
                case NEEDS_METADATA -> "Song title and artist needed";
            };
        }
    }
    public static SourceComparison comparisonFromHit(String id, ProviderHit hit, boolean unavailable) {
        String name = IslandSettings.providerDisplayName(id);
        if (hit != null && hit.hasSynced()) {
            int timed = 0;
            for (LyricLine line : hit.synced()) if (hasWordTiming(List.of(line))) timed++;
            return new SourceComparison(id, name, timed > 0 ? ComparisonStatus.WORD : ComparisonStatus.LINE,
                    timed, hit.synced().size());
        }
        if (hit != null && hit.hasPlain()) return new SourceComparison(id, name, ComparisonStatus.PLAIN, 0, hit.plain().size());
        return new SourceComparison(id, name, unavailable ? ComparisonStatus.UNAVAILABLE : ComparisonStatus.NO_MATCH, 0, 0);
    }

    /**
     * Optional read-only comparison: one current-song lookup per enabled source, off the render thread.
     * The callback receives only counts/status, not lyric text, and no selection or tracker state is changed.
     */
    public static void compareSources(String owner, String artist, String title, long duration,
            java.util.function.BooleanSupplier keepGoing, Consumer<SourceComparison> onEach) {
        List<TrackMetadata.QueryPair> candidates = TrackMetadata.buildCandidates(owner, artist, title);
        TrackMetadata.QueryPair pair = candidates.isEmpty() ? new TrackMetadata.QueryPair("", "") : candidates.get(0);
        List<String> order = List.copyOf(IslandSettings.normalizedProviderPriority());
        try {
            for (String id : order) {
                if (keepGoing != null && !keepGoing.getAsBoolean()) break;
                String name = IslandSettings.providerDisplayName(id);
                if (!IslandSettings.isProviderEnabled(id)) {
                    onEach.accept(new SourceComparison(id, name, ComparisonStatus.DISABLED, 0, 0)); continue;
                }
                if ("musixmatch".equals(id) && !MusixmatchLyrics.isConfiguredAndEnabled()) {
                    onEach.accept(new SourceComparison(id, name, ComparisonStatus.NEEDS_KEY, 0, 0)); continue;
                }
                if (pair.artist().isBlank() || pair.title().isBlank()) {
                    onEach.accept(new SourceComparison(id, name, ComparisonStatus.NEEDS_METADATA, 0, 0)); continue;
                }
                PROVIDER_PROBLEM.set(Boolean.FALSE); LRCLIB_IO_ERROR.set(Boolean.FALSE);
                ProviderHit hit = ProviderHit.EMPTY;
                try {
                    switch (id) {
                        case "lrclib" -> {
                            if (System.currentTimeMillis() < lrclibCooldownUntilMs) reportProviderProblem();
                            else {
                                hit = exactLrclib(pair.artist(), pair.title(), duration);
                                if (!hit.hasSynced() && !hit.hasPlain() && !Boolean.TRUE.equals(LRCLIB_IO_ERROR.get())
                                        && System.currentTimeMillis() >= lrclibCooldownUntilMs)
                                    hit = searchStructuredLrclib(pair.artist(), pair.title(), duration, false);
                            }
                        }
                        case "unison" -> hit = UnisonLyrics.fetch(pair.artist(), pair.title(), title, duration);
                        case "amll" -> hit = AmllLyrics.fetch(pair.artist(), pair.title(), title, duration);
                        case "betterlyrics" -> hit = BetterLyricsProvider.fetch(pair.artist(), pair.title(), duration);
                        case "lrcmux" -> hit = LrcmuxLyrics.fetch(pair.artist(), pair.title(), duration);
                        case "musixmatch" -> hit = MusixmatchLyrics.fetch(pair.artist(), pair.title(), duration);
                        case "lyricsovh" -> hit = LyricsOvhProvider.fetchPlain(pair.artist(), pair.title());
                        case "netease" -> hit = CommunitySourceSupport.timedHit(NeteaseLyrics.fetch(pair.artist(), pair.title(), duration), "NetEase");
                        case "kugou" -> hit = CommunitySourceSupport.timedHit(KugouLyrics.fetch(pair.artist(), pair.title(), duration), "KuGou");
                        default -> { }
                    }
                } catch (RuntimeException e) { reportProviderProblem(); }
                boolean problem = Boolean.TRUE.equals(PROVIDER_PROBLEM.get()) || Boolean.TRUE.equals(LRCLIB_IO_ERROR.get());
                if (keepGoing == null || keepGoing.getAsBoolean()) onEach.accept(comparisonFromHit(id, hit, problem));
            }
        } finally { PROVIDER_PROBLEM.remove(); LRCLIB_IO_ERROR.remove(); }
    }

    /** Explicit manual selection uses LRCLIB's documented search API only, if the user enabled it. */
    public record ManualMatch(String title, String artist, String album, long duration, Result result) {}

    public static List<ManualMatch> searchManualLrclib(String artist, String title, long duration) throws Exception {
        if (!IslandSettings.enableLrclib) throw new IOException("Enable LRCLIB in Sources before searching.");
        if (title == null || title.isBlank()) throw new IOException("This player did not provide a song title.");
        StringBuilder q = new StringBuilder("track_name=").append(URLEncoder.encode(title, StandardCharsets.UTF_8));
        if (artist != null && !artist.isBlank()) q.append("&artist_name=").append(URLEncoder.encode(artist, StandardCharsets.UTF_8));
        HttpResponse<String> resp = sendLrclibGet("https://lrclib.net/api/search?" + q);
        if (resp == null) throw new IOException("LRCLIB is temporarily rate-limited; please try again later.");
        if (resp.statusCode() != 200) throw new IOException("LRCLIB search returned HTTP " + resp.statusCode());
        if (resp.body().length() > 4_194_304) throw new IOException("LRCLIB response is too large.");
        return parseManualLrclib(resp.body(), duration, IslandSettings.autoPaceUnsyncedLyrics);
    }

    public static List<ManualMatch> parseManualLrclib(String body, long duration, boolean autoPace) throws IOException {
        try {
            JsonElement root = JsonParser.parseString(body);
            if (!root.isJsonArray()) throw new IOException("Unexpected LRCLIB search response");
            List<ManualMatch> out = new ArrayList<>();
            for (JsonElement element : root.getAsJsonArray()) {
                if (!element.isJsonObject() || out.size() >= 20) continue;
                JsonObject e = element.getAsJsonObject();
                String title = getString(e, "trackName"), artist = getString(e, "artistName"), album = getString(e, "albumName");
                if (title == null || title.isBlank()) continue;
                long length = e.has("duration") && !e.get("duration").isJsonNull() ? Math.max(0L, Math.round(e.get("duration").getAsDouble())) : 0L;
                String timed = getString(e, "syncedLyrics"), plain = getString(e, "plainLyrics");
                List<LyricLine> lines = parseLrclibTimed(e);
                Result result;
                if (!lines.isEmpty()) {
                    boolean words = hasWordTiming(lines);
                    result = new Result(lines, lines, true, true, "LRCLIB (selected)", words ? SyncQuality.WORD_SYNCED : SyncQuality.LINE_SYNCED);
                } else {
                    List<LyricLine> raw = plain == null ? List.of() : parsePlain(plain);
                    if (raw.isEmpty()) continue;
                    List<LyricLine> paced = autoPace ? autoPacePlain(raw, duration > 0 ? duration : length) : List.of();
                    result = paced.isEmpty() ? new Result(raw, raw, false, false, "LRCLIB (Plain Text)", SyncQuality.PLAIN_UNSYNCED)
                        : new Result(paced, raw, false, true, "LRCLIB (Estimated Pace)", SyncQuality.ESTIMATED_PACE);
                }
                out.add(new ManualMatch(title, artist == null ? "" : artist, album == null ? "" : album, length, result));
            }
            return List.copyOf(out);
        } catch (IllegalStateException | IllegalArgumentException e) { throw new IOException("Invalid LRCLIB search response", e); }
    }

	private static ProviderHit exactLrclib(String artist, String title, long durationSeconds) {
		try {
			String q = "artist_name=" + URLEncoder.encode(artist, StandardCharsets.UTF_8)
				+ "&track_name=" + URLEncoder.encode(title, StandardCharsets.UTF_8);
			if (durationSeconds > 0L) {
				q += "&duration=" + durationSeconds;
			}
			HttpResponse<String> resp = sendLrclibGet("https://lrclib.net/api/get?" + q);
			if (resp == null || resp.statusCode() != 200) {
				return ProviderHit.EMPTY;
			}
			JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
			String matchedArtist = getString(json, "artistName");
			String matchedTitle = getString(json, "trackName");
			long matchedDuration = json.has("duration") && !json.get("duration").isJsonNull()
				? Math.round(json.get("duration").getAsDouble()) : 0L;
			if (!passesTrackGuard(artist, title, durationSeconds, matchedArtist, matchedTitle, matchedDuration)) return ProviderHit.EMPTY;
			List<LyricLine> parsed = parseLrclibTimed(json);
			if (!parsed.isEmpty()) {
				return new ProviderHit(parsed, List.of(), hasWordTiming(parsed)
					? SyncQuality.WORD_SYNCED : SyncQuality.LINE_SYNCED, "LRCLIB");
			}
			String plain = getString(json, "plainLyrics");
			if (plain != null && !plain.isBlank()) {
				List<LyricLine> untimed = parsePlain(plain);
				if (!untimed.isEmpty()) {
					return new ProviderHit(List.of(), untimed, SyncQuality.PLAIN_UNSYNCED, "LRCLIB");
				}
			}
		} catch (Exception e) {
			LOGGER.debug("lrclib get failed for {} - {}: {}", artist, title, e.toString());
		}
		return ProviderHit.EMPTY;
	}

	private static ProviderHit searchStructuredLrclib(
		String artist,
		String title,
		long durationSeconds,
		boolean allowTrackOnlyFallback
	) {
		try {
			StringBuilder q = new StringBuilder();
			q.append("track_name=").append(URLEncoder.encode(title, StandardCharsets.UTF_8));
			if (artist != null && !artist.isBlank()) {
				q.append("&artist_name=").append(URLEncoder.encode(artist, StandardCharsets.UTF_8));
			}
			HttpResponse<String> resp = sendLrclibGet("https://lrclib.net/api/search?" + q);
			ProviderHit primary = scoreLrclibArray(resp, artist, title, durationSeconds);
			if (primary.hasSynced()) {
				return primary;
			}
			if (allowTrackOnlyFallback && (resp == null || resp.statusCode() != 200)
				&& artist != null && !artist.isBlank()) {
				String fallbackQ = "track_name=" + URLEncoder.encode(title, StandardCharsets.UTF_8);
				HttpResponse<String> fbResp = sendLrclibGet("https://lrclib.net/api/search?" + fallbackQ);
				ProviderHit fb = scoreLrclibArray(fbResp, artist, title, durationSeconds);
				if (fb.hasSynced() || !primary.hasPlain()) {
					return fb;
				}
			}
			return primary;
		} catch (Exception e) {
			LOGGER.debug("lrclib search failed for {} - {}: {}", artist, title, e.toString());
			return ProviderHit.EMPTY;
		}
	}

	private static ProviderHit searchFreeTextLrclib(String artist, String title, long durationSeconds) {
		try {
			String combined = (artist == null || artist.isBlank()) ? title.trim() : (artist.trim() + " " + title.trim());
			HttpResponse<String> resp = sendLrclibGet(
				"https://lrclib.net/api/search?q=" + URLEncoder.encode(combined, StandardCharsets.UTF_8)
			);
			return scoreLrclibArray(resp, artist, title, durationSeconds);
		} catch (Exception e) {
			return ProviderHit.EMPTY;
		}
	}

	private static ProviderHit scoreLrclibArray(
		HttpResponse<String> resp,
		String artist,
		String title,
		long durationSeconds
	) {
		if (resp == null || resp.statusCode() != 200) {
			return ProviderHit.EMPTY;
		}
		JsonElement parsedRoot = JsonParser.parseString(resp.body());
		if (!parsedRoot.isJsonArray()) {
			return ProviderHit.EMPTY;
		}
		JsonArray results = parsedRoot.getAsJsonArray();
		String wantedTitle = TrackMetadata.fold(title);
		String wantedArtist = TrackMetadata.fold(artist);

		List<LyricLine> bestSynced = List.of();
		double bestScore = -1.0;
		List<LyricLine> bestPlain = List.of();

		for (JsonElement element : results) {
			if (!element.isJsonObject()) continue;
			JsonObject entry = element.getAsJsonObject();

			String rawEntryTrack = getString(entry, "trackName");
			String rawEntryArtist = getString(entry, "artistName");
			boolean hasDur = durationSeconds > 0L && entry.has("duration") && !entry.get("duration").isJsonNull();
			long candDur = hasDur ? Math.round(entry.get("duration").getAsDouble()) : 0L;

			if (!passesTrackGuard(artist, title, durationSeconds, rawEntryArtist, rawEntryTrack, candDur)) {
				continue;
			}

			String entryTrack = TrackMetadata.fold(rawEntryTrack);
			String entryTrackClean = TrackMetadata.fold(TrackMetadata.cleanTitle(rawEntryTrack));
			String entryArtist = TrackMetadata.fold(rawEntryArtist);
			boolean exactTitle = entryTrack.equals(wantedTitle) || entryTrackClean.equals(wantedTitle);
			boolean artistMatched = !wantedArtist.isEmpty() && !entryArtist.isEmpty()
				&& (entryArtist.equals(wantedArtist) || entryArtist.contains(wantedArtist) || wantedArtist.contains(entryArtist));

			double score = exactTitle ? 50.0 : 25.0;
			if (artistMatched) {
				score += entryArtist.equals(wantedArtist) ? 45.0 : 30.0;
			} else if (wantedArtist.isEmpty()) {
				score += 10.0;
			}
			if (hasDur) {
				long durDiff = Math.abs(candDur - durationSeconds);
				score += Math.max(0.0, 35.0 - durDiff * 0.5);
			}

			List<LyricLine> candidateLines = parseLrclibTimed(entry);
			if (!candidateLines.isEmpty()) {
				double totalScore = score + (hasRenderableScript(candidateLines) ? 25.0 : 0.0);
				boolean candidateWords = hasWordTiming(candidateLines), bestWords = hasWordTiming(bestSynced);
				// Prefer word timings only between very closely matched records (at most
				// two seconds of duration-score difference); do not choose a worse edition.
				boolean close = IslandSettings.preferWordTiming && Math.abs(totalScore - bestScore) <= 1.0;
				if ((close && candidateWords && !bestWords)
						|| (totalScore > bestScore && !(close && bestWords && !candidateWords))) {
					bestScore = totalScore;
					bestSynced = candidateLines;
				}
			} else if (bestPlain.isEmpty()) {
				String plainStr = getString(entry, "plainLyrics");
				if (plainStr != null && !plainStr.isBlank()) {
					bestPlain = parsePlain(plainStr);
				}
			}
		}

		if (!bestSynced.isEmpty()) {
			boolean hasWords = hasWordTiming(bestSynced);
			return new ProviderHit(
				bestSynced,
				List.of(),
				hasWords ? SyncQuality.WORD_SYNCED : SyncQuality.LINE_SYNCED,
				"LRCLIB"
			);
		}
		if (!bestPlain.isEmpty()) {
			return new ProviderHit(List.of(), bestPlain, SyncQuality.PLAIN_UNSYNCED, "LRCLIB");
		}
		return ProviderHit.EMPTY;
	}

	private static HttpResponse<String> sendLrclibGet(String url) throws Exception {
		if (System.currentTimeMillis() < lrclibCooldownUntilMs) {
			return null;
		}
		HttpRequest req = HttpRequest.newBuilder()
			.uri(URI.create(url))
			.timeout(REQUEST_TIMEOUT)
			.header("User-Agent", "LT5Lyrics/" + com.lt5lyrics.client.VersionCheck.VERSION + " (Minecraft 1.21.11 Fabric)")
			.GET()
			.build();
		try {
			HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
			if (resp.statusCode() == 429) {
				lrclibCooldownUntilMs = System.currentTimeMillis() + retryAfterMs(resp, 30_000L);
				LRCLIB_IO_ERROR.set(Boolean.TRUE);
				reportProviderProblem();
			} else if (resp.statusCode() >= 500) {
				LRCLIB_IO_ERROR.set(Boolean.TRUE);
				reportProviderProblem();
			}
			return resp;
		} catch (Exception e) {
			LRCLIB_IO_ERROR.set(Boolean.TRUE);
			reportProviderProblem();
			throw e;
		}
	}

	/** LRCLIB currently exposes word timing in Lyricsfile YAML, while syncedLyrics is usually line-only. */
	public static List<LyricLine> parseLrclibTimed(JsonObject entry) {
		if (entry == null) return List.of();
		String lrc = getString(entry, "syncedLyrics");
		List<LyricLine> lineFallback = lrc == null ? List.of() : parseLrc(lrc);
		if (entry.has("lyricsfile") && entry.get("lyricsfile").isJsonPrimitive()) {
			String yaml = entry.get("lyricsfile").getAsString();
			if (yaml.contains("words:")) {
				List<LyricLine> wordLines = LyricsFileParser.parse(yaml);
				// Keep the original line result if an invalid/incomplete file would lose lines.
				if (hasWordTiming(wordLines) && (lineFallback.isEmpty() || wordLines.size() * 10 >= lineFallback.size() * 9)) {
					return wordLines;
				}
			}
		}
		return lineFallback;
	}

	private static String getString(JsonObject json, String key) {
		if (json.has(key) && !json.get(key).isJsonNull()) {
			return json.get(key).getAsString();
		}
		return null;
	}

	public static List<LyricLine> parseLrc(String lrc) {
		return parseLrcWithTranslation(lrc, null);
	}

	/**
	 * Parses LRC & Enhanced LRC (<mm:ss.xx> word tags) across all international scripts (Khmer, Chinese,
	 * Cantonese, Japanese, Korean, Thai, Vietnamese, Arabic, Cyrillic, Latin) and merges duplicate-timestamp
	 * or separate bilingual translations into LyricLine.translation() WITHOUT ever overwriting primary sung text.
	 */
	public static List<LyricLine> parseLrcWithTranslation(String primaryLrc, String translationLrc) {
		List<LyricLine> rawPrimary = parseRawTimedLines(primaryLrc);
		if (rawPrimary.isEmpty()) {
			return List.of();
		}

		List<LyricLine> merged = new ArrayList<>(rawPrimary.size());
		for (LyricLine item : rawPrimary) {
			if (!isRenderableLine(item.text())) {
				continue;
			}
			if (!merged.isEmpty()) {
				LyricLine prev = merged.get(merged.size() - 1);
				if (Math.abs(item.time() - prev.time()) <= 0.04F) {
					if (!prev.text().equals(item.text()) && prev.translation().isEmpty()) {
						merged.set(merged.size() - 1, new LyricLine(
							prev.time(), prev.text(), item.text(), prev.words()
						));
					}
					continue;
				}
			}
			merged.add(item);
		}

		if (merged.isEmpty()) {
			return rawPrimary;
		}

		if (translationLrc != null && !translationLrc.isBlank()) {
			List<LyricLine> transLines = parseRawTimedLines(translationLrc);
			if (!transLines.isEmpty()) {
				for (int i = 0; i < merged.size(); i++) {
					LyricLine main = merged.get(i);
					if (main.hasTranslation()) continue;
					String matchedTrans = findClosestTranslation(transLines, main.time());
					if (!matchedTrans.isEmpty() && !matchedTrans.equals(main.text())) {
						merged.set(i, new LyricLine(main.time(), main.text(), matchedTrans, main.words()));
					}
				}
			}
		}

		return merged;
	}

	private static String findClosestTranslation(List<LyricLine> transLines, float targetTime) {
		String best = "";
		float bestDiff = 0.35F;
		for (LyricLine tl : transLines) {
			float d = Math.abs(tl.time() - targetTime);
			if (d <= bestDiff && isRenderableLine(tl.text())) {
				bestDiff = d;
				best = tl.text();
			}
		}
		return best;
	}

	private static float parseStampSeconds(String minS, String secS, String fracS) {
		int min = Integer.parseInt(minS);
		int sec = Integer.parseInt(secS);
		float frac = 0.0F;
		if (fracS != null && !fracS.isEmpty()) {
			int divisor = fracS.length() == 1 ? 10 : (fracS.length() == 2 ? 100 : 1000);
			frac = Integer.parseInt(fracS.substring(0, Math.min(3, fracS.length()))) / (float) divisor;
		}
		return min * 60.0F + sec + frac;
	}

	private static List<LyricLine> parseRawTimedLines(String lrc) {
		List<LyricLine> rawParsed = new ArrayList<>();
		if (lrc == null || lrc.isBlank()) return rawParsed;
		String cleanedLrc = lrc.replace("\uFEFF", "").replace("\u200B", "");
		Matcher offsetMatcher = LRC_OFFSET.matcher(cleanedLrc);
		float offsetSeconds = offsetMatcher.find() ? Long.parseLong(offsetMatcher.group(1)) / 1000.0F : 0.0F;
		for (String raw : cleanedLrc.split("\\R")) {
			Matcher matcher = LRC_TIMESTAMP.matcher(raw);
			List<Float> stamps = new ArrayList<>(2);
			int lastEnd = 0;
			while (matcher.find()) {
				stamps.add(parseStampSeconds(matcher.group(1), matcher.group(2), matcher.group(3)));
				lastEnd = matcher.end();
			}
			if (!stamps.isEmpty()) {
				String body = raw.substring(lastEnd)
					.replace("\u266A", "")
					.replace("\u266B", "")
					.replace("\u266C", "")
					.replace("\u2669", "");

				// Parse Enhanced LRC <mm:ss.xx> inline word tags if present and strip tags cleanly
				ParsedEnhanced enhanced = parseEnhancedWordTags(body, stamps.get(0));
				String text = enhanced.cleanText.trim();
				if (!text.isEmpty() && !text.equals("//")) {
					for (float t : stamps) {
						// A repeated line carries absolute tags for its first occurrence;
						// shift the same relative timings for each repeated occurrence.
						float delta = t - stamps.get(0) - offsetSeconds;
						List<WordSpan> spans = new ArrayList<>(enhanced.spans.size());
						for (WordSpan sp : enhanced.spans) {
							spans.add(new WordSpan(Math.max(0.0F, sp.startSec() + delta),
									Math.max(0.0F, sp.endSec() + delta), sp.charStart(), sp.charEnd()));
						}
						rawParsed.add(new LyricLine(Math.max(0.0F, t - offsetSeconds), text, "", List.copyOf(spans)));
					}
				}
			}
		}
		rawParsed.sort(Comparator.comparingDouble(LyricLine::time));
		return rawParsed;
	}

	private record ParsedEnhanced(String cleanText, List<WordSpan> spans) {}

	private static ParsedEnhanced parseEnhancedWordTags(String body, float lineStart) {
		Matcher m = ENHANCED_WORD_TAG.matcher(body);
		if (!m.find()) {
			return new ParsedEnhanced(body, List.of());
		}
		m.reset();

		List<Float> tagTimes = new ArrayList<>();
		List<String> segments = new ArrayList<>();
		int prevEnd = 0;
		String leading = "";

		while (m.find()) {
			if (tagTimes.isEmpty()) {
				leading = body.substring(0, m.start());
			} else {
				segments.add(body.substring(prevEnd, m.start()));
			}
			tagTimes.add(parseStampSeconds(m.group(1), m.group(2), m.group(3)));
			prevEnd = m.end();
		}
		segments.add(body.substring(prevEnd));

		StringBuilder sb = new StringBuilder();
		if (!leading.isBlank()) {
			sb.append(leading.stripLeading());
		}

		List<WordSpan> spans = new ArrayList<>();
		boolean ordered = true;
		for (int i = 1; i < tagTimes.size(); i++) {
			if (tagTimes.get(i) < tagTimes.get(i - 1)) { ordered = false; break; }
		}
		// Untagged leading text begins at the line timestamp and ends at the first tag.
		if (!sb.isEmpty() && !sb.toString().isBlank() && tagTimes.get(0) >= lineStart) {
			spans.add(new WordSpan(lineStart, tagTimes.get(0), 0, sb.length()));
		}
		for (int i = 0; i < tagTimes.size() && i < segments.size(); i++) {
			String seg = segments.get(i);
			if (seg.isEmpty()) continue;
			if (sb.isEmpty()) {
				seg = seg.stripLeading();
			}
			if (seg.isEmpty()) continue;
			int cs = sb.length();
			sb.append(seg);
			int ce = sb.length();
			float ws = tagTimes.get(i);
			float we = (i + 1 < tagTimes.size()) ? Math.max(ws + 0.05F, tagTimes.get(i + 1)) : (ws + 0.45F);
			spans.add(new WordSpan(ws, we, cs, ce));
		}

		String full = sb.toString();
		String trimmed = full.stripTrailing();
		if (trimmed.length() < full.length() && !spans.isEmpty()) {
			int maxLen = trimmed.length();
			List<WordSpan> clamped = new ArrayList<>(spans.size());
			for (WordSpan sp : spans) {
				if (sp.charStart() < maxLen) {
					clamped.add(new WordSpan(sp.startSec(), sp.endSec(), sp.charStart(), Math.min(maxLen, sp.charEnd())));
				}
			}
			spans = clamped;
		}
		return new ParsedEnhanced(trimmed, !ordered || spans.isEmpty() ? List.of() : List.copyOf(spans));
	}

	static boolean hasRenderableScript(List<LyricLine> lines) {
		for (LyricLine line : lines) {
			if (isRenderableLine(line.text())) return true;
		}
		return false;
	}

	static boolean isRenderableLine(String text) {
		if (text == null || text.isEmpty()) return false;
		for (int i = 0; i < text.length(); ) {
			int cp = Character.codePointAt(text, i);
			i += Character.charCount(cp);
			if (Character.isLetterOrDigit(cp)) {
				return true;
			}
		}
		return false;
	}

	static List<LyricLine> parsePlain(String plain) {
		List<LyricLine> lines = new ArrayList<>();
		if (plain == null) return lines;
		for (String raw : plain.replace("\uFEFF", "").replace("\u200B", "").split("\\R")) {
			String trimmed = raw.trim();
			if (!trimmed.isEmpty()) {
				lines.add(new LyricLine(-1.0F, trimmed));
			}
		}
		return lines;
	}

	public static List<LyricLine> autoPacePlain(List<LyricLine> plainLines, long durationSeconds) {
		List<String> nonEmpty = new ArrayList<>();
		for (LyricLine line : plainLines) {
			String t = line.text() == null ? "" : line.text().trim();
			if (t.isEmpty() || (t.startsWith("[") && t.endsWith("]") && t.length() <= 28)) {
				continue;
			}
			nonEmpty.add(t);
		}
		if (nonEmpty.size() < 2) {
			return List.of();
		}

		float startSec;
		float spanSec;
		if (durationSeconds > 18L) {
			startSec = Math.min(7.0F, durationSeconds * 0.05F);
			float endSec = Math.max(startSec + nonEmpty.size() * 1.5F, durationSeconds * 0.93F);
			spanSec = Math.max(6.0F, endSec - startSec);
		} else {
			startSec = 3.0F;
			spanSec = nonEmpty.size() * 3.6F;
		}

		float step = spanSec / nonEmpty.size();
		List<LyricLine> paced = new ArrayList<>(nonEmpty.size());
		for (int i = 0; i < nonEmpty.size(); i++) {
			paced.add(new LyricLine(startSec + i * step, nonEmpty.get(i)));
		}
		return paced;
	}

	public static int currentIndex(List<LyricLine> lines, float position) {
		int idx = -1;
		for (int i = 0; i < lines.size(); i++) {
			if (lines.get(i).time() <= position) idx = i;
			else break;
		}
		return idx;
	}
}
