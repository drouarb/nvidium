package me.cortex.nvidium.util;

import com.mojang.blaze3d.buffers.GpuFence;
import com.mojang.blaze3d.vulkan.VulkanCommandEncoder;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import me.cortex.nvidium.vk.VkRenderDevice;
import me.cortex.nvidium.vk.buffers.VkBuffer;
import me.cortex.nvidium.vk.buffers.VkPersistentClientMappedBuffer;

import java.util.ArrayDeque;
import java.util.Deque;

import static me.cortex.nvidium.util.SegmentedManager.SIZE_LIMIT;
import static org.lwjgl.util.vma.Vma.VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT;

public class UploadingBufferStream {
    private final VkRenderDevice device;
    private final SegmentedManager allocationArena = new SegmentedManager();
    private final VkPersistentClientMappedBuffer uploadBuffer;

    private final Deque<UploadFrame> frames = new ArrayDeque<>();
    private final LongArrayList thisFrameAllocations = new LongArrayList();
    private final Deque<UploadData> uploadList = new ArrayDeque<>();
    private final LongArrayList flushList = new LongArrayList();

    public UploadingBufferStream(VkRenderDevice device, long size) {
        this.device = device;
        this.allocationArena.setLimit(size);
        this.uploadBuffer = device.createClientMappedBuffer(size,VK_BUFFER_USAGE_TRANSFER_SRC_BIT, VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT);
        TickableManager.register(this);
    }

    private long caddr = -1;
    private long offset = 0;
    public long upload(VkBuffer buffer, long destOffset, long size) {
        if (size > Integer.MAX_VALUE || size == 0 || size < 0) {
            throw new IllegalArgumentException();
        }
        if (destOffset < 0) {
            throw new IllegalStateException();
        }
        if (destOffset+size > buffer.getSize()) {
            throw new IllegalStateException();
        }

        long addr;
        if (this.caddr == -1 || !this.allocationArena.expand(this.caddr, (int) size)) {
            this.caddr = this.allocationArena.alloc((int) size);
            //If the upload stream is full, flush it and empty it
            if (this.caddr == SIZE_LIMIT) {
                this.commit();
                int attempts = 10;
                while (--attempts != 0 && this.caddr == SIZE_LIMIT) {
                    System.out.println("WARNING UPLOAD FORCE FLUSH ????????????????????????");
                    device.getVkDevice().createCommandEncoder().submit();
                    device.getVkDevice().graphicsQueue().waitIdle();
                    this.tick();
                    this.caddr = this.allocationArena.alloc((int) size);
                }
                if (this.caddr == SIZE_LIMIT) {
                    throw new IllegalStateException("Could not allocate memory segment big enough for upload even after force flush");
                }
            }
            this.flushList.add(this.caddr);
            this.offset = size;
            addr = this.caddr;
        } else {//Could expand the allocation so just update it
            addr = this.caddr + this.offset;
            this.offset += size;
        }

        if (this.caddr + size > this.uploadBuffer.getSize()) {
            throw new IllegalStateException();
        }

        this.uploadList.add(new UploadData(buffer, addr, destOffset, size));

        return this.uploadBuffer.clientAddress() + addr;
    }


    public void commit() {
        //First flush all the allocations and enqueue them to be freed
        {
            for (long alloc : flushList) {
                this.uploadBuffer.flush(alloc, this.allocationArena.getSize(alloc));
                this.thisFrameAllocations.add(alloc);
            }
            this.flushList.clear();
        }

        VulkanCommandEncoder commandEncoder = device.getVkDevice().createCommandEncoder();
        device.barrier(commandEncoder.commandBuffer());
        // TODO glMemoryBarrier(GL_CLIENT_MAPPED_BUFFER_BARRIER_BIT);
        //Execute all the copies
        for (var entry : this.uploadList) {
            device.copyBuffer(this.uploadBuffer, entry.target, entry.uploadOffset, entry.targetOffset, entry.size);
        }
        this.uploadList.clear();

        device.barrier(commandEncoder.commandBuffer());
        // TODO glMemoryBarrier(GL_BUFFER_UPDATE_BARRIER_BIT);

        this.caddr = -1;
        this.offset = 0;
    }

    public void tick() {
        this.commit();
        if (!this.thisFrameAllocations.isEmpty()) {
            this.frames.add(new UploadFrame(device.getVkDevice().createCommandEncoder().createFence(), new LongArrayList(this.thisFrameAllocations)));
            this.thisFrameAllocations.clear();
        }
        // TODO CHECK IF WE DO THAT HERE

        while (!this.frames.isEmpty()) {
            //Since the ordering of frames is the ordering of the gl commands if we encounter an unsignaled fence
            // all the other fences should also be unsignaled
            if (!this.frames.peek().fence.awaitCompletion(0)) {
                break;
            }
            //Release all the allocations from the frame
            var frame = this.frames.pop();
            frame.allocations.forEach(allocationArena::free);
            frame.fence.close();
        }
    }

    public void delete() {
        TickableManager.remove(this);
        this.uploadBuffer.delete();
        //this.frames.forEach(frame->frame.fence.free());
    }

    private record UploadFrame(GpuFence fence, LongArrayList allocations) {}
    private record UploadData(VkBuffer target, long uploadOffset, long targetOffset, long size) {}

}
