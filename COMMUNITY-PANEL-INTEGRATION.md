> **Historical integration guide:** This documents the community panel as added in 1.11.5. The complete 1.11.7 project also contains later fixed-placement, multi-player selection and automatic community-reveal changes; use the full 1.11.7 source ZIP and `COMMUNITY-AUTO-OPEN-UPDATE.md` for all current features. The verified find/replace blocks below intentionally remain the original 1.11.5 instructions.

# LT5Lyrics community panel — complete integration

This guide is for the **1.11.4** source project used for the 1.11.5 release. The supplied `lt5_arena_context.txt` has the 1.11.3 version of `MusicIsland.java`; the functional Java, renderer, localization, metadata and JAR-notice replacements below were also checked against that 1.11.3 source. The 1.11.4 project is preferred because it retains the quick lyric-timing controls.

Apply each Find/Replace block to its named file, in the order shown. Whitespace is significant. If you use the complete source ZIP, **the changes are already applied**; do not apply them again. The ZIP also contains an optional automated geometry test and the full verification report.

## New file: `src/client/java/com/lt5lyrics/client/island/CommunityPanel.java`

```java
/*
 * Copyright (C) 2026 VoxelZero, for the modifications and additions made by
 * VoxelZero (including the community & credits panel and its text).
 * Portions remain Copyright (C) their original authors.
 *
 * This program is free software licensed under the GNU Affero General Public
 * License, version 3 (see the LICENSE file). It comes WITHOUT ANY WARRANTY.
 * See NOTICE-VoxelZero.txt for the additional terms and notices.
 */
package com.lt5lyrics.client.island;

import com.lt5lyrics.client.island.font.Font;
import com.lt5lyrics.client.island.font.Fonts;
import com.lt5lyrics.client.island.render.IslandRender;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

/** Chat-only community chip and its outward-opening, settings-aware card. */
public final class CommunityPanel {
	private static final String DISCORD_URL = "https://discord.gg/3kqxEwDpXm";
	private static final URI DISCORD_URI = URI.create(DISCORD_URL);
	private static final Identifier PICTURE = IslandRender.id("textures/island/picture.png");
	private static final float CHIP = 16.0F;
	private static final float ISLAND_GAP = 6.0F;
	private static final float PANEL_GAP = 4.0F;
	private static final float PANEL_WIDTH = 110.0F;
	private static final float PANEL_HEIGHT = 164.0F;
	private static final float MIN_SCALE = 0.60F;
	private static final float EDGE = 1.0F;
	private final Anim reveal = new Anim(200L);
	private boolean open;

	public CommunityPanel() {
		// Fabric resets per-screen listeners when a screen is reinitialized.
		ScreenEvents.BEFORE_INIT.register((client, screen, width, height) -> {
			if (screen instanceof ChatScreen) {
				ScreenKeyboardEvents.allowKeyPress(screen).register((current, key) -> {
					if (key.key() == GLFW.GLFW_KEY_ESCAPE && open) {
						open = false;
						return false; // First Escape closes the card, not the chat draft.
					}
					return true;
				});
			}
		});
	}

	/** Immediately discard a stale card when chat, the HUD or a valid layout disappears. */
	public void hide() {
		open = false;
		reveal.snap(0.0F);
	}

	public void render(Minecraft mc, float islandX, float islandY, float islandWidth, float islandHeight,
			float[] settingsObstacle, int accent) {
		Layout layout = layout(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(),
				islandX, islandY, islandWidth, islandHeight, settingsObstacle);
		if (layout == null) {
			hide();
			return;
		}
		double mx = mc.mouseHandler.getScaledXPos(mc.getWindow());
		double my = mc.mouseHandler.getScaledYPos(mc.getWindow());
		drawChip(layout.chip(), accent, layout.chip().contains(mx, my));
		reveal.update(open ? 1.0F : 0.0F);
		if (reveal.get() > 0.001F) drawCard(layout, accent, mx, my);
	}

	/** Returns true only for a chip/card click; an outside click continues to the island. */
	public boolean handleClick(Minecraft mc, float islandX, float islandY, float islandWidth, float islandHeight,
			float[] settingsObstacle, double mouseX, double mouseY) {
		Layout layout = layout(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(),
				islandX, islandY, islandWidth, islandHeight, settingsObstacle);
		if (layout == null) {
			hide();
			return false;
		}
		if (layout.chip().contains(mouseX, mouseY)) {
			open = !open;
			return true;
		}
		if (reveal.get() > 0.001F && visibleCard(layout, reveal.get()).contains(mouseX, mouseY)) {
			if (open && reveal.get() >= 0.85F && discordButton(layout).contains(mouseX, mouseY)) {
				openDiscord(mc);
			}
			return true;
		}
		if (open) open = false;
		return false;
	}

	private void drawChip(Rect chip, int accent, boolean hovered) {
		float opacity = ColorUtil.clamp01(IslandSettings.hudOpacity);
		float a = 255.0F * opacity;
		IslandRender.drawRoundedRect(chip.x(), chip.y(), chip.w(), chip.h(), 5, 5, 5, 5,
				ColorUtil.withAlpha(accent, a * (hovered || open ? 0.90F : 0.48F)));
		IslandRender.drawRoundedRect(chip.x() + 0.8F, chip.y() + 0.8F, chip.w() - 1.6F, chip.h() - 1.6F,
				4.3F, 4.3F, 4.3F, 4.3F, ColorUtil.withAlpha(IslandSettings.hudBgColor, a));
		int ink = ColorUtil.withAlpha(IslandSettings.hudActiveColor, 245.0F * opacity);
		IslandRender.drawRoundedRect(chip.x() + 7.1F, chip.y() + 4.1F, 1.8F, 1.8F, 0.9F, 0.9F, 0.9F, 0.9F, ink);
		IslandRender.drawRoundedRect(chip.x() + 7.1F, chip.y() + 7.2F, 1.8F, 5.0F, 0.8F, 0.8F, 0.8F, 0.8F, ink);
	}

	private void drawCard(Layout layout, int accent, double mx, double my) {
		Rect b = layout.card();
		Rect visible = visibleCard(layout, reveal.get());
		float s = layout.scale();
		float alpha = Math.clamp(reveal.get(), 0.0F, 1.0F);
		float opacity = ColorUtil.clamp01(IslandSettings.hudOpacity);
		IslandRender.pushScissor(visible.x(), visible.y(), visible.w(), visible.h());
		try {
			float radius = 8.0F * s;
			IslandRender.drawBlur(b.x(), b.y(), b.w(), b.h(), 30.0F * alpha, 2.0F,
					radius, radius, radius, radius, ColorUtil.withAlpha(0xFFFFFFFF, 255.0F * alpha));
			IslandRender.drawRoundedRect(b.x(), b.y(), b.w(), b.h(), radius, radius, radius, radius,
					ColorUtil.withAlpha(accent, 115.0F * alpha * opacity));
			IslandRender.drawRoundedRect(b.x() + 0.8F * s, b.y() + 0.8F * s,
					b.w() - 1.6F * s, b.h() - 1.6F * s,
					7.2F * s, 7.2F * s, 7.2F * s, 7.2F * s,
					ColorUtil.withAlpha(IslandSettings.hudBgColor, 255.0F * alpha * opacity));

			float center = b.x() + b.w() / 2.0F;
			int main = ColorUtil.withAlpha(IslandSettings.hudActiveColor, 255.0F * alpha);
			centered("lt5lyrics.community.title", 8.0F * s, center, b.y() + 7.0F * s, b.w() - 12.0F * s, main);
			centered("lt5lyrics.community.made_by", 6.3F * s, center, b.y() + 18.0F * s, b.w() - 12.0F * s, main);
			centered("lt5lyrics.community.ai", 5.2F * s, center, b.y() + 27.0F * s, b.w() - 12.0F * s,
					ColorUtil.withAlpha(IslandSettings.hudActiveColor, 180.0F * alpha));

			// Same resource as the original 16 px image; no mutation of its texture sampler.
			float pictureSize = 64.0F * s;
			IslandRender.drawTextureNearest(PICTURE, center - pictureSize / 2.0F, b.y() + 36.0F * s,
					pictureSize, pictureSize, ColorUtil.withAlpha(0xFFFFFFFF, 255.0F * alpha));
			drawCopyright(b, s, alpha);

			Rect button = discordButton(layout);
			boolean hover = visible.contains(mx, my) && button.contains(mx, my);
			IslandRender.drawRoundedRect(button.x(), button.y(), button.w(), button.h(), 4 * s, 4 * s, 4 * s, 4 * s,
					ColorUtil.withAlpha(accent, alpha * (hover ? 225.0F : 170.0F)));
			centered("lt5lyrics.community.discord", 6.0F * s, center, button.y() + 4.0F * s,
					button.w() - 8.0F * s, ColorUtil.withAlpha(0xFFFFFFFF, 255.0F * alpha));
		} finally {
			IslandRender.popScissor();
		}
	}

	private static void centered(String key, float size, float center, float y, float width, int color) {
		String value = Component.translatable(key).getString();
		Font font = Fonts.medium(size);
		float minimum = size * 0.55F;
		while (font.width(value) > width && size > minimum) {
			size *= 0.93F;
			font = Fonts.medium(size);
		}
		IslandRender.drawCenteredText(font, value, center, y, color);
	}

	private static void drawCopyright(Rect b, float scale, float alpha) {
		String first = Component.translatable("lt5lyrics.community.copyright").getString();
		String second = Component.translatable("lt5lyrics.community.original_copyright").getString();
		float availableWidth = b.w() - 12.0F * scale;
		float availableHeight = 35.0F * scale;
		float fontSize = 4.6F * scale;
		Font font;
		List<String> lines;
		float step;
		do {
			font = Fonts.regular(fontSize);
			lines = wrap(font, first, availableWidth);
			lines.addAll(wrap(font, second, availableWidth));
			step = fontSize * 0.96F;
			if (lines.size() * step <= availableHeight || fontSize <= 2.5F * scale) break;
			fontSize *= 0.93F;
		} while (true);
		IslandRender.pushScissor(b.x() + 5.0F * scale, b.y() + 102.0F * scale,
				b.w() - 10.0F * scale, availableHeight);
		try {
			for (int i = 0; i < lines.size(); i++) {
				IslandRender.drawText(font, lines.get(i), b.x() + 6.0F * scale,
						b.y() + 103.0F * scale + i * step,
						ColorUtil.withAlpha(IslandSettings.hudActiveColor, 205.0F * alpha));
			}
		} finally {
			IslandRender.popScissor();
		}
	}

	/** Word-wrap with a code-point fallback for a single exceptionally long word. */
	private static List<String> wrap(Font font, String text, float maxWidth) {
		List<String> lines = new ArrayList<>();
		String line = "";
		for (String word : text.strip().split("\\s+")) {
			if (word.isEmpty()) continue;
			String joined = line.isEmpty() ? word : line + " " + word;
			if (font.width(joined) <= maxWidth) {
				line = joined;
				continue;
			}
			if (!line.isEmpty()) {
				lines.add(line);
				line = "";
			}
			for (int at = 0; at < word.length();) {
				int next = word.offsetByCodePoints(at, 1);
				String candidate = line + word.substring(at, next);
				if (!line.isEmpty() && font.width(candidate) > maxWidth) {
					lines.add(line);
					line = word.substring(at, next);
				} else {
					line = candidate;
				}
				at = next;
			}
		}
		if (!line.isEmpty()) lines.add(line);
		return lines;
	}

	private void openDiscord(Minecraft mc) {
		if (!(mc.screen instanceof ChatScreen chat)) return;
		ChatDraft draft = ChatDraft.capture(chat);
		hide();
		mc.setScreen(new ConfirmLinkScreen(confirmed -> {
			try {
				if (confirmed) Util.getPlatform().openUri(DISCORD_URI);
			} finally {
				mc.setScreen(chat);
				if (draft != null) draft.restore(chat);
			}
		}, DISCORD_URL, false));
	}

	private record ChatDraft(String text, int cursor, int anchor) {
		static ChatDraft capture(ChatScreen chat) {
			EditBox box = chatInput(chat);
			if (box == null) return null;
			int cursor = box.getCursorPosition();
			int anchor;
			// setCursorPosition does not change the selection endpoint in 1.21.11.
			// With the cursor at zero the selected substring length is the exact endpoint,
			// even if the text contains repeated characters; restore the cursor immediately.
			try {
				box.setCursorPosition(0);
				anchor = box.getHighlighted().length();
			} finally {
				box.setCursorPosition(cursor);
			}
			return new ChatDraft(box.getValue(), cursor, anchor);
		}

		void restore(ChatScreen chat) {
			EditBox box = chatInput(chat);
			if (box == null) return;
			if (!text.equals(box.getValue())) box.setValue(text);
			box.setCursorPosition(cursor);
			box.setHighlightPos(anchor);
		}
	}

	private static EditBox chatInput(ChatScreen chat) {
		GuiEventListener focused = chat.getFocused();
		if (focused instanceof EditBox box) return box;
		for (GuiEventListener child : chat.children()) if (child instanceof EditBox box) return box;
		return null;
	}

	static record Rect(float x, float y, float w, float h) {
		boolean contains(double px, double py) {
			return px >= x && px < x + w && py >= y && py < y + h;
		}
		boolean overlaps(Rect other) {
			return other != null && x < other.x + other.w && x + w > other.x
					&& y < other.y + other.h && y + h > other.y;
		}
		boolean onScreen(float sw, float sh) {
			return x >= EDGE && y >= EDGE && x + w <= sw - EDGE && y + h <= sh - EDGE;
		}
	}

	static record Layout(Rect chip, Rect card, boolean left, float scale) {}

	/** Pure geometry, recalculated for both rendering and clicks using current animated bounds. */
	static Layout layout(float sw, float sh, float islandX, float islandY, float islandWidth, float islandHeight,
			float[] settingsObstacle) {
		if (!Float.isFinite(sw) || !Float.isFinite(sh) || !Float.isFinite(islandX) || !Float.isFinite(islandY)
				|| !Float.isFinite(islandWidth) || !Float.isFinite(islandHeight)
				|| islandWidth <= 0 || islandHeight <= 0) return null;
		Rect obstacle = settingsObstacle == null ? null
				: new Rect(settingsObstacle[0], settingsObstacle[1], settingsObstacle[2], settingsObstacle[3]);
		Rect island = new Rect(islandX, islandY, islandWidth, islandHeight);
		// Prefer the largest card; at each scale prefer the left side, then the right.
		for (int step = 0; step <= 8; step++) {
			float scale = Math.max(MIN_SCALE, 1.0F - 0.05F * step);
			float w = PANEL_WIDTH * scale, h = PANEL_HEIGHT * scale;
			for (boolean left : new boolean[] {true, false}) {
				float chipX = left ? islandX - ISLAND_GAP - CHIP : islandX + islandWidth + ISLAND_GAP;
				Rect chip = new Rect(chipX, islandY, CHIP, CHIP);
				if (!chip.onScreen(sw, sh) || chip.overlaps(obstacle) || chip.overlaps(island)) continue;
				float cardX = left ? chipX - PANEL_GAP - w : chipX + CHIP + PANEL_GAP;
				float defaultY = Math.max(EDGE, Math.min(islandY, sh - EDGE - h));
				float above = obstacle == null ? defaultY : obstacle.y() - h - 1.0F;
				float below = obstacle == null ? defaultY : obstacle.y() + obstacle.h() + 1.0F;
				for (float cardY : new float[] {defaultY, above, below, EDGE, sh - EDGE - h}) {
					Rect card = new Rect(cardX, cardY, w, h);
					if (card.onScreen(sw, sh) && !card.overlaps(island) && !card.overlaps(chip)
							&& !card.overlaps(obstacle)) return new Layout(chip, card, left, scale);
				}
			}
		}
		return null;
	}

	private static Rect visibleCard(Layout layout, float progress) {
		Rect b = layout.card();
		float w = b.w() * Math.clamp(progress, 0.0F, 1.0F);
		return new Rect(layout.left() ? b.x() + b.w() - w : b.x(), b.y(), w, b.h());
	}

	private static Rect discordButton(Layout layout) {
		Rect b = layout.card();
		float s = layout.scale();
		return new Rect(b.x() + 8.0F * s, b.y() + 141.0F * s, b.w() - 16.0F * s, 15.0F * s);
	}
}
```

