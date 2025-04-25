package me.cortex.nvidium;

import me.cortex.nvidium.config.NvidiumConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.util.Util;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.lwjgl.opengl.GL11.GL_EXTENSIONS;
import static org.lwjgl.opengl.GL11.glGetInteger;
import static org.lwjgl.opengl.GL30.GL_NUM_EXTENSIONS;
import static org.lwjgl.opengl.GL30.glGetStringi;

public class Nvidium {
    public static final String MOD_VERSION;
    public static final Logger LOGGER = LoggerFactory.getLogger("Nvidium");
    public static boolean IS_COMPATIBLE = false;
    public static boolean IS_ENABLED = false;
    public static boolean IS_DEBUG = true; // System.getProperty("nvidium.isDebug", "false").equals("TRUE");
    public static boolean SUPPORTS_PERSISTENT_SPARSE_ADDRESSABLE_BUFFER = false;
    public static boolean FORCE_DISABLE = false;

    public static NvidiumConfig config = NvidiumConfig.loadOrCreate();

    static {
        ModContainer mod = (ModContainer) FabricLoader.getInstance().getModContainer("nvidium").orElseThrow(NullPointerException::new);
        var version = mod.getMetadata().getVersion().getFriendlyString();
        var commit = mod.getMetadata().getCustomValue("commit").getAsString();
        MOD_VERSION = version+"-"+commit;
    }

    public static void checkSystemIsCapable() {
        var cap = GL.getCapabilities();

        // TODO Clean that
        LOGGER.info("Nvidium Capabilities: ");
        LOGGER.info("GL_NV_mesh_shader {}", cap.GL_NV_mesh_shader);
        LOGGER.info("GL_NV_gpu_shader5 {}", cap.GL_NV_gpu_shader5);
        LOGGER.info("GL_ARB_shading_language_include {}", cap.GL_ARB_shading_language_include);
        LOGGER.info("GL_KHR_shader_subgroup {}", cap.GL_KHR_shader_subgroup);
        LOGGER.info("AMD_shader_explicit_vertex_parameter {}", cap.GL_AMD_shader_explicit_vertex_parameter);
        LOGGER.info("GL_NV_fragment_shader_barycentric {}", cap.GL_NV_fragment_shader_barycentric);
        LOGGER.info("GL_NV_shader_buffer_load {}", cap.GL_NV_shader_buffer_load);

        LOGGER.info("==========================================================");
        int count = glGetInteger(GL_NUM_EXTENSIONS);
        for (int i = 0; i < count; i++) {
            LOGGER.info("Capability dump: {}", glGetStringi(GL_EXTENSIONS, i));
        }
        LOGGER.info("==========================================================");

        boolean supported = true;
        IS_COMPATIBLE = supported;
        if (IS_COMPATIBLE) {
            LOGGER.info("All capabilities met");
        } else {
            LOGGER.warn("Not all requirements met, disabling nvidium");
        }
        
        if (IS_COMPATIBLE && Util.getOperatingSystem() == Util.OperatingSystem.LINUX) {
            LOGGER.warn("Linux currently uses fallback terrain buffer due to driver inconsistencies, expect increase vram usage");
            SUPPORTS_PERSISTENT_SPARSE_ADDRESSABLE_BUFFER = false;
        }

        if (IS_COMPATIBLE) {
            LOGGER.info("Enabling Nvidium");
        }
        IS_ENABLED = IS_COMPATIBLE;
    }
}
