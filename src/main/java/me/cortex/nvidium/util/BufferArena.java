package me.cortex.nvidium.util;

import me.cortex.nvidium.Nvidium;
import me.cortex.nvidium.vk.VkRenderDevice;
import me.cortex.nvidium.vk.buffers.VkBuffer;
import me.cortex.nvidium.vk.buffers.VkSparseAddressableBuffer;

import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT;

//TODO: make it not remove and immediately deallocate the sparse pages, wait until the end of a frame to deallocate
// since committing pages is not cheap
public class BufferArena {
    SegmentedManager segments = new SegmentedManager();
    public final VkBuffer buffer;
    private long totalQuads;
    private final int vertexFormatSize;

    private final long memory_size;


    public BufferArena(VkRenderDevice device, long memory, int vertexFormatSize) {
        this.vertexFormatSize = vertexFormatSize;
        this.memory_size = memory;
        if (Nvidium.SUPPORTS_PERSISTENT_SPARSE_ADDRESSABLE_BUFFER) {
            buffer = device.createSparseBuffer(80000000000L, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT, 0,
                    () -> "TerrainArena");
        } else {
            buffer = device.createDeviceOnlyMappedBuffer(memory, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT, 0,
                    () -> "TerrainArena");
            this.segments.setLimit(memory/(4L*this.vertexFormatSize));
        }
        //Reserve index 0
        this.allocQuads(1);
    }

    public int allocQuads(int quadCount) {
        totalQuads += quadCount;
        int addr = (int) segments.alloc(quadCount);
        if (addr == SegmentedManager.SIZE_LIMIT) {
            return addr;
        }

        if (buffer instanceof VkSparseAddressableBuffer psab) {
            psab.ensureAllocated(Integer.toUnsignedLong(addr) * 4L * vertexFormatSize, quadCount * 4L * vertexFormatSize);
        }
        return addr;
    }

    public void free(int addr) {
        int count = segments.free(addr);
        totalQuads -= count;
        if (buffer instanceof VkSparseAddressableBuffer psab) {
            psab.deallocate(Integer.toUnsignedLong(addr) * 4L * vertexFormatSize, count * 4L * vertexFormatSize);
        }
    }

    public long upload(UploadingBufferStream stream, int addr) {
        return stream.upload(buffer, Integer.toUnsignedLong(addr)*4L*vertexFormatSize, (int) segments.getSize(addr)*4*vertexFormatSize);
    }

    public void delete() {
        buffer.delete();
    }

    public int getAllocatedMB() {
        if (buffer instanceof VkSparseAddressableBuffer psab) {
            return (int) ((psab.getPagesCommitted() * VkSparseAddressableBuffer.PAGE_SIZE) / (1024 * 1024));
        } else {
            return (int) (memory_size/(1024*1024));
        }
    }

    public int getUsedMB() {
        return (int) ((totalQuads * vertexFormatSize * 4)/(1024*1024));
    }

    public long getMemoryUsed() {
        if (buffer instanceof VkSparseAddressableBuffer psab) {
            return (psab.getPagesCommitted() * VkSparseAddressableBuffer.PAGE_SIZE);
        } else {
            return memory_size;
        }
    }

    public float getFragmentation() {
        long expected = totalQuads * vertexFormatSize * 4;
        return (float) ((double)expected/getMemoryUsed());
    }

    public boolean canReuse(int addr, int quads) {
        return this.segments.getSize(addr) == quads;
    }
}
