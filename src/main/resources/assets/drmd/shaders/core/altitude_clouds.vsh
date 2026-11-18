#version 150
in vec3 Position;
in vec4 Color;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;
uniform int StockCloudMode;
out vec3 trueViewPosition;
out vec4 vertexColor;
out float stockFogDistance;
void main() {
    vec4 view = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * view;
    trueViewPosition = view.xyz;
    vertexColor = Color;
    vec3 fogPosition = StockCloudMode == 1
        ? (ModelViewMat * vec4(Position.x, 0.0, Position.z, 1.0)).xyz : view.xyz;
    stockFogDistance = FogShape == 1 ? max(length(fogPosition.xz), abs(fogPosition.y)) : length(fogPosition);
}
