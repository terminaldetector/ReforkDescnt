#version 150
in vec3 Position;
in vec4 Color;
in vec2 UV0;
uniform vec4 OrbitalOpacity[108];
uniform vec4 OrbitalPixelShift[8];
flat out int authoredOrbital;
flat out vec2 orbitalNdcShift;
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
    // One translation for every vertex of the object: shape, winding, clip z/w and source stay exact.
    vec2 pixelShift = vec2(0.0);
    int object = int(UV0.y + 0.5);
    if (authoredOrbital == 1 && UV0.y >= 0.0 && object < 16) {
        vec4 shiftPair = OrbitalPixelShift[object / 2];
        pixelShift = object % 2 == 0 ? shiftPair.xy : shiftPair.zw;
    }
    gl_Position.xy += pixelShift * gl_Position.w;
    orbitalNdcShift = pixelShift;
    trueViewPosition = (ModelViewMat * vec4(eye, 1.0)).xyz;
    sourceOpacity = Color.a * handoff;
    trueEyeVector = eye * sourceScale;
}
