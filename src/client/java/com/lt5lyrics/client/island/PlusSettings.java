package com.lt5lyrics.client.island;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/** Optional features are off or on the original appearance until explicitly selected. */
public final class PlusSettings {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public enum ProgressStyle { CLASSIC, SEGMENTS, DOTS; public ProgressStyle next() { return values()[(ordinal()+1)%values().length]; } }
    public enum SongAnimation { CLASSIC, GLOW, SLIDE; public SongAnimation next() { return values()[(ordinal()+1)%values().length]; } }
    public static boolean worldDecorations, weatherTheme, distanceSize, finderArrow, playerBadge, hideInventory, hideChat, hideOtherScreens;
    public static ProgressStyle progressStyle = ProgressStyle.CLASSIC;
    public static SongAnimation songAnimation = SongAnimation.CLASSIC;
    public static final Set<String> favorites = new LinkedHashSet<>();
    private PlusSettings() {}
    public static Path configFile() { return IslandSettings.configDir().resolve("extra-settings.json"); }
    public static void defaults() {
        worldDecorations=weatherTheme=distanceSize=finderArrow=playerBadge=false;
        hideInventory=hideChat=hideOtherScreens=false; progressStyle=ProgressStyle.CLASSIC; songAnimation=SongAnimation.CLASSIC; favorites.clear();
    }
    private static boolean bool(JsonObject j, String k, boolean old) {
        try { return j.has(k) && j.get(k).isJsonPrimitive() && j.getAsJsonPrimitive(k).isBoolean() ? j.get(k).getAsBoolean() : old; }
        catch (RuntimeException e) { return old; }
    }
    private static <T extends Enum<T>> T enumeration(JsonObject j,String key,Class<T> type,T fallback) {
        try { return Enum.valueOf(type,j.get(key).getAsString()); } catch(RuntimeException e) { return fallback; }
    }
    public static void load() {
        Path path = configFile();
        loadFrom(path);
        if (!Files.exists(path)) save();
    }
    public static void loadFrom(Path path) {
        defaults(); if(!Files.isRegularFile(path))return;
        try {
            JsonObject j=JsonParser.parseString(Files.readString(path,StandardCharsets.UTF_8)).getAsJsonObject();
            worldDecorations=bool(j,"worldDecorations",false); weatherTheme=bool(j,"weatherTheme",false);
            distanceSize=bool(j,"distanceSize",false); finderArrow=bool(j,"finderArrow",false); playerBadge=bool(j,"playerBadge",false);
            hideInventory=bool(j,"hideInventory",false); hideChat=bool(j,"hideChat",false); hideOtherScreens=bool(j,"hideOtherScreens",false);
            progressStyle=enumeration(j,"progressStyle",ProgressStyle.class,ProgressStyle.CLASSIC);
            songAnimation=enumeration(j,"songAnimation",SongAnimation.class,SongAnimation.CLASSIC);
            if(j.has("favorites")&&j.get("favorites").isJsonArray()) for(JsonElement el:j.getAsJsonArray("favorites"))
                if(el.isJsonPrimitive()&&el.getAsString().length()<=64&&favorites.size()<40) favorites.add(el.getAsString());
        } catch(Exception e) { org.slf4j.LoggerFactory.getLogger("lt5lyrics").warn("Extra settings could not be loaded; existing file was left untouched."); }
    }
    /** Only cosmetic extra values; hide rules and favorite controls are not style presets. */
    public static void applyVisual(JsonObject j) {
        worldDecorations = bool(j, "worldDecorations", worldDecorations);
        weatherTheme = bool(j, "weatherTheme", weatherTheme);
        distanceSize = bool(j, "distanceSize", distanceSize);
        finderArrow = bool(j, "finderArrow", finderArrow);
        playerBadge = bool(j, "playerBadge", playerBadge);
        progressStyle = enumeration(j, "progressStyle", ProgressStyle.class, progressStyle);
        songAnimation = enumeration(j, "songAnimation", SongAnimation.class, songAnimation);
    }

    public static JsonObject toJson() {
        JsonObject j=new JsonObject();
        j.addProperty("worldDecorations",worldDecorations); j.addProperty("weatherTheme",weatherTheme);
        j.addProperty("distanceSize",distanceSize); j.addProperty("finderArrow",finderArrow); j.addProperty("playerBadge",playerBadge);
        j.addProperty("hideInventory",hideInventory); j.addProperty("hideChat",hideChat); j.addProperty("hideOtherScreens",hideOtherScreens);
        j.addProperty("progressStyle",progressStyle.name()); j.addProperty("songAnimation",songAnimation.name());
        JsonArray a=new JsonArray(); for(String s:favorites)a.add(s); j.add("favorites",a); return j;
    }
    public static void save() {
        try { saveTo(configFile()); } catch(Throwable e) { org.slf4j.LoggerFactory.getLogger("lt5lyrics").warn("Could not save extra settings: {}",e.toString()); }
    }
    public static void saveTo(Path path) throws IOException {
        JsonObject out=new JsonObject();
        if(Files.isRegularFile(path)) {
            try { out=JsonParser.parseString(Files.readString(path,StandardCharsets.UTF_8)).getAsJsonObject(); }
            catch(Exception e) { Path corrupt=path.resolveSibling(path.getFileName()+".invalid-backup"); if(!Files.exists(corrupt))Files.copy(path,corrupt); }
        }
        JsonObject ours=toJson(); for(String k:ours.keySet())out.add(k,ours.get(k).deepCopy()); SettingsHistory.write(path,GSON.toJson(out));
    }
}
