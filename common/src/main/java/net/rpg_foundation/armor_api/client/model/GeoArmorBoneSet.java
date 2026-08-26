package net.rpg_foundation.armor_api.client.model;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.EquipmentSlot;
import org.jetbrains.annotations.Nullable;

/// Resolves and toggles the conventional armor bones under a baked root; shared by the model classes.
final class GeoArmorBoneSet implements GeoArmorBones {
    // Conventional armor bones, resolved by name anywhere under the standard parts.
    // Null when the geo model doesn't have the bone (e.g. robes without boot bones).
    private final @Nullable ModelPart armorHead;
    private final @Nullable ModelPart armorBody;
    private final @Nullable ModelPart armorRightArm;
    private final @Nullable ModelPart armorLeftArm;
    private final @Nullable ModelPart armorRightLeg;
    private final @Nullable ModelPart armorLeftLeg;
    private final @Nullable ModelPart armorRightBoot;
    private final @Nullable ModelPart armorLeftBoot;
    private final @Nullable ModelPart armorWaist;

    GeoArmorBoneSet(ModelPart root) {
        this.armorHead = findPart(root, "armorHead");
        this.armorBody = findPart(root, "armorBody");
        this.armorRightArm = findPart(root, "armorRightArm");
        this.armorLeftArm = findPart(root, "armorLeftArm");
        this.armorRightLeg = findPart(root, "armorRightLeg");
        this.armorLeftLeg = findPart(root, "armorLeftLeg");
        this.armorRightBoot = findPart(root, "armorRightBoot");
        this.armorLeftBoot = findPart(root, "armorLeftBoot");
        this.armorWaist = findPart(root, "armorWaist");
    }

    /// The standard biped parts stay visible - they are empty and only exist to carry the vanilla pose;
    /// hiding them would hide their children. (The caller has already `setVisible(true)`d them.)
    @Override
    public void applySlotVisibility(EquipmentSlot slot) {
        setArmorBoneVisible(armorHead, slot == EquipmentSlot.HEAD);
        setArmorBoneVisible(armorBody, slot == EquipmentSlot.CHEST);
        setArmorBoneVisible(armorRightArm, slot == EquipmentSlot.CHEST);
        setArmorBoneVisible(armorLeftArm, slot == EquipmentSlot.CHEST);
        setArmorBoneVisible(armorRightLeg, slot == EquipmentSlot.LEGS);
        setArmorBoneVisible(armorLeftLeg, slot == EquipmentSlot.LEGS);
        setArmorBoneVisible(armorRightBoot, slot == EquipmentSlot.FEET);
        setArmorBoneVisible(armorLeftBoot, slot == EquipmentSlot.FEET);
        // Waist geometry ships with the LEGS piece but hangs off the body part, so it follows
        // torso pose (sneak/swim pitch) rather than a leg's swing.
        setArmorBoneVisible(armorWaist, slot == EquipmentSlot.LEGS);
    }

    @Override
    public @Nullable ModelPart armorBone(String name) {
        return switch (name) {
            case "armorHead" -> armorHead;
            case "armorBody" -> armorBody;
            case "armorRightArm" -> armorRightArm;
            case "armorLeftArm" -> armorLeftArm;
            case "armorRightLeg" -> armorRightLeg;
            case "armorLeftLeg" -> armorLeftLeg;
            case "armorRightBoot" -> armorRightBoot;
            case "armorLeftBoot" -> armorLeftBoot;
            case "armorWaist" -> armorWaist;
            default -> null;
        };
    }

    private static void setArmorBoneVisible(@Nullable ModelPart part, boolean visible) {
        if (part != null) {
            part.visible = visible;
        }
    }

    private static @Nullable ModelPart findPart(ModelPart root, String name) {
        return root.getAllParts().stream()
                .filter(part -> part.hasChild(name))
                .map(part -> part.getChild(name))
                .findFirst()
                .orElse(null);
    }
}
