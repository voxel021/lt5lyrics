package com.lt5lyrics.client.island.render;

import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import java.util.Optional;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public final class IslandPipelines {
	public static final RenderPipeline SHAPE =
			build("shape", "island_shape", false);

	public static final RenderPipeline WORLD_SHAPE =
			build("world_shape", "island_shape", false, true);

	public static final RenderPipeline WORLD_SHAPE_SEE_THROUGH =
			build("world_shape_see_through", "island_shape", false, false);

	// Dedicated to the Normal 3D curved backdrop; existing HUD/Sky pipelines are unchanged.
	public static final RenderPipeline NORMAL_CURVED_PLATE =
			build("normal_curved_plate", "island_normal_plate", false, true);
	public static final RenderPipeline NORMAL_CURVED_PLATE_SEE_THROUGH =
			build("normal_curved_plate_see_through", "island_normal_plate", false, false);

	public static final RenderPipeline WHEEL =
			build("wheel", "island_wheel", false);

	public static final RenderPipeline TEXTURE =
			build("texture", "island_texture", true);

	public static final RenderPipeline BLUR =
			build("blur", "island_blur", true);

	public static final RenderPipeline MSDF =
			build("msdf", "island_msdf", true);

	public static final RenderPipeline WORLD_MSDF =
			build("world_msdf", "island_msdf", true, true);

	public static final RenderPipeline SKY_MSDF =
			build("sky_msdf", "island_msdf", true, false);

	// Normal-only glyph pipelines; Sky and HUD retain their separate shader.
	public static final RenderPipeline NORMAL_MSDF =
			build("normal_msdf", "island_normal_msdf", true, true);
	public static final RenderPipeline NORMAL_MSDF_SEE_THROUGH =
			build("normal_msdf_see_through", "island_normal_msdf", true, false);

	private static RenderPipeline build(
			String name, String shader, boolean sampler) {
		return build(name, shader, sampler, false);
	}

	private static RenderPipeline build(
			String name, String shader, boolean sampler, boolean depth) {

		BindGroupLayout.Builder bindings = BindGroupLayout.builder()
				.withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
				.withUniform("Projection", UniformType.UNIFORM_BUFFER)
				.withUniform("IslandParams", UniformType.UNIFORM_BUFFER);
		if (sampler) bindings.withUniform("Sampler0", UniformType.COMBINED_IMAGE_SAMPLER);
		RenderPipeline.Builder builder = RenderPipeline.builder()
				.withLocation(id(name))
				.withVertexShader(id("core/" + shader))
				.withFragmentShader(id("core/" + shader))
				.withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
				.withPrimitiveTopology(PrimitiveTopology.QUADS)
				.withBindGroupLayout(bindings.build())
				.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
				.withDepthStencilState(depth
						? Optional.of(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
						: Optional.empty())
				.withCull(false);

		return RenderPipelines.register(builder.build());
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("lt5lyrics", path);
	}

	// Force registration before resource reload builds the 26.3 pipeline cache.
	public static void initialize() {}

	private IslandPipelines() {
	}
}
