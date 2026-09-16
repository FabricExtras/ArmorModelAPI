package net.rpg_foundation.armor_api.client.layer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.resources.palette.PalettedTextureManager;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.rpg_foundation.armor_api.ArmorModelApi;
import net.rpg_foundation.armor_api.client.ArmorRenderContext;
import net.rpg_foundation.armor_api.client.ArmorRenderLayer;
import net.rpg_foundation.armor_api.client.GeoModelCache;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

/// Armor trim pass with mod-authored trim textures: picks a **greyscale** trim texture by a
/// naming permutation of the item's trim, has vanilla's paletted-texture manager recolor it
/// with the trim material's palette, and re-renders the model with the result. Port of the
/// `AzArmorTrimLayer` we originally wrote for AzureLib Armor; the existing `armor/trim/*`
/// greyscale textures work unchanged - they only need the palette metadata described below.
///
/// Texture naming: `<base>_<patternName>` when patterns are supported, else just `<base>` -
/// or any custom permutation function. The material is no longer part of the name: since 26.3
/// the `armor_trims` atlas and its `paletted_permutations` source are gone, and trim art is
/// recolored at runtime from the material's `palette_id`. That also covers **third-party trim
/// materials** for free - every material carries a palette, so there is no "unlisted material"
/// case any more. A material whose palette cannot be found is drawn greyscale (vanilla logs it).
///
/// Palette metadata: like vanilla's own trim art, the greyscale texture needs a
/// `<texture>.png.mcmeta` naming the key palette its grey values are drawn in -
/// `{"palette": {"base_palette": "minecraft:trim_base"}}` for art keyed to vanilla's trim
/// greys (the same eight values the old `trim_palette` atlas key used). Without it the texture
/// is drawn uncolored.
///
/// A stack's [net.rpg_foundation.armor_api.client.ArmorOverrides#trim] replaces the base
/// texture for layers built from one (same naming rule); layers built from a custom permutation
/// function have no base to swap and ignore it. A layer from [#fromOverrides] has no base of its
/// own and draws only when the stack supplies one.
///
/// A missing texture skips the pass (logged once per resource reload) rather than drawing the
/// magenta checker; the base-texture constructors fall back from `<base>_<pattern>` to the
/// plain `<base>` first, the custom-function constructor to its optional fallback texture.
public class TrimLayer implements ArmorRenderLayer {

    private final @Nullable Function<ArmorTrim, Identifier> texturePermutations; // null = override-only
    private final @Nullable Identifier fallbackTexture; // tried when the permuted texture is missing; null = skip instead
    /// Whether a stack's trim override may re-derive the texture name: true for layers built
    /// from a base texture (the convention-named layers) and for [#fromOverrides]; false for
    /// custom permutation functions, which have no base to swap.
    private final boolean overridable;
    private final boolean supportPatterns;

    /// Texture ids already reported missing (log once). Render thread only; reset when the
    /// reload generation moves on - a reload can add the texture (or the fallback).
    private static final Set<Identifier> REPORTED_MISSING = new HashSet<>();
    private static int reportedGeneration = -1;

    public TrimLayer(Identifier baseTexture) {
        this(baseTexture, true);
    }

    public TrimLayer(Identifier baseTexture, boolean supportPatterns) {
        this(trim -> textureId(baseTexture, supportPatterns, trim), baseTexture, true, supportPatterns);
    }

    /// A trim pass with no base texture of its own: it draws only for stacks whose overrides
    /// name a `trim` base (the takeover renderer's trim pass).
    public static TrimLayer fromOverrides(boolean supportPatterns) {
        return new TrimLayer(null, null, true, supportPatterns);
    }

    /// The conventional greyscale texture id for a trim on a base texture: `<base>_<pattern>`
    /// when patterns are supported, else the base itself. Resolved by the paletted-texture
    /// manager as `assets/<ns>/textures/<path>.png`.
    public static Identifier textureId(Identifier baseTexture, boolean supportPatterns, ArmorTrim trim) {
        if (!supportPatterns) {
            return baseTexture;
        }
        var patternName = trim.pattern().value().assetId().getPath();
        return baseTexture.withSuffix("_" + patternName);
    }

