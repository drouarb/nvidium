package me.cortex.nvidium.vk;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vulkan.VulkanCommandEncoder;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import me.cortex.nvidium.gl.buffers.*;
import me.cortex.nvidium.mixin.minecraft.GpuDeviceAccessor;
import me.cortex.nvidium.vk.buffers.VkBuffer;
import me.cortex.nvidium.vk.buffers.VkDeviceOnlyMappedBuffer;
import me.cortex.nvidium.vk.buffers.VkPersistentClientMappedBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.util.function.Supplier;

import static org.lwjgl.vulkan.VK10.vkCmdCopyBuffer;
import static org.lwjgl.vulkan.VK13.*;

public class VkRenderDevice {
    private final VulkanDevice vkDevice;

    public VkRenderDevice() {
        vkDevice = (VulkanDevice)((GpuDeviceAccessor) RenderSystem.getDevice()).nvidium$getGpuDeviceBackend();
    }

    public VulkanDevice getVkDevice() {
        return vkDevice;
    }

    public VkPersistentClientMappedBuffer createClientMappedBuffer(long size, int bufferUsage, int allocFlags, Supplier<String> label) {
        return new VkPersistentClientMappedBuffer(size, bufferUsage, allocFlags, label);
    }

    public void barrier(VkCommandBuffer commandBuffer) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // TODO BETTER BARRIER
            VkMemoryBarrier2.Buffer memoryBarrier = VkMemoryBarrier2.calloc(1, stack).sType$Default();
            memoryBarrier.srcStageMask(VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT);
            memoryBarrier.srcAccessMask(VK_ACCESS_2_MEMORY_READ_BIT | VK_ACCESS_2_MEMORY_WRITE_BIT);
            memoryBarrier.dstStageMask(VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT);
            memoryBarrier.dstAccessMask(VK_ACCESS_2_MEMORY_READ_BIT | VK_ACCESS_2_MEMORY_WRITE_BIT);
            VkDependencyInfo depInfo = VkDependencyInfo.calloc(stack).sType$Default();
            depInfo.pMemoryBarriers(memoryBarrier);
            KHRSynchronization2.vkCmdPipelineBarrier2KHR(commandBuffer, depInfo);
        }
    }

    public void copyBuffer(VkBuffer src, VkBuffer dst, long srcOffset, long dstOffset, long size) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VulkanCommandEncoder cmdEncoder = vkDevice.createCommandEncoder();

            VkBufferCopy.Buffer copyRegion = VkBufferCopy.calloc(1, stack)
                    .srcOffset(srcOffset)
                    .dstOffset(dstOffset)
                    .size(size);

            vkCmdCopyBuffer(
                    cmdEncoder.commandBuffer(),
                    src.getHandle(),
                    dst.getHandle(),
                    copyRegion
            );
        }
    }

    /*
    public PersistentSparseAddressableBuffer createSparseBuffer(long totalSize) {
        return new PersistentSparseAddressableBuffer(totalSize);
    }*/

    public VkDeviceOnlyMappedBuffer createDeviceOnlyMappedBuffer(long size, int bufferUsage, int allocFlags, Supplier<String> label) {
        return new VkDeviceOnlyMappedBuffer(size, bufferUsage, allocFlags, label);
    }
}
