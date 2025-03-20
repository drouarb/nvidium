#extension GL_NV_mesh_shader : require

#define EMIT_TASKS(x) gl_TaskCountNV = (x)
#define SET_MESH_OUTPUTS(v, p) gl_MeshVerticesCountNV = (v), gl_PrimitiveCountNV = (p)