package me.cortex.nvidium.vk.shader;

import me.cortex.nvidium.vk.VkRenderDevice;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.EXTMeshShader.VK_SHADER_STAGE_MESH_BIT_EXT;
import static org.lwjgl.vulkan.EXTMeshShader.VK_SHADER_STAGE_TASK_BIT_EXT;
import static org.lwjgl.vulkan.KHRPushDescriptor.VK_DESCRIPTOR_SET_LAYOUT_CREATE_PUSH_DESCRIPTOR_BIT_KHR;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;

public class VkPipelineLayout {
    private final VkRenderDevice vkDevice;
    private final long pipelineLayout;
    private final long descriptorSetLayout;

    public VkPipelineLayout(VkRenderDevice device) {
        this.vkDevice = device;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorSetLayoutBinding.Buffer bindings =
                    VkDescriptorSetLayoutBinding.calloc(1, stack);

            bindings.get(0)
                    .binding(0)
                    .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_TASK_BIT_EXT | VK_SHADER_STAGE_MESH_BIT_EXT | VK_SHADER_STAGE_FRAGMENT_BIT | VK_SHADER_STAGE_COMPUTE_BIT); // TODO ADAPT ???

            VkDescriptorSetLayoutCreateInfo setLayoutInfo =
                    VkDescriptorSetLayoutCreateInfo.calloc(stack)
                            .sType$Default()
                            .flags(VK_DESCRIPTOR_SET_LAYOUT_CREATE_PUSH_DESCRIPTOR_BIT_KHR)
                            .pBindings(bindings);

            LongBuffer pSetLayout = stack.mallocLong(1);

            int result = vkCreateDescriptorSetLayout(
                    vkDevice.getVkDevice().vkDevice(),
                    setLayoutInfo,
                    null,
                    pSetLayout
            );

            if (result != VK_SUCCESS) {
                throw new RuntimeException("vkCreateDescriptorSetLayout failed: " + result);
            }

            descriptorSetLayout = pSetLayout.get(0);

            VkPipelineLayoutCreateInfo layoutInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                    .sType$Default()
                    .pSetLayouts(stack.longs(descriptorSetLayout));

            LongBuffer pLayout = stack.mallocLong(1);

            result = vkCreatePipelineLayout(
                    vkDevice.getVkDevice().vkDevice(),
                    layoutInfo,
                    null,
                    pLayout
            );

            if (result != VK_SUCCESS) {
                throw new RuntimeException("Failed to create pipeline layout: " + result);
            }

            pipelineLayout = pLayout.get(0);
        }
    }

    public long layout() {
        return this.pipelineLayout;
    }

    public void delete() {
        vkDestroyPipelineLayout(vkDevice.getVkDevice().vkDevice(), pipelineLayout, null);
        vkDestroyDescriptorSetLayout(vkDevice.getVkDevice().vkDevice(), descriptorSetLayout, null);
    }
}
