# 1.1.1

- Geo armor is now posed from the entity's own body model: armor on armor stands no longer sways
  with the idle animation, and baby mobs get properly fitted armor (no trims on babies, like vanilla)

# 1.1.0

- Minecraft 26.2 support
- Per-item model and texture overrides through the vanilla `minecraft:custom_data` component
  (`{armor_model_api:{model, texture, glowmask, trim}}`), settable by commands, loot tables,
  recipes and datapacks - no custom component, the library stays client-side
- A stack naming both `model` and `texture` takes over an item nobody registered (any vanilla or
  third-party armor piece), with a default glow + trim pass stack
- `ArmorRenderContext` carries the base texture in effect (`texture()`) and the stack's `overrides()`;
  layers deriving assets from the base texture should read it from there

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
