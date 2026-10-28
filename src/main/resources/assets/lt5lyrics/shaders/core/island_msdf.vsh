#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
#include <lt5lyrics:island.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec4 Color;

layout(location = 0) out vec2 FragCoord;
layout(location = 1) out vec2 TexCoord;
layout(location = 2) out vec4 FragColor;
layout(location = 3) out vec2 GlobalPos;

const vec2[4] RECT_COORDS = vec2[](
    vec2(0.0, 0.0),
    vec2(0.0, 1.0),
    vec2(1.0, 1.0),
    vec2(1.0, 0.0)
);

void main() {
    FragCoord = RECT_COORDS[gl_VertexIndex % 4];
    TexCoord = UV0;
    FragColor = Color;
    GlobalPos = Position.xy;
    vec4 clipPos = ProjMat * ModelViewMat * vec4(Position, 1.0);
    // Sky glyphs only: retain real xy perspective but keep distant glyphs just
    // inside the far plane. Their reversed depth is nearly 0 so loaded terrain can still
    // occlude them. HUD and ordinary world text use Extra.z = 0.
    if (Extra.z > 0.5 && clipPos.w > 0.0 && clipPos.z < clipPos.w * 0.000001) {
        clipPos.z = clipPos.w * 0.000001;
    }
    gl_Position = clipPos;
}
