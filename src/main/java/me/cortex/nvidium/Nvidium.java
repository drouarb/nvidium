package me.cortex.nvidium;

import me.cortex.nvidium.config.NvidiumConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Nvidium {
    public static final String MOD_VERSION;
    public static final Logger LOGGER = LoggerFactory.getLogger("Nvidium");
    public static boolean IS_COMPATIBLE = true;
    public static boolean IS_ENABLED = false;
    public static boolean IS_OPENGL = false;
    public static boolean IS_DEBUG = System.getProperty("nvidium.isDebug", "false").equals("TRUE");
    public static boolean SUPPORTS_PERSISTENT_SPARSE_ADDRESSABLE_BUFFER = true;
    public static boolean FORCE_DISABLE = false;
    public static boolean SUPPORT_NV_representative_fragment_test = false;
    public static boolean SUPPORT_VK_KHR_fragment_shader_barycentric = false;
    public static int SUBGROUP_SIZE = 32;

    public static NvidiumConfig config = NvidiumConfig.loadOrCreate();

    static {
        ModContainer mod = (ModContainer) FabricLoader.getInstance().getModContainer("nvidium").orElseThrow(NullPointerException::new);
        var version = mod.getMetadata().getVersion().getFriendlyString();
        var commit = mod.getMetadata().getCustomValue("commit").getAsString();
        MOD_VERSION = version+"-"+commit;
    }

    public static void checkSystemIsCapable() {
        LOGGER.info("Minecraft started in OpenGL mode, disabling Nvidium");
        IS_COMPATIBLE = false;
        IS_OPENGL = true;
    }
}
