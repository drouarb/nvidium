package me.cortex.nvidium.meshletengine;

import net.caffeinemc.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildOutput;
import net.caffeinemc.mods.sodium.client.render.chunk.data.BuiltSectionMeshParts;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.caffeinemc.mods.sodium.client.util.NativeBuffer;
import org.lwjgl.system.MemoryUtil;

public class MeshletEngine {
    public static void work(ChunkBuildOutput result) {
        // TODO Rework mostly for test
        long solidOffset = 0;
        long cutoutOffset = 0;
        long translucentOffset = 0;
        BuiltSectionMeshParts solidData = result.meshes.get(DefaultTerrainRenderPasses.SOLID);
        BuiltSectionMeshParts cutoutData = result.meshes.get(DefaultTerrainRenderPasses.CUTOUT);
        BuiltSectionMeshParts translucentData = result.meshes.get(DefaultTerrainRenderPasses.TRANSLUCENT);

        MeshletBuilder terrainBuilder = new MeshletBuilder();
        MeshletBuilder translucentBuilder = new MeshletBuilder();

        for (ModelQuadFacing facing : ModelQuadFacing.values()) {
            System.out.printf("===================================== %s ===================================\n", facing.toString());
            if (solidData != null) {
                terrainBuilder.ingestFacing(
                        MemoryUtil.memAddress(solidData.getVertexData().getDirectBuffer()) + solidOffset,
                        solidData.getVertexCounts()[facing.ordinal()] / 4,
                        facing
                );
                solidOffset += solidData.getVertexCounts()[facing.ordinal()] * 16L;
            }

            if (cutoutData != null) {
                terrainBuilder.ingestFacing(
                        MemoryUtil.memAddress(cutoutData.getVertexData().getDirectBuffer()) + cutoutOffset,
                        cutoutData.getVertexCounts()[facing.ordinal()] / 4,
                        facing
                );
                cutoutOffset += cutoutData.getVertexCounts()[facing.ordinal()] * 16L;
            }

            if (translucentData != null) {
                translucentBuilder.ingestFacing(
                        MemoryUtil.memAddress(translucentData.getVertexData().getDirectBuffer()) + translucentOffset,
                        translucentData.getVertexCounts()[facing.ordinal()] / 4,
                        facing
                );
                translucentOffset += translucentData.getVertexCounts()[facing.ordinal()] * 16L;
            }
        }
        terrainBuilder.mesh();
        translucentBuilder.mesh();

        NativeBuffer headers = new NativeBuffer(terrainBuilder.getHeadersSize() + translucentBuilder.getHeadersSize());
        NativeBuffer vertices = new NativeBuffer(terrainBuilder.getVerticesSize() + translucentBuilder.getVerticesSize());
        NativeBuffer indices = new NativeBuffer(terrainBuilder.getIndicesSize() + translucentBuilder.getIndicesSize());
        NativeBuffer attributes = new NativeBuffer(terrainBuilder.getAttributesSize() + translucentBuilder.getAttributesSize());

        /*
        System.out.println("Header size " + terrainBuilder.getHeadersSize() + " " + translucentBuilder.getHeadersSize());
        System.out.println("Vertices size " + terrainBuilder.getVerticesSize() + " " + translucentBuilder.getVerticesSize());
        System.out.println("Indices size " + terrainBuilder.getIndicesSize() + " " + translucentBuilder.getIndicesSize());
        System.out.println("Attribute size " + terrainBuilder.getAttributesSize() + " " + translucentBuilder.getAttributesSize());
        */

        int[] offsets = terrainBuilder.serialize(
                MemoryUtil.memAddress(headers.getDirectBuffer()),
                MemoryUtil.memAddress(vertices.getDirectBuffer()),
                MemoryUtil.memAddress(indices.getDirectBuffer()),
                MemoryUtil.memAddress(attributes.getDirectBuffer()),
                0, 0
        );

        translucentBuilder.serialize(
                MemoryUtil.memAddress(headers.getDirectBuffer()),
                MemoryUtil.memAddress(vertices.getDirectBuffer()),
                MemoryUtil.memAddress(indices.getDirectBuffer()),
                MemoryUtil.memAddress(attributes.getDirectBuffer()),
                offsets[0], offsets[1]
        );

        headers.free();
        vertices.free();
        indices.free();
        attributes.free();
    }
}
