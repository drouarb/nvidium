package me.cortex.nvidium.vk.buffers;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanUtils;
import me.cortex.nvidium.gl.TrackedObject;
import me.cortex.nvidium.mixin.minecraft.GpuDeviceAccessor;
import me.cortex.nvidium.vk.shader.VkPipelineLayout;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.vma.Vma;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;
import java.util.function.Supplier;

import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK12.VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT;
import static org.lwjgl.vulkan.VK12.vkGetBufferDeviceAddress;

public class VkDeviceOnlyMappedBuffer extends TrackedObject implements VkBuffer {
    private final VulkanDevice vkDevice;
    private final long handle;
    private final long vmaAllocation;
    private final long deviceAddr;
    private final long size;
    Supplier<String> label;

    public VkDeviceOnlyMappedBuffer(long size, int bufferUsage, int allocFlags, Supplier<String> label) {
        this.label = label;

        vkDevice = (VulkanDevice)((GpuDeviceAccessor) RenderSystem.getDevice()).nvidium$getGpuDeviceBackend();
        this.size = size;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCreateInfo bufferCreateInfo = VkBufferCreateInfo.calloc(stack)
                    .sType$Default()
                    .size(size)
                    .usage(bufferUsage | VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE);

            VmaAllocationCreateInfo allocCreateInfo = VmaAllocationCreateInfo.calloc(stack)
                    .usage(VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE)
                    .flags(allocFlags);

            LongBuffer bufferPtr = stack.callocLong(1);
            PointerBuffer allocPtr = stack.callocPointer(1);
            int result = Vma.vmaCreateBuffer(vkDevice.vma(), bufferCreateInfo, allocCreateInfo, bufferPtr, allocPtr, null);
            VulkanUtils.crashIfFailure(vkDevice, result, "Failed to allocate VkDeviceOnlyMappedBuffer " + result);

            this.vmaAllocation = allocPtr.get(0);
            handle = bufferPtr.get(0);

            VkBufferDeviceAddressInfo addressInfo = VkBufferDeviceAddressInfo.calloc(stack)
                    .sType$Default()
                    .buffer(this.handle);

            this.deviceAddr = vkGetBufferDeviceAddress(this.vkDevice.vkDevice(), addressInfo);

            if (this.deviceAddr == MemoryUtil.NULL) {
                Vma.vmaDestroyBuffer(this.vkDevice.vma(), this.handle, this.vmaAllocation);
                throw new IllegalStateException("Failed to get buffer device address");
            }

            vkDevice.instance().debug().setObjectName(vkDevice.vkDevice(), 9, handle, label);

            System.out.println("Create VkPersistentClientMappedBuffer " + label.get() + " => 0x" + Long.toHexString(this.handle));
        }
    }

    public long getHandle() {
        return handle;
    }

    public void delete() {
        System.out.println("DELETE BUFFER " + label.get());
        //(new Exception()).printStackTrace();
        super.free0();
        Vma.vmaDestroyBuffer(vkDevice.vma(), this.handle, this.vmaAllocation);
    }

    public void bind(VkCommandBuffer commandBuffer, VkPipelineLayout layout, long size, int bindType) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorBufferInfo.Buffer bufferInfo = VkDescriptorBufferInfo.calloc(1, stack);

            bufferInfo.get(0)
                    .buffer(this.handle)
                    .offset(0)
                    .range(size);

            VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(1, stack);

            writes.get(0)
                    .sType$Default()
                    .dstBinding(0)
                    .dstArrayElement(0)
                    .descriptorCount(1)
                    .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                    .pBufferInfo(bufferInfo);

            KHRPushDescriptor.vkCmdPushDescriptorSetKHR(
                    commandBuffer,
                    bindType,
                    layout.layout(),
                    0, // descriptor set index
                    writes
            );
        }
    }

    public long getDeviceAddress() {
        return deviceAddr;
    }

    public long getSize() {
        return size;
    }

    @Override
    public void free() {
        this.delete();
    }
}
