package me.cortex.nvidium.meshletengine;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.lwjgl.system.MemoryUtil;


import static me.cortex.nvidium.meshletengine.MeshletBuilder.FORMAT_SIZE;
import static me.cortex.nvidium.sodiumCompat.NvidiumCompactChunkVertex.TEXTURE_MAX_VALUE;

public class MeshletEngineDebugger {
    public final static String[] blockIds = new String[] {
            "minecraft:white_stained_glass",
            "minecraft:orange_stained_glass",
            "minecraft:magenta_stained_glass",
            "minecraft:light_blue_stained_glass",
            "minecraft:yellow_stained_glass",
            "minecraft:lime_stained_glass",
            "minecraft:pink_stained_glass",
            "minecraft:gray_stained_glass",
            "minecraft:light_gray_stained_glass",
            "minecraft:cyan_stained_glass",
            "minecraft:purple_stained_glass",
            "minecraft:blue_stained_glass",
            "minecraft:brown_stained_glass",
            "minecraft:green_stained_glass",
            "minecraft:red_stained_glass",
            "minecraft:black_stained_glass",
    };

    public final static MeshletEngineDebugger INSTANCE = new MeshletEngineDebugger();
    public final int[][] UVS;

    MeshletEngineDebugger() {
        UVS = new int[blockIds.length][4];

        // Access the model sprite
        ModelManager modelManager = Minecraft.getInstance().getModelManager();
        BlockModelShaper modelShaper = modelManager.getBlockModelShaper();

        for (int i = 0; i < blockIds.length; i++) {
            ResourceLocation id = ResourceLocation.tryParse(blockIds[i]);
            Block block = BuiltInRegistries.BLOCK.get(id).get().value();
            BlockState state = block.defaultBlockState();

            TextureAtlasSprite sprite = modelShaper.getParticleIcon(state);

            // Grab our dirty the UVs hacks
            float u0 = sprite.getU0();
            float u1 = sprite.getU1();
            float v0 = sprite.getV0();
            float v1 = sprite.getV1();

            UVS[i][0] = ((Math.round(u0 * TEXTURE_MAX_VALUE) & 0xFFFF) << 0) | ((Math.round(v0 * TEXTURE_MAX_VALUE) & 0xFFFF) << 16);
            UVS[i][1] = ((Math.round(u0 * TEXTURE_MAX_VALUE) & 0xFFFF) << 0) | ((Math.round(v1 * TEXTURE_MAX_VALUE) & 0xFFFF) << 16);
            UVS[i][2] = ((Math.round(u1 * TEXTURE_MAX_VALUE) & 0xFFFF) << 0) | ((Math.round(v1 * TEXTURE_MAX_VALUE) & 0xFFFF) << 16);
            UVS[i][3] = ((Math.round(u1 * TEXTURE_MAX_VALUE) & 0xFFFF) << 0) | ((Math.round(v0 * TEXTURE_MAX_VALUE) & 0xFFFF) << 16);

            //System.out.printf("UV box: (%.2f, %.2f) → (%.2f, %.2f) %d|%d|%d|%d %n", u0, v0, u1, v1, UVS[i][0], UVS[i][1], UVS[i][2], UVS[i][3]);
        }
    }

    public void injectQuadDebug(long addr, int meshletId) {
        MemoryUtil.memPutInt(addr + 12 + FORMAT_SIZE * 0, UVS[meshletId % UVS.length][0]);
        MemoryUtil.memPutInt(addr + 12 + FORMAT_SIZE * 1, UVS[meshletId % UVS.length][1]);
        MemoryUtil.memPutInt(addr + 12 + FORMAT_SIZE * 2, UVS[meshletId % UVS.length][2]);
        MemoryUtil.memPutInt(addr + 12 + FORMAT_SIZE * 3, UVS[meshletId % UVS.length][3]);
    }
}
