#version 150
uniform sampler2D Sampler0;
uniform vec2 SurfaceSize;
out vec4 fragColor;
void main() { fragColor = vec4(texture(Sampler0, gl_FragCoord.xy / SurfaceSize).rgb, 1.0); }
