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
            Vertex V = vertexData[meshlet.vtxOffset + vtxId]; // STUFF

            gl_MeshVerticesNV[vtxId].gl_Position = transformVertex(V);
        }
    }

    // Emit a quad per invocation
    uint triCount = 0;
    if (gl_LocalInvocationIndex < meshlet.quadCount) {
        uint quadId = gl_LocalInvocationIndex + meshlet.quadOffset;
        // TODO Culling
        gl_PrimitiveIndicesNV[(gl_LocalInvocationIndex * 6) + 0] = indexData[quadId * 6 + 0];
        gl_PrimitiveIndicesNV[(gl_LocalInvocationIndex * 6) + 1] = indexData[quadId * 6 + 1];
        gl_PrimitiveIndicesNV[(gl_LocalInvocationIndex * 6) + 2] = indexData[quadId * 6 + 2];

        // Pack meshletId + quad id in meshlet + triangle0
        gl_MeshPrimitivesNV[(gl_LocalInvocationIndex << 1) | 0u].gl_PrimitiveID = int(
            ((meshletOffset + gl_WorkGroupID.x) << 6) |
            (gl_LocalInvocationIndex << 1) |
            0u
        );

        gl_PrimitiveIndicesNV[(gl_LocalInvocationIndex * 6) + 3] = indexData[quadId * 6 + 3];
        gl_PrimitiveIndicesNV[(gl_LocalInvocationIndex * 6) + 4] = indexData[quadId * 6 + 4];
        gl_PrimitiveIndicesNV[(gl_LocalInvocationIndex * 6) + 5] = indexData[quadId * 6 + 5];

        // Pack meshletId + quad id in meshlet + triangle1
        gl_MeshPrimitivesNV[(gl_LocalInvocationIndex << 1) | 1u].gl_PrimitiveID = int(
            ((meshletOffset + gl_WorkGroupID.x) << 6) |
            (gl_LocalInvocationIndex << 1) |
            1u
        );

        triCount = 2;
    }

    uint totalTri = subgroupAdd(triCount);
    uint maxTri = subgroupMax(totalTri);

    if (subgroupElect()) {
        gl_PrimitiveCountNV = maxTri;
    }
}