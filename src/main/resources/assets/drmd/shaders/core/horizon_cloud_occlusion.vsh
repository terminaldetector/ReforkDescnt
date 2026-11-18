#version 150
in vec3 Position;
in vec4 Color;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 FieldOriginDelta;
uniform float InnerRadius;
uniform float FieldReach;
out vec3 trueViewPosition;
out float sourceOpacity;
void main() {
    vec3 eye = Position + FieldOriginDelta;
    float distance = length(eye);
    float drawn = distance;
    if (distance > InnerRadius) {
        float span = max(1.0, FieldReach - InnerRadius);
        drawn = InnerRadius + span * (1.0 - exp(-(distance - InnerRadius) / span));
    }
    vec3 projected = distance < 0.0001 ? eye : eye * (drawn / distance);
    gl_Position = ProjMat * ModelViewMat * vec4(projected, 1.0);
    trueViewPosition = (ModelViewMat * vec4(eye, 1.0)).xyz;
    sourceOpacity = Color.a;
}
