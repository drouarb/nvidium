#ifdef USE_SODIUM_VERTEX_FORMAT
struct Vertex {
    uint hi;
    uint lo;
    uint color;

    uint16_t u;
    uint16_t v;

    uint8_t blockLight;
    uint8_t skyLight;
    uint8_t material;
    uint8_t section;
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
    int   tranlucentQuadCount;
    int   translucencyDataIdx;
};

struct Region {
    uint64_t a;
    uint64_t b;
};

ivec3 unpackRegionSize(Region region) {
    return ivec3((region.a>>59)&7, region.a>>62, (region.a>>56)&7);
}

uint unpackRegionTransformId(Region region) {
    return uint((region.b>>(64-24-10))&((1<<10)-1));
}

ivec3 unpackRegionPosition(Region region) {
    //TODO: optimize
    int x = int(int64_t(region.a<<(64-24-24))>>(64-24));
    int y = (int(region.a)<<8)>>8;
    int z = int(int64_t(region.b)>>(64-24));
    return ivec3(x,y,z);
}

int unpackRegionCount(Region region) {
    return int((region.a>>48)&255);
}

bool sectionEmpty(ivec4 header) {
    header.y &= ~0x1FF<<17;
    return header == ivec4(0);
}

layout(buffer_reference, std430, buffer_reference_align = 1)
restrict buffer U8Ptr {
    uint8_t data[];
};

layout(buffer_reference, buffer_reference_align = 2)
readonly restrict buffer U16Ptr {
    uint16_t data[];
};

layout(buffer_reference, buffer_reference_align = 4)
readonly restrict buffer U32Ptr {
    uint data[];
};

layout(buffer_reference, buffer_reference_align = 8)
readonly restrict buffer U64Ptr {
    uint64_t data[];
};

layout(buffer_reference, std430, buffer_reference_align = 16)
readonly restrict buffer RegionPtr {
    Region data[];
};

layout(buffer_reference, std430)
restrict buffer SectionPtr {
    Section data[];
};

layout(buffer_reference, std430)
restrict buffer U8vec3Ptr {
    u8vec3 data[];
};

layout(buffer_reference, std430, buffer_reference_align = 16)
restrict buffer Uvec4Ptr {
    uvec4 data[];
};

layout(buffer_reference, std430) // TODO SIZE ??
restrict buffer VertexPtr {
    Vertex data[];
};

layout(buffer_reference, std430)
restrict buffer Mat4Ptr {
    mat4 data[];
};


layout(std140, binding=0, set=0) uniform SceneData {
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
    U16Ptr regionIndicies;//Pointer to block of memory at the end of the SceneData struct, also mapped to be a uniform
    RegionPtr regionData;
    SectionPtr sectionData;
    //NOTE: for the following, can make it so that region visibility actually uses section visibility array
    U8Ptr regionVisibility;
    U8Ptr sectionVisibility;
    U8vec3Ptr sectionIndices;
    //Terrain command buffer, the first 4 bytes are actually the count
    Uvec4Ptr terrainCommandBuffer;
    Uvec4Ptr translucencyCommandBuffer;
    Uvec4Ptr temporalCommandBuffer;

    U16Ptr sortingRegionList;

    //TODO:FIXME: only apply non readonly to translucency mesh
    VertexPtr terrainData;//readonly
    U32Ptr translucencyIndexData;

    //TODO: possibly make this a uniform instead of a buffer, but it might get quite large is the issue
    Mat4Ptr transformationArray;
    U64Ptr originArray;

    //readonly restrict u64vec4 *terrainData;
    //uvec4 *terrainData;

    U32Ptr statistics_buffer;

    vec2 screenSize;

    vec4 fogColour;
    vec2 environmentFog;
    vec2 renderFog;

    vec2 texCoordShrink;
    vec2 texelSize;

    uint flags;

    //align(2)
    uint16_t regionCount;//Number of regions in regionIndicies
    //align(1)
    uint8_t frameId;
};

mat4 getRegionTransformation(Region region) {
    return transformationArray.data[unpackRegionTransformId(region)];
}

ivec3 unpackOriginOffsetId(uint id) {
    uint64_t val = originArray.data[id];
    int x = (int(uint(val&0x1ffffff))<<7)>>7;
    int y = (int(uint((val>>50)&0x3fff))<<18)>>18;
    int z = (int(uint((val>>25)&0x1ffffff))<<7)>>7;
    return ivec3(x,y,z);
}

bool useBlockFaceCulling() {
    return (flags&1)!=0;
}

bool useRGSS() {
    return (flags&2)!=0;
}