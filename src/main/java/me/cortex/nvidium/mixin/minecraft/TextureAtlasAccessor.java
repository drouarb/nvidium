package me.cortex.nvidium.mixin.minecraft;

import net.minecraft.client.renderer.texture.TextureAtlas;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(TextureAtlas.class)
public interface TextureAtlasAccessor {
    @Invoker("getWidth")
    int nvidium$getWidth();

    @Invoker("getHeight")
    int nvidium$getHeight();
}
