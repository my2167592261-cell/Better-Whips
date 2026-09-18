#version 150
in vec3 Position;
in vec2 UV0;
in vec4 Color;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
out vec2 waterUV;
out vec4 vertexColor;
out vec3 viewPosition;
void main() {
    vec4 view = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * view;
    waterUV = UV0;
    vertexColor = Color;
    viewPosition = view.xyz;
}
