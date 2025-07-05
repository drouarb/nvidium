package me.cortex.nvidium.mixin.minecraft;

import me.cortex.nvidium.Nvidium;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.mojang.blaze3d.opengl.GlDevice;
import java.util.function.BiFunction;

@Mixin(GlDevice.class)
public class MixinGlDevice {
    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL;createCapabilities()Lorg/lwjgl/opengl/GLCapabilities;", shift = At.Shift.AFTER), remap = false)
    private void init(long contextId, int debugVerbosity, boolean sync, BiFunction shaderSourceGetter, boolean renderDebugLabels, CallbackInfo ci) {
        Nvidium.checkSystemIsCapable();
    }
}
