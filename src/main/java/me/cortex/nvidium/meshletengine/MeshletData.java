package me.cortex.nvidium.meshletengine;

import net.caffeinemc.mods.sodium.client.util.NativeBuffer;

public record MeshletData(NativeBuffer meshlet,
                          int meshletCount,
                          NativeBuffer vertex,
                          int vertexCount,
                          NativeBuffer index,
                          NativeBuffer attributes,
                          int quadCount) {
    public void delete() {
        meshlet.free();
        vertex.free();
        index.free();
        attributes.free();
    }
}
