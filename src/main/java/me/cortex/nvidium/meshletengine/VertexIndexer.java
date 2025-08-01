package me.cortex.nvidium.meshletengine;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;

public class VertexIndexer {
    private final int MAX_VERTEX_COUNT;
    private final Long2IntOpenHashMap indexMap = new Long2IntOpenHashMap();
    private final LongArrayList vertices = new LongArrayList();

    public VertexIndexer(int maxVertex) {
        indexMap.defaultReturnValue(-1);
        MAX_VERTEX_COUNT = maxVertex;
    }

    public int addVertex(long vertex) {
        int idx = indexMap.get(vertex);
        if (idx != -1) return idx; // already exists

        int newIndex = vertices.size();
        vertices.add(vertex);
        indexMap.put(vertex, newIndex);
        return newIndex;
    }

    public boolean canAdd(long v0, long v1, long v2, long v3) {
        int cost = (indexMap.get(v0) == -1 ? 1 : 0) +
                   (indexMap.get(v1) == -1 ? 1 : 0) +
                   (indexMap.get(v2) == -1 ? 1 : 0) +
                   (indexMap.get(v3) == -1 ? 1 : 0);

        if (cost + vertices.size() >= MAX_VERTEX_COUNT) {
            System.out.printf("Meshlet full of vertex, need %d, used %d, max %d`\n", cost, indexMap.size(), MAX_VERTEX_COUNT);
            return false;
        }
        return true;
    }

    public int getVertexCount() {
        return vertices.size();
    }
}
