#version 150
in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float GameTime;
out vec3 viewPosition;
out vec3 viewCenter;
out vec3 viewTangent;
out vec3 viewNormal;
out float arcRadius;
out float arcEnergy;
flat out float glowLayer;

float seed;
float strand;
float mode;
float clock;
vec2 interval;
float hash(float p) { return fract(sin(p*127.1+seed*3.17)*43758.5453); }
float jag(float x,float key) {
    float i=floor(x);
    return mix(hash(i+key*97.0),hash(i+1.0+key*97.0),fract(x))*2.0-1.0;
}
float field(float x,float offset) {
    float key=floor(clock), blend=smoothstep(0.0,.35,fract(clock));
    return mix(jag(x,key+offset),jag(x,key+1.0+offset),blend);
}
vec2 displacement(float u) {
    float root=smoothstep(0.0,min(.32,max(.04,interval.y*.25)),u);
    if(mode>0.5) {
        root*=smoothstep(0.0,min(.32,max(.04,interval.y*.25)),interval.y-u);
    } else {

        root*=smoothstep(0.0,min(.24,max(.07,interval.y*.028)),interval.y-u);
    }
    vec2 main=vec2(field(u*6.2,0.0),field(u*6.7,31.0))*.100;
    main+=vec2(field(u*11.3,17.0),field(u*10.7,59.0))*.035;
    if(strand>.5 && strand<2.5) {
        main+=vec2(field(u*7.3,81.0+strand*13.0),field(u*8.6,139.0+strand*17.0))*.115;
    } else if(strand>2.5) {
        float t=clamp((u-interval.x)/max(.001,interval.y-interval.x),0.0,1.0);
        float azimuth=hash(strand*41.0)*6.2831853 + field(.1,151.0+strand)*.7;
        vec2 direction=vec2(cos(azimuth),sin(azimuth));
        main+=direction*(.24+.14*hash(strand*9.0))*t;
        main+=vec2(field(u*12.0,121.0+strand),field(u*13.0,201.0+strand))*.065*t;
    }
    return main*root;
}
vec3 octDecode(vec2 p) {
    vec3 n=vec3(p,1.0-abs(p.x)-abs(p.y));
    if(n.z<0.0) n.xy=(1.0-abs(n.yx))*vec2(n.x>=0.0?1.0:-1.0,n.y>=0.0?1.0:-1.0);
    return normalize(n);
}
void main() {
    seed=floor(Color.r*255.0+.5);
    float code=floor(Color.g*255.0+.5);
    mode=floor(code/16.0); strand=mod(code,16.0);
    code=floor(Color.b*255.0+.5);
    glowLayer=floor(code/128.0);
    float scale=exp2((mod(code,128.0)-64.0)/8.0);
    interval=vec2(UV2)*.001;
    clock=GameTime*1200.0*22.0;
    vec3 tangent=normalize(Normal);
    vec3 n=octDecode(vec2(UV1)/32767.0);
    n=normalize(n-tangent*dot(n,tangent));
    vec3 b=normalize(cross(tangent,n));
    float u=UV0.x;
    vec2 offset=displacement(u)*scale;
    vec3 center=Position+n*offset.x+b*offset.y;
    vec2 derivative=(displacement(u+.008)-displacement(u-.008))/.016;
    vec3 electricTangent=normalize(tangent+n*derivative.x+b*derivative.y);
    vec3 electricN=normalize(n-electricTangent*dot(n,electricTangent));
    vec3 electricB=normalize(cross(electricTangent,electricN));
    float progress=clamp((u-interval.x)/max(.001,interval.y-interval.x),0.0,1.0);
    float taper=mode>.5 ? .85 : mix(1.0,.42,progress);
    float width=strand<.5 ? 1.0 : (strand<2.5 ? .43 : .28);
    if(strand>2.5) taper*=1.0-smoothstep(.60,1.0,progress)*.95;
    float radius=(glowLayer>.5 ? .20*sqrt(width) : .029*width)*taper*scale;
    vec3 radial=electricN*cos(UV0.y)+electricB*sin(UV0.y);
    vec3 position=center+(UV0.y<0.0 ? vec3(0.0) : radial*radius);
    viewPosition=(ModelViewMat*vec4(position,1.0)).xyz;
    viewCenter=(ModelViewMat*vec4(center,1.0)).xyz;
    viewTangent=normalize(mat3(ModelViewMat)*electricTangent);
    viewNormal=normalize(mat3(ModelViewMat)*radial);
    arcRadius=radius;
    arcEnergy=Color.a*(.88+.12*sin(u*7.0-GameTime*1200.0*36.0));
    if(strand>2.5) arcEnergy*=.75*(1.0-smoothstep(.76,1.0,progress));
    gl_Position=ProjMat*vec4(viewPosition,1.0);
}
