package com.lt5lyrics.client.island;

import org.lwjgl.sdl.SDLKeycode;
import com.mojang.blaze3d.platform.InputConstants;
import com.lt5lyrics.client.island.font.DynamicSdfFont;
import com.lt5lyrics.client.island.font.MsdfFont;
import com.lt5lyrics.client.island.font.UnicodeClusterer;
import com.lt5lyrics.client.music.BetterLyricsProvider;
import com.lt5lyrics.client.music.LrcmuxLyrics;
import com.lt5lyrics.client.music.LyricsFetcher;
import com.lt5lyrics.client.music.MusixmatchLyrics;
import com.lt5lyrics.client.music.MusicTracker;
import com.lt5lyrics.client.music.TrackMetadata;
import dev.redstones.mediaplayerinfo.IMediaSession;
import dev.redstones.mediaplayerinfo.MediaInfo;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

public final class WorldLyricsCheck {
	public static void main(String[] args) throws Exception {
		if (!WorldLyricsCheck.class.desiredAssertionStatus()) {
			throw new IllegalStateException("Enable assertions with -ea");
		}

		checkLegacyWorldMath();
		checkSkyCircularBoundaryAndContextArcs();
		checkColorUtilAndThemes();
		checkBeatReactor();
		checkIslandSettingsPersistence();
		checkYouTubeMetadataParsing();
		checkYouTubePlaybackClock();
		checkYouTubeSessionSelection();
		checkLrcParserAndAutoPace();
		checkMsdfFallbackChars();
		checkSkyCurveAndOcclusionSettings();
		checkHologramBillboardBasis();
		checkLyricAnimatorCurves();
		checkUnicodeClusteringAndKhmerChineseMetadata();
		checkDynamicFontsAndHarfBuzzShaping();
		checkV140ColorWheelAndLivePropagation();
		checkV140StudioModeAndAppearanceRestore();
		checkV140ExpandedProvidersAndWordSync();
		checkSkyStablePlacement();
		checkV150SettingsSearchAndReset();
		checkV150MatchingAndStatus();
		checkV150ShaderContract();
		HologramInputCheck.run();
		SkyFollowCheck.run();
		SkyWrapCheck.run();
		SkyStabilityCheck.run();
		NormalLyricsCheck.run();
		NormalFollowAndFacingCheck.run();
		FixedGroundPlacementCheck.run();
		MusicIslandPictureCheck.run();
		QuickTimingLayoutCheck.run();
		CommunityPanelCheck.run();
		SimpleSettingsCheck.run();
        PlusFeaturesCheck.run();
        PlusThemeCheck.run();
        WordSyncCheck.run();
        com.lt5lyrics.client.music.CommunityProviderCheck.run();
        com.lt5lyrics.client.music.ManualSelectionCheck.run();
        com.lt5lyrics.client.music.MultiSessionLyricsCheck.run();
        com.lt5lyrics.client.music.QuickLyricOffsetCheck.run();
		assert LT5SettingsScreen.studioScaleFor(640, 360, 1) == 1.5F;
		assert LT5SettingsScreen.studioScaleFor(427, 240, 2) == 1.0F;
		System.out.println("PASS: LT5Lyrics v1.11.7 headless settings, Sky, Normal 3D, HUD, color, metadata, and provider-parser checks");
	}

	private static void checkSkyStablePlacement() {
		assert IslandSettings.DEFAULT_SKY_BOUNDARY_RADIUS == 360.0;
		assert IslandSettings.MIN_SKY_BOUNDARY_RADIUS == 80.0;
		assert IslandSettings.MAX_SKY_BOUNDARY_RADIUS == 1000.0;
		Vec3 origin = new Vec3(1024.0, 73.0, -5432.0);
		for (double radius : new double[] {80.0, 360.0, 1000.0}) {
			Vec3 anchor = SkyStableLayout.initialSlot(origin, radius, 0, IslandSettings.DEFAULT_SKY_HEIGHT);
			double horizontal = Math.hypot(anchor.x - origin.x, anchor.z - origin.z);
			assert WorldLyrics.isInsideSkyBoundary(anchor, origin, radius);
			assert Math.abs(horizontal - SkyStableLayout.slotDistance(radius)) < 1e-6;
			assert WorldLyrics.computeSkyScale(.5F, 70F, radius) == WorldLyrics.computeSkyScale(.5F, 900F, 8000.0);
		}
		assert WorldLyrics.computeSkyHorizonFade(700, 1000) < WorldLyrics.computeSkyHorizonFade(100, 1000);
		assert WorldLyrics.computeEffectiveBoundaryRadius(1000, 40) == 1000;
		double initial = 360, next = WorldLyrics.easeSkyBoundary(initial, 1000, 1.0F / 60.0F);
		assert next > initial && next < initial + 40;
		double half = WorldLyrics.easeSkyBoundary(initial, 1000, 1.0F / 120.0F);
		half = WorldLyrics.easeSkyBoundary(half, 1000, 1.0F / 120.0F);
		assert Math.abs(half - next) < 0.001;
		for (int f = 0; f < 400; f++) next = WorldLyrics.easeSkyBoundary(next, 1000, 1.0F / 60.0F);
		assert Math.abs(next - 1000.0) < 0.1;
	}

	private static void checkV150SettingsSearchAndReset() throws Exception {
		Path cfg = Files.createTempFile("lt5lyrics-v150-", ".json");
		try {
			IslandSettings.resetAllSettingsToFactory();
			assert !IslandSettings.advancedSettingsMode;
			IslandSettings.lastStudioTab = 3; IslandSettings.advancedSettingsMode = true;
			IslandSettings.skyDriftSpeed = 1.75F;
			IslandSettings.skyBoundaryRadius = 920;
			IslandSettings.skyTextSize = 0.42F;
			IslandSettings.providerPriority = new java.util.ArrayList<>(List.of("kugou", "lrclib", "KUGOU", "invalid"));
			IslandSettings.musixmatchApiKey = "unit-test-key";
			IslandSettings.applyUnifiedLyricColor(0xFF12A0F0);
			IslandSettings.saveToFile(cfg);
			IslandSettings.lastStudioTab = 0; IslandSettings.advancedSettingsMode = false;
			IslandSettings.skyDriftSpeed = 0; IslandSettings.skyBoundaryRadius = 80; IslandSettings.skyTextSize = 1;
			IslandSettings.providerPriority = new java.util.ArrayList<>();
			IslandSettings.applyUnifiedLyricColor(0xFFFFFFFF);
			IslandSettings.loadFromFile(cfg);
			assert IslandSettings.lastStudioTab == 3 && IslandSettings.advancedSettingsMode;
			assert Math.abs(IslandSettings.skyDriftSpeed - 1.75) < 0.001;
			assert IslandSettings.skyBoundaryRadius == 920 && Math.abs(IslandSettings.skyTextSize - .42) < 0.001;
			assert IslandSettings.normalizedProviderPriority().get(0).equals("kugou");
			assert IslandSettings.normalizedProviderPriority().size() == IslandSettings.DEFAULT_PROVIDER_PRIORITY.size();
			assert IslandSettings.resolveWorldPrimaryColor() == 0xFF12A0F0;
			assert IslandSettings.resolveSkyPrimaryColor() == 0xFF12A0F0;
			assert IslandSettings.resolveHudPrimaryColor() == 0xFF12A0F0;
			IslandSettings.moveProviderPriority("kugou", 1);
			assert IslandSettings.normalizedProviderPriority().get(1).equals("kugou");
			IslandSettings.resetSourceDefaults();
			assert IslandSettings.normalizedProviderPriority().equals(IslandSettings.DEFAULT_PROVIDER_PRIORITY);
			assert IslandSettings.musixmatchApiKey.equals("unit-test-key") : "Category reset must retain personal keys";
			IslandSettings.restoreAppearanceDefaults();
			assert Math.abs(IslandSettings.skyTextSize - .42F) < 0.001 : "Appearance reset must not change sky distance size";
			IslandSettings.resetAllSettingsToFactory();
			assert IslandSettings.musixmatchApiKey.isEmpty() && !IslandSettings.advancedSettingsMode && IslandSettings.lastStudioTab == 0;

			assert SettingsPanel.matchingSearchTitles("sky distance").contains("Sky Lyrics Distance");
			assert SettingsPanel.matchingSearchTitles("translation").contains("Show Translations");
			assert SettingsPanel.matchingSearchTitles("provider priority").contains("Provider Priority");
			assert SettingsPanel.matchingSearchTitles("no-such-setting-123").isEmpty();

			// Verify confirmation modal cannot execute a reset when cancelled, but Enter can confirm.
			SettingsPanel panel = new SettingsPanel(null, new SettingsPanel.HostCallbacks() {
				public void onOpenMapper() {} public void onStartDragHud() {} public void onOpenFullScreen() {} public void onClosePanel() {}
			});
			java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
			var ask = SettingsPanel.class.getDeclaredMethod("requestConfirmation", String.class, String.class, Runnable.class);
			ask.setAccessible(true);
			ask.invoke(panel, "Reset?", "Confirm", (Runnable) calls::incrementAndGet);
			assert panel.handleKeyPress(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE, 0, false);
			assert calls.get() == 0;
			ask.invoke(panel, "Reset?", "Confirm", (Runnable) calls::incrementAndGet);
			assert panel.handleKeyPress(com.mojang.blaze3d.platform.InputConstants.KEY_RETURN, 0, false);
			assert calls.get() == 1;
			var edit = SettingsPanel.class.getDeclaredMethod("beginEdit", SettingsPanel.EditTarget.class, String.class, boolean.class);
			edit.setAccessible(true);
			edit.invoke(panel, SettingsPanel.EditTarget.SKY_RADIUS, "365", true);
			assert panel.handleKeyPress(com.mojang.blaze3d.platform.InputConstants.KEY_A, org.lwjgl.sdl.SDLKeycode.SDL_KMOD_CTRL, true);
			assert panel.handleCharTyped("1000");
			assert panel.handleKeyPress(com.mojang.blaze3d.platform.InputConstants.KEY_RETURN, 0, false);
			assert IslandSettings.skyBoundaryRadius == 1000.0 : "Ctrl+A must replace the existing value";
		} finally { Files.deleteIfExists(cfg); IslandSettings.resetAllSettingsToFactory(); }
	}

