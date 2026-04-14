#define COLOR_SCALE        1.0 / 255.0

#ifdef USE_SODIUM_VERTEX_FORMAT
#import <nvidium:terrain/vertex_format/sodium_vertex_format.glsl>
#else
#import <nvidium:terrain/vertex_format/nvidium_vertex_format.glsl>
#endif

float getVertexAlphaCutoff(uint v) {
    return (float[](0.0f,0.0001f,0.5f,1.0f))[v];
}

vec4 sampleLight(vec2 uv) {
    //Its divided by 16 to match sodium/vanilla (it can never be 1 which is funny)
    return vec4(texture(tex_light, uv).rgb, 1);
}

vec3 computeMultiplier(uint aId) {
    vec4 tint = decodeVertexColour(aId);
    tint *= sampleLight(decodeLightUV(aId));
    tint *= tint.w;
    return tint.xyz;
}