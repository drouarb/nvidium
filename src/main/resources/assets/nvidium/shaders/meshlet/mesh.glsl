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
        int quadId = int(gl_LocalInvocationIndex);
        // TODO Culling
        gl_PrimitiveIndicesNV[(gl_LocalInvocationIndex * 6) + 0] = indexData[(gl_LocalInvocationIndex + meshlet.quadOffset) * 6 + 0];
        gl_PrimitiveIndicesNV[(gl_LocalInvocationIndex * 6) + 1] = indexData[(gl_LocalInvocationIndex + meshlet.quadOffset) * 6 + 1];
        gl_PrimitiveIndicesNV[(gl_LocalInvocationIndex * 6) + 2] = indexData[(gl_LocalInvocationIndex + meshlet.quadOffset) * 6 + 2];
        gl_MeshPrimitivesNV[(quadId << 1) | 0].gl_PrimitiveID = (quadId << 1) | 0;

        gl_PrimitiveIndicesNV[(gl_LocalInvocationIndex * 6) + 3] = indexData[(gl_LocalInvocationIndex + meshlet.quadOffset) * 6 + 3];
        gl_PrimitiveIndicesNV[(gl_LocalInvocationIndex * 6) + 4] = indexData[(gl_LocalInvocationIndex + meshlet.quadOffset) * 6 + 4];
        gl_PrimitiveIndicesNV[(gl_LocalInvocationIndex * 6) + 5] = indexData[(gl_LocalInvocationIndex + meshlet.quadOffset) * 6 + 5];
        gl_MeshPrimitivesNV[(quadId << 1) | 1].gl_PrimitiveID = (quadId << 1) | 1;

        triCount = 2;
    }

    uint totalTri = subgroupAdd(triCount);
    uint maxTri = subgroupMax(totalTri);

    if (subgroupElect()) {
        gl_PrimitiveCountNV = maxTri;
    }
}