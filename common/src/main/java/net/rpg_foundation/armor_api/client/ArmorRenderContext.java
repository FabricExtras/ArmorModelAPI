package net.rpg_foundation.armor_api.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
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
public final class ArmorRenderContext {
    private final PoseStack matrices;
    private final SubmitNodeCollector queue;
    private final ItemStack stack;
    private final HumanoidRenderState state;
    private final EquipmentSlot slot;
    private final int light;
    private final GeoArmorRenderer renderer;
    private final GeoArmorBones model;
    private int nextOrder;

    public ArmorRenderContext(
            PoseStack matrices,
            SubmitNodeCollector queue,
            ItemStack stack,
            HumanoidRenderState state,
            EquipmentSlot slot,
            int light,
            GeoArmorRenderer renderer,
            GeoArmorBones model
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

    public PoseStack matrices() { return matrices; }
    public SubmitNodeCollector queue() { return queue; }
    public ItemStack stack() { return stack; }
    public HumanoidRenderState state() { return state; }
    public EquipmentSlot slot() { return slot; }
    public int light() { return light; }
    public GeoArmorRenderer renderer() { return renderer; }
    /// The armor model ([GeoArmorModel] for bipeds, [GeoPlayerArmorModel] for players); also a `BipedEntityModel`
    public GeoArmorBones model() { return model; }

    /// Submits one full re-render of the model on the given render layer, after every pass
    /// submitted so far.
    ///
    /// @param color  ARGB tint, `-1` for none
    /// @param sprite atlas sprite to remap the model's UVs onto, or null to sample the layer's texture
    @SuppressWarnings("unchecked")
    public void submit(RenderType layer, int light, int color, @Nullable TextureAtlasSprite sprite) {
        queue.order(nextOrder++)
                .submitModel((net.minecraft.client.model.EntityModel<HumanoidRenderState>) (net.minecraft.client.model.Model<?>) model, state, matrices, layer, light, OverlayTexture.NO_OVERLAY, color, sprite, state.outlineColor, null);
    }
}
