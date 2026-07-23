#version 460
//Temporal task shader
#pragma optionNV(unroll all)
#define UNROLL_LOOP

#extension GL_EXT_mesh_shader : require
#extension GL_EXT_buffer_reference : require
#extension GL_EXT_shader_explicit_arithmetic_types_int8 : require
#extension GL_EXT_shader_explicit_arithmetic_types_int16 : require
#extension GL_EXT_shader_explicit_arithmetic_types_int64 : require

#moj_import <nvidium:occlusion/scene.glsl>

//This is 1 since each task shader workgroup -> multiple meshlets. its not each globalInvocation (afaik)
layout(local_size_x=1) in;

#moj_import <nvidium:terrain/task_common2.glsl>

void main() {
    uint sectionId = sectionIndices.data[temporalCommandBuffer.data[gl_DrawID].w + gl_WorkGroupID.x].x + temporalCommandBuffer.data[gl_DrawID].w;

#ifdef STATISTICS_SECTIONS
    atomicAdd(statistics_buffer.data[1], 1);
#endif

    ivec4 header = sectionData.data[sectionId].header;
    ivec3 chunk = ivec3(header.xyz)>>8;
    chunk.y &= 0x1ff;
    chunk.y <<= 32-9;
    chunk.y >>= 32-9;
    chunk -= chunkPosition.xyz;

    task.transformationId = unpackRegionTransformId(regionData.data[sectionId>>8]);
    chunk -= unpackOriginOffsetId(task.transformationId);

    task.origin = vec3(chunk<<4);

    populateTasks(chunk, uint(header.w), uvec4(sectionData.data[sectionId].renderRanges));
}