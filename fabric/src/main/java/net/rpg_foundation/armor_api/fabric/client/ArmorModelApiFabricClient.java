package net.rpg_foundation.armor_api.fabric.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.SynchronousResourceReloader;
import net.minecraft.util.Identifier;
import net.rpg_foundation.armor_api.ArmorModelApi;
import net.rpg_foundation.armor_api.client.ArmorRenderDispatcher;
import net.rpg_foundation.armor_api.client.ArmorRenderers;
import net.rpg_foundation.armor_api.client.GeoModelCache;

/// Fabric side of the two platform bridges: every registration in [ArmorRenderers] is mirrored
/// into Fabric API's own per-item ArmorRenderer registry (the sanctioned hook inside
/// `ArmorFeatureRenderer`), whose callback goes straight to the common dispatcher. The
/// listener replays registrations made before this entrypoint ran, so content-mod init order
/// doesn't matter.
public final class ArmorModelApiFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        net.rpg_foundation.armor_api.client.compatibility.ShaderCompat.initialize();

        ArmorRenderers.setRegistrationListener((item, renderer) -> ArmorRenderer.register(
                (ArmorRenderer) (matrices, queue, stack, state, slot, light, contextModel) ->
                        ArmorRenderDispatcher.render(matrices, queue, stack, state, slot, light, contextModel),
                item));

        ResourceLoader.get(ResourceType.CLIENT_RESOURCES).registerReloader(
                Identifier.of(ArmorModelApi.MOD_ID, "geo_models"),
                (SynchronousResourceReloader) manager -> GeoModelCache.invalidate());
    }
}
