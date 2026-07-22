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

public class RegionRasterizer {
    private final VkPipeline shader;

    public RegionRasterizer(int debug, boolean writeDepth, VkPipelineLayout layout) {
        shader = VkPipeline.make()
                .addSource(VkShaderType.MESH, Identifier.fromNamespaceAndPath("nvidium", "occlusion/region_raster/mesh.glsl"))
                .addSource(VkShaderType.FRAGMENT, Identifier.fromNamespaceAndPath("nvidium", "occlusion/region_raster/fragment.frag"))
                .withLayout(layout)
                .withColorTargetState(debug == 1 ?
                        ColorTargetState.DEFAULT :
                        new ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_NONE)
                )
                .withDepthTest(true)
                .withDepthWrite(debug == 1 && writeDepth)
                .withRepresentativeFragmentTest(debug == 0)
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
