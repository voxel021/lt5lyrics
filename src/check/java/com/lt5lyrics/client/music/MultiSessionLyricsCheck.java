package com.lt5lyrics.client.music;

import com.lt5lyrics.client.island.IslandSettings;
import dev.redstones.mediaplayerinfo.IMediaSession;
import dev.redstones.mediaplayerinfo.MediaInfo;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/** End-to-end fake media snapshots and provider results; no network or native SMTC. */
public final class MultiSessionLyricsCheck {
    private static final long WAIT_MS=3000;
    private MultiSessionLyricsCheck() {}

    public static void run() throws Exception {
        Object loader=net.fabricmc.loader.api.FabricLoader.getInstance();
        java.lang.reflect.Field config=loader.getClass().getDeclaredField("configDir");config.setAccessible(true);
        Object previous=config.get(loader);
        config.set(loader,java.nio.file.Files.createTempDirectory("lt5-session-choice-"));
        IslandSettings.resetAllSettingsToFactory();
        try {
            backgroundSongWinsAndUsesItsClock();
            currentWithLyricsStaysStable();
            manualChoiceAndReturnToAuto();
            pausedIsNotProbed();
            changedCandidateIsNotSelected();
            provisionalThenWordUpgrade();
            explicitLyricChoiceBeatsLateCandidate();
            localMappingBeatsLateCandidate();
            unplayableAndNegativeCache();
            resetRejectsLateProbe();
        } finally { IslandSettings.resetAllSettingsToFactory();config.set(loader,previous); }
        System.out.println("PASS: playing-session lyric priority, whole-player clock, manual/automatic choice, paused/stale guards, provisional upgrade and request throttling");
    }

