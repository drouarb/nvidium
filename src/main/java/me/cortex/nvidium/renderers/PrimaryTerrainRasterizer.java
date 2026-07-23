package me.cortex.nvidium.renderers;

import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.vulkan.VulkanGpuSampler;
import com.mojang.blaze3d.vulkan.VulkanGpuTextureView;
import me.cortex.nvidium.vk.buffers.VkDeviceOnlyMappedBuffer;
import me.cortex.nvidium.vk.shader.VkPipeline;
import me.cortex.nvidium.vk.shader.VkPipelineLayout;
import me.cortex.nvidium.vk.shader.VkShaderType;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRPushDescriptor;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDescriptorImageInfo;
import org.lwjgl.vulkan.VkWriteDescriptorSet;

import static org.lwjgl.vulkan.EXTMeshShader.vkCmdDrawMeshTasksIndirectEXT;
import static org.lwjgl.vulkan.VK10.*;

public class PrimaryTerrainRasterizer {
    private final VkPipeline shader;

    public PrimaryTerrainRasterizer(VkPipelineLayout layout) {
        shader = VkPipeline.make()
                .addSource(VkShaderType.TASK, Identifier.fromNamespaceAndPath("nvidium", "terrain/task.glsl"))
                .addSource(VkShaderType.MESH, Identifier.fromNamespaceAndPath("nvidium", "terrain/mesh.glsl"))
                .addSource(VkShaderType.FRAGMENT, Identifier.fromNamespaceAndPath("nvidium", "terrain/frag.frag"))
                .withLayout(layout)
                .withColorTargetState(ColorTargetState.DEFAULT)
                .withDepthTest(true)
                .withDepthWrite(true)
                .compile();
    }

    public static void bindTextures(VkCommandBuffer commandBuffer, VkPipelineLayout layout, TerrainRenderPass pass, GpuSampler terrainSampler) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorImageInfo.Buffer terrainInfo = VkDescriptorImageInfo.calloc(1, stack)
                    .sampler(((VulkanGpuSampler) terrainSampler).vkSampler())
                    .imageView(((VulkanGpuTextureView) pass.getAtlas()).vkImageView())
                    .imageLayout(VK_IMAGE_LAYOUT_GENERAL);

            VkDescriptorImageInfo.Buffer lightInfo = VkDescriptorImageInfo.calloc(1, stack)
                    .sampler(((VulkanGpuSampler) RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)).vkSampler())
                    .imageView(((VulkanGpuTextureView) Minecraft.getInstance().gameRenderer.lightmap()).vkImageView())
                    .imageLayout(VK_IMAGE_LAYOUT_GENERAL);


            VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(2, stack);
            writes.get(0)
                    .sType$Default()
                    .dstBinding(1)
                    .dstArrayElement(0)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .pImageInfo(terrainInfo);
            writes.get(1)
                    .sType$Default()
                    .dstBinding(2)
                    .dstArrayElement(0)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .pImageInfo(lightInfo);

            KHRPushDescriptor.vkCmdPushDescriptorSetKHR(
                    commandBuffer,
                    VK_PIPELINE_BIND_POINT_GRAPHICS,
                    layout.layout(),
                    0,
                    writes
            );
        }
    }

    public void raster(VkCommandBuffer commandBuffer, VkPipelineLayout layout, TerrainRenderPass pass, int regionCount, VkDeviceOnlyMappedBuffer mdiCommandBuffer, GpuSampler terrainSampler) {
        shader.bind(commandBuffer);
        bindTextures(commandBuffer, layout, pass, terrainSampler);

        vkCmdDrawMeshTasksIndirectEXT(commandBuffer, mdiCommandBuffer.getHandle(), 0, regionCount, 16);
    }

    public void delete() {
        shader.delete();
    }
}
