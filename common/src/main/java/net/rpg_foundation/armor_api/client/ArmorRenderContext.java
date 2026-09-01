package net.rpg_foundation.armor_api.client;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.rpg_foundation.armor_api.client.model.GeoArmorBones;
import org.jetbrains.annotations.Nullable;

/// Everything a render layer needs for its passes.
///
/// Since 1.21.9 entity rendering is queue-based: nothing draws inside the feature-render pass,
/// passes are *submitted* to an [OrderedRenderCommandQueue] and drawn later (the model is posed
/// from the [#state] at draw time via `setAngles`). A layer that wants another pass calls
/// [#submit] with its render layer; passes are assigned ascending batching-queue orders in
/// submission order, so the visual stack (base, glow, trim, ...) holds regardless of how the
/// backend batches them. The [#model] already carries the slot visibility.
///
/// The context already reflects the stack's [ArmorOverrides]: [#model] is the model in effect
/// (override or the renderer's own) and [#texture] the base texture in effect. Layers that
/// derive assets from the base texture must use [#texture], not the renderer config, so
/// per-stack reskins carry through; asset-specific overrides (glowmask, trim) are on [#overrides].
public final class ArmorRenderContext {
    private final MatrixStack matrices;
    private final OrderedRenderCommandQueue queue;
    private final ItemStack stack;
    private final BipedEntityRenderState state;
    private final EquipmentSlot slot;
    private final int light;
    private final GeoArmorRenderer renderer;
    private final GeoArmorBones model;
    private final Identifier texture;
    private final ArmorOverrides overrides;
    private int nextOrder;

    public ArmorRenderContext(
            MatrixStack matrices,
            OrderedRenderCommandQueue queue,
            ItemStack stack,
            BipedEntityRenderState state,
            EquipmentSlot slot,
            int light,
            GeoArmorRenderer renderer,
            GeoArmorBones model,
            Identifier texture,
            ArmorOverrides overrides
    ) {
        this.matrices = matrices;
        this.queue = queue;
        this.stack = stack;
        this.state = state;
        this.slot = slot;
        this.light = light;
        this.renderer = renderer;
        this.model = model;
        this.texture = texture;
        this.overrides = overrides;
    }

    public MatrixStack matrices() { return matrices; }
    public OrderedRenderCommandQueue queue() { return queue; }
    public ItemStack stack() { return stack; }
    public BipedEntityRenderState state() { return state; }
    public EquipmentSlot slot() { return slot; }
    public int light() { return light; }
    public GeoArmorRenderer renderer() { return renderer; }
    /// The armor model ([GeoArmorModel] for bipeds, [GeoPlayerArmorModel] for players); also a `BipedEntityModel`
    public GeoArmorBones model() { return model; }
    /// The base texture in effect for this piece (stack override, else the renderer's)
    public Identifier texture() { return texture; }
    /// The stack's overrides, [ArmorOverrides#NONE] when it has none
    public ArmorOverrides overrides() { return overrides; }

    /// Submits one full re-render of the model on the given render layer, after every pass
    /// submitted so far.
    ///
    /// @param color  ARGB tint, `-1` for none
    /// @param sprite atlas sprite to remap the model's UVs onto, or null to sample the layer's texture
    @SuppressWarnings("unchecked")
    public void submit(RenderLayer layer, int light, int color, @Nullable Sprite sprite) {
        queue.getBatchingQueue(nextOrder++)
                .submitModel((net.minecraft.client.render.entity.model.EntityModel<BipedEntityRenderState>) (net.minecraft.client.model.Model<?>) model, state, matrices, layer, light, OverlayTexture.DEFAULT_UV, color, sprite, state.outlineColor, null);
    }
}
