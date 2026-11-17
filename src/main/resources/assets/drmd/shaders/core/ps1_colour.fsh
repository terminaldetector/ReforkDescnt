#version 150

uniform sampler2D Sampler0;
uniform vec2 SceneSize;
uniform vec2 OutputSize;
uniform float ColorLevels;
uniform float DitherStrength;
out vec4 fragColor;

const int bayer[16] = int[16](0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5);

void main() {
    ivec2 size = ivec2(SceneSize);
    ivec2 source = clamp(ivec2(gl_FragCoord.xy * SceneSize / OutputSize), ivec2(0), size - ivec2(1));
    vec4 colour = texelFetch(Sampler0, source, 0);
    float threshold = DitherStrength * ((float(bayer[(source.y & 3) * 4 + (source.x & 3)]) + 0.5) / 16.0 - 0.5);
    float steps = ColorLevels - 1.0;
    vec3 quantized = clamp(floor(colour.rgb * steps + 0.5 + threshold), vec3(0.0), vec3(steps)) / steps;
    fragColor = vec4(quantized, colour.a);
}
