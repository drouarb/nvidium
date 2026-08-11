package me.cortex.nvidium.vk.buffers;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanUtils;
import me.cortex.nvidium.gl.TrackedObject;
import me.cortex.nvidium.mixin.minecraft.GpuDeviceAccessor;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.vma.Vma;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.util.vma.VmaAllocationInfo;
import org.lwjgl.vulkan.VkBufferCreateInfo;

import java.nio.LongBuffer;
import java.util.function.Supplier;

import static org.lwjgl.util.vma.Vma.VMA_ALLOCATION_CREATE_MAPPED_BIT;
import static org.lwjgl.util.vma.Vma.VMA_MEMORY_USAGE_AUTO;
import static org.lwjgl.vulkan.VK10.VK_SHARING_MODE_EXCLUSIVE;

public class VkPersistentClientMappedBuffer extends TrackedObject implements VkBuffer {
    private final VulkanDevice vkDevice;
    private final long handle;
    private final long vmaAllocation;
    private final long addr;
    private final long size;

    public VkPersistentClientMappedBuffer(long size, int bufferUsage, int allocFlags, Supplier<String> label) {
        vkDevice = (VulkanDevice)((GpuDeviceAccessor)RenderSystem.getDevice()).nvidium$getGpuDeviceBackend();
        this.size = size;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCreateInfo bufferCreateInfo = VkBufferCreateInfo.calloc(stack)
                    .sType$Default()
                    .size(size)
                    .usage(bufferUsage)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE);

            VmaAllocationCreateInfo allocCreateInfo = VmaAllocationCreateInfo.calloc(stack)
                    .usage(VMA_MEMORY_USAGE_AUTO)
                    .flags(allocFlags | VMA_ALLOCATION_CREATE_MAPPED_BIT);

            VmaAllocationInfo allocInfo = VmaAllocationInfo.calloc(stack);

            LongBuffer bufferPtr = stack.callocLong(1);
            PointerBuffer allocPtr = stack.callocPointer(1);
            int result = Vma.vmaCreateBuffer(vkDevice.vma(), bufferCreateInfo, allocCreateInfo, bufferPtr, allocPtr, allocInfo);
            VulkanUtils.crashIfFailure(vkDevice, result, "Failed to allocate VkBuffer");

            this.vmaAllocation = allocPtr.get(0);
            handle = bufferPtr.get(0);
            addr = allocInfo.pMappedData();

            if (addr == MemoryUtil.NULL) {
                Vma.vmaDestroyBuffer(vkDevice.vma(), handle, this.vmaAllocation);
                throw new IllegalStateException("VMA allocation was not persistently mapped");
            }

            vkDevice.instance().debug().setObjectName(vkDevice.vkDevice(), 9, handle, label);
        }
    }

    public long getHandle() {
        return handle;
    }

    public void flush(long offset, long size) {
        Vma.vmaFlushAllocation(vkDevice.vma(), this.vmaAllocation, offset, size);
    }

    public void invalidate(long offset, long size) {
        Vma.vmaInvalidateAllocation(vkDevice.vma(), this.vmaAllocation, offset, size);
    }

    public void delete() {
        super.free0();
        vkDevice.graphicsQueue().waitIdle();
        Vma.vmaDestroyBuffer(
                vkDevice.vma(),
                this.handle,
                this.vmaAllocation
        );
    }

    public long clientAddress() {
        return addr;
    }

    public long getSize() {
        return size;
    }

    @Override
    public void free() {
        this.delete();
    }

    @Override
    public long getDeviceAddress() {
        throw new IllegalStateException("No device address on client mapped buffer");
    }
}
