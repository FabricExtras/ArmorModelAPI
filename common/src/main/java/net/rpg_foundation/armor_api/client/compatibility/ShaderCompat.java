package net.rpg_foundation.armor_api.client.compatibility;

import net.irisshaders.iris.api.v0.IrisApi;
import net.rpg_foundation.armor_api.ArmorModelApi;
import net.rpg_foundation.armor_api.Platform;
import net.rpg_foundation.armor_api.client.layer.ArmorRenderLayers;

import java.util.function.Supplier;

/// Shader-mod awareness, mirroring SpellEngine's `ShaderCompatibility`/`IrisCompatibility`:
/// when Iris is loaded this live-queries whether a shader pack is actually in use (packs can be
/// toggled at runtime) and declares our custom render pipelines to Iris.
///
/// Iris is a compile-only dependency; every IrisApi reference sits behind the isModLoaded gate
/// and inside a nested class, so its absence at runtime never loads the API classes.
///
/// [#initialize] is called once from each platform client entrypoint.
public final class ShaderCompat {

    private static Supplier<Boolean> shaderPackInUse = () -> false;
    private static boolean vanillaRenderSystem = true;

    private ShaderCompat() { }

    public static void initialize() {
        if (Platform.util().isModLoaded("iris")) {
            vanillaRenderSystem = false;
            shaderPackInUse = () -> IrisApi.getInstance().isShaderPackInUse();
            try {
                Iris.assignPipelines();
            } catch (Throwable e) {
                ArmorModelApi.LOGGER.warn("Failed to register Armor Model API pipelines with Iris: {}", e.toString());
            }
        }
    }

    public static boolean isShaderPackInUse() {
        return shaderPackInUse.get();
    }

    public static boolean isVanillaRenderSystem() {
        return vanillaRenderSystem;
    }

    /// Nested so the Iris API classes are only loaded when Iris is actually present.
    private static final class Iris {

        /// Since 1.21.11 Iris resolves the shader program to run from the `RenderPipeline`, not
        /// from the render layer: it looks the pipeline up in its own map and, when it isn't
        /// there, leaves the *vanilla* GLSL program in place while the pack's gbuffers are bound
        /// (`MixinShaderManager_Overrides`, plus a "Missing program … in override list" error per
        /// pipeline). A vanilla entity program writes colortex0 only, so the pack's deferred pass
        /// has no normals/lightmap/specular for those fragments and composites the pass away -
        /// the radiant glow simply vanishes under every shader pack while looking correct without
        /// one. The [ArmorRenderLayers#emissive] pass never showed this because it runs on
        /// vanilla's `ENTITY_TRANSLUCENT_EMISSIVE`, which Iris ships in its core map.
        ///
        /// A pure-vanilla fix is not available: the radiant look needs a depth-writing emissive
        /// fill and an additive emissive burn, and vanilla registers no such `RenderPipeline`
        /// (`EYES`/`ENTITY_TRANSLUCENT_EMISSIVE` are both alpha-blended, neither writes depth).
        /// So the pipelines stay custom and are declared to Iris here instead - `assignPipeline`
        /// is the API Iris provides for exactly this, present unchanged in 1.10.x and 1.11.x.
        ///
        /// `EMISSIVE_ENTITIES` maps to Iris' `gbuffers_spidereyes` program (fullbright, no
        /// diffuse lighting), the same program vanilla mob eyes and `ENTITY_TRANSLUCENT_EMISSIVE`
        /// resolve to - which is what makes the pass emissive to the pack and visible to its
        /// bloom. The shadow-pass variant (`assignPipelineShadow`, new in Iris 1.11) is
        /// deliberately not called: it does not exist in 1.10.x and would hard-fail there, and a
        /// glow overdraw contributes nothing to a shadow map.
        static void assignPipelines() {
            var api = IrisApi.getInstance();
            for (var pipeline : ArmorRenderLayers.customPipelines()) {
                api.assignPipeline(pipeline, net.irisshaders.iris.api.v0.IrisProgram.EMISSIVE_ENTITIES);
            }
            ArmorModelApi.LOGGER.info("Registered {} custom pipelines with Iris",
                    ArmorRenderLayers.customPipelines().size());
        }
    }
}
