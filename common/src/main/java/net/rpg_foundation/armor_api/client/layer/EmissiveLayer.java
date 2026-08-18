package net.rpg_foundation.armor_api.client.layer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import net.rpg_foundation.armor_api.ArmorModelApi;
import net.rpg_foundation.armor_api.client.ArmorRenderContext;
import net.rpg_foundation.armor_api.client.ArmorRenderLayer;
import net.rpg_foundation.armor_api.client.GeoModelCache;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/// Glow pass: re-renders the model with the set's `_glowmask` texture on an emissive render
/// layer at full brightness. The mask is drawn *over* the base pass (equal depth passes the
/// depth test) - unlike AzureLib there is no runtime punch-out of the base texture.
///
/// Default texture: `<baseTexture>_glowmask.png` next to the renderer's base texture, the
/// existing hand-authored convention. The pass is skipped (once-logged) when the mask file
/// doesn't exist.
///
/// Subclass hooks mirror what Armory's RadiantGlowLayer needs: [#renderLayer] to swap the
/// render layer, [#render] to add passes around this one.
@Environment(EnvType.CLIENT)
public class EmissiveLayer implements ArmorRenderLayer {

    private final @Nullable Identifier texture; // null = derive from the renderer's base texture

    /// Cleared indirectly on resource reload via the generation stamp. Render thread only,
    /// like all layer code - layers are invoked exclusively by the dispatcher mid-render.
    private static final Map<Identifier, Boolean> TEXTURE_EXISTS = new HashMap<>();
    private static int existsCacheGeneration = -1;

    public EmissiveLayer() {
        this.texture = null;
    }

    public EmissiveLayer(Identifier texture) {
        this.texture = texture;
    }

    @Override
    public void render(ArmorRenderContext context) {
        var glowTexture = emissiveTexture(context);
        if (glowTexture == null) {
            return;
        }
        var renderLayer = renderLayer(context, glowTexture);
        context.model().render(
                context.matrices(),
                context.vertexConsumers().getBuffer(renderLayer),
                LightmapTextureManager.MAX_LIGHT_COORDINATE,
                OverlayTexture.DEFAULT_UV);
    }

    protected RenderLayer renderLayer(ArmorRenderContext context, Identifier glowTexture) {
        return RenderLayer.getEntityTranslucentEmissive(glowTexture);
    }

    /// The glow texture, or null to skip the pass (no mask authored for this set).
    protected @Nullable Identifier emissiveTexture(ArmorRenderContext context) {
        var glowTexture = texture != null
                ? texture
                : glowmaskOf(context.renderer().config().texture());
        return textureExists(glowTexture) ? glowTexture : null;
    }

    /// `.../x.png` → `.../x_glowmask.png`, the convention shared with AzureLib-era assets.
    public static Identifier glowmaskOf(Identifier baseTexture) {
        var path = baseTexture.getPath();
        return path.endsWith(".png")
                ? baseTexture.withPath(path.substring(0, path.length() - 4) + "_glowmask.png")
                : baseTexture.withSuffixedPath("_glowmask");
    }

    private static boolean textureExists(Identifier texture) {
        int currentGeneration = GeoModelCache.generation();
        if (existsCacheGeneration != currentGeneration) {
            TEXTURE_EXISTS.clear();
            existsCacheGeneration = currentGeneration;
        }
        return TEXTURE_EXISTS.computeIfAbsent(texture, id -> {
            boolean exists = MinecraftClient.getInstance().getResourceManager().getResource(id).isPresent();
            if (!exists) {
                ArmorModelApi.LOGGER.warn("Emissive layer texture '{}' not found; skipping the glow pass", id);
            }
            return exists;
        });
    }
}
