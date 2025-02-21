package me.cortex.nvidium.mixin.minecraft;

import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.LightmapTextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LightmapTextureManager.class)
public interface LightMapAccessor {
    @Accessor()
    SimpleFramebuffer getLightmapFramebuffer();
}
