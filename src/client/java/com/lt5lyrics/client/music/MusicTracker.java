package com.lt5lyrics.client.music;

import com.mojang.blaze3d.platform.NativeImage;
import com.lt5lyrics.client.island.BeatReactor;
import com.lt5lyrics.client.island.ColorUtil;
import dev.redstones.mediaplayerinfo.IMediaSession;
import dev.redstones.mediaplayerinfo.MediaInfo;
import dev.redstones.mediaplayerinfo.MediaPlayerInfo;
import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;

public class MusicTracker {
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("lt5lyrics");

	/**
	 * Tracks media playback position across both desktop apps that actively publish SMTC TimelineProperties
	 * (e.g. Spotify) and web browsers / YouTube sessions where SMTC Position stays 0 when started from 0:00
	 * or where TimelineProperties are omitted altogether (e.g. Firefox).
	 */
	public static final class PlaybackClock {
		private static final long MAX_REASONABLE_POSITION = 86_400L;
		private static final long ACTIVE_TIMELINE_WINDOW_MS = 2_400L;

		private float clock = -1.0F;
		private long clockStamp = 0L;
		private long lastObservedRaw = 0L;
		private long lastRawChangeAt = 0L;
		private long lastDuration = 0L;
		private boolean lastPlaying = false;

		public synchronized void reset() {
			clock = -1.0F;
			clockStamp = 0L;
			lastObservedRaw = 0L;
			lastRawChangeAt = 0L;
			lastDuration = 0L;
			lastPlaying = false;
		}

		/**
		 * Updates the playback clock and returns true if the track duration changed significantly
		 * (e.g. a 15s YouTube pre-roll ad transitioning into a 210s music video on the same tab).
		 */
		public synchronized boolean update(long rawPosition, long durationSeconds, boolean isPlaying, long nowMs) {
			float dt = (clockStamp != 0L && nowMs > clockStamp)
				? Math.min(2.0F, (nowMs - clockStamp) / 1000.0F) : 0.0F;
			boolean wasPlaying = lastPlaying;
			if (clock >= 0.0F && wasPlaying) clock += dt;
			clockStamp = nowMs;
			lastPlaying = isPlaying;

			boolean rawValid = rawPosition >= 0L && rawPosition < MAX_REASONABLE_POSITION
				&& (durationSeconds <= 0L || rawPosition <= durationSeconds + 10L);

			// Same-tab pre-roll/ad transitions still get a fresh playback clock.
			if (durationSeconds > 0L && lastDuration > 0L && Math.abs(durationSeconds - lastDuration) >= 5L) {
				clock = rawValid && rawPosition > 0L ? rawPosition + 0.5F : 0.0F;
				lastObservedRaw = rawValid ? rawPosition : 0L;
				lastRawChangeAt = rawValid && rawPosition > 0L ? nowMs : 0L;
				lastDuration = durationSeconds;
				return true;
			}
			if (durationSeconds > 0L) lastDuration = durationSeconds;

			if (clock < 0.0F) {
				// At first observation only a whole-second bucket is known.
				clock = rawValid && rawPosition > 0L ? rawPosition + 0.5F : 0.0F;
				lastObservedRaw = rawValid ? rawPosition : 0L;
				lastRawChangeAt = rawValid && rawPosition > 0L ? nowMs : 0L;
				return false;
			}

			if (rawValid && rawPosition != lastObservedRaw) {
				long previous = lastObservedRaw;
				lastObservedRaw = rawPosition;
				if (rawPosition == 0L) {
					// Restart or seek to the beginning; also covers short tracks/early seeks.
					if (previous > 0L || clock >= 5.0F) clock = 0.0F;
					lastRawChangeAt = 0L;
				} else {
					lastRawChangeAt = nowMs;
					if (rawPosition == previous + 1L && wasPlaying && isPlaying && dt > 0.0F && dt <= 0.75F) {
						// The native bridge truncates to seconds. A 1-second transition was
						// observed between these polls, so it is near the START of the new
						// bucket, not permanently at raw+0.5. Then advance continuously.
						clock = rawPosition + Math.min(0.15F, dt * 0.5F);
					} else if (!isPlaying && clock >= rawPosition && clock < rawPosition + 1.0F) {
						// Preserve our best fractional estimate when pausing in this bucket.
					} else {
						// A seek/jump has unknown fractional phase; use the bucket midpoint.
						clock = rawPosition + 0.5F;
					}
				}
			}

			if (durationSeconds > 0L) {
				if (lastObservedRaw == 0L && lastRawChangeAt == 0L && isPlaying && clock >= durationSeconds + 1.2F) {
					// Preserve the existing fallback for browsers that never publish a timeline.
					clock = 0.0F;
				} else if (lastRawChangeAt > 0L && (nowMs - lastRawChangeAt) <= ACTIVE_TIMELINE_WINDOW_MS) {
					clock = Math.min(clock, (float) durationSeconds);
				}
			}
			return false;
		}

		public synchronized float positionAt(boolean isPlaying, long nowMs) {
			if (clock < 0.0F) return 0.0F;
			float value = clock;
			if (isPlaying && clockStamp != 0L && nowMs > clockStamp) {
				value += Math.min(0.5F, (nowMs - clockStamp) / 1000.0F);
			}
			return Math.max(0.0F, value);
		}
	}

