package net.rpg_foundation.armor_api.client;


/// An extra render pass over an armor piece (trim, glow, enchant effect, ...), run after the
/// base pass.
///
/// Implementations are stateless config objects; per-frame state lives in the context.
///
/// Renderers built fluently ([GeoArmorRenderer#of] and its pass methods) keep their passes
/// sorted in ascending [#preferredOrder] (ties keep the order they were added), so the
/// visually-correct stack - base, then glow, then trim on top - holds no matter what order the
/// calls were chained in. Custom layers default to [#ORDER_OVERLAY] (on top of the built-ins);
/// override [#preferredOrder] to sit elsewhere. Renderers constructed with an explicit layer
/// list are NOT sorted - the list draws exactly as given.
@FunctionalInterface
public interface ArmorRenderLayer {

    /// Emissive glow: drawn first, just over the base texture. See [net.rpg_foundation.armor_api.client.layer.EmissiveLayer].
    int ORDER_EMISSIVE = 100;
    /// Smithing trim: drawn over the glow. See [net.rpg_foundation.armor_api.client.layer.TrimLayer].
    int ORDER_TRIM = 200;
    /// Default for custom layers: drawn on top of the built-in passes.
    int ORDER_OVERLAY = 300;

    void render(ArmorRenderContext context);

    /// Where this layer prefers to sit among a renderer's passes: lower draws first (nearer the
    /// base texture), higher draws on top. A preference, not an absolute - only
    /// [GeoArmorRenderer]'s fluent pass methods sort by this; an explicit layer list passed to
    /// a constructor draws in list order, ignoring it.
    default int preferredOrder() {
        return ORDER_OVERLAY;
    }
}
