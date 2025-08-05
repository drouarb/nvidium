package me.cortex.nvidium.sodiumCompat;

import me.cortex.nvidium.meshletengine.MeshletData;
import net.caffeinemc.mods.sodium.client.util.NativeBuffer;
import org.joml.Vector3i;

//Computed on the build thread instead of the render thread saving alot of 1% lows
public record RepackagedSectionOutput(int quads,
                                      NativeBuffer geometry,
                                      short[] offsets,
                                      Vector3i min,
                                      Vector3i size,
                                      MeshletData meshlet) {
    public void delete() {
        geometry.free();
        meshlet.delete();
    }
}
