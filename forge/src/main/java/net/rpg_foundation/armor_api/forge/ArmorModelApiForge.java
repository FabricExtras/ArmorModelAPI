package net.rpg_foundation.armor_api.forge;

import net.minecraft.resource.SynchronousResourceReloader;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.rpg_foundation.armor_api.ArmorModelApi;
import net.rpg_foundation.armor_api.client.GeoModelCache;

/// The render hook itself is the ArmorFeatureRendererMixin; the entrypoint only wires the
/// resource-reload invalidation. Renderer lookups go straight to the common registry, so
/// there is no registration bridging to do on Forge.
@Mod(ArmorModelApi.MOD_ID)
public final class ArmorModelApiForge {

    // FMLJavaModLoadingContext.get() is flagged for removal by late 47.x builds, but the
    // constructor-injected replacement doesn't exist on early 47.x; get() works on all of [47,).
    @SuppressWarnings("removal")
    public ArmorModelApiForge() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            net.rpg_foundation.armor_api.client.compatibility.ShaderCompat.initialize();
            // Explicit event class: Forge 47's plain addListener(Consumer) infers the event type
            // from the lambda via TypeTools, which is fragile; the 4-arg overload takes it directly.
            FMLJavaModLoadingContext.get().getModEventBus().addListener(
                    EventPriority.NORMAL, false, RegisterClientReloadListenersEvent.class,
                    event -> event.registerReloadListener((SynchronousResourceReloader) manager -> GeoModelCache.invalidate()));
        }
    }
}
