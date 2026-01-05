package me.cortex.nvidium.renderers;

import me.cortex.nvidium.gl.shader.Shader;
import me.cortex.nvidium.sodiumCompat.ShaderLoader;
import net.minecraft.resources.Identifier;

import static me.cortex.nvidium.gl.shader.ShaderType.*;
import static org.lwjgl.opengl.GL43C.glDispatchCompute;

public class SortRegionSectionPhase extends Phase {
    private final Shader shader = Shader.make()
            .addSource(COMPUTE, ShaderLoader.parse(Identifier.fromNamespaceAndPath("nvidium", "sorting/region_section_sorter.comp")))
            .compile();

    public SortRegionSectionPhase() {
    }

    public void dispatch(int sortingRegionCount) {
        shader.bind();
        timing.marker();
        glDispatchCompute(sortingRegionCount, 1, 1);
        timing.marker();
        timing.tick();
    }

    public void delete() {
        super.delete();
        shader.delete();
    }
}
