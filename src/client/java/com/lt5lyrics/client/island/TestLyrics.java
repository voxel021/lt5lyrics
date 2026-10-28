package com.lt5lyrics.client.island;

import com.lt5lyrics.client.music.LyricsFetcher;
import com.lt5lyrics.client.music.MusicTracker;
import java.util.List;

/** Silent, temporary in-world preview. It must never take over an active player. */
public final class TestLyrics {
    private static long started;
    private static final List<LyricsFetcher.LyricLine> LINES=List.of(
        new LyricsFetcher.LyricLine(0,"LT5Lyrics test - no music needed"),
        new LyricsFetcher.LyricLine(8,"A little light follows the words"),
        new LyricsFetcher.LyricLine(16,"Try changing your style"),
        new LyricsFetcher.LyricLine(24,"World lyrics are just a preview"),
        new LyricsFetcher.LyricLine(32,"Your music stays in control"));
    private TestLyrics() {}
    public static synchronized boolean start(MusicTracker tracker) {
        if(tracker==null || tracker.hasSession())return false; started=System.currentTimeMillis();return true;
    }
    public static synchronized void stop(){started=0L;}
    public static synchronized boolean active(MusicTracker tracker){
        if(started==0L)return false;
        if(tracker==null || tracker.hasSession() || System.currentTimeMillis()-started>=60_000L){started=0L;return false;}
        return true;
    }
    public static List<LyricsFetcher.LyricLine> lines(){return LINES;}
    public static synchronized float position(MusicTracker tracker){return active(tracker)?((System.currentTimeMillis()-started)/1000.0F)%40.0F:tracker.smoothPosition();}
}
