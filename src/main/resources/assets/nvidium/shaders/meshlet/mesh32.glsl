#version 460

#extension GL_ARB_shading_language_include : enable
#pragma optionNV(unroll all)
#extension GL_NV_mesh_shader : require
#extension GL_NV_gpu_shader5 : require

#extension GL_KHR_shader_subgroup_basic: require
#extension GL_KHR_shader_subgroup_arithmetic: require
#extension GL_KHR_shader_subgroup_shuffle : enable

layout(binding = 1) uniform sampler2D tex_light;

#import <nvidium:occlusion/scene.glsl>
#import <nvidium:terrain/vertex_format/vertex_format.glsl>

taskNV in Task {
    uvec4 binStarts;
    uvec4 binOffsets;

    vec3 origin;
    uint quadCount;
    uint transformationId;
};

layout(local_size_x = INVOCATIONS_PER_MESHLET) in;
layout(triangles, max_vertices=64, max_primitives=32) out;

//Do a binary search via global invocation index to determine the base offset
// Note, all threads in the work group are probably going to take the same path
uint getOffset() {
    uint gii = gl_WorkGroupID.x;
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

void main() {
    if (gl_LocalInvocationIndex == 0) {
        gl_PrimitiveCountNV = 0;//Set the prim count to 0
    }
    transformMat = transformationArray[transformationId];

    // Fetch our meshlet
    uint meshletId = getOffset();
    Meshlet meshlet = meshletData[meshletId];

    // Emit our vertices
    for (uint i = 0; i < (uint(meshlet.vtxCount) + INVOCATIONS_PER_MESHLET - 1) / INVOCATIONS_PER_MESHLET; i++) {
        uint vtxId = (i * INVOCATIONS_PER_MESHLET) + gl_LocalInvocationIndex;

        if (vtxId < meshlet.vtxCount) {
            Vertex V = vertexData[meshlet.vtxOffset + vtxId];
            gl_MeshVerticesNV[vtxId].gl_Position = transformVertex(V);
        }
    }

    if ((gl_LocalInvocationIndex >> 1) >= meshlet.quadCount) { // Exit if we don't have a quad
        return;
    }

#ifdef STATISTICS_QUADS
    atomicAdd(statistics_buffer+2, 1);
#endif

    bool triangle1 = (gl_LocalInvocationIndex & uint(1)) == 1;
    uint quadId = (gl_LocalInvocationIndex >> 1) + meshlet.quadOffset;

    //Load common vertex tri0 => v0 tri1 => v2
    Vertex Vc = vertexData[meshlet.vtxOffset + indexData[quadId * 6 + (triangle1?3:0)]];
    vec4 pVc = transformVertex(Vc);

    //Load our own vertex tri0 => v1 tri1 => v3
    Vertex V = vertexData[meshlet.vtxOffset + indexData[quadId * 6 + (triangle1?4:1)]];
    vec4 pV = transformVertex(V);

    bool draw = true;

#ifdef CULL_DEGENERATE_TRIANGLES
    { //Compute the bounding pixels of the current triangle in the quad. note, vertex 0 and 2 are the common verticies
        vec2 ssmin = ((pVc.xy/pVc.w)+1)*screenSize;
        vec2 ssmax = ssmin;

        //We exchange data of side thread common vertex here
        vec2 pVc2 = subgroupShuffleXor(ssmin, 1u);
        ssmin = min(ssmin, pVc2);
        ssmax = max(ssmax, pVc2);

        vec2 point = ((pV.xy/pV.w)+1)*screenSize;
        vec2 tmin = min(ssmin, point);
        vec2 tmax = max(ssmax, point);

        //Possibly cull the triangles if they dont cover the center of a pixel on the screen (degen)
        float degenBias = 0.01f;
        draw = all(notEqual(round(tmin-degenBias),round(tmax+degenBias)));

        if (!draw) {
            #ifdef STATISTICS_CULL
            atomicAdd(statistics_buffer+3, 1); // Count culled triangles
            #endif
            return;
        }
    }
#endif

    // Emit a tri per invocation
    uint triCount = uint(draw);
    uint triIndex = subgroupExclusiveAdd(triCount);
    uint totalTris = subgroupMax(triIndex+triCount);
    uint indexIndex = triIndex*3;

    gl_PrimitiveIndicesNV[indexIndex++] = indexData[quadId * 6 + (triangle1?3:0)];
    gl_PrimitiveIndicesNV[indexIndex++] = indexData[quadId * 6 + (triangle1?4:1)];
    gl_PrimitiveIndicesNV[indexIndex++] = indexData[quadId * 6 + (triangle1?5:2)];

    // Pack meshletId + local quad id in meshlet + triangle0
    gl_MeshPrimitivesNV[triIndex++].gl_PrimitiveID = int((meshletId << 6) | gl_LocalInvocationIndex);

    if (subgroupElect()) {
        gl_PrimitiveCountNV = totalTris;
    }
}