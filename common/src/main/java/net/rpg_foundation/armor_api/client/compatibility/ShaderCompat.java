package net.rpg_foundation.armor_api.client.compatibility;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.irisshaders.iris.api.v0.IrisApi;
import net.rpg_foundation.armor_api.Platform;

import java.util.function.Supplier;

/// Shader-mod awareness, mirroring SpellEngine's `ShaderCompatibility`: when Iris is loaded,
/// live-queries whether a shader pack is actually in use (packs can be toggled at runtime).
/// Iris is a compile-only dependency; the IrisApi reference is only reached behind the
/// isModLoaded gate, so its absence at runtime is safe.
///
/// [#initialize] is called once from each platform client entrypoint.
@Environment(EnvType.CLIENT)
public final class ShaderCompat {

    private static Supplier<Boolean> shaderPackInUse = () -> false;
    private static boolean vanillaRenderSystem = true;

    private ShaderCompat() { }

    public static void initialize() {
        if (Platform.util().isModLoaded("iris")) {
            vanillaRenderSystem = false;
            shaderPackInUse = () -> IrisApi.getInstance().isShaderPackInUse();
        }
    }

    public static boolean isShaderPackInUse() {
        return shaderPackInUse.get();
    }

    public static boolean isVanillaRenderSystem() {
        return vanillaRenderSystem;
    }
}
