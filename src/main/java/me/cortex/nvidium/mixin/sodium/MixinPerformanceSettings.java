package me.cortex.nvidium.mixin.sodium;

import me.cortex.nvidium.Nvidium;
import me.cortex.nvidium.config.TranslucencySortingLevel;
import net.caffeinemc.mods.sodium.client.gui.SodiumGameOptions;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.SortBehavior;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SodiumGameOptions.PerformanceSettings.class, remap = false)
public class MixinPerformanceSettings {
    @Inject(method = "getSortBehavior()Lnet/caffeinemc/mods/sodium/client/render/chunk/translucent_sorting/SortBehavior;", at = @At(value = "HEAD"), cancellable = true)
    public void getSortBehavior(CallbackInfoReturnable<SortBehavior> cir) {
        if (Nvidium.IS_ENABLED && Nvidium.config.translucency_sorting_level != TranslucencySortingLevel.SODIUM) {
            cir.setReturnValue(SortBehavior.OFF);
            cir.cancel();
        }
    }
}
