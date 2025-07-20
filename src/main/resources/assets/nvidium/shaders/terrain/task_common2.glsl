#define MESH_WORKLOAD_PER_INVOCATION 32

taskNV out Task {
    //Very compacted search indexs and data
    uvec4 binStarts;
    uvec4 binOffsets;

    vec3 origin;
    uint quadCount;
    uint transformationId;
};

bvec3 and(bvec3 a, bvec3 b) {
    return bvec3(a.x&&b.x, a.y&&b.y, a.z&&b.z);
}

#define BIN(br, cnt) if (br) { if (!pset) {starts[i] = sum; offsets[i++] = off;} sum += cnt; } pset = br; off += cnt;
uint buildBinData(out uvec4 starts, out uvec4 offsets, out uint sum, uint off, uint naa, bvec3 a, bvec3 b, uvec3 cA, uvec3 cB) {
    bool pset = false;
    sum = 0;
    starts = uvec4(-1);
    offsets = uvec4(-1);
    //uint off = off;
    uint i = 0;

    BIN(a.x, cA.x);
    BIN(a.y, cA.y);
    BIN(a.z, cA.z);
    BIN(b.x, cB.x);
    BIN(b.y, cB.y);
    BIN(b.z, cB.z);

    BIN(naa!=0, naa);//Double sided quads

    return i;
}
#undef BIN

//Populate the tasks with respect to the chunk face visibility
void populateTasks(ivec3 relative, uint baseOffset, uvec4 ranges) {
    uvec3 cA_ = ranges.xyz&0xFFFFu;
    uvec3 cB_ = ranges.xyz>>16;
    uvec3 cA = uvec3(cA_.x,cB_.x,cA_.y);
    uvec3 cB = uvec3(cB_.y,cA_.z,cB_.z);

    bvec3 a = and(notEqual(cA, uvec3(0)), lessThanEqual(relative, ivec3(0)));
    bvec3 b = and(notEqual(cB, uvec3(0)), lessThanEqual(ivec3(0), relative));


    uvec4 starts;
    uvec4 offsets;
    uint sum;
    //Note: the + baseOffset here is a cheaky thing which means dont need to add or pass on
    uint ci = buildBinData(starts, offsets, sum, (ranges.w>>16)+baseOffset, ranges.w&0xFFFFu, a, b, cA, cB);

    binStarts = starts;
    binOffsets = offsets-starts;//Make it a delta from start

    quadCount = sum;
    //Emit enough mesh shaders such that max(gl_GlobalInvocationID.x)>=2*quadCount
    gl_TaskCountNV = ((sum*2)+MESH_WORKLOAD_PER_INVOCATION-1)/MESH_WORKLOAD_PER_INVOCATION;
}