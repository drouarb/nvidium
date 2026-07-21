package me.cortex.nvidium.mixin.minecraft;

import com.mojang.blaze3d.vulkan.VulkanBackend;
import com.mojang.blaze3d.vulkan.VulkanPhysicalDevice;
import com.mojang.blaze3d.vulkan.init.VulkanFeature;
import me.cortex.nvidium.Nvidium;
import org.lwjgl.util.vma.VmaAllocatorCreateInfo;
import org.lwjgl.vulkan.EXTMeshShader;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceVulkan12Features;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.Set;

import static org.lwjgl.util.vma.Vma.VMA_ALLOCATOR_CREATE_BUFFER_DEVICE_ADDRESS_BIT;

@Mixin(value = VulkanBackend.class)
public class MixinVulkanBackend {
    @Unique
    private static final VulkanFeature NVIDIUM_BUFFER_DEVICE_ADDRESS_FEATURE = new VulkanFeature(
            VulkanBackend.VK12_FEATURES_STRUCT,
            "bufferDeviceAddress",
            VkPhysicalDeviceVulkan12Features.BUFFERDEVICEADDRESS
    );

    @Inject(
            method = "createDevice(Ljava/util/Collection;Lcom/mojang/blaze3d/vulkan/VulkanPhysicalDevice;Ljava/util/Set;)Lorg/lwjgl/vulkan/VkDevice;",
            at = @At(value = "HEAD")
    )
    private static void nvidium$enableBDAFeature(Collection<String> deviceExtensions, VulkanPhysicalDevice physicalDevice, Set<VulkanFeature> vulkanFeatures, CallbackInfoReturnable<VkDevice> cir) {
        Nvidium.LOGGER.info("Enabling Vulkan BDA");
        vulkanFeatures.add(NVIDIUM_BUFFER_DEVICE_ADDRESS_FEATURE);

        Nvidium.LOGGER.info("Enabling Vulkan Mesh shader");
        deviceExtensions.add(EXTMeshShader.VK_EXT_MESH_SHADER_EXTENSION_NAME);

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
