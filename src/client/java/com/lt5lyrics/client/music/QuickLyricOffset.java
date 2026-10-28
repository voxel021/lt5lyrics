package com.lt5lyrics.client.music;

import java.util.Objects;

/** In-memory timing nudge. It never reads or writes the saved per-song delays. */
final class QuickLyricOffset {
    private static final long MISSING_GRACE_NANOS = 2_000_000_000L;
    private static final int LIMIT_TENTHS = 100;

    private int tenths;
    private String track;
    private long previousPosition = -1;
    private long previousDuration;
    private boolean previousPlaying;
    private boolean pendingRestart;
    private boolean missingActive;
    private long missingSince;

    synchronized float value() {
        return tenths / 10.0F;
    }

    /** Limit the combined saved and temporary offset to the existing +/-10 second range. */
    synchronized void adjust(float savedOffset, float delta) {
        if (!Float.isFinite(savedOffset) || !Float.isFinite(delta)) return;
        int savedTenths = Math.round(savedOffset * 10.0F);
        int total = Math.clamp(savedTenths + tenths, -LIMIT_TENTHS, LIMIT_TENTHS);
        int next = Math.clamp(total + Math.round(delta * 10.0F), -LIMIT_TENTHS, LIMIT_TENTHS);
        tenths = next - savedTenths;
    }

    synchronized void clearAdjustment() {
        tenths = 0;
    }

    /** Ignore a very brief empty media-session poll; an actual session loss clears the nudge. */
    synchronized void missing(long nowNanos) {
        if (!missingActive) {
            missingActive = true;
            missingSince = nowNanos;
        }
        if (nowNanos - missingSince >= MISSING_GRACE_NANOS) forget();
    }

    /**
     * Update lifecycle from the unadjusted media timeline, never from lyric timing.
     * A stable jump to the beginning also counts as a restart (including repeat-one
     * without a reported duration); a one-poll zero or an ordinary seek does not.
     */
    synchronized void observe(String trackKey, long position, long duration,
                              float clockPosition, boolean playing, long nowNanos) {
        if (trackKey == null || trackKey.isBlank()) {
            missing(nowNanos);
            return;
        }
        if (missingActive && nowNanos - missingSince >= MISSING_GRACE_NANOS) forget();
        missingActive = false;
        if (!Objects.equals(track, trackKey)) {
            forget();
            track = trackKey;
        }

        boolean validPosition = position >= 0 && (duration <= 0 || position <= duration + 3);
        // If the source revises the track duration substantially, do not mistake its
        // changing timeline for a loop. Moving from unknown to known is also harmless.
        if (duration > 0 && previousDuration > 0 && Math.abs(duration - previousDuration) >= 5) {
            previousPosition = -1;
            previousPlaying = false;
            pendingRestart = false;
        }

        if (duration > 0 && ((validPosition && position >= duration)
                || (Float.isFinite(clockPosition) && clockPosition >= duration))) {
            clearAdjustment();
            pendingRestart = false;
        } else if (validPosition) {
            if (pendingRestart) {
                if (playing && position <= 2) clearAdjustment();
                pendingRestart = false;
            } else if (playing && previousPlaying && position <= 1 && previousPosition > position
                    && ((duration > 0 && previousPosition >= Math.max(1, duration - 2))
                        || (previousPosition >= 15 && previousPosition - position >= 14))) {
                // Confirm on the next poll, so a single bad zero from a media bridge
                // does not erase an adjustment in the middle of a song. Near-end
                // detection also covers short clips with a known duration.
                pendingRestart = true;
            }
        }
        if (validPosition) previousPosition = position;
        if (duration > 0) previousDuration = duration;
        previousPlaying = playing;
    }

    private void forget() {
        tenths = 0;
        track = null;
        previousPosition = -1;
        previousDuration = 0;
        previousPlaying = false;
        pendingRestart = false;
        missingActive = false;
        missingSince = 0;
    }
}
