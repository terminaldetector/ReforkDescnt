#version 150

#moj_import <light.glsl>
#moj_import <fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;

uniform vec3 Light0_Direction;
uniform vec3 Light1_Direction;

out float vertexDistance;
out vec4 vertexColor;
out vec4 lightMapColor;
out vec4 overlayColor;
out vec2 texCoord0;
noperspective out vec2 affineTexCoord0;
uniform vec4 GeometryViewport;
uniform float VertexSnapPixels;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    // Only XY changes: original clip Z/W, lighting, fog and authoritative geometry remain untouched.
    if (VertexSnapPixels > 0.0 && gl_Position.w > 0.0001 && all(greaterThan(GeometryViewport.zw, vec2(0.0)))) {
        vec2 pixel = (gl_Position.xy / gl_Position.w * 0.5 + 0.5) * GeometryViewport.zw + GeometryViewport.xy;
        vec2 grid = floor(pixel / VertexSnapPixels + 0.5) * VertexSnapPixels;
        gl_Position.xy = ((grid - GeometryViewport.xy) / GeometryViewport.zw * 2.0 - 1.0) * gl_Position.w;
    }

    vertexDistance = fog_distance(Position, FogShape);
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, Normal, Color);
    lightMapColor = texelFetch(Sampler2, UV2 / 16, 0);
    overlayColor = texelFetch(Sampler1, UV1, 0);
    texCoord0 = UV0;
    affineTexCoord0 = UV0;
}
