package net.rpg_foundation.armor_api.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import net.rpg_foundation.armor_api.client.model.GeoArmorModel;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/// One renderer per visual armor set: which geo model, which base texture, which extra passes.
/// Register it for the set's items via [ArmorRenderers#register].
///
/// Construction is pure data - safe during client init, before resources exist. The model is
/// resolved lazily from [GeoModelCache] and rebuilt after every resource reload.
@Environment(EnvType.CLIENT)
public class GeoArmorRenderer {

    public record Config(
            Identifier modelId,          // e.g. "wizards:geo/wizard_robes.geo.json"
            Identifier texture,          // e.g. "wizards:textures/armor/wizard_robe.png"
            List<ArmorRenderLayer> layers // ordered extra passes after the base pass
    ) { }

    private final Config config;
    private @Nullable GeoArmorModel model;
    private int modelGeneration = -1;

    public GeoArmorRenderer(Identifier modelId, Identifier texture, List<ArmorRenderLayer> layers) {
        this(new Config(modelId, texture, List.copyOf(layers)));
    }

    public GeoArmorRenderer(Config config) {
        this.config = config;
    }

    public Config config() {
        return config;
    }

    /// Null while the geo model is missing or broken (logged by the cache).
    public @Nullable GeoArmorModel model() {
        int currentGeneration = GeoModelCache.generation();
        if (model == null || modelGeneration != currentGeneration) {
            var template = GeoModelCache.get(config.modelId());
            model = template != null ? new GeoArmorModel(template.createModel()) : null;
            modelGeneration = currentGeneration;
        }
        return model;
    }
}
