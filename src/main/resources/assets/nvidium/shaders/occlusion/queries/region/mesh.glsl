#version 460
#extension GL_ARB_shading_language_include : enable
#pragma optionNV(unroll all)
#define UNROLL_LOOP
#import <nvidium:utils/mesh_wrapper.glsl>

#import <nvidium:occlusion/scene.glsl>

layout (std430, binding = 1) readonly buffer regionIndiciesBuffer {
    uint regionIndicies[];
};

layout (std430, binding = 2) readonly buffer regionDataBuffer {
    Region regionData[];
};

layout (std430, binding = 4) writeonly buffer regionVisibilityBuffer {
    uint regionVisibility[];
};

#define ADD_SIZE (0.1f/16)

//TODO: maybe do multiple cubes per workgroup? this would increase utilization of individual sm's
layout (local_size_x = 12) in;
layout (triangles, max_vertices = 8, max_primitives = 12) out;

layout(location = 3) perprimitiveEXT out int PRIMITRASH[];

const uvec3 TRILUT[12] = uvec3[12](
    uvec3(0u, 1u, 2u),
    uvec3(1u, 3u, 2u),
    uvec3(0u, 2u, 6u),
    uvec3(6u, 4u, 0u),
    uvec3(0u, 4u, 5u),
    uvec3(5u, 1u, 0u),
    uvec3(1u, 5u, 7u),
    uvec3(7u, 3u, 1u),
    uvec3(4u, 6u, 7u),
    uvec3(7u, 5u, 4u),
    uvec3(2u, 7u, 6u),
    uvec3(2u, 3u, 7u)
);

void main() {
    //FIXME: It might actually be more efficent to just upload the region data straight into the ubo
    // this remove an entire level of indirection and also puts region data in the very fast path
    Region data = regionData[regionIndicies[gl_WorkGroupID.x]];//fetch the region data

    SetMeshOutputsEXT(8u, 12u);
    gl_PrimitiveTriangleIndicesEXT[gl_LocalInvocationID.x] = TRILUT[gl_LocalInvocationID.x];
    PRIMITRASH[gl_LocalInvocationID.x] = int(gl_WorkGroupID.x);

    if (gl_LocalInvocationID.x < 8) {
        ivec3 pos = unpackRegionPosition(data);
        pos -= chunkPosition.xyz;
        pos -= unpackOriginOffsetId(unpackRegionTransformId(data));
        ivec3 size = unpackRegionSize(data);

        vec3 start = pos - ADD_SIZE;
        vec3 end = start + 1 + size + (ADD_SIZE * 2);

        vec3 corner = vec3(
        ((gl_LocalInvocationID.x & 1u) == 0u) ? start.x : end.x,
        ((gl_LocalInvocationID.x & 4u) == 0u) ? start.y : end.y,
        ((gl_LocalInvocationID.x & 2u) == 0u) ? start.z : end.z
        );
        corner *= 16.0f;
        gl_MeshVerticesEXT[gl_LocalInvocationID.x].gl_Position = MVP * (getRegionTransformation(data) * vec4(corner, 1.0));
    }
}