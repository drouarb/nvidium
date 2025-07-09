package me.cortex.nvidium.util;

import com.mojang.authlib.minecraft.client.MinecraftClient;
import net.minecraft.client.Minecraft;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;

import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL15.glDeleteQueries;

public class FrameTimeProfiler {
    int startQuery = GL15.glGenQueries();
    int endQuery = GL15.glGenQueries();

    private final long[] samples;
    private int index = 0;
    private int count = 0;
    private long total = 0;

    public FrameTimeProfiler(int size) {
        samples = new long[size];
    }

    private void addSample(long timeNs) {
        total -= samples[index];
        samples[index] = timeNs;
        total += timeNs;

        index = (index + 1) % samples.length;
        if (count < samples.length) {
            count++;
        }
    }

    public double getAverageMs() {
        pollResults();
        return count == 0 ? 0 : (total / count) / 1_000_000.0;
    }

    public void pollResults() {
        long t0 = GL33.glGetQueryObjectui64(startQuery, GL33.GL_QUERY_RESULT);
        long t1 = GL33.glGetQueryObjectui64(endQuery, GL33.GL_QUERY_RESULT);
        addSample(t1 - t0);
    }

    public void startQuery() {
        if (!Minecraft.getInstance().getDebugOverlay().showDebugScreen())
            return;
        glDeleteQueries(startQuery);
        glDeleteQueries(endQuery);
        endQuery = GL15.glGenQueries();
        startQuery = GL15.glGenQueries();
        GL33.glQueryCounter(startQuery, GL33.GL_TIMESTAMP);
    }

    public void endQuery() {
        if (!Minecraft.getInstance().getDebugOverlay().showDebugScreen())
            return;
        GL33.glQueryCounter(endQuery, GL33.GL_TIMESTAMP);
    }
}