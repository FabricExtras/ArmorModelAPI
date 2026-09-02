package net.rpg_foundation.armor_api.client;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.rpg_foundation.armor_api.ArmorModelApi;
import org.jetbrains.annotations.Nullable;

/// Per-stack overrides of a renderer's assets, carried by the vanilla `minecraft:custom_data`
/// component under the [#KEY] compound - so a server, datapack, loot table or recipe can reskin
/// (or reshape) a registered armor piece without this client-only library registering any
/// component of its own. A missing or empty compound means "no overrides"; every key is optional:
///
/// ```
/// /give @p wizards:wizard_robe[minecraft:custom_data={armor_model_api:{
///     model:    "wizards:geo/arcane_robes.geo.json",        // geo model, replaces the renderer's
///     texture:  "wizards:textures/armor/frost_robe.png",    // base texture (also the default glowmask base)
///     glowmask: "wizards:textures/armor/frost_robe_glowmask.png",
///     trim:     "wizards:armor/trim/spec_robe_generic"      // trim sprite base, replaces TrimLayer's
/// }}]
/// ```
///
/// On an item with a registered renderer ([ArmorRenderers#register]) the data drives *which*
/// assets that renderer uses: an override model that is missing or broken logs once and the piece
/// falls back to the renderer's own model; an override texture is used as-is (a missing one shows
/// the missing texture, like any vanilla texture). A stack that names both `model` and `texture`
/// also **takes over an unregistered item** (any vanilla or third-party armor piece) - it renders
/// through [ArmorRenderers#takeoverRenderer] with the default pass stack; there a broken model
/// leaves the item to vanilla rendering.
///
/// Why `custom_data`: component types cross the wire as raw registry ids, so a client-only mod
/// cannot add one without breaking the registry sync against vanilla servers. `custom_data` exists
/// unchanged on every supported game version, is free-form, and is synced to the client.
///
/// Parsed once per `NbtComponent` instance (identity-cached; the component object is immutable
/// and replaced on the stack when its data changes), so the render path pays no NBT work per frame.
public record ArmorOverrides(
        @Nullable Identifier model,
        @Nullable Identifier texture,
        @Nullable Identifier glowmask,
        @Nullable Identifier trim
) {
    /// Compound key inside `minecraft:custom_data`.
    public static final String KEY = ArmorModelApi.MOD_ID;

    public static final ArmorOverrides NONE = new ArmorOverrides(null, null, null, null);

    /// Weak identity keys: a `NbtComponent` compares by content, but the instance on a stack is
    /// replaced on every change, so identity is the cheap and correct cache key; entries die
    /// with the stack.
    private static final LoadingCache<NbtComponent, ArmorOverrides> CACHE = CacheBuilder.newBuilder()
            .weakKeys()
            .maximumSize(4096)
            .build(CacheLoader.from(ArmorOverrides::parse));

    /// The overrides carried by the stack; [#NONE] when it has none.
    public static ArmorOverrides of(ItemStack stack) {
        var data = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (data == null || data.isEmpty()) {
            return NONE;
        }
        return CACHE.getUnchecked(data);
    }

    public boolean isEmpty() {
        return model == null && texture == null && glowmask == null && trim == null;
    }

    /// Whether this data is complete enough to render an item that has no renderer of its own:
    /// both the geo model and the base texture are given.
    public boolean takesOver() {
        return model != null && texture != null;
    }

    /// `value` unless this stack overrides the given asset.
    public Identifier modelOr(Identifier value) {
        return model != null ? model : value;
    }

    public Identifier textureOr(Identifier value) {
        return texture != null ? texture : value;
    }

    private static ArmorOverrides parse(NbtComponent data) {
        var overrides = data.copyNbt().getCompound(KEY).orElse(null);
        if (overrides == null) {
            return NONE;
        }
        return new ArmorOverrides(
                identifier(overrides, "model"),
                identifier(overrides, "texture"),
                identifier(overrides, "glowmask"),
                identifier(overrides, "trim"));
    }

    private static @Nullable Identifier identifier(NbtCompound compound, String key) {
        var raw = compound.getString(key).orElse(null);
        if (raw == null) {
            return null;
        }
        var id = Identifier.tryParse(raw);
        if (id == null) {
            // Once per component instance thanks to the cache
            ArmorModelApi.LOGGER.warn("custom_data.{}.{} = '{}' is not a valid identifier; ignoring", KEY, key, raw);
        }
        return id;
    }
}
