package me.cortex.nvidium.meshletengine;

import it.unimi.dsi.fastutil.longs.*;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.caffeinemc.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildOutput;
import net.caffeinemc.mods.sodium.client.render.chunk.data.BuiltSectionMeshParts;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import org.lwjgl.system.MemoryUtil;

public class MeshletBuilder {
    private final int FORMAT_SIZE = 16;
    private final LongLinkedOpenHashSet todo = new LongLinkedOpenHashSet();
    private final Long2ReferenceOpenHashMap<LongArrayList> vtx2quad = new Long2ReferenceOpenHashMap<>();
    private final Long2IntOpenHashMap quad2facing = new Long2IntOpenHashMap();

    private QuadPriorityQueue queue = new QuadPriorityQueue();

    private Meshlet currentMeshlet = new Meshlet();
    private final ObjectArrayList<Meshlet> meshlets = new ObjectArrayList<>();

    private int previousVtxCount = 0;

    public MeshletBuilder() {
    }

    private long[] getQuadVertices(long addr) {
        long[] vertices = new long[4];
        vertices[0] = MemoryUtil.memGetLong(addr + FORMAT_SIZE * 0) & 0x0000_FFFF_FFFF_FFFFL;
        vertices[1] = MemoryUtil.memGetLong(addr + FORMAT_SIZE * 1) & 0x0000_FFFF_FFFF_FFFFL;
        vertices[2] = MemoryUtil.memGetLong(addr + FORMAT_SIZE * 2) & 0x0000_FFFF_FFFF_FFFFL;
        vertices[3] = MemoryUtil.memGetLong(addr + FORMAT_SIZE * 3) & 0x0000_FFFF_FFFF_FFFFL;
        return vertices;
    }

    public String getDebugVertex(long vertex) {
        return String.format("x:%.2f y:%.2f z:%.2f",
                ((vertex >> 0) & 0xFFFF) * (32.0 / 65536.0) - 8.0,
                ((vertex >> 16) & 0xFFFF) * (32.0 / 65536.0) - 8.0,
                ((vertex >> 32) & 0xFFFF) * (32.0 / 65536.0) - 8.0
        );
    }

    public void ingestFacing(long addr, int quadCount, ModelQuadFacing facing) {
        for (long i = 0; i < quadCount; i++) {
            // add our quad to todo list
            todo.add(addr + i * 16 * 4);

            // TODO save quadToFacing ?

            long quad = addr + i * FORMAT_SIZE * 4;
            long[] vertices = getQuadVertices(quad);
            vtx2quad.computeIfAbsent(vertices[0], k -> new LongArrayList()).add(quad);
            vtx2quad.computeIfAbsent(vertices[1], k -> new LongArrayList()).add(quad);
            vtx2quad.computeIfAbsent(vertices[2], k -> new LongArrayList()).add(quad);
            vtx2quad.computeIfAbsent(vertices[3], k -> new LongArrayList()).add(quad);
            quad2facing.put(quad, facing.ordinal());
        }

        previousVtxCount += 4 * quadCount;
        System.out.printf("Ingested %d quads %d vtxs\n", quadCount, vtx2quad.size());
    }

    public void injectQuadDebug(long addr) {
        // Dirty debug to see meshlet inject concrete UV into renderer
        int[][] UV = new int[][]{
                {1543521280, 1610630144, 1610630656, 1543521792}, // white
                {1207965696, 1275074560, 1275075072, 1207966208}, // Light gray
                {402669056, 469777920, 469778432, 402669568}, // Gray
                {536874496, 603983360, 603983872, 536875008}, // black
                {671094784, 738203648, 738204160, 671095296}, // Brown
                {1677732352, 1744841216, 1744841728, 1677732864}, // Red
                {1409293824, 1476402688, 1476403200, 1409294336}, // orange
                {1207973888, 1275082752, 1275083264, 1207974400}, // lime
                {1073741824, 1140850688, 1140851200, 1073742336}, // green
                {1006643200, 1073752064, 1073752576, 1006643712}, // cyan
                {1207960576, 1275069440, 1275069952, 1207961088}, // Light blue
                {603983360, 671092224, 671092736, 603983872}, // Blue concrete
                {1610627584, 1677736448, 1677736960, 1610628096}, // Purple
                {1275074560, 1342183424, 1342183936, 1275075072}, // Magenta
                {1476408320, 1543517184, 1543517696, 1476408832} // Pink

                //{2013281280, 1946172416, 1946172928, 2013281792}, // Stone
                //{1342193152, 1409302016, 1409302528, 1342193664}, // Oak log
                //{201339904, 134231040, 134230528, 201339392}, // dirt
        };
        MemoryUtil.memPutInt(addr + 12 + 0, UV[meshlets.size() % UV.length][0]);
        MemoryUtil.memPutInt(addr + 12 + 16, UV[meshlets.size() % UV.length][1]);
        MemoryUtil.memPutInt(addr + 12 + 32, UV[meshlets.size() % UV.length][2]);
        MemoryUtil.memPutInt(addr + 12 + 48, UV[meshlets.size() % UV.length][3]);
    }

