package me.cortex.nvidium.renderers;

import com.mojang.blaze3d.opengl.GlSampler;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import me.cortex.nvidium.gl.shader.Shader;
import me.cortex.nvidium.sodiumCompat.ShaderLoader;
import me.cortex.nvidium.vk.buffers.VkDeviceOnlyMappedBuffer;
import me.cortex.nvidium.vk.shader.VkPipeline;
import me.cortex.nvidium.vk.shader.VkPipelineLayout;
import me.cortex.nvidium.vk.shader.VkShaderType;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.opengl.*;
import org.lwjgl.vulkan.VkCommandBuffer;

import static me.cortex.nvidium.RenderPipeline.GL_DRAW_INDIRECT_ADDRESS_NV;
import static me.cortex.nvidium.gl.shader.ShaderType.*;
import static org.lwjgl.opengl.NVMeshShader.glMultiDrawMeshTasksIndirectNV;
import static org.lwjgl.opengl.NVVertexBufferUnifiedMemory.glBufferAddressRangeNV;
import static org.lwjgl.vulkan.EXTMeshShader.vkCmdDrawMeshTasksIndirectEXT;

public class TemporalTerrainRasterizer {
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

    public void raster(VkCommandBuffer commandBuffer, VkPipelineLayout layout, TerrainRenderPass pass, int regionCount, VkDeviceOnlyMappedBuffer mdiCommandBuffer, GpuSampler terrainSampler) {
        shader.bind(commandBuffer);
        PrimaryTerrainRasterizer.bindTextures(commandBuffer, layout, pass, terrainSampler);

        vkCmdDrawMeshTasksIndirectEXT(commandBuffer, mdiCommandBuffer.getHandle(), 0, regionCount, 16);
    }

    public void delete() {
        shader.delete();
    }
}