    public TrimLayer(Function<ArmorTrim, Identifier> texturePermutations) {
        this(texturePermutations, null);
    }

    public TrimLayer(Function<ArmorTrim, Identifier> texturePermutations, @Nullable Identifier fallbackTexture) {
        this(texturePermutations, fallbackTexture, false, false);
    }

    private TrimLayer(
            @Nullable Function<ArmorTrim, Identifier> texturePermutations,
            @Nullable Identifier fallbackTexture,
            boolean overridable,
            boolean supportPatterns
    ) {
        this.texturePermutations = texturePermutations;
        this.fallbackTexture = fallbackTexture;
        this.overridable = overridable;
        this.supportPatterns = supportPatterns;
    }

    @Override
    public int preferredOrder() {
        return ORDER_TRIM;
    }

    @Override
    public void render(ArmorRenderContext context) {
        var trim = context.stack().get(DataComponents.TRIM);
        if (trim == null) {
            return;
        }
        var overrideBase = context.overrides().trim();
        Identifier textureId;
        Identifier fallback;
        if (overrideBase != null && overridable) {
            textureId = textureId(overrideBase, supportPatterns, trim);
            fallback = overrideBase;
        } else if (texturePermutations != null) {
            textureId = texturePermutations.apply(trim);
            fallback = fallbackTexture;
        } else {
            return; // override-only layer, and the stack supplies no trim base
        }
        var texture = resolveTexture(textureId, fallback, trim.material().value().paletteId());
        if (texture == null) {
            return;
        }
        // The recolored texture lives in one of the manager's dynamic atlases: the handle is
        // both the texture to bind and the UV remap into it. The trim itself is submitted
        // plain; a foil piece's glint follows it, like vanilla's EquipmentLayerRenderer.
        context.submit(RenderTypes.armorTrim(texture.textureLocation(), trim.pattern().value().decal()), context.light(), -1, texture);
        context.submitPendingGlint();
    }

    /// The recolored texture for the item's trim; the fallback texture when the permutation
    /// doesn't exist; null to skip the pass.
    private static PalettedTextureManager.@Nullable Handle resolveTexture(Identifier textureId, @Nullable Identifier fallbackTexture, Identifier paletteId) {
        var handle = prepare(textureId, paletteId);
        if (handle != null) {
            return handle;
        }
        if (fallbackTexture == null || fallbackTexture.equals(textureId)) {
            if (firstReportFor(textureId)) {
                ArmorModelApi.LOGGER.warn(
                        "Trim texture '{}' not found (expected at textures/{}.png) and no fallback texture is set; skipping the trim pass",
                        textureId, textureId.getPath());
            }
            return null;
        }
        var fallback = prepare(fallbackTexture, paletteId);
        if (fallback == null) {
            if (firstReportFor(textureId)) {
                ArmorModelApi.LOGGER.warn(
                        "Trim texture '{}' not found, and fallback '{}' isn't there either; skipping the trim pass",
                        textureId, fallbackTexture);
            }
            return null;
        }
        if (firstReportFor(textureId)) {
            ArmorModelApi.LOGGER.info("Trim texture '{}' not found; falling back to '{}'", textureId, fallbackTexture);
        }
        return fallback;
    }

    /// The paletted texture handle, or null when the base texture doesn't exist. Existence is
    /// checked up front: the manager logs a stack trace for every base texture it cannot load,
    /// and a pattern permutation that a set doesn't ship is an expected miss, not an error.
    private static PalettedTextureManager.@Nullable Handle prepare(Identifier textureId, Identifier paletteId) {
        var minecraft = Minecraft.getInstance();
        var location = textureId.withPath(path -> "textures/" + path + ".png");
        if (minecraft.getResourceManager().getResource(location).isEmpty()) {
            return null;
        }
        var handle = minecraft.getPalettedTextureManager().getOrPrepare(textureId, paletteId);
        return handle.textureLocation().equals(MissingTextureAtlasSprite.getLocation()) ? null : handle;
    }

    private static boolean firstReportFor(Identifier textureId) {
        int currentGeneration = GeoModelCache.generation();
        if (reportedGeneration != currentGeneration) {
            REPORTED_MISSING.clear();
            reportedGeneration = currentGeneration;
        }
        return REPORTED_MISSING.add(textureId);
    }
}
