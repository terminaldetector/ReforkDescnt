#version 150
in vec4 vertexColor;
in vec3 trueEyeVector;
uniform float InnerRadius;
uniform vec4 ColorModulator;
uniform vec4 FogColor;
uniform float Ps1FarFogDensity;
out vec4 fragColor;
void main() {
    float horizontal = length(trueEyeVector.xz);

    float nearFade = 1.0;
    vec4 colour = vertexColor * ColorModulator;
    colour.a *= nearFade;
    if (colour.a < 0.01) discard;
    // Style uses uncompressed source distance; alpha and geometry/depth ownership stay original.
    if (Ps1FarFogDensity > 0.0) {
        float distance = length(trueEyeVector);
        float into = max(0.0, distance - max(512.0, InnerRadius)) / 2048.0;
        float amount = min(0.85, 1.0 - exp(-Ps1FarFogDensity * into));
        colour.rgb = mix(colour.rgb, FogColor.rgb, amount);
    }
    // Real scene geometry owns every depth except the sky background.
    gl_FragDepth = 0.999999;
    fragColor = colour;
}
