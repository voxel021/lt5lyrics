package com.lt5lyrics.client.island.font;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.lt5lyrics.client.island.IslandSettings;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dynamic TrueType + HarfBuzz OpenType cluster-shaping SDF font atlas.
 *
 * <p>Shapes complex scripts (Khmer Coeng subscripts & split vowels, Chinese, Japanese, Korean,
 * Thai, Lao, Vietnamese, Arabic, Cyrillic, Latin) via OpenJDK's built-in HarfBuzz engine, computes
 * an exact linear-time 2D Euclidean Signed Distance Field per cluster, and caches tiles into a
 * GPU-resident Blaze3D DynamicTexture compatible with LT5Lyrics' MSDF HUD, Hologram, and Sky shaders.
 */
public final class DynamicSdfFont {
	private static final Logger LOGGER = LoggerFactory.getLogger("lt5lyrics");

	public static final int ATLAS_SIZE = 2048;
	public static final float EM_PX = 46.0F;
	public static final float SDF_SPREAD = 7.0F;
	public static final float SDF_RANGE = SDF_SPREAD * 2.0F; // 14.0F total range (-7px .. +7px)
	private static final int PAD_PX = 9;
	private static final float INF = 1.0E20F;

	public record CachedCluster(
		float advanceEm,
		float leftEm,
		float topEm,
		float widthEm,
		float heightEm,
		float minU,
		float minV,
		float maxU,
		float maxV,
		boolean visible
	) {
		public float[] quad(float size, float x, float baselineY) {
			float qx = x + leftEm * size;
			float qy = baselineY - topEm * size;
			float qw = widthEm * size;
			float qh = heightEm * size;
			return new float[] {
				qx, qy, 0.0F, minU, minV,
				qx, qy + qh, 0.0F, minU, maxV,
				qx + qw, qy + qh, 0.0F, maxU, maxV,
				qx + qw, qy, 0.0F, maxU, minV
			};
		}
	}

	private static final String[] OS_FALLBACK_FAMILIES = {
		"Leelawadee UI",
		"Khmer UI",
		"Microsoft YaHei UI",
		"Microsoft YaHei",
		"Microsoft JhengHei UI",
		"Yu Gothic UI",
		"Meiryo",
		"Malgun Gothic",
		"Nirmala UI",
		"Segoe UI",
		"Segoe UI Symbol",
		"PingFang SC",
		"Hiragino Sans",
		"Apple SD Gothic Neo",
		"Noto Sans CJK SC",
		"Noto Sans CJK JP",
		"Noto Sans CJK KR",
		"DejaVu Sans"
	};

	private static final Map<IslandSettings.FontPreset, java.awt.Font> BUNDLED_FONTS = new HashMap<>();
	private static final Map<String, java.awt.Font> CUSTOM_FILE_FONTS = new HashMap<>();
	private static final List<java.awt.Font> SYSTEM_FALLBACKS = new ArrayList<>();
	private static java.awt.Font khmerBundledFont;
	private static java.awt.Font logicalSansFallback;

	private static BufferedImage scratchImage;
	private static Graphics2D scratchG2d;
	private static Graphics2D scratchCrispG2d;

	private static DynamicTexture atlasTexture;
	private static boolean atlasDirty;
	private static int shelfX = 2;
	private static int shelfY = 2;
	private static int shelfRowHeight = 0;

	private static final Map<String, CachedCluster> CLUSTER_CACHE = new HashMap<>(2048);
	private static final Map<String, Float> WIDTH_CACHE = new HashMap<>(1024);
	private static boolean initialized;

	private DynamicSdfFont() {
	}

