#version 460
#extension GL_ARB_shading_language_include : enable
#extension GL_ARB_gpu_shader_int64 : require
#pragma optionNV(unroll all)
#define UNROLL_LOOP
#import <nvidium:utils/mesh_wrapper.glsl>

#import <nvidium:occlusion/scene.glsl>
layout(early_fragment_tests) in;

layout(std430, binding=4) writeonly buffer regionVisibilityBuffer {
    uint regionVisibility[];
};

layout(location = 3) perprimitiveEXT in int PRIMITRASH;

#ifdef DEBUG
layout(location = 0) out vec4 colour;
void main() {
    uint uid = gl_PrimitiveID*132471+123571;
    colour = vec4(float((uid>>0)&7)/7, float((uid>>3)&7)/7, float((uid>>6)&7)/7, 1.0);
    regionVisibility[gl_PrimitiveID] = 1;
}
#else
void main() {
    regionVisibility[gl_PrimitiveID] = 1;
}
#endif