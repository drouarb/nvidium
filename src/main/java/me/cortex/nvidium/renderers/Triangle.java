package me.cortex.nvidium.renderers;

import com.mojang.blaze3d.pipeline.ColorTargetState;
import me.cortex.nvidium.vk.shader.VkPipeline;
import me.cortex.nvidium.vk.shader.VkShaderType;
import net.minecraft.resources.Identifier;
import org.lwjgl.vulkan.VkCommandBuffer;

import static org.lwjgl.vulkan.EXTMeshShader.vkCmdDrawMeshTasksEXT;

public class Triangle {
    private final VkPipeline shader = VkPipeline.make()
            .addSource(VkShaderType.MESH, Identifier.fromNamespaceAndPath("nvidium", "dummy/mesh.glsl"))
            .addSource(VkShaderType.FRAGMENT, Identifier.fromNamespaceAndPath("nvidium", "dummy/frag.glsl"))
            .withColorTargetState(ColorTargetState.DEFAULT)
            .compile();

    public void raster(VkCommandBuffer commandBuffer) {
        shader.bind(commandBuffer);
        vkCmdDrawMeshTasksEXT(commandBuffer, 1, 1, 1);
    }
}
