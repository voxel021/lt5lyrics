package com.lt5lyrics.client.island;

import com.lt5lyrics.client.music.MusicTracker;
import com.lt5lyrics.client.island.render.IslandRender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class LT5SettingsScreen extends Screen {
	private final MusicIsland island;
	private final SettingsPanel panel;
	private final Anim openAnim = new Anim(220L);

	public LT5SettingsScreen(MusicIsland island, MusicTracker tracker) {
		super(Component.literal("LT5Lyrics Customization Studio"));
		this.island = island;
		this.panel = new SettingsPanel(tracker, new SettingsPanel.HostCallbacks() {
			@Override
			public void onOpenMapper() {
				MapperScreen mapper = MapperScreen.create(tracker);
				if (mapper != null) {
					Minecraft.getInstance().gui.setScreen(mapper);
				}
			}

			@Override
			public void onStartDragHud() {
				Minecraft.getInstance().gui.setScreen(new DragScreen(island));
			}

			@Override
			public void onOpenFullScreen() {
			}

			@Override
			public void onClosePanel() {
				onClose();
			}
		});
		this.panel.onOpened();
	}

	@Override
	protected void init() {
		super.init();
		// The custom Studio edit fields do not use a vanilla EditBox. SDL only
		// sends text events while a consumer has explicitly enabled text input.
		Minecraft.getInstance().textInputManager().startTextInput(this);
	}

	@Override
	public void removed() {
		Minecraft.getInstance().textInputManager().stopTextInput(this);
		super.removed();
	}

	static float studioScaleFor(int width, int height, int guiScale) {
		if (guiScale > 1) return 1.0F;
		return Math.clamp(Math.min(width / 416.0F, height / 220.0F), 1.0F, 1.5F);
	}

	private float studioScale() {
		return studioScaleFor(this.width, this.height, Minecraft.getInstance().getWindow().getGuiScale());
	}

	private float panelWidth() {
		return Math.min(416.0F, Math.max(1.0F, this.width / studioScale() - 16.0F));
	}

	private float panelHeight() {
		return Math.min(336.0F, Math.max(1.0F, this.height / studioScale() - 12.0F));
	}

	private float panelX() {
		return (this.width / studioScale() - panelWidth()) / 2.0F;
	}

	private float panelY() {
		return (this.height / studioScale() - panelHeight()) / 2.0F;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		graphics.fill(0, 0, this.width, this.height, 0x7006080E);
		openAnim.update(1.0F);
		float open = Math.max(0.35F, openAnim.get());
		float scale = studioScale();
		IslandRender.setUiScale(scale);
		try {
			panel.render(panelX(), panelY(), panelWidth(), panelHeight(), 255.0F, open,
				mouseX / scale, mouseY / scale, true);
		} finally {
			IslandRender.setUiScale(1.0F);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		float scale = studioScale();
		if (panel.handleMousePress(panelX(), panelY(), panelWidth(), panelHeight(),
				event.x() / scale, event.y() / scale, event.button(), true)) {
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		float scale = studioScale();
		if (panel.handleMouseMove(event.x() / scale, event.y() / scale)) {
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (panel.handleMouseRelease(event.button())) {
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		float scale = studioScale();
		if (panel.handleScroll(panelX(), panelY(), panelWidth(), panelHeight(), mouseX / scale, mouseY / scale, scrollY)) {
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (panel.handleKeyPress(event.key(), event.modifiers(), event.hasControlDownWithQuirk())) {
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (panel.handleCharTyped(event.codepointAsString())) {
			return true;
		}
		return super.charTyped(event);
	}

	@Override
	public void onClose() {
		IslandSettings.save();
		Minecraft.getInstance().gui.setScreen(null);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
