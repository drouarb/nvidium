#version 460
#extension GL_ARB_shading_language_include : enable
#extension GL_ARB_gpu_shader_int64 : require
#pragma optionNV(unroll all)
#define UNROLL_LOOP
// We need it for perprimitiveEXT
#import <nvidium:utils/mesh_wrapper.glsl>

#if defined(USE_NV_FRAGMENT_SHADER_BARYCENTRIC) && !defined(EMULATE_BARY)
#extension GL_NV_fragment_shader_barycentric : require
#endif

layout(binding = 1) uniform sampler2D tex_light;

#import <nvidium:occlusion/scene.glsl>
#import <nvidium:terrain/vertex_format/vertex_format.glsl>

#ifdef RENDER_FOG
#define USE_FOG
#import <sodium:include/fog.glsl>
#endif

layout(std430, binding=9) readonly buffer terrainDataBuffer {
    Vertex terrainData[];
};

layout(location = 0) out vec4 colour;
#ifndef USE_NV_FRAGMENT_SHADER_BARYCENTRIC
layout(location = 1) in Interpolants {
    #ifdef RENDER_FOG
    vec2 v_FragDistance;
    #endif

    vec2 uv;
    vec3 v_colour;
} IN;
#endif
#ifdef EMULATE_BARY
layout(location = 1) in Interpolants {
    vec3 barycoord;
} IN;

vec3 barycoord;

#define gl_BaryCoordNV barycoord
#endif

layout(location = 10) perprimitiveEXT in int PRIMITRASH;

Vertex V0;
Vertex Vp;
Vertex V2;
#ifdef USE_NV_FRAGMENT_SHADER_BARYCENTRIC
void computeOutputColour(inout vec3 colour) {
    vec3 multiplier = gl_BaryCoordNV.x*computeMultiplier(V0) + gl_BaryCoordNV.y*computeMultiplier(Vp) + gl_BaryCoordNV.z*computeMultiplier(V2);
    colour *= multiplier;
}
#endif

#ifdef RENDER_FOG
//2 ways to do it, either use an interpolation, or screenspace reversal, screenspace reversal is better when many many vertices
// however interpolation increases ISBE
void applyFog(inout vec4 colour) {

#ifdef USE_NV_FRAGMENT_SHADER_BARYCENTRIC
    //Reverse the transformation and compute the original position
    vec4 clip = (MVPInv * vec4((gl_FragCoord.xy/screenSize)-1, gl_FragCoord.z*2-1, 1));
    vec3 pos = clip.xyz/clip.w;
    vec2 v_FragDistance = getFragDistance(pos);
    colour = _linearFog(colour, v_FragDistance, fogColour, environmentFog, renderFog);
#else
    colour = _linearFog(colour, IN.v_FragDistance, fogColour, environmentFog, renderFog);
#endif
}
#endif


layout(binding = 0) uniform sampler2D tex_diffuse;
void main() {
    uint quadId = uint(PRIMITRASH)>>1;
    bool triangle0 = uint((PRIMITRASH)&1)==0;
    uvec3 TRI_INDICIES = triangle0?uvec3(0,1,2):uvec3(2,3,0);
    V0 = terrainData[(quadId<<2)+TRI_INDICIES.x];
    Vp = terrainData[(quadId<<2)+TRI_INDICIES.y];
    V2 = terrainData[(quadId<<2)+TRI_INDICIES.z];

    #ifdef USE_NV_FRAGMENT_SHADER_BARYCENTRIC
    #ifdef EMULATE_BARY
        barycoord = triangle0 ? IN.barycoord.xyz : IN.barycoord.zyx;
    #endif
        float HALF_SHIFT = (1.0f/TEXTURE_MAX_SCALE)/2.0f;
        vec2 uv0 = decodeVertexUV(V0);
        vec2 uvp = decodeVertexUV(Vp);
        vec2 uv2 = decodeVertexUV(V2);
        vec2 uvr = gl_BaryCoordNV.x*uv0 + gl_BaryCoordNV.y*uvp + gl_BaryCoordNV.z*uv2;
        vec2 uv = clamp(uvr, min(min(uv0, uv2),uvp)+HALF_SHIFT, max(max(uv0, uv2),uvp)-HALF_SHIFT);

        if (hasMipping(V0)) {
            //Since this is a dynamic uniform it is safe to use dF* functions
            //Compute the partial derivatives w.r.t pre-clamping
            colour = textureGrad(tex_diffuse, uv, dFdx(uvr), dFdy(uvr));
        } else {
            colour = textureLod(tex_diffuse, uv, 0);//No mipping
        }
    #else
        float lodBias = hasMipping(V0)?0.0f:-4.0f;
        colour = texture(tex_diffuse, IN.uv, lodBias);
    #endif

    #ifndef TRANSLUCENT_PASS
        uint alphaCutoff = rawVertexAlphaCutoff(V0);
        if (colour.a < getVertexAlphaCutoff(alphaCutoff)) discard;
        colour.a = 1;
    #endif

    #ifdef USE_NV_FRAGMENT_SHADER_BARYCENTRIC
        computeOutputColour(colour.rgb);
    #else
        colour.rgb *= IN.v_colour;
    #endif

    #ifdef RENDER_FOG
    applyFog(colour);
    #endif
}