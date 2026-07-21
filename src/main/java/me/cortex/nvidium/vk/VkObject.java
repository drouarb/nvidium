package me.cortex.nvidium.vk;

import me.cortex.nvidium.gl.IResource;
import me.cortex.nvidium.gl.TrackedObject;

public abstract class VkObject extends TrackedObject implements IResource {
    protected final long id;

    protected VkObject(long id) {
        this.id = id;
    }

    public long getId() {
        return id;
    }
}
