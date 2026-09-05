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
/// [GeoArmorRenderer]. Read on the render thread every armor render; written during client
/// init, which on Forge means **concurrently** - mod constructors and client-setup handlers
/// run on a parallel dispatch pool, so several content mods may register at the same time.
/// (AzureLib Armor's registry was a bare HashMap written from those threads; a torn `put`
/// could silently drop a set's renderer for the whole session - the "random armor failed to
/// load on (Neo)Forge" bug. This registry is built not to have that failure mode.)
///
/// Concurrency scheme: copy-on-write. Writers serialize on the class lock and publish a fresh
/// immutable snapshot through a volatile field; the render-thread read is a single volatile
/// load with no locking, and the happens-before edge of the volatile guarantees a published
/// registration is visible - no reliance on the mod loader's own joins.
///
/// Platform bridges consume it differently: the Forge mixin queries [#get] per render;
/// the Fabric bridge mirrors every entry into Fabric API's own ArmorRenderer registry via
/// [#setRegistrationListener] (existing entries are replayed, so bridge and content-mod
/// init order doesn't matter).
@Environment(EnvType.CLIENT)
public final class ArmorRenderers {

    /// Immutable snapshot, replaced wholesale by writers. Identity keying: items are
    /// registry singletons.
    private static volatile Map<Item, GeoArmorRenderer> renderers = Map.of();

    private static @Nullable BiConsumer<Item, GeoArmorRenderer> registrationListener;

    private ArmorRenderers() { }

    /// Registers one renderer (one shared instance) for all given items - typically the four
    /// pieces of an armor set. Safe to call from any mod-init thread.
    public static synchronized void register(GeoArmorRenderer renderer, ItemConvertible... items) {
        var next = new IdentityHashMap<>(renderers);
        for (var itemConvertible : items) {
            var item = itemConvertible.asItem();
            var previous = next.put(item, renderer);
            if (previous != null) {
                ArmorModelApi.LOGGER.warn("Armor renderer for {} registered twice; the last one wins", item);
                continue; // already mirrored to the platform registry; don't mirror twice
            }
            if (registrationListener != null) {
                registrationListener.accept(item, renderer);
            }
        }
        renderers = next; // volatile store publishes the snapshot (and the renderer instances)
    }

    public static @Nullable GeoArmorRenderer get(Item item) {
        return renderers.get(item);
    }

    /// Platform-bridge hook: replays all existing registrations, then receives future ones.
    /// The listener runs under the registry lock; on Fabric it feeds Fabric API's own
    /// (unsynchronized) registry, so the lock is also what serializes those writes when
    /// content mods register from parallel init threads.
    public static synchronized void setRegistrationListener(BiConsumer<Item, GeoArmorRenderer> listener) {
        registrationListener = listener;
        renderers.forEach(listener);
    }
}
