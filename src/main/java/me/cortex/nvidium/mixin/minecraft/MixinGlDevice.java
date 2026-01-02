package me.cortex.nvidium.mixin.minecraft;

import com.mojang.blaze3d.shaders.ShaderSource;
import me.cortex.nvidium.Nvidium;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.mojang.blaze3d.opengl.GlDevice;

@Mixin(GlDevice.class)
public class MixinGlDevice {
    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL;createCapabilities()Lorg/lwjgl/opengl/GLCapabilities;", shift = At.Shift.AFTER), remap = false)
    private void init(long window, int debugVerbosity, boolean synchronous, ShaderSource defaultShaderSource, boolean renderDebugLabels, CallbackInfo ci) {
        Nvidium.checkSystemIsCapable();
    }
}
