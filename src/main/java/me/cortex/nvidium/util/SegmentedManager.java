package me.cortex.nvidium.util;

import java.util.Arrays;

public class SegmentedManager {
    public static final long SIZE_LIMIT = -1;

    private final int ADDR_BITS = 34;//This gives max size per allocation of 2^30 and max address of 2^39
    private final int SIZE_BITS = 64 - ADDR_BITS;
    private final long SIZE_MSK = (1L<<SIZE_BITS)-1;
    private final long ADDR_MSK = (1L<<ADDR_BITS)-1;
    private final LongSortedArray FREE = new LongSortedArray();//Size Address
    private final LongSortedArray TAKEN = new LongSortedArray();//Address Size

    private long sizeLimit = Long.MAX_VALUE;
    private long totalSize;
    //Flags
    public boolean resized;//If the required memory of the entire buffer grew

    public long getSize() {
        return totalSize;
    }

    public long alloc(int size) {
        if (size == 0) throw new IllegalArgumentException();
        long slot = FREE.ceiling((long) size << ADDR_BITS);
        if (slot == -1) {//No free space for allocation
            //Create new allocation
            resized = true;
            long addr = totalSize;
            if (totalSize+size>sizeLimit) {
                return SIZE_LIMIT;
            }
            totalSize += size;
            TAKEN.add((addr<<SIZE_BITS)|((long) size));
            return addr;
        } else {
            FREE.remove(slot);
            if ((slot >>> ADDR_BITS) == size) {//If the allocation and slot is the same size, just add it to the taken
                TAKEN.add(((slot&ADDR_MSK)<<SIZE_BITS)|(slot >>> ADDR_BITS));
            } else {
                TAKEN.add(((slot&ADDR_MSK)<<SIZE_BITS)|size);
                FREE.add((((slot >>> ADDR_BITS)-size)<<ADDR_BITS)|((slot&ADDR_MSK)+size));
            }
            resized = false;
            return slot&ADDR_MSK;
        }
    }

    public int free(long addr) {//Returns size of freed memory
        addr &= ADDR_MSK;//encase addr stores shit in its upper bits
        long slot = TAKEN.ceiling(addr<<SIZE_BITS);
        if (slot == -1 || slot >>> SIZE_BITS != addr) {
            throw new IllegalStateException();
        }
        long size = slot&SIZE_MSK;
        TAKEN.remove(slot);

        //Note: if there is a previous entry, it means that it is guaranteed for the ending address to either
        // be the addr, or indicate a free slot that needs to be merged
        long prevSlot = TAKEN.lower(addr<<SIZE_BITS);
        if (prevSlot != -1) {
            long endAddr = (prevSlot>>>SIZE_BITS) + (prevSlot&SIZE_MSK);
            if (endAddr != addr) {//It means there is a free slot that needs to get merged into
                long delta = (addr - endAddr);
                FREE.remove((delta<<ADDR_BITS)|endAddr);//Free the slot to be merged into
                //Generate a new slot to get put into FREE
                slot = (endAddr<<SIZE_BITS) | ((slot&SIZE_MSK) + delta);
            }
        }//If there is no previous it means were at the start of the buffer, we might need to merge with block 0 if we are not block 0
        else if (!FREE.isEmpty()) {// if free is not empty it means we must merge with block of free starting at 0
            if (FREE.remove(addr<<ADDR_BITS)) {//Attempt to remove block 0, this is very dodgy as it assumes block zero is 0 addr n size
                slot = addr + size;//slot at address 0 and size of 0 block + new block
            }
        }

        //If there is a next element it is guarenteed to either be the next block, or indicate that there is
        // a block that needs to be merged into
        long nextSlot = TAKEN.higher(addr<<SIZE_BITS);
        if (nextSlot != -1) {
            long endAddr = (slot>>>SIZE_BITS) + (slot&SIZE_MSK);
            if (endAddr != nextSlot>>>SIZE_BITS) {//It means there is a memory block to be merged in FREE
                long delta = ((nextSlot>>>SIZE_BITS) - endAddr);
                FREE.remove((delta<<ADDR_BITS)|endAddr);
                slot = (slot&(ADDR_MSK<<SIZE_BITS)) | ((slot&SIZE_MSK) + delta);
            }
        }// if there is no next block it means that we have reached the end of the allocation sections and we can shrink the buffer
        else {
            resized = true;
            totalSize -= (slot&SIZE_MSK);
            return (int) size;
        }

        resized = false;
        //Need to swap around the slot to be in FREE format
        slot = (slot>>>SIZE_BITS) | (slot<<ADDR_BITS);
        FREE.add(slot);//Add the free slot into segments
        return (int) size;
    }

