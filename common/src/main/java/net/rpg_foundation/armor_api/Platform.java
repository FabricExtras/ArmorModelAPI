package net.rpg_foundation.armor_api;

import dev.architectury.injectables.annotations.ExpectPlatform;

/// Loader abstraction seam, mirroring SpellEngine's `Platform`: the common module never touches
/// a loader API; each platform module provides a `PlatformImpl` next to its entrypoint that
/// Architectury's @ExpectPlatform transformer wires in at build time.
public class Platform {
    public static final boolean Fabric;
    public static final boolean Forge;

    static {
        Fabric = getPlatformType() == Type.FABRIC;
        Forge = getPlatformType() == Type.FORGE;
    }

    public enum Type { FABRIC, FORGE }

    @ExpectPlatform
    protected static Type getPlatformType() {
        throw new AssertionError();
    }

    public interface Util {
        boolean isModLoaded(String modid);

        /// Whether the game is running in a development environment (dev workspace / loom run),
        /// as opposed to a packaged production install. Fabric: `FabricLoader.isDevelopmentEnvironment()`;
        /// Forge: `!FMLLoader.isProduction()`. Kept here so `common` needs no loader API for the check.
        boolean isDevelopmentEnvironment();
    }

    @ExpectPlatform
    public static Util util() {
        throw new AssertionError();
    }
}
