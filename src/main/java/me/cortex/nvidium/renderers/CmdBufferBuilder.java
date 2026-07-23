package me.cortex.nvidium.renderers;

import me.cortex.nvidium.gl.shader.Shader;
import me.cortex.nvidium.sodiumCompat.ShaderLoader;
import me.cortex.nvidium.vk.shader.VkPipeline;
import me.cortex.nvidium.vk.shader.VkPipelineLayout;
import me.cortex.nvidium.vk.shader.VkShaderType;
import net.minecraft.resources.Identifier;
import org.lwjgl.vulkan.VkCommandBuffer;

import static me.cortex.nvidium.gl.shader.ShaderType.COMPUTE;
import static org.lwjgl.opengl.GL43C.glDispatchCompute;
import static org.lwjgl.vulkan.VK10.vkCmdDispatch;

public class CmdBufferBuilder {
    private final VkPipeline shader;

    public CmdBufferBuilder(VkPipelineLayout layout) {
        shader = VkPipeline.make()
                .addSource(VkShaderType.COMPUTE, Identifier.fromNamespaceAndPath("nvidium", "occlusion/command_buffer/command_buffer_builder.comp"))
                .withLayout(layout)
                .compileCompute();
    }

    public void dispatch(VkCommandBuffer commandBuffer, int regionCount) {
        shader.bind(commandBuffer);
        vkCmdDispatch(commandBuffer, regionCount, 1, 1);
    }

    public void delete() {
        shader.delete();
    }
}