	private volatile IMediaSession session;
	private volatile List<IMediaSession> usableSessions = List.of();
	private volatile String preferredOwner = "";
	private volatile String preferredKey = "";
	private volatile List<LyricsFetcher.LyricLine> lyrics = List.of();
	private volatile List<LyricsFetcher.LyricLine> rawLyrics = List.of();
	private volatile String lyricSource = "None";
	private volatile LyricsFetcher.SyncQuality syncQuality = LyricsFetcher.SyncQuality.NOT_FOUND;
	private volatile boolean manualRetryRequested = false;
	private int mediaColor = 0xFFFFFFFF;
	private final Map<Integer, Identifier> textureCache = new ConcurrentHashMap<>();
	private final Map<Integer, Integer> colorCache = new ConcurrentHashMap<>();
	private final Map<String, LyricsFetcher.Result> resultCache = new ConcurrentHashMap<>();
	private volatile String lastTrack = "";
	private volatile long trackChangedAt;
	private volatile boolean playing;
	private volatile float peak;
	// The existing per-song delay is persistent; quickOffset exists only in memory.
	private volatile float lyricsOffset;
	private final QuickLyricOffset quickOffset = new QuickLyricOffset();
	private volatile boolean lyricsSynced;
	private volatile boolean lyricsPlayable;
	private final BeatReactor beatReactor = new BeatReactor();
	private final PlaybackClock playbackClock = new PlaybackClock();

	private volatile long retryAt;
	private volatile int tries;
	private final AtomicBoolean fetchInFlight = new AtomicBoolean(false);
	private final AtomicLong trackEpoch = new AtomicLong(0L);
	private final ExecutorService fetchExecutor;
	// The current track keeps its existing fetch worker. At most one additional
	// playing session is searched at a time, away from both render and poll threads.
	private final ExecutorService alternateExecutor;
	private final LyricsLookup lyricsLookup;
	private final Map<ProbeKey, ProbeEntry> alternateResults = new LinkedHashMap<>();
	private boolean alternateInFlight;
	private ProbeKey inFlightAlternate;
	private long alternateGeneration;
	private ProbeKey selectedAlternate;
	private long selectedAlternateEpoch;
	private boolean manualLyricsChosen;

	@FunctionalInterface
	interface LyricsLookup {
		LyricsFetcher.Result fetch(String owner, String artist, String title, long duration,
				boolean bypass, Consumer<LyricsFetcher.Result> provisional);
	}

	private record ProbeKey(String sessionKey, String owner, String artist, String title,
			long duration, String preferences) {}
	private record ProbeEntry(LyricsFetcher.Result result, boolean complete, long expiresAt) {}
	private record Alternative(IMediaSession session, ProbeKey key, ProbeEntry entry) {}

	private static final Random RANDOM = new Random();

	private int ticks;
	private String currentKey = "";

	public static String sessionKey(IMediaSession session) {
		if (session == null || session.getMedia() == null) return "";
		String owner = session.getOwner() == null ? "" : session.getOwner();
		String artist = session.getMedia().getArtist() == null ? "" : session.getMedia().getArtist();
		String title = session.getMedia().getTitle() == null ? "" : session.getMedia().getTitle();
		return owner + "|" + artist + " - " + title;
	}

	public static boolean isUsableSession(IMediaSession session) {
		if (session == null || session.getMedia() == null) return false;
		String title = session.getMedia().getTitle();
		return title != null && !title.isBlank();
	}

	/**
	 * Picks the best active media session across Spotify and browsers (including multi-tab Chrome/Edge/Firefox
	 * and YouTube sessions with empty SMTC Artist fields).
	 */
	public static IMediaSession selectSession(
		List<IMediaSession> usable,
		String preferredKey,
		String preferredOwner,
		String currentKey
	) {
		if (usable == null || usable.isEmpty()) return null;

		boolean anyOtherPlaying = false;
		for (IMediaSession candidate : usable) {
			if (candidate.getMedia().isPlaying()) {
				anyOtherPlaying = true;
				break;
			}
		}

		// 1. Honor explicit user-selected session/player if it is playing OR nothing else is playing
		if (preferredKey != null && !preferredKey.isEmpty()) {
			for (IMediaSession candidate : usable) {
				if (sessionKey(candidate).equals(preferredKey)
					&& (candidate.getMedia().isPlaying() || !anyOtherPlaying)) {
					return candidate;
				}
			}
		}
		if (preferredOwner != null && !preferredOwner.isEmpty()) {
			for (IMediaSession candidate : usable) {
				if (candidate.getOwner() != null && candidate.getOwner().equalsIgnoreCase(preferredOwner)
					&& candidate.getMedia().isPlaying()) {
					return candidate;
				}
			}
			if (!anyOtherPlaying) {
				for (IMediaSession candidate : usable) {
					if (candidate.getOwner() != null && candidate.getOwner().equalsIgnoreCase(preferredOwner)) {
						return candidate;
					}
				}
			}
		}

		// 2. Keep current playing track stable if still actively playing
		if (currentKey != null && !currentKey.isEmpty()) {
			for (IMediaSession candidate : usable) {
				if (sessionKey(candidate).equals(currentKey) && candidate.getMedia().isPlaying()) {
					return candidate;
				}
			}
		}

		// 3. Pick any session that is currently playing
		for (IMediaSession candidate : usable) {
			if (candidate.getMedia().isPlaying()) {
				return candidate;
			}
		}

		// 4. Keep current paused session if nothing is playing
		if (currentKey != null && !currentKey.isEmpty()) {
			for (IMediaSession candidate : usable) {
				if (sessionKey(candidate).equals(currentKey)) {
					return candidate;
				}
			}
		}

		return usable.get(0);
	}

