#version 150
uniform sampler2D Sampler0;
uniform sampler2D FarSurface;
uniform mat4 OriginalProjection;
uniform mat4 WorldFromView;
uniform vec4 ColorModulator;
uniform vec4 FogColor;
uniform float FogStart;
uniform float FogEnd;
uniform float AltitudeWeight;
uniform float CloudRange;
uniform float DepthCeiling;
uniform int StockCloudMode;
in vec3 trueViewPosition;
in vec4 vertexColor;
in vec2 texCoord0;
in float stockFogDistance;
out vec4 fragColor;
void main() {
    vec4 colour = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    if (colour.a < 0.1) discard;
    float distance = length(trueViewPosition);
    vec2 surface = texelFetch(FarSurface, ivec2(gl_FragCoord.xy), 0).rg;
    if (surface.y >= 0.01 && surface.x > 0.0 && surface.x + 0.05 < distance) discard;
    vec3 world = (WorldFromView * vec4(trueViewPosition, 1.0)).xyz;
    float horizontalFade = 1.0 - smoothstep(CloudRange * 0.7, CloudRange, length(world.xz));
    float radialFade = 1.0 - smoothstep(CloudRange * 0.85, CloudRange, distance);
    vec4 stock;
    vec4 extended;
    if (StockCloudMode == 1) {
        float end = FogStart + max(0.0001, (FogEnd - FogStart) * 4.0);
        float fade = clamp((end - stockFogDistance) / max(0.0001, end - FogStart), 0.0, 1.0) * FogColor.a;
        stock = vec4(mix(FogColor.rgb, colour.rgb, 0.7), colour.a * fade);
        extended = vec4(stock.rgb, colour.a * horizontalFade * radialFade * FogColor.a);
    } else {
        float fog = smoothstep(FogStart, max(FogStart + 0.0001, FogEnd), stockFogDistance) * FogColor.a;
        stock = vec4(mix(colour.rgb, FogColor.rgb, fog), colour.a);
        extended = vec4(mix(colour.rgb, FogColor.rgb, (1.0 - horizontalFade) * FogColor.a), colour.a * radialFade);
    }
    fragColor = mix(stock, extended, AltitudeWeight);
    if (fragColor.a < 0.01) discard;
    vec4 legacy = OriginalProjection * vec4(trueViewPosition, 1.0);
    gl_FragDepth = clamp(legacy.z / legacy.w * 0.5 + 0.5, 0.0, DepthCeiling);
}
