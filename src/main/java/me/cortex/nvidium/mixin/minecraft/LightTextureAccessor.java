package me.cortex.nvidium.mixin.minecraft;

import com.mojang.blaze3d.pipeline.TextureTarget;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LightTexture.class)
public interface LightTextureAccessor {
    @Accessor()
    TextureTarget getTarget();
}
