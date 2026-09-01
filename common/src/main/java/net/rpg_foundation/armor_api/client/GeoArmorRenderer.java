package net.rpg_foundation.armor_api.client;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.rpg_foundation.armor_api.client.layer.EmissiveLayer;
import net.rpg_foundation.armor_api.client.layer.TrimLayer;
import net.rpg_foundation.armor_api.client.model.GeoArmorModel;
import net.rpg_foundation.armor_api.client.model.GeoPlayerArmorModel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/// One renderer per visual armor set: which geo model, which base texture, which extra passes.
/// Register it for the set's items via [ArmorRenderers#register].
///
/// Construction is pure data - safe during client init, before resources exist. The model is
/// resolved lazily from [GeoModelCache] and rebuilt after every resource reload.
///
/// Build one fluently from [#of], adding passes with [#trim], [#glow], [#radiant] or [#layer].
/// Each pass method returns a *new* renderer with that pass appended (the config stays
/// immutable), so a whole set reads as one chain - importing only this class and [Identifier],
/// no layer classes, no `List`, no `EmissiveLayer.Mode`:
///
/// ```java
/// GeoArmorRenderer.of(
///         Identifier.fromNamespaceAndPath(MOD_ID, "geo/crimson_plate.geo.json"),
///         Identifier.fromNamespaceAndPath(MOD_ID, "textures/armor/crimson_plate.png"))
///     .radiant()
///     .trim(Identifier.fromNamespaceAndPath(MOD_ID, "armor/trim/crimson_generic"), false);
/// ```
///
/// The fluent pass methods keep the passes sorted by [ArmorRenderLayer#preferredOrder] (glow under trim
/// under custom overlays) - so the stack is correct however the caller chained the calls. The
/// plain constructors don't sort: an explicit layer list is drawn exactly in list order, for
/// callers who want full manual control of the stack. Because each call returns a new renderer,
/// finish the chain before registering it - a pass added to a value you already registered is on
/// a copy, not the registered renderer.
public class GeoArmorRenderer {

    public record Config(
            Identifier modelId,          // e.g. "wizards:geo/wizard_robes.geo.json"
            Identifier texture,          // e.g. "wizards:textures/armor/wizard_robe.png"
            List<ArmorRenderLayer> layers // extra passes after the base pass, drawn in list order
    ) { }

    private final Config config;
    /// One model per slot: rendering is queued and drawn later, so the slot visibility cannot
    /// be flipped on a shared instance between submissions.
    /// Keyed by geo id (the renderer's own plus any [ArmorOverrides] model a stack asked for), then slot.
    private final Map<Identifier, Map<EquipmentSlot, GeoArmorModel>> models = new HashMap<>();
    private final Map<Identifier, Map<EquipmentSlot, GeoPlayerArmorModel>> playerModels = new HashMap<>();
    private int modelGeneration = -1;

    public GeoArmorRenderer(Identifier modelId, Identifier texture, List<ArmorRenderLayer> layers) {
        this(new Config(modelId, texture, List.copyOf(layers)));
    }

    public GeoArmorRenderer(Config config) {
        this.config = config;
    }

    /// Passes sorted into draw order by [ArmorRenderLayer#preferredOrder] - stably, so layers
    /// sharing an order keep the order they were added. Applied by the fluent pass methods; the
    /// plain constructors leave the list exactly as given.
    private static List<ArmorRenderLayer> ordered(List<ArmorRenderLayer> layers) {
        var sorted = new ArrayList<>(layers);
        sorted.sort(Comparator.comparingInt(ArmorRenderLayer::preferredOrder));
        return List.copyOf(sorted);
    }

    /// Start a fluent renderer for the given geo model and base texture, with no passes yet; add
    /// them with [#trim], [#glow], [#radiant] or [#layer].
    public static GeoArmorRenderer of(Identifier modelId, Identifier texture) {
        return new GeoArmorRenderer(modelId, texture, List.of());
    }

    /// This renderer plus one appended pass, re-sorted into draw order, as a new instance
    /// ([#config] is never mutated).
    private GeoArmorRenderer plus(ArmorRenderLayer layer) {
        var next = new ArrayList<>(config.layers());
        next.add(layer);
        return new GeoArmorRenderer(config.modelId(), config.texture(), ordered(next));
    }

    /// Append an arbitrary pass - the escape hatch for custom [ArmorRenderLayer]s.
    public GeoArmorRenderer layer(ArmorRenderLayer layer) {
        return plus(layer);
    }

