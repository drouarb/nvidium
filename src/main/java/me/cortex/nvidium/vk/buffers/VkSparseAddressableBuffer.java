package me.cortex.nvidium.vk.buffers;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanUtils;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import me.cortex.nvidium.gl.TrackedObject;
import me.cortex.nvidium.mixin.minecraft.GpuDeviceAccessor;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.vma.Vma;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.util.vma.VmaAllocationInfo;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;
import java.util.function.Supplier;

import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK12.VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT;
import static org.lwjgl.vulkan.VK12.vkGetBufferDeviceAddress;

public class VkSparseAddressableBuffer extends TrackedObject implements VkBuffer {
    // Old comment, true on OGL, is it true on VK ??
    //The reason the page size is now 1mb is cause the nv driver doesnt defrag the sparse allocations easily
    // meaning smaller pages result in more fragmented memory and not happy for the driver
    // 1mb seems to work well
    public static final long PAGE_SIZE = 1<<20;

    private final VulkanDevice vkDevice;
    private final long handle;
    private final long deviceAddr;
    private final long size;
    private final long alignment;
    private final int memoryTypeBits;
    Supplier<String> label;

    private final Int2IntOpenHashMap allocationCount = new Int2IntOpenHashMap();
    private final Int2LongOpenHashMap pageMap = new Int2LongOpenHashMap();

    public VkSparseAddressableBuffer(long size, int bufferUsage, int allocFlags, Supplier<String> label) {
        this.label = label;

        vkDevice = (VulkanDevice)((GpuDeviceAccessor) RenderSystem.getDevice()).nvidium$getGpuDeviceBackend();
        this.size = alignUp(size, PAGE_SIZE);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCreateInfo bufferCreateInfo = VkBufferCreateInfo.calloc(stack)
                    .sType$Default()
                    .size(this.size)
                    .usage(bufferUsage | VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT)
                    .flags(VK_BUFFER_CREATE_SPARSE_BINDING_BIT | VK_BUFFER_CREATE_SPARSE_RESIDENCY_BIT)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE);


            LongBuffer bufferPtr = stack.callocLong(1);
            int result = vkCreateBuffer(vkDevice.vkDevice(), bufferCreateInfo, null, bufferPtr);
            VulkanUtils.crashIfFailure(vkDevice, result, "Failed to create sparse buffer");
            handle = bufferPtr.get(0);

            VkBufferDeviceAddressInfo addressInfo = VkBufferDeviceAddressInfo.calloc(stack)
                    .sType$Default()
                    .buffer(this.handle);

            this.deviceAddr = vkGetBufferDeviceAddress(this.vkDevice.vkDevice(), addressInfo);

            if (this.deviceAddr == MemoryUtil.NULL) {
                vkDestroyBuffer(vkDevice.vkDevice(), this.handle, null);
                throw new IllegalStateException("Failed to get buffer device address");
            }

            VkMemoryRequirements req = VkMemoryRequirements.calloc(stack);
            vkGetBufferMemoryRequirements(vkDevice.vkDevice(), handle, req);

            this.alignment = req.alignment();
            this.memoryTypeBits = req.memoryTypeBits();

            vkDevice.instance().debug().setObjectName(vkDevice.vkDevice(), 9, handle, label);