    private static IMediaSession session(String owner,String title,long pos,boolean playing) {
        MediaInfo info=new MediaInfo(title,"Fixture Artist",new byte[0],pos,240,playing);
        return new IMediaSession() {
            public MediaInfo getMedia(){return info;} public String getOwner(){return owner;}
            public void play(){} public void pause(){} public void playPause(){} public void stop(){}
            public void next(){} public void previous(){} public void swapCycle(){} public int getCycleType(){return 0;}
        };
    }
    private static LyricsFetcher.Result timed(String text) {
        return new LyricsFetcher.Result(List.of(new LyricsFetcher.LyricLine(0,text)), true, "Fixture");
    }
    private static LyricsFetcher.Result words(String text) {
        var line=new LyricsFetcher.LyricLine(0,text,"",List.of(new LyricsFetcher.WordSpan(0,1,0,1),new LyricsFetcher.WordSpan(1,2,1,2)));
        return new LyricsFetcher.Result(List.of(line), List.of(line),true,true,"Word fixture",LyricsFetcher.SyncQuality.WORD_SYNCED);
    }
    private static boolean has(MusicTracker t,String text) {
        return !t.getLyrics().isEmpty() && t.getLyrics().getFirst().text().equals(text);
    }
    private static void driveUntil(MusicTracker t,List<IMediaSession> sessions,BooleanSupplier done,String label) throws Exception {
        long end=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(WAIT_MS);
        while(System.nanoTime()<end) {
            t.pollSessions(sessions);
            if(done.getAsBoolean())return;
            Thread.sleep(5);
        }
        throw new AssertionError("Timed out: "+label+"; source="+(t.getSession()==null?"none":t.getSession().getOwner())+" status="+t.getStatusHeadline());
    }
    private static void driveFor(MusicTracker t,List<IMediaSession> sessions,int count) throws Exception {
        for(int i=0;i<count;i++){t.pollSessions(sessions);Thread.sleep(3);}
    }
    private static void waitLatch(CountDownLatch latch) {
        try { if(!latch.await(3,TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout"); }
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
    }

    private static void backgroundSongWinsAndUsesItsClock() throws Exception {
        var video=session("chrome.exe","fixture ordinary video",9,true);
        var song=session("Spotify.exe","fixture background song",73,true);
        CountDownLatch releaseVideo=new CountDownLatch(1), videoEntered=new CountDownLatch(1);
        var t=new MusicTracker(false,(owner,artist,title,duration,bypass,provisional)->{
            if(owner.equals("chrome.exe")){videoEntered.countDown();waitLatch(releaseVideo);return LyricsFetcher.Result.EMPTY;}
            return timed("background words");
        });
        try {
            var list=List.of(video,song);
            t.pollSessions(list);
            assert videoEntered.await(2,TimeUnit.SECONDS);
            driveUntil(t,list,()->t.getSession()==song && has(t,"background words"),"background player with lyrics");
            assert t.isPlaying() && t.getSession().getMedia().getTitle().equals("fixture background song");
            assert t.rawPosition()>=73 && t.rawPosition()<76 : "must use background player's clock, not video clock: "+t.rawPosition();
            releaseVideo.countDown(); driveFor(t,list,30);
            assert t.getSession()==song && has(t,"background words") : "old foreground result cannot overwrite or take back selection";
        } finally { releaseVideo.countDown();t.shutdown(); }
    }

    private static void currentWithLyricsStaysStable() throws Exception {
        var video=session("chrome.exe","fixture current song",13,true);
        var spotify=session("Spotify.exe","fixture also playing",51,true);
        CountDownLatch alternateGate=new CountDownLatch(1);
        var t=new MusicTracker(false,(o,a,title,d,b,p)->{
            if(o.equals("Spotify.exe")){waitLatch(alternateGate);return timed("other words");}
            return timed("current words");
        });
        try {
            var list=List.of(video,spotify);
            driveUntil(t,list,()->has(t,"current words"),"current song result");
            alternateGate.countDown();driveFor(t,list,40);
            assert t.getSession()==video && has(t,"current words") : "do not bounce between two lyric-capable players";
        } finally {alternateGate.countDown();t.shutdown();}
    }

    private static void manualChoiceAndReturnToAuto() throws Exception {
        var song=session("Spotify.exe","fixture manual song",65,true);
        var video=session("chrome.exe","fixture manually chosen video",8,true);
        CountDownLatch songGate=new CountDownLatch(1), songEntered=new CountDownLatch(1);
        var t=new MusicTracker(false,(o,a,title,d,b,p)->{
            if(o.equals("Spotify.exe")){songEntered.countDown();waitLatch(songGate);return timed("spotify words");}
            return LyricsFetcher.Result.EMPTY;
        });
        try {
            var list=List.of(song,video);
            t.pollSessions(list);assert songEntered.await(2,TimeUnit.SECONDS);
            assert "chrome.exe".equals(t.cyclePreferredSource());
            songGate.countDown(); driveFor(t,list,100);
            assert t.getSession()==video && t.getLyrics().isEmpty() : "manual player wins even without lyrics";
            assert t.cyclePreferredSource()!=null : "cycle after last player reaches automatic mode";
            driveUntil(t,list,()->t.getSession()==song && has(t,"spotify words"),"return to automatic lyric-aware choice");
        } finally {songGate.countDown();t.shutdown();}
    }

    private static void pausedIsNotProbed() throws Exception {
        var video=session("chrome.exe","fixture clip",9,true);
        var paused=session("Spotify.exe","fixture paused",40,false);
        var playing=session("Spotify.exe","fixture paused",40,true);
        AtomicInteger probes=new AtomicInteger();
        var t=new MusicTracker(false,(o,a,title,d,b,p)->{
            if(o.equals("Spotify.exe")){probes.incrementAndGet();return timed("resumed song");}
            return LyricsFetcher.Result.EMPTY;
        });
        try {
            var list=List.of(video,paused); driveFor(t,list,60);
            assert probes.get()==0 : "never probe a paused session";
            var resumed=List.of(video,playing);
            driveUntil(t,resumed,()->t.getSession()==playing && has(t,"resumed song"),"resumed player");
            assert probes.get()==1;
        } finally {t.shutdown();}
    }

    private static void changedCandidateIsNotSelected() throws Exception {
        var video=session("chrome.exe","fixture primary clip",8,true);
        var oldSong=session("Spotify.exe","fixture changed A",20,true);
        var newSong=session("Spotify.exe","fixture changed B",5,true);
        CountDownLatch entered=new CountDownLatch(1), gate=new CountDownLatch(1);
        AtomicInteger newProbes=new AtomicInteger();
        var t=new MusicTracker(false,(o,a,title,d,b,p)->{
            if(title.endsWith(" A")){entered.countDown();waitLatch(gate);return timed("obsolete A");}
            if(title.endsWith(" B")){newProbes.incrementAndGet();return LyricsFetcher.Result.EMPTY;}
            return LyricsFetcher.Result.EMPTY;
        });
        try {
            var first=List.of(video,oldSong);t.pollSessions(first);assert entered.await(2,TimeUnit.SECONDS);
            var changed=List.of(video,newSong);t.pollSessions(changed);gate.countDown();
            driveUntil(t,changed,()->newProbes.get()>0,"probe new song after old completes");
            driveFor(t,changed,60);
            assert t.getSession()==video && !has(t,"obsolete A") : "stale title must never be paired with new track";
        } finally {gate.countDown();t.shutdown();}
    }

    private static void provisionalThenWordUpgrade() throws Exception {
        var video=session("chrome.exe","fixture unmusical clip",8,true);
        var song=session("Spotify.exe","fixture upgrade song",90,true);
        CountDownLatch provisionalSent=new CountDownLatch(1), finish=new CountDownLatch(1);
        var t=new MusicTracker(false,(o,a,title,d,b,provisional)->{
            if(o.equals("Spotify.exe")){
                provisional.accept(timed("line result"));provisionalSent.countDown();waitLatch(finish);return words("word result");
            }
            return LyricsFetcher.Result.EMPTY;
        });
        try {
            var list=List.of(video,song);t.pollSessions(list);assert provisionalSent.await(2,TimeUnit.SECONDS);
            driveUntil(t,list,()->t.getSession()==song && has(t,"line result"),"provisional line match");
            assert t.isFetching() : "word check continues without hiding the provisional result";
            finish.countDown();driveUntil(t,list,()->has(t,"word result") && !t.isFetching(),"word-timing upgrade");
            assert t.getSyncQuality()==LyricsFetcher.SyncQuality.WORD_SYNCED;
        } finally {finish.countDown();t.shutdown();}
    }

    private static void explicitLyricChoiceBeatsLateCandidate() throws Exception {
        var video=session("chrome.exe","fixture explicit choice",12,true);
        var song=session("Spotify.exe","fixture other player",50,true);
        CountDownLatch alternateEntered=new CountDownLatch(1), gate=new CountDownLatch(1);
        var t=new MusicTracker(false,(o,a,title,d,b,p)->{
            if(o.equals("Spotify.exe")){alternateEntered.countDown();waitLatch(gate);return timed("late alternate");}
            return LyricsFetcher.Result.EMPTY;
        });
        try {
            var list=List.of(video,song);t.pollSessions(list);assert alternateEntered.await(2,TimeUnit.SECONDS);
            assert t.useManualLyrics(t.trackEpoch(),MusicTracker.sessionKey(video),timed("user selected"));
            gate.countDown();driveFor(t,list,80);
            assert t.getSession()==video && has(t,"user selected") : "manual lyric choice is authoritative";
        } finally {gate.countDown();t.shutdown();}
    }

    private static void localMappingBeatsLateCandidate() throws Exception {
        var video=session("chrome.exe","fixture local mapping",12,true);
        var song=session("Spotify.exe","fixture local alternate",50,true);
        CountDownLatch entered=new CountDownLatch(1), gate=new CountDownLatch(1);
        var t=new MusicTracker(false,(o,a,title,d,b,p)->{
            if(o.equals("Spotify.exe")){entered.countDown();waitLatch(gate);return timed("late mapped alternative");}
            return LyricsFetcher.Result.EMPTY;
        });
        try {
            var list=List.of(video,song);t.pollSessions(list);assert entered.await(2,TimeUnit.SECONDS);
            assert LocalLyrics.save("Fixture Artist","fixture local mapping",List.of(new LyricsFetcher.LyricLine(1,"local mapping")));
            t.reloadLocal();gate.countDown();driveFor(t,list,80);
            assert t.getSession()==video && has(t,"local mapping") : "local LRC wins over late probes";
        } finally {gate.countDown();t.shutdown();}
    }

    private static void unplayableAndNegativeCache() throws Exception {
        var video=session("chrome.exe","fixture primary none",5,true);
        var song=session("Spotify.exe","fixture plain no timing",40,true);
        var counts=new ConcurrentHashMap<String,AtomicInteger>();
        var plain=new LyricsFetcher.Result(List.of(),List.of(new LyricsFetcher.LyricLine(-1,"plain only")),false,false,"plain",LyricsFetcher.SyncQuality.PLAIN_UNSYNCED);
        var t=new MusicTracker(false,(o,a,title,d,b,p)->{
            counts.computeIfAbsent(o,k->new AtomicInteger()).incrementAndGet();
            return o.equals("Spotify.exe") ? plain : LyricsFetcher.Result.EMPTY;
        });
        try {
            var list=List.of(video,song);
            driveUntil(t,list,()->counts.containsKey("Spotify.exe"),"probe plain candidate");
            driveFor(t,list,120);
            assert t.getSession()==video && t.getLyrics().isEmpty() : "non-playable plain data is not mistaken for displayed lyrics";
            assert counts.get("Spotify.exe").get()==1 : "negative candidate result is cached, not queried every poll";
            assert counts.get("chrome.exe").get()==1;
        } finally {t.shutdown();}
    }

    private static void resetRejectsLateProbe() throws Exception {
        var video=session("chrome.exe","fixture reset video",3,true);
        var song=session("Spotify.exe","fixture reset song",50,true);
        CountDownLatch entered=new CountDownLatch(1), gate=new CountDownLatch(1);
        AtomicInteger calls=new AtomicInteger();
        var t=new MusicTracker(false,(o,a,title,d,b,p)->{
            if(o.equals("Spotify.exe")) {
                int n=calls.incrementAndGet();
                if(n==1){entered.countDown();waitLatch(gate);return timed("before reset");}
                return LyricsFetcher.Result.EMPTY;
            }
            return LyricsFetcher.Result.EMPTY;
        });
        try {
            var list=List.of(video,song);t.pollSessions(list);assert entered.await(2,TimeUnit.SECONDS);
            t.reloadLyrics();gate.countDown();
            driveUntil(t,list,()->calls.get()>=2,"retry after search reset");driveFor(t,list,60);
            assert t.getSession()==video && !has(t,"before reset") : "reset invalidates an older in-flight result";
        } finally {gate.countDown();t.shutdown();}
    }
}
