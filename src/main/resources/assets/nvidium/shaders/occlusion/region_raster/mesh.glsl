#version 460
#pragma optionNV(unroll all)
#define UNROLL_LOOP

#extension GL_EXT_mesh_shader : require
#extension GL_EXT_buffer_reference : require
#extension GL_EXT_shader_explicit_arithmetic_types_int8 : require
#extension GL_EXT_shader_explicit_arithmetic_types_int16 : require
#extension GL_EXT_shader_explicit_arithmetic_types_int64 : require

#moj_import <nvidium:occlusion/scene.glsl>

#define ADD_SIZE (0.1f/16)

layout (local_size_x = 12) in;
layout (triangles, max_vertices = 8, max_primitives = 12) out;

// WE NEED TO CREATE OUR OWN gl_PrimitiveId other shaderc will set our fragment to OpCapability Geometry https://github.com/KhronosGroup/glslang/issues/4147
layout(location = 3) perprimitiveEXT flat out int PRIMITRASH[];

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
    Region data = regionData.data[regionIndicies.data[gl_WorkGroupID.x]];//fetch the region data

    int visibilityIndex = int(gl_WorkGroupID.x);
    //If the region metadata was empty, return
    if (data.a == uint64_t(-1)) {
        regionVisibility.data[visibilityIndex] = uint8_t(0);
        SetMeshOutputsEXT(0u, 0u);
        return;
    }

    SetMeshOutputsEXT(8u, 12u);

    gl_PrimitiveTriangleIndicesEXT[gl_LocalInvocationID.x] = TRILUT[gl_LocalInvocationID.x];
    PRIMITRASH[gl_LocalInvocationID.x] = visibilityIndex;

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

        if (gl_LocalInvocationID.x == 0) {
            bool cameraInRegion = all(lessThan(start * 16 + subchunkOffset.xyz, vec3(ADD_SIZE * 16))) && all(lessThan(vec3(-ADD_SIZE * 16), end * 16 + subchunkOffset.xyz));
            regionVisibility.data[visibilityIndex] = cameraInRegion ? uint8_t(1) : uint8_t(0);
        }
    }
}