#version 460

#extension GL_ARB_shading_language_include : enable
#pragma optionNV(unroll all)
#define UNROLL_LOOP
#extension GL_NV_mesh_shader : require
#extension GL_NV_gpu_shader5 : require
#extension GL_NV_bindless_texture : require

#extension GL_KHR_shader_subgroup_arithmetic: require
#extension GL_KHR_shader_subgroup_basic : require
#extension GL_KHR_shader_subgroup_vote : require
#extension GL_KHR_shader_subgroup_shuffle_relative : enable

layout(binding = 1) uniform sampler2D tex_light;

#import <nvidium:occlusion/scene.glsl>
#import <nvidium:terrain/vertex_format/vertex_format.glsl>

#ifdef RENDER_FOG
#import <sodium:include/fog.glsl>
#endif

//It seems like for terrain at least, the sweat spot is ~16 quads per mesh invocation (even if the local size is not 32 )
layout(local_size_x = 32) in;
layout(triangles, max_vertices=128, max_primitives=64) out;

#ifndef USE_NV_FRAGMENT_SHADER_BARYCENTRIC
layout(location=1) out Interpolants {
#ifdef RENDER_FOG
    vec2 v_FragDistance;
#endif
    vec2 uv;
    vec3 v_colour;
} OUT[];
#endif

taskNV in Task {
    uvec4 binStarts;
    uvec4 binOffsets;

    vec3 origin;
    uint quadCount;
    uint transformationId;
};


//Do a binary search via global invocation index to determine the base offset
// Note, all threads in the work group are probably going to take the same path
uint getOffset() {
    uint gii = gl_GlobalInvocationID.x;
    bvec4 le = lessThan(uvec4(gii), binStarts);
    /*
    //This is so jank and funny
    return dot(binOffsets,notEqual(bvec4(ge.yzw,true), not(ge.xyzw)));
    */

    //TODO:IDEA, since x is always false (i.e. binStarts[0] == 0) we can use that extra space to pack more of the offset bits
    // this allows us to use a single uvec4 to transmit an entire section
    // since max size is 16 bit, we need 2/3 extra bits to store worst case, which we can
    // it does mean we need to readd the baseOffset to the task, but that contains inbuilt start offset of binOffsets.x
    uint retval = binOffsets.w;
    if (le.y) {//x is always true
        retval = binOffsets.x;
    } else if (le.z) {
        retval = binOffsets.y;
    } else if (le.w) {
        retval = binOffsets.z;
    }
    return retval+gii;
}

mat4 transformMat;

vec4 transformVertex(Vertex V) {
    vec3 pos = decodeVertexPosition(V)+origin;
    return MVP*(transformMat * vec4(pos,1.0));
}

Vertex V0;
vec4 pV0;
Vertex V1;
vec4 pV1;
Vertex V2;
vec4 pV2;
Vertex V3;
vec4 pV3;

void putVertex(uint id, Vertex V) {
#ifndef USE_NV_FRAGMENT_SHADER_BARYCENTRIC
    #ifdef RENDER_FOG
    vec3 pos = decodeVertexPosition(V)+origin;
    vec3 exactPos = pos+subchunkOffset.xyz;
    //OUT[id].fogLerp = clamp(computeFogLerp(exactPos, isCylindricalFog, fogStart, fogEnd) * fogColour.a, 0, 1);
    OUT[id].v_FragDistance = getFragDistance(exactPos);
    #endif

    OUT[id].uv = decodeVertexUV(V);
    OUT[id].v_colour = computeMultiplier(V);
#endif
}

ivec4 packScam(Vertex V2, Vertex V3, bool t1draw) {
    return ivec4(int(V2.x)<<16|int(V2.y), int(V2.z)<<16|int(V3.x), int(V3.y)<<16|int(V3.z), uint(t1draw));
}

