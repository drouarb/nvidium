#version 460
#extension GL_ARB_shading_language_include : enable
#pragma optionNV(unroll all)
#define UNROLL_LOOP
#extension GL_NV_gpu_shader5 : require
#extension GL_NV_bindless_texture : require
#extension GL_NV_shader_buffer_load : require

//#extension GL_NV_conservative_raster_underestimation : enable

#ifdef USE_NV_FRAGMENT_SHADER_BARYCENTRIC
#extension GL_NV_fragment_shader_barycentric : require
#endif

layout(binding = 1) uniform sampler2D tex_light;

#import <nvidium:occlusion/scene.glsl>
#import <nvidium:terrain/vertex_format.glsl>

#ifdef RENDER_FOG
#define USE_FOG
#import <sodium:include/fog.glsl>
#endif


layout(location = 0) out vec4 colour;
#ifndef USE_NV_FRAGMENT_SHADER_BARYCENTRIC
layout(location = 1) in Interpolants {
    #ifdef RENDER_FOG
    vec2 v_FragDistance;
    #endif

    vec2 uv;
    vec3 v_colour;
};
#endif

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
#endif
    colour = _linearFog(colour, v_FragDistance, fogColour, environmentFog, renderFog);
}
#endif


layout(binding = 0) uniform sampler2D tex_diffuse;
void main() {
    uint quadId = uint(gl_PrimitiveID)>>1;
    bool triangle0 = uint((gl_PrimitiveID)&1)==0;
    uvec3 TRI_INDICIES = triangle0?uvec3(0,1,2):uvec3(2,3,0);
    V0 = terrainData[(quadId<<2)+TRI_INDICIES.x];
    Vp = terrainData[(quadId<<2)+TRI_INDICIES.y];
    V2 = terrainData[(quadId<<2)+TRI_INDICIES.z];

    #ifdef USE_NV_FRAGMENT_SHADER_BARYCENTRIC
        float HALF_SHIFT = (1f/TEXTURE_MAX_SCALE)/2f;
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
        colour = texture(tex_diffuse, uv, lodBias);
    #endif

    #ifndef TRANSLUCENT_PASS
        uint alphaCutoff = rawVertexAlphaCutoff(V0);
        if (colour.a < getVertexAlphaCutoff(alphaCutoff)) discard;
        colour.a = 1;
    #endif

    #ifdef USE_NV_FRAGMENT_SHADER_BARYCENTRIC
        computeOutputColour(colour.rgb);
    #else
        colour.rgb *= v_colour;
    #endif

    #ifdef RENDER_FOG
    applyFog(colour);
    #endif
}