## Music Island hooks

File: `src/client/java/com/lt5lyrics/client/island/MusicIsland.java`

### Replacement 1

Find:

```java
	private final Anim settingsAnim = new Anim(280L);
	private final SettingsPanel settingsPanel;
	private float manualScroll;
	private long manualScrollAt;
```

Replace with:

```java
	private final Anim settingsAnim = new Anim(280L);
	private final SettingsPanel settingsPanel;
	private final CommunityPanel communityPanel = new CommunityPanel();
	private float manualScroll;
	private long manualScrollAt;
```

### Replacement 2

Find:

```java

		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.options.hideGui || ScreenHiding.shouldHide(mc.screen)) return;
		boolean dragScreen = mc.screen instanceof DragScreen;
```

Replace with:

```java

		Minecraft mc = Minecraft.getInstance();
		if (!isChat(mc) || mc.player == null || mc.options.hideGui || !IslandSettings.showHud
				|| ScreenHiding.shouldHide(mc.screen)) communityPanel.hide();
		if (mc.player == null || mc.options.hideGui || ScreenHiding.shouldHide(mc.screen)) return;
		boolean dragScreen = mc.screen instanceof DragScreen;
```

### Replacement 3

Find:

```java
		}

		extendAnim.update(expanded ? 1.0F : 0.0F);
		widthAnim.update(targetWidth(session, showcase, toastActive));
```

