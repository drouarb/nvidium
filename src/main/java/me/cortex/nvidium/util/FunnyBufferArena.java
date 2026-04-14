package me.cortex.nvidium.util;

import me.cortex.nvidium.gl.RenderDevice;
import me.cortex.nvidium.gl.buffers.DeviceOnlyMappedBuffer;
import me.cortex.nvidium.gl.buffers.IDeviceMappedBuffer;
import me.cortex.nvidium.gl.buffers.PersistentClientMappedBuffer;
import me.cortex.nvidium.renderers.CompUploader;
import org.lwjgl.system.MemoryUtil;

import static me.cortex.nvidium.util.SegmentedManager.SIZE_LIMIT;
import static org.lwjgl.opengl.ARBDirectStateAccess.glFlushMappedNamedBufferRange;
import static org.lwjgl.opengl.GL11.glFinish;
import static org.lwjgl.opengl.GL42.glMemoryBarrier;
import static org.lwjgl.opengl.GL43C.GL_SHADER_STORAGE_BARRIER_BIT;
import static org.lwjgl.opengl.GL43C.GL_SHADER_STORAGE_BUFFER;
import static org.lwjgl.opengl.GL44.GL_CLIENT_MAPPED_BUFFER_BARRIER_BIT;

public class FunnyBufferArena {
    private final RenderDevice device;

    SegmentedManager segments = new SegmentedManager();
    public final IDeviceMappedBuffer pool;
    public final IDeviceMappedBuffer vertexIndices;
    public final IDeviceMappedBuffer attributeIndices;

    private int controlIdx = 0;
    private long uploadIdx = 0;
    public final PersistentClientMappedBuffer controlBuffer;
    public final PersistentClientMappedBuffer uploadBuffer;

    public long totalQuads;
    private final int vertexFormatSize;

    private final long memory_size;

    public final CompUploader uploader = new CompUploader();

    private final long POOL_SIZE = 60_000_000;
    private final long CONTROL_SIZE = 12;
    private final long MAX_VTX = 600_000_000;
    private final long UPLOAD_ARENA_SIZE = 1_000_000; // MAX QUAD TO UPLOAD
    private final long HASHMAP_DATA_SIZE = 20; // 8 structure + 12 data

    public FunnyBufferArena(RenderDevice device, long memory, int vertexFormatSize) {
        this.device = device;
        this.vertexFormatSize = vertexFormatSize;
        this.memory_size = memory;

        controlBuffer = device.createClientMappedBuffer(32_000_000); // TODO MORE THAN ENOUGH ?
        uploadBuffer = device.createClientMappedBuffer(UPLOAD_ARENA_SIZE * vertexFormatSize * 4);

        pool = new DeviceOnlyMappedBuffer(POOL_SIZE * HASHMAP_DATA_SIZE, GL_SHADER_STORAGE_BUFFER, "Pool"); // TODO HANDLE SIZE PROPERLY

        vertexIndices = new DeviceOnlyMappedBuffer(MAX_VTX * 4, GL_SHADER_STORAGE_BUFFER, "vertexIndices"); // TODO 20M int ??
        attributeIndices = new DeviceOnlyMappedBuffer(MAX_VTX * 4, GL_SHADER_STORAGE_BUFFER, "attributeIndices"); // TODO 20M int ??

        this.segments.setLimit(MAX_VTX / 4);
        //Reserve index 0
        this.allocQuads(1);
    }

    public int allocQuads(int quadCount) {
        totalQuads += quadCount;
        int addr = (int) segments.alloc(quadCount);
        if (addr == SegmentedManager.SIZE_LIMIT) {
            return addr;
        }
        return addr;
    }

    public void free(int addr) {
        // TODO Delete comp shader
        int count = segments.free(addr);
        totalQuads -= count;
    }

    public long upload(int addr) {
        var quadCount = segments.getSize(addr);

        if (quadCount > Integer.MAX_VALUE || quadCount == 0 || quadCount < 0) {
            throw new IllegalArgumentException();
        }

        if (uploadIdx + quadCount >= UPLOAD_ARENA_SIZE) {
            throw new IllegalStateException("UPLOAD IS FULL :/");
        }
        //if (controlIdx > 0)
        //    this.commit();
        long upAddr = uploadIdx;
        uploadIdx += quadCount;

        // Populate control buffer
        System.out.println("[" + controlIdx + "] Uploading from: " + upAddr + " size: " + segments.getSize(addr) + " to: " + addr);
        var controlAddr = controlBuffer.addr + (controlIdx++ * CONTROL_SIZE);
        MemoryUtil.memPutInt(controlAddr + 0, (int) upAddr * 4); // uploadStart
        MemoryUtil.memPutInt(controlAddr + 4, (int) quadCount); // quadCount
        MemoryUtil.memPutInt(controlAddr + 8, addr * 4); // OutputIdx

        // Return ptr to data
        return uploadBuffer.addr + (upAddr * vertexFormatSize * 4);
    }

    public void commit() {
        if (controlIdx == 0) {
            // add a 0 if we didn't upload to see avg timing
            uploader.getTiming().addSample(0);
            return;
        }
        System.out.println("COMMIT " + controlIdx);
        // TODO Handle retry and wait stuffs
        System.out.println("FLUSH CONTROL " + controlIdx * CONTROL_SIZE);
        glFlushMappedNamedBufferRange(this.controlBuffer.getId(), 0, controlIdx * CONTROL_SIZE);
        System.out.println("FLUSH UPLOAD " + uploadIdx * vertexFormatSize * 4);
        glFlushMappedNamedBufferRange(this.uploadBuffer.getId(), 0, uploadIdx * vertexFormatSize * 4);
        System.out.println("DISPATCH COMPUTE UPLOAD");
        glMemoryBarrier(GL_CLIENT_MAPPED_BUFFER_BARRIER_BIT);

        pool.bind(20);
        vertexIndices.bind(21);
        attributeIndices.bind(22);
        controlBuffer.bind(23, GL_SHADER_STORAGE_BUFFER);
        uploadBuffer.bind(24, GL_SHADER_STORAGE_BUFFER);
        pool.bind(25);

        uploader.dispatch(controlIdx);
        System.out.println("DISPATCHED");
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
        System.out.println("TRIGGER FINISH");
        glFinish();
        System.out.println("FINISHED");
        controlIdx = 0;
        uploadIdx = 0;
    }

    public void delete() {
        uploadBuffer.delete();
        controlBuffer.delete();
        pool.delete();
        vertexIndices.delete();
        attributeIndices.delete();
        uploader.delete();
    }

    public boolean canReuse(int addr, int quads) {
        return this.segments.getSize(addr) == quads;
    }
}
