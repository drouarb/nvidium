package me.cortex.nvidium.meshletengine;

import it.unimi.dsi.fastutil.longs.*;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.caffeinemc.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import org.lwjgl.system.MemoryUtil;

import java.util.Comparator;

public class MeshletBuilder {
    public final static int FORMAT_SIZE = 16;
    public final static int VTX_SIZE = 6;
    public final static int ATTRIBUTE_SIZE = FORMAT_SIZE - VTX_SIZE;
    public final static long VTX_MASK = 0x0000_FFFF_FFFF_FFFFL;

    public static final int MESHLET_HEADER_SIZE = 4 * 4;

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
        vertices[0] = MemoryUtil.memGetLong(addr + FORMAT_SIZE * 0) & VTX_MASK;
        vertices[1] = MemoryUtil.memGetLong(addr + FORMAT_SIZE * 1) & VTX_MASK;
        vertices[2] = MemoryUtil.memGetLong(addr + FORMAT_SIZE * 2) & VTX_MASK;
        vertices[3] = MemoryUtil.memGetLong(addr + FORMAT_SIZE * 3) & VTX_MASK;
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

            // Map vertices
            long quad = addr + i * FORMAT_SIZE * 4;
            long[] vertices = getQuadVertices(quad);
            vtx2quad.computeIfAbsent(vertices[0], k -> new LongArrayList()).add(quad);
            vtx2quad.computeIfAbsent(vertices[1], k -> new LongArrayList()).add(quad);
            vtx2quad.computeIfAbsent(vertices[2], k -> new LongArrayList()).add(quad);
            vtx2quad.computeIfAbsent(vertices[3], k -> new LongArrayList()).add(quad);

            // Also save facing for later
            quad2facing.put(quad, facing.ordinal());
        }

        previousVtxCount += 4 * quadCount;
        //System.out.printf("Ingested %d quads %d vtxs\n", quadCount, vtx2quad.size());
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
        currentMeshlet.addQuad(vertices[0], vertices[1], vertices[2], vertices[3], addr, quad2facing.get(addr));

        //MeshletEngineDebugger.INSTANCE.injectQuadDebug(addr, meshlets.size());

        // Refill queue
        for (int i = 0; i < vertices.length; i++) {
            //System.out.printf("==> V%d (%s)\n", i, getDebugVertex(vertices[i]));
            queue.addNeighbors(vertices[i], vtx2quad.get(vertices[i]), addr, todo, quad2facing);
        }
        return true;
    }

    public void mesh() {
        while (!todo.isEmpty() || !queue.isEmpty()) {
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

        if (currentMeshlet.hasData()) {
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

    public short[] getOffsets() {
        short[] offsets = new short[8];
        offsets[7] = 0;

        //meshlets.sort((m1, m2) -> m2.faceMask - m1.faceMask);

        int offset = 0;
        for (int i = 0; i < ModelQuadFacing.COUNT; i++) {
            int partOffset = offset;
            while (meshlets.size() > offset && (meshlets.get(offset).faceMask & (1 << i)) != 0) {
                offset++;
            }
            offsets[i] = (short)(offset - partOffset);
            //System.out.println("facing: " + ModelQuadFacing.values()[i].toString() + "count:" + offsets[i]);
        }

        return offsets;
    }

    public int[] serialize(long headerAddr, long vtxAddr, long idxAddr, long attributeAddr, int quadOffset, int vtxOffset) {
        for (Meshlet m : meshlets) {
            headerAddr = m.serializeHeader(headerAddr, quadOffset, vtxOffset);
            quadOffset += m.getQuadCount();
            vtxOffset += m.getVertexCount();

            vtxAddr = m.serializeVertices(vtxAddr);
            idxAddr = m.serializeIndices(idxAddr);
            attributeAddr = m.serializeAttributes(attributeAddr);
        }

        return new int[] {quadOffset, vtxOffset};
    }

    public int getHeadersSize() {
        return meshlets.size() * MESHLET_HEADER_SIZE;
    }

    public int getVerticesSize() {
        return meshlets.stream().mapToInt(Meshlet::getVertexCount).sum() * VTX_SIZE;
    }

    public int getIndicesSize() {
        return meshlets.stream().mapToInt(Meshlet::getTriangleCount).sum() * 3;
    }

    public int getAttributesSize() {
        return meshlets.stream().mapToInt(Meshlet::getQuadCount).sum() * 4 * (FORMAT_SIZE - VTX_SIZE);
    }

    public int getMeshletCount() {
        return meshlets.size();
    }

    public int getVertexCount() {
        return meshlets.stream().mapToInt(Meshlet::getVertexCount).sum();
    }

    public int getQuadCount() {
        return meshlets.stream().mapToInt(Meshlet::getQuadCount).sum();
    }
}
