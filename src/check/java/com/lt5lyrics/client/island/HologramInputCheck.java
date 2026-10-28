package com.lt5lyrics.client.island;

import org.lwjgl.sdl.SDLMouse;
import com.mojang.blaze3d.platform.InputConstants;
import java.nio.file.Files;
import java.nio.file.Path;


/** The 3D lyric surfaces must never consume Minecraft gameplay input. */
public final class HologramInputCheck {
    private HologramInputCheck() {}

    public static void run() throws Exception {
        boolean oldWorld = IslandSettings.worldText, oldSky = IslandSettings.skyLyrics;
        boolean oldEnabled = IslandSettings.lyricsEnabled, oldLocked = IslandSettings.locked;
        try {
            for (boolean enabled : new boolean[] {true, false}) {
                for (boolean world : new boolean[] {true, false}) {
                    for (boolean sky : new boolean[] {true, false}) {
                        for (boolean locked : new boolean[] {true, false}) {
                            IslandSettings.lyricsEnabled = enabled;
                            IslandSettings.worldText = world;
                            IslandSettings.skyLyrics = sky;
                            IslandSettings.locked = locked;
                            for (int button : new int[] {SDLMouse.SDL_BUTTON_LEFT, SDLMouse.SDL_BUTTON_RIGHT,
                                    SDLMouse.SDL_BUTTON_MIDDLE}) {
                                for (int action : new int[] {InputConstants.PRESS, InputConstants.RELEASE, InputConstants.REPEAT}) {
                                    assert !WorldLyrics.handleButton(button, action)
                                            : "Lyric holograms must not consume mouse buttons";
                                }
                            }
                            for (double scroll : new double[] {-4, -1, 0, 1, 4}) {
                                assert !WorldLyrics.handleScroll(scroll) : "Lyric holograms must not consume the wheel";
                            }
                            assert IslandSettings.worldText == world && IslandSettings.skyLyrics == sky;
                            assert IslandSettings.lyricsEnabled == enabled && IslandSettings.locked == locked;
                        }
                    }
                }
            }
            // Guard the actual cancellable Minecraft input hook as well as the legacy methods.
            String mixin = Files.readString(Path.of("src/client/java/com/lt5lyrics/client/mixin/MouseHandlerMixin.java"));
            assert !mixin.contains("WorldLyrics.handleButton") : "Gameplay clicks must not be routed to hologram picking";
            assert !mixin.contains("WorldLyrics.handleScroll") : "Gameplay scroll must not be routed to hologram dragging";
            assert mixin.contains("island().handleClick") && mixin.contains("island().handleScroll")
                    : "Music bar and settings input must remain available";
        } finally {
            IslandSettings.worldText = oldWorld; IslandSettings.skyLyrics = oldSky;
            IslandSettings.lyricsEnabled = oldEnabled; IslandSettings.locked = oldLocked;
        }
        System.out.println("PASS: World and Sky lyric holograms never consume mouse buttons or the scroll wheel");
    }
}
