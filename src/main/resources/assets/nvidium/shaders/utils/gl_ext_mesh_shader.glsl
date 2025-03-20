#extension GL_ARB_gpu_shader_int64 : require
#extension GL_EXT_mesh_shader : require

#define taskNV taskPayloadSharedEXT

#define EMIT_TASKS(x) EmitMeshTasksEXT(x, 1, 1)