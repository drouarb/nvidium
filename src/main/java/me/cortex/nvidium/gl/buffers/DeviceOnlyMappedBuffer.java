package me.cortex.nvidium.gl.buffers;


import me.cortex.nvidium.gl.GlObject;

import static org.lwjgl.opengl.ARBDirectStateAccess.glCreateBuffers;
import static org.lwjgl.opengl.ARBDirectStateAccess.glNamedBufferStorage;
import static org.lwjgl.opengl.GL15C.GL_READ_WRITE;
import static org.lwjgl.opengl.GL15C.glDeleteBuffers;
import static org.lwjgl.opengl.GL30.glBindBufferBase;
import static org.lwjgl.opengl.NVShaderBufferLoad.*;
import static org.lwjgl.opengl.NVUniformBufferUnifiedMemory.GL_UNIFORM_BUFFER_ADDRESS_NV;
import static org.lwjgl.opengl.NVVertexBufferUnifiedMemory.glBufferAddressRangeNV;

public class DeviceOnlyMappedBuffer extends GlObject implements IDeviceMappedBuffer {
    public final long size;
    public final long addr;
    public final int type;

    public DeviceOnlyMappedBuffer(long size, int type) {
        super(glCreateBuffers());
        this.size = size;
        this.type = type;
        glNamedBufferStorage(id, size, 0);
        System.out.println("============" + size);

        if (type == GL_BUFFER_GPU_ADDRESS_NV) {
            long[] holder = new long[1];
            glGetNamedBufferParameterui64vNV(id, GL_BUFFER_GPU_ADDRESS_NV, holder);
            glMakeNamedBufferResidentNV(id, GL_READ_WRITE);
            addr = holder[0];
            if (addr == 0) {
                throw new IllegalStateException();
            }
        } else {
            addr = 0;
        }
    }

    @Override
    public void bind(int target) {
        if (type != GL_BUFFER_GPU_ADDRESS_NV) {
            glBindBufferBase(type, target, id);
        }
    }

    @Override
    public void delete() {
        super.free0();
        if (type == GL_BUFFER_GPU_ADDRESS_NV) {
            glMakeNamedBufferNonResidentNV(id);
        }
        glDeleteBuffers(id);
    }

    @Override
    public long getDeviceAddress() {
        return addr;
    }

    @Override
    public void free() {
        this.delete();
    }

    @Override
    public long getSize() {
        return this.size;
    }
}
