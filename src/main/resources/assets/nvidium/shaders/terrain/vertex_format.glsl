#define COLOR_SCALE        1.0 / 255.0

#ifdef USE_SODIUM_VERTEX_FORMAT

const uint POSITION_BITS        = 20u;
const uint POSITION_MAX_COORD   = 1u << POSITION_BITS;
const uint POSITION_MAX_VALUE   = POSITION_MAX_COORD - 1u;

const uint TEXTURE_BITS         = 15u;
const uint TEXTURE_MAX_COORD    = 1u << TEXTURE_BITS;
const uint TEXTURE_MAX_VALUE    = TEXTURE_MAX_COORD - 1u;

const float VERTEX_SCALE = 32.0 / float(POSITION_MAX_COORD);
const float VERTEX_OFFSET = -8.0;

uvec3 _deinterleave_u20x3(Vertex v) {
    uvec3 hi = (uvec3(v.hi) >> uvec3(0u, 10u, 20u)) & 0x3FFu;
    uvec3 lo = (uvec3(v.lo) >> uvec3(0u, 10u, 20u)) & 0x3FFu;

    return (hi << 10u) | lo;
}

vec3 decodeVertexPosition(Vertex v) {
    return (_deinterleave_u20x3(v) * VERTEX_SCALE) + VERTEX_OFFSET;
}

vec2 decodeVertexRawUV(Vertex v) {
    return vec2(v.u & TEXTURE_MAX_VALUE, v.v & TEXTURE_MAX_VALUE) / float(TEXTURE_MAX_COORD);
}

vec2 decodeVertexUVBias(Vertex v) {
    return mix(vec2(-1.0), vec2(1.0), bvec2(uvec2(v.u, v.v) >> TEXTURE_BITS));
}

vec2 decodeVertexUV(Vertex v) {
    return (decodeVertexUVBias(v) * texCoordShrink) + decodeVertexRawUV(v);
}

vec2 decodeLightUV(Vertex v) {
    return vec2(v.blockLight, v.skyLight)/256.0;
}

bool hasMipping(Vertex v) {
    return bool(int(v.material) & 1);
}

uint rawVertexAlphaCutoff(Vertex v) {
    return (int(v.material) >> 1) & 3;
}

vec4 decodeVertexColour(Vertex v) {
    uvec3 packed_color = (uvec3(v.color) >> uvec3(0, 8, 16)) & uvec3(0xFFu);
    return vec4(vec3(packed_color) * COLOR_SCALE, 1);
}

#else
#define MODEL_SCALE        32.0 / 65536.0
#define MODEL_ORIGIN       8.0

vec3 decodeVertexPosition(Vertex v) {
    uvec3 packed_position = uvec3(
        (v.x >>  0) & 0xFFFFu,
        (v.x >> 16) & 0xFFFFu,
        (v.y >>  0) & 0xFFFFu
    );

    return (vec3(packed_position) * MODEL_SCALE) - MODEL_ORIGIN;
}

vec4 decodeVertexColour(Vertex v) {
    uvec3 packed_color = (uvec3(v.z) >> uvec3(0, 8, 16)) & uvec3(0xFFu);
    return vec4(vec3(packed_color) * COLOR_SCALE, 1);
}

vec2 decodeVertexUV(Vertex v) {
    return vec2(v.w&0xffff,v.w>>16)*(1f/(TEXTURE_MAX_SCALE));
}

bool hasMipping(Vertex v) {
    return ((v.y>>16)&1)!=0;
}

float decodeVertexAlphaCutoff(Vertex v) {
    return (float[](0.0f, 0.1f,0.5f))[((v.y>>16)&int16_t(3))];
}

uint rawVertexAlphaCutoff(Vertex v) {
    return uint((v.y>>17)&int16_t(3));
}

vec2 decodeLightUV(Vertex v) {
    uvec2 light = uvec2(v.y>>24, v.z>>24) & uvec2(0xFFu);
    return vec2(light)/256.0;
}
#endif

float getVertexAlphaCutoff(uint v) {
    return (float[](0.0f, 0.1f,0.1f,1.0f))[v];
}

vec4 sampleLight(vec2 uv) {
    //Its divided by 16 to match sodium/vanilla (it can never be 1 which is funny)
    return vec4(texture(tex_light, uv).rgb, 1);
}

vec3 computeMultiplier(Vertex V) {
    vec4 tint = decodeVertexColour(V);
    tint *= sampleLight(decodeLightUV(V));
    tint *= tint.w;
    return tint.xyz;
}