	private volatile boolean running = true;
	private final Thread thread;

	public MusicTracker() { this(true); }

    MusicTracker(boolean startThread) { this(startThread, LyricsFetcher::fetch); }

    // Package-private injection is used by deterministic tests, not by the release UI.
    MusicTracker(boolean startThread, LyricsLookup lyricsLookup) {
		this.lyricsLookup = Objects.requireNonNull(lyricsLookup);
		alternateExecutor = Executors.newSingleThreadExecutor(r -> {
			Thread t = new Thread(r, "lt5lyrics-alternate-fetcher");
			t.setDaemon(true);
			return t;
		});
		fetchExecutor = Executors.newSingleThreadExecutor(r -> {
			Thread t = new Thread(r, "lt5lyrics-fetcher");
			t.setDaemon(true);
			return t;
		});
		thread = new Thread(() -> {
			while (running) {
				try {
					Thread.sleep(25L);
					float value = AudioLevel.peak();
					if (value >= 0.0F) peak = value;
					if (++ticks % 4 == 0) poll();
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					return;
				}
			}
		});
		thread.setDaemon(true);
		thread.setName("lt5lyrics-media");
		if(startThread) thread.start();
	}

	private synchronized void poll() {
		try {
			pollSessions(MediaPlayerInfo.INSTANCE.getMediaSessions());
		} catch (Throwable ignored) {
		}
	}

	/** The native snapshot is also injectable by tests; never called on a render thread. */
	synchronized void pollSessions(List<IMediaSession> sessions) {
		try {
			List<IMediaSession> usable = (sessions == null ? List.<IMediaSession>of() : sessions).stream()
				.filter(MusicTracker::isUsableSession)
				.toList();
			usableSessions = usable;

			IMediaSession found = selectSession(usable, preferredKey, preferredOwner, currentKey);
			Alternative alternate = null;
			if (found != null && found.getMedia().isPlaying()
					&& !hasPlayingManualPreference(usable, preferredKey, preferredOwner)
					&& !(sessionKey(found).equals(lastTrack) && (lyricsPlayable || manualLyricsChosen))) {
				alternate = findPlayableAlternative(usable, found, System.currentTimeMillis());
				if (alternate != null) found = alternate.session();
			}
			if (found != null) {
				String foundKey = sessionKey(found);
				if (!foundKey.equals(currentKey)) {
					currentKey = foundKey;
					playbackClock.reset();
				}
			} else {
				session = null;
				playing = false;
				quickOffset.missing(System.nanoTime());
				return;
			}

			session = found;
			MediaInfo info = found.getMedia();
			playing = info.isPlaying();
			long now = System.currentTimeMillis();

			String rawArtist = info.getArtist() == null ? "" : info.getArtist();
			String rawTitle = info.getTitle() == null ? "" : info.getTitle();
			String owner = found.getOwner() == null ? "" : found.getOwner();
			String trackId = owner + "|" + rawArtist + " - " + rawTitle;

			if (!trackId.equals(lastTrack)) {
				boolean hadPreviousTrack = !lastTrack.isEmpty();
				lastTrack = trackId;
				if (hadPreviousTrack || playing) {
					trackChangedAt = now;
				}
				long epoch = trackEpoch.incrementAndGet();
				fetchInFlight.set(false);
				selectedAlternate = null;
				manualLyricsChosen = false;
				playbackClock.reset();
				lyricsOffset = resolveSavedOffset(owner, rawArtist, rawTitle);
				lyrics = List.of();
				rawLyrics = List.of();
				lyricsSynced = false;
				lyricsPlayable = false;
				syncQuality = LyricsFetcher.SyncQuality.NOT_FOUND;
				lyricSource = "Searching...";
				tries = 0;
				retryAt = 0L;
				if (alternate != null && alternate.session() == found && playableResult(alternate.entry().result())) {
					// Use the candidate's own track, player and clock together. A provisional
					// line-timed match can be upgraded by the same background search later.
					applyFetchedResult(epoch, alternate.entry().result());
					if (!alternate.entry().complete() && alternateInFlight
							&& alternate.key().equals(inFlightAlternate)) {
						selectedAlternate = alternate.key();
						selectedAlternateEpoch = epoch;
						fetchInFlight.set(true);
					}
				}
				LOGGER.debug("Active track [{}] owner='{}' artist='{}' title='{}' dur={}s",
					epoch, owner, rawArtist, rawTitle, info.getDuration());
			}

			long clockNow = monotonicMillis();
			boolean durationShifted = playbackClock.update(info.getPosition(), info.getDuration(), playing, clockNow);
			quickOffset.observe(trackId, info.getPosition(), info.getDuration(),
				playbackClock.positionAt(playing, clockNow), playing, System.nanoTime());
			if (durationShifted && !lyricsSynced) {
				// Re-fetch with the real video duration once a pre-roll ad finishes!
				trackEpoch.incrementAndGet();
				fetchInFlight.set(false);
				tries = 0;
				retryAt = 0L;
			}

			if (rawLyrics.isEmpty() && !lyricsPlayable && !fetchInFlight.get() && tries < 2 && now >= retryAt) {
				tries++;
				retryAt = now + 92_000L;
				long expectedEpoch = trackEpoch.get();
				long duration = info.getDuration();
				boolean bypass = manualRetryRequested;
				manualRetryRequested = false;
				fetchInFlight.set(true);
				fetchExecutor.execute(() -> runAsyncLyricFetch(expectedEpoch, owner, rawArtist, rawTitle, duration, bypass));
			}
			if (playing && !lyricsPlayable && !manualLyricsChosen
					&& !hasPlayingManualPreference(usable, preferredKey, preferredOwner)) {
				probeAnotherPlayingSession(usable, found, now);
			}
		} catch (Throwable ignored) {
		}
	}

