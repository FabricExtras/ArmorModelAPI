# Armor Model API

Renders armor with **custom geometry** authored in the Bedrock/GeckoLib `.geo.json` format, through the **vanilla armor rendering pipeline**.

## Capabilities

- **Custom armor geometry** per armor set: robes, pauldrons, hats, skirts — any cuboid-based Blockbench model
- **Per-slot rendering** with the standard armor bone convention, including separate **boot bones** and a chest-anchored **waist bone** for leg pieces
- **Armor trims** with per-set trim textures (vanilla smithing-table trims, your art)
- **Emissive glowmasks** — the hand-authored `_glowmask.png` convention; pixels that stay bright in the dark
- **Radiant glow** — a high-luminance variant that burns toward white and feeds shader-pack bloom
- **Custom render layers** — a small interface for adding your own passes (extra overlays, effects)
- **Per-item overrides** — a stack can carry its own model, texture, glowmask and trim art in the vanilla `custom_data` component: commands, loot tables, recipes and datapacks reskin registered pieces, or give **any** armor item a geo look, with no code and no custom component
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
armor_model_api_version = <version>+<minecraft>   # e.g. the newest entry on the Modrinth versions page
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
versionRange = "[<version you built against>,)"
```

**Loader notes.** On Fabric the library uses Fabric API (`fabric-rendering-v1`, `fabric-resource-loader-v0`) — any mod already depending on `fabric-api` is covered. On NeoForge nothing extra is needed. For local snapshot builds, `./gradlew publishToMavenLocal` in this repo and swap the Modrinth coordinates for `net.rpg_foundation:armor_model_api-<loader>:<version>` from `mavenLocal()`.

Supported Minecraft, loader and Java versions are per release: read them off the release's version tags on Modrinth / CurseForge (or the mod metadata inside the jar) rather than from this document. The library follows the game version in its `+<minecraft>` suffix; pick the release that matches your workspace.

---

## Quick start

### 1. Add the assets

Two files make an armor set renderable:

| Asset | Path |
|---|---|
| Geometry | `assets/<your_mod>/geo/<set_name>.geo.json` |
| Texture | `assets/<your_mod>/textures/armor/<set_name>.png` |

The geometry is a Bedrock-format model exported from Blockbench, with bones named by the armor convention (see [Authoring armor models](#authoring-armor-models)). The texture is the model's own texture at the size declared in the geo file's `description`.

Optional, added later as you need them: a `<set_name>_glowmask.png` next to the texture for glow, and a greyscale trim texture (with its palette metadata) for smithing trims.

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

## Item component overrides

Any armor stack can carry its own assets in the vanilla **`minecraft:custom_data`** component, under an `armor_model_api` compound. Because `custom_data` is an ordinary vanilla component, the library stays client-side — nothing is registered, and nothing needs to be installed on the server. The data can be set by anything that writes item components: `/give`, a command block, a loot table (`minecraft:set_custom_data` / `set_components`), a recipe result's `components`, or a component patch applied by another mod.

```
/give @p <item>[minecraft:custom_data={armor_model_api:{
    model:    "<mod>:geo/<set>.geo.json",              // geo model
    texture:  "<mod>:textures/armor/<set>.png",        // base texture
    glowmask: "<mod>:textures/armor/<set>_glowmask.png", // emissive mask (optional)
    trim:     "<mod>:armor/trim/<set>_generic"         // trim texture base (optional)
}}]
```

All four keys are optional; what they do depends on whether the item already has a renderer.

### Reskinning a registered piece

On an item registered through `ArmorRenderers.register`, the component decides *which* assets that renderer draws. Anything not given comes from the renderer.

| Key | Replaces | Notes |
|---|---|---|
| `model` | the renderer's geo model | loaded and cached like any registered model; if it is missing or broken the piece logs once and **falls back to the renderer's own model** |
| `texture` | the base texture | also becomes the base for the derived `_glowmask` name and for the emissive composite; a missing file shows the missing texture, like vanilla |
| `glowmask` | the emissive mask | wins over both a constructor-given mask and the derived name |
| `trim` | the trim texture base | same `<base>` / `<base>_<pattern>` naming as the layer was built with (recolored per material at runtime), and it is the fallback when the pattern permutation is missing; layers built from a custom permutation function ignore it |

The renderer's pass stack (glow mode, trim naming, custom layers) is unchanged — only the assets those passes read are swapped.

### Taking over any armor item

A stack that names **both `model` and `texture`** renders through the library even when nobody registered the item: a vanilla helmet, another mod's chestplate, anything the game treats as armor. This is what lets a datapack hand out geo-looking gear without a line of code:

```
/give @p minecraft:turtle_helmet[minecraft:custom_data={armor_model_api:{model:"armory_rpgs:geo/lightbringer_armor.geo.json",texture:"armory_rpgs:textures/armor/lightbringer_armor.png"}}]
```

Taken-over pieces draw with a **default pass stack**: a plain emissive glow from the texture's `_glowmask` sibling (or the `glowmask` key), skipped when there is no mask; and a trim pass that draws only when the `trim` key is set. Radiant glow and custom layers are renderer configuration and are not available this way. There is no renderer to fall back to, so a missing or broken `model` leaves the item to **vanilla rendering** (logged once). Registered items are never affected by this path.

### Where the assets come from

The identifiers are resolved by the **client's** resource manager, exactly like a registered set's assets: the geo file, textures and trim textures must exist in a mod or resource pack loaded on the client that renders the piece. A datapack can carry the data, but not the art — ship the assets in a resource pack or a mod. Referencing another mod's assets (as in the example above) works only where that mod is installed. When a model cannot be found the log says so:

```
Geo model '<id>' not found; its armor will not render
```

For a registered piece that means the renderer's own model is drawn instead; for a takeover it means the item rendered as vanilla. A missing glowmask or trim texture is likewise logged once and that pass is skipped.

### Notes for layer authors

Custom layers get the resolved values from the context: `ctx.texture()` is the base texture in effect and `ctx.overrides()` the raw `ArmorOverrides` record (`model`, `texture`, `glowmask`, `trim`, each nullable; `takesOver()` tells the two modes apart). A layer that derives an asset name from the base texture should start from `ctx.texture()`, not from the renderer config, so per-stack reskins carry through. `TrimLayer.fromOverrides(...)` builds a trim pass with no base of its own that draws only for stacks carrying a `trim` key — the takeover stack uses it.

The component is parsed once per `custom_data` instance (identity-cached; the instance is replaced when the data changes) and the models and composited glow textures are cached like a registered set's, so the per-frame cost is a couple of map lookups per piece.

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

Renders the vanilla trim component using **per-set greyscale trim textures**, recolored with the trim material's palette by vanilla's paletted-texture manager (the same mechanism vanilla's own trims use since 26.3).

```java
new TrimLayer(baseTexture)          // texture per pattern: <base>_<pattern>
new TrimLayer(baseTexture, false)   // one texture for every pattern: <base>
new TrimLayer(trim -> customId)     // fully custom naming (no fallback — unresolved trims skip)
new TrimLayer(trim -> customId, fallbackId)  // custom naming + explicit fallback texture
```

The material is not part of the name: one greyscale texture per set is enough, and every trim material — vanilla or third-party — recolors it through its own `palette_id`. Add the texture at `assets/<mod>/textures/armor/trim/<set>_generic.png`, drawn in the eight greys of vanilla's trim key palette (`#e0e0e0`, `#c0c0c0`, `#a0a0a0`, `#808080`, `#606060`, `#404040`, `#202020`, `#000000` — unchanged from the pre-26.3 `trim_palette` key, so existing art carries over), plus a metadata file next to it naming that key palette:

```json
// assets/<mod>/textures/armor/trim/<set>_generic.png.mcmeta
{
  "palette": {
    "base_palette": "minecraft:trim_base"
  }
}
```

Without the metadata the texture is drawn uncolored. The old `assets/minecraft/atlases/armor_trims.json` entry is obsolete (that atlas no longer exists) and can be deleted.

**Missing textures.** A texture that doesn't exist skips the trim pass instead of drawing the magenta checker, logged once per resource reload. The base-texture constructors first fall back from `<base>_<pattern>` to the plain `<base>`; the custom-function constructor takes an optional fallback texture as its second argument.

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

A pass is just: pick a `RenderLayer`, get a buffer, call `model.render` again. The context carries the posed model (slot visibility already applied), the buffers, stack, entity, slot, light, the base texture in effect (`texture()`) and the stack's overrides. `ctx.model().armorBone("armorHead")` gives you a single conventional bone for per-bone passes.

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

