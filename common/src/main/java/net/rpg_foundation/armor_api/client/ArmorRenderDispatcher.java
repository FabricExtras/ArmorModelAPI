package net.rpg_foundation.armor_api.client;

import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;

/// The one armor render routine, shared verbatim by both platform hooks. Runs inside the
/// entity feature-render pass, replacing vanilla's `renderArmor` body for registered items:
///
/// 1. the per-slot model (slot visibility baked in, incl. the FEET → boot bones mapping
///    vanilla can't express); the vanilla pose is applied at draw time from the render state
/// 2. base pass - armor cutout render layer, dye color, then the armor glint pass
/// 3. the renderer's extra layers (trim, glow, ...), each a plain re-submit with its own layer
///
/// Returns false when nothing was rendered (unregistered item, wrong slot, missing model) so
/// the NeoForge mixin can leave vanilla rendering untouched in that case.
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
        var renderer = ArmorRenderers.get(stack.getItem());
        if (renderer == null) {
            return false;
        }
        // Vanilla's own guard runs after the hook point, so it is replicated here: a chestplate
        // held in the head slot must not render as armor.
        var equippable = stack.get(DataComponentTypes.EQUIPPABLE);
        if (equippable == null || equippable.slot() != slot) {
            return false;
        }
        var model = renderer.model(slot);
        if (model == null) {
            return false; // missing/broken geo asset, already logged by the cache
        }

        int color = stack.isIn(ItemTags.DYEABLE)
                ? DyedColorComponent.getColor(stack, DyedColorComponent.DEFAULT_COLOR)
                : -1;
        var context = new ArmorRenderContext(matrices, queue, stack, state, slot, light, renderer, model);
        context.submit(RenderLayers.armorCutoutNoCull(renderer.config().texture()), light, color, null);
        if (stack.hasGlint()) {
            context.submit(RenderLayers.armorEntityGlint(), light, color, null);
        }
        for (var layer : renderer.config().layers()) {
            layer.render(context);
        }
        return true;
    }
}
