package me.cortex.nvidium.renderers;

import com.mojang.blaze3d.systems.CommandEncoder;
import me.cortex.nvidium.vk.shader.VkPipeline;
import me.cortex.nvidium.vk.shader.VkPipelineLayout;
import me.cortex.nvidium.vk.shader.VkShaderType;
import net.minecraft.resources.Identifier;
import org.lwjgl.vulkan.VkCommandBuffer;

import static org.lwjgl.vulkan.VK10.vkCmdDispatch;

public class CmdBufferBuilder extends Phase {
    private final VkPipeline shader;

    public CmdBufferBuilder(VkPipelineLayout layout) {
        shader = VkPipeline.make()
                .addSource(VkShaderType.COMPUTE, Identifier.fromNamespaceAndPath("nvidium", "occlusion/command_buffer/command_buffer_builder.comp"))
                .withLayout(layout)
                .compileCompute();
    }

    public void dispatch(CommandEncoder commandEncoder, VkCommandBuffer commandBuffer, int regionCount) {
        shader.bind(commandBuffer);
        timing.begin(commandEncoder);
        vkCmdDispatch(commandBuffer, regionCount, 1, 1);
        timing.end(commandEncoder);
    }

    public void delete() {
        timing.free();
        shader.delete();
    }
}