	private static void checkV150MatchingAndStatus() {
		assert TrackMetadata.artistCompatible("Bruno Mars", "Bruno Mars, Lady Gaga");
		assert TrackMetadata.artistCompatible("VannDa (វណ្ណដា)", "វណ្ណដា");
		assert !TrackMetadata.artistCompatible("Adele", "The Beatles");
		assert LyricsFetcher.passesTrackGuard("Adele", "Hello", 295, "Adele", "Hello", 295);
		assert !LyricsFetcher.passesTrackGuard("Adele", "Hello", 295, "Wrong Artist", "Hello", 295);
		assert !LyricsFetcher.passesTrackGuard("Adele", "Hello", 295, "Adele", "Hello", 510);
		assert !LyricsFetcher.passesTrackGuard("Adele", "A Very Long Song Title", 200, "Wrong Artist", "A Very Long Song Title", 200);
		assert LyricsFetcher.Result.UNAVAILABLE.syncQuality() == LyricsFetcher.SyncQuality.PROVIDER_UNAVAILABLE;
		assert LyricsFetcher.Result.UNAVAILABLE.statusMessage().toLowerCase().contains("unavailable");
		assert LyricsFetcher.Result.EMPTY.statusMessage().toLowerCase().contains("no match");
		List<TrackMetadata.QueryPair> remix = TrackMetadata.buildCandidates("spotify.exe", "Adele", "Hello (Acoustic Remix)");
		assert !remix.isEmpty() && remix.get(0).title().contains("Remix") : remix;
	}

	private static void checkV150ShaderContract() throws Exception {
		String vert = Files.readString(Path.of("src/main/resources/assets/lt5lyrics/shaders/core/island_msdf.vsh"));
		String frag = Files.readString(Path.of("src/main/resources/assets/lt5lyrics/shaders/core/island_msdf.fsh"));
		for (String name : List.of("FragCoord", "TexCoord", "FragColor", "GlobalPos")) {
			assert vert.contains(" " + name + ";") && frag.contains(" " + name + ";") : "Shader interface " + name;
		}
		assert vert.contains("Extra.z > 0.5") && vert.contains("clipPos.z < clipPos.w * 0.000001"); // reversed depth, far plane near zero
		assert frag.contains("SizeSmooth") && vert.contains("lt5lyrics:island.glsl");
		assert vert.contains("#include") && !vert.contains("#moj_import");
		assert vert.contains("layout(location = 0) out vec2 FragCoord;") && frag.contains("layout(location = 0) in vec2 FragCoord;");
	}

	private static void checkLegacyWorldMath() {
		Vec3 right = new Vec3(-1.0, 0.0, 0.0);
		for (Direction face : new Direction[] { Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST }) {
			assert WorldLyrics.wallFace(face);
			Vec3 normal = face.getUnitVec3();
			Vec3 up = new Vec3(0.0, 1.0, 0.0);
			Vec3 along = up.cross(normal);
			assert Math.abs(up.lengthSqr() - 1.0) < 0.00001 : face;
			assert Math.abs(along.dot(up)) < 0.00001 : face;
			assert along.cross(up).distanceToSqr(normal) < 0.00001 : face;
			assert WorldLyrics.textFlip(along, up, normal) == 1.0F : face;
			assert WorldLyrics.textFlip(along, up, normal.scale(-1.0)) == -1.0F : face;
		}
		assert !WorldLyrics.wallFace(Direction.UP);
		assert !WorldLyrics.wallFace(Direction.DOWN);
		assert !WorldLyrics.wallFace(null);
		assert WorldLyrics.textFlip(right, new Vec3(0.0, 1.0, 0.0), new Vec3(0.0, 0.0, -5.0)) == 1.0F;
		assert WorldLyrics.textFlip(right, new Vec3(0.0, 1.0, 0.0), new Vec3(0.0, 0.0, 5.0)) == -1.0F;
		Vec3 from = new Vec3(0.0, 0.0, -5.0);
		Vec3 to = new Vec3(0.0, 0.0, 5.0);
		Vec3 halfway = WorldLyrics.followPoint(from, to, 0.5F);
		assert Math.abs(halfway.length() - 5.0) < 0.00001;
		assert halfway.distanceTo(to) < from.distanceTo(to);
		assert WorldLyrics.followPoint(from, to, 1.0F).distanceToSqr(to) < 0.00001;
		float step = (float) (1.0 - Math.exp(-15.0 / 60.0));
		Vec3 first = WorldLyrics.followPoint(from, to, step);
		Vec3 second = WorldLyrics.followPoint(first, to, step);
		Vec3 once = WorldLyrics.followPoint(from, to, (float) (1.0 - Math.exp(-15.0 / 30.0)));
		assert second.distanceToSqr(once) < 0.00001;
		Vec3 up = new Vec3(0.0, 1.0, 0.0);
		Vec3 ray = new Vec3(0.0, 0.0, 1.0);
		assert WorldLyrics.hitDistance(Vec3.ZERO, ray, to, right, up, 1.0, 0.3) == 5.0;
		assert WorldLyrics.hitDistance(new Vec3(2.0, 0.0, 0.0), ray, to, right, up, 1.0, 0.3) < 0.0;
		assert WorldLyrics.hitDistance(new Vec3(0.0, 1.0, 0.0), ray, to, right, up, 1.0, 0.3) < 0.0;
		assert WorldLyrics.hitDistance(Vec3.ZERO, right, to, right, up, 1.0, 0.3) < 0.0;
		assert WorldLyrics.hitDistance(Vec3.ZERO, ray, from, right, up, 1.0, 0.3) < 0.0;
		assert WorldLyrics.hitDistance(new Vec3(0.0, 0.0, 10.0), ray.scale(-1.0), to, right, up, 1.0, 0.3) == 5.0;
		WorldLyrics.Placed held = new WorldLyrics.Placed("first", to, right, up, 0.035F, Direction.NORTH, 50.0F);
		WorldLyrics.Placed next = held.withText("next verse", 90.0F);
		assert next.anchor.equals(held.anchor);
		assert next.right.equals(held.right) && next.up.equals(held.up);
		assert next.scale == held.scale && next.face == held.face;
		assert next.text.equals("next verse") && next.width == 90.0F;
		WorldLyrics.Placed floating = new WorldLyrics.Placed("verse", new Vec3(0.0, 0.0, 8.0), right, up, 0.035F, null, 50.0F);
		assert WorldLyrics.followTarget(floating, Vec3.ZERO, ray, right, up, 1.0, 0.7).distanceToSqr(floating.anchor) < 0.00001;
		Vec3 eye = new Vec3(0.5, 0.0, 0.0);
		Vec3 target = WorldLyrics.followTarget(floating, eye, ray, right, up, 1.0, 0.7);
		assert eye.add(target).distanceToSqr(floating.anchor) < 0.00001;
		eye = Vec3.ZERO;
		for (int frame = 0; frame < 180; frame++) {
			eye = eye.add(0.0, 0.0, 6.0 / 60.0);
			target = WorldLyrics.followTarget(floating, eye, ray, right, up, 1.0, 0.7);
			floating.anchor = eye.add(WorldLyrics.followPoint(floating.anchor.subtract(eye), target,
				(float) (1.0 - Math.exp(-10.0 / 60.0))));
			assert floating.anchor.z - eye.z > 5.0;
		}
		assert floating.scale == 0.035F;
		assert floating.anchor.z - eye.z < 6.0;
	}

