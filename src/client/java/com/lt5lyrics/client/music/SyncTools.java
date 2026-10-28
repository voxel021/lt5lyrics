package com.lt5lyrics.client.music;
public final class SyncTools {
    private SyncTools() {}
    /** Positive offset makes lyrics appear earlier; tap sync saves the desired total offset. */
    public static float offsetAtTap(float lyricStart,float rawPlaybackPosition) {
        if(!Float.isFinite(lyricStart)||!Float.isFinite(rawPlaybackPosition)||lyricStart<0 || rawPlaybackPosition<0)
            throw new IllegalArgumentException("Playback or lyric time is unavailable.");
        return Math.round(Math.clamp(lyricStart-rawPlaybackPosition,-10.0F,10.0F)*10.0F)/10.0F;
    }
}
