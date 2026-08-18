package net.rpg_foundation.armor_api.fabric;

import net.fabricmc.loader.api.FabricLoader;
import net.rpg_foundation.armor_api.Platform;

public class PlatformImpl {
    public static Platform.Type getPlatformType() {
        return Platform.Type.FABRIC;
    }

    public static class FabricUtil implements Platform.Util {
        @Override
        public boolean isModLoaded(String modid) {
            return FabricLoader.getInstance().isModLoaded(modid);
        }

        @Override
        public boolean isDevelopmentEnvironment() {
            return FabricLoader.getInstance().isDevelopmentEnvironment();
        }
    }

    private static final Platform.Util UTIL = new FabricUtil();

    public static Platform.Util util() {
        return UTIL;
    }
}
