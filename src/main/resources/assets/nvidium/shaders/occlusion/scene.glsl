#ifdef USE_SODIUM_VERTEX_FORMAT
struct Vertex {
    uint hi;
    uint lo;
    uint color;

    uint uv;

    /*
    uint8_t blockLight;
    uint8_t skyLight;
    uint8_t material;
    uint8_t section;
    */
    uint data;
};
#else
#define Vertex uvec4
#endif

// this is cause in the section rasterizer you get less cache misses thus higher throughput
struct Section {
    ivec4 header;
    //Header.x -> 0-3=offsetx 4-7=sizex 8-31=chunk x
    //Header.y -> 0-3=offsetz 4-7=sizez 8-31=chunk z
    //Header.z -> 0-3=offsety 4-7=sizey 8-15=chunk y
    //Header.w -> quad offset
    ivec4 renderRanges;
    int   translucencyDataIdx;
};

struct Region {
    uint64_t a;
    uint64_t b;
};

struct UploadControl {
    uint uploadStart;
    uint quadCount;
    uint vtxOutputIdx;
};

struct HashMapData {
    uint key;
    uint count;
    uint x;
    uint y;
    uint z;
};

ivec3 unpackRegionSize(Region region) {
    return ivec3(uint(region.a>>59) & 7u, region.a >> 62, uint(region.a>>56) & 7u);
}

uint unpackRegionTransformId(Region region) {
    return uint(region.b>>(64-24-10)) & uint((1<<10)-1);
}

ivec3 unpackRegionPosition(Region region) {
    //TODO: optimize
    int x = int(int64_t(region.a<<(64-24-24))>>(64-24));
    int y = (int(region.a)<<8)>>8;
    int z = int(int64_t(region.b)>>(64-24));
    return ivec3(x,y,z);
}

int unpackRegionCount(Region region) {
    return int(region.a>>48) & 255;
}

bool sectionEmpty(ivec4 header) {
    header.y &= ~0x1FF<<17;
    return header == ivec4(0);
}


layout(std140, binding=0) uniform SceneData {
    //Need to basicly go in order of alignment
    //align(16)
    mat4 MVP;
    #ifdef RENDER_FOG
    mat4 MVPInv;
    #endif
    ivec4 chunkPosition;
    vec4 subchunkOffset;

    //vec4  subChunkPosition;//The subChunkTranslation is already done inside the MVP
    //align(8)
    /*
    readonly restrict uint16_t *regionIndicies_OLD;//Pointer to block of memory at the end of the SceneData struct, also mapped to be a uniform
    readonly restrict Region *regionData_OLD;
    restrict Section *sectionData_OLD;
    //NOTE: for the following, can make it so that region visibility actually uses section visibility array
    restrict uint8_t *regionVisibility_OLD;
    restrict uint8_t *sectionVisibility_OLD;
    //Terrain command buffer, the first 4 bytes are actually the count
    writeonly restrict uvec2 *terrainCommandBuffer_OLD;
    writeonly restrict uvec2 *translucencyCommandBuffer_OLD;

    readonly restrict uint16_t *sortingRegionList_OLD;

    //TODO:FIXME: only apply non readonly to translucency mesh
    restrict Vertex *terrainData_OLD;//readonly
    restrict uint   *translucencyIndexData_OLD;

    //TODO: possibly make this a uniform instead of a buffer, but it might get quite large is the issue
    readonly restrict mat4 *transformationArray_OLD;
    readonly restrict uint64_t *originArray_OLD;

    //readonly restrict u64vec4 *terrainData;
    //uvec4 *terrainData;

    uint32_t *statistics_buffer_OLD;
    //*/
    uint64_t dummy1;
    uint64_t dummy2;
    uint64_t dummy3;
    uint64_t dummy4;
    uint64_t dummy5;
    uint64_t dummy6;
    uint64_t dummy7;
    uint64_t dummy8;
    uint64_t dummy9;
    uint64_t dummy10;
    uint64_t dummy11;
    uint64_t dummy12;
    uint64_t dummy13;

    vec2 screenSize;

    vec4 fogColour;
    vec2 environmentFog;
    vec2 renderFog;

    vec2 texCoordShrink;
    vec2 texelSize;

    uint flags;

    //align(2)
    uint regionCount;
    //uint16_t regionCount;//Number of regions in regionIndicies
    //align(1)
    uint frameId;
};

layout(std140, binding=11) uniform transformationArrayUniform {
    mat4 transformationArray[1024]; // TODO AUTO
};

layout(std140, binding=12) uniform originArrayUniform {
    uint64_t originArray[1024]; // TODO AUTO
};

mat4 getRegionTransformation(Region region) {
    return transformationArray[unpackRegionTransformId(region)];
}

ivec3 unpackOriginOffsetId(uint id) {
    uint64_t val = originArray[id];
    int x = (int(uint(val)&0x1ffffffu)<<7)>>7;
    int y = (int(uint(val>>50u)&0x3fffu)<<18)>>18;
    int z = (int(uint(val>>25u)&0x1ffffffu)<<7)>>7;
    return ivec3(x,y,z);
}

bool useBlockFaceCulling() {
    return (flags&1)!=0;
}

bool useRGSS() {
    return (flags&2)!=0;
}