package me.cortex.nvidium.meshletengine;

public class Meshlet {
    private final int MAX_VERTEX_COUNT = 96;
    private final int MAX_TRIANGLE_COUNT = 64;

    private int idxCount = 0;
    private final char[] indices = new char[MAX_TRIANGLE_COUNT * 3];
    private final VertexIndexer vtxIndexer = new VertexIndexer(MAX_VERTEX_COUNT);

    public void addQuad(long v0, long v1, long v2, long v3) {
        long v0idx = vtxIndexer.addVertex(v0);
        long v1idx = vtxIndexer.addVertex(v1);
        long v2idx = vtxIndexer.addVertex(v2);
        long v3idx = vtxIndexer.addVertex(v3);

        indices[idxCount++] = (char)v0idx;
        indices[idxCount++] = (char)v1idx;
        indices[idxCount++] = (char)v2idx;

        indices[idxCount++] = (char)v2idx;
        indices[idxCount++] = (char)v3idx;
        indices[idxCount++] = (char)v0idx;
    }

    public boolean canAdd(long v0, long v1, long v2, long v3) {
        if (idxCount + 6 > MAX_TRIANGLE_COUNT * 3) {
            //System.out.print("Meshlet full of triangles\n");
            return false;
        }

        return vtxIndexer.canAdd(v0, v1, v2, v3);
    }

    public int getVertexCount() {
        return vtxIndexer.getVertexCount();
    }

    public int getTriangleCount() {
        return idxCount / 3;
    }
}
