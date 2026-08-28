# Armor Model API

Renders armor with **custom geometry** authored in the Bedrock/GeckoLib `.geo.json` format, through the **vanilla armor rendering pipeline**.

## Capabilities

- **Custom armor geometry** per armor set: robes, pauldrons, hats, skirts — any cuboid-based Blockbench model
- **Per-slot rendering** with the standard armor bone convention, including separate **boot bones** and a chest-anchored **waist bone** for leg pieces
- **Armor trims** with per-set trim textures (vanilla smithing-table trims, your art)
- **Emissive glowmasks** — the hand-authored `_glowmask.png` convention; pixels that stay bright in the dark
- **Radiant glow** — a high-luminance variant that burns toward white and feeds shader-pack bloom
- **Custom render layers** — a small interface for adding your own passes (extra overlays, effects)
- Everything vanilla armor does comes for free: pose and animation following, sneaking/swimming, armor stands, baby mobs, dyed leather color, enchantment glint, the glowing outline effect

### How it works

- **Loading** — on resource load (and every F3+T reload), each registered `.geo.json` is parsed and baked **once** into a vanilla `ModelPart` tree (the same `TexturedModelData` every vanilla entity model is built from), cached until the next reload. Textures are plain resources, resolved by the render layers on demand — nothing is preprocessed.
- **Hooking in** — when an armor piece is about to render, the library steps in per registered item: on Fabric through Fabric API's `ArmorRenderer` hook, on NeoForge through a small equivalent mixin. Vanilla's overlay rendering is skipped for that piece and the dispatcher renders the baked model instead — vanilla pose copied on, slot visibility applied. Unregistered items are untouched.
- **Pass order** — each piece draws as a stack of passes over the same model: **base texture → your render layers** (glow, then trim so it lands on top), with enchantment glint riding the base pass. Renderers built through the fluent API keep the layers sorted into that stack automatically; a renderer constructed with an explicit layer list draws them in list order (see [Render layers](#render-layers)). Every extra pass is just the model rendered again with a different render layer and buffer.

From bake to buffer this is 100% the vanilla code path — the same pose copy, render layers, and buffers vanilla armor uses. That is the whole compatibility and performance story: Sodium, Iris, and anything else that works with vanilla armor sees ordinary vanilla model rendering. There is no custom vertex pipeline.

---

## Installation

The library is published on Modrinth; resolve it from the Modrinth maven.

```gradle
repositories {
    maven {
        name = "Modrinth"
        url = "https://api.modrinth.com/maven"
        content { includeGroup "maven.modrinth" }
    }
}
```

Versions are published per loader, named `<version>+<minecraft>-<loader>`. In an Architectury (common/fabric/neoforge) workspace:

```gradle
// common/build.gradle and fabric/build.gradle
dependencies {
    modImplementation("maven.modrinth:armor-model-api:${project.armor_model_api_version}-fabric")
}

// neoforge/build.gradle
dependencies {
    modImplementation("maven.modrinth:armor-model-api:${project.armor_model_api_version}-neoforge")
}
```

```properties
# gradle.properties
armor_model_api_version = 1.0.2+26.1.2
```

The common module compiles against the fabric artifact — the standard pattern for consuming multi-loader libraries in Architectury workspaces.

Declare the runtime dependency in your mod metadata:

```json
// fabric.mod.json
"depends": { "armor_model_api": "*" }
```

```toml
# neoforge.mods.toml
[[dependencies.<your_mod_id>]]
modId = "armor_model_api"
type = "required"
versionRange = "[1.0,)"
```

**Loader notes.** On Fabric the library uses Fabric API (`fabric-rendering-v1`, `fabric-resource-loader-v0`) — any mod already depending on `fabric-api` is covered. On NeoForge nothing extra is needed. For local snapshot builds, `./gradlew publishToMavenLocal` in this repo and swap the Modrinth coordinates for `net.rpg_foundation:armor_model_api-<loader>:<version>` from `mavenLocal()`.

| | |
|---|---|
| Minecraft | 26.1.x |
| Fabric Loader | ≥ 0.19.3, with Fabric API |
| NeoForge | ≥ 26.1 |
| Java | 25 |

---

## Quick start

### 1. Add the assets

Two files make an armor set renderable:

| Asset | Path |
|---|---|
| Geometry | `assets/<your_mod>/geo/<set_name>.geo.json` |
| Texture | `assets/<your_mod>/textures/armor/<set_name>.png` |

The geometry is a Bedrock-format model exported from Blockbench, with bones named by the armor convention (see [Authoring armor models](#authoring-armor-models)). The texture is the model's own texture at the size declared in the geo file's `description`.

Optional, added later as you need them: a `<set_name>_glowmask.png` next to the texture for glow, and a trim texture + atlas entry for smithing trims.

### 2. Register the renderer

In your **client** initializer:

```java
import net.rpg_foundation.armor_api.client.ArmorRenderers;
import net.rpg_foundation.armor_api.client.GeoArmorRenderer;

ArmorRenderers.register(
    GeoArmorRenderer.of(
        Identifier.of(MOD_ID, "geo/crimson_plate.geo.json"),
        Identifier.of(MOD_ID, "textures/armor/crimson_plate.png")),
    MyItems.CRIMSON_HELMET, MyItems.CRIMSON_CHESTPLATE,
    MyItems.CRIMSON_LEGGINGS, MyItems.CRIMSON_BOOTS);
```

One renderer instance serves the whole set; registration is safe from any mod-init thread on either loader.

### 3. Result

Wearing any registered piece renders your geometry in place of the vanilla overlay, for exactly the equipped slots. If the geo file is missing or broken, the error is logged once and the item **falls back to vanilla armor rendering** (on NeoForge; on Fabric the piece renders nothing) — a broken resource pack degrades, it doesn't crash.

---

## Authoring armor models

### Bone name convention

The model is anchored to the vanilla biped skeleton through bone names:

| Bone | Posed by | Rendered with |
|---|---|---|
| `armorHead` | head | helmet |
| `armorBody` | body | chestplate |
| `armorRightArm` / `armorLeftArm` | arms | chestplate |
| `armorRightLeg` / `armorLeftLeg` | legs | leggings |
| `armorRightBoot` / `armorLeftBoot` | legs | boots |
| `armorWaist` | **body** | **leggings** |

- The usual Blockbench armor template wraps these in empty `bipedHead`, `bipedBody`, … parent bones. Both styles work: the wrappers may be declared in the file or referenced as implicit parents (a bone whose declared parent doesn't exist in the file is anchored by its own — or its parent's — conventional name).
- **Decorative bones** (hats, pauldrons, wings, …) are free-form: name them anything and nest them under a conventional bone; they inherit its slot visibility and pose.
- Bone pivots should follow the vanilla skeleton: head/body at `(0, 24, 0)`, arms at `(±5, 22, 0)`, legs at `(±1.9, 12, 0)` or `(±2, 12, 0)`.

### The waist bone

`armorWaist` is for geometry that ships with the **leggings** but hangs off the **chest** — skirts, tassets, belts. It follows torso pose (sneaking, swimming) rather than a leg's swing, and shows only while leggings are equipped. Author it with pivot `(0, 24, 0)`, optionally under a `bipedWaist` wrapper.

### Supported geometry

- **Box UV** cuboids (the Blockbench default for armor)
- Per-cube **rotation** and per-cube **mirror**
- **Inflate**, per cube
- **Fractional cube sizes** (e.g. `4.5 × 12.9 × 4.9`) — UV spans floor to whole texels, exactly like the Bedrock engine, while geometry keeps the fractional size
- **Zero-thickness panels** (a size of `0` on one axis) — automatically separated by a hair to prevent the panel's front and back faces from z-fighting; both sides keep their own texture regions
- Any `texture_width`/`texture_height`

### Unsupported — fails loudly

These throw a descriptive error at load (naming the file and bone) rather than rendering garbage:

- Per-face UV (the `"uv": { "north": ... }` object form) — use box UV
- `poly_mesh` geometry
- Bone-level `mirror` or `inflate` — set them per cube
- Fractional box-UV **origins** (fractional sizes are fine)

A model that fails to load logs once and the set falls back as described in Quick start.

---

## Render layers

Layers are extra passes drawn **after** the base texture pass. There are two ways to add them, with different ordering contracts:

**Fluent API (recommended) — smart ordering.** `GeoArmorRenderer.of(...)` plus the pass methods `.glow()` / `.radiant()` / `.trim(...)` / `.layer(...)`. The chain keeps the passes sorted into the visually-correct stack — glow under trim under custom overlays — no matter what order you chain the calls in:

```java
GeoArmorRenderer.of(modelId, textureId)
    .radiant()                                                          // emissive glow
    .trim(Identifier.of(MOD_ID, "armor/trim/crimson_generic"), false);  // trim, sorted on top
```

Sorting follows `ArmorRenderLayer.preferredOrder()`: `ORDER_EMISSIVE` (100) → `ORDER_TRIM` (200) → `ORDER_OVERLAY` (300, the default for custom layers). The sort is stable — layers sharing an order keep the order they were added. Each pass method returns a **new** renderer, so finish the chain before registering it.

**Explicit layer list — full manual control.** The plain constructor takes the layers exactly as listed and draws them **in list order**, no sorting — use it when you need a stack the sorted order can't express:

```java
new GeoArmorRenderer(modelId, textureId, List.of(
    new EmissiveLayer(Mode.RADIANT),                               // glow first,
    new TrimLayer(Identifier.of(MOD_ID, "armor/trim/crimson_generic"), false)))  // trim on top
```

### TrimLayer — smithing trims with your art

Renders the vanilla trim component using **per-set trim textures** from the armor-trims atlas.

```java
new TrimLayer(baseTexture)          // sprite per pattern+material: <base>_<pattern>_<material>
new TrimLayer(baseTexture, false)   // sprite per material only:    <base>_<material>
new TrimLayer(trim -> customId)     // fully custom naming (no fallback — unresolved trims skip)
new TrimLayer(trim -> customId, fallbackId)  // custom naming + explicit fallback sprite
```

The sprites come from vanilla's `paletted_permutations` atlas source — one greyscale trim texture per set, recolored per trim material automatically. Add the texture at `assets/<mod>/textures/armor/trim/<set>_generic.png` and an atlas entry (the `single` source stitches the greyscale itself, for the fallback described below):

```json
// assets/minecraft/atlases/armor_trims.json
{
  "replace": false,
  "sources": [{
    "type": "single",
    "resource": "<mod>:armor/trim/<set>_generic"
  }, {
    "type": "paletted_permutations",
    "textures": ["<mod>:armor/trim/<set>_generic"],
    "palette_key": "trims/color_palettes/trim_palette",
    "permutations": {
      "quartz": "trims/color_palettes/quartz",
      "iron": "trims/color_palettes/iron",
      "gold": "trims/color_palettes/gold",
      "diamond": "trims/color_palettes/diamond",
      "netherite": "trims/color_palettes/netherite",
      "redstone": "trims/color_palettes/redstone",
      "copper": "trims/color_palettes/copper",
      "emerald": "trims/color_palettes/emerald",
      "lapis": "trims/color_palettes/lapis",
      "amethyst": "trims/color_palettes/amethyst"
    }
  }]
}
```

**Third-party trim materials — greyscale fallback.** Mods can register new trim materials (with their own color palettes); a fixed `paletted_permutations` source generates no variant for materials it doesn't list, so those trims would resolve to the missing sprite. When that happens, `TrimLayer` falls back to the set's **greyscale base texture** — an uncolored trim instead of the magenta checker. The fallback needs the greyscale stitched into the atlas, which is what the `single` source above does. If the fallback sprite is missing too, the trim pass skips itself; both cases log once per resource reload. The base-texture constructors wire the fallback automatically; the custom-function constructor takes it as an optional second argument.

### EmissiveLayer — glowmask

Pixels that stay fully bright in darkness. The mask is a **stencil**: `<baseTexture>_glowmask.png`, same size as the base texture, transparent everywhere except the pixels that should glow — only the mask's **alpha** matters; the glow **colors are taken from the base texture** underneath. (This is the established AzureLib-era convention; existing masks work unchanged.)

```java
new EmissiveLayer()                        // derives <base>_glowmask.png automatically (Mode.GLOW)
new EmissiveLayer(explicitId)              // explicit mask texture
new EmissiveLayer(Mode.RADIANT)            // radiant mode, see below
new EmissiveLayer(Mode.RADIANT, explicitId)
```

If the mask file doesn't exist, the pass skips itself (logged once) — safe to add across a whole family of sets.

### Mode.RADIANT — glow that burns

The emissive pass plus an **additive burn** that drives the glow pixels past their own brightness — toward white under vanilla, and into a shader pack's bloom threshold under packs. Same mask convention as the default `Mode.GLOW`.

```java
new EmissiveLayer(EmissiveLayer.Mode.RADIANT)
```

- `EmissiveLayer.gain` (static, default `2.5`) controls how hard the burn is driven; `1` or below disables the burn pass. Live-tunable.
- Under an active shader pack the gain is automatically clamped to a safe additive doubling (detected via Iris) — driving shader color past one inside a pack's programs corrupts its color math.

### Writing a custom layer

```java
public class EnchantOverlayLayer implements ArmorRenderLayer {
    @Override
    public void render(ArmorRenderContext ctx) {
        ctx.model().render(
            ctx.matrices(),
            ctx.vertexConsumers().getBuffer(RenderLayer.getArmorEntityGlint()),
            ctx.light(), OverlayTexture.DEFAULT_UV);
    }
}
```

A pass is just: pick a `RenderLayer`, get a buffer, call `model.render` again. The context carries the posed model (slot visibility already applied), the buffers, stack, entity, slot, and light. `ctx.model().armorBone("armorHead")` gives you a single conventional bone for per-bone passes.

Added via the fluent `.layer(...)`, a custom pass sorts at `ORDER_OVERLAY` — on top of the built-in glow and trim passes. Override `preferredOrder()` to sit elsewhere in the sorted stack (e.g. below `ORDER_EMISSIVE` for an underlay), or use the explicit-list constructor to place it by hand (the list ignores `preferredOrder()` entirely).

Two rules for any **custom `RenderLayer`** you build for armor (both learned the hard way — vanilla's own armor layers already follow them):

1. **Match the base pass's depth nudge**: include `VIEW_OFFSET_Z_LAYERING`. Every vanilla armor layer carries it; a pass without it computes depth *behind* the base pass and fails the depth test — invisible in the world, flickering in the inventory doll.
2. **Prefer a translucent-classified transparency phase** (e.g. `TRANSLUCENT_TRANSPARENCY`). Under Iris, draws are bucketed by transparency class and opaque draws first; an opaque-classified overlay can end up drawn *before* — and covered by — a base pass that another mod (e.g. Shoulder Surfing) has made translucent. Same bucket = request order preserved everywhere.

`ArmorRenderLayers.emissive(texture)` in this library is a ready-made reference implementation of both rules.

---

## Compatibility notes

- **Sodium / Iris** — supported. Rendering is vanilla `ModelPart` geometry on vanilla-style render layers, which is precisely what these mods optimize and translate. The radiant mode detects an active shader pack (via the Iris API, soft dependency) and adjusts its gain automatically.
- **Shoulder Surfing** — supported, including its player-transparency fade: the fade rides the vanilla color path, which this library's passes inherit. Its render-type modification (armor layers made translucent while the feature is on) is exactly why custom-layer rule 2 above exists.
- **Other rendering mods** — the general contract: this library draws the base armor texture and then **overdraws** it with its layer passes (glow is layered *on top of* the base, the base texture is never modified). Mods that re-render, tint, or fade armor through the vanilla pipeline compose naturally; mods that reorder draws by render-state classification are handled by the layer rules above.

---

## Project meta

**License**: MIT. The armor bone-name convention and the reference math for anchoring Bedrock geometry to the vanilla skeleton derive from [AzureLib Armor](https://github.com/AzureDoom/AzureLib-Armor) (itself a GeckoLib fork), both MIT — see `NOTICE`. No source code from either project is included.

**Example mod / dev smoke test**: the `example/` modules are a separate, never-published mod (`armor_model_api_example`) consuming the API exactly like a real content mod — all test assets and registrations live there, keeping the API jar clean. `./gradlew :example-fabric:runClient` (or `:example-neoforge:runClient`) launches the game with the API loaded as a dependency mod and test renderers registered on vanilla armor — iron: a plain example set; diamond: emissive glowmask; netherite: radiant glow; gold: waist-bone set. Wear a piece and compare against the source model in Blockbench. `example/common/src/main/java/.../ExampleArmor.java` is the reference for consumer-side registration.

