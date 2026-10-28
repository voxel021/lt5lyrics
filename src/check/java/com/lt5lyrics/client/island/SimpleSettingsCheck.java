package com.lt5lyrics.client.island;

import org.lwjgl.sdl.SDLKeycode;
import com.mojang.blaze3d.platform.InputConstants;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lt5lyrics.client.music.LyricsFetcher;
import com.lt5lyrics.client.music.MusicTracker;
import dev.redstones.mediaplayerinfo.MediaInfo;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;


/** Pure model, persistence, modal, mode-switch and visibility regression checks. */
public final class SimpleSettingsCheck {
    private SimpleSettingsCheck() {}

    public static void run() throws Exception {
        presetsAndCustom();
        modeSwitchAndNavigation();
        selectiveResetAndConfirmation();
        persistenceAndMigration();
        colorReloadAndLegacyInference();
        globalVisibility();
        assert MusicIsland.embeddedScaleFor(640, 360, 1) > 1.3F;
        assert MusicIsland.embeddedScaleFor(427, 240, 2) == 1.0F;
        assert MusicIsland.embeddedScaleFor(320, 240, 1) == 1.0F;
        IslandSettings.resetAllSettingsToFactory();
        System.out.println("PASS: Simple settings presets, Custom, shared mode, selective reset, config migration and visibility");
    }

    private static boolean eq(double a, double b) { return Math.abs(a - b) < .0001; }

