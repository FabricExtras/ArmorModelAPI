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
            PoseStack matrices,
            SubmitNodeCollector queue,
            ItemStack stack,
            HumanoidRenderState state,
            EquipmentSlot slot,
            int light,
            HumanoidModel<HumanoidRenderState> contextModel
    ) {
        var renderer = ArmorRenderers.get(stack.getItem());
        if (renderer == null) {
            return false;
        }
        // Vanilla's own guard runs after the hook point, so it is replicated here: a chestplate
        // held in the head slot must not render as armor.
        var equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable == null || equippable.slot() != slot) {
            return false;
        }
        // Players need the PlayerEntityModel variant so player-animation libraries pose the armor too
        var model = state instanceof AvatarRenderState ? renderer.playerModel(slot) : renderer.model(slot);
        if (model == null) {
            return false; // missing/broken geo asset, already logged by the cache
        }

        // 26.1: there is no `dyeable` item tag any more; vanilla tints per equipment layer from the
        // stack's dyed_color component. Same here: dyed → tint, undyed → untinted.
        int color = DyedItemColor.getOrDefault(stack, -1);
        var context = new ArmorRenderContext(matrices, queue, stack, state, slot, light, renderer, model);
        context.submit(RenderTypes.armorCutoutNoCull(renderer.config().texture()), light, color, null);
        if (stack.hasFoil()) {
            context.submit(RenderTypes.armorEntityGlint(), light, color, null);
        }
        for (var layer : renderer.config().layers()) {
            layer.render(context);
        }
        return true;
    }
}
