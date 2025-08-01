package me.cortex.nvidium.meshletengine;

import it.unimi.dsi.fastutil.longs.*;

public class QuadPriorityQueue {

    private final LongOpenHashSet vertices = new LongOpenHashSet();
    private final Long2IntOpenHashMap counts = new Long2IntOpenHashMap();

    private final LongLinkedOpenHashSet[] queues = {
            new LongLinkedOpenHashSet(),
            new LongLinkedOpenHashSet(),
            new LongLinkedOpenHashSet(),
            new LongLinkedOpenHashSet(),
            new LongLinkedOpenHashSet(),
            new LongLinkedOpenHashSet(),
            new LongLinkedOpenHashSet(),
            new LongLinkedOpenHashSet(),
            new LongLinkedOpenHashSet(),
            new LongLinkedOpenHashSet(),
    }; // TODO there is probably a better way but am too tired

    public QuadPriorityQueue() {
    }

    public void addNeighbors(long vertex, LongArrayList neighbors, long currentQuad, LongLinkedOpenHashSet todo, Long2IntOpenHashMap quad2facing) {
        if (vertices.contains(vertex)) { // If we have already seen this vertex, don't increase priority of related quads
            return;
        }
        vertices.add(vertex);

        int added = 0;
        for (long quad : neighbors) {
            if (quad != currentQuad && todo.contains(quad)) { // Ensure it's not our quad and it hasn't been processed

                if (quad2facing.get(quad) == quad2facing.get(currentQuad)) { // If it's flat relatively to us we double priority
                    int queue = counts.addTo(quad, 2);

                    if (queue > 0) { // Flow to next queue
                        queues[queue - 1].remove(quad);
                    }

                    queues[queue + 1].add(quad);
                    added++;
                } else { // Otherwise just increase priority
                    int queue = counts.addTo(quad, 1);

                    if (queue > 0) { // Flow to next queue
                        queues[queue - 1].remove(quad);
                    }
                    queues[queue].add(quad);
                    added++;
                }

                //System.out.printf("    @PriorityQueue Quad added: %d priority: %d\n", quad, counts.get(quad));
            }
        }
        //System.out.printf("    had %d neighbors, %d added\n", neighbors.size() - 1, added);
    }

    public long getNextQuad() {
        for (int i = queues.length - 1; i >= 0; i--) { // Go from highest priority queue first
            while (!queues[i].isEmpty()) {
                long quad = queues[i].removeFirstLong();
                int count = counts.get(quad);

                if (count == 0) { // We shouldn't be there
                    System.out.printf("    %d count 0\n", quad);
                    continue;
                }
                counts.remove(quad);

                //System.out.printf("\n>Fetch quad: %d priority: %d remaining: %d\n", quad,  count, counts.size());
                return quad;
            }
        }

        if (!counts.isEmpty()) { // We shouldn't be there neither
            System.out.print("/!\\/!\\/!\\/!\\/!\\/!\\/!\\/!\\/!\\Desync");
        }

        return -1;
    }

    public boolean isEmpty() {
        return counts.isEmpty();
    }

    public int size() {
        return counts.size();
    }

    public void flush() {
        for (var queue: queues) {
            queue.clear();
        }
        counts.clear();
        vertices.clear();
    }
}