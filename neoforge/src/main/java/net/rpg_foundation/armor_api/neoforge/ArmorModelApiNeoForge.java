package net.rpg_foundation.armor_api.neoforge;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.rpg_foundation.armor_api.ArmorModelApi;
import net.rpg_foundation.armor_api.client.GeoModelCache;

/// The render hook itself is the ArmorFeatureRendererMixin; the entrypoint only wires the
/// resource-reload invalidation. Renderer lookups go straight to the common registry, so
/// there is no registration bridging to do on NeoForge.
@Mod(ArmorModelApi.MOD_ID)
public final class ArmorModelApiNeoForge {

    public ArmorModelApiNeoForge(IEventBus modBus) {
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            net.rpg_foundation.armor_api.client.compatibility.ShaderCompat.initialize();
            modBus.addListener(AddClientReloadListenersEvent.class, event ->
                    event.addListener(
                            Identifier.fromNamespaceAndPath(ArmorModelApi.MOD_ID, "geo_models"),
                            (ResourceManagerReloadListener) manager -> GeoModelCache.invalidate()));
        }
    }
}
