package net.rpg_foundation.armor_api.client;

import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.rpg_foundation.armor_api.client.model.GeoArmorBones;
import net.minecraft.registry.tag.ItemTags;

/// The one armor render routine, shared verbatim by both platform hooks. Runs inside the
/// entity feature-render pass, replacing vanilla's `renderArmor` body for registered items:
///
/// 1. the stack's [ArmorOverrides] pick the model and base texture in effect (a broken
///    override model falls back to the renderer's own)
/// 2. the per-slot model (slot visibility baked in, incl. the FEET → boot bones mapping
///    vanilla can't express); the vanilla pose is applied at draw time from the render state
/// 3. base pass - armor cutout render layer, dye color, then the armor glint pass
/// 4. the renderer's extra layers (trim, glow, ...), each a plain re-submit with its own layer
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
            MatrixStack matrices,
            OrderedRenderCommandQueue queue,
            ItemStack stack,
            BipedEntityRenderState state,
            EquipmentSlot slot,
            int light,
            BipedEntityModel<BipedEntityRenderState> contextModel
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
        var equippable = stack.get(DataComponentTypes.EQUIPPABLE);
        if (equippable == null || equippable.slot() != slot) {
            return false;
        }
        // Players need the PlayerEntityModel variant so player-animation libraries pose the armor too
        boolean player = state instanceof PlayerEntityRenderState;
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

        int color = stack.isIn(ItemTags.DYEABLE)
                ? DyedColorComponent.getColor(stack, DyedColorComponent.DEFAULT_COLOR)
                : -1;
        var context = new ArmorRenderContext(matrices, queue, stack, state, slot, light, renderer, model, texture, overrides);
        context.submit(RenderLayers.armorCutoutNoCull(texture), light, color, null);
        if (stack.hasGlint()) {
            context.submit(RenderLayers.armorEntityGlint(), light, color, null);
        }
        for (var layer : renderer.config().layers()) {
            layer.render(context);
        }
        return true;
    }
}
