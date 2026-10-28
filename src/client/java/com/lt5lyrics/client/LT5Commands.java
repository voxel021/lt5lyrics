package com.lt5lyrics.client;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.lt5lyrics.client.island.IslandSettings;
import com.lt5lyrics.client.island.MusicIsland;
import com.lt5lyrics.client.island.ThemePreset;
import com.lt5lyrics.client.island.WorldLyrics;
import com.lt5lyrics.client.music.MusicTracker;
import com.lt5lyrics.client.music.TrackMetadata;
import dev.redstones.mediaplayerinfo.IMediaSession;
import dev.redstones.mediaplayerinfo.MediaInfo;
import java.util.Locale;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class LT5Commands {
	private LT5Commands() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			var root = ClientCommands.literal("lt5")
				.executes(ctx -> {
					Minecraft mc = ctx.getSource().getClient();
					mc.execute(() -> mc.execute(() -> {
						MusicIsland island = LT5LyricsClient.island();
						if (island != null) island.openFullSettingsScreen();
					}));
					return 1;
				})
				.then(ClientCommands.literal("studio").executes(ctx -> {
					Minecraft mc = ctx.getSource().getClient();
					mc.execute(() -> mc.execute(() -> {
						MusicIsland island = LT5LyricsClient.island();
						if (island != null) island.openFullSettingsScreen();
					}));
					return 1;
				}))
				.then(ClientCommands.literal("color")
					.then(ClientCommands.argument("hex", StringArgumentType.greedyString())
						.executes(ctx -> {
							String raw = StringArgumentType.getString(ctx, "hex");
							Integer parsed = com.lt5lyrics.client.island.ColorUtil.parseHexStrict(raw);
							if (parsed == null) {
								parsed = com.lt5lyrics.client.island.ColorUtil.parseRgbTriplet(raw);
							}
							if (parsed == null) {
								ctx.getSource().sendError(Component.literal(
									"Invalid color '" + raw + "'. Use #RRGGBB (e.g. #FF79C6) or R, G, B."));
								return 0;
							}
							IslandSettings.applyUnifiedLyricColor(parsed);
							IslandSettings.save();
							String hexStr = com.lt5lyrics.client.island.ColorUtil.toHex(parsed);
							MusicIsland island = LT5LyricsClient.island();
							if (island != null) island.showToast("COLOR " + hexStr);
							sendTag(ctx.getSource(), "Set live lyric color to " + hexStr);
							return 1;
						})))
				.then(ClientCommands.literal("np").executes(ctx -> showNowPlaying(ctx.getSource())))
				.then(ClientCommands.literal("nowplaying").executes(ctx -> showNowPlaying(ctx.getSource())))
				.then(ClientCommands.literal("share").executes(ctx -> {
					MusicIsland island = LT5LyricsClient.island();
					if (island != null) island.shareNowPlaying();
					return 1;
				}))
				.then(ClientCommands.literal("sky").executes(ctx -> {
					IslandSettings.skyLyrics = !IslandSettings.skyLyrics;
					if (IslandSettings.skyLyrics) IslandSettings.worldText = true;
					WorldLyrics.reset();
					IslandSettings.save();
					MusicIsland island = LT5LyricsClient.island();
					if (island != null) {
						island.showToast(IslandSettings.skyLyrics ? "SKY LYRICS ON" : "SKY LYRICS OFF");
					}
					sendTag(ctx.getSource(), "Sky Lyrics Mode: " + (IslandSettings.skyLyrics ? "ON" : "OFF"));
					return 1;
				}))
				.then(ClientCommands.literal("hud").executes(ctx -> {
					IslandSettings.showHud = !IslandSettings.showHud;
					IslandSettings.save();
					MusicIsland island = LT5LyricsClient.island();
					if (island != null) {
						island.showToast(IslandSettings.showHud ? "HUD SHOWN" : "HUD HIDDEN");
					}
					sendTag(ctx.getSource(), "HUD Island: " + (IslandSettings.showHud ? "Shown" : "Hidden"));
					return 1;
				}))
				.then(ClientCommands.literal("reload").executes(ctx -> {
					MusicIsland island = LT5LyricsClient.island();
					if (island != null) {
						island.tracker().reloadLyrics();
						island.showToast("RELOADING LYRICS");
					}
					sendTag(ctx.getSource(), "Reloading lyrics for the active song...");
					return 1;
				}))
				.then(ClientCommands.literal("delay")
					.then(ClientCommands.argument("seconds", FloatArgumentType.floatArg(-10.0F, 10.0F))
						.executes(ctx -> {
							float sec = FloatArgumentType.getFloat(ctx, "seconds");
							MusicIsland island = LT5LyricsClient.island();
							if (island != null) {
								island.tracker().setLyricsOffset(sec);
								String formatted = String.format(Locale.ROOT, "%+.1fs", island.tracker().getLyricsOffset());
								island.showToast("DELAY " + formatted);
								sendTag(ctx.getSource(), "Lyric delay set to " + formatted);
							}
							return 1;
						})))
				.then(ClientCommands.literal("theme")
					.then(ClientCommands.argument("preset", StringArgumentType.word())
						.suggests((ctx, builder) -> {
							for (ThemePreset p : ThemePreset.values()) {
								builder.suggest(p.id);
							}
							return builder.buildFuture();
						})
						.executes(ctx -> {
							String name = StringArgumentType.getString(ctx, "preset");
							ThemePreset preset = ThemePreset.byId(name);
							if (preset == null) {
								ctx.getSource().sendError(Component.literal(
									"Unknown theme '" + name + "'. Try: classic, spotify, sakura, cyberpunk, golden, amethyst"));
								return 0;
							}
							preset.apply();
							MusicIsland island = LT5LyricsClient.island();
							if (island != null) {
								island.showToast("THEME: " + preset.label.toUpperCase(Locale.ROOT));
							}
							sendTag(ctx.getSource(), "Applied theme: " + preset.label);
							return 1;
						})))
				.then(ClientCommands.literal("font")
					.executes(ctx -> {
						String label = IslandSettings.cycleFontChoice(1);
						WorldLyrics.onSkySettingsChanged();
						MusicIsland island = LT5LyricsClient.island();
						if (island != null) island.showToast("FONT: " + label.toUpperCase(Locale.ROOT));
						sendTag(ctx.getSource(), "Active lyric font: " + label);
						return 1;
					})
					.then(ClientCommands.argument("preset", StringArgumentType.word())
						.suggests((ctx, builder) -> {
							for (IslandSettings.FontPreset fp : IslandSettings.FontPreset.values()) {
								builder.suggest(fp.id);
							}
							return builder.buildFuture();
						})
						.executes(ctx -> {
							String id = StringArgumentType.getString(ctx, "preset");
							IslandSettings.FontPreset fp = IslandSettings.FontPreset.byId(id);
							IslandSettings.fontPreset = fp;
							IslandSettings.customFontName = "";
							WorldLyrics.onSkySettingsChanged();
							IslandSettings.save();
							MusicIsland island = LT5LyricsClient.island();
							if (island != null) island.showToast("FONT: " + fp.label.toUpperCase(Locale.ROOT));
							sendTag(ctx.getSource(), "Set lyric font: " + fp.label);
							return 1;
						})))
				.then(ClientCommands.literal("anim")
					.executes(ctx -> {
						IslandSettings.lyricAnimation = IslandSettings.lyricAnimation.next();
						IslandSettings.save();
						MusicIsland island = LT5LyricsClient.island();
						if (island != null) island.showToast("ANIM: " + IslandSettings.lyricAnimation.label.toUpperCase(Locale.ROOT));
						sendTag(ctx.getSource(), "Lyric animation: " + IslandSettings.lyricAnimation.label);
						return 1;
					})
					.then(ClientCommands.argument("style", StringArgumentType.word())
						.suggests((ctx, builder) -> {
							for (IslandSettings.LyricAnimation la : IslandSettings.LyricAnimation.values()) {
								builder.suggest(la.id);
							}
							return builder.buildFuture();
						})
						.executes(ctx -> {
							String id = StringArgumentType.getString(ctx, "style");
							IslandSettings.LyricAnimation la = IslandSettings.LyricAnimation.byId(id);
							IslandSettings.lyricAnimation = la;
							IslandSettings.save();
							MusicIsland island = LT5LyricsClient.island();
							if (island != null) island.showToast("ANIM: " + la.label.toUpperCase(Locale.ROOT));
							sendTag(ctx.getSource(), "Set lyric animation: " + la.label + " (" + la.description + ")");
							return 1;
						})))
				.then(ClientCommands.literal("skyocclusion")
					.executes(ctx -> {
						IslandSettings.skyThroughBlocks = !IslandSettings.skyThroughBlocks;
						IslandSettings.save();
						String state = IslandSettings.skyThroughBlocks ? "Visible Through Blocks" : "Hidden Behind Blocks";
						MusicIsland island = LT5LyricsClient.island();
						if (island != null) island.showToast(IslandSettings.skyThroughBlocks ? "SKY XRAY ON" : "SKY OCCLUSION ON");
						sendTag(ctx.getSource(), "Sky Lyrics block mode: " + state);
						return 1;
					}))
				.then(ClientCommands.literal("skycurve")
					.then(ClientCommands.argument("percent", FloatArgumentType.floatArg(0.0F, 200.0F))
						.executes(ctx -> {
							float pct = FloatArgumentType.getFloat(ctx, "percent");
							IslandSettings.skyCurve = Math.clamp(pct / 100.0F, IslandSettings.MIN_SKY_CURVE, IslandSettings.MAX_SKY_CURVE);
							IslandSettings.save();
							MusicIsland island = LT5LyricsClient.island();
							if (island != null) island.showToast("SKY CURVE " + Math.round(pct) + "%");
							sendTag(ctx.getSource(), "Sky Lyrics curvature set to " + Math.round(pct) + "%");
							return 1;
						})));

			dispatcher.register(root);
		});
	}

	private static int showNowPlaying(FabricClientCommandSource source) {
		MusicIsland island = LT5LyricsClient.island();
		if (island == null) return 0;
		MusicTracker tracker = island.tracker();
		IMediaSession session = tracker.getSession();
		if (session == null || session.getMedia() == null) {
			sendTag(source, "No active song detected. Start Spotify or browser music!");
			return 1;
		}
		MediaInfo media = session.getMedia();
		TrackMetadata.QueryPair shown = TrackMetadata.displayPair(media.getArtist(), media.getTitle());
		String pos = MusicTracker.formatTime((long) tracker.rawPosition());
		String dur = media.getDuration() > 0 ? MusicTracker.formatTime(media.getDuration()) : "--:--";
		String delay = String.format(Locale.ROOT, "%+.1fs", tracker.getLyricsOffset());
		String owner = (session.getOwner() == null || session.getOwner().isBlank()) ? "System" : session.getOwner();

		MutableComponent header = prefix()
			.append(Component.literal(shown.title()).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
		if (!shown.artist().isEmpty()) {
			header.append(Component.literal(" - " + shown.artist()).withStyle(ChatFormatting.GRAY));
		}
		MutableComponent details = Component.literal("  [" + pos + " / " + dur + "]")
			.withStyle(ChatFormatting.AQUA)
			.append(Component.literal(" | Source: " + owner + " | Lyrics: " + tracker.getLyricSource() + " (" + delay + ")")
				.withStyle(ChatFormatting.DARK_GRAY));

		source.sendFeedback(header);
		source.sendFeedback(details);
		return 1;
	}

	private static void sendTag(FabricClientCommandSource source, String message) {
		source.sendFeedback(prefix().append(Component.literal(message).withStyle(ChatFormatting.WHITE)));
	}

	private static MutableComponent prefix() {
		int accent = IslandSettings.hudAccentColor & 0xFFFFFF;
		return Component.literal("[LT5Lyrics] ")
			.withStyle(style -> style.withColor(accent).withBold(true));
	}
}
