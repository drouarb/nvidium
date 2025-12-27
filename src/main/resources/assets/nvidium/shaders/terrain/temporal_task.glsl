#version 460
//Temporal task shader
#extension GL_ARB_shading_language_include : enable
#pragma optionNV(unroll all)
#define UNROLL_LOOP
#import <nvidium:utils/mesh_wrapper.glsl>

#extension GL_KHR_shader_subgroup_basic : require
#extension GL_KHR_shader_subgroup_ballot : require
#extension GL_KHR_shader_subgroup_vote : require

#import <nvidium:occlusion/scene.glsl>


//This is 1 since each task shader workgroup -> multiple meshlets. its not each globalInvocation (afaik)
layout(local_size_x=1) in;

layout (std430, binding = 2) readonly buffer regionDataBuffer {
    Region regionData[];
};

layout (std430, binding = 3) readonly buffer sectionDataBuffer {
    Section sectionData[];
};

layout(std430, binding=14) buffer sectionIndiciesBuffer {
    uvec3 sectionIndicies[];
};

layout(std430, binding=15) buffer temporalCommandBufferBuffer {
    uvec4 temporalCommandBuffer[];
};

#ifdef STATISTICS_QUADS
layout(std430, binding=13) buffer statBuffer {
    uint statistics_buffer[];
};
#endif

#import <nvidium:terrain/task_common2.glsl>

void main() {
    uint sectionId = sectionIndicies[temporalCommandBuffer[gl_DrawID].w + gl_WorkGroupID.x].z + temporalCommandBuffer[gl_DrawID].w;

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