package me.cortex.nvidium.mixin.sodium;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import me.cortex.nvidium.Nvidium;
import me.cortex.nvidium.NvidiumWorldRenderer;
import me.cortex.nvidium.sodiumCompat.INvidiumWorldRendererSetter;
import net.caffeinemc.mods.sodium.client.gl.device.CommandList;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.BuilderTaskOutput;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildOutput;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

@Mixin(value = RenderRegionManager.class, remap = false)
public abstract class MixinRenderRegionManager implements INvidiumWorldRendererSetter {
    @Unique private NvidiumWorldRenderer renderer;

    // Prevent sodium from uploading chunks if nvidium is enabled
    @WrapOperation(
            method = "uploadResults(Lnet/caffeinemc/mods/sodium/client/gl/device/CommandList;Lnet/caffeinemc/mods/sodium/client/render/chunk/region/RenderRegion;Ljava/util/Collection;)V",
            constant = @Constant(classValue = ChunkBuildOutput.class, ordinal = 0)
    )
    private boolean preventSodiumUpload(Object obj, Operation<Boolean> original) {
        if (Nvidium.IS_ENABLED) {
            return false;
        }
        return original.call(obj);
    }

    // Replace with nvidium upload
    @Inject(method = "uploadResults(Lnet/caffeinemc/mods/sodium/client/gl/device/CommandList;Lnet/caffeinemc/mods/sodium/client/render/chunk/region/RenderRegion;Ljava/util/Collection;)V",
            at = @At(value = "JUMP", ordinal = 1))
    private void redirectUpload(CommandList commandList, RenderRegion region, Collection<BuilderTaskOutput> results, CallbackInfo ci, @Local() BuilderTaskOutput result) {
        if (Nvidium.IS_ENABLED) {
            if (result instanceof ChunkBuildOutput) {
                renderer.uploadBuildResult((ChunkBuildOutput)result);
            }
        }
    }

    @Override
    public void setWorldRenderer(NvidiumWorldRenderer renderer) {
        this.renderer = renderer;
    }
}
