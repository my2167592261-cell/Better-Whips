#version 150

in vec2 texCoord0;
in vec4 vertexColor;

uniform vec4 ColorModulator;

out vec4 fragColor;

void main() {
    vec2 p = texCoord0 * 2.0 - 1.0;

    float diamondDistance = abs(p.x) + abs(p.y);
    float tightGlow = exp(-4.8 * diamondDistance * diamondDistance);
    float softGlow = exp(-2.55 * diamondDistance * diamondDistance);

    float edgeDistance = max(abs(p.x), abs(p.y));
    float edgeFade = 1.0 - smoothstep(0.76, 1.0, edgeDistance);

    float intensity = (tightGlow * 0.78 + softGlow * 0.30) * edgeFade;
    float alpha = vertexColor.a * intensity;
    if (alpha < 0.003) {
        discard;
    }

    vec3 glowColor = vertexColor.rgb * (1.02 + tightGlow * 0.62);
    fragColor = vec4(glowColor, alpha) * ColorModulator;
}
