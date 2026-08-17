package net.rpg_foundation.armor_api.fabric.client;

import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.rpg_foundation.armor_api.ArmorModelApi;
import net.rpg_foundation.armor_api.client.ArmorRenderers;
import net.rpg_foundation.armor_api.client.GeoArmorRenderer;
import net.rpg_foundation.armor_api.client.layer.EmissiveLayer;

import java.util.List;

/// Dev-environment-only smoke test: renders iron armor with the bundled test geo model
/// (a copy of Wizards' wizard robes - rotated cubes, hat bone, boots, the works).
/// Wear any iron armor piece in runClient to eyeball the baker's output.
///
/// The test assets under `assets/armor_model_api/` are dev fixtures; strip them from the jar
/// before the first real publish (tracked in docs/TASKS.md).
final class DevTestArmor {

    private DevTestArmor() { }

    static void register() {
        ArmorModelApi.LOGGER.info("Dev environment: registering test armor renderer on iron armor");
        ArmorRenderers.register(
                new GeoArmorRenderer(
                        Identifier.of(ArmorModelApi.MOD_ID, "geo/dev_test.geo.json"),
                        Identifier.of(ArmorModelApi.MOD_ID, "textures/armor/dev_test.png"),
                        List.of(new EmissiveLayer())), // no glowmask file → pass skips itself; proves the guard
                Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS);
    }
}
