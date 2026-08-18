package net.rpg_foundation.armor_api.neoforge;

import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.LoadingModList;
import net.rpg_foundation.armor_api.Platform;

public class PlatformImpl {
    public static Platform.Type getPlatformType() {
        return Platform.Type.NEOFORGE;
    }

    public static class NeoForgeUtil implements Platform.Util {
        @Override
        public boolean isModLoaded(String modid) {
            // LoadingModList (not ModList): populated during mod discovery, before any constructor runs,
            // so early compat gates in static initializers / init match Fabric's "resolved up front" timing.
            return LoadingModList.get().getModFileById(modid) != null;
        }

        @Override
        public boolean isDevelopmentEnvironment() {
            return !FMLLoader.isProduction();
        }
    }

    private static final Platform.Util UTIL = new NeoForgeUtil();

    public static Platform.Util util() {
        return UTIL;
    }
}
