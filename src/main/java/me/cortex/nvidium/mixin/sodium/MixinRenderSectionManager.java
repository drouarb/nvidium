package me.cortex.nvidium.mixin.sodium;

import com.mojang.blaze3d.opengl.GlConst;
import com.mojang.blaze3d.opengl.GlDevice;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuSampler;
import me.cortex.nvidium.Nvidium;
import me.cortex.nvidium.NvidiumWorldRenderer;
import me.cortex.nvidium.sodiumCompat.*;
import net.caffeinemc.mods.sodium.client.gl.device.CommandList;
import net.caffeinemc.mods.sodium.client.render.chunk.*;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.SortBehavior;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import net.caffeinemc.mods.sodium.mixin.core.CommandEncoderAccessor;
import net.caffeinemc.mods.sodium.mixin.core.GlCommandEncoderAccessor;
import net.caffeinemc.mods.sodium.mixin.core.GpuDeviceAccessor;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Collection;

@Mixin(value = RenderSectionManager.class, remap = false, priority = 1500) // Ensure priority over Iris so it doesn't hijack our ChunkVertexFormat
public class MixinRenderSectionManager implements INvidiumWorldRendererGetter {
    @Shadow @Final private RenderRegionManager regions;
    @Unique private NvidiumWorldRenderer renderer;
    @Unique private Viewport viewport;

    @Unique
    private static void updateNvidiumIsEnabled() {
        Nvidium.IS_ENABLED = (!Nvidium.FORCE_DISABLE) && Nvidium.IS_COMPATIBLE && IrisCheck.checkIrisShouldDisable();
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void init(ClientLevel level, int renderDistance, SortBehavior sortBehavior, CommandList commandList, CallbackInfo ci) {
        updateNvidiumIsEnabled();
        if (Nvidium.IS_ENABLED) {
            if (renderer != null)
                throw new IllegalStateException("Cannot have multiple world renderers");
            renderer = new NvidiumWorldRenderer();
            ((INvidiumWorldRendererSetter)regions).setWorldRenderer(renderer);
        }
    }

    @ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/executor/ChunkBuilder;<init>(Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/caffeinemc/mods/sodium/client/render/chunk/vertex/format/ChunkVertexType;)V", remap = true), index = 1)
    private ChunkVertexType modifyVertexType(ChunkVertexType vertexType) {
        updateNvidiumIsEnabled();
        if (Nvidium.IS_ENABLED && !Nvidium.config.use_sodium_vertex_format) {
            return NvidiumCompactChunkVertex.INSTANCE;
        }
        return vertexType;
    }


    @Inject(method = "destroy", at = @At("TAIL"))
    private void destroy(CallbackInfo ci) {
        if (Nvidium.IS_ENABLED) {
            if (renderer == null)
                throw new IllegalStateException("Pipeline already destroyed");
            ((INvidiumWorldRendererSetter)regions).setWorldRenderer(null);
            renderer.delete();
            renderer = null;
        }
    }

    @Redirect(method = "onSectionRemoved", at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSection;delete()V"))
    private void deleteSection(RenderSection section) {
        if (Nvidium.IS_ENABLED) {
            if (Nvidium.config.region_keep_distance == 32 ||
                    Nvidium.config.region_keep_distance <= Minecraft.getInstance().options.getEffectiveRenderDistance()) {
                renderer.deleteSection(section);
            }
        }
        section.delete();
    }

    @Inject(method = "prepareRenderTrees", at = @At("HEAD"))
    private void trackViewport(Camera camera, Viewport viewport, FogParameters fogParameters, boolean spectator, CallbackInfo ci) {
        this.viewport = viewport;
    }

    @Inject(method = "renderLayer", at = @At("HEAD"), cancellable = true)
    public void renderLayer(ChunkRenderMatrices matrices, TerrainRenderPass pass, double x, double y, double z, FogParameters fogParameters, GpuSampler terrainSampler, CallbackInfo ci) {
        if (Nvidium.IS_ENABLED) {
            ci.cancel();
            if (pass == DefaultTerrainRenderPasses.CUTOUT) // Early exit, cutout will be rendered with SOLID
                return;

            RenderTarget target = pass.getTarget();
            GlStateManager._viewport(0, 0, target.getColorTexture().getWidth(0), target.getColorTexture().getHeight(0));
            GlStateManager._glBindFramebuffer(GlConst.GL_FRAMEBUFFER, ((GlTexture) target.getColorTexture()).getFbo(((GlDevice) ((GpuDeviceAccessor) RenderSystem.getDevice()).sodium$getBackend()).directStateAccess(), target.getDepthTexture()));
            ((GlCommandEncoderAccessor) ((CommandEncoderAccessor) RenderSystem.getDevice().createCommandEncoder()).sodium$getBackend()).sodium$applyPipelineState(pass.getPipeline());
            ((GlCommandEncoderAccessor) ((CommandEncoderAccessor) RenderSystem.getDevice().createCommandEncoder()).sodium$getBackend()).sodium$setLastProgram(null);

            if (pass == DefaultTerrainRenderPasses.SOLID) {
                renderer.renderFrame(pass, viewport, fogParameters, matrices, x, y, z, terrainSampler);
            } else if (pass == DefaultTerrainRenderPasses.TRANSLUCENT) {
                renderer.renderTranslucent(pass, terrainSampler);
            }
        }
    }

    @Inject(method = "getDebugStrings", at = @At("HEAD"), cancellable = true)
    private void redirectDebug(CallbackInfoReturnable<Collection<String>> cir) {
        if (Nvidium.IS_ENABLED) {
            var debugStrings = new ArrayList<String>();
            renderer.addDebugInfo(debugStrings);
            cir.setReturnValue(debugStrings);
            cir.cancel();
        }
    }

    @Override
    public NvidiumWorldRenderer getRenderer() {
        return renderer;
    }
}
