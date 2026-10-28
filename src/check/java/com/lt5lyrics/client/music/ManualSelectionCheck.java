package com.lt5lyrics.client.music;

import com.lt5lyrics.client.island.IslandSettings;
import com.lt5lyrics.client.island.TestLyrics;
import dev.redstones.mediaplayerinfo.IMediaSession;
import dev.redstones.mediaplayerinfo.MediaInfo;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ManualSelectionCheck {
    private ManualSelectionCheck() {}
    private static IMediaSession fake(String title){MediaInfo info=new MediaInfo(title,"Test artist",new byte[0],0,120,true);
        return new IMediaSession(){public MediaInfo getMedia(){return info;}public String getOwner(){return "Spotify.exe";}
            public void play(){}public void pause(){}public void playPause(){}public void stop(){}public void next(){}public void previous(){}public void swapCycle(){}public int getCycleType(){return 0;}};}
    public static void run()throws Exception{
        MusicTracker t=new MusicTracker(false);Field session=MusicTracker.class.getDeclaredField("session");session.setAccessible(true);
        Method publish=MusicTracker.class.getDeclaredMethod("applyFetchedResult",long.class,LyricsFetcher.Result.class);publish.setAccessible(true);
        Method worker=MusicTracker.class.getDeclaredMethod("runAsyncLyricFetch",long.class,String.class,String.class,String.class,long.class,boolean.class);worker.setAccessible(true);
        Field flightField=MusicTracker.class.getDeclaredField("fetchInFlight");flightField.setAccessible(true);
        try{
            IslandSettings.lyricsEnabled=true;IslandSettings.worldText=true;assert TestLyrics.start(t)&&TestLyrics.active(t)&&!TestLyrics.lines().isEmpty();
            IMediaSession first=fake("Song A");session.set(t,first);assert !TestLyrics.active(t);
            long epoch=t.trackEpoch();String key=MusicTracker.sessionKey(first);
            var chosen=new LyricsFetcher.Result(List.of(new LyricsFetcher.LyricLine(1,"Chosen")),true,"Manual");
            var stale=new LyricsFetcher.Result(List.of(new LyricsFetcher.LyricLine(1,"Stale")),true,"Old");
            assert t.useManualLyrics(epoch,key,chosen);publish.invoke(t,epoch,stale);publish.invoke(t,epoch,new LyricsFetcher.Result(List.of(),false,"Empty"));
            assert t.getLyrics().get(0).text().equals("Chosen");AtomicBoolean flag=(AtomicBoolean)flightField.get(t);flag.set(true);
            worker.invoke(t,epoch,null,"","",0L,false);assert flag.get():"old finally must not clear newer in-flight work";
            assert !t.useManualLyrics(epoch,key,stale);session.set(t,fake("Song B"));assert !t.useManualLyrics(t.trackEpoch(),key,stale);
        }finally{TestLyrics.stop();t.shutdown();IslandSettings.resetAllSettingsToFactory();}
        MusicTracker failed=new MusicTracker(false);try{session.set(failed,fake("Error fixture"));AtomicBoolean flag=(AtomicBoolean)flightField.get(failed);flag.set(true);
            worker.invoke(failed,failed.trackEpoch(),null,"","",0L,false);assert !flag.get();assert failed.getStatusHeadline().contains("unavailable");
        }finally{failed.shutdown();}
        System.out.println("PASS: stale success, empty, finally and current error guards; preview handoff");
    }
}
