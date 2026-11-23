#version 150
in vec4 vertexColor;
in vec3 trueEyeVector;
in float sourceScale;
flat in int authoredOrbital;
uniform float InnerRadius;
uniform float FieldOuterRadius;
uniform int VolumeHorizon;
uniform vec4 ColorModulator;
uniform vec4 FogColor;
uniform float Ps1FarFogDensity;
uniform float Ps1SkyFogMix;
uniform vec3 Ps1SkyFogColor;
uniform float Ps1FogStart;
uniform float Ps1FogEnd;
out vec4 fragColor;
void main() {
    // Certified station/asteroid solids are opaque closed boxes. Ground and legacy
    // skirts retain their existing two-sided policy; native foreground depth stays authoritative.
    bool volume = VolumeHorizon != 0;
    if ((authoredOrbital == 1 || volume) && !gl_FrontFacing) discard;
    // Source weights use the original perspective, not the compressed per-vertex clip W.
    vec3 sourceEyeVector = trueEyeVector / max(sourceScale, 0.0000001);
    float distance = length(sourceEyeVector);
    if (volume && distance < InnerRadius) discard;
    float extent = (authoredOrbital == 1 || volume) ? distance : length(sourceEyeVector.xz);

    float nearFade = 1.0 - smoothstep(FieldOuterRadius * 0.55, FieldOuterRadius, extent);
    if (volume) nearFade *= smoothstep(InnerRadius, InnerRadius + 64.0, distance);
    vec4 colour = vertexColor * ColorModulator;
    colour.a *= nearFade;
    if (colour.a < 0.01) discard;
    // Style uses uncompressed source distance; alpha and geometry/depth ownership stay original.
    if (Ps1FarFogDensity > 0.0) {
        float amount = smoothstep(Ps1FogStart, Ps1FogEnd, distance) * FogColor.a;
        vec3 haze = mix(FogColor.rgb, Ps1SkyFogColor, Ps1SkyFogMix);
        colour.rgb = mix(colour.rgb, haze, amount);
    }
    // Volumes retain relative depth past the near-world cutoff. Flat legacy ground stays in the background.
    gl_FragDepth = volume ? gl_FragCoord.z : 0.999999;
    fragColor = colour;
}
