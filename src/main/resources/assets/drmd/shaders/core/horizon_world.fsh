#version 150
in vec4 vertexColor;
in vec3 trueEyeVector;
flat in int authoredOrbital;
uniform float InnerRadius;
uniform float FieldOuterRadius;
uniform vec4 ColorModulator;
uniform vec4 FogColor;
uniform float Ps1FarFogDensity;
uniform float Ps1SkyFogMix;
uniform vec3 Ps1SkyFogColor;
uniform float Ps1FogStart;
uniform float Ps1FogEnd;
out vec4 fragColor;
void main() {
    float horizontal = authoredOrbital == 1 ? length(trueEyeVector) : length(trueEyeVector.xz);

    float nearFade = 1.0 - smoothstep(FieldOuterRadius * 0.55, FieldOuterRadius, horizontal);
    vec4 colour = vertexColor * ColorModulator;
    colour.a *= nearFade;
    if (colour.a < 0.01) discard;
    // Style uses uncompressed source distance; alpha and geometry/depth ownership stay original.
    if (Ps1FarFogDensity > 0.0) {
        float distance = length(trueEyeVector);
        float amount = smoothstep(Ps1FogStart, Ps1FogEnd, distance) * FogColor.a;
        vec3 haze = mix(FogColor.rgb, Ps1SkyFogColor, Ps1SkyFogMix);
        colour.rgb = mix(colour.rgb, haze, amount);
    }
    // Real scene geometry owns every depth except the sky background.
    gl_FragDepth = 0.999999;
    fragColor = colour;
}
