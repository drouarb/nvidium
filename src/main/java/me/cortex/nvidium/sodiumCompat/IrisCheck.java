package me.cortex.nvidium.sodiumCompat;

import net.irisshaders.iris.api.v0.IrisApi;
import net.neoforged.fml.ModList;

public class IrisCheck {
    public static final boolean IRIS_LOADED = ModList.get().isLoaded("iris");

    private static boolean checkIrisShaders() {
        return IrisApi.getInstance().isShaderPackInUse();
    }

    public static boolean checkIrisShouldDisable() {
        return !(IRIS_LOADED && checkIrisShaders());
    }
}
