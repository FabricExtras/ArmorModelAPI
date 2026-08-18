package net.rpg_foundation.armor_api.client.layer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import net.rpg_foundation.armor_api.client.ArmorRenderContext;
import net.rpg_foundation.armor_api.client.compatibility.ShaderCompat;

/// High-luminance glow: the [EmissiveLayer] pass swapped for an opaque radiant fill, plus an
/// additive burn pass that drives the same pixels past the texel's own brightness - toward
/// white under vanilla, and into a shader pack's bloom threshold under packs. Ported from
/// Armory's RadiantGlowLayer/ArmoryGlowLayers (where the config gating stays; this class is
/// the mechanism only).
///
/// ## Gain policy - live-read every frame
///
/// [#gain] is how hard the burn is driven (vanilla only). Above one it buys brightness as
/// coverage - mid tones climb into the framebuffer clamp; too far and the mask flattens white.
/// One or below turns the burn pass off entirely.
///
/// Under a shader pack ([ShaderCompat#isShaderPackInUse]) the gain is forced to 1: Iris folds
/// shader color into `gl_Color` at the *start* of the pack's program, and a >1 texel breaks
/// every color test the pack runs (Lightbringer's yellow famously came back green). At gain 1
/// the burn is a plain additive duplicate of the fill - which is all the brightness a pack
/// gets from it, and enough for its bloom to find.
@Environment(EnvType.CLIENT)
public class RadiantEmissiveLayer extends EmissiveLayer {

    /// Live-tunable; Armory ships 2.5.
    public static float gain = 2.5F;

    public RadiantEmissiveLayer() {
        super();
    }

    public RadiantEmissiveLayer(Identifier maskTexture) {
        super(maskTexture);
    }

    public static float effectiveGain() {
        return ShaderCompat.isShaderPackInUse() ? 1F : gain;
    }

    /// The fill: same emissive program, but opaque and depth-writing.
    @Override
    protected RenderLayer renderLayer(ArmorRenderContext context, Identifier glowTexture) {
        return ArmorRenderLayers.radiantFill(glowTexture);
    }

    /// Fill (inherited), then the additive burn over it.
    @Override
    public void render(ArmorRenderContext context) {
        super.render(context);
        if (gain <= 1F) {
            return; // burn turned off everywhere (under a pack, gain 1 still draws the additive duplicate)
        }
        var glowTexture = emissiveTexture(context);
        if (glowTexture == null) {
            return;
        }
        context.model().render(
                context.matrices(),
                context.vertexConsumers().getBuffer(ArmorRenderLayers.radiantBurn(glowTexture)),
                LightmapTextureManager.MAX_LIGHT_COORDINATE,
                OverlayTexture.DEFAULT_UV);
    }
}
