#version 150
in vec3 Position;
in vec4 Color;
in vec2 UV0;
uniform vec4 OrbitalOpacity[108];
flat out int authoredOrbital;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 FieldOriginDelta;
uniform float InnerRadius;
uniform float FieldReach;
out vec3 trueViewPosition;
out float sourceOpacity;
// Correct the smooth varying for the radial change to clip W, then undo it per fragment.
out vec3 trueEyeVector;
out float sourceScale;
void main() {
    authoredOrbital = UV0.x >= 0.0 ? 1 : 0;
    int slot = int(UV0.x + 0.5);
    float handoff = authoredOrbital == 0 ? 1.0 : OrbitalOpacity[slot / 4][slot % 4];
    vec3 eye = Position + FieldOriginDelta;
    float distance = length(eye);
    float drawn = distance;
    if (distance > InnerRadius) {
        float span = max(1.0, FieldReach - InnerRadius);
        drawn = InnerRadius + span * (1.0 - exp(-(distance - InnerRadius) / span));
    }
    sourceScale = distance < 0.0001 ? 1.0 : drawn / distance;
    vec3 projected = eye * sourceScale;
    gl_Position = ProjMat * ModelViewMat * vec4(projected, 1.0);
    trueViewPosition = (ModelViewMat * vec4(eye, 1.0)).xyz;
    sourceOpacity = Color.a * handoff;
    trueEyeVector = eye * sourceScale;
}
