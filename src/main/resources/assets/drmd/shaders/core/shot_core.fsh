#version 150
#moj_import <fog.glsl>

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in vec4 vertexColor;
in vec2 texCoord;
in float vertexDistance;
out vec4 fragColor;

void main() {
    float radius = length(texCoord * 2.0 - 1.0);
    float falloff = 1.0 - smoothstep(0.12, 1.0, radius);
    vec4 color = vertexColor * ColorModulator;
    color.a *= falloff;
    if (color.a < 0.003) discard;
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
