package me.cortex.nvidium.renderers;

import com.mojang.blaze3d.opengl.GlSampler;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import me.cortex.nvidium.gl.buffers.IDeviceMappedBuffer;
import me.cortex.nvidium.gl.shader.Shader;
import me.cortex.nvidium.sodiumCompat.ShaderLoader;
import me.cortex.nvidium.util.GPUTiming;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.opengl.*;

import static me.cortex.nvidium.RenderPipeline.GL_DRAW_INDIRECT_ADDRESS_NV;
import static me.cortex.nvidium.gl.EXTMeshShader.glMultiDrawMeshTasksIndirectEXT;
import static me.cortex.nvidium.gl.shader.ShaderType.*;
import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL33.glGenSamplers;
import static org.lwjgl.opengl.GL40.GL_DRAW_INDIRECT_BUFFER;
import static org.lwjgl.opengl.NVMeshShader.glMultiDrawMeshTasksIndirectNV;
import static org.lwjgl.opengl.NVVertexBufferUnifiedMemory.glBufferAddressRangeNV;

public class TranslucentTerrainRasterizer extends Phase {
    private final Shader shader = Shader.make()
            .addSource(TASK, ShaderLoader.parse(Identifier.fromNamespaceAndPath("nvidium", "terrain/translucent/task.glsl")))
            .addSource(MESH, ShaderLoader.parse(Identifier.fromNamespaceAndPath("nvidium", "terrain/translucent/mesh.glsl")))
            .addSource(FRAGMENT, ShaderLoader.parse(Identifier.fromNamespaceAndPath("nvidium", "terrain/frag.frag"), builder->{builder.add("TRANSLUCENT_PASS");}))
            .compile();

    public TranslucentTerrainRasterizer() {
    }

    private static void setTexture(GpuTextureView texView, int bindingPoint, GpuSampler sampler) {
        GlTexture tex = (GlTexture) texView.texture();
        GlStateManager._activeTexture(GL32C.GL_TEXTURE0 + bindingPoint);
        GlStateManager._bindTexture(tex.glId());
        GlStateManager._texParameter(GL32C.GL_TEXTURE_2D, 33084, texView.baseMipLevel());
        GlStateManager._texParameter(GL32C.GL_TEXTURE_2D, 33085, texView.baseMipLevel() + texView.mipLevels() - 1);
        GL33C.glBindSampler(bindingPoint, ((GlSampler) sampler).getId());
    }

    //Translucency is rendered in a very cursed and incorrect way
    // it hijacks the unassigned indirect command dispatch and uses that to dispatch the translucent chunks as well
    public void raster(TerrainRenderPass pass, int regionCount, IDeviceMappedBuffer commandBuffer, GpuSampler terrainSampler, GPUTiming gpuTiming) {
        if (regionCount == 0) {
            return;
        }

        shader.bind();

        GpuTextureView blockTexture = pass.getAtlas();
        GpuTextureView lightTexture = Minecraft.getInstance().gameRenderer.lightTexture().getTextureView();

        setTexture(blockTexture, 0, terrainSampler);
        setTexture(lightTexture, 1, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));

        //the +8*6 is to offset to the unassigned dispatch
        // TODO Make it auto if we can't use nvidia
        //glBufferAddressRangeNV(GL_DRAW_INDIRECT_ADDRESS_NV, 0, commandBuffer.getDeviceAddress(), regionCount*8L);//Bind the command buffer
        glBindBuffer(GL_DRAW_INDIRECT_BUFFER, commandBuffer.getId());
        gpuTiming.marker();
        glMultiDrawMeshTasksIndirectEXT(0, regionCount, 16);
        //glMultiDrawMeshTasksIndirectNV( 0, regionCount, 0);
        gpuTiming.marker();
        gpuTiming.tick();
        GL45C.glBindSampler(0, 0);
        GL45C.glBindSampler(1, 0);
    }

    public void delete() {
        shader.delete();
    }
}
