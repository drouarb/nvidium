#version 460

#extension GL_ARB_shading_language_include : enable
#pragma optionNV(unroll all)
#extension GL_NV_mesh_shader : require
#extension GL_NV_gpu_shader5 : require

#import <nvidium:occlusion/scene.glsl>
#import <nvidium:terrain/task_common2.glsl>

layout(local_size_x=1) in;

bool shouldRenderVisible(uint sectionId) {
    return (sectionVisibility[sectionId]&uint8_t(1)) != uint8_t(0);
}

void main() {
    uint sectionId = gl_WorkGroupID.x;

    if (!shouldRenderVisible(sectionId)) {
        //Early exit if the section isnt visible
        gl_TaskCountNV = 0;
        return;
    }

    ivec4 header = sectionData[sectionId].header;

    ivec3 chunk = ivec3(header.xyz)>>8;
    chunk.y &= 0x1ff;
    chunk.y <<= 32-9;
    chunk.y >>= 32-9;
    chunk -= chunkPosition.xyz;
    transformationId = unpackRegionTransformId(regionData[sectionId>>8]);
    chunk -= unpackOriginOffsetId(transformationId);

    origin = vec3(chunk<<4);

    populateTasks(chunk, sectionData[sectionId].meshletOffset, uvec4(sectionData[sectionId].renderRanges));
}