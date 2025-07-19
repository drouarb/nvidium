#define MESH_WORKLOAD_PER_INVOCATION 32

taskNV out Task {
    vec3 origin;
    uint baseOffset;
    uint quadCount;
    uint transformationId;

    //Binary search indexs and data
    uvec4 binIa;
    uvec4 binIb;
    uvec4 binVa;
    uvec4 binVb;

#ifdef TRANSLUCENT_PASS
    int translucencyIdx;
#endif
};

void putBinData(inout uint idx, inout uint lastIndex, uint offset, uint nextOffset) {
    uint len = nextOffset - offset;
    uint id = idx++;
    if (id < 4) {
        binIa[id] = lastIndex + len;
        binVa[id] = offset;
    } else {
        binIb[id - 4] = lastIndex + len;
        binVb[id - 4] = offset;
    }
    lastIndex += len;
}

//Populate the tasks with respect to the chunk face visibility
void populateTasks(ivec3 relChunkPos, uvec4 ranges, uvec4 translucencyRanges) {
    //TODO: make the ranges cumulate up, this means that we can fit much much more data per chunk
    // as the range will be spred across all the offsets since they are not the absolute offset

    //Hacky thing to render all block faces if the flag is not set
    if (!useBlockFaceCulling()) {
        relChunkPos = ivec3(0);
    }

    uint idx = 0;
    uint lastIndex = 0;

    binIa = uvec4(0);
    binIb = uvec4(0);

    // And now we sum all translucent ranges
    uint fr = ((translucencyRanges.x&0xFFFF) + ((translucencyRanges.x>>16)&0xFFFF) +
               (translucencyRanges.y&0xFFFF) + ((translucencyRanges.y>>16)&0xFFFF) +
               (translucencyRanges.z&0xFFFF) + ((translucencyRanges.z>>16)&0xFFFF) +
               (translucencyRanges.w&0xFFFF)
    );

    uint delta = (ranges.x&0xFFFF);
    if (relChunkPos.x <= 0 && delta > 0) {
        putBinData(idx, lastIndex, fr, fr + delta);
    }
    fr += ranges.x&0xFFFF;

    delta = ((ranges.x>>16)&0xFFFF);
    if (relChunkPos.y <= 0 && delta > 0) {
        putBinData(idx, lastIndex, fr, fr + delta);
    }
    fr += (ranges.x>>16)&0xFFFF;

    delta = ranges.y&0xFFFF;
    if (relChunkPos.z <= 0 && delta > 0) {
        putBinData(idx, lastIndex, fr, fr + delta);
    }
    fr += ranges.y&0xFFFF;

    delta = (ranges.y>>16)&0xFFFF;
    if (relChunkPos.x >= 0 && delta > 0) {
        putBinData(idx, lastIndex, fr, fr + delta);
    }
    fr += (ranges.y>>16)&0xFFFF;

    delta = ranges.z&0xFFFF;
    if (relChunkPos.y >= 0 && delta > 0) {
        putBinData(idx, lastIndex, fr, fr + delta);
    }
    fr += ranges.z&0xFFFF;

    delta = (ranges.z>>16)&0xFFFF;
    if (relChunkPos.z >= 0 && delta > 0) {
        putBinData(idx, lastIndex, fr, fr + delta);
    }
    fr += (ranges.z>>16)&0xFFFF;

    //TODO: Put unsigned quads at the begining? since it should be cheaper
    putBinData(idx, lastIndex, fr, fr + (ranges.w&0xFFFF));




    quadCount = lastIndex;

    //Emit enough mesh shaders such that max(gl_GlobalInvocationID.x)>=2*quadCount
    gl_TaskCountNV = ((lastIndex*2)+MESH_WORKLOAD_PER_INVOCATION-1)/MESH_WORKLOAD_PER_INVOCATION;
}