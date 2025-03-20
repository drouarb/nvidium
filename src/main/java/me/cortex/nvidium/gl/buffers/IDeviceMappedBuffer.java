package me.cortex.nvidium.gl.buffers;

public interface IDeviceMappedBuffer extends Buffer {
    long getDeviceAddress();
    void bind(int target);
}
