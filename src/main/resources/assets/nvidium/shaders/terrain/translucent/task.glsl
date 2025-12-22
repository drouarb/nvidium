#version 460

#extension GL_ARB_shading_language_include : enable
#pragma optionNV(unroll all)
#define UNROLL_LOOP
#import <nvidium:utils/mesh_wrapper.glsl>

#extension GL_KHR_shader_subgroup_basic : require
#extension GL_KHR_shader_subgroup_ballot : require
#extension GL_KHR_shader_subgroup_vote : require

#import <nvidium:occlusion/scene.glsl>

#define MESH_WORKLOAD_PER_INVOCATION 32

//This is 1 since each task shader workgroup -> multiple meshlets. its not each globalInvocation (afaik)
layout(local_size_x=1) in;

//In here add an array that is then "logged" on in the mesh shader to find the draw data
struct Task {
    vec4 originAndBaseData;
    uint quadCount;
    #ifdef TRANSLUCENCY_SORTING_QUADS
    uint jiggle;
    #endif
    int translucencyIndex;
};

taskPayloadSharedEXT Task task;

layout (std430, binding = 3) readonly buffer sectionDataBuffer {
    Section sectionData[];
};

layout (std430, binding = 5) readonly buffer sectionVisibilityBuffer {
    uint sectionVisibility[];
};

layout(std430, binding=7) readonly buffer translucencyCommandBufferBuffer {
    uvec4 translucencyCommandBuffer[];
};

#ifdef STATISTICS_SECTIONS
layout(std430, binding=13) buffer statBuffer {
    uint statistics_buffer[];
};
#endif

void main() {
    uint sectionId = (sectionVisibility[translucencyCommandBuffer[gl_DrawID].w + gl_WorkGroupID.x] >> 16) + translucencyCommandBuffer[gl_DrawID].w;
    #ifdef TRANSLUCENCY_SORTING_SECTIONS
    //Compute indirection for translucency sorting
    {
        ivec4 header = sectionData[sectionId].header;
        //If the section is empty, we dont care about it at all, so ignore it and return
        if (sectionEmpty(header)) {
            return;
        }
        //Compute the redirected section index
        sectionId &= ~0xFFu;
        sectionId |= uint((header.y>>18)&0xFFu);
    }
    #endif

    task.translucencyIndex = sectionData[sectionId].translucencyDataIdx;

    ivec4 header = sectionData[sectionId].header;
    uint baseDataOffset = uint(header.w);
    ivec3 chunk = ivec3(header.xyz)>>8;
    chunk.y &= 0x1ff;
    chunk.y <<= 32-9;
    chunk.y >>= 32-9;
    task.originAndBaseData.xyz = vec3((chunk - chunkPosition.xyz)<<4);


    task.quadCount = ((sectionData[sectionId].renderRanges.w>>16)&0xFFFF);
    #ifdef TRANSLUCENCY_SORTING_QUADS
    task.jiggle = min(task.quadCount>>1,(uint(frameId)&1u));//Jiggle by 1 quads (either 0 or 1)//*15
    //jiggle = uint8_t(0);
    task.quadCount += task.jiggle;
    task.originAndBaseData.w = uintBitsToFloat(baseDataOffset - task.jiggle);
    #else
    task.originAndBaseData.w = uintBitsToFloat(baseDataOffset);
    #endif

    //Emit enough mesh shaders such that max(gl_GlobalInvocationID.x)>=quadCount
    EmitMeshTasksEXT((task.quadCount+MESH_WORKLOAD_PER_INVOCATION-1)/MESH_WORKLOAD_PER_INVOCATION, 1, 1);

    #ifdef STATISTICS_QUADS
    atomicAdd(statistics_buffer[2], task.quadCount);
    #endif

    #ifdef STATISTICS_SECTIONS
    atomicAdd(statistics_buffer[1], 1);
    #endif
}