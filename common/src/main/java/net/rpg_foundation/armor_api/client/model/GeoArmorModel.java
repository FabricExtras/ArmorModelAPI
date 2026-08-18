package net.rpg_foundation.armor_api.client.model;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/// A real `BipedEntityModel` whose standard parts carry baked geo armor bones as children.
/// Everything vanilla does with a biped armor model - pose copy via `copyBipedStateTo`,
/// baby scaling, render passes with any buffer - works on it unchanged.
///
/// One instance per [net.rpg_foundation.armor_api.client.GeoArmorRenderer], mutated freely on
/// the render thread: the dispatcher rewrites pose and visibility at the start of every render.
@Environment(EnvType.CLIENT)
public class GeoArmorModel extends BipedEntityModel<LivingEntity> {

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

    public GeoArmorModel(ModelPart root) {
        super(root);
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

    /// Shows exactly the armor bones belonging to `slot` and hides the other conventional
    /// bones. The standard biped parts stay visible - they are empty and only exist to carry
    /// the vanilla pose; hiding them would hide their children.
    public void applySlotVisibility(EquipmentSlot slot) {
        setVisible(true);
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

    /// The conventional bone part, for layers that want a single-bone pass. Null when the
    /// model doesn't have it.
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
        return root.traverse()
                .filter(part -> part.hasChild(name))
                .map(part -> part.getChild(name))
                .findFirst()
                .orElse(null);
    }
}
