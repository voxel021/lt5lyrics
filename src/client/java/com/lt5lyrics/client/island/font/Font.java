package com.lt5lyrics.client.island.font;

import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.lt5lyrics.client.island.IslandSettings;
import java.util.List;

public class Font {
	private final MsdfFont msdfFont;
	private final float size;
	private final IslandSettings.FontPreset presetOverride;
	private final String customFontOverride;
	private final boolean followUserSelection;

	public Font(MsdfFont msdfFont, float size) {
		this(msdfFont, size, null, null, false);
	}

	public Font(
		MsdfFont msdfFont,
		float size,
		IslandSettings.FontPreset presetOverride,
		String customFontOverride,
		boolean followUserSelection
	) {
		this.msdfFont = msdfFont;
		this.size = size;
		this.presetOverride = presetOverride;
		this.customFontOverride = customFontOverride;
		this.followUserSelection = followUserSelection;
	}

	public float height() {
		return this.size * 0.7F;
	}

	public IslandSettings.FontPreset effectivePreset() {
		if (presetOverride != null) return presetOverride;
		if (followUserSelection) return IslandSettings.fontPreset;
		return IslandSettings.FontPreset.INTER_MODERN;
	}

	public String effectiveCustomFont() {
		if (customFontOverride != null) return customFontOverride;
		if (followUserSelection) return IslandSettings.customFontName;
		return "";
	}

	public boolean usesDynamicAtlas(String text) {
		String custom = effectiveCustomFont();
		if (custom != null && !custom.isBlank()) {
			return true;
		}
		IslandSettings.FontPreset preset = effectivePreset();
		if (preset != IslandSettings.FontPreset.INTER_MODERN) {
			return true;
		}
		if (this.msdfFont == null) {
			return true;
		}
		return UnicodeClusterer.containsExtendedUnicode(text);
	}

	public float width(String text) {
		if (text == null || text.isEmpty()) return 0.0F;
		if (usesDynamicAtlas(text)) {
			return DynamicSdfFont.measureWidth(text, this.size, effectivePreset(), effectiveCustomFont());
		}
		return this.msdfFont.getWidth(text, this.size);
	}

	public List<float[]> buildGlyphs(String text, float x, float baselineY, List<Integer> charIndices) {
		if (text == null || text.isEmpty()) return List.of();
		if (usesDynamicAtlas(text)) {
			return DynamicSdfFont.buildGlyphs(
				text,
				this.size,
				x,
				baselineY,
				effectivePreset(),
				effectiveCustomFont(),
				charIndices
			);
		}
		return this.msdfFont.buildGlyphs(text, this.size, 0.05F * 0.5F * this.size, x, baselineY, charIndices);
	}

	public GpuTextureView textureView(String text) {
		if (usesDynamicAtlas(text)) {
			return DynamicSdfFont.textureView();
		}
		return this.msdfFont.getTexture().getTextureView();
	}

	public GpuSampler sampler(String text) {
		if (usesDynamicAtlas(text)) {
			return DynamicSdfFont.linearSampler();
		}
		return this.msdfFont.getTexture().getSampler();
	}

	public float sdfRange(String text) {
		if (usesDynamicAtlas(text)) {
			return DynamicSdfFont.SDF_RANGE;
		}
		return this.msdfFont.getAtlas().range();
	}

	public MsdfFont getFont() {
		return this.msdfFont;
	}

	public float getSize() {
		return this.size;
	}
}
