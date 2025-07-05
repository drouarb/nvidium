package me.cortex.nvidium.mixin.minecraft;

import me.cortex.nvidium.Nvidium;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(FogRenderer.class)
public class MixinFogRenderer {
    @ModifyVariable(
            method = "setupFog(Lnet/minecraft/client/Camera;IZLnet/minecraft/client/DeltaTracker;FLnet/minecraft/client/multiplayer/ClientLevel;)Lorg/joml/Vector4f;",
            at = @At(value = "STORE"),
            ordinal = 2
    )
    private float modifyFogRD(float viewDistance) {
        if (Nvidium.IS_ENABLED && Nvidium.config.region_keep_distance != 32) {
            return Math.max(viewDistance, Nvidium.config.region_keep_distance * 16);
        }
        return viewDistance;
    }
}