	private void runAsyncLyricFetch(
		long expectedEpoch,
		String owner,
		String rawArtist,
		String rawTitle,
		long duration,
		boolean bypassCache
	) {
		try {
			if (trackEpoch.get() != expectedEpoch) return;

			// 1. Check local custom .lrc file under raw metadata or any parsed (artist, title) candidate
			List<LyricsFetcher.LyricLine> local = loadLocalCandidate(owner, rawArtist, rawTitle);
			if (!local.isEmpty()) {
                boolean words = LyricsFetcher.hasWordTiming(local);
                applyFetchedResult(expectedEpoch, new LyricsFetcher.Result(local, local, true, true, "Local (.lrc)",
                    words ? LyricsFetcher.SyncQuality.WORD_SYNCED : LyricsFetcher.SyncQuality.LINE_SYNCED));
				return;
			}

			// 2. Check session RAM cache unless manual retry requested
			String cacheKey = owner.toLowerCase(java.util.Locale.ROOT) + "|" + rawArtist + "|" + rawTitle
				+ "|" + (duration / 5L) + "|" + com.lt5lyrics.client.island.IslandSettings.autoPaceUnsyncedLyrics
				+ "|" + com.lt5lyrics.client.island.IslandSettings.preferWordTiming
				+ "|" + String.join(">", com.lt5lyrics.client.island.IslandSettings.normalizedProviderPriority());
			if (!bypassCache) {
				LyricsFetcher.Result cached = resultCache.get(cacheKey);
				if (cached != null && !cached.isEmpty()) {
					applyFetchedResult(expectedEpoch, cached);
					return;
				}
			}

			// 3. Query enabled lyric providers off the render thread
			LyricsFetcher.Result fetched = lyricsLookup.fetch(owner, rawArtist, rawTitle, duration, bypassCache,
				provisional -> applyFetchedResult(expectedEpoch, provisional));
			if (trackEpoch.get() != expectedEpoch) return;

			if (!fetched.isEmpty()) {
				if (resultCache.size() > 64) {
					resultCache.clear();
				}
				resultCache.put(cacheKey, fetched);
				applyFetchedResult(expectedEpoch, fetched);
            } else {
                applyFetchedResult(expectedEpoch, fetched);
            }
        } catch (Throwable t) {
            LOGGER.debug("Async lyric fetch error: {}", LyricsFetcher.redactSecrets(t.toString()));
            synchronized(this) { if(trackEpoch.get()==expectedEpoch && rawLyrics.isEmpty()) {
                syncQuality=LyricsFetcher.SyncQuality.PROVIDER_UNAVAILABLE; lyricSource="Provider unavailable";
            }}
        } finally { synchronized(this) { if(trackEpoch.get()==expectedEpoch)fetchInFlight.set(false); } }
	}

	private synchronized void applyFetchedResult(long expectedEpoch, LyricsFetcher.Result fetched) {
		if (trackEpoch.get() != expectedEpoch) return;
		lyrics = fetched.lines();
		rawLyrics = fetched.rawLines();
		lyricsSynced = fetched.synced();
		lyricsPlayable = fetched.playable();
		syncQuality = fetched.syncQuality();
		lyricSource = fetched.source();
	}

	/** Only automatically pick a result that can actually be displayed. */
	static boolean playableResult(LyricsFetcher.Result result) {
		return result != null && result.playable() && result.lines() != null
				&& result.lines().stream().anyMatch(line -> line != null && !line.text().isBlank());
	}

	private static boolean hasPlayingManualPreference(List<IMediaSession> usable, String key, String owner) {
		for (IMediaSession candidate : usable) {
			if (!candidate.getMedia().isPlaying()) continue;
			if (key != null && !key.isEmpty() && key.equals(sessionKey(candidate))) return true;
			if (owner != null && !owner.isEmpty() && candidate.getOwner() != null
					&& owner.equalsIgnoreCase(candidate.getOwner())) return true;
		}
		return false;
	}

