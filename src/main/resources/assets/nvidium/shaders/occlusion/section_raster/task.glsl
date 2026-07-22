#version 460
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

struct Task {
    uint _visOutBase;// The base offset for the visibility output of the shader
    uint _offset;//start offset for regions (can/should probably be a uint16 since this is just the region id << 8)
    //uint64_t bitcheck[4];//TODO: MAYBE DO THIS, each bit is whether there a section at that index, doing so is faster than pulling metadata to check if a section is valid or not
    mat4 regionTransform;
    ivec3 chunkShift;
};
taskPayloadSharedEXT Task task;

void main() {
    //TODO: see whats faster, atomicAdd (for mdic) or dispatching alot of empty calls (mdi)
    //TODO: experiment with emitting 8 workgroups with the 8th always being 0
    // doing so would enable to batch memory write 2 commands
    // thus taking 4 mem moves instead of 7

    //Emit 7 workloads per chunk
    uint cmdIdx = gl_WorkGroupID.x;
    uint transCmdIdx = (uint(regionCount) - gl_WorkGroupID.x) - 1;

    //Early exit if the region wasnt visible
    if (regionVisibility.data[gl_WorkGroupID.x] == 0) {
        EmitMeshTasksEXT(0, 0, 0);
        return;
    }

    #ifdef STATISTICS_REGIONS
        atomicAdd(statistics_buffer.data[0], 1);
    #endif

    //FIXME: It might actually be more efficent to just upload the region data straight into the ubo
    uint offset = regionIndicies.data[gl_WorkGroupID.x];
    Region data = regionData.data[offset];
    int count = unpackRegionCount(data)+1;

    //Write in order
    task._visOutBase = offset<<8;//This makes checking visibility very fast and quick in the compute shader
    task._offset = offset<<8;
    task.regionTransform = getRegionTransformation(data);

    task.chunkShift = (-chunkPosition.xyz) - unpackOriginOffsetId(unpackRegionTransformId(data));

    EmitMeshTasksEXT(count, 1, 1);
}
