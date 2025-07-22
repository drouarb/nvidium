#define MODEL_SCALE        32.0 / 65536.0
#define MODEL_ORIGIN       8.0

vec3 decodeVertexPosition(Vertex v) {
    return (vec3(v.x, v.y, v.z) * MODEL_SCALE) - MODEL_ORIGIN;
}

vec4 decodeVertexColour(VertexAttribute v) {
    uvec3 packed_color = uvec3(v.colorR, v.colorG, v.colorB);
    return vec4(vec3(packed_color) * COLOR_SCALE, 1);
}

vec2 decodeVertexUV(VertexAttribute v) {
    return vec2(v.u,v.v)*(1f/(TEXTURE_MAX_SCALE));
}

bool hasMipping(VertexAttribute v) {
    return (int(v.material)&1)!=0;
}

uint rawVertexAlphaCutoff(VertexAttribute v) {
    return uint(v.material>>1)&3u;
}

vec2 decodeLightUV(VertexAttribute v) {
    return vec2(v.blockLight, v.skyLight)/256.0;
}