package net.rpg_foundation.armor_api.forge.mixin;

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

/// Forge's half of the render hook, mirroring the injection semantics of Fabric API's own
/// ArmorRenderer mixin: cancel at the head of the per-slot armor render when the dispatcher
/// takes the item, before vanilla touches pose, visibility, or material layers. Cancels only
/// on an actual render, so unregistered items and missing models fall through to vanilla.
///
/// Forge 47 has no equivalent full-takeover API: `IClientItemExtensions#getHumanoidArmorModel`
/// can swap the model and `IForgeItem#getArmorTexture` the texture, but the trim pass is
/// unpatched and still draws on the *vanilla* model, and the extra passes (glow, custom trim
/// sprites) have no seat at all - hence the mixin.
///
/// Target: 1.20.1's private per-slot method `renderArmor(MatrixStack, VertexConsumerProvider,
/// T entity, EquipmentSlot, int light, A model)`; Forge's patch keeps the exact vanilla
/// signature (it only reroutes the model/texture lookups inside the body), and there is a
/// single overload, so the plain yarn name is unambiguous. Loom remaps the name to SRG for
/// the production jar. Generic parameters erase to `LivingEntity` / `BipedEntityModel`.
@Mixin(ArmorFeatureRenderer.class)
public abstract class ArmorFeatureRendererMixin extends FeatureRenderer<LivingEntity, BipedEntityModel<LivingEntity>> {

    private ArmorFeatureRendererMixin(FeatureRendererContext<LivingEntity, BipedEntityModel<LivingEntity>> context) {
        super(context);
    }

    @Inject(
            method = "renderArmor(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/entity/EquipmentSlot;ILnet/minecraft/client/render/entity/model/BipedEntityModel;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void armor_model_api$renderGeoArmor(
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            LivingEntity entity,
            EquipmentSlot slot,
            int light,
            @SuppressWarnings("rawtypes") BipedEntityModel model,
            CallbackInfo ci
    ) {
        var stack = entity.getEquippedStack(slot);
        if (ArmorRenderDispatcher.render(matrices, vertexConsumers, stack, entity, slot, light, this.getContextModel())) {
            ci.cancel();
        }
    }
}
