package me.cortex.nvidium.sodiumCompat;

import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import me.cortex.nvidium.Nvidium;
import me.cortex.nvidium.config.StatisticsLoggingLevel;
import me.cortex.nvidium.config.TranslucencySortingLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderDefines;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.apache.commons.io.IOUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.util.Optional;

public class ShaderLoader {
    public static String parse(Identifier path) {
        return parse(path, ShaderDefines.builder());
    }

    public static String parse(Identifier path, ShaderDefines.Builder builder) {
        if (Nvidium.IS_DEBUG) {
            builder.define("DEBUG");
        }

        for (int i = 1; i <= Nvidium.config.statistics_level.ordinal(); i++) {
            builder.define("STATISTICS_" + StatisticsLoggingLevel.values()[i].name());
        }

        if (Nvidium.config.translucency_sorting_level.ordinal() >= TranslucencySortingLevel.SECTIONS.ordinal()) {
            builder.define("TRANSLUCENCY_SORTING_SECTIONS");
        }
        if (Nvidium.config.translucency_sorting_level == TranslucencySortingLevel.QUADS) {
            builder.define("TRANSLUCENCY_SORTING_QUADS");
        }
        if (Nvidium.config.translucency_sorting_level == TranslucencySortingLevel.SODIUM) {
            builder.define("TRANSLUCENCY_SORTING_SODIUM");
        }

        if (Nvidium.config.render_fog) {
            builder.define("RENDER_FOG");
        }

        if (Nvidium.config.use_sodium_vertex_format) {
            builder.define("USE_SODIUM_VERTEX_FORMAT");
        }
        if (Nvidium.config.cull_degenerate_triangles) {
            builder.define("CULL_DEGENERATE_TRIANGLES");
        }
        if (Nvidium.config.use_nv_fragment_shader_barycentric) {
            builder.define("USE_NV_FRAGMENT_SHADER_BARYCENTRIC");
        }

        builder.define("TEXTURE_MAX_SCALE", String.valueOf(NvidiumCompactChunkVertex.TEXTURE_MAX_VALUE));

        GlslPreprocessor preprocessor = new GlslPreprocessor() {
            @Override
            public @Nullable String applyImport(boolean isRelative, @NonNull String path) {
                Identifier id = Identifier.parse(path);
                return ShaderLoader.resolve(id, !isRelative);
            }
        };

        String source = ShaderLoader.resolve(path);
        source = String.join("", preprocessor.process(source));
        source = GlslPreprocessor.injectDefines(source, builder.build());

        return source;
    }

    public static String resolve(Identifier id) {
        return resolve(id, false);
    }

    public static String resolve(Identifier id, boolean allowIncludeFallback) {
        ResourceManager rm = Minecraft.getInstance().getResourceManager();

        Identifier directPath = id.withPrefix("shaders/");
        Optional<Resource> res = rm.getResource(directPath);

        if (res.isPresent()) {
            try (Reader reader = res.get().openAsReader()) {
                return IOUtils.toString(reader);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to open shader " + directPath, e);
            }
        }

        if (allowIncludeFallback) {
            Identifier includePath = id.withPrefix("shaders/include/");
            res = rm.getResource(includePath);

            if (res.isPresent()) {
                try (Reader reader = res.get().openAsReader()) {
                    return IOUtils.toString(reader);
                } catch (IOException e) {
                    throw new IllegalStateException("Failed to open shader include " + includePath, e);
                }
            }

            throw new IllegalStateException(
                    "Failed to find shader import " + id +
                            " (tried " + directPath + " and " + includePath + ")"
            );
        }

        throw new IllegalStateException("Failed to find shader " + id);
    }
}