    private static void presetsAndCustom() {
        IslandSettings.resetAllSettingsToFactory();
        var world = IslandSettings.DisplayMode.WORLD_3D;
        var sky = IslandSettings.DisplayMode.SKY_LYRICS;
        var hud = IslandSettings.DisplayMode.HUD_ONLY;
        SimpleSettings.Size[] sizes = {SimpleSettings.Size.SMALL, SimpleSettings.Size.MEDIUM, SimpleSettings.Size.LARGE};
        double[] worldValues = {.70, 1.0, 1.5}, skyValues = {.30, .50, .80};
        for (int i = 0; i < sizes.length; i++) {
            IslandSettings.skyTextSize = .42F;
            SimpleSettings.setSize(world, sizes[i]);
            assert SimpleSettings.size(world) == sizes[i] && eq(IslandSettings.normalTextSize, worldValues[i]);
            assert eq(IslandSettings.skyTextSize, .42) : "World size must not change Sky";
            IslandSettings.normalTextSize = 1.27F;
            SimpleSettings.setSize(sky, sizes[i]);
            assert SimpleSettings.size(sky) == sizes[i] && eq(IslandSettings.skyTextSize, skyValues[i]);
            assert eq(IslandSettings.normalTextSize, 1.27) : "Sky size must not change World";
        }
        IslandSettings.skyTextSize = .44F;
        assert SimpleSettings.size(sky) == SimpleSettings.Size.CUSTOM;
        SimpleSettings.setSize(sky, SimpleSettings.Size.CUSTOM);
        SimpleSettings.setSize(hud, SimpleSettings.Size.LARGE);
        assert eq(IslandSettings.skyTextSize, .44) && eq(IslandSettings.normalTextSize, 1.27);
        assert SimpleSettings.size(hud) == SimpleSettings.Size.CUSTOM;

        SimpleSettings.Distance[] distances = {SimpleSettings.Distance.NEAR, SimpleSettings.Distance.MEDIUM, SimpleSettings.Distance.FAR};
        double[] blocks = {120, 360, 720};
        for (int i = 0; i < distances.length; i++) {
            SimpleSettings.setDistance(distances[i]);
            assert SimpleSettings.distance() == distances[i] && eq(IslandSettings.skyBoundaryRadius, blocks[i]);
        }
        IslandSettings.skyBoundaryRadius = 278;
        assert SimpleSettings.distance() == SimpleSettings.Distance.CUSTOM;
        SimpleSettings.setDistance(SimpleSettings.Distance.CUSTOM);
        assert IslandSettings.skyBoundaryRadius == 278;

        IslandSettings.normalSide = IslandSettings.NormalSide.RANDOM;
        IslandSettings.normalOffset = -7.25;
        assert SimpleSettings.position().equals("Custom");
        IslandSettings.setNormalSide(IslandSettings.NormalSide.RIGHT);
        assert SimpleSettings.position().equals("Right") && eq(IslandSettings.normalOffset, 7.25);
        IslandSettings.setNormalSide(IslandSettings.NormalSide.LEFT);
        assert SimpleSettings.position().equals("Left") && eq(IslandSettings.normalOffset, -7.25);
        IslandSettings.setNormalSide(IslandSettings.NormalSide.ALTERNATE);
        assert SimpleSettings.position().equals("Alternate") && eq(IslandSettings.normalOffset, -7.25);

        IslandSettings.hologramBackground = true;
        IslandSettings.hologramBgOpacity = .47F;
        assert SimpleSettings.background() == SimpleSettings.Background.CUSTOM;
        SimpleSettings.setBackground(SimpleSettings.Background.OFF);
        assert SimpleSettings.background() == SimpleSettings.Background.OFF && eq(IslandSettings.hologramBgOpacity, .47);
        SimpleSettings.setBackground(SimpleSettings.Background.SOFT);
        assert SimpleSettings.background() == SimpleSettings.Background.SOFT && eq(IslandSettings.hologramBgOpacity, .32);
        SimpleSettings.setBackground(SimpleSettings.Background.STRONG);
        assert SimpleSettings.background() == SimpleSettings.Background.STRONG && eq(IslandSettings.hologramBgOpacity, .65);
        SimpleSettings.setBackground(SimpleSettings.Background.CUSTOM);
        assert SimpleSettings.background() == SimpleSettings.Background.STRONG;

        IslandSettings.separateSkyColors = false;
        IslandSettings.separateHighlightColor = true;
        IslandSettings.separateGlowColor = true;
        IslandSettings.lyricHighlightColor = 0xFF223344;
        IslandSettings.lyricGlowColor = 0xFF334455;
        IslandSettings.hudActiveColor = 0xFF445566;
        IslandSettings.skyActiveColor = 0xFF556677;
        for (SimpleSettings.Color c : SimpleSettings.Color.values()) {
            if (c == SimpleSettings.Color.CUSTOM) continue;
            SimpleSettings.setColor(world, c);
            assert SimpleSettings.color(world) == c && SimpleSettings.color(sky) == c && SimpleSettings.color(hud) == c;
        }
        assert IslandSettings.hudActiveColor == 0xFF445566 && IslandSettings.skyActiveColor == 0xFF556677;
        assert IslandSettings.lyricHighlightColor == 0xFF223344 && IslandSettings.lyricGlowColor == 0xFF334455;
        assert IslandSettings.separateHighlightColor && IslandSettings.separateGlowColor && !IslandSettings.separateSkyColors;
        IslandSettings.lyricPrimaryColor = 0xFF102030;
        assert SimpleSettings.color(world) == SimpleSettings.Color.CUSTOM;
        IslandSettings.separateSkyColors = true;
        SimpleSettings.setColor(sky, SimpleSettings.Color.PINK);
        assert SimpleSettings.color(sky) == SimpleSettings.Color.PINK;
        assert IslandSettings.lyricPrimaryColor == 0xFF102030 && IslandSettings.separateSkyColors;
        SimpleSettings.setColor(world, SimpleSettings.Color.BLUE);
        assert SimpleSettings.color(world) == SimpleSettings.Color.BLUE && SimpleSettings.color(sky) == SimpleSettings.Color.PINK;
    }

    private static SettingsPanel panel() {
        return new SettingsPanel(null, new SettingsPanel.HostCallbacks() {
            public void onOpenMapper() {} public void onStartDragHud() {}
            public void onOpenFullScreen() {} public void onClosePanel() {}
        });
    }