//TODO: make it so that its 32 threads but still 16 quads, each thread processes 2 verticies
// it computes the min of 0,2 with subgroups, then locally it decieds if its triangle needs to be discarded
// should significantly increase the warp efficency
void main() {
    if (gl_LocalInvocationIndex == 0) {
        gl_PrimitiveCountNV = 0;//Set the prim count to 0
    }

    if (quadCount<=gl_GlobalInvocationID.x) {
        return;
    }
    uint id = getOffset();

    //If its over, dont rende
    if (id == uint(-1)) {
        return;
    }
    transformMat = transformationArray[transformationId];

    //Load the data
    V0 = terrainData[(id<<2)+0];
    V1 = terrainData[(id<<2)+1];
    V2 = terrainData[(id<<2)+2];
    V3 = terrainData[(id<<2)+3];

    //Transform the vertices
    pV0 = transformVertex(V0);
    pV1 = transformVertex(V1);
    pV2 = transformVertex(V2);
    pV3 = transformVertex(V3);

    bool t0draw = true;
    bool t1draw = true;

    #ifdef CULL_DEGENERATE_TRIANGLES
    //Compute the bounding pixels of the 2 triangles in the quad. note, vertex 0 and 2 are the common verticies
    {
        vec2 ssmin = ((pV0.xy/pV0.w)+1)*screenSize;
        vec2 ssmax = ssmin;

        vec2 point = ((pV2.xy/pV2.w)+1)*screenSize;
        ssmin = min(ssmin, point);
        ssmax = max(ssmax, point);

        point = ((pV1.xy/pV1.w)+1)*screenSize;
        vec2 t0min = min(ssmin, point);
        vec2 t0max = max(ssmax, point);

        point = ((pV3.xy/pV3.w)+1)*screenSize;
        vec2 t1min = min(ssmin, point);
        vec2 t1max = max(ssmax, point);

        //Possibly cull the triangles if they dont cover the center of a pixel on the screen (degen)
        float degenBias = 0.01f;
        t0draw = all(notEqual(round(t0min-degenBias),round(t0max+degenBias)));
        t1draw = all(notEqual(round(t1min-degenBias),round(t1max+degenBias)));
    }

    // Still broadcast even if culled
    ivec4 scam = subgroupShuffleUp(packScam(V2, V3, t1draw), 1);

    //Abort if there are no triangles to dispatch
    if (!(t0draw || t1draw)) {
        return;
    }
    #else
    ivec4 scam = subgroupShuffleUp(packScam(V2, v3, t1draw), 1);
    #endif


    uint vertCount = (gl_LocalInvocationIndex > 0 && (packScam(V1, V0, true) == scam)) ?
        2 : (t0draw ? 4 : 3);

    #ifdef STATISTICS_CULL
    atomicAdd(statistics_buffer+3, vertCount == 2 ? 2 : 0);
    #endif

    uint triCnt = uint(t0draw)+uint(t1draw);
    //*
    uint triIndex = subgroupExclusiveAdd(triCnt);
    uint totalTris = subgroupMax(triIndex+triCnt);
    uint indexIndex = triIndex*3;
    uint vertIndex = subgroupExclusiveAdd(vertCount);

    if (vertCount > 2) {
        // We can't scam them from previous thread, we have to emmit them, swapped to match previous quad layout
        if (t0draw) { // culling degen
            gl_MeshVerticesNV[vertIndex++].gl_Position = pV1;
        }
        gl_MeshVerticesNV[vertIndex++].gl_Position = pV0;
    }

    // Anyway we need pv2
    gl_MeshVerticesNV[vertIndex++].gl_Position = pV2;

    // Emit first triangle
    if (t0draw) {
        gl_PrimitiveIndicesNV[indexIndex++] = vertIndex - 2; // pV0 has been swapped with pV1 to match prev quad orde
        gl_PrimitiveIndicesNV[indexIndex++] = vertIndex - 3; // pV1
        gl_PrimitiveIndicesNV[indexIndex++] = vertIndex - 1; // pV2

        gl_MeshPrimitivesNV[triIndex++].gl_PrimitiveID = int(id<<1)|0;
    }

    if (t1draw) {
        gl_MeshVerticesNV[vertIndex].gl_Position = pV3;
        gl_PrimitiveIndicesNV[indexIndex++] = vertIndex-1; // pV2
        gl_PrimitiveIndicesNV[indexIndex++] = vertIndex;   // pV3
        gl_PrimitiveIndicesNV[indexIndex++] = vertIndex-2; // still pV0

        gl_MeshPrimitivesNV[triIndex++].gl_PrimitiveID = int(id<<1)|1;
    }

    if (subgroupElect()) {
        gl_PrimitiveCountNV = totalTris;
    }
}