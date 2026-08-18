package net.rpg_foundation.armor_api.client.layer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.texture.MissingSprite;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.trim.ArmorTrim;
import net.minecraft.util.Identifier;
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
@Environment(EnvType.CLIENT)
public class TrimLayer implements ArmorRenderLayer {

    private final Function<ArmorTrim, Identifier> texturePermutations;
    private final @Nullable Identifier fallbackTexture; // greyscale base; null = no fallback, skip instead

    /// Permuted sprite ids already reported missing (log once). Render thread only; reset when
    /// the reload generation moves on - a reload can add the sprite (or the fallback).
    private static final Set<Identifier> REPORTED_MISSING = new HashSet<>();
    private static int reportedGeneration = -1;

    public TrimLayer(Identifier baseTexture) {
        this(baseTexture, true);
    }

    public TrimLayer(Identifier baseTexture, boolean supportPatterns) {
        this(supportPatterns
                ? trim -> {
                    var patternName = trim.getPattern().value().assetId().getPath();
                    var materialName = trim.getMaterial().value().assetName();
                    return baseTexture.withSuffixedPath("_" + patternName + "_" + materialName);
                }
                : trim -> baseTexture.withSuffixedPath("_" + trim.getMaterial().value().assetName()),
                baseTexture);
    }

    public TrimLayer(Function<ArmorTrim, Identifier> texturePermutations) {
        this(texturePermutations, null);
    }

    public TrimLayer(Function<ArmorTrim, Identifier> texturePermutations, @Nullable Identifier fallbackTexture) {
        this.texturePermutations = texturePermutations;
        this.fallbackTexture = fallbackTexture;
    }

    @Override
    public int preferredOrder() {
        return ORDER_TRIM;
    }

    @Override
    public void render(ArmorRenderContext context) {
        var trim = context.stack().get(DataComponentTypes.TRIM);
        if (trim == null) {
            return;
        }
        var atlas = MinecraftClient.getInstance().getBakedModelManager()
                .getAtlas(TexturedRenderLayers.ARMOR_TRIMS_ATLAS_TEXTURE);
        var sprite = resolveSprite(atlas, trim);
        if (sprite == null) {
            return;
        }
        var consumer = sprite.getTextureSpecificVertexConsumer(ItemRenderer.getArmorGlintConsumer(
                context.vertexConsumers(),
                TexturedRenderLayers.getArmorTrims(trim.getPattern().value().decal()),
                context.stack().hasGlint()));
        context.model().render(context.matrices(), consumer, context.light(), OverlayTexture.DEFAULT_UV);
    }

    /// The sprite for the item's trim; the greyscale fallback when the permutation isn't in
    /// the atlas; null to skip the pass.
    private @Nullable Sprite resolveSprite(SpriteAtlasTexture atlas, ArmorTrim trim) {
        var spriteId = texturePermutations.apply(trim);
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

    private static boolean isMissing(Sprite sprite) {
        return sprite.getContents().getId().equals(MissingSprite.getMissingSpriteId());
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
