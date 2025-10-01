package me.cortex.nvidium.sodiumCompat;

import me.cortex.nvidium.Nvidium;
import me.cortex.nvidium.config.StatisticsLoggingLevel;
import me.cortex.nvidium.config.TranslucencySortingLevel;
import net.caffeinemc.mods.sodium.client.gl.shader.ShaderConstants;
import net.caffeinemc.mods.sodium.client.gl.shader.ShaderParser;
import net.minecraft.resources.ResourceLocation;
import java.util.function.Consumer;

public class ShaderLoader {
    public static String parse(ResourceLocation path) {
        return parse(path, shaderConstants -> {});
    }

    public static String parse(ResourceLocation path, Consumer<ShaderConstants.Builder> constantBuilder) {
        var builder = ShaderConstants.builder();
        if (Nvidium.IS_DEBUG) {
            builder.add("DEBUG");
        }

        for (int i = 1; i <= Nvidium.config.statistics_level.ordinal(); i++) {
            builder.add("STATISTICS_"+StatisticsLoggingLevel.values()[i].name());
        }


        if (Nvidium.config.translucency_sorting_level.ordinal() >= TranslucencySortingLevel.SECTIONS.ordinal()) {
            builder.add("TRANSLUCENCY_SORTING_SECTIONS");
        }
        if (Nvidium.config.translucency_sorting_level == TranslucencySortingLevel.QUADS) {
            builder.add("TRANSLUCENCY_SORTING_QUADS");
        }
        if (Nvidium.config.translucency_sorting_level == TranslucencySortingLevel.SODIUM) {
            builder.add("TRANSLUCENCY_SORTING_SODIUM");
        }

        if (Nvidium.config.render_fog) {
            builder.add("RENDER_FOG");
        }

        if (Nvidium.config.use_sodium_vertex_format) {
            builder.add("USE_SODIUM_VERTEX_FORMAT");
        }
        if (Nvidium.config.cull_degenerate_triangles) {
            builder.add("CULL_DEGENERATE_TRIANGLES");
        }
        if (Nvidium.config.use_nv_fragment_shader_barycentric) {
            builder.add("USE_NV_FRAGMENT_SHADER_BARYCENTRIC");
        }

        builder.add("TEXTURE_MAX_SCALE", String.valueOf(NvidiumCompactChunkVertex.TEXTURE_MAX_VALUE));
        constantBuilder.accept(builder);

        var shaderSrc = net.caffeinemc.mods.sodium.client.gl.shader.ShaderLoader.getShaderSource(path);
        return ShaderParser.parseShader(shaderSrc, builder.build()).src();
    }
}