Replace with:

```java
		}

		if (isChat(mc) && IslandSettings.showHud) {
			communityPanel.render(mc, x, y, size.width, size.height, communityObstacle(mc, x, y), accent);
		}

		extendAnim.update(expanded ? 1.0F : 0.0F);
		widthAnim.update(targetWidth(session, showcase, toastActive));
```

### Replacement 4

Find:

```java
		py = Math.max(2.0F, Math.min(py, sh - ph - 2.0F));
		return new float[] { px, py, pw, ph };
	}

```

Replace with:

```java
		py = Math.max(2.0F, Math.min(py, sh - ph - 2.0F));
		return new float[] { px, py, pw, ph };
	}

	// Reserve the embedded settings card's whole slide path and its outer stroke.
	private float[] communityObstacle(Minecraft mc, float x, float y) {
		if (!settingsOpen && settingsAnim.get() <= 0.01F) return null;
		float[] bounds = settingsBounds(mc, x, y);
		float scale = embeddedSettingsScale(mc);
		float pad = 1.0F * scale;
		float slide = 5.0F * scale;
		return new float[] {bounds[0] - pad, bounds[1] - slide - pad,
				bounds[2] + 2.0F * pad, bounds[3] + slide + 2.0F * pad};
	}

```

### Replacement 5

