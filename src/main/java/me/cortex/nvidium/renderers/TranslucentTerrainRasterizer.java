package me.cortex.nvidium.renderers;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.textures.GpuSampler;
import me.cortex.nvidium.vk.buffers.VkDeviceOnlyMappedBuffer;
import me.cortex.nvidium.vk.shader.VkPipeline;
import me.cortex.nvidium.vk.shader.VkPipelineLayout;
import me.cortex.nvidium.vk.shader.VkShaderType;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.minecraft.client.renderer.ShaderDefines;
import net.minecraft.resources.Identifier;
import org.lwjgl.opengl.*;
import org.lwjgl.vulkan.VkCommandBuffer;

import java.util.Optional;

import static org.lwjgl.vulkan.EXTMeshShader.vkCmdDrawMeshTasksIndirectEXT;

public class TranslucentTerrainRasterizer {
    private final VkPipeline shader;

    public TranslucentTerrainRasterizer(VkPipelineLayout layout) {
        shader = VkPipeline.make()
                .addSource(VkShaderType.TASK, Identifier.fromNamespaceAndPath("nvidium", "terrain/translucent/task.glsl"))
                .addSource(VkShaderType.MESH, Identifier.fromNamespaceAndPath("nvidium", "terrain/translucent/mesh.glsl"))
                .withShaderDefines(ShaderDefines.builder().define("TRANSLUCENT_PASS"))
                .addSource(VkShaderType.FRAGMENT, Identifier.fromNamespaceAndPath("nvidium", "terrain/frag.frag"))
                .withLayout(layout)
                .withColorTargetState(new ColorTargetState(Optional.of(BlendFunction.TRANSLUCENT), GpuFormat.RGBA8_UNORM, 0xFFFFFFFF))
                .withDepthTest(true)
                .withDepthWrite(false)
                .compile();
    }

    public void raster(VkCommandBuffer commandBuffer, VkPipelineLayout layout, TerrainRenderPass pass, int regionCount, VkDeviceOnlyMappedBuffer mdiCommandBuffer, GpuSampler terrainSampler) {
        shader.bind(commandBuffer);
        PrimaryTerrainRasterizer.bindTextures(commandBuffer, layout, pass, terrainSampler);

        vkCmdDrawMeshTasksIndirectEXT(commandBuffer, mdiCommandBuffer.getHandle(), 0, regionCount, 16);
    }

    public void delete() {
        shader.delete();
    }
}