	public static synchronized void ensureInitialized() {
		if (initialized) return;
		System.setProperty("java.awt.headless", "true");
		scratchImage = new BufferedImage(384, 160, BufferedImage.TYPE_INT_ARGB);
		scratchG2d = scratchImage.createGraphics();
		scratchG2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		scratchG2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		scratchG2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

		scratchCrispG2d = scratchImage.createGraphics();
		scratchCrispG2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
		scratchCrispG2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
		scratchCrispG2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

		for (IslandSettings.FontPreset preset : IslandSettings.FontPreset.values()) {
			if (preset.resourceFile != null) {
				java.awt.Font loaded = loadBundledTtf(preset.resourceFile);
				if (loaded != null) {
					BUNDLED_FONTS.put(preset, loaded);
					if (preset == IslandSettings.FontPreset.KHMER_CLASSIC) {
						khmerBundledFont = loaded;
					}
				}
			}
		}
		if (khmerBundledFont == null) {
			khmerBundledFont = loadBundledTtf("khmer_classic.ttf");
		}

		try {
			String[] available = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames(Locale.ROOT);
			java.util.Set<String> availLower = new java.util.HashSet<>();
			for (String fam : available) {
				availLower.add(fam.toLowerCase(Locale.ROOT));
			}
			for (String wanted : OS_FALLBACK_FAMILIES) {
				if (availLower.contains(wanted.toLowerCase(Locale.ROOT))) {
					SYSTEM_FALLBACKS.add(new java.awt.Font(wanted, java.awt.Font.BOLD, Math.round(EM_PX)).deriveFont(EM_PX));
				}
			}
		} catch (Throwable t) {
			LOGGER.debug("System font enumeration skipped: {}", t.toString());
		}

		logicalSansFallback = new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, Math.round(EM_PX)).deriveFont(EM_PX);
		initialized = true;
	}

	private static java.awt.Font loadBundledTtf(String fileName) {
		String resourcePath = "/assets/lt5lyrics/fonts/ttf/" + fileName;
		try (InputStream in = DynamicSdfFont.class.getResourceAsStream(resourcePath)) {
			if (in != null) {
				return java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, in).deriveFont(EM_PX);
			}
		} catch (Exception ignored) {
		}
		try {
			Minecraft mc = Minecraft.getInstance();
			if (mc != null && mc.getResourceManager() != null) {
				Identifier id = Identifier.fromNamespaceAndPath("lt5lyrics", "fonts/ttf/" + fileName);
				try (InputStream in = mc.getResourceManager().open(id)) {
					return java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, in).deriveFont(EM_PX);
				}
			}
		} catch (Exception e) {
			LOGGER.warn("Failed to load bundled font {}: {}", fileName, e.toString());
		}
		return null;
	}

	public static synchronized void clearCustomFontCache() {
		CUSTOM_FILE_FONTS.clear();
		WIDTH_CACHE.clear();
	}

	private static java.awt.Font resolvePrimaryAwtFont(IslandSettings.FontPreset preset, String customFontName) {
		ensureInitialized();
		if (customFontName != null && !customFontName.isBlank()) {
			String cleanName = customFontName.trim();
			java.awt.Font cached = CUSTOM_FILE_FONTS.get(cleanName);
			if (cached != null) return cached;
			try {
				Path p = IslandSettings.customFontsDir().resolve(cleanName);
				if (Files.isRegularFile(p)) {
					try (InputStream in = Files.newInputStream(p)) {
						java.awt.Font created = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, in).deriveFont(EM_PX);
						CUSTOM_FILE_FONTS.put(cleanName, created);
						return created;
					}
				}
			} catch (Exception e) {
				LOGGER.warn("Could not load custom font '{}': {}", cleanName, e.toString());
			}
		}

		java.awt.Font presetFont = BUNDLED_FONTS.get(preset);
		if (presetFont != null) {
			return presetFont;
		}
		// INTER_MODERN fallback when rendering extended Unicode
		for (java.awt.Font sys : SYSTEM_FALLBACKS) {
			String fam = sys.getFamily(Locale.ROOT).toLowerCase(Locale.ROOT);
			if (fam.contains("segoe") || fam.contains("yahei") || fam.contains("dejavu")) {
				return sys;
			}
		}
		return logicalSansFallback;
	}

	/**
	 * Picks the best font capable of displaying every code point in the cluster so Khmer, Chinese,
	 * Japanese, Korean, Thai, Arabic, and Symbols always shape with a complete font face.
	 */
	public static java.awt.Font selectFontForCluster(
		String clusterText,
		IslandSettings.FontPreset preset,
		String customFontName
	) {
		ensureInitialized();
		int firstCp = clusterText.codePointAt(0);

		// Prefer bundled Kantumruy Pro first for Khmer unless custom font also supports Khmer
		if (UnicodeClusterer.isKhmerCodePoint(firstCp)) {
			java.awt.Font primary = resolvePrimaryAwtFont(preset, customFontName);
			if (primary != null && primary.canDisplayUpTo(clusterText) == -1) {
				return primary;
			}
			if (khmerBundledFont != null && khmerBundledFont.canDisplayUpTo(clusterText) == -1) {
				return khmerBundledFont;
			}
		}

		java.awt.Font primary = resolvePrimaryAwtFont(preset, customFontName);
		if (primary != null && primary.canDisplayUpTo(clusterText) == -1) {
			return primary;
		}
		if (khmerBundledFont != null && khmerBundledFont.canDisplayUpTo(clusterText) == -1) {
			return khmerBundledFont;
		}
		for (java.awt.Font fallback : SYSTEM_FALLBACKS) {
			if (fallback.canDisplayUpTo(clusterText) == -1) {
				return fallback;
			}
		}
		return logicalSansFallback;
	}

	private static String styleKey(IslandSettings.FontPreset preset, String customFontName) {
		if (customFontName != null && !customFontName.isBlank()) {
			return "C:" + customFontName.trim();
		}
		return "P:" + preset.ordinal();
	}

	public static float measureWidth(
		String text,
		float size,
		IslandSettings.FontPreset preset,
		String customFontName
	) {
		if (text == null || text.isEmpty()) return 0.0F;
		ensureInitialized();
		String sk = styleKey(preset, customFontName);
		String wKey = sk + "\u0000" + text;
		Float cachedEm = WIDTH_CACHE.get(wKey);
		if (cachedEm != null) {
			return cachedEm * size;
		}

		List<UnicodeClusterer.Cluster> clusters = UnicodeClusterer.segment(text);
		float totalEm = 0.0F;
		for (UnicodeClusterer.Cluster cl : clusters) {
			if (cl.whitespace()) {
				totalEm += 0.27F;
				continue;
			}
			CachedCluster cc = getOrBakeCluster(cl, preset, customFontName, false);
			totalEm += cc.advanceEm() + 0.015F;
		}
		if (WIDTH_CACHE.size() > 4096) {
			WIDTH_CACHE.clear();
		}
		WIDTH_CACHE.put(wKey, totalEm);
		return totalEm * size;
	}

	public static List<float[]> buildGlyphs(
		String text,
		float size,
		float x,
		float baselineY,
		IslandSettings.FontPreset preset,
		String customFontName,
		List<Integer> charIndices
	) {
		List<float[]> quads = new ArrayList<>();
		if (text == null || text.isEmpty()) return quads;
		ensureInitialized();

		List<UnicodeClusterer.Cluster> clusters = UnicodeClusterer.segment(text);
		float cursorX = x;
		for (UnicodeClusterer.Cluster cl : clusters) {
			if (cl.whitespace()) {
				cursorX += 0.27F * size;
				continue;
			}
			CachedCluster cc = getOrBakeCluster(cl, preset, customFontName, true);
			if (cc.visible()) {
				quads.add(cc.quad(size, cursorX, baselineY));
				if (charIndices != null) {
					charIndices.add(cl.start());
				}
			}
			cursorX += (cc.advanceEm() + 0.015F) * size;
		}
		flushAtlasIfDirty();
		return quads;
	}

	/**
	 * Headless-safe shape & metric verification entry point used by :checkBtp tests (does not touch
	 * Blaze3D GPU textures when uploadToGpu is false).
	 */
	public static CachedCluster shapeClusterHeadless(
		String clusterText,
		IslandSettings.FontPreset preset
	) {
		ensureInitialized();
		UnicodeClusterer.Cluster cl = new UnicodeClusterer.Cluster(0, clusterText.length(), clusterText, false, false);
		return bakeClusterInternal(cl, preset, "", false);
	}

	/**
	 * Headless verification helper: shapes & rasterizes a cluster onto BufferedImage and runs the
	 * exact 2D Euclidean SDF transform, returning the resulting [0..255] SDF byte field.
	 */
	public static synchronized byte[] computeSdfBytesHeadless(
		String clusterText,
		IslandSettings.FontPreset preset
	) {
		ensureInitialized();
		java.awt.Font awtFont = selectFontForCluster(clusterText, preset, "");
		if (awtFont == null) return new byte[0];
		boolean crispPixel = (preset == IslandSettings.FontPreset.MINECRAFT_PIXEL)
			&& !UnicodeClusterer.containsExtendedUnicode(clusterText);
		FontRenderContext frc = new FontRenderContext(null, !crispPixel, true);
		char[] chars = clusterText.toCharArray();
		GlyphVector gv = awtFont.layoutGlyphVector(frc, chars, 0, chars.length, java.awt.Font.LAYOUT_LEFT_TO_RIGHT);
		Rectangle2D vis = gv.getVisualBounds();
		if (vis.getWidth() <= 0.05 && vis.getHeight() <= 0.05) return new byte[0];

		int w = Math.min(256, (int) Math.ceil(vis.getWidth()) + PAD_PX * 2 + 2);
		int h = Math.min(256, (int) Math.ceil(vis.getHeight()) + PAD_PX * 2 + 2);
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2 = img.createGraphics();
		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
			crispPixel ? RenderingHints.VALUE_TEXT_ANTIALIAS_OFF : RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
		g2.setColor(Color.WHITE);
		g2.drawGlyphVector(gv, (float) (PAD_PX - Math.floor(vis.getX())), (float) (PAD_PX - Math.floor(vis.getY())));
		g2.dispose();

		int total = w * h;
		float[] outsideSq = new float[total];
		float[] insideSq = new float[total];
		float[] alpha = new float[total];
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				float cov = ((img.getRGB(x, y) >>> 24) & 0xFF) / 255.0F;
				alpha[y * w + x] = cov;
				if (cov <= 0.0F) {
					outsideSq[y * w + x] = INF;
					insideSq[y * w + x] = 0.0F;
				} else if (cov >= 1.0F) {
					outsideSq[y * w + x] = 0.0F;
					insideSq[y * w + x] = INF;
				} else {
					float dOut = Math.max(0.0F, 0.5F - cov);
					float dIn = Math.max(0.0F, cov - 0.5F);
					outsideSq[y * w + x] = dOut * dOut;
					insideSq[y * w + x] = dIn * dIn;
				}
			}
		}
		edt2d(outsideSq, w, h);
		edt2d(insideSq, w, h);

		byte[] out = new byte[total];
		for (int idx = 0; idx < total; idx++) {
			float dOut = (float) Math.sqrt(outsideSq[idx]);
			float dIn = (float) Math.sqrt(insideSq[idx]);
			float signedDist = (alpha[idx] >= 0.5F) ? dIn : -dOut;
			if (alpha[idx] > 0.02F && alpha[idx] < 0.98F) {
				signedDist = (alpha[idx] - 0.5F) * 1.15F;
			}
			float norm = 0.5F + (signedDist / SDF_RANGE);
			out[idx] = (byte) Math.clamp(Math.round(norm * 255.0F), 0, 255);
		}
		return out;
	}

	private static synchronized CachedCluster getOrBakeCluster(
		UnicodeClusterer.Cluster cluster,
		IslandSettings.FontPreset preset,
		String customFontName,
		boolean requireGpuAtlas
	) {
		String key = styleKey(preset, customFontName) + (requireGpuAtlas ? "@G:" : "@M:") + cluster.text();
		CachedCluster hit = CLUSTER_CACHE.get(key);
		if (hit != null) {
			return hit;
		}
		if (!requireGpuAtlas) {
			String gpuKey = styleKey(preset, customFontName) + "@G:" + cluster.text();
			CachedCluster gpuHit = CLUSTER_CACHE.get(gpuKey);
			if (gpuHit != null) return gpuHit;
		}
		CachedCluster baked = bakeClusterInternal(cluster, preset, customFontName, requireGpuAtlas);
		CLUSTER_CACHE.put(key, baked);
		return baked;
	}

	private static CachedCluster bakeClusterInternal(
		UnicodeClusterer.Cluster cluster,
		IslandSettings.FontPreset preset,
		String customFontName,
		boolean writeToGpuAtlas
	) {
		boolean crispPixel = (customFontName == null || customFontName.isBlank()) && preset.pixelCrisp;
		Graphics2D g2 = crispPixel ? scratchCrispG2d : scratchG2d;
		java.awt.Font awtFont = selectFontForCluster(cluster.text(), preset, customFontName);
		FontRenderContext frc = g2.getFontRenderContext();

		int layoutFlags = cluster.rtl() ? java.awt.Font.LAYOUT_RIGHT_TO_LEFT : java.awt.Font.LAYOUT_LEFT_TO_RIGHT;
		char[] chars = cluster.text().toCharArray();
		GlyphVector gv = awtFont.layoutGlyphVector(frc, chars, 0, chars.length, layoutFlags);

		float advancePx = (float) gv.getLogicalBounds().getWidth();
		if (advancePx <= 0.5F) {
			advancePx = awtFont.getStringBounds(cluster.text(), frc).getWidth() > 0
				? (float) awtFont.getStringBounds(cluster.text(), frc).getWidth()
				: EM_PX * 0.30F;
		}
		Rectangle2D vis = gv.getVisualBounds();
		if (vis.isEmpty() || vis.getWidth() <= 0.25 || vis.getHeight() <= 0.25) {
			return new CachedCluster(advancePx / EM_PX, 0, 0, 0, 0, 0, 0, 0, 0, false);
		}

		if (!writeToGpuAtlas) {
			float leftEm = (float) (vis.getX() - PAD_PX) / EM_PX;
			float topEm = (float) (-vis.getY() + PAD_PX) / EM_PX;
			float widthEm = (float) (vis.getWidth() + PAD_PX * 2) / EM_PX;
			float heightEm = (float) (vis.getHeight() + PAD_PX * 2) / EM_PX;
			return new CachedCluster(advancePx / EM_PX, leftEm, topEm, widthEm, heightEm, 0, 0, 0, 0, true);
		}

		ensureGpuAtlas();

		int inkW = Math.max(1, (int) Math.ceil(vis.getWidth()));
		int inkH = Math.max(1, (int) Math.ceil(vis.getHeight()));
		int tileW = inkW + PAD_PX * 2;
		int tileH = inkH + PAD_PX * 2;

		if (tileW > scratchImage.getWidth() || tileH > scratchImage.getHeight()) {
			int nw = Math.max(scratchImage.getWidth(), tileW + 32);
			int nh = Math.max(scratchImage.getHeight(), tileH + 32);
			scratchG2d.dispose();
			scratchCrispG2d.dispose();
			scratchImage = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
			scratchG2d = scratchImage.createGraphics();
			scratchG2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			scratchG2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			scratchG2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
			scratchCrispG2d = scratchImage.createGraphics();
			scratchCrispG2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
			scratchCrispG2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
			scratchCrispG2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
			g2 = crispPixel ? scratchCrispG2d : scratchG2d;
		}

		g2.setComposite(AlphaComposite.Clear);
		g2.fillRect(0, 0, tileW, tileH);
		g2.setComposite(AlphaComposite.SrcOver);
		g2.setColor(Color.WHITE);

		float drawX = (float) (PAD_PX - Math.floor(vis.getX()));
		float drawY = (float) (PAD_PX - Math.floor(vis.getY()));
		g2.drawGlyphVector(gv, drawX, drawY);

		// Allocate tile slot in 2048x2048 shelf atlas (reset cleanly if full)
		if (shelfX + tileW + 2 >= ATLAS_SIZE) {
			shelfX = 2;
			shelfY += shelfRowHeight + 2;
			shelfRowHeight = 0;
		}
		if (shelfY + tileH + 2 >= ATLAS_SIZE) {
			resetAtlasFull();
		}

		int ax = shelfX;
		int ay = shelfY;
		shelfX += tileW + 2;
		if (tileH > shelfRowHeight) {
			shelfRowHeight = tileH;
		}

		writeSdfTileToAtlas(ax, ay, tileW, tileH);
		atlasDirty = true;

		float leftEm = -drawX / EM_PX;
		float topEm = drawY / EM_PX;
		float widthEm = tileW / EM_PX;
		float heightEm = tileH / EM_PX;

		float minU = (ax + 0.5F) / ATLAS_SIZE;
		float minV = (ay + 0.5F) / ATLAS_SIZE;
		float maxU = (ax + tileW - 0.5F) / ATLAS_SIZE;
		float maxV = (ay + tileH - 0.5F) / ATLAS_SIZE;

		return new CachedCluster(
			advancePx / EM_PX,
			leftEm,
			topEm,
			widthEm,
			heightEm,
			minU,
			minV,
			maxU,
			maxV,
			true
		);
	}

	/**
	 * Exact O(W*H) 2D Euclidean Signed Distance Transform (Felzenszwalb & Huttenlocher) with
	 * sub-pixel edge seeding from anti-aliased coverage.
	 */
	private static void writeSdfTileToAtlas(int atlasX, int atlasY, int w, int h) {
		int n = w * h;
		float[] outsideSq = new float[n];
		float[] insideSq = new float[n];
		float[] alpha = new float[n];

		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int a = (scratchImage.getRGB(x, y) >>> 24) & 0xFF;
				float cov = a / 255.0F;
				alpha[y * w + x] = cov;
				if (cov <= 0.0F) {
					outsideSq[y * w + x] = INF;
					insideSq[y * w + x] = 0.0F;
				} else if (cov >= 1.0F) {
					outsideSq[y * w + x] = 0.0F;
					insideSq[y * w + x] = INF;
				} else {
					float dOut = Math.max(0.0F, 0.5F - cov);
					float dIn = Math.max(0.0F, cov - 0.5F);
					outsideSq[y * w + x] = dOut * dOut;
					insideSq[y * w + x] = dIn * dIn;
				}
			}
		}

		edt2d(outsideSq, w, h);
		edt2d(insideSq, w, h);

		NativeImage pixels = atlasTexture.getPixels();
		if (pixels == null) return;

		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int idx = y * w + x;
				float dOut = (float) Math.sqrt(outsideSq[idx]);
				float dIn = (float) Math.sqrt(insideSq[idx]);
				float signedDist = (alpha[idx] >= 0.5F) ? dIn : -dOut;
				// Refine subpixel contour smoothly near the anti-aliased edge
				if (alpha[idx] > 0.02F && alpha[idx] < 0.98F) {
					signedDist = (alpha[idx] - 0.5F) * 1.15F;
				}
				float norm = 0.5F + (signedDist / SDF_RANGE);
				int byteVal = Math.clamp(Math.round(norm * 255.0F), 0, 255);
				int argb = 0xFF000000 | (byteVal << 16) | (byteVal << 8) | byteVal;
				pixels.setPixel(atlasX + x, atlasY + y, argb);
			}
		}
	}

	private static void edt2d(float[] grid, int w, int h) {
		int maxDim = Math.max(w, h);
		float[] f = new float[maxDim];
		float[] d = new float[maxDim];
		int[] v = new int[maxDim];
		float[] z = new float[maxDim + 1];

		for (int y = 0; y < h; y++) {
			int row = y * w;
			for (int x = 0; x < w; x++) f[x] = grid[row + x];
			edt1d(f, d, v, z, w);
			for (int x = 0; x < w; x++) grid[row + x] = d[x];
		}
		for (int x = 0; x < w; x++) {
			for (int y = 0; y < h; y++) f[y] = grid[y * w + x];
			edt1d(f, d, v, z, h);
			for (int y = 0; y < h; y++) grid[y * w + x] = d[y];
		}
	}

	private static void edt1d(float[] f, float[] d, int[] v, float[] z, int n) {
		int k = 0;
		v[0] = 0;
		z[0] = -INF;
		z[1] = INF;
		for (int q = 1; q < n; q++) {
			float fq = f[q] + q * q;
			float s = (fq - (f[v[k]] + v[k] * v[k])) / (2.0F * q - 2.0F * v[k]);
			while (s <= z[k]) {
				k--;
				s = (fq - (f[v[k]] + v[k] * v[k])) / (2.0F * q - 2.0F * v[k]);
			}
			k++;
			v[k] = q;
			z[k] = s;
			z[k + 1] = INF;
		}
		k = 0;
		for (int q = 0; q < n; q++) {
			while (z[k + 1] < q) k++;
			float dx = q - v[k];
			d[q] = dx * dx + f[v[k]];
		}
	}

	private static void ensureGpuAtlas() {
		if (atlasTexture != null) return;
		atlasTexture = new DynamicTexture(() -> "lt5lyrics_dynamic_sdf_atlas", ATLAS_SIZE, ATLAS_SIZE, true);
		NativeImage pixels = atlasTexture.getPixels();
		if (pixels != null) {
			pixels.fillRect(0, 0, ATLAS_SIZE, ATLAS_SIZE, 0xFF000000);
		}
		atlasTexture.upload();
		atlasDirty = false;
	}

	private static long atlasRevision;

	/** Changes only when old dynamic-glyph UV coordinates become invalid. */
	public static synchronized long atlasRevision() { return atlasRevision; }

	private static void resetAtlasFull() {
		atlasRevision++;
		CLUSTER_CACHE.clear();
		shelfX = 2;
		shelfY = 2;
		shelfRowHeight = 0;
		if (atlasTexture != null && atlasTexture.getPixels() != null) {
			atlasTexture.getPixels().fillRect(0, 0, ATLAS_SIZE, ATLAS_SIZE, 0xFF000000);
			atlasDirty = true;
		}
	}

	public static synchronized void flushAtlasIfDirty() {
		if (atlasDirty && atlasTexture != null) {
			atlasTexture.upload();
			atlasDirty = false;
		}
	}

	public static GpuTextureView textureView() {
		ensureGpuAtlas();
		flushAtlasIfDirty();
		return atlasTexture.getTextureView();
	}

	public static GpuSampler linearSampler() {
		return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
	}
}
