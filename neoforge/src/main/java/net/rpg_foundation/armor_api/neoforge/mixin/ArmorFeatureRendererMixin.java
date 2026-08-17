package net.rpg_foundation.armor_api.neoforge.mixin;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.rpg_foundation.armor_api.client.ArmorRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/// NeoForge's half of the render hook, mirroring the injection semantics of Fabric API's own
/// ArmorRenderer mixin: cancel at the head of the per-slot armor render when the dispatcher
/// takes the item, before vanilla touches pose, visibility, or material layers. Cancels only
/// on an actual render, so unregistered items and missing models fall through to vanilla.
/// NeoForge has no equivalent full-takeover API (its IClientItemExtensions can only reskin the
/// vanilla passes), hence the mixin.
///
/// Target subtlety: NeoForge's patched class has TWO renderArmorPiece overloads - the vanilla
/// 6-arg signature (a dead binary-compat delegator) and the patched 12-arg one that `render`
/// actually calls. Only the 12-arg one may be hooked. In the yarn dev jar the patched overload
/// keeps its mojmap name `renderArmorPiece` (no yarn mapping matches its descriptor), and at
/// remap time the name likewise passes through unmapped while the descriptor's types remap to
/// mojmap - so this one explicit target is correct in both dev and production.
@Mixin(ArmorFeatureRenderer.class)
public abstract class ArmorFeatureRendererMixin extends FeatureRenderer<LivingEntity, BipedEntityModel<LivingEntity>> {

    private ArmorFeatureRendererMixin(FeatureRendererContext<LivingEntity, BipedEntityModel<LivingEntity>> context) {
        super(context);
    }

    @Inject(
            method = "renderArmorPiece(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/entity/EquipmentSlot;ILnet/minecraft/client/render/entity/model/BipedEntityModel;FFFFFF)V",
            at = @At("HEAD"),
            cancellable = true
    )
    @SuppressWarnings("unchecked")
    private void armor_model_api$renderGeoArmor(
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            LivingEntity entity,
            EquipmentSlot slot,
            int light,
            @SuppressWarnings("rawtypes") BipedEntityModel model,
            float limbSwing,
            float limbSwingAmount,
            float partialTick,
            float ageInTicks,
            float netHeadYaw,
            float headPitch,
            CallbackInfo ci
    ) {
        var stack = entity.getEquippedStack(slot);
        if (ArmorRenderDispatcher.render(matrices, vertexConsumers, stack, entity, slot, light, this.getContextModel())) {
            ci.cancel();
        }
    }
}
