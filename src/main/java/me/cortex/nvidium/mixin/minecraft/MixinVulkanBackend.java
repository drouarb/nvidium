package me.cortex.nvidium.mixin.minecraft;

import com.mojang.blaze3d.vulkan.VulkanBackend;
import com.mojang.blaze3d.vulkan.VulkanPhysicalDevice;
import com.mojang.blaze3d.vulkan.init.VulkanFeature;
import com.mojang.blaze3d.vulkan.init.VulkanPNextStruct;
import me.cortex.nvidium.Nvidium;
import org.lwjgl.util.vma.VmaAllocatorCreateInfo;
import org.lwjgl.vulkan.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.Set;

import static org.lwjgl.util.vma.Vma.VMA_ALLOCATOR_CREATE_BUFFER_DEVICE_ADDRESS_BIT;
import static org.lwjgl.vulkan.EXTMeshShader.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_MESH_SHADER_FEATURES_EXT;

@Mixin(value = VulkanBackend.class)
public class MixinVulkanBackend {
    @Unique
    private static final VulkanPNextStruct MESH_SHADER_FEATURES_STRUCT = new VulkanPNextStruct(
            VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_MESH_SHADER_FEATURES_EXT,
            VkPhysicalDeviceMeshShaderFeaturesEXT.SIZEOF
    );

    @Unique
    private static final VulkanPNextStruct REPRESENTATIVE_FRAGMENT_FEATURES_STRUCT = new VulkanPNextStruct(
            NVRepresentativeFragmentTest.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_REPRESENTATIVE_FRAGMENT_TEST_FEATURES_NV,
            VkPhysicalDeviceRepresentativeFragmentTestFeaturesNV.SIZEOF
    );

    @Unique
    private static final VulkanPNextStruct FRAGMENT_SHADER_BARYCENTRIC_FEATURES_STRUCT = new VulkanPNextStruct(
            KHRFragmentShaderBarycentric.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FRAGMENT_SHADER_BARYCENTRIC_FEATURES_KHR,
            VkPhysicalDeviceFragmentShaderBarycentricFeaturesKHR.SIZEOF
    );

    @Unique
    private static final Set<VulkanFeature> NVIDIUM_REQUIRED_FEATURES = Set.of(
            new VulkanFeature(VulkanBackend.VK10_FEATURES_STRUCT, "shaderInt64", VkPhysicalDeviceFeatures.SHADERINT64),
            new VulkanFeature(VulkanBackend.VK11_FEATURES_STRUCT, "storageBuffer16BitAccess", VkPhysicalDeviceVulkan11Features.STORAGEBUFFER16BITACCESS),
            new VulkanFeature(VulkanBackend.VK11_FEATURES_STRUCT, "uniformAndStorageBuffer16BitAccess", VkPhysicalDeviceVulkan11Features.UNIFORMANDSTORAGEBUFFER16BITACCESS),
            new VulkanFeature(VulkanBackend.VK12_FEATURES_STRUCT, "shaderInt8", VkPhysicalDeviceVulkan12Features.SHADERINT8),
            new VulkanFeature(VulkanBackend.VK12_FEATURES_STRUCT, "storageBuffer8BitAccess", VkPhysicalDeviceVulkan12Features.STORAGEBUFFER8BITACCESS),
            new VulkanFeature(VulkanBackend.VK12_FEATURES_STRUCT, "uniformAndStorageBuffer8BitAccess", VkPhysicalDeviceVulkan12Features.UNIFORMANDSTORAGEBUFFER8BITACCESS),
            new VulkanFeature(VulkanBackend.VK12_FEATURES_STRUCT, "bufferDeviceAddress", VkPhysicalDeviceVulkan12Features.BUFFERDEVICEADDRESS),
            new VulkanFeature(MESH_SHADER_FEATURES_STRUCT, "meshShader", VkPhysicalDeviceMeshShaderFeaturesEXT.MESHSHADER),
            new VulkanFeature(MESH_SHADER_FEATURES_STRUCT, "taskShader", VkPhysicalDeviceMeshShaderFeaturesEXT.TASKSHADER),

            // TODO PROPER CHECK AND NOT ENABLING
            new VulkanFeature(REPRESENTATIVE_FRAGMENT_FEATURES_STRUCT, "representativeFragmentTest", VkPhysicalDeviceRepresentativeFragmentTestFeaturesNV.REPRESENTATIVEFRAGMENTTEST),
            new VulkanFeature(FRAGMENT_SHADER_BARYCENTRIC_FEATURES_STRUCT, "fragmentShaderBarycentric", VkPhysicalDeviceFragmentShaderBarycentricFeaturesKHR.FRAGMENTSHADERBARYCENTRIC)
    );

    @Inject(
            method = "createDevice(Ljava/util/Collection;Lcom/mojang/blaze3d/vulkan/VulkanPhysicalDevice;Ljava/util/Set;)Lorg/lwjgl/vulkan/VkDevice;",
            at = @At(value = "HEAD")
    )
    private static void nvidium$enableBDAFeature(Collection<String> deviceExtensions, VulkanPhysicalDevice physicalDevice, Set<VulkanFeature> vulkanFeatures, CallbackInfoReturnable<VkDevice> cir) {
        Nvidium.LOGGER.info("Enabling Vulkan Mesh shader");
        deviceExtensions.add(EXTMeshShader.VK_EXT_MESH_SHADER_EXTENSION_NAME);

        Nvidium.LOGGER.info("Enabling VK KHR barycentric");
        deviceExtensions.add(KHRFragmentShaderBarycentric.VK_KHR_FRAGMENT_SHADER_BARYCENTRIC_EXTENSION_NAME);

        for (var feature : NVIDIUM_REQUIRED_FEATURES) {
            // TODO CHECK
            Nvidium.LOGGER.info("Enabling feature " + feature.name());
        }

        vulkanFeatures.addAll(NVIDIUM_REQUIRED_FEATURES);

        // TODO INIT NVIDIUM PROPERLY
    }

    @ModifyArg(
            method = "createVma(Lorg/lwjgl/vulkan/VkDevice;)J",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/util/vma/Vma;vmaCreateAllocator(Lorg/lwjgl/util/vma/VmaAllocatorCreateInfo;Lorg/lwjgl/PointerBuffer;)I"
            ),
            index = 0
    )
    private static VmaAllocatorCreateInfo nvidium$enableVMABDA(VmaAllocatorCreateInfo createInfo) {
        Nvidium.LOGGER.info("Enabling Vulkan VMA BDA");
        createInfo.flags(createInfo.flags() | VMA_ALLOCATOR_CREATE_BUFFER_DEVICE_ADDRESS_BIT);

        return createInfo;
    }
}
