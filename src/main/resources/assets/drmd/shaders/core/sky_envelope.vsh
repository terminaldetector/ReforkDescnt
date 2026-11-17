#version 150

in vec3 Position;
in vec4 Color;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
out vec4 vertexColor;

void main() {
    vec4 clip = ProjMat * ModelViewMat * vec4(Position, 1.0);
    // A sky direction has no terrain distance. Keep this enclosing cube inside the far clip
    // for every 6DoF attitude, at clear-background depth so real geometry stays in front.
    gl_Position = vec4(clip.xy, clip.w, clip.w);
    vertexColor = Color;
}
