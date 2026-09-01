package net.rpg_foundation.armor_api.example;

import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.rpg_foundation.armor_api.client.ArmorRenderers;
import net.rpg_foundation.armor_api.client.GeoArmorRenderer;
import net.rpg_foundation.armor_api.client.layer.EmissiveLayer;
import net.rpg_foundation.armor_api.client.layer.EmissiveLayer.Mode;
import net.rpg_foundation.armor_api.client.layer.TrimLayer;

import java.util.List;

/// Example consumer of the Armor Model API, doubling as the dev smoke test: registers custom
/// renderers on the vanilla armor sets, one per feature area. Wear a piece in runClient and
/// compare against the source model in Blockbench.
///
/// - **Iron** — the bundled license-free example paladin set: hat bones, boots,
///   fractional-size cubes, implicit bipedX parents; no glowmask, proving the emissive
///   skip-guard
/// - **Diamond** — justicar set: hand-authored glowmask on the default [Mode#GLOW]
/// - **Netherite** — lightbringer set: [Mode#RADIANT] (fill + additive burn), side-by-side
///   with diamond's plain glow
/// - **Gold** — spellblade set: the `armorWaist` bone (skirt/tassets shown with the LEGS
///   piece, anchored to the chest). Gold leggings alone must render the waist following torso
///   pose; gold chestplate alone must NOT show it.
/// - **Chainmail** — wizard robe set: cloth geometry on a 64x64 atlas, where every other set
///   here is 128x128, so it covers `texture_width`/`texture_height` being honoured per model
///   rather than assumed.
///
/// Trim test: trim any piece at a smithing table, or:
///   /give @p iron_chestplate[trim={material:"minecraft:redstone",pattern:"minecraft:sentry"}]
/// (patterns are ignored - material-only permutations, like the Wizards sets). A trim material
/// added by a third-party mod exercises TrimLayer's greyscale fallback.
///
/// Override test (`custom_data` component, see README "Item component overrides"): iron wearing
/// the chainmail set's assets, then a broken model id that must fall back to iron's own model:
///   /give @p iron_chestplate[minecraft:custom_data={armor_model_api:{model:"armor_model_api_example:geo/copyright_wizard_robes.geo.json",texture:"armor_model_api_example:textures/armor/copyright_wizard_robe.png"}}]
///   /give @p iron_chestplate[minecraft:custom_data={armor_model_api:{model:"armor_model_api_example:geo/nope.geo.json"}}]
///
/// The `copyright_`-prefixed assets (justicar/lightbringer/spellblade/wizard-robe sets) are
/// copyright-protected and gitignored - present only on machines that have them locally. In a
/// fresh clone those registrations log a missing-geo error once and fall back
/// gracefully; only the license-free iron dev_test set renders.
public final class ExampleArmor {

    private ExampleArmor() { }

    public static void register() {
        ExampleArmorMod.LOGGER.info("Registering example armor renderers on vanilla armor sets");
        ArmorRenderers.register(
                new GeoArmorRenderer(
                        Identifier.of(ExampleArmorMod.MOD_ID, "geo/dev_test.geo.json"),
                        Identifier.of(ExampleArmorMod.MOD_ID, "textures/armor/dev_test.png"),
                        List.of(
                                new EmissiveLayer(), // no glowmask file → pass skips itself; proves the guard
                                new TrimLayer(Identifier.of(ExampleArmorMod.MOD_ID, "armor/trim/dev_test_generic"), false))),
                Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS);

        ArmorRenderers.register(
                new GeoArmorRenderer(
                        Identifier.of(ExampleArmorMod.MOD_ID, "geo/copyright_justicar_armor.geo.json"),
                        Identifier.of(ExampleArmorMod.MOD_ID, "textures/armor/copyright_justicar_armor.png"),
                        List.of(
                                new EmissiveLayer(),
                                new TrimLayer(Identifier.of(ExampleArmorMod.MOD_ID, "armor/trim/copyright_justicar_armor_generic"), false))),
                Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS);

        ArmorRenderers.register(
                new GeoArmorRenderer(
                        Identifier.of(ExampleArmorMod.MOD_ID, "geo/copyright_lightbringer_armor.geo.json"),
                        Identifier.of(ExampleArmorMod.MOD_ID, "textures/armor/copyright_lightbringer_armor.png"),
                        List.of(
                                new EmissiveLayer(Mode.RADIANT),
                                new TrimLayer(Identifier.of(ExampleArmorMod.MOD_ID, "armor/trim/copyright_lightbringer_armor_generic"), false))),
                Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS);

        ArmorRenderers.register(
                new GeoArmorRenderer(
                        Identifier.of(ExampleArmorMod.MOD_ID, "geo/copyright_spellblade_armor.geo.json"),
                        Identifier.of(ExampleArmorMod.MOD_ID, "textures/armor/copyright_spellblade_armor.png"),
                        List.of(new EmissiveLayer())), // no glowmask → skip-guard path
                Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS);

        ArmorRenderers.register(
                new GeoArmorRenderer(
                        Identifier.of(ExampleArmorMod.MOD_ID, "geo/copyright_wizard_robes.geo.json"),
                        Identifier.of(ExampleArmorMod.MOD_ID, "textures/armor/copyright_wizard_robe.png"),
                        List.of(
                                new EmissiveLayer(), // no glowmask → skip-guard path
                                new TrimLayer(Identifier.of(ExampleArmorMod.MOD_ID, "armor/trim/copyright_wizard_robe_generic"), false))),
                Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS);
    }
}
