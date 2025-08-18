package me.cortex.nvidium.managers;

import it.unimi.dsi.fastutil.longs.*;
import me.cortex.nvidium.Nvidium;
import me.cortex.nvidium.NvidiumWorldRenderer;
import me.cortex.nvidium.gl.RenderDevice;
import me.cortex.nvidium.sodiumCompat.INvidiumWorldRendererGetter;
import me.cortex.nvidium.sodiumCompat.IRepackagedResult;
import me.cortex.nvidium.util.BufferArena;
import me.cortex.nvidium.util.SegmentedManager;
import me.cortex.nvidium.util.UploadingBufferStream;
import net.caffeinemc.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildOutput;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkSortOutput;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.caffeinemc.mods.sodium.client.util.NativeBuffer;
import net.minecraft.core.SectionPos;
import org.joml.Vector3i;
import org.joml.Vector4i;
import org.lwjgl.system.MemoryUtil;

import java.nio.IntBuffer;

import static me.cortex.nvidium.Nvidium.LOGGER;
import static me.cortex.nvidium.meshletengine.MeshletBuilder.MESHLET_HEADER_SIZE;

public class SectionManager {
    public static final int SECTION_SIZE = 32 + 16;

    //Sections should be grouped and batched into sizes of the count of sections in a region
    private final RegionManager regionManager;

    private final Long2IntOpenHashMap section2id = new Long2IntOpenHashMap();
    private final Long2IntOpenHashMap section2terrain = new Long2IntOpenHashMap();
    private final Long2IntOpenHashMap section2attributes = new Long2IntOpenHashMap();
    private final Long2IntOpenHashMap section2translucencyIdx = new Long2IntOpenHashMap();

    public final UploadingBufferStream uploadStream;
    public final BufferArena terrainAreana;
    public final BufferArena attributesArena;
    public final BufferArena translucencyIndexArena;

    public final BufferArena meshletArena;
    public final BufferArena vertexArena;
    public final BufferArena indexArena;
    public final BufferArena attrArena;
    private final Long2IntOpenHashMap section2meshlet = new Long2IntOpenHashMap();
    private final Long2IntOpenHashMap section2vertex = new Long2IntOpenHashMap();
    private final Long2IntOpenHashMap section2index = new Long2IntOpenHashMap();
    private final Long2IntOpenHashMap section2attr = new Long2IntOpenHashMap();


    private final Long2ObjectOpenHashMap<int[]> translucencyQuadCounts = new Long2ObjectOpenHashMap<int[]>();

    private final RenderDevice device;

    private final LongSet hiddenSectionKeys = new LongOpenHashSet();

    public SectionManager(RenderDevice device, long fallbackMemorySize, UploadingBufferStream uploadStream, int quadVertexSize, NvidiumWorldRenderer worldRenderer) {
        int maxRegions = 50_000;

        this.device = device;
        this.uploadStream = uploadStream;

        //this.terrainAreana = new BufferArena(device, fallbackMemorySize, 2 * 4);
        //this.attributesArena = new BufferArena(device, fallbackMemorySize, 3 * 4);
        this.terrainAreana = new BufferArena(device, fallbackMemorySize, 6, 4);
        this.attributesArena = new BufferArena(device, fallbackMemorySize, 10, 4);

        this.meshletArena = new BufferArena(device, fallbackMemorySize, 16, 1);
        this.vertexArena = new BufferArena(device, fallbackMemorySize, 6, 1);
        this.indexArena = new BufferArena(device, fallbackMemorySize, 4, 1);
        this.attrArena = new BufferArena(device, fallbackMemorySize, 10, 4);
        section2meshlet.defaultReturnValue(-1);
        section2vertex.defaultReturnValue(-1);
        section2index.defaultReturnValue(-1);
        section2attr.defaultReturnValue(-1);

        // TODO adapt fallbackMemorySize
        this.translucencyIndexArena = new BufferArena(device, fallbackMemorySize, 1, 4);
        this.regionManager = new RegionManager(device, maxRegions, maxRegions * 200, uploadStream, worldRenderer::enqueueRegionSort);

        this.section2id.defaultReturnValue(-1);
        this.section2terrain.defaultReturnValue(-1);
        this.section2attributes.defaultReturnValue(-1);
        this.section2translucencyIdx.defaultReturnValue(-1);

        this.translucencyQuadCounts.defaultReturnValue(null);
    }