Find:

```java
		float x = islandX(mc);
		float y = islandY(mc);
		boolean inside = hovered(x, y, size.width, size.height, mouseX, mouseY);

```

Replace with:

```java
		float x = islandX(mc);
		float y = islandY(mc);
		if (isChat(mc) && !mc.options.hideGui && communityPanel.handleClick(mc, x, y, size.width, size.height,
				communityObstacle(mc, x, y), mouseX, mouseY)) return true;
		boolean inside = hovered(x, y, size.width, size.height, mouseX, mouseY);

```

## Nearest-neighbor texture helper

File: `src/client/java/com/lt5lyrics/client/island/render/IslandRender.java`

### Replacement 1

Find:

```java
	}

	public static void drawRoundedTexture(Identifier id, float x, float y, float width, float height,
		float tl, float bl, float tr, float br, int argb) {
```

Replace with:

```java
	}

	/** Draw pixel art with a cached nearest sampler without changing the texture's usual sampler. */
	public static void drawTextureNearest(Identifier id, float x, float y, float width, float height, int argb) {
		AbstractTexture tex = texture(id);
		if (tex == null) return;
		submit(IslandPipelines.TEXTURE, tex.getTextureView(),
			RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST),
			quadUi(x, y, width, height), fillUi(argb, 4), 1,
			params(width, height, SMOOTHNESS, 1.0F, 0, 0, 0, 0, 0, 0, 0, 0));
	}

	public static void drawRoundedTexture(Identifier id, float x, float y, float width, float height,
		float tl, float bl, float tr, float br, int argb) {
```

