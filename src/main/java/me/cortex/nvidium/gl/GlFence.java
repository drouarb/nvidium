package me.cortex.nvidium.gl;

import static org.lwjgl.opengl.GL32.*;

public class GlFence extends TrackedObject {
    private final long fence;
    private boolean signaled;

    public GlFence() {
        this.fence = glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
    }

    public boolean signaled() {
        this.assertNotFreed();
        if (!this.signaled) {
            int ret = glClientWaitSync(this.fence, 0, 0);
            if (ret == GL_ALREADY_SIGNALED || ret == GL_CONDITION_SATISFIED) {
                this.signaled = true;
            } else if (ret != GL_TIMEOUT_EXPIRED) {
                throw new IllegalStateException("Poll for fence failed, ret: " + ret + " glError: " + glGetError());
            }
        }
        return this.signaled;
    }

    /** Block until the fence signals or timeoutNanos elapses. Returns true if signaled. */
    public boolean await(long timeoutNanos) {
        this.assertNotFreed();
        if (this.signaled) return true;
        int ret = glClientWaitSync(this.fence, GL_SYNC_FLUSH_COMMANDS_BIT, timeoutNanos);
        if (ret == GL_ALREADY_SIGNALED || ret == GL_CONDITION_SATISFIED) {
            this.signaled = true;
            return true;
        }
        if (ret != GL_TIMEOUT_EXPIRED) {
            throw new IllegalStateException("Wait for fence failed, ret: " + ret + " glError: " + glGetError());
        }
        return false;
    }

    @Override
    public void free() {
        super.free0();
        glDeleteSync(this.fence);
    }
}
