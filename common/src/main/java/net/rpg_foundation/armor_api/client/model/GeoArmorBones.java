package net.rpg_foundation.armor_api.client.model;

import net.minecraft.client.model.ModelPart;
import net.minecraft.entity.EquipmentSlot;
import org.jetbrains.annotations.Nullable;

/// The conventional armor bones of a baked geo armor model, independent of which vanilla model class
/// carries them ([GeoArmorModel] for bipeds, [GeoPlayerArmorModel] for players).
public interface GeoArmorBones {
    /// Shows exactly the armor bones belonging to `slot` and hides the other conventional bones.
    void applySlotVisibility(EquipmentSlot slot);

    /// The conventional bone part, for layers that want a single-bone pass. Null when the model doesn't have it.
    @Nullable ModelPart armorBone(String name);
}