## English translation keys

File: `src/main/resources/assets/lt5lyrics/lang/en_us.json`

### Replacement 1

Find:

```json
	"key.lt5lyrics.next_track": "Next Track",
	"key.lt5lyrics.toggle_sky": "Toggle Sky Lyrics Mode",
	"key.lt5lyrics.toggle_hud": "Toggle HUD Island"
}
```

Replace with:

```json
	"key.lt5lyrics.next_track": "Next Track",
	"key.lt5lyrics.toggle_sky": "Toggle Sky Lyrics Mode",
	"key.lt5lyrics.toggle_hud": "Toggle HUD Island",
	"lt5lyrics.community.title": "LT5Lyrics",
	"lt5lyrics.community.made_by": "Made by VoxelZero",
	"lt5lyrics.community.ai": "Vibe-coded with AI",
	"lt5lyrics.community.discord": "Join our Discord",
	"lt5lyrics.community.copyright": "Copyright (C) 2026 VoxelZero, for the modifications and additions made by VoxelZero (including the community & credits panel and its text).",
	"lt5lyrics.community.original_copyright": "Portions remain Copyright (C) their original authors."
}
```

## Preserved authors, contact and notice metadata

File: `src/main/resources/fabric.mod.json`

### Replacement 1

Find:

```json
	"description": "LT5Lyrics - music HUD, synchronized multi-language karaoke, visual-only 3D World and Sky lyrics, and a Customization Studio with Simple and Advanced settings.",
	"authors": [
		"LT5Lyrics"
	],
	"contact": {
		"homepage": "",
		"sources": ""
	},
	"license": "AGPL-3.0",
```