	/** In-memory only; contains no raw keys and is never written to logs. */
	private static String searchPreferences() {
		var order = com.lt5lyrics.client.island.IslandSettings.normalizedProviderPriority();
		StringBuilder fingerprint = new StringBuilder();
		for (String id : order) fingerprint.append(id).append('=')
				.append(com.lt5lyrics.client.island.IslandSettings.isProviderEnabled(id)).append(';');
		fingerprint.append(com.lt5lyrics.client.island.IslandSettings.autoPaceUnsyncedLyrics).append(';')
				.append(com.lt5lyrics.client.island.IslandSettings.preferWordTiming).append(';')
				.append(Objects.hashCode(com.lt5lyrics.client.island.IslandSettings.musixmatchApiKey)).append(';')
				.append(Objects.hashCode(com.lt5lyrics.client.island.IslandSettings.betterLyricsApiKey));
		return fingerprint.toString();
	}

	private static ProbeKey probeKey(IMediaSession candidate) {
		MediaInfo info = candidate.getMedia();
		return new ProbeKey(sessionKey(candidate), candidate.getOwner() == null ? "" : candidate.getOwner(),
				info.getArtist() == null ? "" : info.getArtist(), info.getTitle() == null ? "" : info.getTitle(),
				info.getDuration(), searchPreferences());
	}

	private Alternative findPlayableAlternative(List<IMediaSession> usable, IMediaSession current, long now) {
		String currentTrack = sessionKey(current);
		for (IMediaSession candidate : usable) {
			if (!candidate.getMedia().isPlaying() || sessionKey(candidate).equals(currentTrack)) continue;
			ProbeKey key = probeKey(candidate);
			ProbeEntry entry = alternateResults.get(key);
			if (entry != null && entry.expiresAt() > now && playableResult(entry.result())) {
				return new Alternative(candidate, key, entry);
			}
		}
		return null;
	}

	/** Probe active alternatives only. A single bounded worker avoids a search storm. */
	private void probeAnotherPlayingSession(List<IMediaSession> usable, IMediaSession current, long now) {
		if (!running || alternateInFlight) return;
		String currentTrack = sessionKey(current);
		for (IMediaSession candidate : usable) {
			if (!candidate.getMedia().isPlaying() || sessionKey(candidate).equals(currentTrack)) continue;
			ProbeKey key = probeKey(candidate);
			ProbeEntry entry = alternateResults.get(key);
			if (entry != null && entry.expiresAt() > now) continue;
			alternateInFlight = true;
			inFlightAlternate = key;
			long generation = alternateGeneration;
			alternateExecutor.execute(() -> runAlternateProbe(key, generation));
			return;
		}
	}

	private void runAlternateProbe(ProbeKey key, long generation) {
		try {
			// Local .lrc is authoritative, exactly as for the foreground session.
			List<LyricsFetcher.LyricLine> local = loadLocalCandidate(key.owner(), key.artist(), key.title());
			LyricsFetcher.Result result;
			if (!local.isEmpty()) {
				boolean words = LyricsFetcher.hasWordTiming(local);
				result = new LyricsFetcher.Result(local, local, true, true, "Local (.lrc)",
						words ? LyricsFetcher.SyncQuality.WORD_SYNCED : LyricsFetcher.SyncQuality.LINE_SYNCED);
			} else {
				result = lyricsLookup.fetch(key.owner(), key.artist(), key.title(), key.duration(), false,
						provisional -> acceptAlternateResult(key, generation, provisional, false));
			}
			acceptAlternateResult(key, generation, result, true);
		} catch (Throwable t) {
			LOGGER.debug("Alternate lyric search error: {}", LyricsFetcher.redactSecrets(t.toString()));
			acceptAlternateResult(key, generation, LyricsFetcher.Result.UNAVAILABLE, true);
		} finally {
			synchronized (this) {
				if (key.equals(inFlightAlternate)) { alternateInFlight = false; inFlightAlternate = null; }
				if (key.equals(selectedAlternate) && selectedAlternateEpoch == trackEpoch.get()) {
					fetchInFlight.set(false);
					selectedAlternate = null;
				}
			}
		}
	}

	/** Store a bounded result. Only the still-current epoch can receive an upgrade. */
	private synchronized void acceptAlternateResult(ProbeKey key, long generation,
			LyricsFetcher.Result incoming, boolean complete) {
		if (!running || generation != alternateGeneration || !key.preferences().equals(searchPreferences())) return;
		ProbeEntry old = alternateResults.get(key);
		LyricsFetcher.Result result = incoming;
		if (!playableResult(result) && old != null && playableResult(old.result())) result = old.result();
		if (!complete && !playableResult(result)) return;
		if (result == null) result = LyricsFetcher.Result.UNAVAILABLE;
		long ttl = playableResult(result) ? 30L * 60_000L
				: result.syncQuality() == LyricsFetcher.SyncQuality.PROVIDER_UNAVAILABLE ? 15_000L : 90_000L;
		if (alternateResults.size() >= 64 && !alternateResults.containsKey(key)) {
			var it = alternateResults.keySet().iterator();
			if (it.hasNext()) { it.next(); it.remove(); }
		}
		alternateResults.put(key, new ProbeEntry(result, complete, System.currentTimeMillis() + ttl));
		if (key.equals(selectedAlternate) && selectedAlternateEpoch == trackEpoch.get()
				&& session != null && sessionKey(session).equals(key.sessionKey()) && !manualLyricsChosen) {
			if (playableResult(result)) applyFetchedResult(selectedAlternateEpoch, result);
			if (complete) { fetchInFlight.set(false); selectedAlternate = null; }
		}
	}