            System.out.println("Create VkSparseAddressableBuffer " + label.get() + " => 0x" + Long.toHexString(this.handle));
        }
    }

    public void ensureAllocated(long addr, long size) {
        int pstart = (int) (addr/PAGE_SIZE);
        int pend   = (int) ((addr+size+PAGE_SIZE-1)/PAGE_SIZE);
        allocatePages(pstart, pend-pstart);
    }

    public void deallocate(long addr, long size) {
        int pstart = (int) (addr/PAGE_SIZE);
        int pend   = (int) ((addr+size+PAGE_SIZE-1)/PAGE_SIZE);
        deallocatePages(pstart, pend-pstart);
    }

    public int getPagesCommitted() {
        return allocationCount.size();
    }

    private void allocatePages(int page, int pageCount) {
        int newPageCount = 0;
        int[] pagesTodo = new int[pageCount];

        for (int i = 0; i < pageCount; i++) {
            int oldCount = allocationCount.addTo(page+i, 1);
            if (oldCount == 0) {
                pagesTodo[newPageCount++] = page+i;
            }
        }

        if (newPageCount == 0)
            return;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer allocations = stack.mallocPointer(newPageCount);

            VkMemoryRequirements requirements = VkMemoryRequirements.calloc(stack)
                    .size(PAGE_SIZE)
                    .alignment(alignment)
                    .memoryTypeBits(memoryTypeBits);

            VmaAllocationCreateInfo allocInfo = VmaAllocationCreateInfo.calloc(stack)
                    .usage(VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);

            VmaAllocationInfo.Buffer allocInfos = VmaAllocationInfo.calloc(newPageCount, stack);

            int result = Vma.vmaAllocateMemoryPages(vkDevice.vma(), requirements, allocInfo, allocations, allocInfos);
            VulkanUtils.crashIfFailure(vkDevice, result, "VMA failed sparse allocation pages, this is vvvv bad");

            VkSparseMemoryBind.Buffer binds = VkSparseMemoryBind.calloc(newPageCount, stack);
            for (int i = 0; i < newPageCount; i++) {
                binds.get(i)
                        .resourceOffset((long)pagesTodo[i] * PAGE_SIZE)
                        .size(PAGE_SIZE)
                        .memory(allocInfos.get(i).deviceMemory())
                        .memoryOffset(allocInfos.get(i).offset());

                pageMap.put(pagesTodo[i], allocations.get(i));
            }

            VkSparseBufferMemoryBindInfo.Buffer bufferBinds = VkSparseBufferMemoryBindInfo.calloc(1, stack);
            bufferBinds.get(0)
                    .buffer(handle)
                    .pBinds(binds);

            VkBindSparseInfo.Buffer bindInfo = VkBindSparseInfo.calloc(1, stack);

            bindInfo.get(0)
                    .sType$Default()
                    .pBufferBinds(bufferBinds);

            result = vkQueueBindSparse(vkDevice.graphicsQueue().vkQueue(), bindInfo, VK_NULL_HANDLE);
            VulkanUtils.crashIfFailure(vkDevice, result, "Sparse pages failed to bind, this is vvvv bad");
        }
    }

    private void deallocatePages(int page, int pageCount) {
        int deallocatePageCount = 0;
        int[] pagesToUnbind = new int[pageCount];
        long[] pagesToFree = new long[pageCount];

        for (int i = 0; i < pageCount; i++) {
            int newCount = allocationCount.get(i+page) - 1;
            if (newCount != 0) {
                allocationCount.put(i+page, newCount);
            } else {
                allocationCount.remove(i+page);
                pagesToUnbind[deallocatePageCount] = page+i;
                pagesToFree[deallocatePageCount++] = pageMap.remove(page+i);
            }
        }

        if (deallocatePageCount == 0)
            return;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSparseMemoryBind.Buffer binds = VkSparseMemoryBind.calloc(deallocatePageCount, stack);
            for (int i = 0; i < deallocatePageCount; i++) {
                binds.get(i)
                        .resourceOffset((long)pagesToUnbind[i] * PAGE_SIZE)
                        .size(PAGE_SIZE)
                        .memory(VK_NULL_HANDLE);
            }

            VkSparseBufferMemoryBindInfo.Buffer bufferBinds = VkSparseBufferMemoryBindInfo.calloc(1, stack);
            bufferBinds.get(0)
                    .buffer(handle)
                    .pBinds(binds);

            VkBindSparseInfo.Buffer bindInfo = VkBindSparseInfo.calloc(1, stack);

            bindInfo.get(0)
                    .sType$Default()
                    .pBufferBinds(bufferBinds);

            int result = vkQueueBindSparse(vkDevice.graphicsQueue().vkQueue(), bindInfo, VK_NULL_HANDLE);
            VulkanUtils.crashIfFailure(vkDevice, result, "Sparse pages failed to unbind, this is vvvv bad");

            // TODO THIS IS WRONG, WE SHOULDN'T FREE UNTIL SPARSE FINISHED UNBINDING
            PointerBuffer allocations = stack.mallocPointer(deallocatePageCount);
            for (int i = 0; i < deallocatePageCount; i++) {
                allocations.put(i, pagesToFree[i]);
            }
            Vma.vmaFreeMemoryPages(vkDevice.vma(), allocations);
        }
    }

    public long getHandle() {
        return handle;
    }

    public void delete() {
        System.out.println("DELETE BUFFER " + label.get());
        super.free0();
        vkDevice.graphicsQueue().waitIdle();
        vkDestroyBuffer(vkDevice.vkDevice(), this.handle, null);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer allocations = stack.mallocPointer(pageMap.size());
            int i = 0;
            for (long allocation : pageMap.values()) {
                allocations.put(i++, allocation);
            }
            Vma.vmaFreeMemoryPages(vkDevice.vma(), allocations);
        }
    }

    @Override
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

    public static long alignUp(long number, long alignment) {
        long delta = number % alignment;
        return delta == 0?number: number + (alignment - delta);
    }
}
