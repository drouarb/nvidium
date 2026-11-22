package me.cortex.nvidium.renderers;

import com.mojang.blaze3d.systems.CommandEncoder;
import me.cortex.nvidium.vk.shader.VkPipeline;
import me.cortex.nvidium.vk.shader.VkPipelineLayout;
import me.cortex.nvidium.vk.shader.VkShaderType;
import net.minecraft.resources.Identifier;
import org.lwjgl.vulkan.VkCommandBuffer;

import static org.lwjgl.vulkan.VK10.vkCmdDispatch;

public class SortRegionSectionPhase extends Phase {
    private final VkPipeline shader;

    public SortRegionSectionPhase(VkPipelineLayout layout) {
        shader = VkPipeline.make()
                .addSource(VkShaderType.COMPUTE, Identifier.fromNamespaceAndPath("nvidium", "sorting/region_section_sorter.comp"))
                .withLayout(layout)
                .compileCompute();
    }

    public void dispatch(CommandEncoder commandEncoder, VkCommandBuffer commandBuffer, int sortingRegionCount) {
        shader.bind(commandBuffer);
        timing.begin(commandEncoder);
        vkCmdDispatch(commandBuffer, sortingRegionCount, 1, 1);
        timing.end(commandEncoder);
    }

    public void delete() {
        timing.free();
        shader.delete();
    }
}
