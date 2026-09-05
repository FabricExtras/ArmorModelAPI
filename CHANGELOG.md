# 1.0.0.001

Port of 1.0.0 to Minecraft 1.20.1 (Fabric + Forge 47). Same feature set; no functional changes.

- Forge replaces NeoForge: the armor hook is a mixin on `ArmorFeatureRenderer#renderArmor`
- Dye color read through `DyeableItem`, trims through the stack's `Trim` NBT
- Shader awareness gates on Iris (Fabric) or Oculus (Forge)

# 1.0.0

Initial release.

- Renders Bedrock/GeckoLib geo armor models through the vanilla armor pipeline
- Armor bone convention with boot bones and a chest-anchored waist bone
- Armor trims with per-set trim textures, with greyscale fallback for third-party trim materials
- Emissive glowmasks (AzureLib `_glowmask` convention) and a radiant glow mode that feeds shader-pack bloom
