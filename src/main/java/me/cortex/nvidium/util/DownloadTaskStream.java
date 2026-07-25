package me.cortex.nvidium.util;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import me.cortex.nvidium.vk.VkRenderDevice;
import me.cortex.nvidium.vk.buffers.VkBuffer;
import me.cortex.nvidium.vk.buffers.VkPersistentClientMappedBuffer;

import static org.lwjgl.util.vma.Vma.VMA_ALLOCATION_CREATE_HOST_ACCESS_RANDOM_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT;

//Download stream from gpu to cpu
public class DownloadTaskStream {
    public interface IDownloadFinishedCallback {void accept(long addr);}

    private record Download(long addr, IDownloadFinishedCallback callback) {}

    private final SegmentedManager allocator = new SegmentedManager();
    private final VkRenderDevice device;
    private VkPersistentClientMappedBuffer buffer;

    private int cidx;
    private final ObjectList<Download>[] allocations;
    public DownloadTaskStream(VkRenderDevice device, int frames, long size) {
        this.device = device;
        allocator.setLimit(size);
        buffer = device.createClientMappedBuffer(size, VK_BUFFER_USAGE_TRANSFER_DST_BIT, VMA_ALLOCATION_CREATE_HOST_ACCESS_RANDOM_BIT, () -> "DownloadStagingBuffer");
        TickableManager.register(this);
        allocations = new ObjectList[frames];
        for (int i = 0; i < frames; i++) {
            allocations[i] = new ObjectArrayList<>();
        }
    }

    public void download(VkBuffer source, long offset, int size, IDownloadFinishedCallback callback) {
        long addr = allocator.alloc(size);
        device.copyBuffer(source, buffer, offset, addr, size);
        allocations[cidx].add(new Download(addr, callback));
    }

    void tick() {
        cidx = (cidx+1)%allocations.length;
        for (var download : allocations[cidx]) {
            download.callback.accept(download.addr + buffer.clientAddress());
            allocator.free(download.addr);
        }
        allocations[cidx].clear();
    }

    public void delete() {
        TickableManager.remove(this);
        buffer.delete();
    }
}