	private static void checkSkyCircularBoundaryAndContextArcs() {
		assert WorldLyrics.clampSkyBoundaryRadius(10.0) == IslandSettings.MIN_SKY_BOUNDARY_RADIUS;
		assert WorldLyrics.clampSkyBoundaryRadius(9999.0) == IslandSettings.MAX_SKY_BOUNDARY_RADIUS;
		assert WorldLyrics.clampSkyBoundaryRadius(Double.NaN) == IslandSettings.DEFAULT_SKY_BOUNDARY_RADIUS;
		assert WorldLyrics.clampSkyBoundaryRadius(240.0) == 240.0;

		assert WorldLyrics.computeEffectiveBoundaryRadius(300.0, 1000.0) == 300.0;
		assert WorldLyrics.computeEffectiveBoundaryRadius(300.0, 200.0) == 300.0;
		assert WorldLyrics.computeEffectiveBoundaryRadius(250.0, -1.0) == 250.0;

		double[] radii = new double[] { 80.0, 180.0, 360.0, 500.0, 1000.0 };
		Vec3[] playerLocations = new Vec3[] {
			new Vec3(0.0, 64.0, 0.0),
			new Vec3(1450.5, 120.25, -2890.75),
			new Vec3(-99999.0, -40.0, 42424.0)
		};

		for (double radius : radii) {
			for (Vec3 playerPos : playerLocations) {
				for (int deg = -180; deg < 180; deg += 45) {
					double rad = Math.toRadians(deg);
					Vec3 farAnchor = new Vec3(
						playerPos.x + Math.sin(rad) * (radius * 5.0),
						playerPos.y + 999.0,
						playerPos.z + Math.cos(rad) * (radius * 5.0)
					);
					float scale = WorldLyrics.computeSkyScale(0.95F, 260.0F, radius * 0.8);
					Vec3 constrained = WorldLyrics.constrainSkyAnchor(
						farAnchor, playerPos, radius, 150.0, scale, rad
					);
					assert WorldLyrics.isInsideSkyBoundary(constrained, playerPos, radius)
						: "Anchor escaped boundary at radius=" + radius + " deg=" + deg;

					// Verify main line, upper context/header line, and lower context line vertices stay inside boundary
					for (double extraY : new double[] { -scale * 14.0, 0.0, scale * 14.0, scale * 24.0 }) {
						Vec3 lineAnchor = new Vec3(constrained.x, constrained.y + extraY, constrained.z);
						for (float ox : new float[] { -220.0F, -90.0F, 0.0F, 90.0F, 220.0F }) {
							for (float oy : new float[] { -14.0F, 0.0F, 14.0F }) {
								Vec3 v = WorldLyrics.computeSkyVertexWorld(lineAnchor, playerPos, radius, ox, oy);
								assert WorldLyrics.isInsideSkyBoundary(v, playerPos, radius)
									: "Sky vertex escaped boundary at extraY=" + extraY + " ox=" + ox;
							}
						}
					}
				}
			}
		}

		// Moving player 2,000+ blocks: boundary must carry sky anchor with the player
		Vec3 movingPlayer = new Vec3(0.0, 70.0, 0.0);
		double ringRadius = 150.0;
		Vec3 skyAnchor = new Vec3(0.0, 106.0, 120.0);
		for (int step = 0; step < 250; step++) {
			movingPlayer = movingPlayer.add(8.5, 0.4, -6.2);
			skyAnchor = WorldLyrics.constrainSkyAnchor(skyAnchor, movingPlayer, ringRadius, 36.0, 1.0F, 0.0);
			assert WorldLyrics.isInsideSkyBoundary(skyAnchor, movingPlayer, ringRadius)
				: "Boundary failed to follow moving player at step " + step;
		}

		// Right-hand inward tangent orientation across all 8 compass directions
		Vec3 origin = Vec3.ZERO;
		Vec3 up = new Vec3(0.0, 1.0, 0.0);
		for (int deg = 0; deg < 360; deg += 45) {
			double angle = Math.toRadians(deg);
			Vec3 anchor = new Vec3(Math.sin(angle) * 100.0, 20.0, Math.cos(angle) * 100.0);
			Vec3 skyRight = new Vec3(-Math.cos(angle), 0.0, Math.sin(angle)).normalize();
			Vec3 vLeft = WorldLyrics.computeSkyVertexWorld(anchor, origin, 180.0, -5.0F, 0.0F);
			Vec3 vRight = WorldLyrics.computeSkyVertexWorld(anchor, origin, 180.0, 5.0F, 0.0F);
			Vec3 chord = vRight.subtract(vLeft);
			assert chord.dot(skyRight) > 0.95 : "Sky text reversed at angle deg=" + deg;
			Vec3 lookHoriz = new Vec3(Math.sin(angle), 0.0, Math.cos(angle));
			Vec3 screenRight = lookHoriz.cross(up);
			assert screenRight.dot(skyRight) > 0.99 : "Sky text mirrored relative to player view at deg=" + deg;
		}
	}

	private static void checkColorUtilAndThemes() {
		int[] samples = new int[] {
			0xFFFFFFFF, 0xFF000000, 0xFF8FC7E3, 0xFFFF5964, 0xFF1ED760, 0xFFFFD166, 0xFFC77DFF, 0xFF141824
		};
		for (int sample : samples) {
			float[] hsv = ColorUtil.rgbToHsv(sample);
			int rebuilt = ColorUtil.hsvToRgb(hsv[0], hsv[1], hsv[2]);
			int dr = Math.abs(((sample >> 16) & 0xFF) - ((rebuilt >> 16) & 0xFF));
			int dg = Math.abs(((sample >> 8) & 0xFF) - ((rebuilt >> 8) & 0xFF));
			int db = Math.abs((sample & 0xFF) - (rebuilt & 0xFF));
			assert dr <= 2 && dg <= 2 && db <= 2 : "HSV round-trip drift for " + ColorUtil.toHex(sample);

			String hex = ColorUtil.toHex(sample);
			assert ColorUtil.parseHexOrDefault(hex, 0) == sample : "Hex round-trip failed for " + hex;
		}

		for (float hue : new float[] { 0.0F, 0.18F, 0.42F, 0.67F, 0.91F }) {
			for (float sat : new float[] { 0.15F, 0.55F, 1.0F }) {
				float[] offset = ColorUtil.hueSatToWheel(hue, sat);
				float[] hs = ColorUtil.wheelToHueSat(offset[0], offset[1]);
				float hueDiff = Math.abs(hue - hs[0]);
				hueDiff = Math.min(hueDiff, 1.0F - hueDiff);
				assert hueDiff < 0.005F && Math.abs(sat - hs[1]) < 0.005F
					: "Wheel coordinate round-trip drift";
			}
		}

		// ThemePresets check
		for (ThemePreset preset : ThemePreset.values()) {
			assert ThemePreset.byId(preset.id) == preset;
			assert ThemePreset.byId(preset.id.toUpperCase()) == preset;
		}
		assert ThemePreset.byId("nonexistent") == null;
	}

	private static void checkBeatReactor() {
		BeatReactor br = new BeatReactor();
		long now = 1_000_000_000L;
		// Quiet baseline
		for (int i = 0; i < 20; i++) {
			now += 16_000_000L;
			br.updateStep(0.05F, true, 0.016F, now);
		}
		float quietPulse = br.pulse();
		assert quietPulse < 0.05F : "Quiet baseline should not trigger beat pulse";

		// Kick drum transient spike
		now += 200_000_000L;
		br.updateStep(0.75F, true, 0.016F, now);
		assert br.pulse() > 0.45F : "Kick transient should trigger strong beat pulse";
		assert br.level() > 0.25F : "Audio level should rise on loud transient";

		// Pause decays back toward zero cleanly
		for (int i = 0; i < 60; i++) {
			now += 16_000_000L;
			br.updateStep(0.75F, false, 0.016F, now);
		}
		assert br.pulse() < 0.01F && br.level() < 0.02F : "Paused playback must decay reactor to zero";
	}

