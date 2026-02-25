package me.cortex.nvidium.mixin.sodium;

import net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.caffeinemc.mods.sodium.client.render.viewport.frustum.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Viewport.class)
public interface ViewportAccessor {
    @Accessor("frustum")
    Frustum nvidium$getFrustum();

    @Accessor("transform")
    CameraTransform nvidium$getTransform();
}
