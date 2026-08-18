package net.rpg_foundation.armor_api.client.layer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.rpg_foundation.armor_api.ArmorModelApi;
import net.rpg_foundation.armor_api.client.ArmorRenderContext;
import net.rpg_foundation.armor_api.client.ArmorRenderLayer;
import net.rpg_foundation.armor_api.client.GeoModelCache;
import net.rpg_foundation.armor_api.client.compatibility.ShaderCompat;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/// Glow pass: re-renders the model with the set's emissive texture on an emissive render layer
/// at full brightness, drawn *over* the base pass (equal depth passes the depth test). The
/// [Mode] picks the intensity: [Mode#GLOW] (the default) is the single fullbright pass;
/// [Mode#RADIANT] swaps it for an opaque radiant fill plus an additive burn pass that drives
/// the same pixels past the texel's own brightness - toward white under vanilla, and into a
/// shader pack's bloom threshold under packs.
///
/// The hand-authored `<baseTexture>_glowmask.png` is a **stencil**, not a picture: only its
/// pixels' positions and alpha matter, the colors come from the base texture (AzureLib's
/// convention - its `AutoGlowingTexture` composited the two at runtime). This layer does the
/// same compositing once per mask: a runtime texture is baked with the base texture's RGB and
/// the mask's alpha, registered with the texture manager, and re-baked after resource reloads.
/// Unlike AzureLib the base texture is left intact (no punch-out) - the emissive overdraw
/// covers the same pixels.
///
/// The pass is skipped (once-logged) when the mask file doesn't exist, so the layer is safe to
/// add unconditionally across a family of sets.
///
/// [#emissiveTexture] stays overridable for layers that resolve the glow texture differently.
@Environment(EnvType.CLIENT)
public class EmissiveLayer implements ArmorRenderLayer {

    /// How hard the glow pixels are driven.
    public enum Mode {
        /// One translucent emissive pass at the texel's own brightness, fullbright in darkness.
        GLOW,
        /// Fill + additive burn, ported from Armory's RadiantGlowLayer/ArmoryGlowLayers (where
        /// the config gating stays; this mode is the mechanism only). See [#gain].
        RADIANT
    }

    /// How hard the [Mode#RADIANT] burn is driven (vanilla only); live-read every frame.
    /// Above one it buys brightness as coverage - mid tones climb into the framebuffer clamp;
    /// too far and the mask flattens white. One or below turns the burn pass off entirely.
    /// Armory ships 2.5.
    public static float gain = 2.5F;

    private final Mode mode;
    private final @Nullable Identifier maskTexture; // null = derive from the renderer's base texture

    /// mask id → id of the baked composite texture (empty = missing/broken, logged once).
    /// Render thread only, like all layer code; reset when the reload generation moves on.
    private static final Map<Identifier, Optional<Identifier>> BAKED = new HashMap<>();
    private static int bakedCacheGeneration = -1;

    public EmissiveLayer() {
        this(Mode.GLOW, null);
    }

    public EmissiveLayer(Mode mode) {
        this(mode, null);
    }

    public EmissiveLayer(Identifier maskTexture) {
        this(Mode.GLOW, maskTexture);
    }

    public EmissiveLayer(Mode mode, @Nullable Identifier maskTexture) {
        this.mode = mode;
        this.maskTexture = maskTexture;
    }

    /// Under a shader pack ([ShaderCompat#isShaderPackInUse]) the gain is forced to 1: Iris
    /// folds shader color into `gl_Color` at the *start* of the pack's program, and a >1 texel
    /// breaks every color test the pack runs (Lightbringer's yellow famously came back green).
    /// At gain 1 the burn is a plain additive duplicate of the fill - which is all the
    /// brightness a pack gets from it, and enough for its bloom to find.
    public static float effectiveGain() {
        return ShaderCompat.isShaderPackInUse() ? 1F : gain;
    }

    @Override
    public void render(ArmorRenderContext context) {
        var glowTexture = emissiveTexture(context);
        if (glowTexture == null) {
            return;
        }
        draw(context, mainRenderLayer(glowTexture));
        if (mode == Mode.RADIANT && gain > 1F) {
            // gain <= 1F is burn-off everywhere; under a pack, effectiveGain()'s 1 still
            // draws the additive duplicate
            draw(context, ArmorRenderLayers.radiantBurn(glowTexture));
        }
    }

