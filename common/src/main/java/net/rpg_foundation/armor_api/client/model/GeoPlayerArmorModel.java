package net.rpg_foundation.armor_api.client.model;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.world.entity.EquipmentSlot;
import org.jetbrains.annotations.Nullable;

/// The player variant of [GeoArmorModel]: a `PlayerEntityModel` (like vanilla's own player armor
/// pieces, `EntityModelLayers.PLAYER_EQUIPMENT`), so player-animation libraries that hook
/// `PlayerEntityModel.setAngles` (PAL, used by Better Combat / Spell Engine) pose the armor along with
/// the player. The baked root carries the empty `jacket` / sleeve / pants parts the constructor requires.
public class GeoPlayerArmorModel extends PlayerModel implements GeoArmorBones {
    private final GeoArmorBoneSet bones;

    public GeoPlayerArmorModel(ModelPart root) {
        super(root, false);
        this.bones = new GeoArmorBoneSet(root);
    }

    @Override
    public void applySlotVisibility(EquipmentSlot slot) {
        setAllVisible(true);
        // The player-skin overlay parts are empty carriers here; keep them out of the way regardless
        leftSleeve.visible = false;
        rightSleeve.visible = false;
        leftPants.visible = false;
        rightPants.visible = false;
        jacket.visible = false;
        bones.applySlotVisibility(slot);
    }

    @Override
    public @Nullable ModelPart armorBone(String name) {
        return bones.armorBone(name);
    }
}