	private synchronized void invalidateAlternateSearch() {
		alternateGeneration++;
		alternateResults.clear();
		selectedAlternate = null;
	}

	private static List<LyricsFetcher.LyricLine> loadLocalCandidate(String owner, String rawArtist, String rawTitle) {
		List<LyricsFetcher.LyricLine> direct = LocalLyrics.load(rawArtist, rawTitle);
		if (!direct.isEmpty()) return direct;
		for (TrackMetadata.QueryPair pair : TrackMetadata.buildCandidates(owner, rawArtist, rawTitle)) {
			if (!pair.artist().isEmpty()) {
				List<LyricsFetcher.LyricLine> candidate = LocalLyrics.load(pair.artist(), pair.title());
				if (!candidate.isEmpty()) return candidate;
			}
		}
		return List.of();
	}

	private static float resolveSavedOffset(String owner, String rawArtist, String rawTitle) {
		float direct = LyricDelays.get(rawArtist, rawTitle);
		if (Math.abs(direct) >= 0.05F) return direct;
		for (TrackMetadata.QueryPair pair : TrackMetadata.buildCandidates(owner, rawArtist, rawTitle)) {
			if (!pair.artist().isEmpty()) {
				float alt = LyricDelays.get(pair.artist(), pair.title());
				if (Math.abs(alt) >= 0.05F) return alt;
			}
		}
		return 0.0F;
	}

	public IMediaSession getSession() {
		return session;
	}

	public int getSessionCount() {
		return usableSessions.size();
	}

	/**
	 * Cycles to the next open media player session when multiple players or browser tabs exist.
	 * Returns the newly selected player owner name, or null if fewer than 2 sessions are active.
	 */
	public synchronized String cyclePreferredSource() {
		List<IMediaSession> list = usableSessions;
		boolean manual = !preferredKey.isEmpty() || !preferredOwner.isEmpty();
		if (list.size() < 2) {
			if (!manual) return null;
			preferredKey = ""; preferredOwner = "";
			invalidateAlternateSearch();
			return Component.translatable("lt5lyrics.player.automatic").getString();
		}
		IMediaSession cur = session;
		int currentIndex = -1;
		if (cur != null) {
			String curKey = sessionKey(cur);
			for (int i = 0; i < list.size(); i++) {
				if (sessionKey(list.get(i)).equals(curKey)) { currentIndex = i; break; }
			}
		}
		// Cycle through the players, then back to the automatic lyric-aware mode.
		if (manual && currentIndex == list.size() - 1) {
			preferredKey = ""; preferredOwner = "";
			invalidateAlternateSearch();
			return Component.translatable("lt5lyrics.player.automatic").getString();
		}
		IMediaSession next = list.get((currentIndex + 1) % list.size());
		preferredOwner = next.getOwner() == null ? "" : next.getOwner();
		preferredKey = sessionKey(next);
		invalidateAlternateSearch();
		if (!preferredKey.equals(lastTrack)) {
			// Do not briefly show old lyrics against the manually selected player's clock.
			trackEpoch.incrementAndGet();
			fetchInFlight.set(false); manualLyricsChosen = false;
			lyrics = List.of(); rawLyrics = List.of(); lyricsSynced = false; lyricsPlayable = false;
			syncQuality = LyricsFetcher.SyncQuality.NOT_FOUND; lyricSource = "Searching...";
		}
		session = next;
		currentKey = "";
		playbackClock.reset();
		return next.getOwner();
	}

	public void shutdown() {
		running = false;
		if (thread != null) thread.interrupt();
		fetchExecutor.shutdownNow();
		alternateExecutor.shutdownNow();
		try {
			dev.redstones.mediaplayerinfo.impl.win.WindowsMediaPlayerInfo.cleanup();
		} catch (Throwable ignored) {
		}
	}

	public float getPeak() {
		return peak;
	}

	public boolean isPlaying() {
		return playing && session != null;
	}

	public BeatReactor beatReactor() {
		return beatReactor;
	}

	public long getTrackChangedAt() {
		return trackChangedAt;
	}

	public String getLyricSource() {
		return session == null ? "Idle" : lyricSource;
	}

	public LyricsFetcher.SyncQuality getSyncQuality() {
		return syncQuality;
	}

	public boolean isFetching() {
		return fetchInFlight.get();
	}

