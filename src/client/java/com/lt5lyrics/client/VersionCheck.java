package com.lt5lyrics.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class VersionCheck {
	public static final String MOD_ID = "lt5lyrics";
	public static final String MOD_NAME = "LT5Lyrics";
	public static final String VERSION = "1.11.7";

	private static volatile String latest = VERSION;
	private static volatile boolean outdated = false;

	public static String latest() {
		return latest;
	}

	public static boolean isOutdated() {
		return outdated;
	}

	public static void check() {
		latest = VERSION;
		outdated = false;
	}

	public static void notifyPlayer() {
		if (!outdated) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;

		MutableComponent title = Component.literal(MOD_NAME)
			.withStyle(style -> style.withColor(0xFF8FC7E3).withBold(true));
		MutableComponent header = Component.literal(" - a new version is available")
			.withStyle(style -> style.withColor(ChatFormatting.WHITE));

		mc.gui.hud.getChat().addClientSystemMessage(Component.empty());
		mc.gui.hud.getChat().addClientSystemMessage(title.append(header));
		mc.gui.hud.getChat().addClientSystemMessage(Component.empty());
	}

	private VersionCheck() {
	}
}