    public void uploadIndexBuffer(IntBuffer indexBuffer, int[] quadCountData, long upload) {
        int quadOffset = 0;
        for (var facing : ModelQuadFacing.values()) {
            for (int i = 0; i < quadCountData[facing.ordinal()]; i++) {
                // We only need 1 index out of 6 because we are working with quad indexes, also /4 because we have 4 vertices per quad
                int idx = (indexBuffer.get((quadOffset + i) * 6) / 4) + quadOffset;
                MemoryUtil.memPutInt(upload + (long)(quadOffset + i) * 4, idx);
            }
            quadOffset += quadCountData[facing.ordinal()];
        }
    }

    public void uploadChunkSort(ChunkSortOutput sortOutput) {
        if (true)
            return;
        NativeBuffer indexBuffer = sortOutput.getIndexBuffer();
        // Early exit
        if (indexBuffer == null) {
            return;
        }

        RenderSection section = sortOutput.render;
        long sectionKey = SectionPos.asLong(section.getChunkX(), section.getChunkY(), section.getChunkZ());
        var quadCountData = this.translucencyQuadCounts.get(sectionKey);
        if (quadCountData == null) { // early exist if we don't have a section
            return;
        }

        // Quick dirty integrity check to prevent race condition crash because translucencyQuadCounts can be overridden by an already reprocessed chunk
        var totalQuads = 0;
        for (var facing : ModelQuadFacing.values()) {
            totalQuads += quadCountData[facing.ordinal()];
        }
        if (totalQuads * 6 * 4 != indexBuffer.getLength()) {
            LOGGER.error("ChunkSortOutput integrity check failed, aborting (totalQuads={};indexBuffer={})", totalQuads, indexBuffer.getLength() / 24);
            return;
        }

        int indexDataAddress;
        {
            var idxBufferLength = indexBuffer.getLength() / 6;
            IntBuffer idxBuffer = indexBuffer.getDirectBuffer().asIntBuffer();

            indexDataAddress = this.section2translucencyIdx.get(sectionKey);
            if (indexDataAddress != -1 && !this.translucencyIndexArena.canReuse(indexDataAddress, idxBufferLength)) {
                this.section2translucencyIdx.remove(sectionKey);
                this.translucencyIndexArena.free(indexDataAddress);
                indexDataAddress = -1;
            }

            if (indexDataAddress == -1) {
                indexDataAddress = this.translucencyIndexArena.allocQuads(idxBufferLength);
            }

            this.section2translucencyIdx.put(sectionKey, indexDataAddress);

            long upload = translucencyIndexArena.upload(uploadStream, indexDataAddress);
            uploadIndexBuffer(idxBuffer, quadCountData, upload);
        }

        int sectionIdx = this.section2id.get(sectionKey);
        if (sectionIdx == -1) {
            // We shouldn't get there, section should be created by ChunkBuildOutput
            LOGGER.error("Got translucency data but no section found for {}", sectionKey);
            return;
        }

        long metadata = regionManager.setSectionData(sectionIdx);
        metadata += 32; // Go to translucency data offset
        MemoryUtil.memPutInt(metadata, indexDataAddress);
    }

    public int uploadBuffer(long sectionKey, int count, BufferArena arena, Long2IntOpenHashMap sectionMap, NativeBuffer data) {
        int address = sectionMap.get(sectionKey);
        if (address != -1 && !arena.canReuse(address, count)) {
            sectionMap.remove(sectionKey);
            arena.free(address);
            address = -1;
        }
        if (address == -1) {
            address = arena.allocQuads(count);
        }
        sectionMap.put(sectionKey, address);

        long meshletUpload = arena.upload(uploadStream, address);
        MemoryUtil.memCopy(
                MemoryUtil.memAddress(data.getDirectBuffer()),
                meshletUpload,
                data.getLength()
        );

        return address;
    }

