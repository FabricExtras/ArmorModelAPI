package net.rpg_foundation.armor_api.example.forge;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.rpg_foundation.armor_api.example.ExampleArmor;
import net.rpg_foundation.armor_api.example.ExampleArmorMod;

@Mod(ExampleArmorMod.MOD_ID)
public final class ExampleForge {

    public ExampleForge() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ExampleArmor.register();
        }
    }
}
