package net.rpg_foundation.neoforge;

import net.neoforged.fml.common.Mod;

import net.rpg_foundation.ExampleMod;

@Mod(ExampleMod.MOD_ID)
public final class ExampleModNeoForge {
    public ExampleModNeoForge() {
        // Run our common setup.
        ExampleMod.init();
    }
}
