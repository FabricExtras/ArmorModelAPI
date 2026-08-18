package net.rpg_foundation.armor_api.client.dev;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.rpg_foundation.armor_api.ArmorModelApi;
import net.rpg_foundation.armor_api.client.ArmorRenderers;
import net.rpg_foundation.armor_api.client.GeoArmorRenderer;
import net.rpg_foundation.armor_api.client.layer.EmissiveLayer;
import net.rpg_foundation.armor_api.client.layer.TrimLayer;

import java.util.List;

/// Dev-environment-only smoke test, shared by both platform entrypoints (each gates on its
/// loader's dev-environment check): renders iron armor with the bundled test geo model - a
/// license-free example paladin set exercising hat bones, boots, fractional-size cubes, and
/// implicit bipedX parents. Wear any iron armor piece in runClient to eyeball the baker's
/// output.
///
/// Trim test: trim any iron piece at a smithing table, or:
///   /give @p iron_chestplate[trim={material:"minecraft:redstone",pattern:"minecraft:sentry"}]
/// (patterns are ignored - material-only permutations, like the Wizards sets)
///
/// The test assets under `common/.../assets/armor_model_api/` and the minecraft armor-trims
/// atlas entry are dev fixtures; strip them from the jar before the first real publish
/// (tracked in docs/TASKS.md).
@Environment(EnvType.CLIENT)
public final class DevTestArmor {

    private DevTestArmor() { }

    public static void register() {
        ArmorModelApi.LOGGER.info("Dev environment: registering test armor renderer on iron armor");
        ArmorRenderers.register(
                new GeoArmorRenderer(
                        Identifier.of(ArmorModelApi.MOD_ID, "geo/dev_test.geo.json"),
                        Identifier.of(ArmorModelApi.MOD_ID, "textures/armor/dev_test.png"),
                        List.of(
                                new EmissiveLayer(), // no glowmask file → pass skips itself; proves the guard
                                new TrimLayer(Identifier.of(ArmorModelApi.MOD_ID, "armor/trim/dev_test_generic"), false))),
                Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS);
    }
}