    public void uploadChunkBuildResult(ChunkBuildOutput result) {
        //System.out.println("==============================================");
        var output = ((IRepackagedResult)result).getOutput();

        RenderSection section = result.render;
        long sectionKey = SectionPos.asLong(section.getChunkX(), section.getChunkY(), section.getChunkZ());

        if (output == null || output.quads() == 0 || output.meshlet().meshletCount() == 0) {
            deleteSection(sectionKey);
            return;
        }

        // We need to store quadCount per ModelFacing to pad translucency sorting data
        var translucentData = result.meshes.get(DefaultTerrainRenderPasses.TRANSLUCENT);
        if (translucentData != null) {
            int[] quadOffsets = translucencyQuadCounts.get(sectionKey);
            if (quadOffsets == null) {
                quadOffsets = new int[]{0, 0, 0, 0, 0, 0, 0};
            }
            for (var facing : ModelQuadFacing.VALUES) {
                quadOffsets[facing.ordinal()] = translucentData.getVertexCounts()[facing.ordinal()] / 4;
            }

            translucencyQuadCounts.put(sectionKey, quadOffsets);
        }

        int terrainAddress;
        int attributesAddress;
        {
            //Attempt to reuse the same memory
            terrainAddress = this.section2terrain.get(sectionKey);
            if (terrainAddress != -1 && !this.terrainAreana.canReuse(terrainAddress, output.quads())) {
                this.section2terrain.remove(sectionKey);
                this.terrainAreana.free(terrainAddress);
                terrainAddress = -1;
            }
            attributesAddress = this.section2attributes.get(sectionKey);
            if (attributesAddress != -1 && !this.attributesArena.canReuse(attributesAddress, output.quads())) {
                this.section2attributes.remove(sectionKey);
                this.attributesArena.free(attributesAddress);
                attributesAddress = -1;
            }

            if (terrainAddress == -1) {
                terrainAddress = this.terrainAreana.allocQuads(output.quads());
            }
            if (attributesAddress == -1) {
                attributesAddress = this.attributesArena.allocQuads(output.quads());
            }

            if (terrainAddress == SegmentedManager.SIZE_LIMIT) {
                Nvidium.LOGGER.error("Terrain arena critically out of memory, expect issues with chunks!! " +
                        " quad_used: " + this.terrainAreana.getUsedMB() +
                        " physically used: " + this.terrainAreana.getMemoryUsed() +
                        " limit: " + ((INvidiumWorldRendererGetter)(SodiumWorldRenderer.instance())).getRenderer().getMaxGeometryMemory());

                deleteSection(sectionKey);
                return;
            }

            if (terrainAddress != attributesAddress) {
                System.out.println("DESYNC!! Terrain: " + terrainAddress + " Attributes: " + attributesAddress + " Quads: " + output.quads() + " data: " + output.geometry().getLength());
            }
            if (attributesAddress == -1) {
                System.out.println("attribute address -1");
            }

            this.section2terrain.put(sectionKey, terrainAddress);
            this.section2attributes.put(sectionKey, attributesAddress);

            long vertexDataAddress = MemoryUtil.memAddress(output.geometry().getDirectBuffer());
            long geometryUpload = terrainAreana.upload(uploadStream, terrainAddress);
            long attributesUpload = attributesArena.upload(uploadStream, terrainAddress);
            for (long vertId = 0; vertId < output.quads() * 4L; vertId++) {
                //MemoryUtil.memCopy(vertexDataAddress + vertId * 20,         geometryUpload   + vertId * 2 * 4, 2 * 4);
                //MemoryUtil.memCopy(vertexDataAddress + vertId * 20 + 2 * 4, attributesUpload + vertId * 3 * 4, 3 * 4);
                MemoryUtil.memCopy(vertexDataAddress + vertId * 16,     geometryUpload   + vertId * 6, 6);
                MemoryUtil.memCopy(vertexDataAddress + vertId * 16 + 6, attributesUpload + vertId * 10, 10);
            }
        }

        int meshletAddr;
        {
            System.out.printf("Uploading %d meshlets %d vertices %d quads\n", output.meshlet().meshletCount(), output.meshlet().vertexCount(), output.meshlet().quadCount());
            // Upload vertex
            int vtxAddr = this.uploadBuffer(sectionKey, output.meshlet().vertexCount(), this.vertexArena, section2vertex, output.meshlet().vertex());
            // Upload indices
            int idxAddr = this.uploadBuffer(sectionKey, output.meshlet().quadCount(), this.indexArena, section2index, output.meshlet().index());
            // Upload attributes TODO check, should be synced to idxAddr
            int attributeAddr = this.uploadBuffer(sectionKey, output.meshlet().quadCount(), this.attrArena, section2attr, output.meshlet().attributes());

            // Patch our meshlet with our arena values
            long inputMeshletAddr = MemoryUtil.memAddress(output.meshlet().meshlet().getDirectBuffer());
            for (long i = 0; i < output.meshlet().meshletCount(); i++) {
                int quadOffset = MemoryUtil.memGetInt(inputMeshletAddr + i * MESHLET_HEADER_SIZE);
                int vtxOffset  = MemoryUtil.memGetInt(inputMeshletAddr + i * MESHLET_HEADER_SIZE + 4);
                /*
                System.out.printf("Patch quadOffset: %d => %d | vtxAddr %d => %d | QuadCount: %d | VtxCount: %d\n",
                        quadOffset,
                        quadOffset + idxAddr,
                        vtxOffset,
                        vtxOffset + vtxAddr,
                        MemoryUtil.memGetShort(inputMeshletAddr + i * MESHLET_HEADER_SIZE + 12),
                        MemoryUtil.memGetShort(inputMeshletAddr + i * MESHLET_HEADER_SIZE + 14)
                );
                 */

                MemoryUtil.memPutInt(inputMeshletAddr + i * MESHLET_HEADER_SIZE, quadOffset + idxAddr);
                MemoryUtil.memPutInt(inputMeshletAddr + i * MESHLET_HEADER_SIZE + 4, vtxOffset + vtxAddr);
            }

            // Upload meshlets
            meshletAddr = this.uploadBuffer(sectionKey, output.meshlet().meshletCount(), this.meshletArena, section2meshlet, output.meshlet().meshlet());

            //System.out.printf("meshletAddr: %d vtxAddr: %d idxAddr: %d attributeAddr: %d\n", meshletAddr, vtxAddr, idxAddr, attributeAddr);
            //System.out.printf("MeshletCount: %d | VtxCount: %d | QuadCount: %d\n", output.meshlet().meshletCount(), output.meshlet().vertexCount(), output.meshlet().quadCount());
        }


        //Get the section id or allocate a new instance for it
        int sectionIdx = this.section2id.computeIfAbsent(
                sectionKey,
                key -> this.regionManager.allocateSection(SectionPos.x(key), SectionPos.y(key), SectionPos.z(key))
        );


        long metadata = regionManager.setSectionData(sectionIdx);
        boolean hideSectionBitSet = this.hiddenSectionKeys.contains(sectionKey);
        Vector3i min  = output.min();
        Vector3i size = output.size();


        //bits 18->26 taken by section id (used for translucency sorting/rendering)
        // 26->32 is free
        int px = section.getChunkX()<<8 | size.x<<4 | min.x;
        int py = (section.getChunkY()&0x1FF)<<8 | size.y<<4 | min.y | (hideSectionBitSet?1<<17:0) | ((regionManager.getSectionRefId(sectionIdx))<<18);
        int pz = section.getChunkZ()<<8 | size.z<<4 | min.z;
        int pw = terrainAddress;
        new Vector4i(px, py, pz, pw).getToAddress(metadata);
        metadata += 4*4;

        //Write the geometry offsets, packed into ints
        for (int i = 0; i < 4; i++) {
            int geo = Short.toUnsignedInt(output.meshlet().meshletOffsets()[i*2])|(Short.toUnsignedInt(output.meshlet().meshletOffsets()[i*2+1])<<16);
            MemoryUtil.memPutInt(metadata, geo);
            metadata += 4;
        }
        metadata += 4;
        MemoryUtil.memPutInt(metadata, meshletAddr);
        metadata += 4;
        MemoryUtil.memPutInt(metadata, output.meshlet().meshletCount());
        metadata += 4;
    }