    //Attempts to expand an allocation, returns true on success
    public boolean expand(long addr, int extra) {
        addr &= ADDR_MSK;//encase addr stores shit in its upper bits
        long slot = TAKEN.ceiling(addr<<SIZE_BITS);
        if (slot == -1 || slot >>> SIZE_BITS != addr) {
            return false;
        }
        long updatedSlot = (slot & (ADDR_MSK << SIZE_BITS)) | ((slot & SIZE_MSK) + extra);
        resized = false;
        long nextSlot = TAKEN.higher(addr<<SIZE_BITS);
        if (nextSlot != -1) {
            long endAddr = (slot>>>SIZE_BITS)+(slot&SIZE_MSK);
            long delta = (nextSlot>>>SIZE_BITS) - endAddr;
            if (extra <= delta) {
                FREE.remove((delta<<ADDR_BITS)|endAddr);//Should assert this
                TAKEN.remove(slot);//Remove the allocation so it can be updated
                TAKEN.add(updatedSlot);//Update the taken allocation
                if (extra != delta) {//More space than needed, need to add a new FREE block
                    FREE.add(((delta-extra)<<ADDR_BITS)|(endAddr+extra));
                }
                //else There is exactly enough free space, so removing the free block and updating the allocation is enough
                return true;
            } else {
                return false;//Not enough room to expand
            }
        } else {//We are at the end of the buffer, we can expand as we like
            if (totalSize+extra>sizeLimit)//If expanding and we would exceed the size limit, dont resize
                return false;
            TAKEN.remove(slot);
            TAKEN.add(updatedSlot);
            totalSize += extra;
            resized = true;
            return true;
        }
    }

    public long getSize(long addr) {
        addr &= ADDR_MSK;
        long slot = TAKEN.ceiling(addr << SIZE_BITS);
        if (slot == -1 || slot >>> SIZE_BITS != addr)
            throw new IllegalArgumentException();
        return slot&SIZE_MSK;
    }

    /**
     * A primitive long-based sorted array that provides allocation-free search and update operations.
     * While O(N) for insertions and removals, it offers excellent cache locality and zero GC pressure.
     */
    private static final class LongSortedArray {
        private long[] array = new long[16];
        private int size = 0;

        public void add(long key) {
            int i = Arrays.binarySearch(array, 0, size, key);
            if (i < 0) {
                i = -i - 1;
                if (size == array.length) array = Arrays.copyOf(array, array.length << 1);
                System.arraycopy(array, i, array, i + 1, size - i);
                array[i] = key;
                size++;
            }
        }

        public boolean remove(long key) {
            int i = Arrays.binarySearch(array, 0, size, key);
            if (i >= 0) {
                System.arraycopy(array, i + 1, array, i, size - i - 1);
                size--;
                return true;
            }
            return false;
        }

        public long ceiling(long key) {
            int i = Arrays.binarySearch(array, 0, size, key);
            if (i >= 0) return array[i];
            i = -i - 1;
            return i < size ? array[i] : -1;
        }

        public long lower(long key) {
            int i = Arrays.binarySearch(array, 0, size, key);
            if (i >= 0) return i > 0 ? array[i - 1] : -1;
            i = -i - 1;
            return i > 0 ? array[i - 1] : -1;
        }

        public long higher(long key) {
            int i = Arrays.binarySearch(array, 0, size, key);
            if (i >= 0) return i < size - 1 ? array[i + 1] : -1;
            i = -i - 1;
            return i < size ? array[i] : -1;
        }

        public boolean isEmpty() { return size == 0; }
    }

    public void setLimit(long size) {
        this.sizeLimit = size;
    }
}
