package me.cortex.nvidium.mixin.minecraft;

import me.cortex.nvidium.Nvidium;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LevelRenderer.class)
public class MixinLevelRenderer {
    @Redirect(method = "renderLevel", at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(FF)F"), require = 0)
    private float redirectMax(float a, float b) {
        if (Nvidium.IS_ENABLED) {
            return a;
        }
        return Math.max(a, b);
    }

    @Redirect(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;getRenderDistance()F"))
    private float changeRD(GameRenderer instance) {
        float viewDistance = instance.getRenderDistance();
        if (Nvidium.IS_ENABLED) {
            var dist = Nvidium.config.region_keep_distance * 16;
            return dist == 32 * 16 ? viewDistance : (dist == 256 * 16 ? 9999999 : dist);
        }
        return viewDistance;
    }
}
