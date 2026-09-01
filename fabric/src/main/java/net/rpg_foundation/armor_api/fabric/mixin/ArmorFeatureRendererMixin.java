package net.rpg_foundation.armor_api.fabric.mixin;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
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
/// injection, mirroring the one Fabric API itself places at the head of `renderArmor`.
/// Registered items are skipped here so they are never rendered twice, whichever of the two
/// head injections runs first; the dispatcher declines everything else, leaving vanilla untouched.
@Mixin(ArmorFeatureRenderer.class)
public abstract class ArmorFeatureRendererMixin extends FeatureRenderer<LivingEntity, BipedEntityModel<LivingEntity>> {

    private ArmorFeatureRendererMixin(FeatureRendererContext<LivingEntity, BipedEntityModel<LivingEntity>> context) {
        super(context);
    }

    @Inject(method = "renderArmor", at = @At("HEAD"), cancellable = true)
    private void armor_model_api$renderTakeover(
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            LivingEntity entity,
            EquipmentSlot slot,
            int light,
            @SuppressWarnings("rawtypes") BipedEntityModel model,
            CallbackInfo ci
    ) {
        var stack = entity.getEquippedStack(slot);
        if (ArmorRenderers.get(stack.getItem()) != null) {
            return; // registered: Fabric API's ArmorRenderer callback owns this item
        }
        if (ArmorRenderDispatcher.render(matrices, vertexConsumers, stack, entity, slot, light, this.getContextModel())) {
            ci.cancel();
        }
    }
}
