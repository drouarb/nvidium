#version 460

#extension GL_ARB_shading_language_include : enable
#pragma optionNV(unroll all)
#define UNROLL_LOOP
#import <nvidium:utils/mesh_wrapper.glsl>

#extension GL_KHR_shader_subgroup_basic : require
#extension GL_KHR_shader_subgroup_ballot : require
#extension GL_KHR_shader_subgroup_vote : require

layout(binding = 1) uniform sampler2D tex_light;

#import <nvidium:occlusion/scene.glsl>
#import <nvidium:terrain/vertex_format/vertex_format.glsl>

#ifdef RENDER_FOG
#import <sodium:include/fog.glsl>
#endif

#ifdef TRANSLUCENCY_SORTING_QUADS
vec3 depthPos = vec3(0);
shared float depthBuffers[32];
#endif

layout(local_size_x = 32) in;
layout(triangles, max_vertices=128, max_primitives=64) out;

//originAndBaseData.w is in quad count space, so is endIdx
struct Task {
    vec4 originAndBaseData;
    uint quadCount;
    #ifdef TRANSLUCENCY_SORTING_QUADS
    uint jiggle;
    #endif
    int translucencyIndex;
};

taskPayloadSharedEXT Task task;

layout(location = 3) perprimitiveEXT out int PRIMITRASH[];

layout(std430, binding=9) readonly buffer terrainDataBuffer {
    Vertex terrainData[];
};

#ifdef TRANSLUCENCY_SORTING_SODIUM
layout(std430, binding=10) buffer translucencyIndexDataBuffer {
    uint translucencyIndexData[];
};
#endif

#ifndef USE_NV_FRAGMENT_SHADER_BARYCENTRIC
layout(location=1) out Interpolants {
#ifdef RENDER_FOG
    vec2 v_FragDistance;
#endif
    vec2 uv;
    vec3 v_colour;
} OUT[];
#endif
#ifdef EMULATE_BARY
layout(location = 1) out Interpolants {
    vec3 barycoord;
} OUT[];
#endif

void emitQuadIndicies() {
    uint vertexBase = gl_LocalInvocationID.x<<2;
    gl_PrimitiveTriangleIndicesEXT[gl_LocalInvocationID.x * 2] = uvec3(vertexBase+0, vertexBase+1, vertexBase+2);
    gl_PrimitiveTriangleIndicesEXT[gl_LocalInvocationID.x * 2 + 1] = uvec3(vertexBase+2, vertexBase+3, vertexBase+0);
}

void emitVertex(uint vertexBaseId, uint innerId) {
    Vertex V = terrainData[vertexBaseId + innerId];
    uint outId = (gl_LocalInvocationID.x<<2)+innerId;
    vec3 pos = decodeVertexPosition(V)+task.originAndBaseData.xyz;
    gl_MeshVerticesEXT[outId].gl_Position = MVP*vec4(pos,1.0);

#ifndef USE_NV_FRAGMENT_SHADER_BARYCENTRIC
    #ifdef RENDER_FOG
    OUT[outId].v_FragDistance = getFragDistance(pos+subchunkOffset.xyz);
    #endif

    OUT[outId].uv = decodeVertexUV(V);

    vec4 tint = decodeVertexColour(V);
    tint *= sampleLight(decodeLightUV(V));
    tint *= tint.w;
    OUT[outId].v_colour = tint.rgb;
#endif
}

#ifdef TRANSLUCENCY_SORTING_QUADS
void computeDepth(uint vertexBaseId) {
    for (uint innerId = 0; innerId < 4; innerId++) {
        Vertex V = terrainData[vertexBaseId + innerId];
        uint outId = (gl_LocalInvocationID.x<<2)+innerId;
        vec3 pos = decodeVertexPosition(V)+task.originAndBaseData.xyz;
        vec3 exactPos = pos+subchunkOffset.xyz;
        depthPos += exactPos;
    }
}

void swapQuads(uint idxA, uint idxB) {
    if (idxA == idxB) {
        return;
    }

    Vertex A0 = terrainData[(idxA<<2)+0];
    Vertex A1 = terrainData[(idxA<<2)+1];
    Vertex A2 = terrainData[(idxA<<2)+2];
    Vertex A3 = terrainData[(idxA<<2)+3];
    Vertex B0 = terrainData[(idxB<<2)+0];
    Vertex B1 = terrainData[(idxB<<2)+1];
    Vertex B2 = terrainData[(idxB<<2)+2];
    Vertex B3 = terrainData[(idxB<<2)+3];
    //groupMemoryBarrier();
    //memoryBarrier();
    //barrier();
    terrainData[(idxA<<2)+0] = B0;
    terrainData[(idxA<<2)+1] = B1;
    terrainData[(idxA<<2)+2] = B2;
    terrainData[(idxA<<2)+3] = B3;
    terrainData[(idxB<<2)+0] = A0;
    terrainData[(idxB<<2)+1] = A1;
    terrainData[(idxB<<2)+2] = A2;
    terrainData[(idxB<<2)+3] = A3;
    //groupMemoryBarrier();
    //memoryBarrier();
    //barrier();
}

