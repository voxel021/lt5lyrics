package com.lt5lyrics.client.island;

import com.lt5lyrics.client.island.font.Font;
import com.lt5lyrics.client.island.font.Fonts;
import com.lt5lyrics.client.island.render.IslandRender;
import com.lt5lyrics.client.music.MusicTracker;
import com.lt5lyrics.client.music.UnisonLyrics;
import net.minecraft.client.Minecraft;

/** Passive on-screen attribution; never handles input or masks world/crosshair rendering. */
public final class SourceCredits {
    private SourceCredits() {}
    public static boolean needsUnisonCredit(String source, boolean lyricsEnabled, boolean anyDisplay, boolean hidden) {
        return lyricsEnabled && anyDisplay && !hidden && source != null && source.contains(UnisonLyrics.SOURCE);
    }
    public static void render(MusicTracker tracker) {
        Minecraft mc = Minecraft.getInstance();
        if (tracker == null || mc.player == null || mc.gui.hud.isHidden() || tracker.getLyrics().isEmpty()) return;
        boolean hidden = ScreenHiding.shouldHide(mc.gui.screen());
        if (!needsUnisonCredit(tracker.getLyricSource(), IslandSettings.lyricsEnabled,
                IslandSettings.worldText || IslandSettings.showHud, hidden)) return;
        float screenWidth = mc.getWindow().getGuiScaledWidth(), screenHeight = mc.getWindow().getGuiScaledHeight();
        if (screenWidth < 40 || screenHeight < 25) return;
        Font font = Fonts.regular(6.5F);
        String first = UnisonLyrics.CREDIT, second = "";
        if (font.width(first) > screenWidth - 14.0F) {
            first = "Lyrics from Unison"; second = "(https://unison.boidu.dev)";
        }
        float largest = Math.max(font.width(first), font.width(second));
        // Very narrow windows still show the full text, rather than silently dropping the URL.
        if (largest > screenWidth - 14.0F) {
            float size = Math.max(3.0F, 6.5F * (screenWidth - 14.0F) / largest);
            font = Fonts.regular(size); largest = Math.max(font.width(first), font.width(second));
        }
        float line = Math.max(5.0F, font.height() + 1.5F);
        float h = second.isEmpty() ? line + 5.0F : line * 2.0F + 5.0F;
        float x = 5.0F, y = Math.max(3.0F, screenHeight - h - 5.0F);
        IslandRender.drawRoundedRect(x, y, largest + 8.0F, h, 3, 3, 3, 3, 0xC010141B);
        IslandRender.drawText(font, first, x + 4.0F, y + 2.5F, 0xFFF3F5FA);
        if (!second.isEmpty()) IslandRender.drawText(font, second, x + 4.0F, y + 2.5F + line, 0xFFF3F5FA);
    }
}
