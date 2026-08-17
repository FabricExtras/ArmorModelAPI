package net.rpg_foundation.armor_api.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/// An extra render pass over an armor piece (trim, glow, enchant effect, ...), run after the
/// base pass in the order layers were listed in the renderer config.
///
/// Implementations are stateless config objects; per-frame state lives in the context.
@Environment(EnvType.CLIENT)
@FunctionalInterface
public interface ArmorRenderLayer {

    void render(ArmorRenderContext context);
}
