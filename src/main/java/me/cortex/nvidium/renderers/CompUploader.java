package me.cortex.nvidium.renderers;

import me.cortex.nvidium.gl.shader.Shader;
import me.cortex.nvidium.sodiumCompat.ShaderLoader;
import net.minecraft.resources.Identifier;

import static me.cortex.nvidium.gl.shader.ShaderType.COMPUTE;
import static org.lwjgl.opengl.GL43C.glDispatchCompute;

public class CompUploader extends Phase {
    private final Shader shader = Shader.make()
            .addSource(COMPUTE, ShaderLoader.parse(Identifier.fromNamespaceAndPath("nvidium", "hashmap/upload.comp")))
            .compile();

    public CompUploader() {
    }

    public void dispatch(int uploadCount) {
        shader.bind();
        timing.marker();
        System.out.println("DISPATCH " + uploadCount);
        glDispatchCompute(uploadCount, 1, 1);
        timing.marker();
        timing.tick();
    }

    @Override
    public void delete() {
        super.delete();
        shader.delete();
    }
}
