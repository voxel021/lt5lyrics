#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <lt5lyrics:island.glsl>

layout(location = 0) in vec2 FragCoord;
layout(location = 1) in vec2 TexCoord;
layout(location = 2) in vec4 FragColor;
layout(location = 3) in vec2 GlobalPos;

layout(location = 0) out vec4 fragColor;

void main() {
    vec2 size = SizeSmooth.xy;
    float smoothness = SizeSmooth.z;
    float corner = SizeSmooth.w;
    vec2 center = size * 0.5;
    vec2 p = center - (FragCoord * size);
    float dist = corner > 1.001
        ? islandSdfSquircle(p, center - 1.0, Radius, corner)
        : islandSdf(p, center - 1.0, Radius);
    float alpha = 1.0 - smoothstep(1.0 - smoothness, 1.0, dist);
    vec4 color = vec4(FragColor.rgb, FragColor.a * alpha);
    if (color.a == 0.0) {
        discard;
    }
    fragColor = color * ColorModulator;
}
