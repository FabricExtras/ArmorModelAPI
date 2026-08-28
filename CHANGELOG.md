# 1.0.2

- Minecraft 26.1.2 support (Java 25).
- Undyed armor is no longer leather-tinted: only an explicit `dyed_color` component tints a geo model
- The access widener now ships in the production jars
- Radiant glow pipelines are registered with Iris, so radiant armor glows under shader packs again
- Player armor uses a `PlayerEntityModel`-based geo model, so player animation libraries (PAL) pose it
- Fix `GeoArmorModel` construction (missing biped parts)

# 1.0.0

Initial release.

- Renders Bedrock/GeckoLib geo armor models through the vanilla armor pipeline
- Armor bone convention with boot bones and a chest-anchored waist bone
- Armor trims with per-set trim textures, with greyscale fallback for third-party trim materials
- Emissive glowmasks (AzureLib `_glowmask` convention) and a radiant glow mode that feeds shader-pack bloom
