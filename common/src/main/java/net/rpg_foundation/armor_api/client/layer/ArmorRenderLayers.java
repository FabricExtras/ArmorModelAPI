package net.rpg_foundation.armor_api.client.layer;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.LayeringTransform;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.rpg_foundation.armor_api.ArmorModelApi;

import java.util.function.Function;

/// Custom render layers for armor passes. Since 1.21.11 a render layer is a public
/// `RenderSetup` over a `RenderPipeline` (which owns blend/depth/cull state), so no access
/// widener is needed any more; the pipelines are compiled on first use.
public final class ArmorRenderLayers {

    private ArmorRenderLayers() { }

    /// Vanilla's `entityTranslucentEmissive` with one addition: `VIEW_OFFSET_Z_LAYERING`, the
    /// depth nudge every vanilla armor layer (`armorCutoutNoCull`, trim, glint) carries so
    /// armor doesn't z-fight the body. Without it an overdraw pass sits at TRUE depth -
    /// *behind* the base pass's offset depth - and fails the depth test everywhere (glow
    /// invisible in world, flickering in the tiny inventory viewport where the offset is
    /// sub-precision). Any layer drawn over an armor base pass must carry the same layering.
    private static final Function<Identifier, RenderLayer> EMISSIVE = Util.memoize(texture ->
            RenderLayer.of(
                    "armor_model_api_emissive",
                    RenderSetup.builder(RenderPipelines.ENTITY_TRANSLUCENT_EMISSIVE)
                            .texture("Sampler0", texture)
                            .useOverlay()
                            .translucent()
                            .layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                            .build()));

    public static RenderLayer emissive(Identifier texture) {
        return EMISSIVE.apply(texture);
    }

    // --- Radiant glow (fill + additive burn), ported from Armory's ArmoryGlowLayers ---------
    //
    // The fill draws the emissive pixels; the burn re-draws them additively, driven past the
    // texel's own brightness (EmissiveLayer#effectiveGain), which is the only lever that
    // climbs past the texel and gives shader-pack bloom something to find. Both carry
    // VIEW_OFFSET_Z_LAYERING - with the overdraw design every armor pass must match the base
    // pass's depth nudge.
    //
    // Pre-1.21.11 the burn confined additive blending to color attachment 0 (glDisablei on the
    // shader pack's extra gbuffer attachments). Blend state is now pipeline-owned and there
    // is no per-attachment hook, so the burn blends every attachment; under a pack the burn is
    // a single plain additive duplicate (gain 1), which keeps the damage minimal.

    /// Emissive fill: the translucent-emissive pipeline, but WRITING depth so the burn's
    /// depth test has an anchor. Translucent-classified on purpose (see EmissiveLayer): it
    /// must stay in the same sorting bucket as the (possibly SS-translucent) base pass.
    private static final RenderPipeline RADIANT_FILL_PIPELINE = RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
            .withLocation(Identifier.of(ArmorModelApi.MOD_ID, "pipeline/radiant_fill"))
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .withShaderDefine("PER_FACE_LIGHTING")
            .withSampler("Sampler1")
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .withDepthWrite(true)
            .build();

    /// Additive burn over the fill; writes no depth - the fill already did, at the same
    /// coordinates, and this only adds light to what is there.
    private static final RenderPipeline RADIANT_BURN_PIPELINE = RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
            .withLocation(Identifier.of(ArmorModelApi.MOD_ID, "pipeline/radiant_burn"))
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .withShaderDefine("PER_FACE_LIGHTING")
            .withSampler("Sampler1")
            .withBlend(BlendFunction.ADDITIVE)
            .withCull(false)
            .withDepthWrite(false)
            .build();

    private static final Function<Identifier, RenderLayer> RADIANT_FILL = Util.memoize(texture ->
            RenderLayer.of(
                    "armor_model_api_radiant_fill",
                    RenderSetup.builder(RADIANT_FILL_PIPELINE)
                            .texture("Sampler0", texture)
                            .useOverlay()
                            .translucent()
                            .layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                            .build()));

    private static final Function<Identifier, RenderLayer> RADIANT_BURN = Util.memoize(texture ->
            RenderLayer.of(
                    "armor_model_api_radiant_burn",
                    RenderSetup.builder(RADIANT_BURN_PIPELINE)
                            .texture("Sampler0", texture)
                            .useOverlay()
                            .translucent()
                            .layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                            .build()));

    /// @param emissiveTexture the composited glow texture, not the base armor texture
    public static RenderLayer radiantFill(Identifier emissiveTexture) {
        return RADIANT_FILL.apply(emissiveTexture);
    }

    /// @param emissiveTexture the composited glow texture, not the base armor texture
    public static RenderLayer radiantBurn(Identifier emissiveTexture) {
        return RADIANT_BURN.apply(emissiveTexture);
    }
}
