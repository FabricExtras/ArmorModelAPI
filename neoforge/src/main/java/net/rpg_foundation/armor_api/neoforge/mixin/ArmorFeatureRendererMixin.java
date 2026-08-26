package net.rpg_foundation.armor_api.neoforge.mixin;

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
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/// NeoForge's half of the render hook, mirroring the injection semantics of Fabric API's own
/// ArmorRenderer mixin: cancel at the head of the per-slot armor render when the dispatcher
/// takes the item, before vanilla touches the equipment model or material layers. Cancels only
/// on an actual render, so unregistered items and missing models fall through to vanilla.
/// NeoForge has no equivalent full-takeover API (its IClientItemExtensions can only reskin the
/// vanilla passes), hence the mixin.
///
/// Since 1.21.11 NeoForge no longer patches this method (the 1.21.1 12-arg overload is gone),
/// so the plain vanilla per-slot method is the one and only target.
@Mixin(HumanoidArmorLayer.class)
public abstract class ArmorFeatureRendererMixin extends RenderLayer<HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {

    private ArmorFeatureRendererMixin(RenderLayerParent<HumanoidRenderState, HumanoidModel<HumanoidRenderState>> context) {
        super(context);
    }

    @Inject(method = "renderArmorPiece", at = @At("HEAD"), cancellable = true)
    private void armor_model_api$renderGeoArmor(
            PoseStack matrices,
            SubmitNodeCollector queue,
            ItemStack stack,
            EquipmentSlot slot,
            int light,
            HumanoidRenderState state,
            CallbackInfo ci
    ) {
        if (ArmorRenderDispatcher.render(matrices, queue, stack, state, slot, light, this.getParentModel())) {
            ci.cancel();
        }
    }
}
