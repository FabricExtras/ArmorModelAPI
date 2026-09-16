package net.rpg_foundation.armor_api.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.rpg_foundation.armor_api.client.model.GeoArmorBones;

/// The one armor render routine, shared verbatim by both platform hooks. Runs inside the
/// entity feature-render pass, replacing vanilla's `renderArmor` body for registered items:
///
/// 1. the stack's [ArmorOverrides] pick the model and base texture in effect (a broken
///    override model falls back to the renderer's own)
/// 2. the per-slot model (slot visibility baked in, incl. the FEET → boot bones mapping
///    vanilla can't express); the vanilla pose is applied at draw time from the render state
/// 3. base pass - armor cutout render layer, dye color, with the glint folded in for untrimmed foil
/// 4. the renderer's extra layers (trim, glow, ...), each a plain re-submit with its own layer;
///    a trimmed foil piece gets vanilla's post-trim glint pass after its trim (or last)
///
/// An item without a renderer is still rendered when its stack's overrides name both a model
/// and a texture ([ArmorOverrides#takesOver]) - through the shared
/// [ArmorRenderers#takeoverRenderer]; there a broken model is left to vanilla instead of falling
/// back, since the takeover renderer has no model of its own.
///
/// Returns false when nothing was rendered (unregistered item without takeover data, wrong slot,
/// missing model) so the platform mixins can leave vanilla rendering untouched in that case.
public final class ArmorRenderDispatcher {

    private ArmorRenderDispatcher() { }

    public static boolean render(
            PoseStack matrices,
            SubmitNodeCollector queue,
            ItemStack stack,
            HumanoidRenderState state,
            EquipmentSlot slot,
            int light,
            HumanoidModel<HumanoidRenderState> contextModel
    ) {
        var overrides = ArmorOverrides.of(stack);
        var renderer = ArmorRenderers.get(stack.getItem());
        boolean takeover = false;
        if (renderer == null) {
            if (!overrides.takesOver()) {
                return false;
            }
            renderer = ArmorRenderers.takeoverRenderer();
            takeover = true;
        }
        // Vanilla's own guard runs after the hook point, so it is replicated here: a chestplate
        // held in the head slot must not render as armor.
        var equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable == null || equippable.slot() != slot) {
            return false;
        }
        // Players need the PlayerEntityModel variant so player-animation libraries pose the armor too
        boolean player = state instanceof AvatarRenderState;
        GeoArmorBones model = null;
        if (overrides.model() != null) {
            model = player ? renderer.playerModel(overrides.model(), slot) : renderer.model(overrides.model(), slot);
        }
        if (model == null && !takeover) {
            // no override, or a missing/broken one (logged once by the cache) → the renderer's own
            model = player ? renderer.playerModel(slot) : renderer.model(slot);
        }
        if (model == null) {
            return false; // missing/broken geo asset, already logged by the cache
        }
        var texture = overrides.textureOr(renderer.config().texture());

        // 26.1: there is no `dyeable` item tag any more; vanilla tints per equipment layer from the
        // stack's dyed_color component. Same here: dyed → tint, undyed → untinted.
        int color = DyedItemColor.getOrDefault(stack, -1);
        var context = new ArmorRenderContext(matrices, queue, stack, state, slot, light, renderer, model, texture, overrides);
        // 26.3: no standalone glint pass any more. Like vanilla's EquipmentLayerRenderer, an
        // untrimmed foil piece draws its glint inside the base pass; a trimmed one gets the
        // post-trim glint from the context (after the trim layer, else after the last layer).
        boolean glintOnBase = stack.hasFoil() && !context.glintPending();
        context.submit(glintOnBase ? RenderTypes.armorCutoutNoCullGlint(texture) : RenderTypes.armorCutoutNoCull(texture), light, color, null);
        for (var layer : renderer.config().layers()) {
            layer.render(context);
        }
        context.submitPendingGlint();
        return true;
    }
}
