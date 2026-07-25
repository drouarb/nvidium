package me.cortex.nvidium.mixin.minecraft;

import com.mojang.blaze3d.vulkan.VulkanBackend;
import com.mojang.blaze3d.vulkan.VulkanPhysicalDevice;
import com.mojang.blaze3d.vulkan.init.VulkanFeature;
import com.mojang.blaze3d.vulkan.init.VulkanPNextStruct;
import me.cortex.nvidium.Nvidium;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.VmaAllocatorCreateInfo;
import org.lwjgl.vulkan.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
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
            new VulkanFeature(VulkanBackend.VK10_FEATURES_STRUCT, "sparseBinding", VkPhysicalDeviceFeatures.SPARSEBINDING),
            new VulkanFeature(VulkanBackend.VK10_FEATURES_STRUCT, "sparseResidencyBuffer", VkPhysicalDeviceFeatures.SPARSERESIDENCYBUFFER),
            new VulkanFeature(VulkanBackend.VK11_FEATURES_STRUCT, "storageBuffer16BitAccess", VkPhysicalDeviceVulkan11Features.STORAGEBUFFER16BITACCESS),
            new VulkanFeature(VulkanBackend.VK11_FEATURES_STRUCT, "uniformAndStorageBuffer16BitAccess", VkPhysicalDeviceVulkan11Features.UNIFORMANDSTORAGEBUFFER16BITACCESS),
            new VulkanFeature(VulkanBackend.VK12_FEATURES_STRUCT, "shaderInt8", VkPhysicalDeviceVulkan12Features.SHADERINT8),
            new VulkanFeature(VulkanBackend.VK12_FEATURES_STRUCT, "storageBuffer8BitAccess", VkPhysicalDeviceVulkan12Features.STORAGEBUFFER8BITACCESS),
            new VulkanFeature(VulkanBackend.VK12_FEATURES_STRUCT, "uniformAndStorageBuffer8BitAccess", VkPhysicalDeviceVulkan12Features.UNIFORMANDSTORAGEBUFFER8BITACCESS),
            new VulkanFeature(VulkanBackend.VK12_FEATURES_STRUCT, "bufferDeviceAddress", VkPhysicalDeviceVulkan12Features.BUFFERDEVICEADDRESS),
            new VulkanFeature(MESH_SHADER_FEATURES_STRUCT, "meshShader", VkPhysicalDeviceMeshShaderFeaturesEXT.MESHSHADER),
            new VulkanFeature(MESH_SHADER_FEATURES_STRUCT, "taskShader", VkPhysicalDeviceMeshShaderFeaturesEXT.TASKSHADER)
    );

    @Unique
    private static final VulkanFeature REPRESENTATIVE_FRAGMENT_TEST_FEATURE = new VulkanFeature(REPRESENTATIVE_FRAGMENT_FEATURES_STRUCT, "representativeFragmentTest", VkPhysicalDeviceRepresentativeFragmentTestFeaturesNV.REPRESENTATIVEFRAGMENTTEST);

    @Unique
    private static final VulkanFeature FRAGMENT_SHADER_BARYCENTRIC_FEATURE = new VulkanFeature(FRAGMENT_SHADER_BARYCENTRIC_FEATURES_STRUCT, "fragmentShaderBarycentric", VkPhysicalDeviceFragmentShaderBarycentricFeaturesKHR.FRAGMENTSHADERBARYCENTRIC);

    @Inject(
            method = "createDevice(Ljava/util/Collection;Lcom/mojang/blaze3d/vulkan/VulkanPhysicalDevice;Ljava/util/Set;)Lorg/lwjgl/vulkan/VkDevice;",
            at = @At(value = "HEAD")
    )
    private static void nvidium$enableBDAFeature(Collection<String> deviceExtensions, VulkanPhysicalDevice physicalDevice, Set<VulkanFeature> vulkanFeatures, CallbackInfoReturnable<VkDevice> cir) {
        if (physicalDevice.hasDeviceExtension(EXTMeshShader.VK_EXT_MESH_SHADER_EXTENSION_NAME)) {
            Nvidium.LOGGER.info("Enabling VK_EXT_mesh_shader");
        } else {
            Nvidium.LOGGER.info("VK_EXT_mesh_shader not found, disabling Nvidium");
            Nvidium.IS_COMPATIBLE = false;
            return;
        }

        for (var feature : NVIDIUM_REQUIRED_FEATURES) {
            if (isFeatureSupported(physicalDevice.vkPhysicalDevice(), feature)) {
                Nvidium.LOGGER.info("Enabling feature " + feature.name());
            } else {
                Nvidium.LOGGER.info("Missing feature " + feature.name() + ", disabling Nvidium");
                Nvidium.IS_COMPATIBLE = false;
            }
        }

        if (Nvidium.IS_COMPATIBLE) {
            deviceExtensions.add(EXTMeshShader.VK_EXT_MESH_SHADER_EXTENSION_NAME);
            vulkanFeatures.addAll(NVIDIUM_REQUIRED_FEATURES);
            Nvidium.IS_ENABLED = Nvidium.IS_COMPATIBLE;
        } else {
            return; // We don't have minimum required extension we stop there and disable
        }

        if (physicalDevice.hasDeviceExtension(KHRFragmentShaderBarycentric.VK_KHR_FRAGMENT_SHADER_BARYCENTRIC_EXTENSION_NAME)) { // TODO EMULATE BARY
            if (isFeatureSupported(physicalDevice.vkPhysicalDevice(), FRAGMENT_SHADER_BARYCENTRIC_FEATURE)) {
                Nvidium.LOGGER.info("Enabling VK_KHR_fragment_shader_barycentric");
                deviceExtensions.add(KHRFragmentShaderBarycentric.VK_KHR_FRAGMENT_SHADER_BARYCENTRIC_EXTENSION_NAME);
                vulkanFeatures.add(FRAGMENT_SHADER_BARYCENTRIC_FEATURE);
                Nvidium.SUPPORT_VK_KHR_fragment_shader_barycentric = true;
            } else {
                Nvidium.LOGGER.info("No VK_KHR_fragment_shader_barycentric feature, using compatibility mode");
            }
        } else {
            Nvidium.LOGGER.info("No VK_KHR_fragment_shader_barycentric device extension, using compatibility mode");
        }

        if (physicalDevice.hasDeviceExtension(NVRepresentativeFragmentTest.VK_NV_REPRESENTATIVE_FRAGMENT_TEST_EXTENSION_NAME)) {
            if (isFeatureSupported(physicalDevice.vkPhysicalDevice(), REPRESENTATIVE_FRAGMENT_TEST_FEATURE)) {
                Nvidium.LOGGER.info("Enabling VK_NV_representative_fragment_test");
                deviceExtensions.add(NVRepresentativeFragmentTest.VK_NV_REPRESENTATIVE_FRAGMENT_TEST_EXTENSION_NAME);
                vulkanFeatures.add(REPRESENTATIVE_FRAGMENT_TEST_FEATURE);
                Nvidium.SUPPORT_NV_representative_fragment_test = true;
            } else {
                Nvidium.LOGGER.info("No VK_NV_representative_fragment_test feature, using compatibility mode");
            }
        } else {
            Nvidium.LOGGER.info("No VK_NV_representative_fragment_test device extension, using compatibility mode");
        }

        Nvidium.config.automatic_memory = false; // TODO REMOVE BUT SHOULD PREVENT CRASH SINCE NOT SUPPORTED YET
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

    @Shadow
    private static boolean isFeatureSupported(final VkPhysicalDevice vkPhysicalDevice, final VulkanFeature feature) {
        return false;
    }
}