	/**
	 * Everyday-language lyric status headline for Customization Studio & HUD diagnostics.
	 */
	public String getStatusHeadline() {
		if (session == null) {
			return "No active song playing";
		}
		if (fetchInFlight.get()) {
			return lyricsPlayable ? "Checking word timings · " + lyricSource : "Searching lyric sources...";
		}
		return switch (syncQuality) {
			case WORD_SYNCED -> "Word timing where available · " + lyricSource;
			case LINE_SYNCED -> "Line timed; words estimated · " + lyricSource;
			case ESTIMATED_PACE -> "Plain lyrics found (estimated pace) · " + lyricSource;
			case PLAIN_UNSYNCED -> "Plain lyrics found (unsynced) · " + lyricSource;
			case PROVIDER_UNAVAILABLE -> "No lyrics found; a provider is unavailable or disabled";
			case NOT_FOUND -> "No match found";
		};
	}

	public synchronized void reloadLyrics() {
		LyricsFetcher.clearCache();
		resultCache.clear();
		invalidateAlternateSearch();
		manualLyricsChosen = false;
		if (session == null) return;
		manualRetryRequested = true;
		trackEpoch.incrementAndGet();
		fetchInFlight.set(false);
		lyrics = List.of();
		rawLyrics = List.of();
		lyricsSynced = false;
		lyricsPlayable = false;
		syncQuality = LyricsFetcher.SyncQuality.NOT_FOUND;
		lyricSource = "Searching...";
		tries = 0;
		retryAt = 0L;
	}

    public long trackEpoch() { return trackEpoch.get(); }

    /** Reject stale results if a different song or provider preference arrived while searching. */
    public synchronized boolean useManualLyrics(long expectedEpoch, String expectedTrack, LyricsFetcher.Result result) {
        if (result == null || result.isEmpty() || session == null || !sessionKey(session).equals(expectedTrack)) return false;
        if (!trackEpoch.compareAndSet(expectedEpoch, expectedEpoch + 1)) return false;
        fetchInFlight.set(false); tries = 2; retryAt = Long.MAX_VALUE; manualRetryRequested = false;
        manualLyricsChosen = true;
        selectedAlternate = null;
        applyFetchedResult(expectedEpoch + 1, result);
        return true;
    }

	private static long monotonicMillis() { return System.nanoTime() / 1_000_000L; }

	public float rawPosition() {
		return playbackClock.positionAt(playing, monotonicMillis());
	}

	public float smoothPosition() {
		return playbackClock.positionAt(playing, monotonicMillis()) + getLyricsOffset();
	}

	/** Effective timing used by all lyric views: saved per-song delay plus quick adjustment. */
	public synchronized float getLyricsOffset() {
		return lyricsOffset + quickOffset.value();
	}

	public float getSavedLyricsOffset() {
		return lyricsOffset;
	}

	public float getQuickLyricsOffset() {
		return quickOffset.value();
	}

	/** A persistent timing action saves the effective result and replaces the temporary nudge. */
	public synchronized void setLyricsOffset(float offset) {
		float snapped = Math.round(Math.max(-10.0F, Math.min(10.0F, offset)) * 10.0F) / 10.0F;
		lyricsOffset = snapped;
		quickOffset.clearAdjustment();
		IMediaSession current = session;
		if (current != null && current.getMedia() != null) {
			LyricDelays.set(current.getMedia().getArtist(), current.getMedia().getTitle(), lyricsOffset);
		}
	}

	public synchronized void resetAllLyricOffsets() {
		LyricDelays.clearAll();
		lyricsOffset = 0.0F;
		quickOffset.clearAdjustment();
	}

	public synchronized void adjustLyricsOffset(float delta) {
		setLyricsOffset(getLyricsOffset() + delta);
	}

	/** Island buttons: never modify the saved delay or delays.json. */
	public synchronized void adjustQuickLyricsOffset(float delta) {
		if (session == null || session.getMedia() == null) return;
		quickOffset.adjust(lyricsOffset, delta);
	}

	public synchronized void resetQuickLyricsOffset() {
		quickOffset.clearAdjustment();
	}

	public boolean hasSession() {
		return session != null;
	}

	public List<LyricsFetcher.LyricLine> getLyrics() {
		return lyricsPlayable ? lyrics : List.of();
	}

	public List<LyricsFetcher.LyricLine> getRawLyrics() {
		return rawLyrics;
	}

	public boolean hasLocalMapping() {
		IMediaSession current = session;
		if (current == null || current.getMedia() == null) return false;
		return LocalLyrics.exists(current.getMedia().getArtist(), current.getMedia().getTitle());
	}

	public void forgetMapping() {
		IMediaSession current = session;
		if (current == null || current.getMedia() == null) return;
		LocalLyrics.delete(current.getMedia().getArtist(), current.getMedia().getTitle());
		reloadLyrics();
	}

