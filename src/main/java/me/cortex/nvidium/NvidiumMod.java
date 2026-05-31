package me.cortex.nvidium;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

// NeoForge entrypoint. Nvidium does its real initialization lazily through mixins
// (see mixin.minecraft.MixinWindow -> Nvidium.checkSystemIsCapable), so this class
// only needs to exist so NeoForge recognizes the "nvidium" mod id.
@Mod(value = "nvidium", dist = Dist.CLIENT)
public class NvidiumMod {
    public NvidiumMod() {
    }
}
