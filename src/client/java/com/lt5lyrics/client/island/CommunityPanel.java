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

import com.mojang.blaze3d.platform.InputConstants;
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
	private static final float MIN_COMPACT_SCALE = 0.40F;
	private static final float CHAT_INPUT_RESERVED = 15.0F;
	private static final float EDGE = 1.0F;
	private final Anim reveal = new Anim(200L);
	private boolean open;

	public CommunityPanel() {
		// Fabric resets per-screen listeners when a screen is reinitialized.
		ScreenEvents.BEFORE_INIT.register((client, screen, width, height) -> {
			if (screen instanceof ChatScreen) {
				ScreenKeyboardEvents.allowKeyPress(screen).register((current, key) -> {
					if (key.key() == InputConstants.KEY_ESCAPE && open) {
						open = false;
						return false; // First Escape closes the card, not the chat draft.
					}
					return true;
				});
			}
		});
	}

	/** Open on island interaction; the existing reveal animation starts from its current value. */
	public void showForIsland() {
		open = true;
	}

	/** Close with the same animation when the expanded island is dismissed. */
	public void closeForIsland() {
		open = false;
	}

	/** Immediately discard a stale card when chat or the HUD disappears. */
	public void hide() {
		open = false;
		reveal.snap(0.0F);
	}

	/** A temporary lack of space hides the card without losing the request to show it. */
	void pauseForMissingLayout() {
		reveal.snap(0.0F);
	}

	public void render(Minecraft mc, float islandX, float islandY, float islandWidth, float islandHeight,
			float[] settingsObstacle, int accent) {
		Layout layout = layout(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(),
				islandX, islandY, islandWidth, islandHeight, settingsObstacle);
		if (layout == null) {
			pauseForMissingLayout();
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
			pauseForMissingLayout();
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
		if (open && shouldCloseForOutside(islandX, islandY, islandWidth, islandHeight,
				settingsObstacle, mouseX, mouseY)) open = false;
		return false;
	}

	/** Island and settings controls must not dismiss the card or consume the click. */
	static boolean shouldCloseForOutside(float islandX, float islandY, float islandWidth, float islandHeight,
			float[] settingsObstacle, double mouseX, double mouseY) {
		Rect island = new Rect(islandX, islandY, islandWidth, islandHeight);
		if (island.contains(mouseX, mouseY)) return false;
		if (settingsObstacle != null && new Rect(settingsObstacle[0], settingsObstacle[1],
				settingsObstacle[2], settingsObstacle[3]).contains(mouseX, mouseY)) return false;
		return true;
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
		if (!(mc.gui.screen() instanceof ChatScreen chat)) return;
		ChatDraft draft = ChatDraft.capture(chat);
		boolean reopen = open;
		hide();
		mc.gui.setScreen(new ConfirmLinkScreen(confirmed -> {
			try {
				if (confirmed) com.mojang.blaze3d.Blaze3D.openUri(DISCORD_URI);
			} finally {
				mc.gui.setScreen(chat);
				if (draft != null) draft.restore(chat);
				if (reopen) showForIsland();
			}
		}, DISCORD_URI, false));
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

	/** Vertical placements are a narrow-screen fallback; left still describes the chip side. */
	static record Layout(Rect chip, Rect card, boolean left, float scale, boolean vertical, boolean below) {}

	/** Pure geometry, recalculated for both rendering and clicks using current animated bounds. */
	static Layout layout(float sw, float sh, float islandX, float islandY, float islandWidth, float islandHeight,
			float[] settingsObstacle) {
		if (!Float.isFinite(sw) || !Float.isFinite(sh) || !Float.isFinite(islandX) || !Float.isFinite(islandY)
				|| !Float.isFinite(islandWidth) || !Float.isFinite(islandHeight)
				|| islandWidth <= 0 || islandHeight <= 0) return null;
		Rect obstacle = settingsObstacle == null ? null
				: new Rect(settingsObstacle[0], settingsObstacle[1], settingsObstacle[2], settingsObstacle[3]);
		Rect island = new Rect(islandX, islandY, islandWidth, islandHeight);

		// Preserve the original 100%-to-60% outward layout whenever it fits.
		Layout result = horizontalLayout(sw, sh, island, obstacle, 0, 8);
		if (result != null) return result;
		// Narrow screens can fit a more readable card above or below the island.
		result = verticalLayout(sw, sh, island, obstacle);
		if (result != null) return result;
		// When lyrics fill the island vertically, a smaller side card is better than hiding it.
		return horizontalLayout(sw, sh, island, obstacle, 9, 12);
	}

	private static Layout horizontalLayout(float sw, float sh, Rect island, Rect obstacle, int first, int last) {
		for (int step = first; step <= last; step++) {
			float scale = Math.max(MIN_COMPACT_SCALE, 1.0F - 0.05F * step);
			float w = PANEL_WIDTH * scale, h = PANEL_HEIGHT * scale;
			for (boolean left : new boolean[] {true, false}) {
				float chipX = left ? island.x() - ISLAND_GAP - CHIP : island.x() + island.w() + ISLAND_GAP;
				Rect chip = new Rect(chipX, island.y(), CHIP, CHIP);
				if (!chip.onScreen(sw, sh) || chip.overlaps(obstacle) || chip.overlaps(island)) continue;
				float cardX = left ? chipX - PANEL_GAP - w : chipX + CHIP + PANEL_GAP;
				float defaultY = Math.max(EDGE, Math.min(island.y(), sh - EDGE - h));
				float above = obstacle == null ? defaultY : obstacle.y() - h - 1.0F;
				float below = obstacle == null ? defaultY : obstacle.y() + obstacle.h() + 1.0F;
				for (float cardY : new float[] {defaultY, above, below, EDGE, sh - EDGE - h}) {
					Rect card = new Rect(cardX, cardY, w, h);
					if (card.onScreen(sw, sh) && !card.overlaps(island) && !card.overlaps(chip)
							&& !card.overlaps(obstacle)) return new Layout(chip, card, left, scale, false, false);
				}
			}
		}
		return null;
	}

	private static Layout verticalLayout(float sw, float sh, Rect island, Rect obstacle) {
		for (int step = 0; step <= 8; step++) {
			float scale = Math.max(MIN_SCALE, 1.0F - 0.05F * step);
			float w = PANEL_WIDTH * scale, h = PANEL_HEIGHT * scale;
			float centered = Math.max(EDGE, Math.min(island.x() + island.w() / 2.0F - w / 2.0F, sw - EDGE - w));
			for (boolean left : new boolean[] {true, false}) {
				float chipX = left ? island.x() - ISLAND_GAP - CHIP : island.x() + island.w() + ISLAND_GAP;
				Rect chip = new Rect(chipX, island.y(), CHIP, CHIP);
				if (!chip.onScreen(sw, sh) || chip.overlaps(obstacle) || chip.overlaps(island)) continue;
				for (boolean below : new boolean[] {true, false}) {
					float y = below ? island.y() + island.h() + PANEL_GAP : island.y() - PANEL_GAP - h;
					for (float x : new float[] {centered, EDGE, sw - EDGE - w}) {
						Rect card = new Rect(x, y, w, h);
						if (card.onScreen(sw, sh) && card.y() + card.h() <= sh - CHAT_INPUT_RESERVED
								&& !card.overlaps(island) && !card.overlaps(chip) && !card.overlaps(obstacle))
							return new Layout(chip, card, left, scale, true, below);
					}
				}
			}
		}
		return null;
	}

	static Rect visibleCard(Layout layout, float progress) {
		Rect b = layout.card();
		float amount = Math.clamp(progress, 0.0F, 1.0F);
		if (layout.vertical()) {
			float h = b.h() * amount;
			return new Rect(b.x(), layout.below() ? b.y() : b.y() + b.h() - h, b.w(), h);
		}
		float w = b.w() * amount;
		return new Rect(layout.left() ? b.x() + b.w() - w : b.x(), b.y(), w, b.h());
	}

	private static Rect discordButton(Layout layout) {
		Rect b = layout.card();
		float s = layout.scale();
		return new Rect(b.x() + 8.0F * s, b.y() + 141.0F * s, b.w() - 16.0F * s, 15.0F * s);
	}
}