	public synchronized void reloadLocal() {
		IMediaSession current = session;
		if (current == null || current.getMedia() == null) return;
		List<LyricsFetcher.LyricLine> local = loadLocalCandidate(
			current.getOwner() == null ? "" : current.getOwner(),
			current.getMedia().getArtist(),
			current.getMedia().getTitle()
		);
		if (!local.isEmpty()) {
			// A deliberate local mapping wins over late primary/alternate searches.
			trackEpoch.incrementAndGet();
			fetchInFlight.set(false);
			selectedAlternate = null;
			manualLyricsChosen = true;
			lyrics = local;
			rawLyrics = local;
			lyricsSynced = true;
			lyricsPlayable = true;
			syncQuality = LyricsFetcher.hasWordTiming(local)
					? LyricsFetcher.SyncQuality.WORD_SYNCED : LyricsFetcher.SyncQuality.LINE_SYNCED;
			lyricSource = "Local";
		}
	}

	public boolean isSynced() {
		return lyricsSynced;
	}

	public boolean needsMapping() {
		return !rawLyrics.isEmpty() && !lyricsSynced;
	}

	public int getMediaColor() {
		return mediaColor;
	}

	public Identifier getImage() {
		try {
			if (textureCache.size() > 10) {
				textureCache.clear();
				colorCache.clear();
			}
			IMediaSession cur = session;
			if (cur == null || cur.getMedia() == null) return null;
			byte[] data = cur.getMedia().getArtworkPng();
			if (data == null || data.length == 0) return null;
			int hash = Arrays.hashCode(data);
			Integer cachedColor = colorCache.get(hash);
			if (cachedColor != null) {
				mediaColor = cachedColor;
				return textureCache.get(hash);
			}
			NativeImage decoded = decodeImage(data);
			if (decoded == null) return null;
			Identifier id = com.lt5lyrics.client.island.render.IslandRender.id("temp/" + randomString());
			DynamicTexture texture = new DynamicTexture(() -> "lt5lyrics cover", decoded);
			mediaColor = averageColor(texture.getPixels(), 2);
			Minecraft.getInstance().getTextureManager().register(id, texture);
			textureCache.put(hash, id);
			colorCache.put(hash, mediaColor);
			return id;
		} catch (Exception e) {
			return null;
		}
	}

	private static NativeImage decodeImage(byte[] data) {
		try {
			return NativeImage.read(new ByteArrayInputStream(data));
		} catch (Exception ignored) {
			try {
				return NativeImage.read(new ByteArrayInputStream(toPng(data)));
			} catch (Exception e) {
				return null;
			}
		}
	}

	private static byte[] toPng(byte[] data) throws Exception {
		java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(new ByteArrayInputStream(data));
		if (image == null) throw new java.io.IOException("unsupported artwork format");
		java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
		javax.imageio.ImageIO.write(image, "png", out);
		return out.toByteArray();
	}

	/**
	 * Saturation-weighted vibrant accent extractor so dark album backgrounds do not muddy bright cover accents.
	 */
	public static int averageColor(NativeImage image, int step) {
		int width = image.getWidth();
		int height = image.getHeight();
		double weightedR = 0.0, weightedG = 0.0, weightedB = 0.0, totalWeight = 0.0;
		long plainR = 0L, plainG = 0L, plainB = 0L;
		int count = 0;

		for (int y = 0; y < height; y += Math.max(1, step)) {
			for (int x = 0; x < width; x += Math.max(1, step)) {
				int argb = image.getPixel(x, y);
				int r = (argb >> 16) & 0xFF;
				int g = (argb >> 8) & 0xFF;
				int b = argb & 0xFF;
				plainR += r;
				plainG += g;
				plainB += b;
				count++;

				int max = Math.max(r, Math.max(g, b));
				int min = Math.min(r, Math.min(g, b));
				float val = max / 255.0F;
				float sat = max == 0 ? 0.0F : (max - min) / (float) max;
				double w = (0.15 + sat * sat * 5.0) * (0.20 + val);
				weightedR += r * w;
				weightedG += g * w;
				weightedB += b * w;
				totalWeight += w;
			}
		}
		if (count == 0) return 0xFFFFFFFF;

		int baseR = totalWeight > 0.01 ? (int) Math.round(weightedR / totalWeight) : (int) (plainR / count);
		int baseG = totalWeight > 0.01 ? (int) Math.round(weightedG / totalWeight) : (int) (plainG / count);
		int baseB = totalWeight > 0.01 ? (int) Math.round(weightedB / totalWeight) : (int) (plainB / count);
		int rawRgb = 0xFF000000 | (ColorUtil.clampChannel(baseR) << 16)
			| (ColorUtil.clampChannel(baseG) << 8) | ColorUtil.clampChannel(baseB);

		float[] hsv = ColorUtil.rgbToHsv(rawRgb);
		float boostedSat = hsv[1] > 0.08F ? Math.min(0.92F, hsv[1] * 1.18F) : hsv[1];
		float boostedVal = Math.max(0.68F, Math.min(0.98F, hsv[2] + 0.16F));
		return ColorUtil.hsvToRgb(hsv[0], boostedSat, boostedVal);
	}

	private static String randomString() {
		StringBuilder sb = new StringBuilder(32);
		for (int i = 0; i < 32; i++) sb.append((char) (97 + RANDOM.nextInt(26)));
		return sb.toString();
	}

	public static String formatTime(long totalSeconds) {
		return String.format("%d:%02d", totalSeconds / 60L, totalSeconds % 60L);
	}
}
