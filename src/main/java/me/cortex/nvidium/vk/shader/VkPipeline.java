package me.cortex.nvidium.vk.shader;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vulkan.VulkanConst;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import me.cortex.nvidium.mixin.minecraft.GpuDeviceAccessor;
import me.cortex.nvidium.sodiumCompat.ShaderLoader;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.vulkan.VK10.*;

public class VkPipeline {
    private final long pipeline;
    private final long[] shaderModules;
    private final long pipelineLayout;

    private VkPipeline(long pipeline, long[] shaderModules, long pipelineLayout) {
        this.pipeline = pipeline;
        this.shaderModules = shaderModules;
        this.pipelineLayout = pipelineLayout;
    }

    public long handle() {
        return pipeline;
    }

    public long layout() {
        return this.pipelineLayout;
    }

    public void bind(VkCommandBuffer commandBuffer) {
        VK12.vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);
    }

    public void delete() {
        VkDevice vkDevice = ((VulkanDevice) ((GpuDeviceAccessor) RenderSystem.getDevice()).nvidium$getGpuDeviceBackend()).vkDevice();

        vkDestroyPipeline(vkDevice, pipeline, null);
        // vkDestroyPipelineLayout(vkDevice, pipelineLayout, null);
        for (long shaderModule : shaderModules) { // TODO FREE AT COMPILE ??
            vkDestroyShaderModule(vkDevice, shaderModule, null);
        }
    }


    public static Builder make() {
        return new Builder();
    }

    public static class Builder {
        private final List<ShaderStage> sources = new ArrayList<>();
        private ColorTargetState colorTargetState;
        private VkPipelineLayout layout = null;

        private Builder() {
        }

        public Builder addSource(VkShaderType type, Identifier path) {
            sources.add(new ShaderStage(type, path, ShaderLoader.parse(path)));
            return this;
        }

        public Builder withColorTargetState(ColorTargetState colorTargetState) {
            this.colorTargetState = colorTargetState;
            return this;
        }

        public Builder withLayout(VkPipelineLayout layout) {
            this.layout = layout;
            return this;
        }

        public VkPipeline compile() {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkDevice vkDevice = ((VulkanDevice) ((GpuDeviceAccessor) RenderSystem.getDevice()).nvidium$getGpuDeviceBackend()).vkDevice();

                VkPipelineShaderStageCreateInfo.Buffer stages = VkPipelineShaderStageCreateInfo.calloc(sources.size(), stack);

                GlslCompiler compiler = new GlslCompiler();
                long[] shaderModules = sources
                        .stream()
                        .mapToLong(stage -> createShaderModule(vkDevice, compiler, stage))
                        .toArray();
                compiler.close();

                for (int i = 0; i < sources.size(); i++) {
                    stages.get(i)
                            .sType$Default()
                            .stage(sources.get(i).type.vkFlag)
                            .module(shaderModules[i])
                            .pName(stack.UTF8("main"));
                }

                long pipelineLayout;
                if (layout == null) {
                    VkPipelineLayoutCreateInfo layoutInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                            .sType$Default();

                    LongBuffer pLayout = stack.mallocLong(1);

                    int result = vkCreatePipelineLayout(
                            vkDevice,
                            layoutInfo,
                            null,
                            pLayout
                    );

                    if (result != VK_SUCCESS) {
                        throw new RuntimeException("Failed to create pipeline layout: " + result);
                    }

                    pipelineLayout = pLayout.get(0);
                } else {
                    pipelineLayout = layout.layout();
                }

                VkPipelineViewportStateCreateInfo viewportState =
                        VkPipelineViewportStateCreateInfo.calloc(stack)
                                .sType$Default()
                                .viewportCount(1)
                                .scissorCount(1);
                VkPipelineDynamicStateCreateInfo dynamicState =
                        VkPipelineDynamicStateCreateInfo.calloc(stack)
                                .sType$Default()
                                .pDynamicStates(stack.ints(
                                        VK_DYNAMIC_STATE_VIEWPORT,
                                        VK_DYNAMIC_STATE_SCISSOR
                                ));


                VkPipelineRasterizationStateCreateInfo rasterizationState =
                        VkPipelineRasterizationStateCreateInfo.calloc(stack)
                                .sType$Default()
                                .depthClampEnable(false)
                                .rasterizerDiscardEnable(false)
                                .polygonMode(VK_POLYGON_MODE_FILL)
                                .cullMode(VK_CULL_MODE_NONE)
                                .frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE)
                                .depthBiasEnable(false)
                                .lineWidth(1.0f);

                VkPipelineMultisampleStateCreateInfo multisampleState =
                        VkPipelineMultisampleStateCreateInfo.calloc(stack)
                                .sType$Default()
                                .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT)
                                .sampleShadingEnable(false);


                VkPipelineColorBlendAttachmentState.Buffer colorBlendAttachment =
                        VkPipelineColorBlendAttachmentState.calloc(1, stack);

                colorBlendAttachment.get(0)
                        .blendEnable(false)
                        .colorWriteMask(
                                VK_COLOR_COMPONENT_R_BIT |
                                        VK_COLOR_COMPONENT_G_BIT |
                                        VK_COLOR_COMPONENT_B_BIT |
                                        VK_COLOR_COMPONENT_A_BIT
                        );

                VkPipelineColorBlendStateCreateInfo colorBlendState =
                        VkPipelineColorBlendStateCreateInfo.calloc(stack)
                                .sType$Default()
                                .logicOpEnable(false)
                                .pAttachments(colorBlendAttachment);

                VkPipelineDepthStencilStateCreateInfo depthStencilState =
                        VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                                .sType$Default()
                                .depthTestEnable(false)
                                .depthWriteEnable(false)
                                .depthBoundsTestEnable(false)
                                .stencilTestEnable(false);

                VkPipelineRenderingCreateInfoKHR renderingInfo =
                        VkPipelineRenderingCreateInfoKHR.calloc(stack)
                                .sType$Default()
                                .pColorAttachmentFormats(
                                        stack.ints(VulkanConst.toVk(colorTargetState.format()))
                                )
                                .depthAttachmentFormat(VK_FORMAT_D32_SFLOAT)
                                .stencilAttachmentFormat(VK_FORMAT_UNDEFINED);

                VkGraphicsPipelineCreateInfo.Buffer pipelineInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack)
                        .sType$Default();

                pipelineInfo.get(0)
                        .pStages(stages)
                        .layout(pipelineLayout)
                        .pViewportState(viewportState)
                        .pDynamicState(dynamicState)
                        .pRasterizationState(rasterizationState)
                        .pMultisampleState(multisampleState)
                        .pColorBlendState(colorBlendState)
                        .pDepthStencilState(depthStencilState)
                        .pNext(renderingInfo);

                LongBuffer pPipeline = stack.mallocLong(1);

                int result = vkCreateGraphicsPipelines(
                        vkDevice,
                        VK_NULL_HANDLE,
                        pipelineInfo,
                        null,
                        pPipeline
                );

                if (result != VK_SUCCESS) {
                    throw new RuntimeException("Failed to create graphics pipeline: " + result);
                }

                long pipeline = pPipeline.get(0);

                return new VkPipeline(pipeline, shaderModules, pipelineLayout);
            }
        }

        private static long createShaderModule(VkDevice device, GlslCompiler compiler, ShaderStage stage) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer spirv = compiler.compileGlsl(stack, stage);

                VkShaderModuleCreateInfo createInfo = VkShaderModuleCreateInfo.calloc(stack)
                        .sType$Default()
                        .pCode(spirv);

                LongBuffer pShaderModule = stack.mallocLong(1);
                int result = vkCreateShaderModule(device, createInfo, null, pShaderModule);

                if (result != VK_SUCCESS) {
                    throw new RuntimeException("Failed to create shader module: " + result);
                }

                return pShaderModule.get(0);
            }
        }
    }

    private static class GlslCompiler {
        private final long shaderCompiler = Shaderc.shaderc_compiler_initialize();
        private final long shaderOptions = Shaderc.shaderc_compile_options_initialize();

        public GlslCompiler() {
            if (shaderCompiler == MemoryUtil.NULL || shaderOptions == MemoryUtil.NULL) {
                throw new IllegalStateException("Failed to initialize Shaderc");
            }

            Shaderc.shaderc_compile_options_set_source_language(shaderOptions, Shaderc.shaderc_source_language_glsl);
            Shaderc.shaderc_compile_options_set_target_env(shaderOptions, Shaderc.shaderc_target_env_vulkan, Shaderc.shaderc_env_version_vulkan_1_2);
            Shaderc.shaderc_compile_options_set_optimization_level(shaderOptions, Shaderc.shaderc_optimization_level_performance);
        }

        public ByteBuffer compileGlsl(MemoryStack stack, ShaderStage stage) {
            System.out.println("Compiling " + stage.path);
            System.out.println(stage.source);
            long result = Shaderc.shaderc_compile_into_spv(
                    shaderCompiler,
                    stage.source,
                    stage.type.shadercType,
                    stage.path.toString(),
                    "main",
                    shaderOptions
            );
            if (result == MemoryUtil.NULL) {
                throw new IllegalStateException("Shaderc returned NULL while compiling " + stage.path);
            }

            int status = Shaderc.shaderc_result_get_compilation_status(result);
            if (status != Shaderc.shaderc_compilation_status_success) {
                System.out.println(stage.source);
                String error = Shaderc.shaderc_result_get_error_message(result);

                throw new IllegalArgumentException("Failed to compile " + stage.path + ":\n" + error);
            }

            ByteBuffer shadercBytes = Shaderc.shaderc_result_get_bytes(result);
            if (shadercBytes == null) {
                throw new IllegalStateException("Shaderc returned no SPIR-V bytes for " + stage.path.getPath());
            }

            ByteBuffer spirv = stack.malloc(shadercBytes.remaining());
            spirv.put(shadercBytes);
            spirv.flip();

            Shaderc.shaderc_result_release(result);

            try { // TODO REMOVE IT
                ByteBuffer dump = spirv.duplicate();
                byte[] bytes = new byte[dump.remaining()];
                dump.get(bytes);
                System.out.println("DUMPING " + stage.path.toDebugFileName());
                Files.write(Path.of(stage.path.toDebugFileName() + ".spv"), bytes);
            } catch (Exception e) {
                System.out.println("FAILED TO DUMP " + stage.path + e);
            }

            return spirv;
        }

        public void close() {
            Shaderc.shaderc_compile_options_release(this.shaderOptions);
            Shaderc.shaderc_compiler_release(this.shaderCompiler);
        }
    }

    private record ShaderStage(VkShaderType type, Identifier path, String source) {
    }
}
