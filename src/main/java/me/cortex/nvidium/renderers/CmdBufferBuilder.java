package me.cortex.nvidium.renderers;

import me.cortex.nvidium.gl.shader.Shader;
import me.cortex.nvidium.sodiumCompat.ShaderLoader;
import net.minecraft.resources.ResourceLocation;

import static me.cortex.nvidium.gl.shader.ShaderType.COMPUTE;
import static org.lwjgl.opengl.GL43C.glDispatchCompute;

public class CmdBufferBuilder extends Phase {
    private final Shader shader = Shader.make()
            .addSource(COMPUTE, ShaderLoader.parse(ResourceLocation.fromNamespaceAndPath("nvidium", "occlusion/command_buffer/command_buffer_builder.comp")))
            .compile();

    public CmdBufferBuilder() {
    }

    public void dispatch(int regionCount) {
        shader.bind();
        glDispatchCompute(regionCount, 1, 1);
    }

    public void delete() {
        shader.delete();
    }
}
