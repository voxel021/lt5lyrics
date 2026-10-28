package com.lt5lyrics.client.island;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Only explicit appearance/layout fields may enter a named or exported style. */
public final class StylePresets {
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    private static final String FORMAT="lt5lyrics-style-1";
    private static final Set<String> SETTINGS=Set.of(
        "lyricGlowColor","skyGlowColor","animationSpeed","lyricAnimation","normalSide","skyTextSize",
        "separateSkyColors","separateHighlightColor","hudAccentColor","hudBeatPulse","hudUpcomingColor",
        "lyricTranslationColor","skyBeatPulse","customFontName","hologramScale","skyAngleDegrees",
        "lyricGlowIntensity","posY","posX","skyActiveColor","lyricOutlineThickness","visualizerStyle",
        "hologramShadow","normalOffset","autoAccentColor","normalTextSize","hudBgColor","normalDistance",
        "skyCurve","lyricPrimaryColor","lyricLineSpacing","lyricTextScale","normalLineSpacing","normalCurve",
        "skyGlow","skyHeight","lyricGlowEnabled","fontPreset","normalCurveEnabled","hudActiveColor",
        "hologramBgOpacity","hologramBackground","hologramBillboard","separateGlowColor","lyricSmoothness",
        "lyricHighlightColor","lyricOutlineColor","hudOpacity","normalWordsPerLine","skyBoundaryRadius","clockMode");
    private static final Set<String> EXTRAS=Set.of("progressStyle","finderArrow","distanceSize","songAnimation","worldDecorations","playerBadge","weatherTheme");
    private StylePresets() {}
    private static Path file(){return IslandSettings.configDir().resolve("style-presets.json");}
    private static JsonObject copyAllowed(JsonObject source,Set<String> keys) {
        JsonObject out=new JsonObject();
        for(String key:keys)if(source.has(key)&&source.get(key).isJsonPrimitive()) {
            JsonPrimitive p=source.getAsJsonPrimitive(key);
            if(!p.isString()||p.getAsString().length()<=128)out.add(key,p.deepCopy());
        }
        return out;
    }
    public static JsonObject sanitize(JsonObject in) throws IOException {
        if(in==null || !in.has("format") || !FORMAT.equals(in.get("format").getAsString())
            || !in.has("settings") || !in.get("settings").isJsonObject() || !in.has("extras") || !in.get("extras").isJsonObject())
            throw new IOException("Not a compatible LT5Lyrics style");
        JsonObject o=new JsonObject(); o.addProperty("format",FORMAT);
        o.addProperty("name",safeName(in.has("name")?in.get("name").getAsString():"Imported style"));
        o.add("settings",copyAllowed(in.getAsJsonObject("settings"),SETTINGS));
        o.add("extras",copyAllowed(in.getAsJsonObject("extras"),EXTRAS)); return o;
    }
    private static String safeName(String name) throws IOException {
        String n=name==null?"":name.trim().replaceAll("[\\p{Cntrl}]", " ");
        if(n.isBlank())throw new IOException("Enter a style name");
        return n.length()>48?n.substring(0,48):n;
    }
    private static JsonObject readCollection() throws IOException {
        Path p=file(); if(!Files.exists(p))return new JsonObject();
        try {
            if(Files.size(p)>4_194_304L)throw new IOException("Style collection is too large");
            JsonObject root=JsonParser.parseString(Files.readString(p,StandardCharsets.UTF_8)).getAsJsonObject();
            if(!root.has("presets")||!root.get("presets").isJsonObject())throw new IOException("Invalid style collection");
            return root.getAsJsonObject("presets");
        }catch(IllegalStateException|JsonParseException e){throw new IOException("Invalid style collection; original was kept",e);}
    }
    private static void writeCollection(JsonObject presets)throws IOException {
        JsonObject root=new JsonObject();root.addProperty("format",FORMAT);root.add("presets",presets);
        SettingsHistory.atomicReplace(file(),GSON.toJson(root));
    }
    public static synchronized List<String> names()throws IOException{return List.copyOf(readCollection().keySet());}
    public static synchronized JsonObject get(String name)throws IOException {
        JsonObject all=readCollection();if(!all.has(name)||!all.get(name).isJsonObject())throw new IOException("Style not found");
        return sanitize(all.getAsJsonObject(name));
    }
    public static synchronized String saveNamed(String name)throws IOException {
        String n=safeName(name); IslandSettings.save(); PlusSettings.save();
        JsonObject settings;
        try {settings=JsonParser.parseString(Files.readString(IslandSettings.configFile(),StandardCharsets.UTF_8)).getAsJsonObject();}
        catch(Exception e){throw new IOException("Could not read settings",e);}
        JsonObject style=new JsonObject();style.addProperty("format",FORMAT);style.addProperty("name",n);
        style.add("settings",copyAllowed(settings,SETTINGS));style.add("extras",copyAllowed(PlusSettings.toJson(),EXTRAS));
        JsonObject all=readCollection();all.add(n,style);writeCollection(all);return n;
    }
    public static JsonObject mergeSettings(JsonObject original,JsonObject style)throws IOException {
        JsonObject out=original.deepCopy(),cosmetic=sanitize(style).getAsJsonObject("settings");
        for(String k:cosmetic.keySet())out.add(k,cosmetic.get(k).deepCopy()); return out;
    }
    public static synchronized void apply(JsonObject style)throws IOException {
        JsonObject clean=sanitize(style); IslandSettings.save();
        JsonObject base;
        try{base=JsonParser.parseString(Files.readString(IslandSettings.configFile(),StandardCharsets.UTF_8)).getAsJsonObject();}
        catch(Exception e){throw new IOException("Could not read current settings",e);}
        IslandSettings.applyFromJson(mergeSettings(base,clean)); PlusSettings.applyVisual(clean.getAsJsonObject("extras"));
        IslandSettings.save();PlusSettings.save();WorldLyrics.onSkySettingsChanged();
    }
    public static synchronized void delete(String name)throws IOException {JsonObject all=readCollection();all.remove(name);writeCollection(all);}
    public static synchronized void exportFile(String name,Path destination)throws IOException {SettingsHistory.atomicReplace(destination,GSON.toJson(get(name)));}
    public static synchronized String importFile(Path source)throws IOException {
        if(!Files.isRegularFile(source)||Files.size(source)>2_097_152L)throw new IOException("Choose a style file under 2 MB");
        JsonObject style;
        try {style=sanitize(JsonParser.parseString(Files.readString(source,StandardCharsets.UTF_8)).getAsJsonObject());}
        catch(RuntimeException e){throw new IOException("Invalid style file",e);}
        JsonObject all=readCollection();String base=style.get("name").getAsString(), name=base; int i=2;
        while(all.has(name)){String suffix=" ("+(i++)+")";name=base.substring(0,Math.min(base.length(),48-suffix.length()))+suffix;}
        style.addProperty("name",name);all.add(name,style);writeCollection(all);return name;
    }
}
