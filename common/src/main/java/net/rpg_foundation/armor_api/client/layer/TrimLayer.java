package net.rpg_foundation.armor_api.client.layer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.component.DataComponents;
import net.minecraft.data.AtlasIds;
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

/// Armor trim pass with mod-authored trim textures: resolves a sprite from the vanilla
/// armor-trims atlas by a naming permutation of the item's trim, and re-renders the model with
/// it. Port of the `AzArmorTrimLayer` we originally wrote for AzureLib Armor; the existing
/// `armor/trim/*` sprites and atlas configuration work unchanged.
///
/// Sprite naming: `<base>_<patternName>_<materialName>` when patterns are supported, else
/// `<base>_<materialName>` - or any custom permutation function.
///
/// A stack's [net.rpg_foundation.armor_api.client.ArmorOverrides#trim] replaces the base
/// texture for layers built from one (same naming rule, and it becomes the greyscale fallback);
/// layers built from a custom permutation function have no base to swap and ignore it. A layer
/// from [#fromOverrides] has no base of its own and draws only when the stack supplies one.
///
/// ## Third-party trim materials - greyscale fallback
///
/// Mods can register new trim materials (with their own color palettes). A fixed
/// `paletted_permutations` atlas source generates no variant for a material it doesn't list,
/// so the permuted sprite id resolves to the missing sprite at runtime. Rather than rendering
/// the magenta checker, the layer falls back to the set's **greyscale base trim texture** -
/// an uncolored trim beats a broken one. For the fallback to resolve, stitch the base texture
/// into the armor-trims atlas with a `single` source next to the `paletted_permutations`
/// entry (see the README's atlas example). If the fallback isn't in the atlas either, the
/// pass is skipped. Both cases are logged once per resource reload.
public class TrimLayer implements ArmorRenderLayer {

    private final @Nullable Function<ArmorTrim, Identifier> texturePermutations; // null = override-only
    private final @Nullable Identifier fallbackTexture; // greyscale base; null = no fallback, skip instead
    /// Whether a stack's trim override may re-derive the sprite name: true for layers built
    /// from a base texture (the convention-named layers) and for [#fromOverrides]; false for
    /// custom permutation functions, which have no base to swap.
    private final boolean overridable;
    private final boolean supportPatterns;

    /// Permuted sprite ids already reported missing (log once). Render thread only; reset when
    /// the reload generation moves on - a reload can add the sprite (or the fallback).
    private static final Set<Identifier> REPORTED_MISSING = new HashSet<>();
    private static int reportedGeneration = -1;

    public TrimLayer(Identifier baseTexture) {
        this(baseTexture, true);
    }

    public TrimLayer(Identifier baseTexture, boolean supportPatterns) {
        this(trim -> spriteId(baseTexture, supportPatterns, trim), baseTexture, true, supportPatterns);
    }

    /// A trim pass with no base texture of its own: it draws only for stacks whose overrides
    /// name a `trim` base (the takeover renderer's trim pass).
    public static TrimLayer fromOverrides(boolean supportPatterns) {
        return new TrimLayer(null, null, true, supportPatterns);
    }

    /// The conventional sprite name for a trim on a base texture.
    public static Identifier spriteId(Identifier baseTexture, boolean supportPatterns, ArmorTrim trim) {
        var materialName = trim.material().value().assets().base().suffix();
        if (!supportPatterns) {
            return baseTexture.withSuffix("_" + materialName);
        }
        var patternName = trim.pattern().value().assetId().getPath();
        return baseTexture.withSuffix("_" + patternName + "_" + materialName);
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
        var atlas = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.ARMOR_TRIMS);
        var overrideBase = context.overrides().trim();
        TextureAtlasSprite sprite;
        if (overrideBase != null && overridable) {
            sprite = resolveSprite(atlas, spriteId(overrideBase, supportPatterns, trim), overrideBase);
        } else if (texturePermutations != null) {
            sprite = resolveSprite(atlas, texturePermutations.apply(trim), fallbackTexture);
        } else {
            return; // override-only layer, and the stack supplies no trim base
        }
        if (sprite == null) {
            return;
        }
        // Like vanilla's EquipmentRenderer the glint is a base-pass thing (drawn by the
        // dispatcher); the trim itself is submitted plain, UV-remapped onto its atlas sprite.
        context.submit(Sheets.armorTrimsSheet(trim.pattern().value().decal()), context.light(), -1, sprite);
    }

    /// The sprite for the item's trim; the greyscale fallback when the permutation isn't in
    /// the atlas; null to skip the pass.
    private static @Nullable TextureAtlasSprite resolveSprite(TextureAtlas atlas, Identifier spriteId, @Nullable Identifier fallbackTexture) {
        var sprite = atlas.getSprite(spriteId);
        if (!isMissing(sprite)) {
            return sprite;
        }
        if (fallbackTexture == null) {
            if (firstReportFor(spriteId)) {
                ArmorModelApi.LOGGER.warn(
                        "Trim sprite '{}' not in the armor-trims atlas and no fallback texture is set; skipping the trim pass",
                        spriteId);
            }
            return null;
        }
        var fallback = atlas.getSprite(fallbackTexture);
        if (isMissing(fallback)) {
            if (firstReportFor(spriteId)) {
                ArmorModelApi.LOGGER.warn(
                        "Trim sprite '{}' not in the armor-trims atlas, and fallback '{}' isn't stitched either "
                                + "(add it with a `single` atlas source); skipping the trim pass",
                        spriteId, fallbackTexture);
            }
            return null;
        }
        if (firstReportFor(spriteId)) {
            ArmorModelApi.LOGGER.info(
                    "Trim sprite '{}' not in the armor-trims atlas (third-party trim material?); "
                            + "falling back to greyscale '{}'",
                    spriteId, fallbackTexture);
        }
        return fallback;
    }

    private static boolean isMissing(TextureAtlasSprite sprite) {
        return sprite.contents().name().equals(MissingTextureAtlasSprite.getLocation());
    }

    private static boolean firstReportFor(Identifier spriteId) {
        int currentGeneration = GeoModelCache.generation();
        if (reportedGeneration != currentGeneration) {
            REPORTED_MISSING.clear();
            reportedGeneration = currentGeneration;
        }
        return REPORTED_MISSING.add(spriteId);
    }
}
