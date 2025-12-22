#version 460
#extension GL_ARB_shading_language_include : enable
#extension GL_ARB_gpu_shader_int64 : require
#pragma optionNV(unroll all)
#define UNROLL_LOOP
#import <nvidium:utils/mesh_wrapper.glsl>

#import <nvidium:occlusion/scene.glsl>
layout(early_fragment_tests) in;

layout(std430, binding=5) buffer sectionVisibilityBuffer {
    uint sectionVisibility[];
};

layout(std430, binding=6) buffer terrainCommandBufferBuffer {
    uvec4 terrainCommandBuffer[];
};

layout(std430, binding=7) buffer translucencyCommandBufferBuffer {
    uvec4 translucencyCommandBuffer[];
};

layout(location = 3) perprimitiveEXT in int PRIMITRASH;
layout(location = 4) perprimitiveEXT in uint cmdIdxStore;

#ifdef DEBUG
layout(location = 0) out vec4 colour;
#endif

void main() {
#ifdef DEBUG
    uint uid = bitfieldReverse(PRIMITRASH*132471+123571);
    colour = vec4(float((uid>>0)&7u)/7, float((uid>>3)&7u)/7, float((uid>>6)&7u)/7, 1.0);
#endif

    bool isFirst = !bool(atomicOr(sectionVisibility[PRIMITRASH>>8], (PRIMITRASH & 0xFF) | 1 << 8) & (1 << 8));
    if (isFirst) {
        // We count one more section to render
        uint workId = atomicAdd(terrainCommandBuffer[cmdIdxStore].x, 1);
        atomicAdd(translucencyCommandBuffer[(uint(regionCount) - cmdIdxStore) - 1].x, 1);

        // We hijack sectionVisibility to map our sections
        atomicOr(sectionVisibility[((PRIMITRASH >> 16) << 8) + workId], ((PRIMITRASH >> 8) & 0xFF) << 16);
    }
}