void performTranslucencySort() {
    uint baseQuadPtr = floatBitsToUint(task.originAndBaseData.w) + (gl_WorkGroupID.x<<5);

    float depth = dot(depthPos, depthPos) * ((1/4.0f)*(1/4.0f));
    depthBuffers[gl_LocalInvocationID.x] = depth;

    if (gl_GlobalInvocationID.x < task.jiggle) {
        //If we are in the jiggle index dont attempt to swap else we start rendering garbage data
        depthBuffers[gl_LocalInvocationID.x] = -9999.0f;
    }

    groupMemoryBarrier();
    memoryBarrier();
    barrier();
    //TODO: use subgroup ballot to check if all the quads are already sorted, if they are dont perform sort op

    //Only use 16 threads to sort all 32 data
    if (gl_LocalInvocationID.x < 16) {
        uint idA = (gl_LocalInvocationID.x<<1);
        uint idB = (gl_LocalInvocationID.x<<1)+1;
        float a = depthBuffers[idA];
        float b = depthBuffers[idB];

        if (a > 0.0001f &&  b > 0.0001f && a < b) {
            swapQuads(idA + baseQuadPtr, idB + baseQuadPtr);
        }
    }
}
#endif

//TODO: extra per quad culling
void main() {
    #ifdef TRANSLUCENCY_SORTING_QUADS
    depthBuffers[gl_LocalInvocationID.x] = -99999999.0f;
    #endif
    if ((gl_GlobalInvocationID.x)>=task.quadCount) { //If its over the quad count, dont render
        return;
    }

    uint currentPrimCount = uint(min(uint(int(task.quadCount)-int(gl_WorkGroupID.x<<5))<<1, 64u));
    SetMeshOutputsEXT(currentPrimCount<<1, currentPrimCount);

    emitQuadIndicies();

    //Each pair of meshlet invokations emits 4 vertices each and 2 primative each
    uint id;
    #ifdef TRANSLUCENCY_SORTING_SODIUM
    if (task.translucencyIndex == -1) { // If no translucency data, fallback to quad order
        id = (floatBitsToUint(task.originAndBaseData.w) + gl_GlobalInvocationID.x)<<2;
    } else { // If we have sorting data, process the vertex at the index translucencyIndexData
        id = (floatBitsToUint(task.originAndBaseData.w) + translucencyIndexData[task.translucencyIndex + gl_GlobalInvocationID.x])<<2;
    }
    #else
    id = (floatBitsToUint(task.originAndBaseData.w) + gl_GlobalInvocationID.x)<<2;
    #endif

#ifdef TRANSLUCENCY_SORTING_QUADS
    // Need to sort before emitting quads if we want to do nv fragment shader barycentric
    computeDepth(id);
    performTranslucencySort();
    barrier();
    memoryBarrierShared();

    //If we are at the start, dont want to render as it contains garbled data (out of bounds)
    if (gl_GlobalInvocationID.x < task.jiggle) {
        gl_MeshVerticesNV[(gl_LocalInvocationID.x<<2)+0].gl_Position = vec4(1,1,1,-1);
        gl_MeshVerticesNV[(gl_LocalInvocationID.x<<2)+1].gl_Position = vec4(1,1,1,-1);
        gl_MeshVerticesNV[(gl_LocalInvocationID.x<<2)+2].gl_Position = vec4(1,1,1,-1);
        gl_MeshVerticesNV[(gl_LocalInvocationID.x<<2)+3].gl_Position = vec4(1,1,1,-1);

    } else {
        emitVertex(id, 0);
        emitVertex(id, 1);
        emitVertex(id, 2);
        emitVertex(id, 3);
    }
#else
    #ifdef EMULATE_BARY
    OUT[(gl_LocalInvocationID.x<<2)+0].barycoord = vec3(1.0, 0.0, 0.0);
    OUT[(gl_LocalInvocationID.x<<2)+1].barycoord = vec3(0.0, 1.0, 0.0);
    OUT[(gl_LocalInvocationID.x<<2)+2].barycoord = vec3(0.0, 0.0, 1.0);
    OUT[(gl_LocalInvocationID.x<<2)+3].barycoord = vec3(0.0, 1.0, 0.0);
    #endif
    emitVertex(id, 0);
    emitVertex(id, 1);
    emitVertex(id, 2);
    emitVertex(id, 3);
#endif

    PRIMITRASH[(gl_LocalInvocationID.x<<1)] = int((id>>2)<<1)|0;
    PRIMITRASH[(gl_LocalInvocationID.x<<1)|1u] = int((id>>2)<<1)|1;
}