Replace with:

```json
	"description": "LT5Lyrics - music HUD, synchronized multi-language karaoke, visual-only 3D World and Sky lyrics, and a Customization Studio with Simple and Advanced settings.",
	"authors": [
		"LT5Lyrics",
		"VoxelZero"
	],
	"contact": {
		"homepage": "https://discord.gg/3kqxEwDpXm",
		"sources": "",
		"discord": "https://discord.gg/3kqxEwDpXm"
	},
	"custom": {
		"lt5lyrics:voxelzero": {
			"credit": "Made by VoxelZero",
			"ai": "Vibe-coded with AI",
			"discord": "https://discord.gg/3kqxEwDpXm",
			"copyright": "Copyright (C) 2026 VoxelZero, for the modifications and additions made by VoxelZero (including the community & credits panel and its text).",
			"original_copyright": "Portions remain Copyright (C) their original authors.",
			"notice": "NOTICE-VoxelZero.txt"
		}
	},
	"license": "AGPL-3.0",
```

## Include the supplied notice in the JAR

File: `build.gradle`

### Replacement 1

Find:

```groovy
		rename { "${it}_$projectName"}
	}
}

```

Replace with:

```groovy
		rename { "${it}_$projectName"}
	}
	from("NOTICE-VoxelZero.txt")
}

```

## README credit, link and release name

File: `README.md`

### Replacement 1

Find:

```markdown
# LT5Lyrics 1.11.4

A client-side Minecraft Fabric mod for a music HUD, synchronized lyrics, normal 3D world lyrics, and distant Sky Lyrics. The compiled JAR is provided separately; you **do not need a GitHub account or to build the source** to use it.
```

Replace with:

```markdown
# LT5Lyrics 1.11.5

A client-side Minecraft Fabric mod for a music HUD, synchronized lyrics, normal 3D world lyrics, and distant Sky Lyrics. The compiled JAR is provided separately; you **do not need a GitHub account or to build the source** to use it.
```

### Replacement 2

Find:

```markdown
1. Install **Minecraft: Java Edition 1.21.11** and [Fabric Loader](https://fabricmc.net/use/installer/) for that exact Minecraft version. The build was made with Fabric Loader **0.19.3** and Java **21**.
2. Download a **Fabric API** release for Minecraft **1.21.11** (version **0.141.6+1.21.11** or newer compatible release) from its [Modrinth page](https://modrinth.com/mod/fabric-api/versions?g=1.21.11&l=fabric).
3. Put the supplied **`lt5lyrics-1.11.4.jar`** and the Fabric API JAR in `%APPDATA%\.minecraft\mods`. Remove older LT5Lyrics JARs from that folder first. Do not put the source ZIP or `-sources.jar` in `mods`.
4. Launch the Fabric 1.21.11 profile. Start Spotify or a supported browser's YouTube/YouTube Music playback on Windows, join a world, and press **O** to open LT5Lyrics Studio. Some browsers or tracks may not expose a usable Windows media session; if no song appears, try another supported player/browser and make sure audio is actually playing.

The included native media-session library uses Windows SMTC; the existing non-Windows backend does not detect desktop players. Fabric and the settings can load on other platforms, but Spotify/YouTube detection and playback control on macOS/Linux are **not implemented**. The mod does not log in to music services or bypass their restrictions.

## New in 1.11.4 — quick lyric timing on the island
```

Replace with:

```markdown
1. Install **Minecraft: Java Edition 1.21.11** and [Fabric Loader](https://fabricmc.net/use/installer/) for that exact Minecraft version. The build was made with Fabric Loader **0.19.3** and Java **21**.
2. Download a **Fabric API** release for Minecraft **1.21.11** (version **0.141.6+1.21.11** or newer compatible release) from its [Modrinth page](https://modrinth.com/mod/fabric-api/versions?g=1.21.11&l=fabric).
3. Put the supplied **`lt5lyrics-1.11.5.jar`** and the Fabric API JAR in `%APPDATA%\.minecraft\mods`. Remove older LT5Lyrics JARs from that folder first. Do not put the source ZIP or `-sources.jar` in `mods`.
4. Launch the Fabric 1.21.11 profile. Start Spotify or a supported browser's YouTube/YouTube Music playback on Windows, join a world, and press **O** to open LT5Lyrics Studio. Some browsers or tracks may not expose a usable Windows media session; if no song appears, try another supported player/browser and make sure audio is actually playing.

The included native media-session library uses Windows SMTC; the existing non-Windows backend does not detect desktop players. Fabric and the settings can load on other platforms, but Spotify/YouTube detection and playback control on macOS/Linux are **not implemented**. The mod does not log in to music services or bypass their restrictions.

## Community and credits

Open chat with **T**, then click the small **i** next to the music island. The card credits **Made by VoxelZero**, **Vibe-coded with AI**, and links to [our Discord](https://discord.gg/3kqxEwDpXm). Minecraft asks for confirmation before opening the link; your chat draft is restored when you return. Escape closes the card first. The card shifts sides, shrinks or hides where there is insufficient space, so it does not cover the embedded settings. The original image below Share remains unchanged.

The project remains AGPL-3.0. See `LICENSE` and the supplied `NOTICE-VoxelZero.txt` for attribution, additional terms and the AI-training statement. Existing authors and third-party notices remain credited.

## New in 1.11.4 — quick lyric timing on the island
```

### Replacement 3

Find:

```markdown
## Building from source (optional)

On Windows, install a **JDK 21**, extract the source ZIP, and double-click `BUILD_MOD.bat`. Alternatively run `./gradlew build` from the project folder. The first build downloads Gradle, Minecraft/Fabric dependencies and mappings and requires an internet connection; no GitHub account is required. The built mod is `build/libs/lt5lyrics-1.11.4.jar` (not the `-sources` JAR). `./gradlew checkBtp` runs the headless checks, and `./gradlew probeLyricsSources`, `./gradlew probeWordSync`, or `./gradlew probeCommunitySources` optionally test live network services; responses depend on network and provider availability. See `VERIFICATION.md` for what was and was not checked.

## License
```

Replace with:

