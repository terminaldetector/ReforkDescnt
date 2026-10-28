#version 150
in vec4 vertexColor;
in vec3 trueEyeVector;
uniform float InnerRadius;
uniform vec4 ColorModulator;
out vec4 fragColor;
void main() {
    float horizontal = length(trueEyeVector.xz);

    float nearFade = 1.0;
    vec4 colour = vertexColor * ColorModulator;
    colour.a *= nearFade;
    if (colour.a < 0.01) discard;
    // Real scene geometry owns every depth except the sky background.
    gl_FragDepth = 0.999999;
    fragColor = colour;
}
