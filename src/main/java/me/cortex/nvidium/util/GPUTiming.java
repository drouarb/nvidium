package me.cortex.nvidium.util;

import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import me.cortex.nvidium.gl.TrackedObject;

import java.util.Arrays;

import static org.lwjgl.opengl.GL11C.GL_TRUE;
import static org.lwjgl.opengl.GL15C.*;
import static org.lwjgl.opengl.GL33C.*;

public class GPUTiming {
    private int index = 0;
    private int count = 0;
    private long total = 0;
    private final long[] samples = new long[100];
    private final GlTimestampQuerySet timingSet = new GlTimestampQuerySet();

    public void marker() {
        this.timingSet.capture(0);
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

    public void tick() {
        this.timingSet.download((meta, data) -> {
            long current = data[0];
            for (int i = 1; i < meta.length; i++) {
                long next = data[i];
                long delta = next - current;
                this.addSample(delta);
                current = next;
            }
        });
        this.timingSet.tick();
    }

    public void free() {
        this.timingSet.free();
    }

    public interface TimingDataConsumer {
        void accept(int[] metadata, long[] timings);
    }

    private static final class GlTimestampQuerySet extends TrackedObject {
        private record InflightRequest(int[] queries, int[] meta, TimingDataConsumer callback) {
            private boolean callbackIfReady(IntArrayFIFOQueue queryPool) {
                boolean ready = glGetQueryObjecti(this.queries[this.queries.length - 1], GL_QUERY_RESULT_AVAILABLE) == GL_TRUE;
                if (!ready) {
                    return false;
                }
                long[] results = new long[this.queries.length];
                for (int i = 0; i < this.queries.length; i++) {
                    results[i] = glGetQueryObjecti64(this.queries[i], GL_QUERY_RESULT);
                    queryPool.enqueue(this.queries[i]);
                }
                this.callback.accept(this.meta, results);
                return true;
            }
        }

        private final IntArrayFIFOQueue POOL = new IntArrayFIFOQueue();
        private final ObjectArrayFIFOQueue<InflightRequest> INFLIGHT = new ObjectArrayFIFOQueue();

        private final int[] queries = new int[64];
        private final int[] metadata = new int[64];
        private int index;


        public void capture(int metadata) {
            if (this.index > this.metadata.length) {
                throw new IllegalStateException();
            }
            int slot = this.index++;
            this.metadata[slot] = metadata;
            int query = this.getQuery();
            glQueryCounter(query, GL_TIMESTAMP);
            this.queries[slot] = query;

        }

        public void download(TimingDataConsumer consumer) {
            var queries = Arrays.copyOf(this.queries, this.index);
            var metadata = Arrays.copyOf(this.metadata, this.index);
            this.index = 0;
            this.INFLIGHT.enqueue(new InflightRequest(queries, metadata, consumer));
        }

        public void tick() {
            while (!INFLIGHT.isEmpty()) {
                if (INFLIGHT.first().callbackIfReady(POOL)) {
                    INFLIGHT.dequeue();
                } else {
                    break;
                }
            }
        }

        private int getQuery() {
            if (POOL.isEmpty()) {
                return glGenQueries();
            } else {
                return POOL.dequeueInt();
            }
        }

        @Override
        public void free() {
            super.free0();
            while (!POOL.isEmpty()) {
                glDeleteQueries(POOL.dequeueInt());
            }
            while (!INFLIGHT.isEmpty()) {
                glDeleteQueries(INFLIGHT.dequeue().queries);
            }
        }
    }
}