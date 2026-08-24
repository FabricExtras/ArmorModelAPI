package net.rpg_foundation.armor_api.neoforge.mixin;

import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
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
@Mixin(ArmorFeatureRenderer.class)
public abstract class ArmorFeatureRendererMixin extends FeatureRenderer<BipedEntityRenderState, BipedEntityModel<BipedEntityRenderState>> {

    private ArmorFeatureRendererMixin(FeatureRendererContext<BipedEntityRenderState, BipedEntityModel<BipedEntityRenderState>> context) {
        super(context);
    }

    @Inject(method = "renderArmor", at = @At("HEAD"), cancellable = true)
    private void armor_model_api$renderGeoArmor(
            MatrixStack matrices,
            OrderedRenderCommandQueue queue,
            ItemStack stack,
            EquipmentSlot slot,
            int light,
            BipedEntityRenderState state,
            CallbackInfo ci
    ) {
        if (ArmorRenderDispatcher.render(matrices, queue, stack, state, slot, light, this.getContextModel())) {
            ci.cancel();
        }
    }
}