	private static void checkIslandSettingsPersistence() throws Exception {
		Path tempDir = Files.createTempDirectory("lt5lyrics-check");
		Path settingsFile = tempDir.resolve("settings.json");
		try {
			IslandSettings.posX = -1.0F;
			IslandSettings.posY = -1.0F;
			assert !IslandSettings.isMoved();

			IslandSettings.showHud = false;
			IslandSettings.hudBeatPulse = false;
			IslandSettings.trackToast = false;
			IslandSettings.visualizerStyle = IslandSettings.VisualizerStyle.MIRROR;
			IslandSettings.clockMode = IslandSettings.ClockMode.CLOCK_12H;

			IslandSettings.skyLyrics = true;
			IslandSettings.skyCrosshairFollow = true;
			IslandSettings.skyContextLines = false;
			IslandSettings.skySongHeader = false;
			IslandSettings.skyBeatPulse = false;
			IslandSettings.skyBoundaryRadius = 340.0;
			IslandSettings.skyHeight = 72.0;
			IslandSettings.skyAngleDegrees = -90.0F;
			IslandSettings.skyTextSize = 0.62F;
			IslandSettings.skyGlow = 0.80F;
			IslandSettings.hudOpacity = 0.74F;
			IslandSettings.hudAccentColor = 0xFF1ED760;
			IslandSettings.skyActiveColor = 0xFFFFD166;
			IslandSettings.skyGlowColor = 0xFFC77DFF;
			IslandSettings.posX = 210.0F;
			IslandSettings.posY = 24.0F;
			assert IslandSettings.isMoved();
			assert IslandSettings.compassLabel(-90.0F).startsWith("W");

			IslandSettings.saveToFile(settingsFile);

			// Mutate in-memory values, then reload from file and verify exact restoration
			IslandSettings.showHud = true;
			IslandSettings.hudBeatPulse = true;
			IslandSettings.visualizerStyle = IslandSettings.VisualizerStyle.WAVES;
			IslandSettings.clockMode = IslandSettings.ClockMode.CLOCK_24H;
			IslandSettings.skyLyrics = false;
			IslandSettings.skyContextLines = true;
			IslandSettings.skyBoundaryRadius = 60.0;
			IslandSettings.skyAngleDegrees = 0.0F;
			IslandSettings.hudAccentColor = 0xFFFFFFFF;
			IslandSettings.posX = -1.0F;
			IslandSettings.posY = -1.0F;

			IslandSettings.loadFromFile(settingsFile);
			assert !IslandSettings.showHud;
			assert !IslandSettings.hudBeatPulse;
			assert !IslandSettings.trackToast;
			assert IslandSettings.visualizerStyle == IslandSettings.VisualizerStyle.MIRROR;
			assert IslandSettings.clockMode == IslandSettings.ClockMode.CLOCK_12H;
			assert IslandSettings.skyLyrics;
			assert IslandSettings.skyCrosshairFollow;
			assert !IslandSettings.skyContextLines;
			assert !IslandSettings.skySongHeader;
			assert !IslandSettings.skyBeatPulse;
			assert Math.abs(IslandSettings.skyBoundaryRadius - 340.0) < 0.001;
			assert Math.abs(IslandSettings.skyHeight - 72.0) < 0.001;
			assert Math.abs(IslandSettings.skyAngleDegrees - (-90.0F)) < 0.001F;
			assert Math.abs(IslandSettings.skyTextSize - 0.62F) < 0.001F;
			assert Math.abs(IslandSettings.skyGlow - 0.80F) < 0.001F;
			assert Math.abs(IslandSettings.hudOpacity - 0.74F) < 0.001F;
			assert IslandSettings.hudAccentColor == 0xFF1ED760;
			assert IslandSettings.skyActiveColor == 0xFFFFD166;
			assert IslandSettings.skyGlowColor == 0xFFC77DFF;
			assert IslandSettings.isMoved() && IslandSettings.posX == 210.0F && IslandSettings.posY == 24.0F;

			// Out-of-bounds clamping check
			Files.writeString(settingsFile, """
				{
				  "posX": -1.0,
				  "posY": -1.0,
				  "skyBoundaryRadius": 99999.0,
				  "skyHeight": -50.0,
				  "skyAngleDegrees": 999.0,
				  "hudOpacity": 0.01,
				  "hudAccentColor": "#FF5964"
				}
				""", StandardCharsets.UTF_8);
			IslandSettings.loadFromFile(settingsFile);
			assert !IslandSettings.isMoved();
			assert IslandSettings.skyBoundaryRadius == IslandSettings.MAX_SKY_BOUNDARY_RADIUS;
			assert IslandSettings.skyHeight == IslandSettings.MIN_SKY_HEIGHT;
			assert IslandSettings.skyAngleDegrees == IslandSettings.MAX_SKY_ANGLE;
			assert IslandSettings.hudOpacity == IslandSettings.MIN_HUD_OPACITY;
			assert IslandSettings.hudAccentColor == 0xFFFF5964;
		} finally {
			Files.deleteIfExists(settingsFile);
			Files.deleteIfExists(tempDir);
		}
	}

	private static void checkYouTubeMetadataParsing() {
		// 1. Official VEVO YouTube Music Video
		List<TrackMetadata.QueryPair> vevo = TrackMetadata.buildCandidates(
			"chrome.exe", "TheWeekndVEVO", "The Weeknd - Blinding Lights (Official Video)");
		assert !vevo.isEmpty();
		assert vevo.get(0).equals(new TrackMetadata.QueryPair("The Weeknd", "Blinding Lights")) : vevo;

		// 2. Lyric Curator Channel ("7clouds") with multiple artists
		List<TrackMetadata.QueryPair> lyricCh = TrackMetadata.buildCandidates(
			"msedge.exe", "7clouds", "Bruno Mars, Lady Gaga - Die With A Smile (Lyrics)");
		assert lyricCh.contains(new TrackMetadata.QueryPair("Bruno Mars, Lady Gaga", "Die With A Smile")) : lyricCh;
		assert lyricCh.contains(new TrackMetadata.QueryPair("Bruno Mars", "Die With A Smile")) : lyricCh;
		assert lyricCh.contains(new TrackMetadata.QueryPair("Lady Gaga", "Die With A Smile")) : lyricCh;
		for (TrackMetadata.QueryPair p : lyricCh) {
			assert !p.artist().equalsIgnoreCase("7clouds") : "Curator channel should not pollute artist: " + lyricCh;
		}

		// 3. Auto-generated YouTube Music "- Topic" channel
		List<TrackMetadata.QueryPair> topic = TrackMetadata.buildCandidates(
			"chrome.exe", "The Weeknd - Topic", "Starboy");
		assert !topic.isEmpty();
		assert topic.get(0).equals(new TrackMetadata.QueryPair("The Weeknd", "Starboy")) : topic;

		// 4. Inline ft. & Director tag ("Lyrical Lemonade", "Travis Scott")
		List<TrackMetadata.QueryPair> dir = TrackMetadata.buildCandidates(
			"chrome.exe", "Lyrical Lemonade", "Juice WRLD - Lucid Dreams (Directed by Cole Bennett)");
		assert dir.get(0).equals(new TrackMetadata.QueryPair("Juice WRLD", "Lucid Dreams")) : dir;

		List<TrackMetadata.QueryPair> inlineFt = TrackMetadata.buildCandidates(
			"brave.exe", "Travis Scott", "Travis Scott - FE!N ft. Playboi Carti");
		assert inlineFt.get(0).equals(new TrackMetadata.QueryPair("Travis Scott", "FE!N")) : inlineFt;

		// 5. Firefox / Empty SMTC Artist with browser tab counter and "- YouTube" suffix
		List<TrackMetadata.QueryPair> firefox = TrackMetadata.buildCandidates(
			"firefox.exe", "", "(3) Arctic Monkeys - Do I Wanna Know? (Official Video) - YouTube");
		assert firefox.get(0).equals(new TrackMetadata.QueryPair("Arctic Monkeys", "Do I Wanna Know?")) : firefox;
		TrackMetadata.QueryPair disp = TrackMetadata.displayPair(
			"", "(3) Arctic Monkeys - Do I Wanna Know? (Official Video) - YouTube");
		assert disp.artist().equals("Arctic Monkeys") : disp;
		assert disp.title().equals("Do I Wanna Know?") : disp;

		// 6. Reversed Lyric Video ("Song - Artist")
		List<TrackMetadata.QueryPair> rev = TrackMetadata.buildCandidates(
			"chrome.exe", "7clouds", "Espresso - Sabrina Carpenter (Lyrics)");
		assert rev.contains(new TrackMetadata.QueryPair("Sabrina Carpenter", "Espresso")) : rev;

		// 7. VEVO channel where video title is ONLY the song name ("bad guy" on "BillieEilishVEVO")
		List<TrackMetadata.QueryPair> singleTitle = TrackMetadata.buildCandidates(
			"chrome.exe", "BillieEilishVEVO", "bad guy");
		assert singleTitle.contains(new TrackMetadata.QueryPair("Billie Eilish", "bad guy")) : singleTitle;

		// 8. NFD vs NFC Unicode folding ("ROSÉ" vs "ROSE\u0301")
		assert TrackMetadata.fold("ROS\u00C9").equals("rose");
		assert TrackMetadata.fold("ROSE\u0301").equals("rose");

		// 9. Spotify structured preservation (internal hyphens & AC/DC slash preserved!)
		List<TrackMetadata.QueryPair> spotify = TrackMetadata.buildCandidates(
			"Spotify.exe", "Taylor Swift", "Anti-Hero");
		assert spotify.get(0).equals(new TrackMetadata.QueryPair("Taylor Swift", "Anti-Hero")) : spotify;

		List<TrackMetadata.QueryPair> acdc = TrackMetadata.buildCandidates(
			"Spotify.exe", "AC/DC", "Back In Black");
		assert acdc.get(0).equals(new TrackMetadata.QueryPair("AC/DC", "Back In Black")) : acdc;
	}

