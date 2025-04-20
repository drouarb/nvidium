package me.cortex.nvidium.renderers;

import com.mojang.blaze3d.platform.GlStateManager;
import me.cortex.nvidium.gl.buffers.IDeviceMappedBuffer;
import me.cortex.nvidium.gl.shader.Shader;
import me.cortex.nvidium.sodiumCompat.ShaderLoader;
import me.cortex.nvidium.mixin.minecraft.LightMapAccessor;
import me.cortex.nvidium.util.DownloadTaskStream;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL12C;
import org.lwjgl.opengl.GL45;
import org.lwjgl.opengl.GL45C;
import org.lwjgl.system.MemoryUtil;

import static me.cortex.nvidium.RenderPipeline.GL_DRAW_INDIRECT_ADDRESS_NV;
import static me.cortex.nvidium.gl.shader.ShaderType.*;
import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL33.glGenSamplers;
import static org.lwjgl.opengl.GL40.GL_DRAW_INDIRECT_BUFFER;
import static org.lwjgl.opengl.NVMeshShader.*;

public class PrimaryTerrainRasterizer extends Phase {
    private final int blockSampler = glGenSamplers();
    private final int lightSampler = glGenSamplers();
    private final Shader shader = Shader.make()
            .addSource(TASK, ShaderLoader.parse(Identifier.of("nvidium", "terrain/task.glsl")))
            .addSource(MESH, ShaderLoader.parse(Identifier.of("nvidium", "terrain/mesh.glsl")))
            .addSource(FRAGMENT, ShaderLoader.parse(Identifier.of("nvidium", "terrain/frag.frag"))).compile();

    public PrimaryTerrainRasterizer() {
        GL45C.glSamplerParameteri(blockSampler,     GL45C.GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_LINEAR);
        GL45C.glSamplerParameteri(blockSampler, GL45C.GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        GL45C.glSamplerParameteri(blockSampler, GL45C.GL_TEXTURE_MIN_LOD, 0);
        GL45C.glSamplerParameteri(blockSampler, GL45C.GL_TEXTURE_MAX_LOD, 4);
        GL45C.glSamplerParameteri(lightSampler, GL_TEXTURE_WRAP_T, GL12C.GL_CLAMP_TO_EDGE);
        GL45C.glSamplerParameteri(lightSampler, GL_TEXTURE_WRAP_S, GL12C.GL_CLAMP_TO_EDGE);
        GL45C.glSamplerParameteri(lightSampler, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        GL45C.glSamplerParameteri(lightSampler, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    }

    private static void setTexture(int textureId, int bindingPoint) {
        GlStateManager._activeTexture(33984 + bindingPoint);
        GlStateManager._bindTexture(textureId);
    }

    public void raster(int regionCount, IDeviceMappedBuffer commandBuffer, DownloadTaskStream downloadTask) {
        shader.bind();

        int blockId = MinecraftClient.getInstance().getTextureManager().getTexture(Identifier.of("minecraft", "textures/atlas/blocks.png")).getGlId();
        int lightId = ((LightMapAccessor)MinecraftClient.getInstance().gameRenderer.getLightmapTextureManager()).getLightmapFramebuffer().getColorAttachment();

        GL45C.glBindSampler(0, blockSampler);
        GL45C.glBindSampler(1, lightSampler);
        setTexture(blockId, 0);
        setTexture(lightId, 1);

        // TODO Make it auto if we can't use nvidia
        //glBufferAddressRangeNV(GL_DRAW_INDIRECT_ADDRESS_NV, 0, commandBuffer.getDeviceAddress(), regionCount*8L);//Bind the command buffer
        glBindBuffer(GL_DRAW_INDIRECT_BUFFER, commandBuffer.getId());

        if (regionCount == 0)
            return;

        // Pick one of the following 3
        // 1. glMultiDrawMeshTasksIndirectNV Ideal
        //glMultiDrawMeshTasksIndirectNV(0, regionCount, 8);

        // 2. Terrible performance for debug purpose
        //for(int i =0; i < regionCount; i++) {glDrawMeshTasksIndirectNV(i*8L);}

        // 3. Even more cursed
        //*
        downloadTask.download(commandBuffer, 0, regionCount*2*4, (addr)-> {
            System.out.println("RegionCount: " + regionCount);

            for (int i = 0; i < regionCount; i++) {
                int count = MemoryUtil.memGetInt(addr + (i * 8L));
                int first = MemoryUtil.memGetInt(addr + (i * 8L + 4));

                System.out.println(i + ">count:" + count + " | first:" + first);
                if (count > 0) {
                    glDrawMeshTasksNV(first, count);
                }
            }
        });
        downloadTask.forceTickAll();
        //*

        GL45C.glBindSampler(0, 0);
        GL45C.glBindSampler(1, 0);
    }

    public void delete() {
        GL45.glDeleteSamplers(blockSampler);
        GL45.glDeleteSamplers(lightSampler);
        shader.delete();
    }
}
