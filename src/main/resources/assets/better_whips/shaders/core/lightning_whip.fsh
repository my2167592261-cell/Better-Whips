#version 150
uniform mat4 ProjMat;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 ColorModulator;
in vec3 viewPosition;
in vec3 viewCenter;
in vec3 viewTangent;
in vec3 viewNormal;
in float arcRadius;
in float arcEnergy;
flat in float glowLayer;
out vec4 fragColor;

void main() {
    vec3 ray=ProjMat[3][3]>.5 ? vec3(0.0,0.0,-1.0) : normalize(viewPosition);
    vec3 tangent=normalize(viewTangent);
    vec3 radial=viewPosition-viewCenter;
    vec3 side=cross(ray,tangent);
    float crossLength=length(side);
    float distanceToAxis=crossLength>.02 ? abs(dot(radial,side/crossLength))
        : length(radial-ray*dot(radial,ray));
    vec3 emission;
    if(glowLayer>.5) {
        float r=distanceToAxis/max(.00001,arcRadius);
        float haze=exp(-r*r*22.0)*.55+exp(-r*5.0)*.17;
        haze*=1.0-smoothstep(.70,1.0,r);
        emission=vec3(.035,.25,1.0)*haze;
    } else {
        float facing=abs(dot(normalize(viewNormal),-ray));
        emission=mix(vec3(.22,.66,1.0),vec3(.76,.96,1.0),.45+.55*facing)*.58;
    }
    float distance=length(viewPosition);
    float fog=FogEnd>FogStart ? 1.0-smoothstep(FogStart,FogEnd,distance) : 1.0;
    fragColor=vec4(emission*arcEnergy*fog*ColorModulator.rgb,ColorModulator.a);
    if(max(max(fragColor.r,fragColor.g),fragColor.b)<.002) discard;
}
