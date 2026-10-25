#version 150

in vec3 Position;
in vec4 Color;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 FieldOriginDelta;
uniform float InnerRadius;
uniform float FieldReach;
out vec4 vertexColor;
out vec3 trueEyeVector;

void main() {
    // Rebase in true world units BEFORE the nonlinear distance compression.
    vec3 eyeVector = Position + FieldOriginDelta;
    float distance = length(eyeVector);
    float drawn = distance;
    if (distance > InnerRadius) {
        float span = max(1.0, FieldReach - InnerRadius);
        drawn = InnerRadius + span * (1.0 - exp(-(distance - InnerRadius) / span));
    }
    vec3 projected = distance < 0.0001 ? eyeVector : eyeVector * (drawn / distance);
    gl_Position = ProjMat * ModelViewMat * vec4(projected, 1.0);
    vertexColor = Color;
    trueEyeVector = eyeVector;
}
