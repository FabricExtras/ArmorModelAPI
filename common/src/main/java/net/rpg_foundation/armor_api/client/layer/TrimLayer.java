package net.rpg_foundation.armor_api.client.layer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.trim.ArmorTrim;
import net.minecraft.util.Identifier;
import net.rpg_foundation.armor_api.client.ArmorRenderContext;
import net.rpg_foundation.armor_api.client.ArmorRenderLayer;

import java.util.function.Function;

/// Armor trim pass with mod-authored trim textures: resolves a sprite from the vanilla
/// armor-trims atlas by a naming permutation of the item's trim, and re-renders the model with
/// it. Port of the `AzArmorTrimLayer` we originally wrote for AzureLib Armor; the existing
/// `armor/trim/*` sprites and atlas configuration work unchanged.
///
/// Sprite naming: `<base>_<patternName>_<materialName>` when patterns are supported, else
/// `<base>_<materialName>` - or any custom permutation function.
@Environment(EnvType.CLIENT)
public class TrimLayer implements ArmorRenderLayer {

    private final Function<ArmorTrim, Identifier> texturePermutations;

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
                : trim -> baseTexture.withSuffixedPath("_" + trim.getMaterial().value().assetName()));
    }

    public TrimLayer(Function<ArmorTrim, Identifier> texturePermutations) {
        this.texturePermutations = texturePermutations;
    }

    @Override
    public void render(ArmorRenderContext context) {
        var trim = context.stack().get(DataComponentTypes.TRIM);
        if (trim == null) {
            return;
        }
        var atlas = MinecraftClient.getInstance().getBakedModelManager()
                .getAtlas(TexturedRenderLayers.ARMOR_TRIMS_ATLAS_TEXTURE);
        var sprite = atlas.getSprite(texturePermutations.apply(trim));
        var consumer = sprite.getTextureSpecificVertexConsumer(ItemRenderer.getArmorGlintConsumer(
                context.vertexConsumers(),
                TexturedRenderLayers.getArmorTrims(trim.getPattern().value().decal()),
                context.stack().hasGlint()));
        context.model().render(context.matrices(), consumer, context.light(), OverlayTexture.DEFAULT_UV);
    }
}
