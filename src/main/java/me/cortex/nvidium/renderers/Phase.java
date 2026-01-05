package me.cortex.nvidium.renderers;

import me.cortex.nvidium.util.GPUTiming;

public abstract class Phase {
    GPUTiming timing = new GPUTiming();

    public void delete() {
        timing.free();
    }

    public GPUTiming getTiming() {
        return timing;
    }
}
