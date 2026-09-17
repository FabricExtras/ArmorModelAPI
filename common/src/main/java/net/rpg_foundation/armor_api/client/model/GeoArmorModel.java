package net.rpg_foundation.armor_api.client.model;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import org.jetbrains.annotations.Nullable;

/// A real `BipedEntityModel` whose standard parts carry baked geo armor bones as children, for
/// non-player bipeds (mobs, armor stands). It is never posed by its own `setAngles`: at draw time
/// the pose is copied from the entity's body model ([PoseCopyingModel]), which is what makes it
/// entity-agnostic - an armor stand's fixed rotations, a zombie's raised arms, a player's
/// animations all come from the model vanilla already posed for that entity.
///
/// Players get [GeoPlayerArmorModel] instead: player-animation mods (PAL) hook `PlayerEntityModel.setAngles`,
/// which is also what vanilla's player armor pieces are, so only a `PlayerEntityModel` follows those animations.
///
/// One instance per slot per [net.rpg_foundation.armor_api.client.GeoArmorRenderer]: the slot
/// visibility is applied once at creation, the pose is copied on right before each draw.
public class GeoArmorModel extends HumanoidModel<HumanoidRenderState> implements GeoArmorBones {
    private final GeoArmorBoneSet bones;

    public GeoArmorModel(ModelPart root) {
        super(root);
        this.bones = new GeoArmorBoneSet(root);
    }

    @Override
    public void applySlotVisibility(EquipmentSlot slot) {
        GeoArmorBoneSet.setBipedVisible(this, true);
        bones.applySlotVisibility(slot);
    }

    @Override
    public @Nullable ModelPart armorBone(String name) {
        return bones.armorBone(name);
    }
}
