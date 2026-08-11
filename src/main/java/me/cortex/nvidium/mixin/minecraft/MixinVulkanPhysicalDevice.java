package me.cortex.nvidium.mixin.minecraft;

import com.mojang.blaze3d.vulkan.VulkanPhysicalDevice;
import me.cortex.nvidium.Nvidium;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import static org.lwjgl.vulkan.VK10.VK_QUEUE_SPARSE_BINDING_BIT;

@Mixin(VulkanPhysicalDevice.class)
public class MixinVulkanPhysicalDevice {
    @ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vulkan/VulkanUtils;hasAllBits(II)Z", ordinal = 0), index = 1)
    private int nvidium$requireSparseGraphicsQueue(int flags) {
        Nvidium.LOGGER.info("Adding VK_QUEUE_SPARSE_BINDING_BIT to VkQueue");
        return flags | VK_QUEUE_SPARSE_BINDING_BIT;
    }
}
