const uint POSITION_BITS        = 20u;
const uint POSITION_MAX_COORD   = 1u << POSITION_BITS;
const uint POSITION_MAX_VALUE   = POSITION_MAX_COORD - 1u;

const uint TEXTURE_BITS         = 15u;
const uint TEXTURE_MAX_COORD    = 1u << TEXTURE_BITS;
const uint TEXTURE_MAX_VALUE    = TEXTURE_MAX_COORD - 1u;

const float VERTEX_SCALE = 32.0 / float(POSITION_MAX_COORD);
const float VERTEX_OFFSET = -8.0;

uvec3 _deinterleave_u20x3(uint vId) {
    uvec3 hi = (uvec3(pool[vId].x) >> uvec3(0u, 10u, 20u)) & 0x3FFu;
    uvec3 lo = (uvec3(pool[vId].y) >> uvec3(0u, 10u, 20u)) & 0x3FFu;

    return (hi << 10u) | lo;
}

vec3 decodeVertexPosition(uint vId) {
    return (_deinterleave_u20x3(vId) * VERTEX_SCALE) + VERTEX_OFFSET;
}

vec2 decodeVertexRawUV(uint aId) {
    return vec2(pool[aId].y & 0xFFFF & TEXTURE_MAX_VALUE, (pool[aId].y >> 16) & 0xFFFF & TEXTURE_MAX_VALUE) / float(TEXTURE_MAX_COORD);
}

vec2 decodeVertexUVBias(uint aId) {
    return mix(vec2(-1.0), vec2(1.0), bvec2(uvec2(pool[aId].y & 0xFFFF, pool[aId].y >> 16) >> TEXTURE_BITS));
}

vec2 decodeVertexUV(uint aId) {
    return (decodeVertexUVBias(aId) * texCoordShrink) + decodeVertexRawUV(aId);
}

vec2 decodeLightUV(uint aId) {
    return vec2(pool[aId].z & 0xFF, (pool[aId].z >> 8) & 0xFF)/256.0;
}

bool hasMipping(uint aId) {
    return bool((pool[aId].z >> 16) & 1);
}

uint rawVertexAlphaCutoff(uint aId) {
    return (pool[aId].z >> 17) & 3;
}

vec4 decodeVertexColour(uint aId) {
    uvec3 packed_color = (uvec3(pool[aId].x) >> uvec3(0, 8, 16)) & uvec3(0xFFu);
    return vec4(vec3(packed_color) * COLOR_SCALE, 1);
}