package net.rpg_foundation.armor_api.fabric.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import net.rpg_foundation.armor_api.ArmorModelApi;
import net.rpg_foundation.armor_api.client.ArmorRenderDispatcher;
import net.rpg_foundation.armor_api.client.ArmorRenderers;
import net.rpg_foundation.armor_api.client.GeoModelCache;
import net.rpg_foundation.armor_api.client.dev.DevTestArmor;

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
                (matrices, vertexConsumers, stack, entity, slot, light, contextModel) ->
                        ArmorRenderDispatcher.render(matrices, vertexConsumers, stack, entity, slot, light, contextModel),
                item));

        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES)
                .registerReloadListener(new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public Identifier getFabricId() {
                        return Identifier.of(ArmorModelApi.MOD_ID, "geo_models");
                    }

                    @Override
                    public void reload(ResourceManager manager) {
                        GeoModelCache.invalidate();
                    }
                });

        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            DevTestArmor.register();
        }
    }
}
