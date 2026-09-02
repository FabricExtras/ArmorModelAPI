package net.rpg_foundation.armor_api.fabric.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.rpg_foundation.armor_api.client.ArmorRenderDispatcher;
import net.rpg_foundation.armor_api.client.ArmorRenderers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/// Fabric's hook for **data-driven takeovers** only. Registered items reach the dispatcher
/// through Fabric API's per-item `ArmorRenderer` callback (mirrored by the client entrypoint),
/// but an item nobody registered has no callback - so a stack whose overrides take over such
/// an item ([net.rpg_foundation.armor_api.client.ArmorOverrides#takesOver]) needs this
/// injection, mirroring the one Fabric API itself places at the head of `renderArmorPiece`.
/// Registered items are skipped here so they are never rendered twice, whichever of the two
/// head injections runs first; the dispatcher declines everything else, leaving vanilla untouched.
@Mixin(HumanoidArmorLayer.class)
public abstract class ArmorFeatureRendererMixin extends RenderLayer<HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {

    private ArmorFeatureRendererMixin(RenderLayerParent<HumanoidRenderState, HumanoidModel<HumanoidRenderState>> context) {
        super(context);
    }

    @Inject(method = "renderArmorPiece", at = @At("HEAD"), cancellable = true)
    private void armor_model_api$renderTakeover(
            PoseStack matrices,
            SubmitNodeCollector queue,
            ItemStack stack,
            EquipmentSlot slot,
            int light,
            HumanoidRenderState state,
            CallbackInfo ci
    ) {
        if (ArmorRenderers.get(stack.getItem()) != null) {
            return; // registered: Fabric API's ArmorRenderer callback owns this item
        }
        if (ArmorRenderDispatcher.render(matrices, queue, stack, state, slot, light, this.getParentModel())) {
            ci.cancel();
        }
    }
}
