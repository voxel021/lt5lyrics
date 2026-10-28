package com.lt5lyrics.client.island;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lt5lyrics.client.music.LocalLyrics;
import com.lt5lyrics.client.music.LyricsFetcher;
import com.lt5lyrics.client.music.SyncTools;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class PlusFeaturesCheck {
    private PlusFeaturesCheck() {}
    public static void run() throws Exception {
        Path dir=Files.createTempDirectory("lt5-plus-");Path a=dir.resolve("settings.json"), b=dir.resolve("extra.json");
        PlusSettings.defaults();assert !PlusSettings.worldDecorations&&!PlusSettings.weatherTheme&&!PlusSettings.distanceSize&&!PlusSettings.finderArrow;
        assert PlusSettings.progressStyle==PlusSettings.ProgressStyle.CLASSIC && PlusSettings.songAnimation==PlusSettings.SongAnimation.CLASSIC;
        PlusSettings.saveTo(b);PlusSettings.worldDecorations=true;PlusSettings.favorites.add("GLOW");PlusSettings.saveTo(b);
        assert SettingsHistory.hasUndo(a,b);PlusSettings.loadFrom(b);assert PlusSettings.worldDecorations&&PlusSettings.favorites.contains("GLOW");
        assert SettingsHistory.undoLatest(a,b);PlusSettings.loadFrom(b);assert !PlusSettings.worldDecorations&&PlusSettings.favorites.isEmpty();
        SettingsHistory.write(a,"{\"value\":1,\"lastStudioTab\":0}");SettingsHistory.write(a,"{\"value\":1,\"lastStudioTab\":1}");assert !SettingsHistory.hasUndo(a,b);
        SettingsHistory.checkpoint(a,b);SettingsHistory.write(a,"{\"value\":2}");assert SettingsHistory.restoreCheckpoint(a,b);
        assert JsonParser.parseString(Files.readString(a)).getAsJsonObject().get("value").getAsInt()==1;
        Files.writeString(b,"{bad");PlusSettings.loadFrom(b);assert Files.readString(b).equals("{bad");PlusSettings.saveTo(b);assert Files.exists(dir.resolve("extra.json.invalid-backup"));
        JsonObject settings=new JsonObject();settings.addProperty("normalTextSize",.7F);settings.addProperty("musixmatchApiKey","SECRET");settings.addProperty("enableLrclib",true);settings.addProperty("follow",true);settings.addProperty("lyricsOffset",9);
        JsonObject extras=new JsonObject();extras.addProperty("worldDecorations",true);extras.addProperty("hideChat",true);extras.addProperty("apiKey","SECRET");
        JsonObject style=new JsonObject();style.addProperty("format","lt5lyrics-style-1");style.addProperty("name","Example");style.add("settings",settings);style.add("extras",extras);
        JsonObject clean=StylePresets.sanitize(style);assert !clean.toString().contains("SECRET")&&!clean.toString().contains("lyricsOffset")&&!clean.getAsJsonObject("extras").has("hideChat");
        JsonObject original=new JsonObject();original.addProperty("musixmatchApiKey","KEEP");original.addProperty("enableLrclib",false);original.addProperty("follow",false);
        JsonObject merged=StylePresets.mergeSettings(original,style);assert merged.get("musixmatchApiKey").getAsString().equals("KEEP")&&!merged.get("enableLrclib").getAsBoolean()&&!merged.get("follow").getAsBoolean();
        assert WorldIndicator.edge(0,0,10,70,1.7)==null;assert WorldIndicator.edge(20,0,1,70,1.7).x()>0;assert WorldIndicator.edge(-20,0,1,70,1.7).x()<0;
        assert WorldIndicator.distanceMultiplier(true,false,5,20)==2F && WorldIndicator.distanceMultiplier(true,false,5,0)==.65F && WorldIndicator.distanceMultiplier(true,true,5,20)==1F;
        var anchor=new net.minecraft.world.phys.Vec3(2,3,4);
        var placed=new WorldLyrics.Placed("Test",anchor,new net.minecraft.world.phys.Vec3(-1,0,0),new net.minecraft.world.phys.Vec3(0,1,0),.035F,null,5);
        placed.normalReferenceDistance=7;var copy=placed.withTextAndTranslation("Other","",5);assert copy.anchor.equals(anchor)&&copy.normalReferenceDistance==7;
        assert !ScreenHiding.classify(true,true,true,false,true,true,true);assert ScreenHiding.classify(true,false,false,true,false,true,false);assert !ScreenHiding.classify(false,false,false,false,true,true,true);
        assert Math.abs(SyncTools.offsetAtTap(10F,8.3F)-1.7F)<.001F && SyncTools.offsetAtTap(100,0)==10F;
        var lines=List.of(new LyricsFetcher.LyricLine(0,"First"),new LyricsFetcher.LyricLine(10,"Second"));LineSelection c=new LineSelection();
        c.update("A",1,lines);assert c.selected()==null;c.move(1,5);assert c.index()==0;c.move(1,5);assert c.index()==1;
        c.update("A",1,List.copyOf(lines));assert c.index()==1;c.update("A",2,lines);assert c.selected()==null;c.move(1,0);c.update("B",2,lines);assert c.selected()==null;
        c.move(1,0);c.update("B",2,List.of(new LyricsFetcher.LyricLine(0,"Changed")));assert c.selected()==null;
        String body="[{\"trackName\":\"One\",\"artistName\":\"Singer\",\"albumName\":\"Album\",\"duration\":120,\"syncedLyrics\":\"[00:01.00]One\"},{\"trackName\":\"Two\",\"artistName\":\"Singer\",\"plainLyrics\":\"one\\ntwo\"}]";
        var results=LyricsFetcher.parseManualLrclib(body,120,false);assert results.size()==2&&results.get(0).result().syncQuality()==LyricsFetcher.SyncQuality.LINE_SYNCED&&!results.get(1).result().playable();
        boolean enabled=IslandSettings.enableLrclib;IslandSettings.enableLrclib=false;
        try{LyricsFetcher.searchManualLrclib("Singer","One",120);throw new AssertionError("Disabled source queried");}
        catch(java.io.IOException expected){}finally{IslandSettings.enableLrclib=enabled;}
        Path src=dir.resolve("chosen.lrc"),dst=dir.resolve("old.lrc");String lrc="[00:05.00]<00:05.00>Shine <00:06.00>bright\n[00:05.00]Translation\n";Files.writeString(src,lrc);Files.writeString(dst,"[00:01.00]Old\n");
        var imported=LocalLyrics.importTo(src,dst);assert Files.readString(dst).equals(lrc)&&Files.readString(dir.resolve("old.lrc.before-import")).contains("Old")&&imported.get(0).hasWordSpans();
        Files.writeString(src,"not timed");try{LocalLyrics.importTo(src,dst);throw new AssertionError();}catch(java.io.IOException expected){}assert Files.readString(dst).equals(lrc);
        assert "Spotify".equals(PlayerBadge.detect("Spotify.exe"))&&"Chrome".equals(PlayerBadge.detect("chrome.exe"))&&PlayerBadge.detect("unknown")==null;
        PlusSettings.defaults();System.out.println("PASS: extra defaults, styles, history, selection, timing, indicator, hide, LRC and badge");
    }
}
