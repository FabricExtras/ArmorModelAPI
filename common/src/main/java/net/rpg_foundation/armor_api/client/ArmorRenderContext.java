package net.rpg_foundation.armor_api.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.rpg_foundation.armor_api.client.model.GeoArmorModel;

/// Everything a render layer needs for its passes. Immutable - a layer that wants another pass
/// picks a buffer from [#vertexConsumers] and calls [GeoArmorModel#render] again; there is no
/// shared mutable pipeline state to save and restore.
///
/// The model arrives posed (vanilla pose copied) with slot visibility already applied, and
/// already reflects the stack's [ArmorOverrides]: [#model] is the model in effect (override or
/// the renderer's own) and [#texture] the base texture in effect. Layers that derive assets
/// from the base texture must use [#texture], not the renderer config, so per-stack reskins
/// carry through; asset-specific overrides (glowmask, trim) are on [#overrides].
///
/// @param texture   the base texture in effect for this piece (stack override, else the renderer's)
/// @param overrides the stack's overrides, [ArmorOverrides#NONE] when it has none
@Environment(EnvType.CLIENT)
public record ArmorRenderContext(
        MatrixStack matrices,
        VertexConsumerProvider vertexConsumers,
        ItemStack stack,
        LivingEntity entity,
        EquipmentSlot slot,
        int light,
        GeoArmorRenderer renderer,
        GeoArmorModel model,
        Identifier texture,
        ArmorOverrides overrides
) { }
