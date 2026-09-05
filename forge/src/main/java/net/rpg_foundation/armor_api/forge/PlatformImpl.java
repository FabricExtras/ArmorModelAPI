package net.rpg_foundation.armor_api.forge;

import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.LoadingModList;
import net.rpg_foundation.armor_api.Platform;

public class PlatformImpl {
    public static Platform.Type getPlatformType() {
        return Platform.Type.FORGE;
    }

    public static class ForgeUtil implements Platform.Util {
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

    private static final Platform.Util UTIL = new ForgeUtil();

    public static Platform.Util util() {
        return UTIL;
    }
}
