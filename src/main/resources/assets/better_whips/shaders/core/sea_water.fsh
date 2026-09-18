#version 150
uniform float GameTime;
uniform float GlowPass;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 ColorModulator;
in vec2 waterUV;
in vec4 vertexColor;
in vec3 viewPosition;
out vec4 fragColor;

float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1,311.7))) * 43758.5453); }
float noise(vec2 p) {
    vec2 cell=floor(p), f=fract(p); f=f*f*(3.0-2.0*f);
    return mix(mix(hash(cell),hash(cell+vec2(1,0)),f.x),mix(hash(cell+vec2(0,1)),hash(cell+vec2(1,1)),f.x),f.y);
}
void main() {
    float time=GameTime*1200.0;
    vec2 uv=waterUV;
    float flow=noise(vec2(uv.x*3.6-time*2.9,uv.y*7.0+time*.4));
    float veins=sin(uv.x*13.0-time*12.0+sin(uv.y*7.0+time*3.0)*1.4+flow*7.0);
    float caustic=1.0-smoothstep(.06,.18+fwidth(veins)*1.2,abs(veins));
    float edge=pow(abs(uv.y*2.0-1.0),7.0);
    vec3 surface=cross(dFdx(viewPosition),dFdy(viewPosition));
    vec3 normal=surface*inversesqrt(max(dot(surface,surface),1e-12));
    float fresnel=pow(1.0-abs(dot(normal,normalize(-viewPosition+vec3(0,0,.0001)))),2.3);
    vec3 rainbow=.55+.45*cos(vec3(0.0,2.1,4.2)+uv.x*2.1-time*2.4+vertexColor.r*4.0);
    vec3 water=mix(vec3(.015,.32,.46),vec3(.12,.87,.89),.25+flow*.55);
    water=mix(water,rainbow*.85+vec3(.02,.08,.12),(.08+.36*fresnel)*edge);
    vec3 foam=mix(vec3(.30,.87,1.0),vec3(.91,1.0,.98),caustic);
    float glint=(caustic*.62+edge*.48+fresnel*.14);
    float alpha=vertexColor.a*(.43+flow*.22+edge*.26);
    vec3 color=mix(water,foam,clamp(caustic*.46+edge*.55,0.0,1.0));
    if(GlowPass>.5) { color=mix(foam,rainbow+vec3(.16,.20,.24),edge*.45);alpha=vertexColor.a*glint*.53; }
    float fog=1.0-smoothstep(FogStart,max(FogStart+.001,FogEnd),length(viewPosition));
    alpha*=fog*ColorModulator.a;
    if(alpha<.003)discard;
    fragColor=vec4(color*ColorModulator.rgb,alpha);
}
