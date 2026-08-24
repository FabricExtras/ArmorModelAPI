package net.rpg_foundation.armor_api.client;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.rpg_foundation.armor_api.client.model.GeoArmorModel;
import org.jetbrains.annotations.Nullable;

/// Everything a render layer needs for its passes.
///
/// Since 1.21.9 entity rendering is queue-based: nothing draws inside the feature-render pass,
/// passes are *submitted* to an [OrderedRenderCommandQueue] and drawn later (the model is posed
/// from the [#state] at draw time via `setAngles`). A layer that wants another pass calls
/// [#submit] with its render layer; passes are assigned ascending batching-queue orders in
/// submission order, so the visual stack (base, glow, trim, ...) holds regardless of how the
/// backend batches them. The [#model] already carries the slot visibility.
public final class ArmorRenderContext {
    private final MatrixStack matrices;
    private final OrderedRenderCommandQueue queue;
    private final ItemStack stack;
    private final BipedEntityRenderState state;
    private final EquipmentSlot slot;
    private final int light;
    private final GeoArmorRenderer renderer;
    private final GeoArmorModel model;
    private int nextOrder;

    public ArmorRenderContext(
            MatrixStack matrices,
            OrderedRenderCommandQueue queue,
            ItemStack stack,
            BipedEntityRenderState state,
            EquipmentSlot slot,
            int light,
            GeoArmorRenderer renderer,
            GeoArmorModel model
    ) {
        this.matrices = matrices;
        this.queue = queue;
        this.stack = stack;
        this.state = state;
        this.slot = slot;
        this.light = light;
        this.renderer = renderer;
        this.model = model;
    }

    public MatrixStack matrices() { return matrices; }
    public OrderedRenderCommandQueue queue() { return queue; }
    public ItemStack stack() { return stack; }
    public BipedEntityRenderState state() { return state; }
    public EquipmentSlot slot() { return slot; }
    public int light() { return light; }
    public GeoArmorRenderer renderer() { return renderer; }
    public GeoArmorModel model() { return model; }

    /// Submits one full re-render of the model on the given render layer, after every pass
    /// submitted so far.
    ///
    /// @param color  ARGB tint, `-1` for none
    /// @param sprite atlas sprite to remap the model's UVs onto, or null to sample the layer's texture
    public void submit(RenderLayer layer, int light, int color, @Nullable Sprite sprite) {
        queue.getBatchingQueue(nextOrder++)
                .submitModel(model, state, matrices, layer, light, OverlayTexture.DEFAULT_UV, color, sprite, state.outlineColor, null);
    }
}
