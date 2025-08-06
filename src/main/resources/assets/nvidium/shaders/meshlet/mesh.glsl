#version 460

#extension GL_ARB_shading_language_include : enable
#pragma optionNV(unroll all)
#extension GL_NV_mesh_shader : require
#extension GL_NV_gpu_shader5 : require

#extension GL_KHR_shader_subgroup_basic: require
#extension GL_KHR_shader_subgroup_arithmetic: require

layout(binding = 1) uniform sampler2D tex_light;

#import <nvidium:occlusion/scene.glsl>
#import <nvidium:terrain/vertex_format/vertex_format.glsl>

taskNV in Task {
    vec3 origin;
    uint meshletOffset;
    uint meshletCount;
    uint transformationId;
};

layout(local_size_x = INVOCATIONS_PER_MESHLET) in;
layout(triangles, max_vertices=96, max_primitives=64) out;

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
    Meshlet meshlet = meshletData[meshletOffset + gl_WorkGroupID.x];

    // Emit our vertices
    for (uint i = 0; i < (uint(meshlet.vtxCount) + INVOCATIONS_PER_MESHLET - 1) / INVOCATIONS_PER_MESHLET; i++) {
        uint vtxId = (i * INVOCATIONS_PER_MESHLET) + gl_LocalInvocationIndex;

        if (vtxId < meshlet.vtxCount) {
            Vertex V = vertexData[meshlet.vtxOffset + vtxId];
            gl_MeshVerticesNV[vtxId].gl_Position = transformVertex(V);
        }
    }

    if (gl_LocalInvocationIndex >= meshlet.quadCount) { // Exit if we don't have a quad
        return;
    }

#ifdef STATISTICS_QUADS
    atomicAdd(statistics_buffer+2, 1);
#endif
    uint quadId = gl_LocalInvocationIndex + meshlet.quadOffset;

    // Culling time
    // Fetch V0 V1 V2
    Vertex V0 = vertexData[meshlet.vtxOffset + indexData[quadId * 6 + 0]];
    vec4 pV0 = transformVertex(V0);
    Vertex V1 = vertexData[meshlet.vtxOffset + indexData[quadId * 6 + 1]];
    vec4 pV1 = transformVertex(V1);
    Vertex V2 = vertexData[meshlet.vtxOffset + indexData[quadId * 6 + 2]];
    vec4 pV2 = transformVertex(V2);

    vec2 v0 = pV0.xy / pV0.w;
    vec2 v1 = pV1.xy / pV1.w;
    vec2 v2 = pV2.xy / pV2.w;

    float area = (v1.x - v0.x)*(v2.y - v0.y) - (v1.y - v0.y)*(v2.x - v0.x);
    if (area <= 0.0) { // Backface quad
        return;
    }

    // indexData[quadId * 6 + 3] is actually V2
    Vertex V3 = vertexData[meshlet.vtxOffset + indexData[quadId * 6 + 4]];
    vec4 pV3 = transformVertex(V3);

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

    //Abort if there are no triangles to dispatch
    if (!(t0draw || t1draw)) {
        #ifdef STATISTICS_CULL
        atomicAdd(statistics_buffer+3, 1);
        #endif
        return;
    }
#endif


    // Emit a quad per invocation
    uint triCount = uint(t0draw)+uint(t1draw);
    uint triIndex = subgroupExclusiveAdd(triCount);
    uint totalTris = subgroupMax(triIndex+triCount);
    uint indexIndex = triIndex*3;

    if (t0draw) {
        gl_PrimitiveIndicesNV[indexIndex++] = indexData[quadId * 6 + 0];
        gl_PrimitiveIndicesNV[indexIndex++] = indexData[quadId * 6 + 1];
        gl_PrimitiveIndicesNV[indexIndex++] = indexData[quadId * 6 + 2];

        // Pack meshletId + local quad id in meshlet + triangle0
        gl_MeshPrimitivesNV[triIndex++].gl_PrimitiveID = int(
            ((meshletOffset + gl_WorkGroupID.x) << 6) |
            (gl_LocalInvocationIndex << 1) |
            0u
        );
    }

    if (t1draw) {
        gl_PrimitiveIndicesNV[indexIndex++] = indexData[quadId * 6 + 3];
        gl_PrimitiveIndicesNV[indexIndex++] = indexData[quadId * 6 + 4];
        gl_PrimitiveIndicesNV[indexIndex++] = indexData[quadId * 6 + 5];

        // Pack meshletId + quad id in meshlet + triangle1
        gl_MeshPrimitivesNV[triIndex].gl_PrimitiveID = int(
            ((meshletOffset + gl_WorkGroupID.x) << 6) |
            (gl_LocalInvocationIndex << 1) |
            1u
        );
    }

    if (subgroupElect()) {
        gl_PrimitiveCountNV = totalTris;
    }
}