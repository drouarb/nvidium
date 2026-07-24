package me.cortex.nvidium.vk.shader;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vulkan.VulkanConst;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import me.cortex.nvidium.Nvidium;
import me.cortex.nvidium.mixin.minecraft.GpuDeviceAccessor;
import me.cortex.nvidium.sodiumCompat.ShaderLoader;
import net.minecraft.client.renderer.ShaderDefines;
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
    private final int bindPoint;

    private VkPipeline(long pipeline, long[] shaderModules, long pipelineLayout, int bindPoint) {
        this.pipeline = pipeline;
        this.shaderModules = shaderModules;
        this.pipelineLayout = pipelineLayout;
        this.bindPoint = bindPoint;
    }

    public long handle() {
        return pipeline;
    }

    public long layout() {
        return this.pipelineLayout;
    }

    public void bind(VkCommandBuffer commandBuffer) {
        VK12.vkCmdBindPipeline(commandBuffer, bindPoint, pipeline);
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
        private boolean representativeFragmentTest = false;
        private boolean depthTest = false;
        private boolean depthWrite = false;
        private ShaderDefines.Builder shaderDefinesBuilder = ShaderDefines.builder();

        private Builder() {
        }

        public Builder addSource(VkShaderType type, Identifier path) {
            sources.add(new ShaderStage(type, path, ShaderLoader.parse(path, shaderDefinesBuilder)));
            shaderDefinesBuilder = ShaderDefines.builder();
            return this;
        }

        public Builder withDepthTest(boolean v) {
            this.depthTest = v;
            return this;
        }

        public Builder withDepthWrite(boolean v) {
            this.depthWrite = v;
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

        public Builder withRepresentativeFragmentTest(boolean v) {
            representativeFragmentTest = v;
            return this;
        }

        public Builder withShaderDefines(ShaderDefines.Builder builder) {
            this.shaderDefinesBuilder = builder;
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

                VkPipelineViewportStateCreateInfo viewportState = VkPipelineViewportStateCreateInfo.calloc(stack)
                        .sType$Default()
                        .viewportCount(1)
                        .scissorCount(1);

                VkPipelineDynamicStateCreateInfo dynamicState = VkPipelineDynamicStateCreateInfo.calloc(stack)
                        .sType$Default()
                        .pDynamicStates(stack.ints(
                                VK_DYNAMIC_STATE_VIEWPORT,
                                VK_DYNAMIC_STATE_SCISSOR
                        ));


                VkPipelineRasterizationStateCreateInfo rasterizationState = VkPipelineRasterizationStateCreateInfo.calloc(stack)
                        .sType$Default()
                        .depthClampEnable(false)
                        .rasterizerDiscardEnable(false)
                        .polygonMode(VK_POLYGON_MODE_FILL)
                        .cullMode(VK_CULL_MODE_NONE)
                        .frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE)
                        .depthBiasEnable(false)
                        .lineWidth(1.0f);

                VkPipelineMultisampleStateCreateInfo multisampleState = VkPipelineMultisampleStateCreateInfo.calloc(stack)
                        .sType$Default()
                        .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT)
                        .sampleShadingEnable(false);

                VkPipelineColorBlendAttachmentState.Buffer colorBlendAttachment = VkPipelineColorBlendAttachmentState.calloc(1, stack);
                VkPipelineColorBlendAttachmentState attachment = colorBlendAttachment.get(0)
                        .colorWriteMask(VulkanConst.toVk(colorTargetState));

                colorTargetState.blendFunction().ifPresentOrElse(
                        blend ->
                                attachment
                                        .blendEnable(true)
                                        .colorBlendOp(VulkanConst.toVk(blend.color().op()))
                                        .alphaBlendOp(VulkanConst.toVk(blend.alpha().op()))
                                        .srcColorBlendFactor(VulkanConst.toVk(blend.color().sourceFactor()))
                                        .dstColorBlendFactor(VulkanConst.toVk(blend.color().destFactor()))
                                        .srcAlphaBlendFactor(VulkanConst.toVk(blend.alpha().sourceFactor()))
                                        .dstAlphaBlendFactor(VulkanConst.toVk(blend.alpha().destFactor())),
                        () -> attachment
                                .blendEnable(false)
                );


                VkPipelineColorBlendStateCreateInfo colorBlendState = VkPipelineColorBlendStateCreateInfo.calloc(stack)
                        .sType$Default()
                        .logicOpEnable(false)
                        .pAttachments(colorBlendAttachment);

                VkPipelineDepthStencilStateCreateInfo depthStencilState = VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                        .sType$Default()
                        .depthTestEnable(this.depthTest)
                        .depthWriteEnable(this.depthWrite)
                        .depthCompareOp(VK_COMPARE_OP_GREATER_OR_EQUAL)
                        .depthBoundsTestEnable(false)
                        .stencilTestEnable(false);

                VkPipelineRenderingCreateInfoKHR renderingInfo = VkPipelineRenderingCreateInfoKHR.calloc(stack)
                        .sType$Default()
                        .depthAttachmentFormat(VK_FORMAT_D32_SFLOAT)
                        .stencilAttachmentFormat(VK_FORMAT_UNDEFINED);

                if (colorTargetState != null) {
                    renderingInfo
                            .pColorAttachmentFormats(stack.ints(VulkanConst.toVk(colorTargetState.format())));
                } else {
                    renderingInfo.colorAttachmentCount(0);
                }

                VkGraphicsPipelineCreateInfo.Buffer pipelineInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack)
                        .sType$Default();
                pipelineInfo.get(0)
                        .pStages(stages)
                        .layout(layout.layout())
                        .pViewportState(viewportState)
                        .pDynamicState(dynamicState)
                        .pRasterizationState(rasterizationState)
                        .pMultisampleState(multisampleState)
                        .pColorBlendState(colorBlendState)
                        .pDepthStencilState(depthStencilState)
                        .pNext(renderingInfo);

                if (Nvidium.SUPPORT_NV_REPRESENTATIVE_TEST_FRAGMENT && this.representativeFragmentTest) {
                    VkPipelineRepresentativeFragmentTestStateCreateInfoNV representativeState = VkPipelineRepresentativeFragmentTestStateCreateInfoNV.calloc(stack)
                            .sType$Default()
                            .representativeFragmentTestEnable(true);

                    renderingInfo.pNext(representativeState.address());
                }

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

                return new VkPipeline(pipeline, shaderModules, layout.layout(), VK_PIPELINE_BIND_POINT_GRAPHICS);
            }
        }

        public VkPipeline compileCompute() {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkDevice vkDevice = ((VulkanDevice) ((GpuDeviceAccessor) RenderSystem.getDevice()).nvidium$getGpuDeviceBackend()).vkDevice();

                GlslCompiler compiler = new GlslCompiler();
                long[] shaderModules = sources
                        .stream()
                        .mapToLong(stage -> createShaderModule(vkDevice, compiler, stage))
                        .toArray();
                compiler.close();

                VkPipelineShaderStageCreateInfo stage = VkPipelineShaderStageCreateInfo.calloc(stack)
                                .sType$Default()
                                .stage(VK_SHADER_STAGE_COMPUTE_BIT)
                                .module(shaderModules[0])
                                .pName(stack.UTF8("main"));

                VkComputePipelineCreateInfo.Buffer pipelineInfo = VkComputePipelineCreateInfo.calloc(1, stack)
                                .sType$Default()
                                .stage(stage)
                                .layout(layout.layout());

                LongBuffer pPipeline = stack.mallocLong(1);

                int result = vkCreateComputePipelines(
                        vkDevice,
                        VK_NULL_HANDLE,
                        pipelineInfo,
                        null,
                        pPipeline
                );

                if (result != VK_SUCCESS) {
                    throw new RuntimeException("Failed to create compute pipeline: " + result);
                }

                long pipeline = pPipeline.get(0);

                return new VkPipeline(pipeline, shaderModules, layout.layout(), VK_PIPELINE_BIND_POINT_COMPUTE);
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
