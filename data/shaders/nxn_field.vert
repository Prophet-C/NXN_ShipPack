#version 330 compatibility

out vec2 texCoord;

void main() {
    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
    texCoord = gl_MultiTexCoord0.xy;
}
