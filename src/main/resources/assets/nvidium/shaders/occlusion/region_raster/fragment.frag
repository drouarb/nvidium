#version 460
#pragma optionNV(unroll all)
#define UNROLL_LOOP

#extension GL_EXT_mesh_shader : require
#extension GL_EXT_buffer_reference : require
#extension GL_EXT_shader_explicit_arithmetic_types_int8 : require
#extension GL_EXT_shader_explicit_arithmetic_types_int16 : require
#extension GL_EXT_shader_explicit_arithmetic_types_int64 : require

#moj_import <nvidium:occlusion/scene.glsl>
layout(early_fragment_tests) in;

// WE NEED TO CREATE OUR OWN gl_PrimitiveID other shaderc will set our fragment to OpCapability Geometry https://github.com/KhronosGroup/glslang/issues/4147
layout(location = 3) perprimitiveEXT flat in int PRIMITRASH;

#ifdef DEBUG
layout(location = 0) out vec4 colour;
void main() {
    uint uid = PRIMITRASH*132471+123571;
    colour = vec4(float((uid>>0)&7)/7, float((uid>>3)&7)/7, float((uid>>6)&7)/7, 1.0);
    regionVisibility.data[PRIMITRASH] = uint8_t(1);
}
#else
void main() {
    regionVisibility.data[PRIMITRASH] = uint8_t(1);
}
#endif