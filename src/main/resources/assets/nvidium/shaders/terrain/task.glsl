#version 460

#extension GL_ARB_shading_language_include : enable
#pragma optionNV(unroll all)
#define UNROLL_LOOP
#import <nvidium:utils/mesh_wrapper.glsl>

#extension GL_KHR_shader_subgroup_basic : require
#extension GL_KHR_shader_subgroup_ballot : require
#extension GL_KHR_shader_subgroup_vote : require

#import <nvidium:occlusion/scene.glsl>
#import <nvidium:terrain/task_common2.glsl>

//This is 1 since each task shader workgroup -> multiple meshlets. its not each globalInvocation (afaik)
layout(local_size_x=1) in;

layout (std430, binding = 2) readonly buffer regionDataBuffer {
    Region regionData[];
};

layout (std430, binding = 3) readonly buffer sectionDataBuffer {
    Section sectionData[];
};

layout (std430, binding = 5) readonly buffer sectionVisibilityBuffer {
    uint sectionVisibility[];
};

layout(std430, binding=6) readonly buffer terrainCommandBufferBuffer {
    uvec4 terrainCommandBuffer[];
};

#ifdef STATISTICS_SECTIONS
layout(std430, binding=13) buffer statBuffer {
    uint statistics_buffer[];
};
#endif

bool shouldRenderVisible(uint sectionId) {
    return (sectionVisibility[sectionId]&1u) != 0u;
}

void main() {
    uint sectionId = terrainCommandBuffer[gl_DrawID].w + gl_WorkGroupID.x;

    if (!shouldRenderVisible(sectionId)) { //Early exit if the section isnt visible
        EmitMeshTasksEXT(0, 0, 0);
        return;
    }

    #ifdef STATISTICS_SECTIONS
    atomicAdd(statistics_buffer[1], 1);
    #endif

    ivec4 header = sectionData[sectionId].header;
    ivec3 chunk = ivec3(header.xyz)>>8;
    chunk.y &= 0x1ff;
    chunk.y <<= 32-9;
    chunk.y >>= 32-9;
    chunk -= chunkPosition.xyz;
    task.transformationId = unpackRegionTransformId(regionData[sectionId>>8]);
    chunk -= unpackOriginOffsetId(task.transformationId);

    task.origin = vec3(chunk<<4);
    populateTasks(chunk, uint(header.w), uvec4(sectionData[sectionId].renderRanges));


    #ifdef STATISTICS_QUADS
    atomicAdd(statistics_buffer[2], task.quadCount);
    #endif
}