    private static JsonObject snapshot(Path path) throws Exception {
        IslandSettings.saveToFile(path);
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static void modeSwitchAndNavigation() throws Exception {
        Path cfg = Files.createTempFile("lt5-simple-mode-", ".json");
        try {
            IslandSettings.resetAllSettingsToFactory();
            IslandSettings.advancedSettingsMode = false;
            IslandSettings.lastStudioTab = 4;
            IslandSettings.lyricsEnabled = false;
            IslandSettings.normalTextSize = 1.37F;
            IslandSettings.skyTextSize = .46F;
            IslandSettings.skyBoundaryRadius = 278;
            IslandSettings.separateSkyColors = true;
            IslandSettings.skyActiveColor = 0xFF123456;
            IslandSettings.lyricOutlineThickness = .73F;
            IslandSettings.musixmatchApiKey = "test-key-unchanged";
            JsonObject before = snapshot(cfg);
            SettingsPanel p = panel();
            p.onOpened();
            assert !IslandSettings.advancedSettingsMode : "Remembered Advanced tab must not override Simple";
            assert IslandSettings.lastStudioTab == 4;
            Method switchMode = SettingsPanel.class.getDeclaredMethod("switchSettingsMode", boolean.class);
            switchMode.setAccessible(true);
            switchMode.invoke(p, true);
            JsonObject advanced = snapshot(cfg);
            assert advanced.get("advancedSettingsMode").getAsBoolean();
            advanced.addProperty("advancedSettingsMode", false);
            assert advanced.equals(before) : "Only the mode preference may change";
            switchMode.invoke(p, false);
            assert snapshot(cfg).equals(before) : "Round-trip through views must preserve every setting";
            p.handleKeyPress(InputConstants.KEY_TAB, SDLKeycode.SDL_KMOD_CTRL, true);
            p.handleKeyPress(InputConstants.KEY_RIGHT, 0, false);
            assert !IslandSettings.advancedSettingsMode && IslandSettings.lastStudioTab == 4;
            assert snapshot(cfg).equals(before) : "Simple navigation must not change Advanced category or settings";
        } finally { Files.deleteIfExists(cfg); }
    }

    private static void selectiveResetAndConfirmation() throws Exception {
        Path cfg = Files.createTempFile("lt5-simple-reset-", ".json");
        try {
            IslandSettings.resetAllSettingsToFactory();
            IslandSettings.advancedSettingsMode = false; IslandSettings.lastStudioTab = 4;
            IslandSettings.lyricsEnabled = false; IslandSettings.worldText = true; IslandSettings.skyLyrics = true;
            IslandSettings.showHud = false; IslandSettings.showTranslations = false;
            IslandSettings.normalSide = IslandSettings.NormalSide.LEFT; IslandSettings.normalOffset = -9.75;
            IslandSettings.normalTextSize = 1.43F; IslandSettings.skyTextSize = .43F;
            IslandSettings.skyCrosshairFollow = true; IslandSettings.skyBoundaryRadius = 614;
            IslandSettings.skyContextLines = false; IslandSettings.skySongHeader = false;
            IslandSettings.hologramBackground = false; IslandSettings.hologramBgOpacity = .71F;
            IslandSettings.separateSkyColors = true; IslandSettings.skyActiveColor = 0xFF123456;
            IslandSettings.lyricPrimaryColor = 0xFF123456;
            IslandSettings.separateGlowColor = true; IslandSettings.lyricGlowColor = 0xFF112233;
            IslandSettings.lyricOutlineThickness = .72F; IslandSettings.lyricTextScale = 1.35F;
            IslandSettings.hologramScale = 1.45F; IslandSettings.skyHeight = 98;
            IslandSettings.lyricAnimation = IslandSettings.LyricAnimation.WAVE_RIPPLE;
            IslandSettings.enableLrcmux = false; IslandSettings.musixmatchApiKey = "not-deleted";
            IslandSettings.betterLyricsApiKey = "also-not-deleted";
            IslandSettings.providerPriority = new ArrayList<>(List.of("kugou", "lrclib"));
            JsonObject before = snapshot(cfg);
            SettingsPanel p = panel();
            Method request = SettingsPanel.class.getDeclaredMethod("requestConfirmation", String.class, String.class, Runnable.class);
            request.setAccessible(true);
            request.invoke(p, "Reset Simple?", "Only Simple choices", (Runnable) SimpleSettings::resetSimpleSettings);
            assert p.handleKeyPress(InputConstants.KEY_ESCAPE, 0, false);
            assert snapshot(cfg).equals(before) : "Cancel must not modify settings";
            request.invoke(p, "Reset Simple?", "Only Simple choices", (Runnable) SimpleSettings::resetSimpleSettings);
            assert p.handleKeyPress(InputConstants.KEY_RETURN, 0, false);
            JsonObject after = snapshot(cfg);
            Set<String> allowed = Set.of("lyricsEnabled", "worldText", "skyLyrics", "showTranslations", "normalSide",
                "normalOffset", "normalTextSize", "skyTextSize", "skyCrosshairFollow", "skyBoundaryRadius",
                "skyContextLines", "skySongHeader", "lyricPrimaryColor", "skyActiveColor", "hologramBackground", "hologramBgOpacity");
            assert before.keySet().equals(after.keySet());
            for (String key : before.keySet()) {
                if (!allowed.contains(key)) assert before.get(key).equals(after.get(key)) : "Simple reset changed " + key;
            }
            assert IslandSettings.lyricsEnabled && IslandSettings.worldText && !IslandSettings.skyLyrics;
            assert IslandSettings.showTranslations && !IslandSettings.showHud;
            assert IslandSettings.normalSide == IslandSettings.NormalSide.RIGHT && eq(IslandSettings.normalOffset, 9.75);
            assert eq(IslandSettings.normalTextSize, 1) && eq(IslandSettings.skyTextSize, .5);
            assert !IslandSettings.skyCrosshairFollow && eq(IslandSettings.skyBoundaryRadius, 360);
            assert IslandSettings.skyContextLines && IslandSettings.skySongHeader;
            assert IslandSettings.separateSkyColors && IslandSettings.skyActiveColor == IslandSettings.DEFAULT_SKY_ACTIVE_COLOR;
            assert IslandSettings.lyricPrimaryColor == IslandSettings.DEFAULT_LYRIC_PRIMARY_COLOR;
            assert IslandSettings.hologramBackground && eq(IslandSettings.hologramBgOpacity, .32);
            assert IslandSettings.lyricAnimation == IslandSettings.LyricAnimation.WAVE_RIPPLE;
            assert IslandSettings.musixmatchApiKey.equals("not-deleted") && IslandSettings.betterLyricsApiKey.equals("also-not-deleted");
            IslandSettings.separateSkyColors = false; IslandSettings.skyActiveColor = 0xFF102030;
            SimpleSettings.resetSimpleSettings();
            assert IslandSettings.skyActiveColor == 0xFF102030 : "Dormant separate Sky color must not be erased";
        } finally { Files.deleteIfExists(cfg); }
    }

    private static void persistenceAndMigration() throws Exception {
        Path dir = Files.createTempDirectory("lt5-simple-config-");
        Path cfg = dir.resolve("settings.json");
        try {
            IslandSettings.resetAllSettingsToFactory();
            Files.writeString(cfg, "{\"worldText\":true,\"skyLyrics\":true,\"showHud\":false,"
                + "\"advancedSettingsMode\":false,\"lastStudioTab\":4,\"skyTextSize\":0.43,"
                + "\"skyHeight\":70,\"musixmatchApiKey\":\"legacy-key\",\"futureUserData\":{\"keep\":42}}\n");
            IslandSettings.lyricsEnabled = false; IslandSettings.loadFromFile(cfg);
            assert IslandSettings.lyricsEnabled : "Legacy settings must preserve old enabled behavior";
            assert IslandSettings.worldText && IslandSettings.skyLyrics && !IslandSettings.showHud;
            assert !IslandSettings.advancedSettingsMode && IslandSettings.lastStudioTab == 4;
            assert eq(IslandSettings.skyTextSize, .43) && eq(IslandSettings.skyHeight, 70);
            assert IslandSettings.musixmatchApiKey.equals("legacy-key");
            IslandSettings.lyricsEnabled = false; IslandSettings.advancedSettingsMode = true;
            IslandSettings.saveToFile(cfg);
            JsonObject saved = JsonParser.parseString(Files.readString(cfg)).getAsJsonObject();
            assert saved.getAsJsonObject("futureUserData").get("keep").getAsInt() == 42 : "Unknown JSON data must be preserved";
            IslandSettings.lyricsEnabled = true; IslandSettings.advancedSettingsMode = false;
            IslandSettings.skyTextSize = .9F; IslandSettings.loadFromFile(cfg);
            assert !IslandSettings.lyricsEnabled && IslandSettings.advancedSettingsMode && IslandSettings.lastStudioTab == 4;
            assert eq(IslandSettings.skyTextSize, .43) && IslandSettings.musixmatchApiKey.equals("legacy-key");
            Files.writeString(cfg, "{\"lyricsEnabled\":\"invalid\",\"advancedSettingsMode\":\"maybe\","
                + "\"lastStudioTab\":\"nan\",\"skyTextSize\":\"bad\",\"musixmatchApiKey\":\"still-here\",\"other\":12}");
            IslandSettings.lyricsEnabled = false; IslandSettings.advancedSettingsMode = true;
            IslandSettings.skyTextSize = IslandSettings.DEFAULT_SKY_TEXT_SIZE;
            IslandSettings.loadFromFile(cfg);
            assert IslandSettings.lyricsEnabled && !IslandSettings.advancedSettingsMode && IslandSettings.lastStudioTab == 0;
            assert eq(IslandSettings.skyTextSize, IslandSettings.DEFAULT_SKY_TEXT_SIZE);
            assert IslandSettings.musixmatchApiKey.equals("still-here");
            IslandSettings.saveToFile(cfg);
            assert JsonParser.parseString(Files.readString(cfg)).getAsJsonObject().get("other").getAsInt() == 12;

            String malformed = "{\"saved-key\": \"do not discard\", ";
            Files.writeString(cfg, malformed);
            IslandSettings.loadFromFile(cfg); // Never overwrites an invalid file just by loading.
            assert Files.readString(cfg).equals(malformed);
            IslandSettings.saveToFile(cfg);
            Path backup = dir.resolve("settings.json.invalid-backup");
            assert Files.readString(backup).equals(malformed) : "Invalid raw configuration must be backed up before saving";
            assert JsonParser.parseString(Files.readString(cfg)).isJsonObject();
        } finally {
            try (var files = Files.list(dir)) { for (Path path : files.toList()) Files.deleteIfExists(path); }
            Files.deleteIfExists(dir);
        }
    }

    private static void colorReloadAndLegacyInference() throws Exception {
        Path cfg = Files.createTempFile("lt5-simple-colors-", ".json");
        try {
            IslandSettings.resetAllSettingsToFactory();
            IslandSettings.separateSkyColors = false;
            IslandSettings.skyActiveColor = 0xFF556677; // Dormant Advanced setting.
            IslandSettings.hudActiveColor = 0xFF445566; // Unrelated HUD title color.
            SimpleSettings.setColor(IslandSettings.DisplayMode.WORLD_3D, SimpleSettings.Color.PINK);
            IslandSettings.saveToFile(cfg);
            IslandSettings.resetAllSettingsToFactory();
            IslandSettings.loadFromFile(cfg);
            assert !IslandSettings.separateSkyColors : "An explicit shared-color choice must survive restart";
            assert IslandSettings.skyActiveColor == 0xFF556677 : "Dormant Sky color must survive restart";
            assert IslandSettings.hudActiveColor == 0xFF445566 : "Unrelated HUD color must survive restart";
            assert SimpleSettings.color(IslandSettings.DisplayMode.SKY_LYRICS) == SimpleSettings.Color.PINK;
            assert SimpleSettings.color(IslandSettings.DisplayMode.WORLD_3D) == SimpleSettings.Color.PINK;

            // Older configs without an explicit sharing flag still infer separate Sky colors.
            Files.writeString(cfg, "{\"lyricPrimaryColor\":\"#112233\",\"skyActiveColor\":\"#556677\"}");
            IslandSettings.resetAllSettingsToFactory();
            IslandSettings.loadFromFile(cfg);
            assert IslandSettings.separateSkyColors;
            assert IslandSettings.resolveSkyPrimaryColor() == 0xFF556677;
            assert IslandSettings.hudActiveColor == 0xFF112233 : "Missing legacy HUD color follows the shared color";

            // Simple reset must not erase unrelated colors across a save/reload either.
            IslandSettings.separateSkyColors = false;
            IslandSettings.skyActiveColor = 0xFF102030;
            IslandSettings.hudActiveColor = 0xFF405060;
            SimpleSettings.resetSimpleSettings();
            IslandSettings.saveToFile(cfg);
            IslandSettings.resetAllSettingsToFactory();
            IslandSettings.loadFromFile(cfg);
            assert !IslandSettings.separateSkyColors && IslandSettings.skyActiveColor == 0xFF102030;
            assert IslandSettings.hudActiveColor == 0xFF405060;
        } finally { Files.deleteIfExists(cfg); }
    }

    private static void globalVisibility() throws Exception {
        var lines = List.of(new LyricsFetcher.LyricLine(0.0F, "Visible lyric", "Translation"));
        MusicTracker tracker = new MusicTracker() {
            @Override public List<LyricsFetcher.LyricLine> getLyrics() { return lines; }
            @Override public float smoothPosition() { return 1.0F; }
        };
        try {
            MusicIsland island = new MusicIsland(tracker);
            Method pill = MusicIsland.class.getDeclaredMethod("pillLine", MediaInfo.class);
            pill.setAccessible(true);
            MediaInfo media = new MediaInfo("Song title", "Artist", new byte[0], 0L, 200L, true);
            IslandSettings.worldText = true; IslandSettings.skyLyrics = true; IslandSettings.showHud = true;
            IslandSettings.lyricsEnabled = true;
            assert island.isWorldTextEnabled();
            assert pill.invoke(island, media).equals("Visible lyric");
            IslandSettings.lyricsEnabled = false;
            assert !island.isWorldTextEnabled();
            assert pill.invoke(island, media).equals("Song title  |  Artist") : "Music title must remain when lyrics are hidden";
            assert IslandSettings.worldText && IslandSettings.skyLyrics && IslandSettings.showHud && tracker.getLyrics() == lines;
            // With lyrics off this must return before any drawing or Minecraft state is required.
            Method drawLyrics = MusicIsland.class.getDeclaredMethod("drawLyrics", float.class, float.class, MediaInfo.class, float.class, float.class);
            drawLyrics.setAccessible(true);
            drawLyrics.invoke(island, 0F, 0F, media, 1F, 128F);
            IslandSettings.lyricsEnabled = true;
            assert island.isWorldTextEnabled() && pill.invoke(island, media).equals("Visible lyric");
            IslandSettings.worldText = false;
            assert !island.isWorldTextEnabled() : "Top-bar-only mode must still disable world lyrics";
        } finally { tracker.shutdown(); }
    }
}
