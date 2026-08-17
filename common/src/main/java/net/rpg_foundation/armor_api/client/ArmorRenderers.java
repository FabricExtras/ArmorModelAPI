package net.rpg_foundation.armor_api.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemConvertible;
import net.rpg_foundation.armor_api.ArmorModelApi;
import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/// The renderer registry - the single source of truth for which items render with which
/// [GeoArmorRenderer]. Written during client init, read on the render thread.
///
/// Platform bridges consume it differently: the NeoForge mixin queries [#get] per render;
/// the Fabric bridge mirrors every entry into Fabric API's own ArmorRenderer registry via
/// [#setRegistrationListener] (existing entries are replayed, so bridge and content-mod
/// init order doesn't matter).
@Environment(EnvType.CLIENT)
public final class ArmorRenderers {

    private static final Map<Item, GeoArmorRenderer> RENDERERS = new IdentityHashMap<>();
    private static @Nullable BiConsumer<Item, GeoArmorRenderer> registrationListener;

    private ArmorRenderers() { }

    /// Registers one renderer (one shared instance) for all given items - typically the four
    /// pieces of an armor set.
    public static synchronized void register(GeoArmorRenderer renderer, ItemConvertible... items) {
        for (var itemConvertible : items) {
            var item = itemConvertible.asItem();
            var previous = RENDERERS.put(item, renderer);
            if (previous != null) {
                ArmorModelApi.LOGGER.warn("Armor renderer for {} registered twice; the last one wins", item);
                continue; // already mirrored to the platform registry; don't mirror twice
            }
            if (registrationListener != null) {
                registrationListener.accept(item, renderer);
            }
        }
    }

    public static @Nullable GeoArmorRenderer get(Item item) {
        return RENDERERS.get(item);
    }

    /// Platform-bridge hook: replays all existing registrations, then receives future ones.
    public static synchronized void setRegistrationListener(BiConsumer<Item, GeoArmorRenderer> listener) {
        registrationListener = listener;
        RENDERERS.forEach(listener);
    }
}