    public boolean processQuad(long addr) {
        long[] vertices = getQuadVertices(addr);

        // Meshlet is full, rotate it
        if (!currentMeshlet.canAdd(vertices[0], vertices[1], vertices[2], vertices[3])) {
            //System.out.printf("====Rotating meshlet %d\n", meshlets.size() + 1);
            meshlets.add(currentMeshlet);
            currentMeshlet = new Meshlet();

            // Reinit queue, it's pointless since we flushed meshlet
            queue.flush();
            return false;
        }

        //System.out.printf("=====Processing quad: %d\n", addr);
        // Remove the quad we are going to add
        todo.remove(addr);
        currentMeshlet.addQuad(vertices[0], vertices[1], vertices[2], vertices[3]);

        injectQuadDebug(addr);

        // Refill queue
        for (int i = 0; i < vertices.length; i++) {
            //System.out.printf("==> V%d (%s)\n", i, getDebugVertex(vertices[i]));
            queue.addNeighbors(vertices[i], vtx2quad.get(vertices[i]), addr, todo, quad2facing);
        }
        return true;
    }

    public void mesh() {
        boolean worked = false;
        while (!todo.isEmpty() || !queue.isEmpty()) {
            worked = true;
            //System.out.printf("@@@@@ Meshing ... todo: %d, queue: %d @@@@@\n", todo.size(), queue.size());
            // First process priority queue
            long quad;
            while ((quad = queue.getNextQuad()) != -1) {
                if (!processQuad(quad))
                    break;
            }

            //System.out.print("======Queue empty, add another quads\n");
            // Once priority queue is done, add one quad, will probably refill priority queue
            LongIterator it = todo.iterator();
            if (!it.hasNext()) {
                continue;
            }
            quad = it.nextLong();
            processQuad(quad);
        }

        if (worked) {
            meshlets.add(currentMeshlet);
            currentMeshlet = new Meshlet();
        }

        int totalVtx = 0;
        for (int i = 0; i < meshlets.size(); i++) {
            Meshlet m = meshlets.get(i);
            //System.out.printf("Meshlet %d | Vtx: %d | Tri: %d\n", i, m.getVertexCount(), m.getTriangleCount());
            totalVtx += m.getVertexCount();
        }
        System.out.printf("Built %d meshlets totalVtx: %d previousVtx: %d compression: %.2f%%\n", meshlets.size(), totalVtx, previousVtxCount, ((float)totalVtx / (float)previousVtxCount) * 100.0);
    }

    public static void work(ChunkBuildOutput result) {
        // TODO Rework mostly for test
        long solidOffset = 0;
        long cutoutOffset = 0;
        long translucentOffset = 0;
        BuiltSectionMeshParts solidData = result.meshes.get(DefaultTerrainRenderPasses.SOLID);
        BuiltSectionMeshParts cutoutData = result.meshes.get(DefaultTerrainRenderPasses.CUTOUT);
        BuiltSectionMeshParts translucentData = result.meshes.get(DefaultTerrainRenderPasses.TRANSLUCENT);

        MeshletBuilder terrainBuilder = new MeshletBuilder();
        for (ModelQuadFacing facing : ModelQuadFacing.values()) {
            System.out.printf("===================================== %s ===================================\n", facing.toString());

            //MeshletBuilder translucentBuilder = new MeshletBuilder();
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

            /*
            if (translucentData != null) {
                translucentBuilder.ingestFacing(
                        MemoryUtil.memAddress(translucentData.getVertexData().getDirectBuffer()) + translucentOffset,
                        translucentData.getVertexCounts()[facing.ordinal()] / 4,
                        facing
                );
                translucentOffset += translucentData.getVertexCounts()[facing.ordinal()] * 16L;
            } */

            //translucentBuilder.mesh();
        }
        terrainBuilder.mesh();
    }
}
