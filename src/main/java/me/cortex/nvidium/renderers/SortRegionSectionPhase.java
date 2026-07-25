package me.cortex.nvidium.renderers;

import me.cortex.nvidium.vk.shader.VkPipeline;
import me.cortex.nvidium.vk.shader.VkPipelineLayout;
import me.cortex.nvidium.vk.shader.VkShaderType;
import net.minecraft.resources.Identifier;
import org.lwjgl.vulkan.VkCommandBuffer;

import static org.lwjgl.vulkan.VK10.vkCmdDispatch;

public class SortRegionSectionPhase {
    private final VkPipeline shader;

    public SortRegionSectionPhase(VkPipelineLayout layout) {
        shader = VkPipeline.make()
                .addSource(VkShaderType.COMPUTE, Identifier.fromNamespaceAndPath("nvidium", "sorting/region_section_sorter.comp"))
                .withLayout(layout)
                .compileCompute();
    }

    public void dispatch(VkCommandBuffer commandBuffer, int sortingRegionCount) {
        shader.bind(commandBuffer);
        vkCmdDispatch(commandBuffer, sortingRegionCount, 1, 1);
    }

    public void delete() {
        shader.delete();
    }
}
