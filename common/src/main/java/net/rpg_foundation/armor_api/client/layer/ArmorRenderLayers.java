package net.rpg_foundation.armor_api.client.layer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.util.function.Function;

/// Custom render layers for armor passes. Extends RenderLayer for access to the protected
/// phase constants and parameter builder (the standard pattern for mod-defined render layers,
/// same as SpellEngine's CustomLayers); RenderLayer.of itself comes via the access widener.
@Environment(EnvType.CLIENT)
public final class ArmorRenderLayers extends RenderLayer {

    /// Vanilla's `entityTranslucentEmissive` with one addition: `VIEW_OFFSET_Z_LAYERING`, the
    /// depth nudge every vanilla armor layer (`armorCutoutNoCull`, trim, glint) carries so
    /// armor doesn't z-fight the body. Without it an overdraw pass sits at TRUE depth -
    /// *behind* the base pass's offset depth - and fails the depth test everywhere (glow
    /// invisible in world, flickering in the tiny inventory viewport where the offset is
    /// sub-precision). Any layer drawn over an armor base pass must carry the same layering.
    private static final Function<Identifier, RenderLayer> EMISSIVE = Util.memoize(texture ->
            of(
                    "armor_model_api_emissive",
                    VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL,
                    VertexFormat.DrawMode.QUADS,
                    1536,
                    true,
                    true,
                    MultiPhaseParameters.builder()
                            .program(ENTITY_TRANSLUCENT_EMISSIVE_PROGRAM)
                            .texture(new Texture(texture, false, false))
                            .transparency(TRANSLUCENT_TRANSPARENCY)
                            .cull(DISABLE_CULLING)
                            .writeMaskState(COLOR_MASK)
                            .overlay(ENABLE_OVERLAY_COLOR)
                            .layering(VIEW_OFFSET_Z_LAYERING)
                            .build(true)));

    private ArmorRenderLayers() {
        // never instantiated - subclassing only for protected member access
        super("armor_model_api_layers", VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL,
                VertexFormat.DrawMode.QUADS, 1536, false, false, () -> { }, () -> { });
    }

    public static RenderLayer emissive(Identifier texture) {
        return EMISSIVE.apply(texture);
    }
}
