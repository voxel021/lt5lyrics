#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <lt5lyrics:island.glsl>

layout(location = 0) in vec2 FragCoord;
layout(location = 1) in vec2 TexCoord;
layout(location = 2) in vec4 FragColor;
layout(location = 3) in vec2 GlobalPos;

layout(location = 0) out vec4 fragColor;

const float TWO_PI = 6.28318530718;

vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

void main() {
    float diameter = max(SizeSmooth.x, 1.0);
    float brightness = clamp(Extra.x, 0.0, 1.0);
    vec2 p = FragCoord * 2.0 - 1.0;
    float r = length(p);
    float aa = max(2.2 / diameter, 0.015);
    float alpha = 1.0 - smoothstep(1.0 - aa, 1.0, r);
    if (alpha <= 0.001) {
        discard;
    }

    float hue = fract(atan(p.y, p.x) / TWO_PI + 1.0);
    float sat = clamp(r, 0.0, 1.0);
    vec3 rgb = hsv2rgb(vec3(hue, sat, brightness));

    float rim = smoothstep(1.0 - aa * 2.2, 1.0 - aa * 0.6, r);
    rgb = mix(rgb, vec3(1.0), rim * 0.22);

    vec4 color = vec4(rgb, FragColor.a * alpha);
    fragColor = color * ColorModulator;
}
