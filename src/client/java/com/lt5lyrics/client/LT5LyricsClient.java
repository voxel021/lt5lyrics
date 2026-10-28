package com.lt5lyrics.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.lt5lyrics.client.island.IslandSettings;
import com.lt5lyrics.client.island.MusicIsland;
import com.lt5lyrics.client.island.PlusSettings;
import com.lt5lyrics.client.island.WorldLyrics;
import com.lt5lyrics.client.island.font.Fonts;
import com.lt5lyrics.client.island.render.IslandRender;
import com.lt5lyrics.client.music.MusicTracker;
import dev.redstones.mediaplayerinfo.IMediaSession;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;


@Environment(EnvType.CLIENT)
public class LT5LyricsClient implements ClientModInitializer {
	private static MusicIsland island;
	private static MusicTracker tracker;

	private static KeyMapping openSettingsKey;
	private static KeyMapping playPauseKey;
	private static KeyMapping prevTrackKey;
	private static KeyMapping nextTrackKey;
	private static KeyMapping toggleSkyKey;
	private static KeyMapping toggleHudKey;

	private static boolean fontsReady;
	private static boolean loggedFonts;
	private static boolean loggedRender;

	public static MusicIsland island() {
		return island;
	}

	@Override
	public void onInitializeClient() {
		com.lt5lyrics.client.island.render.IslandPipelines.initialize();
		IslandSettings.load();
		PlusSettings.load();
		VersionCheck.check();

		KeyMapping.Category category = KeyMapping.Category.register(
			Identifier.fromNamespaceAndPath("lt5lyrics", "general")
		);
		openSettingsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.lt5lyrics.settings",
			InputConstants.Type.KEYBOARD,
			InputConstants.KEY_U,
			category
		));
		playPauseKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.lt5lyrics.play_pause",
			InputConstants.Type.KEYBOARD,
			InputConstants.KEY_K,
			category
		));
		prevTrackKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.lt5lyrics.prev_track",
			InputConstants.Type.KEYBOARD,
			InputConstants.KEY_LBRACKET,
			category
		));
		nextTrackKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.lt5lyrics.next_track",
			InputConstants.Type.KEYBOARD,
			InputConstants.KEY_RBRACKET,
			category
		));
		toggleSkyKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.lt5lyrics.toggle_sky",
			InputConstants.Type.KEYBOARD,
			InputConstants.KEY_J,
			category
		));
		toggleHudKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.lt5lyrics.toggle_hud",
			InputConstants.Type.KEYBOARD,
			InputConstants.KEY_BACKSLASH,
			category
		));

		LT5Commands.register();

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
			client.execute(VersionCheck::notifyPlayer));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> WorldLyrics.reset());
		tracker = new MusicTracker();
		island = new MusicIsland(tracker);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (island == null || client.player == null) return;

			while (openSettingsKey != null && openSettingsKey.consumeClick()) {
				island.openFullSettingsScreen();
			}
			while (playPauseKey != null && playPauseKey.consumeClick()) {
				IMediaSession session = tracker.getSession();
				if (session != null) {
					boolean wasPlaying = session.getMedia() != null && session.getMedia().isPlaying();
					session.playPause();
					island.showToast(wasPlaying ? "PAUSED" : "PLAYING");
				} else {
					island.showToast("NO ACTIVE SONG");
				}
			}
			while (prevTrackKey != null && prevTrackKey.consumeClick()) {
				IMediaSession session = tracker.getSession();
				if (session != null) {
					session.previous();
					island.showToast("PREVIOUS TRACK");
				} else {
					island.showToast("NO ACTIVE SONG");
				}
			}
			while (nextTrackKey != null && nextTrackKey.consumeClick()) {
				IMediaSession session = tracker.getSession();
				if (session != null) {
					session.next();
					island.showToast("NEXT TRACK");
				} else {
					island.showToast("NO ACTIVE SONG");
				}
			}
			while (toggleSkyKey != null && toggleSkyKey.consumeClick()) {
				IslandSettings.skyLyrics = !IslandSettings.skyLyrics;
				if (IslandSettings.skyLyrics) {
					IslandSettings.worldText = true;
				}
				WorldLyrics.reset();
				IslandSettings.save();
				island.showToast(IslandSettings.skyLyrics ? "SKY LYRICS ON" : "SKY LYRICS OFF");
			}
			while (toggleHudKey != null && toggleHudKey.consumeClick()) {
				IslandSettings.showHud = !IslandSettings.showHud;
				IslandSettings.save();
				island.showToast(IslandSettings.showHud ? "HUD SHOWN" : "HUD HIDDEN");
			}
		});

		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			IslandSettings.save();
			if(tracker != null) tracker.shutdown();
			forceExit();
		});
		LevelRenderEvents.END_MAIN.register(context -> {
			if(island == null || !island.isWorldTextEnabled() || !fontsReady) return;
			try{
				WorldLyrics.capture();
				WorldLyrics.render(tracker);
				IslandRender.flushWorld();
			}catch(Throwable e){
				if(!loggedRender){
					loggedRender = true;
					org.slf4j.LoggerFactory.getLogger("lt5lyrics").error("world lyrics failed", e);
				}
			}
		});
	}

	private interface Kernel32 extends com.sun.jna.Library {
		Kernel32 INSTANCE = com.sun.jna.Native.load("kernel32", Kernel32.class);

		com.sun.jna.Pointer GetCurrentProcess();

		boolean TerminateProcess(com.sun.jna.Pointer handle, int code);
	}

	private static void forceExit(){
		Runtime.getRuntime().addShutdownHook(new Thread(LT5LyricsClient::kill, "lt5lyrics-hook"));

		Thread killer = new Thread(() -> {
			try{
				Thread.sleep(2500L);
			}catch(InterruptedException e){
			}
			kill();
		}, "lt5lyrics-exit");
		killer.setDaemon(false);
		killer.start();
	}

	private static void kill(){
		try{
			Kernel32.INSTANCE.TerminateProcess(Kernel32.INSTANCE.GetCurrentProcess(), 0);
		}catch(Throwable e){
			Runtime.getRuntime().halt(0);
		}
	}

	public static void renderIsland() {
		if(island == null) return;
		if(!fontsReady){
			try{
				Fonts.init();
				fontsReady = true;
			}catch(Throwable e){
				if(!loggedFonts){
					loggedFonts = true;
					org.slf4j.LoggerFactory.getLogger("lt5lyrics").error("font init failed", e);
				}
				return;
			}
		}
		try{
			island.render();
            WorldLyrics.renderFinderIndicator();
            com.lt5lyrics.client.island.SourceCredits.render(tracker);
			IslandRender.flush();
		}catch(Throwable e){
			if(!loggedRender){
				loggedRender = true;
				org.slf4j.LoggerFactory.getLogger("lt5lyrics").error("lt5lyrics tick failed", e);
			}
		}
	}
}
