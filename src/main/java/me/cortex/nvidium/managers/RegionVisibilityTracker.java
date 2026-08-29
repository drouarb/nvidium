package me.cortex.nvidium.managers;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import me.cortex.nvidium.gl.shader.Shader;
import me.cortex.nvidium.sodiumCompat.ShaderLoader;
import me.cortex.nvidium.util.DownloadTaskStream;
import me.cortex.nvidium.vk.VkRenderDevice;
import me.cortex.nvidium.vk.buffers.VkBuffer;
import me.cortex.nvidium.vk.shader.VkPipeline;
import me.cortex.nvidium.vk.shader.VkPipelineLayout;
import me.cortex.nvidium.vk.shader.VkShaderType;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkCommandBuffer;

import java.util.Optional;

import static me.cortex.nvidium.gl.shader.ShaderType.FRAGMENT;
import static me.cortex.nvidium.gl.shader.ShaderType.MESH;
import static org.lwjgl.opengl.GL42.glMemoryBarrier;
import static org.lwjgl.opengl.GL43C.GL_SHADER_STORAGE_BARRIER_BIT;
import static org.lwjgl.opengl.NVMeshShader.glDrawMeshTasksNV;
import static org.lwjgl.vulkan.EXTMeshShader.vkCmdDrawMeshTasksEXT;

public class RegionVisibilityTracker {
    private final VkPipeline shader;
    private final VkRenderDevice device;

    private final DownloadTaskStream downStream;
    private final int[] frustum;
    private final int[] visible;
    public RegionVisibilityTracker(VkRenderDevice device, VkPipelineLayout layout, DownloadTaskStream downStream, int maxRegions) {
        this.device = device;
        this.downStream = downStream;
        visible = new int[maxRegions];
        frustum = new int[maxRegions];
        for (int i = 0; i < maxRegions; i++) {
            frustum[i] = 0;
            visible[i] = 0;
        }

        shader = VkPipeline.make()
                .addSource(VkShaderType.MESH, Identifier.fromNamespaceAndPath("nvidium", "occlusion/queries/region/mesh.glsl"))
                .addSource(VkShaderType.FRAGMENT, Identifier.fromNamespaceAndPath("nvidium", "occlusion/queries/region/fragment.frag"))
                .withLayout(layout)
                .withColorTargetState(new ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_NONE))
                .withDepthTest(true)
                .withDepthWrite(false)
                .withRepresentativeFragmentTest(true)
                .compile();
    }

    private int fram = 0;
    //This is kind of evil in the fact that it just reuses the visibility buffer
    public void computeVisibility(VkCommandBuffer commandBuffer,  int regionCount, VkBuffer regionVisibilityBuffer, short[] regionMapping) {
        shader.bind(commandBuffer);
        fram++;
        vkCmdDrawMeshTasksEXT(commandBuffer, regionCount, 1, 1);
        device.barrier(commandBuffer);

        downStream.download(regionVisibilityBuffer, 0, regionCount, ptr -> {
            for (int i = 0; i < regionMapping.length; i++) {
                if (MemoryUtil.memGetByte(ptr + i) == 1) {
                    //System.out.println(regionMapping[i] + " was visible");
                    frustum[regionMapping[i]]++;
                    visible[regionMapping[i]] = fram;
                } else {
                    //System.out.println(regionMapping[i] + " was not visible");
                    frustum[regionMapping[i]]++;
                }
            }
        });
    }


    public void delete() {
        shader.delete();
    }

    public void resetRegion(int id) {
        frustum[id] = 0;
        visible[id] = 0;
    }

    public int findMostLikelyLeastSeenRegion(int maxIndex) {
        int maxRank = Integer.MIN_VALUE;
        int id = -1;
        for (int i = 0; i < maxIndex; i++) {
            if (frustum[i] <= 200) continue;
            int rank =  - visible[i];
            //int rank = -visible[i];
            if (maxRank < rank) {
                maxRank = rank;
                id = i;
            }
        }
        return id;
    }
}