	private static void checkYouTubePlaybackClock() {
		// 1. YouTube started from 0:00 (MediaPlayerInfo.dll returns raw=0 for entire video because Position == 0)
		MusicTracker.PlaybackClock ytClock = new MusicTracker.PlaybackClock();
		long t = 1_000_000L;
		ytClock.update(0L, 215L, true, t);
		assert Math.abs(ytClock.positionAt(true, t) - 0.0F) < 0.05F;

		// Advance 45 seconds at 100ms intervals while raw stays 0: must reach ~45.0s (NOT freeze at 1.33s!)
		for (int i = 0; i < 450; i++) {
			t += 100L;
			ytClock.update(0L, 215L, true, t);
		}
		float pos45 = ytClock.positionAt(true, t);
		assert Math.abs(pos45 - 45.0F) < 0.25F : "YouTube clock froze or drifted: " + pos45;

		// Pause for 10 seconds: clock must hold steady at ~45.0s
		for (int i = 0; i < 100; i++) {
			t += 100L;
			ytClock.update(0L, 215L, false, t);
		}
		assert Math.abs(ytClock.positionAt(false, t) - 45.0F) < 0.25F;

		// User seeks YouTube to 1:30 (raw jumps from 0 -> 90): clock must immediately snap to ~90.5s
		t += 100L;
		ytClock.update(90L, 215L, true, t);
		assert Math.abs(ytClock.positionAt(true, t) - 90.5F) < 0.25F;

		// User restarts video back to 0:00 (raw jumps from 90 -> 0): clock must snap back to 0.0s
		t += 100L;
		ytClock.update(0L, 215L, true, t);
		assert Math.abs(ytClock.positionAt(true, t) - 0.0F) < 0.25F;

		// 2. YouTube pre-roll ad (15s) -> real music video (215s) on same page resets clock & flags shift
		MusicTracker.PlaybackClock adClock = new MusicTracker.PlaybackClock();
		long tAd = 2_000_000L;
		adClock.update(0L, 15L, true, tAd);
		for (int i = 0; i < 140; i++) {
			tAd += 100L;
			assert !adClock.update(0L, 15L, true, tAd);
		}
		assert adClock.positionAt(true, tAd) > 13.5F;
		tAd += 100L;
		boolean shifted = adClock.update(0L, 215L, true, tAd);
		assert shifted : "Expected duration shift when pre-roll ad transitions to real song";
		assert Math.abs(adClock.positionAt(true, tAd) - 0.0F) < 0.1F;

		// 3. Corrupted WinRT epoch (LastUpdatedTime == 0 -> raw = 13.4 billion seconds) is rejected safely
		MusicTracker.PlaybackClock epochClock = new MusicTracker.PlaybackClock();
		long tEp = 3_000_000L;
		epochClock.update(13_400_000_000L, 0L, true, tEp);
		assert Math.abs(epochClock.positionAt(true, tEp) - 0.0F) < 0.1F;
		tEp += 1000L;
		epochClock.update(13_400_000_000L, 0L, true, tEp);
		assert Math.abs(epochClock.positionAt(true, tEp) - 1.0F) < 0.2F;

		// 4. Spotify normal 1-second SMTC timeline updates
		MusicTracker.PlaybackClock spClock = new MusicTracker.PlaybackClock();
		long tSp = 4_000_000L;
		for (int sec = 0; sec <= 20; sec++) {
			for (int sub = 0; sub < 10; sub++) {
				spClock.update(sec, 180L, true, tSp);
				tSp += 100L;
			}
		}
		float spPos = spClock.positionAt(true, tSp);
		assert spPos >= 20.0F && spPos <= 22.0F : "Spotify clock mismatch: " + spPos;
	}

	private static void checkYouTubeSessionSelection() {
		IMediaSession pausedSpotify = dummySession("Spotify.exe", "The Weeknd", "Starboy", false);
		IMediaSession firefoxEmptyArtist = dummySession("firefox.exe", "", "Arctic Monkeys - Do I Wanna Know? - YouTube", true);
		IMediaSession pausedChromeTab = dummySession("chrome.exe", "7clouds", "Old Paused Video", false);
		IMediaSession playingChromeTab = dummySession("chrome.exe", "TheWeekndVEVO", "The Weeknd - Blinding Lights", true);

		assert MusicTracker.isUsableSession(firefoxEmptyArtist);
		assert !MusicTracker.isUsableSession(dummySession("chrome.exe", "", "   ", true));

		// Even if preferredOwner was Spotify.exe, when Spotify is paused and YouTube is playing, pick YouTube!
		IMediaSession chosen = MusicTracker.selectSession(
			List.of(pausedSpotify, firefoxEmptyArtist), "", "Spotify.exe", MusicTracker.sessionKey(pausedSpotify));
		assert chosen == firefoxEmptyArtist;

		// With two Chrome tabs (one paused, one playing YouTube), pick the playing YouTube tab!
		IMediaSession chosenTab = MusicTracker.selectSession(
			List.of(pausedChromeTab, playingChromeTab), "", "chrome.exe", "");
		assert chosenTab == playingChromeTab;
	}

	private static IMediaSession dummySession(String owner, String artist, String title, boolean playing) {
		MediaInfo info = new MediaInfo(title, artist, new byte[0], 0L, 200L, playing);
		return new IMediaSession() {
			@Override public MediaInfo getMedia() { return info; }
			@Override public String getOwner() { return owner; }
			@Override public void play() {}
			@Override public void pause() {}
			@Override public void playPause() {}
			@Override public void stop() {}
			@Override public void next() {}
			@Override public void previous() {}
			@Override public void swapCycle() {}
			@Override public int getCycleType() { return 0; }
		};
	}

	private static void checkLrcParserAndAutoPace() {
		String lrc = "[00:00.00] \u266A\n[00:10.50][01:20.00]Repeated chorus\n[00:10.50]\u4e2d\u6587\u7ffb\u8bd1\n[00:25:75]Colon hundredths\n";
		List<LyricsFetcher.LyricLine> parsed = LyricsFetcher.parseLrc(lrc);
		assert parsed.size() == 3 : parsed;
		assert LyricsFetcher.currentIndex(parsed, 10.60F) == 0;
		assert parsed.get(0).text().equals("Repeated chorus");
		assert Math.abs(parsed.get(0).time() - 10.50F) < 0.01F && parsed.get(0).text().equals("Repeated chorus");
		assert Math.abs(parsed.get(1).time() - 25.75F) < 0.01F && parsed.get(1).text().equals("Colon hundredths");
		assert Math.abs(parsed.get(2).time() - 80.00F) < 0.01F && parsed.get(2).text().equals("Repeated chorus");

		List<LyricsFetcher.LyricLine> plain = List.of(
			new LyricsFetcher.LyricLine(-1.0F, "[Verse 1]"),
			new LyricsFetcher.LyricLine(-1.0F, "First line in the sky"),
			new LyricsFetcher.LyricLine(-1.0F, "Second line in the sky"),
			new LyricsFetcher.LyricLine(-1.0F, "[Chorus]"),
			new LyricsFetcher.LyricLine(-1.0F, "Third line in the sky"),
			new LyricsFetcher.LyricLine(-1.0F, "Fourth line in the sky")
		);
		List<LyricsFetcher.LyricLine> paced = LyricsFetcher.autoPacePlain(plain, 120L);
		assert paced.size() == 4 : paced;
		assert paced.get(0).time() >= 0.0F && paced.get(0).text().equals("First line in the sky");
		assert paced.get(3).time() > paced.get(0).time() && paced.get(3).time() < 120.0F;
	}

	private static void checkMsdfFallbackChars() {
		assert MsdfFont.fallbackChar('\u2019') == '\'';
		assert MsdfFont.fallbackChar('\u2013') == '-';
		assert MsdfFont.fallbackChar('\u2014') == '-';
		assert MsdfFont.fallbackChar('\u00C9') == 'E';
		assert MsdfFont.fallbackChar('\u00E9') == 'e';
		assert MsdfFont.fallbackChar('\u00F1') == 'n';
	}

	private static void checkSkyCurveAndOcclusionSettings() throws Exception {
		Vec3 playerPos = new Vec3(100.0, 64.0, -200.0);
		Vec3 anchor = new Vec3(100.0, 100.0, -100.0); // 100m North-South (+Z)
		double boundary = 200.0;

		// Center point (offsetX = 0) must stay pinned at anchor across ALL curve factors (0%..200%)
		for (float curve : new float[] { 0.0F, 0.5F, 1.0F, 1.5F, 2.0F }) {
			Vec3 center = WorldLyrics.computeSkyVertexWorld(anchor, playerPos, boundary, 0.0F, 0.0F, curve);
			assert center.distanceTo(anchor) < 1.0E-4 : "Center drifted for curve=" + curve;
		}

		// 0% curve: flat tangent plane (collinear along X)
		Vec3 flatL = WorldLyrics.computeSkyVertexWorld(anchor, playerPos, boundary, -30.0F, 0.0F, 0.0F);
		Vec3 flatC = WorldLyrics.computeSkyVertexWorld(anchor, playerPos, boundary, 0.0F, 0.0F, 0.0F);
		Vec3 flatR = WorldLyrics.computeSkyVertexWorld(anchor, playerPos, boundary, 30.0F, 0.0F, 0.0F);
		assert Math.abs(flatL.z - anchor.z) < 1.0E-4 && Math.abs(flatR.z - anchor.z) < 1.0E-4;
		assert flatL.add(flatR).scale(0.5).distanceTo(flatC) < 1.0E-4;

		// 100% curve: matches default 5-arg dome arc identically
		Vec3 dome5 = WorldLyrics.computeSkyVertexWorld(anchor, playerPos, boundary, 35.0F, 4.0F);
		Vec3 dome6 = WorldLyrics.computeSkyVertexWorld(anchor, playerPos, boundary, 35.0F, 4.0F, 1.0F);
		assert dome5.distanceTo(dome6) < 1.0E-6;

		// 200% curve: wraps tighter inward toward player than 100%, and stays strictly inside 3D boundary
		Vec3 deepWrap = WorldLyrics.computeSkyVertexWorld(anchor, playerPos, boundary, 35.0F, 0.0F, 2.0F);
		assert deepWrap.z < dome6.z : "200% curve should curve further inward toward player than 100%";
		assert WorldLyrics.isInsideSkyBoundary(deepWrap, playerPos, boundary);

		// Verify v1.3.0 settings defaults + JSON round-trip
		IslandSettings.resetSkyDefaults();
		assert !IslandSettings.skyThroughBlocks : "Sky Lyrics must default to block occlusion ON (skyThroughBlocks=false)";
		assert Math.abs(IslandSettings.skyCurve - 1.0F) < 1.0E-4F;

		Path tempCfg = Files.createTempFile("lt5lyrics-v130-settings-", ".json");
		try {
			IslandSettings.skyThroughBlocks = true;
			IslandSettings.skyCurve = 1.65F;
			IslandSettings.fontPreset = IslandSettings.FontPreset.KHMER_CLASSIC;
			IslandSettings.customFontName = "my_custom.ttf";
			IslandSettings.lyricAnimation = IslandSettings.LyricAnimation.POP_SPRING;
			IslandSettings.animationSpeed = 1.35F;
			IslandSettings.showTranslations = false;
			IslandSettings.hologramBillboard = IslandSettings.HologramBillboard.VERTICAL;
			IslandSettings.hologramBackground = false;
			IslandSettings.hologramBgOpacity = 0.65F;
			IslandSettings.hologramShadow = false;
			IslandSettings.hologramScale = 1.40F;

			IslandSettings.saveToFile(tempCfg);

			// Mutate before reload
			IslandSettings.skyThroughBlocks = false;
			IslandSettings.skyCurve = 0.2F;
			IslandSettings.fontPreset = IslandSettings.FontPreset.INTER_MODERN;
			IslandSettings.customFontName = "";
			IslandSettings.lyricAnimation = IslandSettings.LyricAnimation.CLASSIC_GLIDE;
			IslandSettings.animationSpeed = 0.8F;
			IslandSettings.showTranslations = true;
			IslandSettings.hologramBillboard = IslandSettings.HologramBillboard.SMART_BTP;
			IslandSettings.hologramBackground = true;
			IslandSettings.hologramBgOpacity = 0.2F;
			IslandSettings.hologramShadow = true;
			IslandSettings.hologramScale = 0.8F;

			IslandSettings.loadFromFile(tempCfg);

			assert IslandSettings.skyThroughBlocks;
			assert Math.abs(IslandSettings.skyCurve - 1.65F) < 0.001F;
			assert IslandSettings.fontPreset == IslandSettings.FontPreset.KHMER_CLASSIC;
			assert IslandSettings.customFontName.equals("my_custom.ttf");
			assert IslandSettings.lyricAnimation == IslandSettings.LyricAnimation.POP_SPRING;
			assert Math.abs(IslandSettings.animationSpeed - 1.35F) < 0.001F;
			assert !IslandSettings.showTranslations;
			assert IslandSettings.hologramBillboard == IslandSettings.HologramBillboard.VERTICAL;
			assert !IslandSettings.hologramBackground;
			assert Math.abs(IslandSettings.hologramBgOpacity - 0.65F) < 0.001F;
			assert !IslandSettings.hologramShadow;
			assert Math.abs(IslandSettings.hologramScale - 1.40F) < 0.001F;
		} finally {
			Files.deleteIfExists(tempCfg);
			IslandSettings.resetSkyDefaults();
			IslandSettings.fontPreset = IslandSettings.FontPreset.INTER_MODERN;
			IslandSettings.customFontName = "";
			IslandSettings.lyricAnimation = IslandSettings.LyricAnimation.RISE_FLOW;
			IslandSettings.animationSpeed = 1.0F;
			IslandSettings.showTranslations = true;
			IslandSettings.hologramBillboard = IslandSettings.HologramBillboard.SMART_BTP;
			IslandSettings.hologramBackground = true;
			IslandSettings.hologramBgOpacity = IslandSettings.DEFAULT_HOLO_BG_OPACITY;
			IslandSettings.hologramShadow = true;
			IslandSettings.hologramScale = IslandSettings.DEFAULT_HOLO_SCALE;
		}
	}

