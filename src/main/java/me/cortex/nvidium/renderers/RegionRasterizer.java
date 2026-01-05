package me.cortex.nvidium.renderers;

import me.cortex.nvidium.gl.shader.Shader;
import me.cortex.nvidium.sodiumCompat.ShaderLoader;
import net.minecraft.resources.Identifier;

import static me.cortex.nvidium.gl.shader.ShaderType.FRAGMENT;
import static me.cortex.nvidium.gl.shader.ShaderType.MESH;
import static org.lwjgl.opengl.NVMeshShader.glDrawMeshTasksNV;

public class RegionRasterizer extends Phase {
    private final Shader shader = Shader.make()
                    .addSource(MESH, ShaderLoader.parse(Identifier.fromNamespaceAndPath("nvidium", "occlusion/region_raster/mesh.glsl")))
                    .addSource(FRAGMENT, ShaderLoader.parse(Identifier.fromNamespaceAndPath("nvidium", "occlusion/region_raster/fragment.frag")))
                    .compile();

    public void raster(int regionCount) {
        shader.bind();
        timing.marker();
        glDrawMeshTasksNV(0,regionCount);
        timing.marker();
        timing.tick();
    }

    public void delete() {
        super.delete();
        shader.delete();
    }
}
