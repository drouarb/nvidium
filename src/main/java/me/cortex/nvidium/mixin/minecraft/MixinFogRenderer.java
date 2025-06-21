package me.cortex.nvidium.mixin.minecraft;

import me.cortex.nvidium.Nvidium;
import net.minecraft.client.render.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(FogRenderer.class)
public class MixinFogRenderer {
    @ModifyVariable(
            method = "applyFog(Lnet/minecraft/client/render/Camera;IZLnet/minecraft/client/render/RenderTickCounter;FLnet/minecraft/client/world/ClientWorld;)Lorg/joml/Vector4f;",
            at = @At(value = "STORE"),
            ordinal = 2
    )
    private float modifyFogRD(float viewDistance) {
        if (Nvidium.IS_ENABLED) {
            return Math.max(viewDistance, Nvidium.config.region_keep_distance * 16);
        }
        return viewDistance;
    }
}
