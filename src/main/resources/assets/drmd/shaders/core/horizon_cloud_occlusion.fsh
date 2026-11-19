#version 150
uniform mat4 InverseProjection;
uniform vec2 CloudTargetSize;
uniform float MaxRayDistance;
uniform vec4 ColorModulator;
in vec3 trueViewPosition;
in float sourceOpacity;
in vec3 trueEyeVector;
flat in int authoredOrbital;
uniform float FieldOuterRadius;
out vec4 fragColor;
void main() {
    float opacity = sourceOpacity * ColorModulator.a * (1.0 - smoothstep(FieldOuterRadius * 0.55, FieldOuterRadius, (authoredOrbital == 1 ? length(trueEyeVector) : length(trueEyeVector.xz))));
    if (opacity < 0.01) discard;
    vec3 normal = cross(dFdx(trueViewPosition), dFdy(trueViewPosition));
    float normalLength = length(normal);
    if (normalLength < 0.000000000001 || isnan(normalLength) || isinf(normalLength)) discard;
    normal /= normalLength;
    vec2 ndc = gl_FragCoord.xy / CloudTargetSize * 2.0 - 1.0;
    vec4 point = InverseProjection * vec4(ndc, 1.0, 1.0);
    vec3 ray = normalize(point.xyz / point.w);
    float denominator = dot(normal, ray);
    if (abs(denominator) < 0.0000001) discard;
    float distance = dot(normal, trueViewPosition) / denominator;
    if (distance <= 0.0 || distance >= MaxRayDistance || isnan(distance) || isinf(distance)) discard;
    gl_FragDepth = distance / MaxRayDistance;
    fragColor = vec4(distance, opacity, 0.0, 1.0);
}
