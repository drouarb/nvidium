#version 460

#extension GL_ARB_shading_language_include : enable
#pragma optionNV(unroll all)
#define UNROLL_LOOP
#extension GL_NV_mesh_shader : require
#extension GL_NV_gpu_shader5 : require
#extension GL_NV_bindless_texture : require

#extension GL_KHR_shader_subgroup_arithmetic: require
#extension GL_KHR_shader_subgroup_basic : require
#extension GL_KHR_shader_subgroup_vote : require
#extension GL_KHR_shader_subgroup_shuffle : require

layout(binding = 1) uniform sampler2D tex_light;

#import <nvidium:occlusion/scene.glsl>
#import <nvidium:terrain/vertex_format/vertex_format.glsl>

#ifdef RENDER_FOG
#import <sodium:include/fog.glsl>
#endif

//It seems like for terrain at least, the sweat spot is ~16 quads per mesh invocation (even if the local size is not 32 )
layout(local_size_x = 32) in;
layout(triangles, max_vertices=64, max_primitives=32) out;

#ifndef USE_NV_FRAGMENT_SHADER_BARYCENTRIC
layout(location=1) out Interpolants {
#ifdef RENDER_FOG
    vec2 v_FragDistance;
#endif
    vec2 uv;
    vec3 v_colour;
} OUT[];
#endif

taskNV in Task {
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


//Do a binary search via global invocation index to determine the base offset
// Note, all threads in the work group are probably going to take the same path
uint getOffset() {
    uint gii = gl_GlobalInvocationID.x >> 1;

#ifdef TRANSLUCENT_PASS
    if (translucencyIdx != -1) {
        gii = translucencyIndexData[translucencyIdx + gii];
    }
#endif

    //TODO: replace this with binary search
    if (gii < binIa.x) {
        return binVa.x + gii + baseOffset;
    } else if (gii < binIa.y) {
        return binVa.y + (gii - binIa.x) + baseOffset;
    } else if (gii < binIa.z) {
        return binVa.z + (gii - binIa.y) + baseOffset;
    } else if (gii < binIa.w) {
        return binVa.w + (gii - binIa.z) + baseOffset;
    } else if (gii < binIb.x) {
        return binVb.x + (gii - binIa.w) + baseOffset;
    } else if (gii < binIb.y) {
        return binVb.y + (gii - binIb.x) + baseOffset;
    } else if (gii < binIb.z) {
        return binVb.z + (gii - binIb.y) + baseOffset;
    } else if (gii < binIb.w) {
        return binVb.w + (gii - binIb.z) + baseOffset;
    } else {
        return uint(-1);
    }
}

mat4 transformMat;

vec4 transformVertex(Vertex V) {
    vec3 pos = decodeVertexPosition(V)+origin;
    return MVP*(transformMat * vec4(pos,1.0));
}

Vertex V0;
vec4 pV0;
Vertex V2;
vec4 pV2;
Vertex V;
vec4 pV;

void putVertex(uint id, Vertex V) {
#ifndef USE_NV_FRAGMENT_SHADER_BARYCENTRIC
    #ifdef RENDER_FOG
    vec3 pos = decodeVertexPosition(V)+origin;
    vec3 exactPos = pos+subchunkOffset.xyz;
    OUT[id].v_FragDistance = getFragDistance(exactPos);
    #endif

    OUT[id].uv = decodeVertexUV(V);
    OUT[id].v_colour = computeMultiplier(V);
#endif
}

void main() {
    if (gl_LocalInvocationIndex == 0) {
        gl_PrimitiveCountNV = 0;//Set the prim count to 0
    }

    uint quadId = getOffset();

    //If its over, dont render
    if (quadId == uint(-1)) {
        return;
    }
    transformMat = transformationArray[transformationId];

    bool triangle0 = (gl_LocalInvocationIndex & uint(1)) == 0;

    //Load common vertex triangles
    V0 = terrainData[(quadId<<2)+0];
    V2 = terrainData[(quadId<<2)+2];

    //Load our unique vertex V1 or V3 depending on triangle0
    V = terrainData[(quadId<<2)+(triangle0 ? 1 : 3)];

    //Transform the vertices
    pV0 = transformVertex(V0);
    pV2 = transformVertex(V2);
    pV = transformVertex(V);

    bool draw = true;
    bool peerDraw = true;

#ifdef CULL_DEGENERATE_TRIANGLES
    { //Compute the bounding pixels of the current triangle in the quad. note, vertex 0 and 2 are the common verticies
        vec2 ssmin = ((pV0.xy/pV0.w)+1)*screenSize;
        vec2 ssmax = ssmin;

        vec2 point = ((pV2.xy/pV2.w)+1)*screenSize;
        ssmin = min(ssmin, point);
        ssmax = max(ssmax, point);

        point = ((pV.xy/pV.w)+1)*screenSize;
        vec2 tmin = min(ssmin, point);
        vec2 tmax = max(ssmax, point);

        //Possibly cull the triangles if they dont cover the center of a pixel on the screen (degen)
        float degenBias = 0.01f;
        draw = all(notEqual(round(tmin-degenBias),round(tmax+degenBias)));

        // Exchage results with neighbor
        peerDraw = subgroupShuffleXor(draw, 1u);

        // Abort if quad got culled
        if (!(draw || peerDraw)) {
            return;
        }
    }
    #endif

    uint triIndex = subgroupExclusiveAdd(uint(draw));
    uint vertBase = subgroupExclusiveAdd(draw ? 2 : 1);
    uint totalTris = subgroupMax(triIndex+uint(draw));

#ifdef STATISTICS_CULL
    atomicAdd(statistics_buffer+3, uint(!draw)); // Count culled triangles
#endif

    //Common vertex depending on warp id
    putVertex(vertBase, triangle0 ? V0 : V2);
    gl_MeshVerticesNV[vertBase].gl_Position = triangle0 ? pV0 : pV2;

    // The third vertex of our triangle if it hasn't been culled
    if (draw) {
        putVertex(vertBase + 1, V);
        gl_MeshVerticesNV[vertBase + 1].gl_Position = pV;

        gl_PrimitiveIndicesNV[triIndex * 3 + 0] = vertBase + 0; // Common vertex
        gl_PrimitiveIndicesNV[triIndex * 3 + 1] = vertBase + 1; // Unique vertex
        gl_PrimitiveIndicesNV[triIndex * 3 + 2] = vertBase + (triangle0 ? 2 :         // If it's triangle 0 our other common vertex is +2, ez
                                                               (peerDraw ? -2 : -1)); // If it's triangle 1 we need to check if peer triangle has been drawn to adjust common vertex idx

        // Emit primitive
        gl_MeshPrimitivesNV[triIndex++].gl_PrimitiveID = int(quadId<<1) | (triangle0 ? 0 : 1);
    }

    if (subgroupElect()) {
        gl_PrimitiveCountNV = totalTris;
    }
}