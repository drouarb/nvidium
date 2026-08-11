package me.cortex.nvidium.vk.buffers;

import me.cortex.nvidium.gl.IResource;

public interface VkBuffer extends IResource {
    long getHandle();
    long getSize();
    long getDeviceAddress();
}