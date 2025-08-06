package me.cortex.nvidium.meshletengine;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.lwjgl.system.MemoryUtil;

public class Meshlet {
    public static final int MAX_VERTEX_COUNT = 96;
    public static final int MAX_TRIANGLE_COUNT = 64;

    public int faceMask = 0;
    private int idxCount = 0;
    private final byte[] indices = new byte[MAX_TRIANGLE_COUNT * 3];
    private final VertexIndexer vtxIndexer = new VertexIndexer(MAX_VERTEX_COUNT);
    private final LongArrayList quads = new LongArrayList();

    public void addQuad(long v0, long v1, long v2, long v3, long quad, int facing) {
        long v0idx = vtxIndexer.addVertex(v0);
        long v1idx = vtxIndexer.addVertex(v1);
        long v2idx = vtxIndexer.addVertex(v2);
        long v3idx = vtxIndexer.addVertex(v3);

        indices[idxCount++] = (byte) v0idx;
        indices[idxCount++] = (byte) v1idx;
        indices[idxCount++] = (byte) v2idx;

        indices[idxCount++] = (byte) v2idx;
        indices[idxCount++] = (byte) v3idx;
        indices[idxCount++] = (byte) v0idx;

        faceMask |= 1 << facing;
        quads.add(quad);
    }

    public boolean canAdd(long v0, long v1, long v2, long v3) {
        if (idxCount + 6 > MAX_TRIANGLE_COUNT * 3) {
            //System.out.print("Meshlet full of triangles\n");
            return false;
        }

        return vtxIndexer.canAdd(v0, v1, v2, v3);
    }

    public long serializeHeader(long addr, int quadOffset, int vtxOffset) {
        MemoryUtil.memPutInt(addr, quadOffset); // temporary quad Offset of our section, will need patch when entering arena
        addr += 4;
        MemoryUtil.memPutInt(addr, vtxOffset); // temporary vtx Offset of our section, will need patch when entering arena
        addr += 4;
        MemoryUtil.memPutInt(addr, faceMask);
        addr += 4;
        MemoryUtil.memPutShort(addr, (short)getQuadCount()); // quadCount
        addr += 2;
        MemoryUtil.memPutShort(addr, (short)getVertexCount()); // vtx Count
        addr += 2;
        return addr;
    }

    public long serializeVertices(long addr) {
        return vtxIndexer.serialize(addr);
    }

    public long serializeIndices(long addr) {
        for (int i = 0; i < idxCount; i++) {
            MemoryUtil.memPutByte(addr++, indices[i]);
        }
        return addr;
    }

    public long serializeAttributes(long addr) {
        for (long quad : quads) {
            for (int i = 0; i < 4; i++) {
                MemoryUtil.memCopy(quad + (MeshletBuilder.FORMAT_SIZE * i) + MeshletBuilder.VTX_SIZE, addr, MeshletBuilder.ATTRIBUTE_SIZE);
                addr += MeshletBuilder.ATTRIBUTE_SIZE;
            }
        }
        return addr;
    }

    public boolean hasData() {
        return idxCount > 0;
    }

    public int getVertexCount() {
        return vtxIndexer.getVertexCount();
    }

    public int getTriangleCount() {
        return idxCount / 3;
    }

    public int getQuadCount() {
        return quads.size();
    }
}