    /// Both layers carry armor view-offset layering - see [ArmorRenderLayers#emissive] for why
    /// a plain `entityTranslucentEmissive` fails the depth test over an armor base pass.
    private RenderLayer mainRenderLayer(Identifier glowTexture) {
        return switch (mode) {
            case GLOW -> ArmorRenderLayers.emissive(glowTexture);
            case RADIANT -> ArmorRenderLayers.radiantFill(glowTexture);
        };
    }

    private static void draw(ArmorRenderContext context, RenderLayer layer) {
        context.model().render(
                context.matrices(),
                context.vertexConsumers().getBuffer(layer),
                LightmapTextureManager.MAX_LIGHT_COORDINATE,
                OverlayTexture.DEFAULT_UV);
    }

    /// The renderable glow texture (already composited), or null to skip the pass.
    protected @Nullable Identifier emissiveTexture(ArmorRenderContext context) {
        var baseTexture = context.renderer().config().texture();
        var maskId = maskTexture != null ? maskTexture : glowmaskOf(baseTexture);
        return bakedEmissiveTexture(baseTexture, maskId);
    }

    /// `.../x.png` → `.../x_glowmask.png`, the convention shared with AzureLib-era assets.
    public static Identifier glowmaskOf(Identifier baseTexture) {
        var path = baseTexture.getPath();
        return path.endsWith(".png")
                ? baseTexture.withPath(path.substring(0, path.length() - 4) + "_glowmask.png")
                : baseTexture.withSuffixedPath("_glowmask");
    }

    /// Returns the id of the composite texture for a base+mask pair, baking and registering it
    /// on first use. Null when the mask is missing or unusable. Render thread only.
    public static @Nullable Identifier bakedEmissiveTexture(Identifier baseTexture, Identifier maskId) {
        int currentGeneration = GeoModelCache.generation();
        if (bakedCacheGeneration != currentGeneration) {
            BAKED.clear(); // stale entries are re-baked lazily; registerTexture replaces old ids
            bakedCacheGeneration = currentGeneration;
        }
        return BAKED.computeIfAbsent(maskId, id -> bake(baseTexture, id)).orElse(null);
    }

    private static Optional<Identifier> bake(Identifier baseTexture, Identifier maskId) {
        var resourceManager = MinecraftClient.getInstance().getResourceManager();
        var maskResource = resourceManager.getResource(maskId);
        if (maskResource.isEmpty()) {
            ArmorModelApi.LOGGER.warn("Emissive mask texture '{}' not found; skipping the glow pass", maskId);
            return Optional.empty();
        }
        var baseResource = resourceManager.getResource(baseTexture);
        if (baseResource.isEmpty()) {
            ArmorModelApi.LOGGER.warn("Base texture '{}' not found for emissive mask '{}'; skipping the glow pass",
                    baseTexture, maskId);
            return Optional.empty();
        }
        try (InputStream maskStream = maskResource.get().getInputStream();
             InputStream baseStream = baseResource.get().getInputStream();
             NativeImage mask = NativeImage.read(maskStream);
             NativeImage base = NativeImage.read(baseStream)) {

            if (mask.getWidth() != base.getWidth() || mask.getHeight() != base.getHeight()) {
                ArmorModelApi.LOGGER.error(
                        "Emissive mask '{}' is {}x{} but base texture '{}' is {}x{}; skipping the glow pass",
                        maskId, mask.getWidth(), mask.getHeight(),
                        baseTexture, base.getWidth(), base.getHeight());
                return Optional.empty();
            }

            var composite = new NativeImage(mask.getWidth(), mask.getHeight(), true);
            for (int y = 0; y < mask.getHeight(); y++) {
                for (int x = 0; x < mask.getWidth(); x++) {
                    int maskColor = mask.getColor(x, y); // ABGR packed
                    int alpha = maskColor >>> 24;
                    composite.setColor(x, y, alpha == 0
                            ? 0
                            : (maskColor & 0xFF000000) | (base.getColor(x, y) & 0x00FFFFFF));
                }
            }

            var compositeId = Identifier.of(ArmorModelApi.MOD_ID,
                    "emissive/" + maskId.getNamespace() + "/" + maskId.getPath());
            // The NativeImageBackedTexture takes ownership of the composite image; registering
            // under an existing id replaces (and closes) a previously baked texture.
            MinecraftClient.getInstance().getTextureManager()
                    .registerTexture(compositeId, new NativeImageBackedTexture(composite));
            return Optional.of(compositeId);
        } catch (Exception e) {
            ArmorModelApi.LOGGER.error("Failed to bake emissive texture for mask '{}'; skipping the glow pass", maskId, e);
            return Optional.empty();
        }
    }
}
