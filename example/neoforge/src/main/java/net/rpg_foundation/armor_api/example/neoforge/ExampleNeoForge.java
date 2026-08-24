package net.rpg_foundation.armor_api.example.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.rpg_foundation.armor_api.example.ExampleArmor;
import net.rpg_foundation.armor_api.example.ExampleArmorMod;

@Mod(ExampleArmorMod.MOD_ID)
public final class ExampleNeoForge {

    public ExampleNeoForge() {
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            ExampleArmor.register();
        }
    }
}
