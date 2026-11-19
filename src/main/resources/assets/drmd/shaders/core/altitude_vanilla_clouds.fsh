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
uniform int HasFarSurface;
uniform float Ps1FarFogDensity;
uniform float Ps1SkyFogMix;
uniform vec3 Ps1SkyFogColor;
uniform float Ps1FogStart;
uniform float Ps1FogEnd;
uniform float CloudStockFogStart;
uniform float CloudStockFogEnd;
uniform vec4 CloudStockFogColor;
void main() {
    bool styled = Ps1FarFogDensity > 0.0;
    float stockStart = styled ? CloudStockFogStart : FogStart;
    float stockEnd = styled ? CloudStockFogEnd : FogEnd;
    vec4 stockColour = styled ? CloudStockFogColor : FogColor;

    vec4 colour = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    if (colour.a < 0.1) discard;
    float distance = length(trueViewPosition);
    if (HasFarSurface != 0) {
        vec2 surface = texelFetch(FarSurface, ivec2(gl_FragCoord.xy), 0).rg;
        if (surface.y >= 0.01 && surface.x > 0.0 && surface.x + 0.05 < distance) discard;
    }
    vec3 world = (WorldFromView * vec4(trueViewPosition, 1.0)).xyz;
    float horizontalFade = 1.0 - smoothstep(CloudRange * 0.7, CloudRange, length(world.xz));
    float radialFade = 1.0 - smoothstep(CloudRange * 0.85, CloudRange, distance);
    vec4 stock;
    vec4 extended;
    if (StockCloudMode == 1) {
        float end = stockStart + max(0.0001, (stockEnd - stockStart) * 4.0);
        float fade = clamp((end - stockFogDistance) / max(0.0001, end - stockStart), 0.0, 1.0) * stockColour.a;
        stock = vec4(mix(stockColour.rgb, colour.rgb, 0.7), colour.a * fade);
        extended = vec4(stock.rgb, colour.a * horizontalFade * radialFade * stockColour.a);
    } else {
        float fog = smoothstep(stockStart, max(stockStart + 0.0001, stockEnd), stockFogDistance) * stockColour.a;
        stock = vec4(mix(colour.rgb, stockColour.rgb, fog), colour.a);
        extended = vec4(mix(colour.rgb, stockColour.rgb, (1.0 - horizontalFade) * stockColour.a), colour.a * radialFade);
    }
    fragColor = mix(stock, extended, AltitudeWeight);
    if (fragColor.a < 0.01) discard;
    if (styled) {
        vec3 base = StockCloudMode == 1 ? mix(stockColour.rgb, colour.rgb, 0.7) : colour.rgb;
        vec3 haze = mix(stockColour.rgb, Ps1SkyFogColor, Ps1SkyFogMix);
        fragColor.rgb = mix(base, haze, smoothstep(Ps1FogStart, Ps1FogEnd, distance) * stockColour.a);
    }
    vec4 legacy = OriginalProjection * vec4(trueViewPosition, 1.0);
    gl_FragDepth = clamp(legacy.z / legacy.w * 0.5 + 0.5, 0.0, DepthCeiling);
}