    /// Smithing trim keyed per pattern+material (`<base>_<pattern>_<material>`).
    public GeoArmorRenderer trim(Identifier baseTexture) {
        return plus(new TrimLayer(baseTexture));
    }

    /// Smithing trim; `supportPatterns=false` keys per material only (`<base>_<material>`),
    /// like the class-mod sets.
    public GeoArmorRenderer trim(Identifier baseTexture, boolean supportPatterns) {
        return plus(new TrimLayer(baseTexture, supportPatterns));
    }

    /// Smithing trim with fully custom sprite naming; unresolved trims skip.
    public GeoArmorRenderer trim(Function<ArmorTrim, Identifier> texturePermutations) {
        return plus(new TrimLayer(texturePermutations));
    }

    /// Smithing trim with custom sprite naming plus an explicit fallback sprite.
    public GeoArmorRenderer trim(Function<ArmorTrim, Identifier> texturePermutations, @Nullable Identifier fallbackTexture) {
        return plus(new TrimLayer(texturePermutations, fallbackTexture));
    }

    /// Plain emissive glow, deriving `<baseTexture>_glowmask.png` automatically.
    public GeoArmorRenderer glow() {
        return plus(new EmissiveLayer());
    }

    /// Plain emissive glow with an explicit mask texture.
    public GeoArmorRenderer glow(Identifier maskTexture) {
        return plus(new EmissiveLayer(maskTexture));
    }

    /// Radiant emissive (fill + additive burn), deriving the default mask - the burn drives the
    /// glow past its own brightness toward white / shader bloom.
    public GeoArmorRenderer radiant() {
        return plus(new EmissiveLayer(EmissiveLayer.Mode.RADIANT));
    }

    /// Radiant emissive with an explicit mask texture.
    public GeoArmorRenderer radiant(Identifier maskTexture) {
        return plus(new EmissiveLayer(EmissiveLayer.Mode.RADIANT, maskTexture));
    }

    public Config config() {
        return config;
    }

    /// The model for the given slot, with that slot's bone visibility applied; null while the
    /// geo model is missing or broken (logged by the cache).
    ///
    /// Render thread only: the cached instances and their generation stamp are deliberately
    /// unsynchronized, and the returned model is shared mutable state (pose, visibility)
    /// that only means anything mid-render. Registration threads have no business here.
    public @Nullable GeoArmorModel model(EquipmentSlot slot) {
        return model(config.modelId(), slot);
    }

    /// The per-slot model for an arbitrary geo id rendered through this renderer - how a
    /// stack's [ArmorOverrides#model] is served. Same caching and threading rules as [#model(EquipmentSlot)].
    public @Nullable GeoArmorModel model(Identifier modelId, EquipmentSlot slot) {
        refreshGeneration();
        var bySlot = models.computeIfAbsent(modelId, id -> new EnumMap<>(EquipmentSlot.class));
        if (!bySlot.containsKey(slot)) {
            var template = GeoModelCache.get(modelId);
            GeoArmorModel model = null;
            if (template != null) {
                model = new GeoArmorModel(template.bakeRoot());
                model.applySlotVisibility(slot);
            }
            bySlot.put(slot, model);
        }
        return bySlot.get(slot);
    }

    /// The player-state variant (a `PlayerEntityModel`, so player animation libraries pose it);
    /// same caching rules as [#model].
    public @Nullable GeoPlayerArmorModel playerModel(EquipmentSlot slot) {
        return playerModel(config.modelId(), slot);
    }

    /// Player-state variant of [#model(Identifier, EquipmentSlot)].
    public @Nullable GeoPlayerArmorModel playerModel(Identifier modelId, EquipmentSlot slot) {
        refreshGeneration();
        var bySlot = playerModels.computeIfAbsent(modelId, id -> new EnumMap<>(EquipmentSlot.class));
        if (!bySlot.containsKey(slot)) {
            var template = GeoModelCache.get(modelId);
            GeoPlayerArmorModel model = null;
            if (template != null) {
                model = new GeoPlayerArmorModel(template.bakeRoot());
                model.applySlotVisibility(slot);
            }
            bySlot.put(slot, model);
        }
        return bySlot.get(slot);
    }

    private void refreshGeneration() {
        int currentGeneration = GeoModelCache.generation();
        if (modelGeneration != currentGeneration) {
            models.clear();
            playerModels.clear();
            modelGeneration = currentGeneration;
        }
    }
}
