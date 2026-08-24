package net.rpg_foundation.armor_api.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.util.Identifier;
import net.rpg_foundation.armor_api.ArmorModelApi;
import net.rpg_foundation.armor_api.client.geo.GeoBaker;
import net.rpg_foundation.armor_api.client.geo.GeoParser;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/// Baked geo templates by model id, loaded lazily on first request (render thread) and thrown
/// away wholesale on resource reload. Lazy loading makes the cache independent of platform
/// init ordering: it does not matter whether renderers register before or after the reload
/// listener fires - the first frame that needs a model loads it.
///
/// A model that fails to load is cached as a miss (logged once); its items render nothing
/// until the next reload. Failures are asset bugs, not user-recoverable states.
public final class GeoModelCache {

    /// Optional.empty() = known failure; absent key = not attempted yet.
    /// Guarded by the class lock; `generation` is volatile so a stamp read outside the lock
    /// still sees reload bumps. In normal operation loads and invalidation both happen on the
    /// render/main thread, but the lock makes the cache safe regardless of caller - NeoForge
    /// runs mod init on a parallel dispatch pool, and this class must not care who calls when.
    private static final Map<Identifier, Optional<TexturedModelData>> TEMPLATES = new HashMap<>();
    private static volatile int generation = 0;

    private GeoModelCache() { }

    /// Bumped on every resource reload; renderers stamp their lazily created model with it.
    public static int generation() {
        return generation;
    }

    /// Called from the platform reload listeners.
    public static synchronized void invalidate() {
        TEMPLATES.clear();
        generation++;
    }

    public static synchronized @Nullable TexturedModelData get(Identifier modelId) {
        return TEMPLATES.computeIfAbsent(modelId, GeoModelCache::load).orElse(null);
    }

    private static Optional<TexturedModelData> load(Identifier modelId) {
        var resourceManager = MinecraftClient.getInstance().getResourceManager();
        var resource = resourceManager.getResource(modelId);
        if (resource.isEmpty()) {
            ArmorModelApi.LOGGER.error("Geo model '{}' not found; its armor will not render", modelId);
            return Optional.empty();
        }
        try (Reader reader = resource.get().getReader()) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            var parsed = GeoParser.parse(json, modelId.toString());
            return Optional.of(GeoBaker.bake(parsed, modelId.toString()));
        } catch (Exception e) {
            ArmorModelApi.LOGGER.error("Failed to load geo model '{}'; its armor will not render", modelId, e);
            return Optional.empty();
        }
    }
}
