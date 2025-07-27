package me.cortex.nvidium.mixin.minecraft;

import net.minecraft.client.renderer.texture.TextureAtlas;
import org.spongepowered.asm.mixin.Intrinsic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(TextureAtlas.class)
public interface TextureAtlasAccessor {
    @Intrinsic
    @Invoker("getWidth")
    int nvidium$getWidth();

    @Intrinsic
    @Invoker("getHeight")
    int nvidium$getHeight();
}
