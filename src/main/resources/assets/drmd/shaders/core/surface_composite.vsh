#version 150
in vec3 Position;
uniform mat4 SurfaceMatrix;
void main() { gl_Position = SurfaceMatrix * vec4(Position, 1.0); }