    public void setHideBit(int x, int y, int z, boolean hide) {
        long sectionKey = SectionPos.asLong(x, y, z);

        if (hide) {
            //Do a fast return if it was already hidden
            if (!this.hiddenSectionKeys.add(sectionKey)) {
                return;
            }
        } else {
            //Do a fast return if the section was not hidden
            if (!this.hiddenSectionKeys.remove(sectionKey)) {
                return;
            }
        }

        int sectionId = this.section2id.get(sectionKey);
        //Only update the section if it is loaded
        if (sectionId != -1) {
            long metadata = this.regionManager.setSectionData(sectionId);
            MemoryUtil.memPutInt(metadata + 4, (MemoryUtil.memGetInt(metadata + 4)&~(1<<17))| (hide?1:0)<<17);
        }
    }

    public void deleteSection(RenderSection section) {
        deleteSection(SectionPos.asLong(section.getChunkX(), section.getChunkY(), section.getChunkZ()));
    }

    private void deleteSection(long sectionKey) {
        int sectionIdx = this.section2id.remove(sectionKey);
        if (sectionIdx != -1) {
            int terrainIndex = this.section2terrain.remove(sectionKey);
            if (terrainIndex != -1) {
                this.terrainAreana.free(terrainIndex);
            }
            int attributesIndex = this.section2attributes.remove(sectionKey);
            if (attributesIndex != -1) {
                this.attributesArena.free(terrainIndex);
            }

            int meshletIndex = this.section2meshlet.remove(sectionKey);
            if (meshletIndex != -1) {
                this.meshletArena.free(meshletIndex);
            }
            int vertexIndex = this.section2vertex.remove(sectionKey);
            if (vertexIndex != -1) {
                this.vertexArena.free(vertexIndex);
            }
            int indexIndex = this.section2index.remove(sectionKey);
            if (indexIndex != -1) {
                this.indexArena.free(indexIndex);
            }
            int attrIndex = this.section2attr.remove(sectionKey);
            if (attrIndex != -1) {
                this.attrArena.free(attrIndex);
            }

            int indexIdx = this.section2translucencyIdx.remove(sectionKey);
            if (indexIdx != -1) {
                this.translucencyIndexArena.free(indexIdx);
            }
            this.translucencyQuadCounts.remove(sectionKey);
            //Clear the segment
            this.regionManager.removeSection(sectionIdx);
        }
    }

    public void destroy() {
        this.regionManager.destroy();
        this.terrainAreana.delete();
        this.attributesArena.delete();
        this.translucencyIndexArena.delete();

        this.meshletArena.delete();
        this.vertexArena.delete();
        this.indexArena.delete();
        this.attrArena.delete();
    }

    public void commitChanges() {
        this.regionManager.commitChanges();
    }

    public RegionManager getRegionManager() {
        return regionManager;
    }

    public void removeRegionById(int regionId) {
        if (!this.regionManager.regionExists(regionId)) return;
        long rk = this.regionManager.regionIdToKey(regionId);
        int X = SectionPos.x(rk)<<3;
        int Y = SectionPos.y(rk)<<2;
        int Z = SectionPos.z(rk)<<3;
        for (int x = X; x < X+8; x++) {
            for (int y = Y; y < Y+4; y++) {
                for (int z = Z; z < Z+8; z++) {
                    this.deleteSection(SectionPos.asLong(x, y, z));
                }
            }
        }
    }
}




