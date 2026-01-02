package me.cortex.nvidium.renderers;

import com.mojang.blaze3d.opengl.GlSampler;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import me.cortex.nvidium.gl.shader.Shader;
import me.cortex.nvidium.sodiumCompat.ShaderLoader;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.opengl.*;

import static me.cortex.nvidium.RenderPipeline.GL_DRAW_INDIRECT_ADDRESS_NV;
import static me.cortex.nvidium.gl.shader.ShaderType.*;
import static org.lwjgl.opengl.NVMeshShader.glMultiDrawMeshTasksIndirectNV;
import static org.lwjgl.opengl.NVVertexBufferUnifiedMemory.glBufferAddressRangeNV;

public class TemporalTerrainRasterizer extends Phase {
    private final Shader shader = Shader.make()
            .addSource(TASK, ShaderLoader.parse(Identifier.fromNamespaceAndPath("nvidium", "terrain/temporal_task.glsl")))
            .addSource(MESH, ShaderLoader.parse(Identifier.fromNamespaceAndPath("nvidium", "terrain/mesh.glsl")))
            .addSource(FRAGMENT, ShaderLoader.parse(Identifier.fromNamespaceAndPath("nvidium", "terrain/frag.frag"))).compile();

    public TemporalTerrainRasterizer() {
    }

    private static void setTexture(GpuTextureView texView, int bindingPoint, GpuSampler sampler) {
        GlTexture tex = (GlTexture) texView.texture();
        GlStateManager._activeTexture(GL32C.GL_TEXTURE0 + bindingPoint);
        GlStateManager._bindTexture(tex.glId());
        GlStateManager._texParameter(GL32C.GL_TEXTURE_2D, 33084, texView.baseMipLevel());
        GlStateManager._texParameter(GL32C.GL_TEXTURE_2D, 33085, texView.baseMipLevel() + texView.mipLevels() - 1);
        GL33C.glBindSampler(bindingPoint, ((GlSampler) sampler).getId());
    }

    public void raster(TerrainRenderPass pass, int regionCount, long commandAddr, GpuSampler terrainSampler) {
        shader.bind();

        GpuTextureView blockTexture = pass.getAtlas();
        GpuTextureView lightTexture = Minecraft.getInstance().gameRenderer.lightTexture().getTextureView();

        setTexture(blockTexture, 0, terrainSampler);
        setTexture(lightTexture, 1, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));

        glBufferAddressRangeNV(GL_DRAW_INDIRECT_ADDRESS_NV, 0, commandAddr, regionCount*8L);//Bind the command buffer
        glMultiDrawMeshTasksIndirectNV( 0, regionCount, 0);

        GL45C.glBindSampler(0, 0);
        GL45C.glBindSampler(1, 0);
    }

    public void delete() {
        shader.delete();
    }
}