	private static void checkHologramBillboardBasis() {
		Vec3 anchor = new Vec3(10.0, 70.0, 10.0);
		WorldLyrics.Placed floating = new WorldLyrics.Placed(
			"Hologram Test", anchor, new Vec3(-1.0, 0.0, 0.0), new Vec3(0.0, 1.0, 0.0), 0.035F, null, 60.0F
		);

		for (int deg = 0; deg < 360; deg += 45) {
			double rad = Math.toRadians(deg);
			Vec3 camPos = new Vec3(anchor.x + Math.sin(rad) * 6.0, anchor.y + 1.5, anchor.z + Math.cos(rad) * 6.0);
			Quaternionf camRot = new Quaternionf().rotateY((float) -rad);

			// VERTICAL billboard must keep up == (0,1,0), unit-length right, and face toward camera
			Vec3[] vertBasis = WorldLyrics.computeHologramBasis(
				floating, camRot, camPos, IslandSettings.HologramBillboard.VERTICAL
			);
			assert Math.abs(vertBasis[0].length() - 1.0) < 1.0E-5;
			assert vertBasis[1].distanceTo(new Vec3(0.0, 1.0, 0.0)) < 1.0E-5;
			assert Math.abs(vertBasis[0].dot(vertBasis[1])) < 1.0E-5;
			assert WorldLyrics.textFlip(vertBasis[0], vertBasis[1], camPos.subtract(anchor)) == 1.0F;

			// CENTER billboard must produce orthonormal right & up vectors
			Vec3[] centerBasis = WorldLyrics.computeHologramBasis(
				floating, camRot, camPos, IslandSettings.HologramBillboard.CENTER
			);
			assert Math.abs(centerBasis[0].length() - 1.0) < 1.0E-5;
			assert Math.abs(centerBasis[1].length() - 1.0) < 1.0E-5;
			assert Math.abs(centerBasis[0].dot(centerBasis[1])) < 1.0E-5;
		}
	}

	private static void checkLyricAnimatorCurves() {
		// Verify RISE_FLOW ("Rise & Fade"): rises from below -> center -> gentle upward drift, then exits smoothly upward
		LyricAnimator.MotionState start = LyricAnimator.computeActive(
			IslandSettings.LyricAnimation.RISE_FLOW, 0L, 1.0F
		);
		LyricAnimator.MotionState settled = LyricAnimator.computeActive(
			IslandSettings.LyricAnimation.RISE_FLOW, 600L, 1.0F
		);
		LyricAnimator.MotionState endActive = LyricAnimator.computeActive(
			IslandSettings.LyricAnimation.RISE_FLOW, 2000L, 1.0F
		);
		assert start.liftUnits() < -0.5F && start.alpha() == 0.0F;
		assert settled.liftUnits() > 0.0F && settled.alpha() > 0.99F;
		assert endActive.liftUnits() > settled.liftUnits();

		// Continuity check at handoff to computeExiting
		LyricAnimator.MotionState exit0 = LyricAnimator.computeExiting(
			IslandSettings.LyricAnimation.RISE_FLOW, 0L, 1.0F, endActive.liftUnits(), endActive.scaleFactor()
		);
		LyricAnimator.MotionState exitMid = LyricAnimator.computeExiting(
			IslandSettings.LyricAnimation.RISE_FLOW, 300L, 1.0F, endActive.liftUnits(), endActive.scaleFactor()
		);
		LyricAnimator.MotionState exitDone = LyricAnimator.computeExiting(
			IslandSettings.LyricAnimation.RISE_FLOW, 800L, 1.0F, endActive.liftUnits(), endActive.scaleFactor()
		);
		assert Math.abs(exit0.liftUnits() - endActive.liftUnits()) < 1.0E-5F;
		assert exit0.alpha() == 1.0F;
		assert exitMid.liftUnits() > exit0.liftUnits() && exitMid.alpha() < 1.0F && exitMid.alpha() > 0.05F;
		assert exitDone.liftUnits() > exitMid.liftUnits() && exitDone.alpha() == 0.0F;

		// Verify all 6 animation modes produce finite bounded values
		for (IslandSettings.LyricAnimation mode : IslandSettings.LyricAnimation.values()) {
			for (long elapsedMs : new long[] { 0L, 150L, 450L, 1200L }) {
				LyricAnimator.MotionState st = LyricAnimator.computeActive(mode, elapsedMs, 1.0F);
				assert Float.isFinite(st.liftUnits()) && Float.isFinite(st.alpha()) && Float.isFinite(st.scaleFactor());
				assert st.alpha() >= 0.0F && st.alpha() <= 1.0F;
				float rev = LyricAnimator.glyphRevealAlpha(mode, 2, 10, st.progress(), false);
				assert rev >= 0.0F && rev <= 1.0F;
			}
		}
	}

