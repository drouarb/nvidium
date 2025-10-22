package me.cortex.nvidium.mixin.minecraft;

import me.cortex.nvidium.Nvidium;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogParameters;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.util.Mth;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
public class MixinFogRenderer {
    @ModifyVariable(method = "setupFog", at = @At("HEAD"), argsOnly = true, index = 3)
    private static float modifyFogRD(
            float viewDistance,
            Camera camera,
            FogRenderer.FogMode fogMode,
            Vector4f fogColor,
            float viewDistanceArg,
            boolean isFoggy,
            float partialTick
    ) {
        if (Nvidium.IS_ENABLED && Nvidium.config.region_keep_distance != 32) {
            return Math.max(viewDistance, Nvidium.config.region_keep_distance * 16);
        }
        return viewDistance;
    }

    @Inject(method = "setupFog", at = @At("RETURN"), cancellable = true)
    private static void clampSkyEnd(
            Camera camera,
            FogRenderer.FogMode fogMode,
            Vector4f fogColor,
            float viewDistance,
            boolean isFoggy,
            float partialTick,
            CallbackInfoReturnable<FogParameters> cir
    ) {
        if (fogMode != FogRenderer.FogMode.FOG_SKY) {
            return;
        }

        FogParameters params = cir.getReturnValue();
        float clampedEnd = Mth.clamp(params.end(), 2 * 16, 32 * 16);
        if (clampedEnd == params.end()) {
            return;
        }

        cir.setReturnValue(new FogParameters(
                params.start(),
                clampedEnd,
                params.shape(),
                params.red(),
                params.green(),
                params.blue(),
                params.alpha()
        ));
    }
}
