package net.rpg_foundation.armor_api.client.layer;

import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.rpg_foundation.armor_api.ArmorModelApi;

import java.util.List;
import java.util.function.Function;

/// Custom render layers for armor passes. Since 1.21.11 a render layer is a public
/// `RenderSetup` over a `RenderPipeline` (which owns blend/depth/cull state), so no access
/// widener is needed any more; the pipelines are compiled on first use.
///
/// Every layer here blends, and since 26.3 a blending model submit is routed to the
/// translucent phase - which, with *Improved Transparency* on, is the order-independent
/// (OIT) phase, where a render type without OIT pipelines throws. These passes are overdraw
/// at the armor's own depth over an opaque base pass, so they have no use for OIT (the
/// radiant fill even writes depth for the burn, which OIT never does): like vanilla's
/// `trimmedArmorGlint`, they force the solid model phase and draw in submission order.
public final class ArmorRenderLayers {

    private ArmorRenderLayers() { }

    /// Vanilla's `entityTranslucentEmissive` with one addition: `VIEW_OFFSET_Z_LAYERING`, the
    /// depth nudge every vanilla armor layer (`armorCutoutNoCull`, trim, glint) carries so
    /// armor doesn't z-fight the body. Without it an overdraw pass sits at TRUE depth -
    /// *behind* the base pass's offset depth - and fails the depth test everywhere (glow
    /// invisible in world, flickering in the tiny inventory viewport where the offset is
    /// sub-precision). Any layer drawn over an armor base pass must carry the same layering.
    private static final Function<Identifier, RenderType> EMISSIVE = Util.memoize(texture ->
            RenderType.create(
                    "armor_model_api_emissive",
                    RenderSetup.builder(RenderPipelines.ENTITY_TRANSLUCENT_EMISSIVE)
                            .withTexture("Sampler0", texture)
                            .useOverlay()
                            .sortOnUpload()
                            .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                            .withForcedSolidModelPhase()
                            .createRenderSetup()));

    public static RenderType emissive(Identifier texture) {
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
    /// depth test has an anchor. Blending on purpose (see EmissiveLayer); the phase it draws
    /// in is pinned to solid by the render setup (see the class comment).
    private static final RenderPipeline RADIANT_FILL_PIPELINE = RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(ArmorModelApi.MOD_ID, "pipeline/radiant_fill"))
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .withShaderDefine("PER_FACE_LIGHTING")
            .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withCull(false)
            .withDepthStencilState(DepthStencilState.DEFAULT)   // GEQUAL (reverse-Z since 26.2) + depth write
            .build();

    /// Additive burn over the fill; writes no depth - the fill already did, at the same
    /// coordinates, and this only adds light to what is there.
    private static final RenderPipeline RADIANT_BURN_PIPELINE = RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(ArmorModelApi.MOD_ID, "pipeline/radiant_burn"))
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .withShaderDefine("PER_FACE_LIGHTING")
            .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
            .withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
            .withCull(false)
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .build();

    private static final Function<Identifier, RenderType> RADIANT_FILL = Util.memoize(texture ->
            RenderType.create(
                    "armor_model_api_radiant_fill",
                    RenderSetup.builder(RADIANT_FILL_PIPELINE)
                            .withTexture("Sampler0", texture)
                            .useOverlay()
                            .sortOnUpload()
                            .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                            .withForcedSolidModelPhase()
                            .createRenderSetup()));

    private static final Function<Identifier, RenderType> RADIANT_BURN = Util.memoize(texture ->
            RenderType.create(
                    "armor_model_api_radiant_burn",
                    RenderSetup.builder(RADIANT_BURN_PIPELINE)
                            .withTexture("Sampler0", texture)
                            .useOverlay()
                            .sortOnUpload()
                            .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                            .withForcedSolidModelPhase()
                            .createRenderSetup()));

    /// @param emissiveTexture the composited glow texture, not the base armor texture
    public static RenderType radiantFill(Identifier emissiveTexture) {
        return RADIANT_FILL.apply(emissiveTexture);
    }

    /// @param emissiveTexture the composited glow texture, not the base armor texture
    public static RenderType radiantBurn(Identifier emissiveTexture) {
        return RADIANT_BURN.apply(emissiveTexture);
    }

    /// Every `RenderPipeline` this class builds itself, i.e. the ones a shader mod cannot know
    /// about from vanilla's registry. Iris maps *pipelines* (not render layers) to its shader
    /// programs, so each of these has to be declared once - see
    /// [net.rpg_foundation.armor_api.client.compatibility.ShaderCompat#initialize].
    ///
    /// The vanilla-derived [#emissive] layer is deliberately absent: it runs on
    /// `RenderPipelines.ENTITY_TRANSLUCENT_EMISSIVE`, which Iris already has in its core map.
    public static List<RenderPipeline> customPipelines() {
        return List.of(RADIANT_FILL_PIPELINE, RADIANT_BURN_PIPELINE);
    }
}
