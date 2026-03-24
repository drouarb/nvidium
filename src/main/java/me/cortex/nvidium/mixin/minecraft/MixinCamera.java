package me.cortex.nvidium.mixin.minecraft;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import me.cortex.nvidium.Nvidium;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Camera.class)
public class MixinCamera {
    @ModifyExpressionValue(
            method = "update",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/lang/Math;max(FF)F"
            )
    )
    private float nvidium$modifyDepthFar(float original) {
        if (Nvidium.IS_ENABLED) {
            return 16 * 512f;
        }
        return original;
    }
}
