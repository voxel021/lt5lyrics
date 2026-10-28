#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <lt5lyrics:island.glsl>

uniform sampler2D Sampler0;

layout(location = 0) in vec2 FragCoord;
layout(location = 1) in vec2 TexCoord;
layout(location = 2) in vec4 FragColor;
layout(location = 3) in vec2 GlobalPos;

layout(location = 0) out vec4 fragColor;

float median(vec3 color) {
    return max(min(color.r, color.g), min(max(color.r, color.g), color.b));
}

void main() {
    float range = SizeSmooth.x;
    float thickness = SizeSmooth.y;
    float smoothness = SizeSmooth.z;

    float dist = median(texture(Sampler0, TexCoord).rgb) - 0.5 + thickness;
    vec2 unit = vec2(range) / vec2(textureSize(Sampler0, 0));
    vec2 screen = 1.0 / max(fwidth(TexCoord), vec2(0.000001));
    float pixels = max(0.5 * dot(unit, screen), 1.0);

    float alpha = smoothstep(-smoothness, smoothness, dist * pixels);
    vec4 color = vec4(FragColor.rgb, FragColor.a * alpha);

    if(Extra.w > 0.5){
        float windowStart = Radius.x;
        float windowEnd = Radius.y;
        float fadeRight = Radius.z;
        float fadeLeft = Radius.w;
        float left = fadeLeft > 0.01
            ? smoothstep(windowStart, windowStart + fadeLeft, GlobalPos.x)
            : step(windowStart, GlobalPos.x);
        float right = fadeRight > 0.01
            ? 1.0 - smoothstep(windowEnd - fadeRight, windowEnd, GlobalPos.x)
            : step(GlobalPos.x, windowEnd);
        color.a *= min(left, right);
    }

    if(color.a <= 0.001){
        discard;
    }
    fragColor = color * ColorModulator;
}
