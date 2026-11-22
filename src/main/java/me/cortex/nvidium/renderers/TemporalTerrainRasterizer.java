package me.cortex.nvidium.renderers;

import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.textures.GpuSampler;
import me.cortex.nvidium.vk.buffers.VkDeviceOnlyMappedBuffer;
import me.cortex.nvidium.vk.shader.VkPipeline;
import me.cortex.nvidium.vk.shader.VkPipelineLayout;
import me.cortex.nvidium.vk.shader.VkShaderType;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.minecraft.resources.Identifier;
import org.lwjgl.vulkan.VkCommandBuffer;

import static org.lwjgl.vulkan.EXTMeshShader.vkCmdDrawMeshTasksIndirectEXT;

public class TemporalTerrainRasterizer extends Phase {
    private final VkPipeline shader;

    public TemporalTerrainRasterizer(VkPipelineLayout layout) {
        shader = VkPipeline.make()
                .addSource(VkShaderType.TASK, Identifier.fromNamespaceAndPath("nvidium", "terrain/temporal_task.glsl"))
                .addSource(VkShaderType.MESH, Identifier.fromNamespaceAndPath("nvidium", "terrain/mesh.glsl"))
                .addSource(VkShaderType.FRAGMENT, Identifier.fromNamespaceAndPath("nvidium", "terrain/frag.frag"))
                .withLayout(layout)
                .withColorTargetState(ColorTargetState.DEFAULT)
                .withDepthTest(true)
                .withDepthWrite(true)
                .compile();
    }

    public void raster(CommandEncoder commandEncoder, VkCommandBuffer commandBuffer, VkPipelineLayout layout, TerrainRenderPass pass, int regionCount, VkDeviceOnlyMappedBuffer mdiCommandBuffer, GpuSampler terrainSampler) {
        shader.bind(commandBuffer);
        PrimaryTerrainRasterizer.bindTextures(commandBuffer, layout, pass, terrainSampler);

        timing.begin(commandEncoder);
        vkCmdDrawMeshTasksIndirectEXT(commandBuffer, mdiCommandBuffer.getHandle(), 0, regionCount, 16);
        timing.end(commandEncoder);
    }

    public void delete() {
        timing.free();
        shader.delete();
    }
}
