package me.cortex.nvidium.renderers;

import me.cortex.nvidium.gl.shader.Shader;
import me.cortex.nvidium.sodiumCompat.ShaderLoader;
import me.cortex.nvidium.util.GPUTiming;
import net.minecraft.resources.ResourceLocation;

import static me.cortex.nvidium.gl.shader.ShaderType.COMPUTE;
import static org.lwjgl.opengl.GL43C.glDispatchCompute;

public class CmdBufferBuilder extends Phase {
    private final Shader shader = Shader.make()
            .addSource(COMPUTE, ShaderLoader.parse(ResourceLocation.fromNamespaceAndPath("nvidium", "occlusion/command_buffer/command_buffer_builder.comp")))
            .compile();

    public CmdBufferBuilder() {
    }

    public void dispatch(int regionCount, GPUTiming timing) {
        shader.bind();
        timing.marker();
        glDispatchCompute(regionCount, 1, 1);
        timing.marker();
        timing.tick();
    }

    public void delete() {
        shader.delete();
    }
}
