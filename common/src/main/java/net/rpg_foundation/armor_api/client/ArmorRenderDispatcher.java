package net.rpg_foundation.armor_api.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.math.ColorHelper;

/// The one armor render routine, shared verbatim by both platform hooks. Runs inside the
/// entity feature-render pass, replacing vanilla's `renderArmor` body for registered items:
///
/// 1. pose copy from the entity's posed context model (vanilla would do this too)
/// 2. slot visibility (incl. the FEET → boot bones mapping vanilla can't express)
/// 3. base pass - armor cutout render layer, dye color, glint via the armor foil buffer
/// 4. the renderer's extra layers (trim, glow, ...), each a plain re-render with its own buffer
///
/// Returns false when nothing was rendered (unregistered item, wrong slot, missing model) so
/// the NeoForge mixin can leave vanilla rendering untouched in that case.
@Environment(EnvType.CLIENT)
public final class ArmorRenderDispatcher {

    private ArmorRenderDispatcher() { }

    public static boolean render(
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            ItemStack stack,
            LivingEntity entity,
            EquipmentSlot slot,
            int light,
            BipedEntityModel<LivingEntity> contextModel
    ) {
        var renderer = ArmorRenderers.get(stack.getItem());
        if (renderer == null) {
            return false;
        }
        // Vanilla's own guard runs after the hook point, so it is replicated here: a chestplate
        // held in the head slot must not render as armor.
        if (!(stack.getItem() instanceof ArmorItem armorItem) || armorItem.getSlotType() != slot) {
            return false;
        }
        var model = renderer.model();
        if (model == null) {
            return false; // missing/broken geo asset, already logged by the cache
        }

        contextModel.copyBipedStateTo(model);
        model.applySlotVisibility(slot);

        int color = stack.isIn(ItemTags.DYEABLE)
                ? ColorHelper.Argb.fullAlpha(DyedColorComponent.getColor(stack, DyedColorComponent.DEFAULT_COLOR))
                : -1;
        var consumer = ItemRenderer.getArmorGlintConsumer(
                vertexConsumers,
                RenderLayer.getArmorCutoutNoCull(renderer.config().texture()),
                stack.hasGlint());
        model.render(matrices, consumer, light, OverlayTexture.DEFAULT_UV, color);

        var context = new ArmorRenderContext(matrices, vertexConsumers, stack, entity, slot, light, renderer, model);
        for (var layer : renderer.config().layers()) {
            layer.render(context);
        }
        return true;
    }
}
