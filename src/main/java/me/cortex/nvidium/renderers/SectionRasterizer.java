package me.cortex.nvidium.renderers;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import me.cortex.nvidium.vk.shader.VkPipeline;
import me.cortex.nvidium.vk.shader.VkPipelineLayout;
import me.cortex.nvidium.vk.shader.VkShaderType;
import net.minecraft.resources.Identifier;
import org.lwjgl.vulkan.VkCommandBuffer;

import java.util.Optional;

import static org.lwjgl.vulkan.EXTMeshShader.vkCmdDrawMeshTasksEXT;

public class SectionRasterizer{

    private final VkPipeline shader;

    public SectionRasterizer(int debug, boolean writeDepth, VkPipelineLayout layout) {
        shader = VkPipeline.make()
                .addSource(VkShaderType.TASK, Identifier.fromNamespaceAndPath("nvidium", "occlusion/section_raster/task.glsl"))
                .addSource(VkShaderType.MESH, Identifier.fromNamespaceAndPath("nvidium", "occlusion/section_raster/mesh.glsl"))
                .addSource(VkShaderType.FRAGMENT, Identifier.fromNamespaceAndPath("nvidium", "occlusion/section_raster/fragment.glsl"))
                .withLayout(layout)
                .withColorTargetState(debug == 2 ?
                        ColorTargetState.DEFAULT :
                        new ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_NONE)
                )
                .withDepthTest(true)
                .withDepthWrite(debug == 2 && writeDepth)
                .withRepresentativeFragmentTest(false)
                .compile();
    }

    public void raster(VkCommandBuffer commandBuffer, int regionCount) {
        shader.bind(commandBuffer);
        vkCmdDrawMeshTasksEXT(commandBuffer, regionCount, 1, 1);
    }

    public void delete() {
        shader.delete();
    }
}
