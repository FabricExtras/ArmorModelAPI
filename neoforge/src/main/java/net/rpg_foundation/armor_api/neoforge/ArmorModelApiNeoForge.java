package net.rpg_foundation.armor_api.neoforge;

import net.minecraft.resource.SynchronousResourceReloader;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.rpg_foundation.armor_api.ArmorModelApi;
import net.rpg_foundation.armor_api.client.GeoModelCache;
import net.rpg_foundation.armor_api.client.dev.DevTestArmor;

/// The render hook itself is the ArmorFeatureRendererMixin; the entrypoint only wires the
/// resource-reload invalidation. Renderer lookups go straight to the common registry, so
/// there is no registration bridging to do on NeoForge.
@Mod(ArmorModelApi.MOD_ID)
public final class ArmorModelApiNeoForge {

    public ArmorModelApiNeoForge(IEventBus modBus) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modBus.addListener(RegisterClientReloadListenersEvent.class, event ->
                    event.registerReloadListener((SynchronousResourceReloader) manager -> GeoModelCache.invalidate()));
            if (!FMLEnvironment.production) {
                DevTestArmor.register();
            }
        }
    }
}