	private static void checkUnicodeClusteringAndKhmerChineseMetadata() {
		// 1. Khmer Coeng subscript & split-vowel clustering ("ខ្ញុំស្រលាញ់អ្នក")
		String khmer = "ខ្ញុំស្រលាញ់អ្នក";
		assert UnicodeClusterer.containsExtendedUnicode(khmer);
		List<UnicodeClusterer.Cluster> khClusters = UnicodeClusterer.segment(khmer);
		assert khClusters.size() >= 4 && khClusters.size() < khmer.length() : khClusters;
		// First syllable "ខ្ញុំ" (Kha + Coeng + Nyo + Muusikatoan/U + Nikahit) must stay ONE intact cluster!
		assert khClusters.get(0).text().equals("ខ្ញុំ") : "Broken Khmer Coeng cluster: " + khClusters.get(0).text();
		// Second syllable "ស្រ" (Sa + Coeng + Ro) must stay ONE intact cluster!
		assert khClusters.get(1).text().equals("ស្រ") : "Broken Khmer Coeng+Ro cluster: " + khClusters.get(1).text();

		// 2. Script-safe TrackMetadata.fold(...)
		assert TrackMetadata.fold("ROS\u00C9").equals("rose");
		assert TrackMetadata.fold("ROSE\u0301").equals("rose");
		assert !TrackMetadata.fold("កា").equals(TrackMetadata.fold("កី")) : "Khmer vowels must not be stripped!";
		assert TrackMetadata.fold("បង\u200Bស្រលាញ់\u200Bអូន").equals(TrackMetadata.fold("បងស្រលាញ់អូន"))
			: "Khmer zero-width space must fold cleanly without splitting words";
		assert TrackMetadata.fold("周杰伦").equals("周杰伦");
		assert TrackMetadata.fold("봄날").equals("봄날");

		// 3. Bilingual Khmer & Chinese YouTube metadata candidate extraction
		List<TrackMetadata.QueryPair> khmerPairs = TrackMetadata.buildCandidates(
			"chrome.exe",
			"Rasmey Hang Meas",
			"Suly Pheng - សារជាតិ Unfaithful (Official Music Video) - YouTube"
		);
		assert khmerPairs.stream().anyMatch(p -> p.artist().equals("Suly Pheng") && p.title().equals("សារជាតិ"))
			: "Expected Khmer title candidate in " + khmerPairs;
		assert khmerPairs.stream().anyMatch(p -> p.artist().equals("Suly Pheng") && p.title().equals("Unfaithful"))
			: "Expected English title candidate in " + khmerPairs;

		List<TrackMetadata.QueryPair> vanndaPairs = TrackMetadata.buildCandidates(
			"chrome.exe",
			"VannDa Official",
			"វណ្ណដា VannDa - ម៉ែ (Mama) [វីដេអូផ្លូវការ]"
		);
		assert vanndaPairs.stream().anyMatch(p -> p.title().equals("ម៉ែ")) : vanndaPairs;
		assert vanndaPairs.stream().anyMatch(p -> p.title().equals("Mama")) : vanndaPairs;

		List<TrackMetadata.QueryPair> jayPairs = TrackMetadata.buildCandidates(
			"msedge.exe",
			"杰威爾音樂 JVR Music",
			"周杰伦 Jay Chou - 晴天 (Sunny Day) [官方MV] - YouTube"
		);
		assert jayPairs.stream().anyMatch(p -> p.artist().equals("周杰伦") && p.title().equals("晴天")) : jayPairs;

		// 4. Bilingual LRC parsing (Khmer + Chinese + Translation merge)
		String bilingualLrc = "[00:05.00]បងនឹកអូនគ្រប់ពេល\n[00:05.00]I miss you every moment\n[00:10.00]故事的小黄花\n[00:10.00]Little yellow flower of the story\n";
		List<LyricsFetcher.LyricLine> biLines = LyricsFetcher.parseLrc(bilingualLrc);
		assert biLines.size() == 2 : biLines;
		assert biLines.get(0).text().equals("បងនឹកអូនគ្រប់ពេល");
		assert biLines.get(0).translation().equals("I miss you every moment");
		assert biLines.get(1).text().equals("故事的小黄花");
		assert biLines.get(1).translation().equals("Little yellow flower of the story");

		// 5. Cluster-safe Karaoke word segmentation for Khmer & Chinese
		List<Karaoke.Word> khWords = Karaoke.words(khmer);
		assert khWords.size() >= 2 : khWords;
		for (Karaoke.Word w : khWords) {
			String sub = khmer.substring(w.start(), w.end());
			assert sub.charAt(0) != '\u17D2' : "Karaoke split inside Khmer Coeng cluster!";
		}
		List<Karaoke.Word> zhWords = Karaoke.words("从前从前有个人爱你很久");
		assert zhWords.size() >= 4 : zhWords;
	}

	private static void checkDynamicFontsAndHarfBuzzShaping() {
		// Verify all 7 presets shape headless glyphs and produce valid 2D Euclidean SDF fields
		for (IslandSettings.FontPreset preset : IslandSettings.FontPreset.values()) {
			String sample = preset == IslandSettings.FontPreset.KHMER_CLASSIC ? "សួស្តី" : "LT5";
			List<UnicodeClusterer.Cluster> clusters = UnicodeClusterer.segment(sample);
			assert !clusters.isEmpty();
			String clusterText = clusters.get(0).text();
			var shaped = DynamicSdfFont.shapeClusterHeadless(clusterText, preset);
			assert shaped != null : "Failed to shape cluster for preset " + preset;
			assert shaped.advanceEm() > 0.05F : "Zero advance for preset " + preset;
			assert shaped.visible() && shaped.widthEm() > 0.05F && shaped.heightEm() > 0.05F
				: "Empty glyph bounds for preset " + preset;
			byte[] sdf = DynamicSdfFont.computeSdfBytesHeadless(clusterText, preset);
			assert sdf.length > 0 : "Empty SDF output for preset " + preset;
			int maxAlpha = 0;
			int minAlpha = 255;
			for (byte b : sdf) {
				int u = b & 0xFF;
				maxAlpha = Math.max(maxAlpha, u);
				minAlpha = Math.min(minAlpha, u);
			}
			assert maxAlpha > 135 && minAlpha < 80
				: "SDF failed contour range for preset " + preset + " [min=" + minAlpha + ", max=" + maxAlpha + "]";
		}

		// Also verify Khmer Coeng ligature shaping ("ស្រ") + Chinese ("晴") produce crisp SDF ink & advance
		var khShaped = DynamicSdfFont.shapeClusterHeadless("ស្រ", IslandSettings.FontPreset.INTER_MODERN);
		assert khShaped != null && khShaped.visible() && khShaped.advanceEm() > 0.1F;
		byte[] khSdf = DynamicSdfFont.computeSdfBytesHeadless("ស្រ", IslandSettings.FontPreset.KHMER_CLASSIC);
		assert khSdf.length > 0;

		var zhShaped = DynamicSdfFont.shapeClusterHeadless("晴", IslandSettings.FontPreset.INTER_MODERN);
		assert zhShaped != null && zhShaped.visible() && zhShaped.advanceEm() > 0.2F;
	}

	private static void checkV140ColorWheelAndLivePropagation() {
		// 1. Strict HEX & RGB triplet parsing + formatting
		assert Integer.valueOf(0xFFFF79C6).equals(ColorUtil.parseHexStrict("#FF79C6"));
		assert Integer.valueOf(0xFF58E1FF).equals(ColorUtil.parseHexStrict("58e1ff"));
		assert Integer.valueOf(0xFFAABBCC).equals(ColorUtil.parseHexStrict("#ABC"));
		assert ColorUtil.parseHexStrict("#12FG99") == null;
		assert ColorUtil.parseHexStrict("") == null;

		assert Integer.valueOf(0xFFFF85B3).equals(ColorUtil.parseRgbTriplet("255, 133, 179"));
		assert Integer.valueOf(0xFF00F0FF).equals(ColorUtil.parseRgbTriplet("0 240 255"));
		assert ColorUtil.parseRgbTriplet("300, 10, 10") == null;
		assert ColorUtil.parseRgbTriplet("-1, 10, 10") == null;
		assert ColorUtil.toRgbString(0xFF1ED760).equals("30, 215, 96");

		// 2. Color wheel coordinate <-> HSV round-trip consistency
		float[] wheelPt = ColorUtil.hueSatToWheel(0.82F, 0.74F);
		float[] hsBack = ColorUtil.wheelToHueSat(wheelPt[0], wheelPt[1]);
		assert Math.abs(hsBack[0] - 0.82F) < 0.005F : "Hue drift: " + hsBack[0];
		assert Math.abs(hsBack[1] - 0.74F) < 0.005F : "Sat drift: " + hsBack[1];

		// 3. Selecting a color on the wheel updates World, Sky, and HUD lyrics live
		IslandSettings.separateSkyColors = false;
		IslandSettings.separateHighlightColor = false;
		IslandSettings.separateGlowColor = false;
		int picked = ColorUtil.hsvToRgb(hsBack[0], hsBack[1], 0.96F);
		IslandSettings.applyUnifiedLyricColor(picked);

		assert IslandSettings.resolveWorldPrimaryColor() == picked;
		assert IslandSettings.resolveSkyPrimaryColor() == picked;
		assert IslandSettings.resolveHudPrimaryColor() == picked;
		assert IslandSettings.hudActiveColor == picked;
		assert IslandSettings.skyActiveColor == picked;

		// Upcoming color adapts to chosen hue instead of staying hard-coded white
		int upcoming = ColorUtil.upcomingForPrimary(picked, 0xFF9A9A9A);
		assert (upcoming & 0xFFFFFF) != 0xFFFFFF : "Upcoming color should tint toward chosen wheel color";

		// 4. Separate highlight sweep in Karaoke.fillColors (zero-allocation buffer write)
		IslandSettings.separateHighlightColor = true;
		IslandSettings.lyricHighlightColor = 0xFF00F0FF;
		assert IslandSettings.resolveHighlightColor() == 0xFF00F0FF;

		int[] buf = new int[24];
		Karaoke.fillColors(buf, 20, 10.0F, 0xFFFCEE0A, 0xFF00F0FF, 0x807AD7E8, 1.0F);
		assert (buf[0] & 0xFFFFFF) == 0xFCEE0A : "Start of line should be sung primary color";
		assert (buf[9] & 0xFFFFFF) != 0xFCEE0A : "Active edge should sweep through highlight color";
	}

