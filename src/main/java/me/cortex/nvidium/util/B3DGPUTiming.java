package me.cortex.nvidium.util;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuQueryPool;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import me.cortex.nvidium.Nvidium;
import me.cortex.nvidium.gl.TrackedObject;

public class B3DGPUTiming extends TrackedObject {
    private final static int MAX_TIMINGS = 64;
    private int timingSlot = -1;

    private final GpuQueryPool pool;
    private final IntArrayFIFOQueue POOL = new IntArrayFIFOQueue();
    private final IntArrayFIFOQueue INFLIGHT = new IntArrayFIFOQueue();

    private int index = 0;
    private int count = 0;
    private long total = 0;
    private final long[] samples = new long[100];

    public B3DGPUTiming() {
        pool = RenderSystem.getDevice().createTimestampQueryPool(128);
        for (int i = 0; i < MAX_TIMINGS; i++) {
            POOL.enqueue(i);
        }
    }

    public void begin(CommandEncoder commandEncoder) {
        this.tick();

        if (POOL.isEmpty()) {
            Nvidium.LOGGER.error("Timing pool: no more slot available");
            return;
        }

        this.timingSlot = POOL.dequeueInt();
        commandEncoder.writeTimestamp(pool, this.timingSlot * 2);
    }

    public void end(CommandEncoder commandEncoder) {
        if (this.timingSlot == -1) {
            Nvidium.LOGGER.error("Timing pool: end before begin");
            return;
        }
        commandEncoder.writeTimestamp(pool, this.timingSlot * 2 + 1);
        INFLIGHT.enqueue(this.timingSlot);
        this.timingSlot = -1;
    }

    private void tick() {
        while (!INFLIGHT.isEmpty()) {
            int slot = INFLIGHT.firstInt();
            var timestamps = pool.getValues(slot * 2, 2);

            if (timestamps[0].isPresent() && timestamps[1].isPresent()) {
                long delta = timestamps[1].getAsLong() - timestamps[0].getAsLong();
                long time = Math.round(delta * RenderSystem.getDevice().getDeviceInfo().timestampPeriod());
                this.addSample(time);
                INFLIGHT.dequeueInt();
                POOL.enqueue(slot);
            } else {
                break;
            }
        }
    }

    public void addSample(long timeNs) {
        total -= samples[index];
        samples[index] = timeNs;
        total += timeNs;

        index = (index + 1) % samples.length;
        if (count < samples.length) {
            count++;
        }
    }

    public double getAverageMs() {
        return count == 0 ? 0 : (total / count) / 1_000_000.0;
    }

    @Override
    public void free() {
        this.free0();
        pool.close();
    }
}
