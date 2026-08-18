package net.rpg_foundation.armor_api.example.fabric.client;

import net.fabricmc.api.ClientModInitializer;
import net.rpg_foundation.armor_api.example.ExampleArmor;

public final class ExampleFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ExampleArmor.register();
    }
}