```markdown
## Building from source (optional)

On Windows, install a **JDK 21**, extract the source ZIP, and double-click `BUILD_MOD.bat`. Alternatively run `./gradlew build` from the project folder. The first build downloads Gradle, Minecraft/Fabric dependencies and mappings and requires an internet connection; no GitHub account is required. The built mod is `build/libs/lt5lyrics-1.11.5.jar` (not the `-sources` JAR). `./gradlew checkBtp` runs the headless checks, and `./gradlew probeLyricsSources`, `./gradlew probeWordSync`, or `./gradlew probeCommunitySources` optionally test live network services; responses depend on network and provider availability. See `VERIFICATION.md` for what was and was not checked.

## License
```

## Release version

File: `gradle.properties`

### Replacement 1

Find:

```properties

# Mod Properties
version=1.11.4
group=com.lt5lyrics

```

Replace with:

```properties

# Mod Properties
version=1.11.5
group=com.lt5lyrics

```

## Release marker

File: `version.txt`

### Replacement 1

Find:

```text
1.11.4
```

Replace with:

```text
1.11.5
```

## Version checker

File: `src/client/java/com/lt5lyrics/client/VersionCheck.java`

### Replacement 1

Find:

```java
	public static final String MOD_ID = "lt5lyrics";
	public static final String MOD_NAME = "LT5Lyrics";
	public static final String VERSION = "1.11.4";

	private static volatile String latest = VERSION;
```

Replace with:

```java
	public static final String MOD_ID = "lt5lyrics";
	public static final String MOD_NAME = "LT5Lyrics";
	public static final String VERSION = "1.11.5";

	private static volatile String latest = VERSION;
```

## Windows build helper

File: `BUILD_MOD.bat`

### Replacement 1

Find:

```bat
cd /d "%~dp0"
echo ===================================================
echo   Building LT5Lyrics v1.11.4 for Minecraft 1.21.11
echo   This is optional: the supplied mod JAR is already built.
echo ===================================================
```

Replace with:

```bat
cd /d "%~dp0"
echo ===================================================
echo   Building LT5Lyrics v1.11.5 for Minecraft 1.21.11
echo   This is optional: the supplied mod JAR is already built.
echo ===================================================
```

### Replacement 2

Find:

```bat
echo ===================================================
echo   SUCCESS! Your compiled mod JAR is ready at:
echo   %~dp0build\libs\lt5lyrics-1.11.4.jar
echo.
echo   Copy lt5lyrics-1.11.4.jar (NOT the -sources jar)
echo   into your Minecraft "mods" folder!
echo ===================================================
```

Replace with:

```bat
echo ===================================================
echo   SUCCESS! Your compiled mod JAR is ready at:
echo   %~dp0build\libs\lt5lyrics-1.11.5.jar
echo.
echo   Copy lt5lyrics-1.11.5.jar (NOT the -sources jar)
echo   into your Minecraft "mods" folder!
echo ===================================================
```

## Supplied files and behavior

* Copy the original `NOTICE-VoxelZero.txt` **without editing it**, beside `LICENSE`. The Gradle replacement also includes it at the root of the built JAR. Do not alter the existing license or notices.
* Retain `assets/lt5lyrics/textures/island/picture.png` exactly as it is. It is pixel-identical to the supplied 32×32 image. The original 16×16, 50%-opacity image under Share is untouched; the new card uses a separate nearest-neighbor draw and is 64×64 at 100% card scale.
* The card and chip are computed from the island's current rectangle each frame. At each scale it tries left, then right; scales descend from 100% to 60%, with vertical alternatives. The padded settings rectangle includes its entire five-pixel slide and outer stroke. If neither side fits on screen without an intersection, both card and chip hide. Clicks outside close the card but are passed through to existing island/chat handling.
* The first Escape closes the card and keeps chat open; the next Escape follows vanilla chat behavior. Both link confirmation outcomes return to the same chat screen and restore its draft, cursor, and selection. Opening the URL is delegated to Minecraft's OS handler only after confirmation.
* Existing `MouseHandlerMixin`, Share rendering, settings, timing and lyric-provider code do not need replacement. The extra `WorldLyricsCheck` hook and `CommunityPanelCheck.java` in the source ZIP are optional tests, not runtime dependencies.
