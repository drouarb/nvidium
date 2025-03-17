package me.cortex.nvidium.mixin.sodium;

import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import me.cortex.nvidium.Nvidium;
import me.cortex.nvidium.NvidiumWorldRenderer;
import me.cortex.nvidium.config.TranslucencySortingLevel;
import me.cortex.nvidium.managers.AsyncOcclusionTracker;
import me.cortex.nvidium.sodiumCompat.*;
import net.caffeinemc.mods.sodium.api.texture.SpriteUtil;
import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.gl.device.CommandList;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkUpdateType;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.SortBehavior;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Fog;
import net.minecraft.client.world.ClientWorld;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import static me.cortex.nvidium.Nvidium.LOGGER;

@Mixin(value = RenderSectionManager.class, remap = false, priority = 1500) // Ensure priority over Iris so it doesn't hijack our ChunkVertexFormat
public class MixinRenderSectionManager implements INvidiumWorldRendererGetter {
    @Shadow @Final private RenderRegionManager regions;
    @Shadow @Final private Long2ReferenceMap<RenderSection> sectionByPosition;
    @Shadow private @NotNull Map<ChunkUpdateType, ArrayDeque<RenderSection>> taskLists;
    @Shadow @Final private int renderDistance;
    @Unique private NvidiumWorldRenderer renderer;
    @Unique private Viewport viewport;

