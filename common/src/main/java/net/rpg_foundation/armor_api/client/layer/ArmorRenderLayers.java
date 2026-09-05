package net.rpg_foundation.armor_api.client.layer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.function.Function;

/// Custom render layers for armor passes. Extends RenderLayer for access to the protected
/// phase constants and parameter builder (the standard pattern for mod-defined render layers,
/// same as SpellEngine's CustomLayers); RenderLayer.of itself comes via the access widener
/// (converted to an access transformer on Forge).
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

    // --- Radiant glow (fill + additive burn), ported from Armory's ArmoryGlowLayers ---------
    //
    // The fill draws the emissive pixels opaquely; the burn re-draws them additively with the
    // shader color driven past one (EmissiveLayer#effectiveGain), which is the only
    // lever that climbs past the texel's own brightness and gives shader-pack bloom something
    // to find. Both carry VIEW_OFFSET_Z_LAYERING - Armory's originals didn't need it because
    // AzureLib punched glow pixels out of the base texture, so no base depth existed there;
    // with our overdraw design every armor pass must match the base pass's depth nudge.

    /// GL guarantees at least this many draw buffers, so indices 0..7 are always valid to
    /// address without raising GL_INVALID_VALUE.
    private static final int GUARANTEED_DRAW_BUFFERS = 8;

    /// Plain additive, **confined to color attachment 0**. Blending is framebuffer state: a
    /// shader pack's gbuffers_entities writes per-pixel material/light/normal notes to further
    /// attachments, and additive-blending THOSE pins them at max - Complementary then mirrored
    /// sky onto the glow (yellow came back green). glDisablei keeps the burn additive on the
    /// color attachment only; the other attachments take a plain write of the same values the
    /// fill just wrote, so the notes come out identical to a single pass.
    private static final Transparency ADDITIVE_COLOR_ONLY = new Transparency(
            "armor_model_api_burn_transparency",
            () -> {
                RenderSystem.enableBlend();
                RenderSystem.blendFunc(GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ONE);
                // enableBlend is a plain glEnable, which turns every attachment on at once
                for (int attachment = 1; attachment < GUARANTEED_DRAW_BUFFERS; attachment++) {
                    GL30.glDisablei(GL11.GL_BLEND, attachment);
                }
            },
            () -> {
                // no glEnablei loop needed: disableBlend clears the per-attachment enables too
                RenderSystem.disableBlend();
                RenderSystem.defaultBlendFunc();
            });

    /// Shader color is global render state; saved and restored rather than reset to a presumed
    /// default, so another mod's shader color survives. Draws are sequential and setup/teardown
    /// are paired, so a single slot is enough.
    private static final float[] SHADER_COLOR_TO_RESTORE = new float[4];

    private static final Texturing GAIN = new Texturing("armor_model_api_burn_gain",
            () -> {
                // getShaderColor hands out the live array, not a copy
                System.arraycopy(RenderSystem.getShaderColor(), 0, SHADER_COLOR_TO_RESTORE, 0, 4);
                float gain = EmissiveLayer.effectiveGain();
                RenderSystem.setShaderColor(gain, gain, gain, 1F);
            },
            () -> RenderSystem.setShaderColor(
                    SHADER_COLOR_TO_RESTORE[0], SHADER_COLOR_TO_RESTORE[1],
                    SHADER_COLOR_TO_RESTORE[2], SHADER_COLOR_TO_RESTORE[3]));

    /// Emissive fill. Armory's original used NO_TRANSPARENCY to land in Iris's *opaque* draw
    /// bucket - safe there, because AzureLib punched glow pixels out of the base texture, so
    /// pass order between base and fill never mattered. With our overdraw design it matters
    /// absolutely, and the opaque bucket became a trap: Shoulder Surfing's player-transparency
    /// feature rebuilds every `armorCutoutNoCull` with TRANSLUCENT_TRANSPARENCY, which moves
    /// the BASE pass into Iris's general-translucent bucket - drawn after the opaque bucket -
    /// so the world-lit base composited OVER the opaque-bucketed glow (radiant went dim the
    /// moment SS transparency was on under a pack).
    ///
    /// Fix: the fill is translucent-classified itself, keeping it in the same bucket as the
    /// base pass under every combination of SS and shader packs; within a bucket Iris keeps
    /// request order, and vanilla's Immediate keeps request order regardless. At the mask's
    /// full alpha, SRC_ALPHA blending is a passthrough, so the rendered result is unchanged -
    /// and SS's near-camera alpha fade now applies to the fill for free. ALL_MASK still writes
    /// depth so the burn's depth test has an anchor.
    private static final Function<Identifier, RenderLayer> RADIANT_FILL = Util.memoize(texture ->
            of(
                    "armor_model_api_radiant_fill",
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
                            .writeMaskState(ALL_MASK)
                            .overlay(ENABLE_OVERLAY_COLOR)
                            .layering(VIEW_OFFSET_Z_LAYERING)
                            .build(false)));

    /// Additive burn over the fill; writes no depth - the fill already did, at the same
    /// coordinates, and this only adds light to what is there.
    private static final Function<Identifier, RenderLayer> RADIANT_BURN = Util.memoize(texture ->
            of(
                    "armor_model_api_radiant_burn",
                    VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL,
                    VertexFormat.DrawMode.QUADS,
                    1536,
                    true,
                    true,
                    MultiPhaseParameters.builder()
                            .program(ENTITY_TRANSLUCENT_EMISSIVE_PROGRAM)
                            .texture(new Texture(texture, false, false))
                            .transparency(ADDITIVE_COLOR_ONLY)
                            .cull(DISABLE_CULLING)
                            .writeMaskState(COLOR_MASK)
                            .overlay(ENABLE_OVERLAY_COLOR)
                            .texturing(GAIN)
                            .layering(VIEW_OFFSET_Z_LAYERING)
                            .build(false)));

    /// @param emissiveTexture the composited glow texture, not the base armor texture
    public static RenderLayer radiantFill(Identifier emissiveTexture) {
        return RADIANT_FILL.apply(emissiveTexture);
    }

    /// @param emissiveTexture the composited glow texture, not the base armor texture
    public static RenderLayer radiantBurn(Identifier emissiveTexture) {
        return RADIANT_BURN.apply(emissiveTexture);
    }
}
