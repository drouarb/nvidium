package me.cortex.nvidium.vk.shader;

import org.lwjgl.util.shaderc.Shaderc;

import static org.lwjgl.vulkan.EXTMeshShader.VK_SHADER_STAGE_MESH_BIT_EXT;
import static org.lwjgl.vulkan.EXTMeshShader.VK_SHADER_STAGE_TASK_BIT_EXT;
import static org.lwjgl.vulkan.VK10.*;

public enum VkShaderType {
    VERTEX(VK_SHADER_STAGE_VERTEX_BIT, Shaderc.shaderc_glsl_vertex_shader),
    FRAGMENT(VK_SHADER_STAGE_FRAGMENT_BIT, Shaderc.shaderc_glsl_fragment_shader),
    COMPUTE(VK_SHADER_STAGE_COMPUTE_BIT, Shaderc.shaderc_glsl_compute_shader),
    MESH(VK_SHADER_STAGE_MESH_BIT_EXT, Shaderc.shaderc_glsl_mesh_shader),
    TASK(VK_SHADER_STAGE_TASK_BIT_EXT, Shaderc.shaderc_glsl_task_shader);

    public final int vkFlag;
    public final int shadercType;

    VkShaderType(int vkFlag, int shadercType) {
        this.vkFlag = vkFlag;
        this.shadercType = shadercType;
    }
}
