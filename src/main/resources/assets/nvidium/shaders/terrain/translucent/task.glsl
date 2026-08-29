#version 460

#extension GL_EXT_mesh_shader : require
#extension GL_EXT_buffer_reference : require
#extension GL_EXT_shader_explicit_arithmetic_types_int8 : require
#extension GL_EXT_shader_explicit_arithmetic_types_int16 : require
#extension GL_EXT_shader_explicit_arithmetic_types_int64 : require

#moj_import <nvidium:occlusion/scene.glsl>

#define MESH_WORKLOAD_PER_INVOCATION 32

//This is 1 since each task shader workgroup -> multiple meshlets. its not each globalInvocation (afaik)
layout(local_size_x=1) in;

//In here add an array that is then "logged" on in the mesh shader to find the draw data
struct Task {
    vec4 originAndBaseData;
    uint quadCount;
    #ifdef TRANSLUCENCY_SORTING_QUADS
    uint8_t jiggle;
    #endif
    int translucencyIndex;
};

taskPayloadSharedEXT Task task;

void main() {
    uint sectionId = sectionIndices.data[translucencyCommandBuffer.data[gl_DrawID].w + gl_WorkGroupID.x].y + translucencyCommandBuffer.data[gl_DrawID].w;

    task.translucencyIndex = sectionData.data[sectionId].translucencyDataIdx;

    ivec4 header = sectionData.data[sectionId].header;
    uint baseDataOffset = uint(header.w);
    ivec3 chunk = ivec3(header.xyz)>>8;
    chunk.y &= 0x1ff;
    chunk.y <<= 32-9;
    chunk.y >>= 32-9;
    task.originAndBaseData.xyz = vec3((chunk - chunkPosition.xyz)<<4);

    task.quadCount = sectionData.data[sectionId].tranlucentQuadCount;
    #ifdef TRANSLUCENCY_SORTING_QUADS
    jiggle = uint8_t(min(task.quadCount>>1,(uint(frameId)&1)));//Jiggle by 1 quads (either 0 or 1)//*15
    //jiggle = uint8_t(0);
    task.quadCount += jiggle;
    task.originAndBaseData.w = uintBitsToFloat(baseDataOffset - uint(jiggle));
    #else
    task.originAndBaseData.w = uintBitsToFloat(baseDataOffset);
    #endif

    #ifdef STATISTICS_QUADS
    atomicAdd(statistics_buffer.data[2], task.quadCount);
    #endif

    #ifdef STATISTICS_SECTIONS
    atomicAdd(statistics_buffer.data[1], 1);
    #endif

    EmitMeshTasksEXT((task.quadCount+MESH_WORKLOAD_PER_INVOCATION-1)/MESH_WORKLOAD_PER_INVOCATION, 1, 1);
}