#version 460

#extension GL_ARB_shading_language_include: enable
#extension GL_ARB_gpu_shader_int64: require
#pragma optionNV(unroll all)
#define UNROLL_LOOP
#import <nvidium:utils/mesh_wrapper.glsl>

#extension GL_KHR_shader_subgroup_basic: require
#extension GL_KHR_shader_subgroup_ballot: require
#extension GL_KHR_shader_subgroup_vote: require

#import <nvidium:occlusion/scene.glsl>

#define ADD_SIZE (0.1f)
layout (local_size_x = 12) in;
layout (triangles, max_vertices = 8, max_primitives = 12) out;

layout(location = 3) perprimitiveEXT out int PRIMITRASH[];
layout(location = 4) perprimitiveEXT out uint cmdIdxStore[];

layout (std430, binding = 3) readonly buffer sectionDataBuffer {
    Section sectionData[];
};

layout (std430, binding = 5) buffer sectionVisibilityBuffer {
    uint sectionVisibility[];
};

struct Task {
    uint _visOutBase;//Base output visibility index
    uint _offset;
    mat4 regionTransform;
    ivec3 chunkShift;
    uint cmdIdx;
};

taskPayloadSharedEXT Task task;

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

//TODO: Check if the section can be culled via fog
void main() {
    int visibilityIndex = int(task._visOutBase | gl_WorkGroupID.x);

    uint lastData = sectionVisibility[visibilityIndex];
    // this is almost 100% guarenteed not needed afaik
    //barrier();

    ivec4 header = sectionData[task._offset | gl_WorkGroupID.x].header;
    //If the section header was empty or the hide section bit is set, return

    //NOTE: technically this has the infinitly small probability of not rendering a block if the block is located at
    // 0,0,0 the only block in the chunk and the first thing in the buffer
    // to fix, also check that the ranges are null
    if (sectionEmpty(header) || (header.y & (1 << 17)) != 0) {
        if (gl_LocalInvocationID.x == 0) {
            sectionVisibility[visibilityIndex] = 0;
        }
        SetMeshOutputsEXT(0u, 0u);
        return;
    }

    SetMeshOutputsEXT(8u, 12u);

    int prim_payload = (visibilityIndex << 8) | int((lastData << 1) & 0xFFu) | 1;
    gl_PrimitiveTriangleIndicesEXT[gl_LocalInvocationID.x] = TRILUT[gl_LocalInvocationID.x];
    PRIMITRASH[gl_LocalInvocationID.x] = prim_payload;
    cmdIdxStore[gl_LocalInvocationID.x] = task.cmdIdx;

    if (gl_LocalInvocationID.x < 8) {
        vec3 mins = (header.xyz & 0xF) - ADD_SIZE;
        vec3 maxs = mins + ((header.xyz >> 4) & 0xF) + 1 + (ADD_SIZE * 2);
        ivec3 chunk = ivec3(header.xyz) >> 8;
        chunk.y &= 0x1ff;
        chunk.y <<= 32 - 9;
        chunk.y >>= 32 - 9;

        ivec3 relativeChunkPos = (chunk + task.chunkShift);
        vec3 corner = vec3(relativeChunkPos << 4);
        vec3 cornerCopy = corner;

        //TODO: try mix instead or something other than just ternaries, i think they get compiled to a cmov type instruction but not sure
        corner += vec3(
            ((gl_LocalInvocationID.x & 1u) == 0u) ? mins.x : maxs.x,
            ((gl_LocalInvocationID.x & 4u) == 0u) ? mins.y : maxs.y,
            ((gl_LocalInvocationID.x & 2u) == 0u) ? mins.z : maxs.z
        );

        gl_MeshVerticesEXT[gl_LocalInvocationID.x].gl_Position = MVP * (task.regionTransform * vec4(corner, 1.0));

        if (gl_LocalInvocationID.x == 0) {
            cornerCopy += subchunkOffset.xyz;
            vec3 minPos = mins + cornerCopy;
            vec3 maxPos = maxs + cornerCopy;
            bool isInSection = all(lessThan(minPos, vec3(ADD_SIZE))) && all(lessThan(vec3(-ADD_SIZE), maxPos));

            //Shift and set, this gives us a bonus of having the last 8 frames as visibility history
            sectionVisibility[visibilityIndex] = uint((lastData << 1) & 0xFFu) | uint(isInSection ? 1 : 0);//Inject visibility aswell
            //sectionVisibility[visibilityIndex] = uint8_t(lastData<<1) | uint8_t(0);
        }
    }
}