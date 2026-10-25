#version 150
in vec4 vertexColor;
in vec3 trueEyeVector;
uniform float InnerRadius;
uniform vec4 ColorModulator;
out vec4 fragColor;
void main() {
    float horizontal = length(trueEyeVector.xz);
    if (horizontal < InnerRadius) discard;
    float nearFade = InnerRadius > 1.0 ? smoothstep(InnerRadius, InnerRadius * 1.2, horizontal) : 1.0;
    vec4 colour = vertexColor * ColorModulator;
    colour.a *= nearFade;
    if (colour.a < 0.01) discard;
    fragColor = colour;
}