	private static void checkV140StudioModeAndAppearanceRestore() throws Exception {
		Path tempCfg = Files.createTempFile("lt5lyrics-v140-studio-", ".json");
		try {
			// Customize both appearance AND non-appearance settings
			IslandSettings.advancedSettingsMode = false; // Default Simple Mode
			IslandSettings.applyUnifiedLyricColor(0xFFFF79C6);
			IslandSettings.lyricTextScale = 1.35F;
			IslandSettings.lyricGlowEnabled = false;
			IslandSettings.lyricOutlineThickness = 0.65F;
			IslandSettings.lyricOutlineColor = 0xFF101828;
			IslandSettings.lyricTranslationColor = 0xFFFFE5B4;
			IslandSettings.enableLrcmux = false;
			IslandSettings.musixmatchApiKey = "test_user_key_1234";
			IslandSettings.posX = 142.0F;
			IslandSettings.posY = 18.0F;

			IslandSettings.StyleSnapshot beforeSnap = IslandSettings.captureStyleSnapshot();
			assert beforeSnap.primaryColor() == 0xFFFF79C6;
			assert !beforeSnap.glowEnabled();
			assert Math.abs(beforeSnap.textScale() - 1.35F) < 1.0E-4F;

			// Switching Simple -> Advanced -> Simple must NEVER reset any settings
			IslandSettings.advancedSettingsMode = true;
			IslandSettings.saveToFile(tempCfg);
			IslandSettings.applyUnifiedLyricColor(0xFFFFFFFF);
			IslandSettings.loadFromFile(tempCfg);

			assert IslandSettings.advancedSettingsMode;
			assert IslandSettings.resolveWorldPrimaryColor() == 0xFFFF79C6;
			assert Math.abs(IslandSettings.lyricTextScale - 1.35F) < 1.0E-4F;
			assert !IslandSettings.lyricGlowEnabled;
			assert Math.abs(IslandSettings.lyricOutlineThickness - 0.65F) < 1.0E-4F;
			assert !IslandSettings.enableLrcmux;
			assert "test_user_key_1234".equals(IslandSettings.musixmatchApiKey);

			// Restore Appearance Defaults resets ONLY appearance while keeping providers, keys, and HUD placement!
			IslandSettings.restoreAppearanceDefaults();
			assert IslandSettings.resolveWorldPrimaryColor() == 0xFFFFFFFF;
			assert Math.abs(IslandSettings.lyricTextScale - 1.0F) < 1.0E-4F;
			assert IslandSettings.lyricGlowEnabled;
			assert !IslandSettings.enableLrcmux : "Provider toggle must not be wiped by Restore Appearance Defaults";
			assert "test_user_key_1234".equals(IslandSettings.musixmatchApiKey) : "API key must not be wiped";
			assert Math.abs(IslandSettings.posX - 142.0F) < 1.0E-4F : "HUD position must not be wiped";
		} finally {
			IslandSettings.enableLrcmux = true;
			IslandSettings.musixmatchApiKey = "";
			IslandSettings.posX = -1.0F;
			IslandSettings.posY = -1.0F;
			IslandSettings.advancedSettingsMode = false;
			Files.deleteIfExists(tempCfg);
		}
	}

	private static void checkV140ExpandedProvidersAndWordSync() {
		// 1. BetterLyrics TTML syllable/word parser + translation metadata
		String sampleTtml = """
			<?xml version="1.0" encoding="UTF-8"?>
			<tt xmlns="http://www.w3.org/ns/ttml" xmlns:itunes="http://music.apple.com/lyric-ttml-internal" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
			  <head>
			    <metadata>
			      <translations>
			        <translation xml:lang="en">
			          <text for="L1">Hello starlight</text>
			          <text for="L2">Dancing through the night</text>
			          <text for="L3">Underneath the moon</text>
			        </translation>
			      </translations>
			    </metadata>
			  </head>
			  <body>
			    <div>
			      <p begin="00:10.000" end="00:13.000" itunes:key="L1"><span begin="00:10.000" end="00:11.200">Bonjour </span><span begin="00:11.200" end="00:13.000">étoile</span></p>
			      <p begin="00:13.500" end="00:16.500" itunes:key="L2"><span begin="00:13.500" end="00:15.000">Danser </span><span begin="00:15.000" end="00:16.500">la nuit</span></p>
			      <p begin="00:17.000" end="00:20.000" itunes:key="L3"><span begin="00:17.000" end="00:20.000">Sous la lune</span></p>
			    </div>
			  </body>
			</tt>
			""";
		List<LyricsFetcher.LyricLine> ttmlLines = BetterLyricsProvider.parseTtml(sampleTtml);
		assert ttmlLines.size() == 3 : "Expected 3 TTML lines, got " + ttmlLines;
		LyricsFetcher.LyricLine first = ttmlLines.get(0);
		assert "Bonjour étoile".equals(first.text()) : "Unexpected TTML text: '" + first.text() + "'";
		assert "Hello starlight".equals(first.translation()) : "Unexpected TTML translation: '" + first.translation() + "'";
		assert first.words().size() == 2 : "Expected 2 word spans";
		float midWord1 = Karaoke.sungChars(first, 0.5F, 10.60F);
		assert midWord1 > 3.5F && midWord1 < 4.5F : "Word-span karaoke should be mid-first-word: " + midWord1;
		float endWord2 = Karaoke.sungChars(first, 1.0F, 13.00F);
		assert Math.abs(endWord2 - first.text().length()) < 0.01F;

		// 2. Enhanced LRC <mm:ss.xx> inline word tag parsing
		String enhancedLrc = "[00:05.00]<00:05.00>Shine <00:06.00>bright <00:07.00>tonight\n[00:09.00]Second line\n";
		List<LyricsFetcher.LyricLine> enh = LyricsFetcher.parseLrc(enhancedLrc);
		assert enh.size() == 2;
		assert "Shine bright tonight".equals(enh.get(0).text()) : enh.get(0).text();
		assert enh.get(0).words().size() == 3 : enh.get(0).words();

		// 3. Lrcmux JSON parser + wrong-artist track collision rejection
		String lrcmuxGood = """
			{
			  "track": { "title": "Time to Rise", "artist": "VannDa", "duration": 248 },
			  "meta": { "level": "word", "source": { "id": "lrclib" } },
			  "lines": [
			    { "start": 12000, "text": "សួស្តីកម្ពុជា", "words": [ { "start": 12000, "end": 13000, "text": "សួស្តី" }, { "start": 13000, "end": 14500, "text": "កម្ពុជា" } ] },
			    { "start": 15000, "text": "ក្រោកឈរឡើង" },
			    { "start": 18000, "text": "Time to rise" }
			  ]
			}
			""";
		LyricsFetcher.ProviderHit muxHit = LrcmuxLyrics.parseJsonResponse(lrcmuxGood, "VannDa", "Time to Rise", 248L);
		assert muxHit.hasSynced() && muxHit.quality() == LyricsFetcher.SyncQuality.WORD_SYNCED;
		assert muxHit.synced().size() == 3;
		String geniusSource = lrcmuxGood.replace("\"id\": \"lrclib\"", "\"id\": \"genius\"");
		assert !LrcmuxLyrics.parseJsonResponse(geniusSource, "VannDa", "Time to Rise", 248L).hasSynced()
			: "Disallowed Genius upstream must be rejected even when source is an object";

		// Reject wrong artist on short title ("Stay" by Justin Bieber vs unrelated artist)
		String lrcmuxWrongArtist = """
			{
			  "track": { "title": "Stay", "artist": "Completely Unrelated Band", "duration": 141 },
			  "meta": { "level": "line" },
			  "lines": [
			    { "start": 5000, "text": "Wrong song line 1" },
			    { "start": 8000, "text": "Wrong song line 2" },
			    { "start": 12000, "text": "Wrong song line 3" }
			  ]
			}
			""";
		LyricsFetcher.ProviderHit rejected = LrcmuxLyrics.parseJsonResponse(lrcmuxWrongArtist, "The Kid LAROI", "Stay", 141L);
		assert !rejected.hasSynced() : "Track collision guard should reject unrelated artist on short title";

		// 4. Musixmatch commercial disclaimer stripping & 30%-truncation detection + secret redaction
		String mxBody = "First verse line\nSecond verse line\n...\n\n******* This Lyrics is NOT for Commercial use *******\n(1409624098570)";
		assert MusixmatchLyrics.isTruncatedCommercialPreview(mxBody);
		String stripped = MusixmatchLyrics.stripCommercialDisclaimer(mxBody);
		assert !stripped.contains("NOT for Commercial use");
		assert !stripped.contains("(1409624098570)");

		IslandSettings.musixmatchApiKey = "mx_secret_998877";
		String redacted = LyricsFetcher.redactSecrets("GET https://api.musixmatch.com/ws/1.1/matcher.subtitle.get?apikey=mx_secret_998877&q_track=test");
		assert !redacted.contains("mx_secret_998877") : "API key leaked in redacted string: " + redacted;
		IslandSettings.musixmatchApiKey = "";

		// 5. Arabic & Cyrillic dual-script candidate splitting
		List<TrackMetadata.QueryPair> cyrPairs = TrackMetadata.buildCandidates(
			"chrome.exe", "Кино", "Кино Viktor Tsoi - Группа крови (Gruppa krovi) [Official]"
		);
		assert cyrPairs.stream().anyMatch(p -> p.title().contains("Группа крови")) : cyrPairs;
		List<TrackMetadata.QueryPair> arPairs = TrackMetadata.buildCandidates(
			"chrome.exe", "Fairuz", "فيروز Fairuz - لبيروت (Li Beirut)"
		);
		assert arPairs.stream().anyMatch(p -> p.title().contains("لبيروت")) : arPairs;
	}
}