    @Unique
    private static void updateNvidiumIsEnabled() {
        Nvidium.IS_ENABLED = (!Nvidium.FORCE_DISABLE) && Nvidium.IS_COMPATIBLE && IrisCheck.checkIrisShouldDisable();

        // Disable sodium translucency sorting since nvidium is doing it
        if (Nvidium.IS_ENABLED && Nvidium.config.translucency_sorting_level == TranslucencySortingLevel.SODIUM) {
            LOGGER.info("Ensuring translucency sorting is enabled");
            SodiumClientMod.options().performance.sortingEnabled = true;
        }
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void init(ClientWorld world, int renderDistance, CommandList commandList, CallbackInfo ci) {
        updateNvidiumIsEnabled();
        if (Nvidium.IS_ENABLED) {
            if (renderer != null)
                throw new IllegalStateException("Cannot have multiple world renderers");
            renderer = new NvidiumWorldRenderer(Nvidium.config.async_bfs?new AsyncOcclusionTracker(renderDistance, sectionByPosition, world, taskLists):null);
            ((INvidiumWorldRendererSetter)regions).setWorldRenderer(renderer);
        }
    }

    @ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/executor/ChunkBuilder;<init>(Lnet/minecraft/client/world/ClientWorld;Lnet/caffeinemc/mods/sodium/client/render/chunk/vertex/format/ChunkVertexType;)V", remap = true), index = 1)
    private ChunkVertexType modifyVertexType(ChunkVertexType vertexType) {
        updateNvidiumIsEnabled();
        if (Nvidium.IS_ENABLED) {
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
            if (Nvidium.config.region_keep_distance != 32 &&
                    Nvidium.config.region_keep_distance <= MinecraftClient.getInstance().options.getClampedViewDistance()) {
                renderer.deleteSection(section);
            }
        }
        section.delete();
    }

    @Inject(method = "update", at = @At("HEAD"))
    private void trackViewport(Camera camera, Viewport viewport, Fog fogParameters, boolean spectator, CallbackInfo ci) {
        this.viewport = viewport;
    }

    @Inject(method = "renderLayer", at = @At("HEAD"), cancellable = true)
    public void renderLayer(ChunkRenderMatrices matrices, TerrainRenderPass pass, double x, double y, double z, CallbackInfo ci) {
        if (Nvidium.IS_ENABLED) {
            ci.cancel();
            pass.startDrawing();
            if (pass == DefaultTerrainRenderPasses.SOLID) {
                renderer.renderFrame(viewport, matrices, x, y, z);
            } else if (pass == DefaultTerrainRenderPasses.TRANSLUCENT) {
                renderer.renderTranslucent();
            }
            pass.endDrawing();
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

    @Inject(method = "createTerrainRenderList", at = @At("HEAD"), cancellable = true)
    private void redirectTerrainRenderList(Camera camera, Viewport viewport, Fog fogParameters, int frame, boolean spectator, CallbackInfo ci) {
        if (Nvidium.IS_ENABLED && Nvidium.config.async_bfs) {
            ci.cancel();
        }
    }

    @Redirect(method = "submitSectionTasks(Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/executor/ChunkJobCollector;Lnet/caffeinemc/mods/sodium/client/render/chunk/ChunkUpdateType;Z)V", at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSection;setPendingUpdate(Lnet/caffeinemc/mods/sodium/client/render/chunk/ChunkUpdateType;)V"))
    private void injectEnqueueFalse(RenderSection instance, ChunkUpdateType type) {
        instance.setPendingUpdate(type);
        if (Nvidium.IS_ENABLED && Nvidium.config.async_bfs) {
            //We need to reset the fact that its been submitted to the rebuild queue from the build queue
            ((IRenderSectionExtension) instance).isSubmittedRebuild(false);
        }
    }

    @Unique
    private boolean isSectionVisibleBfs(RenderSection section) {
        //The reason why this is done is that since the bfs search is async it could be updating the frame counter with the next frame
        // while some sections that arnt updated/ticked yet still have the old frame id
        int delta = Math.abs(section.getLastVisibleFrame() - renderer.getAsyncFrameId());
        return delta <= 1;
    }

    @Inject(method = "isSectionVisible", at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSection;getLastVisibleFrame()I", shift = At.Shift.BEFORE), cancellable = true, locals = LocalCapture.CAPTURE_FAILHARD)
    private void redirectIsSectionVisible(int x, int y, int z, CallbackInfoReturnable<Boolean> cir, RenderSection render) {
        if (Nvidium.IS_ENABLED && Nvidium.config.async_bfs) {
            cir.setReturnValue(isSectionVisibleBfs(render));
        }
    }

    @Inject(method = "tickVisibleRenders", at = @At("HEAD"), cancellable = true)
    private void redirectAnimatedSpriteUpdates(CallbackInfo ci) {
        if (Nvidium.IS_ENABLED && Nvidium.config.async_bfs && SodiumClientMod.options().performance.animateOnlyVisibleTextures) {
            ci.cancel();
            var sprites = renderer.getAnimatedSpriteSet();
            if (sprites == null) {
                return;
            }
            for (var sprite : sprites) {
                SpriteUtil.INSTANCE.markSpriteActive(sprite);
            }
        }
    }

    @Inject(method = "scheduleRebuild", at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSection;setPendingUpdate(Lnet/caffeinemc/mods/sodium/client/render/chunk/ChunkUpdateType;)V", shift = At.Shift.AFTER), locals = LocalCapture.CAPTURE_FAILHARD)
    private void instantReschedule(int x, int y, int z, boolean important, CallbackInfo ci, RenderSection section, ChunkUpdateType pendingUpdate) {
        // this might result in the section being enqueued multiple times, if this gets executed,
        // and the async search sees it at the exactly wrong moment
        // This is a problem when sodium translucency sorting is enabled since translucentData.getGeometryPlanes()
        // can be null on the second ChunkBuildOutput resulting in a NPE
        if (Nvidium.IS_ENABLED && Nvidium.config.async_bfs) {
            var queue = taskLists.get(pendingUpdate);
            if (isSectionVisibleBfs(section)  && queue.size() < pendingUpdate.getMaximumQueueSize() && !queue.contains(section)) {
                ((IRenderSectionExtension)section).isSubmittedRebuild(true);
                taskLists.get(pendingUpdate).add(section);
            }
        }
    }

    @Inject(method = "scheduleSort(JZ)V", at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSection;setPendingUpdate(Lnet/caffeinemc/mods/sodium/client/render/chunk/ChunkUpdateType;)V"), locals = LocalCapture.CAPTURE_FAILHARD)
    public void promoteScheduleSort(long sectionPos, boolean isDirectTrigger, CallbackInfo ci, RenderSection section, ChunkUpdateType pendingUpdate, SortBehavior.PriorityMode priorityMode) {
        if (Nvidium.IS_ENABLED && section.getPendingUpdate() != null && pendingUpdate != section.getPendingUpdate()) {
            // The sorter promoted our task, we need to change the taskList
            taskLists.get(section.getPendingUpdate()).remove(section);
            taskLists.get(pendingUpdate).add(section);
        }
    }

    @Inject(method = "getVisibleChunkCount", at = @At("HEAD"), cancellable = true)
    private void injectVisibilityCount(CallbackInfoReturnable<Integer> cir) {
        if (Nvidium.IS_ENABLED && Nvidium.config.async_bfs) {
            cir.setReturnValue(this.renderer.getAsyncBfsVisibilityCount());
        }
    }
}
