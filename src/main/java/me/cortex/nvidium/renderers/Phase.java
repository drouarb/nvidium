package me.cortex.nvidium.renderers;

import me.cortex.nvidium.util.B3DGPUTiming;

public abstract class Phase {
    B3DGPUTiming timing = new B3DGPUTiming();

    public void delete() {
        timing.free();
    }

    public B3DGPUTiming getTiming() {
        return timing;
    }
}
