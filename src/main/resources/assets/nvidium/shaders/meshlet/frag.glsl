#version 460
#extension GL_ARB_shading_language_include : enable
#pragma optionNV(unroll all)
#define UNROLL_LOOP
#extension GL_NV_gpu_shader5 : require
#extension GL_NV_fragment_shader_barycentric : require

layout(binding = 0) uniform sampler2D tex_diffuse;
layout(binding = 1) uniform sampler2D tex_light;

layout(location = 0) out vec4 colour;

#import <nvidium:occlusion/scene.glsl>
#import <nvidium:terrain/vertex_format/vertex_format.glsl>

VertexAttribute VA0;
VertexAttribute VAp;
VertexAttribute VA2;

void computeOutputColour(inout vec3 colour) {
    vec3 multiplier = gl_BaryCoordNV.x*computeMultiplier(VA0) + gl_BaryCoordNV.y*computeMultiplier(VAp) + gl_BaryCoordNV.z*computeMultiplier(VA2);
    colour *= multiplier;
}

void main() {
    uint meshletId = uint(gl_PrimitiveID)>>6;
    Meshlet meshlet = meshletData[meshletId];

    uint triId = uint(gl_PrimitiveID) & 0x3Fu;
    uint localQuadId = triId >> 1;
    uint quadId = uint(meshlet.quadOffset) + localQuadId;

    //V0 = vertexData[meshlet.vtxOffset + indexData[meshlet.quadOffset * 6 + triId * 3 + 0]];
    //Vp = vertexData[meshlet.vtxOffset + indexData[meshlet.quadOffset * 6 + triId * 3 + 1]];
    //V2 = vertexData[meshlet.vtxOffset + indexData[meshlet.quadOffset * 6 + triId * 3 + 2]];

    bool triangle0 = uint((gl_PrimitiveID)&1)==0;
    uvec3 TRI_INDICIES = triangle0?uvec3(0,1,2):uvec3(2,3,0);

    VA0 = attrData[(quadId<<2)+TRI_INDICIES.x];
    VAp = attrData[(quadId<<2)+TRI_INDICIES.y];
    VA2 = attrData[(quadId<<2)+TRI_INDICIES.z];

    float HALF_SHIFT = (1f/TEXTURE_MAX_SCALE)/2f;
    vec2 uv0 = decodeVertexUV(VA0);
    vec2 uvp = decodeVertexUV(VAp);
    vec2 uv2 = decodeVertexUV(VA2);
    vec2 uvr = gl_BaryCoordNV.x*uv0 + gl_BaryCoordNV.y*uvp + gl_BaryCoordNV.z*uv2;
    vec2 uv = clamp(uvr, min(min(uv0, uv2),uvp)+HALF_SHIFT, max(max(uv0, uv2),uvp)-HALF_SHIFT);

    if (hasMipping(VA0)) {
        //Since this is a dynamic uniform it is safe to use dF* functions
        //Compute the partial derivatives w.r.t pre-clamping
        colour = textureGrad(tex_diffuse, uv, dFdx(uvr), dFdy(uvr));
    } else {
        colour = textureLod(tex_diffuse, uv, 0);//No mipping
    }

    uint alphaCutoff = rawVertexAlphaCutoff(VA0);
    if (colour.a < getVertexAlphaCutoff(alphaCutoff)) discard;
    colour.a = 1;

    computeOutputColour(colour.rgb);
}