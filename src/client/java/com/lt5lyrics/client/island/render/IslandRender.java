package com.lt5lyrics.client.island.render;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import com.lt5lyrics.client.island.ColorUtil;
import com.lt5lyrics.client.island.IslandSettings;
import com.lt5lyrics.client.island.font.Font;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class IslandRender {

	private static final int VERTEX_SIZE = 24;
	private static final int PARAM_FLOATS = 12;
	private static final float SMOOTHNESS = 0.5F;

	private static final List<Cmd> QUEUE = new ArrayList<>();
	private static final List<Cmd> WORLD_QUEUE = new ArrayList<>();
	private static Matrix4f worldModelView;
	private static GpuBufferSlice worldProjection;
	private static final List<float[]> SCISSORS = new ArrayList<>();
	private static float uiScale = 1.0F;

	/** Scale only commands submitted by the full Studio, never HUD or world queues. */
	public static void setUiScale(float scale) {
		uiScale = Float.isFinite(scale) ? Math.clamp(scale, 0.5F, 2.0F) : 1.0F;
	}

	// Reusable per-frame array pools to eliminate steady-state heap allocations in render loops
	private static final List<float[]> WORLD_FLOAT_POOL = new ArrayList<>();
	private static final List<int[]> WORLD_INT_POOL = new ArrayList<>();
	private static int worldFloatCursor = 0;
	private static int worldIntCursor = 0;

	private static final List<float[]> UI_FLOAT_POOL = new ArrayList<>();
	private static final List<int[]> UI_INT_POOL = new ArrayList<>();
	private static int uiFloatCursor = 0;
	private static int uiIntCursor = 0;
	private static final List<Integer> UI_SCRATCH_INDICES = new ArrayList<>(128);

	private static GpuBuffer vertexBuffer;
	private static int vertexBufferQuads;
	private static GpuBuffer uniformBuffer;
	private static int uniformSlots;
	private static int uniformAlign;
	private static GpuTexture snapshot;
	private static GpuTextureView snapshotView;
	private static GpuSampler snapshotSampler;
	private static boolean needsSnapshot;
	private static GpuBuffer projectionBuffer;
	private static float projectionWidth = -1.0F;
	private static float projectionHeight = -1.0F;
	private static final Logger LOGGER = LoggerFactory.getLogger("lt5lyrics");
	private static boolean loggedError;

	private record Cmd(
		RenderPipeline pipeline,
		GpuTextureView texture,
		GpuSampler sampler,
		float[] verts,
		int[] colors,
		int quadCount,
		float[] params,
		float[] scissor,
		float uiScale
	) {
		private Cmd(RenderPipeline pipeline, GpuTextureView texture, GpuSampler sampler,
			float[] verts, int[] colors, int quadCount, float[] params, float[] scissor) {
			this(pipeline, texture, sampler, verts, colors, quadCount, params, scissor, 1.0F);
		}
	}

	public static float[] acquireWorldFloats(int minLen) {
		int cap = Math.max(20, minLen);
		if (worldFloatCursor < WORLD_FLOAT_POOL.size()) {
			float[] existing = WORLD_FLOAT_POOL.get(worldFloatCursor);
			if (existing.length < cap) {
				existing = new float[Math.max(cap, existing.length * 2)];
				WORLD_FLOAT_POOL.set(worldFloatCursor, existing);
			}
			worldFloatCursor++;
			return existing;
		}
		float[] created = new float[Math.max(160, cap)];
		WORLD_FLOAT_POOL.add(created);
		worldFloatCursor++;
		return created;
	}

	public static int[] acquireWorldInts(int minLen) {
		int cap = Math.max(4, minLen);
		if (worldIntCursor < WORLD_INT_POOL.size()) {
			int[] existing = WORLD_INT_POOL.get(worldIntCursor);
			if (existing.length < cap) {
				existing = new int[Math.max(cap, existing.length * 2)];
				WORLD_INT_POOL.set(worldIntCursor, existing);
			}
			worldIntCursor++;
			return existing;
		}
		int[] created = new int[Math.max(32, cap)];
		WORLD_INT_POOL.add(created);
		worldIntCursor++;
		return created;
	}

	private static float[] acquireUiFloats(int minLen) {
		int cap = Math.max(20, minLen);
		if (uiFloatCursor < UI_FLOAT_POOL.size()) {
			float[] existing = UI_FLOAT_POOL.get(uiFloatCursor);
			if (existing.length < cap) {
				existing = new float[Math.max(cap, existing.length * 2)];
				UI_FLOAT_POOL.set(uiFloatCursor, existing);
			}
			uiFloatCursor++;
			return existing;
		}
		float[] created = new float[Math.max(80, cap)];
		UI_FLOAT_POOL.add(created);
		uiFloatCursor++;
		return created;
	}

	private static int[] acquireUiInts(int minLen) {
		int cap = Math.max(4, minLen);
		if (uiIntCursor < UI_INT_POOL.size()) {
			int[] existing = UI_INT_POOL.get(uiIntCursor);
			if (existing.length < cap) {
				existing = new int[Math.max(cap, existing.length * 2)];
				UI_INT_POOL.set(uiIntCursor, existing);
			}
			uiIntCursor++;
			return existing;
		}
		int[] created = new int[Math.max(16, cap)];
		UI_INT_POOL.add(created);
		uiIntCursor++;
		return created;
	}

	/**
	 * 3D Minecraft TextDisplay-style hologram background plate quad in world space.
	 */
	public static void submitWorldHologramPlate(
		float[] plateVerts,
		int plateArgb,
		float pixelWidth,
		float pixelHeight,
		float cornerRadius,
		boolean seeThrough,
		Matrix4f modelView,
		GpuBufferSlice projection
	) {
		worldModelView = modelView;
		worldProjection = projection;
		RenderPipeline pipeline = seeThrough
			? IslandPipelines.WORLD_SHAPE_SEE_THROUGH
			: IslandPipelines.WORLD_SHAPE;
		int[] plateCols = acquireWorldInts(4);
		Arrays.fill(plateCols, 0, 4, plateArgb);
		WORLD_QUEUE.add(new Cmd(
			pipeline,
			null,
			null,
			plateVerts,
			plateCols,
			1,
			params(
				Math.max(2.0F, pixelWidth),
				Math.max(2.0F, pixelHeight),
				SMOOTHNESS,
				1.0F,
				cornerRadius,
				cornerRadius,
				cornerRadius,
				cornerRadius,
				0, 0, 0, 0
			),
			null
		));
	}

	/** One Normal-only flat or continuous curved plate; UVs span the full rounded rectangle. */
	public static void submitNormalCurvedPlate(float[] verts, int quadCount, int argb,
			float pixelWidth, float pixelHeight, float cornerRadius, boolean seeThrough,
			Matrix4f modelView, GpuBufferSlice projection) {
		if (quadCount <= 0) return;
		worldModelView = modelView;
		worldProjection = projection;
		int[] colors = acquireWorldInts(quadCount * 4);
		Arrays.fill(colors, 0, quadCount * 4, argb);
		RenderPipeline pipeline = seeThrough ? IslandPipelines.NORMAL_CURVED_PLATE_SEE_THROUGH : IslandPipelines.NORMAL_CURVED_PLATE;
		WORLD_QUEUE.add(new Cmd(pipeline, null, null, verts, colors, quadCount,
				params(Math.max(2.0F, pixelWidth), Math.max(2.0F, pixelHeight), SMOOTHNESS, 1.0F,
						cornerRadius, cornerRadius, cornerRadius, cornerRadius,
						0, 0, 0, 0), null));
	}

	/** Normal-only shadow pass, with no screen-center cutout. */
	public static void submitNormalGlyphs(GpuTextureView texView, GpuSampler sampler, float sdfRange,
			float[] verts, int quadCount, int[] colors, boolean seeThrough, Matrix4f modelView,
			GpuBufferSlice projection) {
		if (quadCount <= 0) return;
		worldModelView = modelView;
		worldProjection = projection;
		RenderPipeline pipeline = seeThrough ? IslandPipelines.NORMAL_MSDF_SEE_THROUGH : IslandPipelines.NORMAL_MSDF;
		WORLD_QUEUE.add(new Cmd(pipeline, texView, sampler, verts, colors, quadCount,
				params(sdfRange, 0.05F, 0.5F, 0.0F, 0, 0, 0, 0,
						0, 0, 0, 0), null));
	}

	/** Normal-only styled glyphs, retaining the existing glow, outline and karaoke colors. */
	public static void submitNormalStyledGlyphs(GpuTextureView texView, GpuSampler sampler, float sdfRange,
			float[] verts, int quadCount, int[] colors, int[] glowColors, float glowStrength,
			int[] outlineColors, float outlineThickness, boolean seeThrough, Matrix4f modelView,
			GpuBufferSlice projection) {
		if (quadCount <= 0) return;
		worldModelView = modelView;
		worldProjection = projection;
		RenderPipeline pipeline = seeThrough ? IslandPipelines.NORMAL_MSDF_SEE_THROUGH : IslandPipelines.NORMAL_MSDF;
		if (glowStrength > 0.01F && glowColors != null) {
			float glowThick = 0.05F + 0.15F * glowStrength;
			float glowSmooth = 1.8F + 3.4F * glowStrength;
			WORLD_QUEUE.add(new Cmd(pipeline, texView, sampler, verts, glowColors, quadCount,
					params(sdfRange, glowThick, glowSmooth, 0, 0, 0, 0, 0,
							0, 0, 0, 0), null));
		}
		if (outlineThickness > 0.01F && outlineColors != null) {
			float outThick = 0.05F + 0.16F * outlineThickness;
			WORLD_QUEUE.add(new Cmd(pipeline, texView, sampler, verts, outlineColors, quadCount,
					params(sdfRange, outThick, 0.55F, 0, 0, 0, 0, 0,
							0, 0, 0, 0), null));
		}
		WORLD_QUEUE.add(new Cmd(pipeline, texView, sampler, verts, colors, quadCount,
				params(sdfRange, 0.05F, 0.5F, 0, 0, 0, 0, 0,
						0, 0, 0, 0), null));
	}

	/**
	 * Unified 3D world & Sky glyph submission supporting SDF soft glow, SDF crisp outline, and main fill.
	 */
	public static void submitWorldStyledGlyphs(
		GpuTextureView texView,
		GpuSampler sampler,
		float sdfRange,
		float[] verts,
		int quadCount,
		int[] colors,
		int[] glowColors,
		float glowStrength,
		int[] outlineColors,
		float outlineThickness,
		boolean skyMode,
		boolean seeThrough,
		Matrix4f modelView,
		GpuBufferSlice projection
	) {
		if (quadCount <= 0) return;
		worldModelView = modelView;
		worldProjection = projection;
		RenderPipeline pipeline = seeThrough
			? (skyMode ? IslandPipelines.SKY_MSDF : IslandPipelines.MSDF)
			: IslandPipelines.WORLD_MSDF;

		float skyFarFlag = skyMode ? 1.0F : 0.0F;
		if (glowStrength > 0.01F && glowColors != null) {
			float glowThick = 0.05F + 0.15F * glowStrength;
			float glowSmooth = 1.8F + 3.4F * glowStrength;
			WORLD_QUEUE.add(new Cmd(pipeline, texView, sampler,
				verts, glowColors, quadCount, params(sdfRange, glowThick, glowSmooth, 0.0F,
					0, 0, 0, 0, 0, 0, skyFarFlag, 0), null));
		}
		if (outlineThickness > 0.01F && outlineColors != null) {
			float outThick = 0.05F + 0.16F * outlineThickness;
			WORLD_QUEUE.add(new Cmd(pipeline, texView, sampler,
				verts, outlineColors, quadCount, params(sdfRange, outThick, 0.55F, 0.0F,
					0, 0, 0, 0, 0, 0, skyFarFlag, 0), null));
		}
		WORLD_QUEUE.add(new Cmd(pipeline, texView, sampler,
			verts, colors, quadCount, params(sdfRange, 0.05F, 0.5F, 0.0F,
				0, 0, 0, 0, 0, 0, skyFarFlag, 0), null));
	}

	/**
	 * Normal 3D world hologram glyph submission supporting both MSDF and dynamic Unicode SDF fonts.
	 */
	public static void submitWorldGlyphs(
		GpuTextureView texView,
		GpuSampler sampler,
		float sdfRange,
		float[] verts,
		int[] colors,
		boolean seeThrough,
		Matrix4f modelView,
		GpuBufferSlice projection
	) {
		submitWorldGlyphsCount(texView, sampler, sdfRange, verts, verts.length / 20, colors, seeThrough, modelView, projection);
	}

	public static void submitWorldGlyphsCount(
		GpuTextureView texView,
		GpuSampler sampler,
		float sdfRange,
		float[] verts,
		int quadCount,
		int[] colors,
		boolean seeThrough,
		Matrix4f modelView,
		GpuBufferSlice projection
	) {
		if (quadCount <= 0) return;
		worldModelView = modelView;
		worldProjection = projection;
		RenderPipeline pipeline = seeThrough
			? IslandPipelines.MSDF
			: IslandPipelines.WORLD_MSDF;
		WORLD_QUEUE.add(new Cmd(pipeline, texView, sampler,
			verts, colors, quadCount, params(sdfRange, 0.05F, 0.5F, 0.0F,
				0, 0, 0, 0, 0, 0, 0, 0), null));
	}

	/**
	 * Legacy MSDF world submission convenience wrapper.
	 */
	public static void submitWorld(AbstractTexture texture, float[] verts, int[] colors,
		Matrix4f modelView, GpuBufferSlice projection) {
		submitWorldGlyphs(
			texture.getTextureView(),
			texture.getSampler(),
			msdfRange(),
			verts,
			colors,
			IslandSettings.throughBlocks,
			modelView,
			projection
		);
	}

	/**
	 * Sky Lyrics world submission with luminous SDF glow halo pass and dedicated Sky block occlusion.
	 */
	public static void submitWorldSkyGlyphs(
		GpuTextureView texView,
		GpuSampler sampler,
		float sdfRange,
		float[] verts,
		int[] colors,
		int[] glowColors,
		float glowStrength,
		boolean seeThrough,
		Matrix4f modelView,
		GpuBufferSlice projection
	) {
		submitWorldStyledGlyphs(
			texView,
			sampler,
			sdfRange,
			verts,
			verts.length / 20,
			colors,
			glowColors,
			glowStrength,
			null,
			0.0F,
			true,
			seeThrough,
			modelView,
			projection
		);
	}

	/**
	 * Sky Lyrics legacy convenience wrapper using dedicated IslandSettings.skyThroughBlocks.
	 */
	public static void submitWorldSky(AbstractTexture texture, float[] verts, int[] colors,
		int[] glowColors, float glowStrength, Matrix4f modelView, GpuBufferSlice projection) {
		submitWorldSkyGlyphs(
			texture.getTextureView(),
			texture.getSampler(),
			msdfRange(),
			verts,
			colors,
			glowColors,
			glowStrength,
			IslandSettings.skyThroughBlocks,
			modelView,
			projection
		);
	}

	private static float msdfRange() {
		return com.lt5lyrics.client.island.font.Fonts.MEDIUM != null
			? com.lt5lyrics.client.island.font.Fonts.MEDIUM.getAtlas().range() : 10.0F;
	}

	public static void pushScissor(float x, float y, float width, float height) {
		if (SCISSORS.isEmpty()) {
			SCISSORS.add(new float[] { x, y, width, height });
			return;
		}
		float[] parent = SCISSORS.get(SCISSORS.size() - 1);
		float nx1 = Math.max(parent[0], x);
		float ny1 = Math.max(parent[1], y);
		float nx2 = Math.min(parent[0] + parent[2], x + width);
		float ny2 = Math.min(parent[1] + parent[3], y + height);
		SCISSORS.add(new float[] { nx1, ny1, Math.max(0.0F, nx2 - nx1), Math.max(0.0F, ny2 - ny1) });
	}

	public static void popScissor() {
		if (!SCISSORS.isEmpty()) SCISSORS.remove(SCISSORS.size() - 1);
	}

	private static float[] scissor() {
		return SCISSORS.isEmpty() ? null : SCISSORS.get(SCISSORS.size() - 1);
	}

	private static void submit(RenderPipeline pipeline, GpuTextureView tex, GpuSampler sampler,
		float[] verts, int[] colors, int quadCount, float[] params) {
		if (quadCount <= 0) return;
		QUEUE.add(new Cmd(pipeline, tex, sampler, verts, colors, quadCount, params, scissor(), uiScale));
	}

	private static int[] fillUi(int color, int vertices) {
		int[] colors = acquireUiInts(vertices);
		Arrays.fill(colors, 0, vertices, color);
		return colors;
	}

	public static void drawRoundedRect(float x, float y, float width, float height,
		float tl, float bl, float tr, float br, int argb) {
		float hp = -SMOOTHNESS / 2.0F + SMOOTHNESS * 2.0F;
		float vp = SMOOTHNESS / 2.0F + SMOOTHNESS;
		submit(IslandPipelines.SHAPE, null, null,
			quadUi(x - hp / 2.0F, y - vp / 2.0F, width + hp, height + vp), fillUi(argb, 4), 1,
			params(width, height, SMOOTHNESS, 1.0F, tl, bl, tr, br, 0, 0, 0, 0));
	}

	public static void drawSquircle(float x, float y, float width, float height, float squirt,
		float tl, float bl, float tr, float br, int argb) {
		float hp = -SMOOTHNESS / 2.0F + SMOOTHNESS * 2.0F;
		float vp = SMOOTHNESS / 2.0F + SMOOTHNESS;
		submit(IslandPipelines.SHAPE, null, null,
			quadUi(x - hp / 2.0F, y - vp / 2.0F, width + hp, height + vp), fillUi(argb, 4), 1,
			params(width, height, SMOOTHNESS, squirt,
				tl * squirt / 2.0F, bl * squirt / 2.0F, tr * squirt / 2.0F, br * squirt / 2.0F, 0, 0, 0, 0));
	}

	public static void drawColorWheel(float x, float y, float diameter, float brightness, int argb) {
		submit(IslandPipelines.WHEEL, null, null,
			quadUi(x, y, diameter, diameter), fillUi(argb, 4), 1,
			params(diameter, diameter, SMOOTHNESS, 1.0F, 0, 0, 0, 0, brightness, 0, 0, 0));
	}

	public static void drawTexture(Identifier id, float x, float y, float width, float height, int argb) {
		AbstractTexture tex = texture(id);
		if (tex == null) return;
		submit(IslandPipelines.TEXTURE, tex.getTextureView(), tex.getSampler(),
			quadUi(x, y, width, height), fillUi(argb, 4), 1,
			params(width, height, SMOOTHNESS, 1.0F, 0, 0, 0, 0, 0, 0, 0, 0));
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
		AbstractTexture tex = texture(id);
		if (tex == null) return;
		float hp = -SMOOTHNESS / 2.0F + SMOOTHNESS * 2.0F;
		float vp = SMOOTHNESS / 2.0F + SMOOTHNESS;
		submit(IslandPipelines.TEXTURE, tex.getTextureView(), tex.getSampler(),
			quadUi(x - hp / 2.0F, y - vp / 2.0F, width + hp, height + vp), fillUi(argb, 4), 1,
			params(width, height, SMOOTHNESS, 1.0F, tl, bl, tr, br, 0, 0, 0, 0));
	}

	public static void drawBlur(float x, float y, float width, float height, float blurRadius, float squirt,
		float tl, float bl, float tr, float br, int argb) {
		blurRadius /= 22.5F;
		if (blurRadius <= 0.0F) return;
		needsSnapshot = true;
		Minecraft mc = Minecraft.getInstance();
		float sw = mc.getWindow().getGuiScaledWidth();
		float sh = mc.getWindow().getGuiScaledHeight();
		float u = x * uiScale / sw;
		float v = (sh - (y + height) * uiScale) / sh;
		float tw = width * uiScale / sw;
		float th = height * uiScale / sh;
		float[] verts = acquireUiFloats(20);
		verts[0] = x; verts[1] = y; verts[2] = 0; verts[3] = u; verts[4] = v + th;
		verts[5] = x; verts[6] = y + height; verts[7] = 0; verts[8] = u; verts[9] = v;
		verts[10] = x + width; verts[11] = y + height; verts[12] = 0; verts[13] = u + tw; verts[14] = v;
		verts[15] = x + width; verts[16] = y; verts[17] = 0; verts[18] = u + tw; verts[19] = v + th;
		submit(IslandPipelines.BLUR, null, null, verts, fillUi(argb, 4), 1,
			params(width, height, 0.1F, squirt,
				tl * squirt / 2.0F, bl * squirt / 2.0F, tr * squirt / 2.0F, br * squirt / 2.0F,
				blurRadius, 0, 0, 0));
	}

	public static void drawText(Font font, String text, float x, float y, int argb) {
		text(font, text, x, y, argb, null, -1, false, 0.0F, 0.0F, 0.0F, 0.0F, 0.05F, 0.5F);
	}

	public static void drawFadeoutText(Font font, String text, float x, float y, int argb,
		float fadeStart, float fadeEnd, float maxWidth) {
		drawWindowedText(font, text, x, y, argb, x, x + maxWidth, 7.0F, 0.0F);
	}

	public static void drawWindowedText(Font font, String text, float x, float y, int argb,
		float windowStart, float windowEnd, float fadeRight, float fadeLeft) {
		text(font, text, x, y, argb, null, -1, true, windowStart, windowEnd, fadeRight, fadeLeft, 0.05F, 0.5F);
	}

	public static void drawKaraokeText(Font font, String text, float x, float y, int[] charColors,
		float windowStart, float windowEnd, float fadeRight, float fadeLeft) {
		text(font, text, x, y, 0xFFFFFFFF, charColors, charColors == null ? -1 : charColors.length,
			true, windowStart, windowEnd, fadeRight, fadeLeft, 0.05F, 0.5F);
	}

	/**
	 * Shared 2D Styled Lyric Renderer used identically by BOTH the Settings Live Preview Panel
	 * and the Dynamic Island HUD so preview colors, glow, outlines, shadows, and karaoke sweeps
	 * match actual in-game rendering 1:1.
	 */
	public static void drawStyledLyric2D(
		Font font,
		String text,
		float x,
		float y,
		int baseArgb,
		int[] charColors,
		int charColorsLen,
		int glowArgb,
		float glowStrength,
		int outlineArgb,
		float outlineThickness,
		boolean dropShadow,
		boolean windowed,
		float windowStart,
		float windowEnd,
		float fadeRight,
		float fadeLeft
	) {
		if (text == null || text.isEmpty()) return;
		if (dropShadow) {
			float shOff = Math.max(0.45F, font.getSize() * 0.075F);
			int shAlpha = Math.min(255, Math.round(((baseArgb >>> 24) & 0xFF) * 0.68F));
			int shCol = (shAlpha << 24) | 0x06080E;
			text(font, text, x + shOff, y + shOff, shCol, null, -1,
				windowed, windowStart, windowEnd, fadeRight, fadeLeft, 0.05F, 0.55F);
		}
		if (glowStrength > 0.01F && ((glowArgb >>> 24) & 0xFF) > 2) {
			text(font, text, x, y, glowArgb, null, -1,
				windowed, windowStart, windowEnd, fadeRight, fadeLeft,
				0.05F + 0.14F * glowStrength, 1.5F + 2.9F * glowStrength);
		}
		if (outlineThickness > 0.01F && ((outlineArgb >>> 24) & 0xFF) > 2) {
			text(font, text, x, y, outlineArgb, null, -1,
				windowed, windowStart, windowEnd, fadeRight, fadeLeft,
				0.05F + 0.15F * outlineThickness, 0.55F);
		}
		text(font, text, x, y, baseArgb, charColors, charColorsLen,
			windowed, windowStart, windowEnd, fadeRight, fadeLeft, 0.05F, 0.5F);
	}

	public static void drawCenteredText(Font font, String text, float x, float y, int argb) {
		text(font, text, x - font.width(text) / 2.0F, y, argb, null, -1, false, 0.0F, 0.0F, 0.0F, 0.0F, 0.05F, 0.5F);
	}

	public static void drawCenteredGlowText(Font font, String text, float x, float y, int textArgb, int glowArgb, float glowStrength) {
		float left = x - font.width(text) / 2.0F;
		if (glowStrength > 0.01F) {
			text(font, text, left, y, glowArgb, null, -1, false, 0.0F, 0.0F, 0.0F, 0.0F,
				0.05F + 0.14F * glowStrength, 1.5F + 2.8F * glowStrength);
		}
		text(font, text, left, y, textArgb, null, -1, false, 0.0F, 0.0F, 0.0F, 0.0F, 0.05F, 0.5F);
	}

	private static void text(Font font, String text, float x, float y, int argb, int[] charColors, int charColorsLen,
		boolean windowed, float windowStart, float windowEnd, float fadeRight, float fadeLeft,
		float thickness, float smoothness) {
		if (text == null || text.isEmpty()) return;
		float size = font.getSize();
		List<Integer> charIndices = null;
		if (charColors != null) {
			UI_SCRATCH_INDICES.clear();
			charIndices = UI_SCRATCH_INDICES;
		}
		List<float[]> glyphs = font.buildGlyphs(text, x - 0.75F, y + size * 0.7F, charIndices);
		int qCount = glyphs.size();
		if (qCount == 0) return;

		float[] verts = acquireUiFloats(qCount * 20);
		int o = 0;
		for (int i = 0; i < qCount; i++) {
			System.arraycopy(glyphs.get(i), 0, verts, o, 20);
			o += 20;
		}
		int[] colors = acquireUiInts(qCount * 4);
		for (int i = 0; i < qCount; i++) {
			int color = argb;
			if (charColors != null && charIndices != null && i < charIndices.size()) {
				int index = charIndices.get(i);
				if (index >= 0 && index < charColorsLen) color = charColors[index];
			}
			int base = i * 4;
			colors[base] = color;
			colors[base + 1] = color;
			colors[base + 2] = color;
			colors[base + 3] = color;
		}
		submit(IslandPipelines.MSDF, font.textureView(text), font.sampler(text), verts, colors, qCount,
			params(font.sdfRange(text), thickness, smoothness, 0.0F,
				windowStart * uiScale, windowEnd * uiScale, fadeRight * uiScale, fadeLeft * uiScale,
				0, 0, 0, windowed ? 1.0F : 0.0F));
	}

	private static AbstractTexture texture(Identifier id) {
		try {
			return Minecraft.getInstance().getTextureManager().getTexture(id);
		} catch (Exception e) {
			return null;
		}
	}

	private static float[] params(float a, float b, float c, float d,
		float r0, float r1, float r2, float r3,
		float e0, float e1, float e2, float e3) {
		float[] p = acquireUiFloats(12);
		p[0] = a; p[1] = b; p[2] = c; p[3] = d;
		p[4] = r0; p[5] = r1; p[6] = r2; p[7] = r3;
		p[8] = e0; p[9] = e1; p[10] = e2; p[11] = e3;
		return p;
	}

	private static float[] quadUi(float x, float y, float w, float h) {
		float[] q = acquireUiFloats(20);
		q[0] = x; q[1] = y; q[2] = 0; q[3] = 0; q[4] = 0;
		q[5] = x; q[6] = y + h; q[7] = 0; q[8] = 0; q[9] = 1;
		q[10] = x + w; q[11] = y + h; q[12] = 0; q[13] = 1; q[14] = 1;
		q[15] = x + w; q[16] = y; q[17] = 0; q[18] = 1; q[19] = 0;
		return q;
	}

	private static boolean loggedOnce;

	public static void flushWorld() {
		if (!WORLD_QUEUE.isEmpty()) {
			try {
				executeWorld();
			} catch (Throwable e) {
				if (!loggedError) {
					loggedError = true;
					LOGGER.error("world lyrics render failed", e);
				}
			} finally {
				WORLD_QUEUE.clear();
				worldFloatCursor = 0;
				worldIntCursor = 0;
			}
		} else {
			worldFloatCursor = 0;
			worldIntCursor = 0;
		}
	}

	public static void flush() {
		if (!loggedOnce && !QUEUE.isEmpty()) {
			loggedOnce = true;
		}
		if (QUEUE.isEmpty()) {
			needsSnapshot = false;
			uiFloatCursor = 0;
			uiIntCursor = 0;
			return;
		}
		try {
			execute();
		} catch (Throwable e) {
			if (!loggedError) {
				loggedError = true;
				LOGGER.error("lt5lyrics render failed", e);
			}
		} finally {
			QUEUE.clear();
			SCISSORS.clear();
			needsSnapshot = false;
			uiFloatCursor = 0;
			uiIntCursor = 0;
		}
	}

	private static void executeWorld() {
		if (worldModelView == null || worldProjection == null) return;
		Minecraft mc = Minecraft.getInstance();
		GpuDevice device = RenderSystem.getDevice();
		CommandEncoder encoder = device.createCommandEncoder();
		RenderTarget target = mc.gameRenderer.mainRenderTarget();

		int quadCount = 0;
		for (Cmd cmd : WORLD_QUEUE) quadCount += cmd.quadCount();
		if (quadCount == 0) return;
		ensureVertexBuffer(device, quadCount);
		writeVertices(encoder, WORLD_QUEUE, quadCount);

		ensureUniformBuffer(device, WORLD_QUEUE.size());
		writeUniforms(encoder, WORLD_QUEUE);

		RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
		GpuBuffer ibuf = indices.getBuffer(quadCount * 6);
		IndexType itype = indices.type();

		GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(
			worldModelView, new Vector4f(1.0F, 1.0F, 1.0F, 1.0F), new Vector3f(), new Matrix4f());

		GpuTextureView colorView = target.getColorTextureView();
		GpuTextureView depthView = target.getDepthTextureView();

		drawWorldInto(encoder, colorView, depthView, java.util.Optional.empty(),
			transform, ibuf, itype);
	}

	private static void drawWorldInto(CommandEncoder encoder, GpuTextureView view, GpuTextureView depthView, java.util.Optional<org.joml.Vector4fc> clear,
		GpuBufferSlice transform, GpuBuffer ibuf, IndexType itype) {
		int baseVertex = 0;
		try (RenderPass pass = encoder.createRenderPass(() -> "lt5lyrics world",
			view, clear, depthView, OptionalDouble.empty())) {
			pass.setUniform("Projection", worldProjection);
			pass.setUniform("DynamicTransforms", transform);
			pass.setVertexBuffer(0, vertexBuffer.slice());
			pass.setIndexBuffer(ibuf, itype);
			for (int i = 0; i < WORLD_QUEUE.size(); i++) {
				Cmd cmd = WORLD_QUEUE.get(i);
				int quads = cmd.quadCount();
				pass.setPipeline(RenderSystem.getCompiledPipeline(cmd.pipeline()));
				pass.setUniform("IslandParams", uniformBuffer.slice(i * uniformAlign, uniformAlign));
				if (cmd.texture() != null) pass.setUniform("Sampler0", cmd.texture(), cmd.sampler());
				pass.drawIndexed(quads * 6, 1, 0, baseVertex, 0);
				baseVertex += quads * 4;
			}
		}
	}

	private static void writeVertices(CommandEncoder encoder, List<Cmd> queue, int quadCount) {
		ByteBuffer vbuf = MemoryUtil.memAlloc(quadCount * 4 * VERTEX_SIZE);
		vbuf.order(ByteOrder.nativeOrder());
		for (Cmd cmd : queue) {
			float[] v = cmd.verts();
			int[] colors = cmd.colors();
			int floatEnd = cmd.quadCount() * 20;
			for (int i = 0, vertex = 0; i < floatEnd; i += 5, vertex++) {
				vbuf.putFloat(v[i] * cmd.uiScale()).putFloat(v[i + 1] * cmd.uiScale()).putFloat(v[i + 2]);
				vbuf.putFloat(v[i + 3]).putFloat(v[i + 4]);
				int c = colors[vertex];
				vbuf.put((byte) (c >> 16 & 0xFF)).put((byte) (c >> 8 & 0xFF))
					.put((byte) (c & 0xFF)).put((byte) (c >>> 24 & 0xFF));
			}
		}
		vbuf.flip();
		encoder.writeToBuffer(vertexBuffer.slice(0, vbuf.remaining()), vbuf);
		MemoryUtil.memFree(vbuf);
	}

	private static void writeUniforms(CommandEncoder encoder, List<Cmd> queue) {
		ByteBuffer pbuf = MemoryUtil.memAlloc(uniformAlign);
		pbuf.order(ByteOrder.nativeOrder());
		for (int i = 0; i < queue.size(); i++) {
			pbuf.clear();
			float[] p = queue.get(i).params();
			for (int k = 0; k < PARAM_FLOATS; k++) pbuf.putFloat(p[k]);
			while (pbuf.hasRemaining()) pbuf.putFloat(0.0F);
			pbuf.flip();
			encoder.writeToBuffer(uniformBuffer.slice(i * uniformAlign, uniformAlign), pbuf);
		}
		MemoryUtil.memFree(pbuf);
	}

	private static void execute() {
		Minecraft mc = Minecraft.getInstance();
		GpuDevice device = RenderSystem.getDevice();
		CommandEncoder encoder = device.createCommandEncoder();
		RenderTarget target = mc.gameRenderer.mainRenderTarget();

		if (needsSnapshot) updateSnapshot(device, encoder, target);

		int quadCount = 0;
		for (Cmd cmd : QUEUE) quadCount += cmd.quadCount();
		if (quadCount == 0) return;
		ensureVertexBuffer(device, quadCount);
		ensureUniformBuffer(device, QUEUE.size());
		writeVertices(encoder, QUEUE, quadCount);
		writeUniforms(encoder, QUEUE);

		RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
		GpuBuffer ibuf = indices.getBuffer(quadCount * 6);
		IndexType itype = indices.type();

		GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(
			new Matrix4f(), new Vector4f(1.0F, 1.0F, 1.0F, 1.0F), new Vector3f(), new Matrix4f());

		float guiWidth = mc.getWindow().getGuiScaledWidth();
		float guiHeight = mc.getWindow().getGuiScaledHeight();
		GpuBufferSlice proj = projectionSlice(guiWidth, guiHeight);

		int guiScale = mc.getWindow().getGuiScale();
		int screenHeight = mc.getWindow().getHeight();
		int baseVertex = 0;
		RenderSystem.backupProjectionMatrix();
		RenderSystem.setProjectionMatrix(proj, ProjectionType.ORTHOGRAPHIC);
		try (RenderPass pass = encoder.createRenderPass(() -> "lt5lyrics",
			target.getColorTextureView(), java.util.Optional.empty(), null, OptionalDouble.empty())) {
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("DynamicTransforms", transform);
			pass.setVertexBuffer(0, vertexBuffer.slice());
			pass.setIndexBuffer(ibuf, itype);
			for (int i = 0; i < QUEUE.size(); i++) {
				Cmd cmd = QUEUE.get(i);
				int quads = cmd.quadCount();
				pass.setPipeline(RenderSystem.getCompiledPipeline(cmd.pipeline()));
				pass.setUniform("IslandParams", uniformBuffer.slice(i * uniformAlign, uniformAlign));
				if (cmd.pipeline() == IslandPipelines.BLUR) {
					pass.setUniform("Sampler0", snapshotView, snapshotSampler);
				} else if (cmd.texture() != null) {
					pass.setUniform("Sampler0", cmd.texture(), cmd.sampler());
				}
				float[] sc = cmd.scissor();
				if (sc != null) {
					float effectiveScale = guiScale * cmd.uiScale();
					int sx = (int) (sc[0] * effectiveScale);
					int sw = (int) (sc[2] * effectiveScale);
					int sh = (int) (sc[3] * effectiveScale);
					int sy = screenHeight - (int) ((sc[1] + sc[3]) * effectiveScale);
					if (sw <= 0 || sh <= 0) { baseVertex += quads * 4; continue; }
					pass.enableScissor(sx, sy, sw, sh);
				} else {
					pass.disableScissor();
				}
				pass.drawIndexed(quads * 6, 1, 0, baseVertex, 0);
				baseVertex += quads * 4;
			}
		} finally {
			RenderSystem.restoreProjectionMatrix();
		}
	}

	private static GpuBufferSlice projectionSlice(float width, float height) {
		GpuDevice device = RenderSystem.getDevice();
		if (projectionBuffer == null) {
			projectionBuffer = device.createBuffer(() -> "lt5lyrics projection",
				GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, RenderSystem.PROJECTION_MATRIX_UBO_SIZE);
			projectionWidth = -1.0F;
		}
		if (width != projectionWidth || height != projectionHeight) {
			projectionWidth = width;
			projectionHeight = height;
			Matrix4f ortho = new Matrix4f().setOrtho(0.0F, width, height, 0.0F, -1000.0F, 1000.0F);
			try (MemoryStack stack = MemoryStack.stackPush()) {
				ByteBuffer data = Std140Builder.onStack(stack, RenderSystem.PROJECTION_MATRIX_UBO_SIZE)
					.putMat4f(ortho).get();
				device.createCommandEncoder().writeToBuffer(projectionBuffer.slice(), data);
			}
		}
		return projectionBuffer.slice();
	}

	private static void updateSnapshot(GpuDevice device, CommandEncoder encoder, RenderTarget target) {
		int w = target.width;
		int h = target.height;
		if (snapshot == null || snapshot.getWidth(0) != w || snapshot.getHeight(0) != h) {
			if (snapshotView != null) snapshotView.close();
			if (snapshot != null) snapshot.close();
			snapshot = device.createTexture(() -> "lt5lyrics snapshot",
				GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
				GpuFormat.RGBA8_UNORM, w, h, 1, 1);
			snapshotView = device.createTextureView(snapshot);
		}
		if (snapshotSampler == null) {
			snapshotSampler = device.createSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
				FilterMode.LINEAR, FilterMode.LINEAR, 1, OptionalDouble.empty());
		}
		encoder.copyTextureToTexture(target.getColorTexture(), snapshot, 0, 0, 0, 0, 0, w, h);
	}

	private static void ensureVertexBuffer(GpuDevice device, int quads) {
		if (vertexBuffer != null && vertexBufferQuads >= quads) return;
		if (vertexBuffer != null) vertexBuffer.close();
		vertexBufferQuads = Math.max(256, quads * 2);
		vertexBuffer = device.createBuffer(() -> "lt5lyrics vertices",
			GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
			(long) vertexBufferQuads * 4 * VERTEX_SIZE);
	}

	private static void ensureUniformBuffer(GpuDevice device, int slots) {
		if (uniformAlign == 0) {
			uniformAlign = Math.max(device.getDeviceInfo().limits().minUniformOffsetAlignment(), PARAM_FLOATS * 4);
		}
		if (uniformBuffer != null && uniformSlots >= slots) return;
		if (uniformBuffer != null) uniformBuffer.close();
		uniformSlots = Math.max(128, slots * 2);
		uniformBuffer = device.createBuffer(() -> "lt5lyrics params",
			GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
			(long) uniformSlots * uniformAlign);
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("lt5lyrics", path);
	}

	private IslandRender() {
	}
}
