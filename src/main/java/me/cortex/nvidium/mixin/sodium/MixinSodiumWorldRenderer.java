package me.cortex.nvidium.mixin.sodium;

import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import me.cortex.nvidium.Nvidium;
import me.cortex.nvidium.NvidiumWorldRenderer;
import me.cortex.nvidium.sodiumCompat.INvidiumWorldRendererGetter;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.server.level.BlockDestructionProgress;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.SortedSet;

@Mixin(value = SodiumWorldRenderer.class, remap = false)
public abstract class MixinSodiumWorldRenderer implements INvidiumWorldRendererGetter {
    @Shadow private RenderSectionManager renderSectionManager;

    @Shadow
    private void extractBlockEntity(BlockEntity blockEntity, PoseStack poseStack, Camera camera, float tickDelta, Long2ObjectMap<SortedSet<BlockDestructionProgress>> progression, LevelRenderState levelRenderState) {
    }

    @Inject(method = "setupTerrain", at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSectionManager;needsUpdate()Z", shift = At.Shift.BEFORE))
    private void injectTerrainSetup(Camera camera, Viewport viewport, FogParameters fogParameters, boolean spectator, boolean updateChunksImmediately, Matrix4f cullMatrix, CallbackInfo ci) {
        if (Nvidium.IS_ENABLED && Nvidium.config.async_bfs) {
            ((INvidiumWorldRendererGetter)renderSectionManager).getRenderer().update(camera, viewport, spectator);
        }
    }

    @Inject(method = "extractBlockEntities(Lnet/minecraft/client/Camera;FLit/unimi/dsi/fastutil/longs/Long2ObjectMap;Lnet/minecraft/client/renderer/state/level/LevelRenderState;)V", at = @At("HEAD"), cancellable = true, remap = true)
    private void overrideEntityRenderer(Camera camera, float tickDelta, Long2ObjectMap<SortedSet<BlockDestructionProgress>> progression, LevelRenderState levelRenderState, CallbackInfo ci) {
        if (Nvidium.IS_ENABLED && Nvidium.config.async_bfs) {
            ci.cancel();
            PoseStack stack = new PoseStack();

            var sectionsWithEntities = ((INvidiumWorldRendererGetter)renderSectionManager).getRenderer().getSectionsWithEntities();
            for (var section : sectionsWithEntities) {
                if (section.isDisposed() || section.getCulledBlockEntities() == null)
                    continue;
                for (var entity : section.getCulledBlockEntities()) {
                    extractBlockEntity(entity, stack, camera, tickDelta, progression, levelRenderState);
                }
            }

            var sectionsWithGlobalEntities = renderSectionManager.getSectionsWithGlobalEntities();
            for (var section : sectionsWithGlobalEntities) {
                if (section.isDisposed() || section.getGlobalBlockEntities() == null)
                    continue;
                for (var entity : section.getGlobalBlockEntities()) {
                    extractBlockEntity(entity, stack, camera, tickDelta, progression, levelRenderState);
                }
            }
        }
    }

    @Override
    public NvidiumWorldRenderer getRenderer() {
        if (Nvidium.IS_ENABLED) {
            return ((INvidiumWorldRendererGetter)renderSectionManager).getRenderer();
        } else {
            return null;
        }
    }
}
