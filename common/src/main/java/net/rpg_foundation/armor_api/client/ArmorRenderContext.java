package net.rpg_foundation.armor_api.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.rpg_foundation.armor_api.client.model.GeoArmorBones;
import net.rpg_foundation.armor_api.client.model.PoseCopyingModel;
import org.jetbrains.annotations.Nullable;

/// Everything a render layer needs for its passes.
///
/// Since 1.21.9 entity rendering is queue-based: nothing draws inside the feature-render pass,
/// passes are *submitted* to an [OrderedRenderCommandQueue] and drawn later. What is submitted is
/// a [PoseCopyingModel]: at draw time it copies the pose of the entity's body model (the render
/// layer's parent model) onto [#model], so the armor follows whatever vanilla or an animation
/// library did to that entity. A layer that wants another pass calls
/// [#submit] with its render layer; passes are assigned ascending batching-queue orders in
/// submission order, so the visual stack (base, glow, trim, ...) holds regardless of how the
/// backend batches them. The [#model] already carries the slot visibility.
///
/// The context already reflects the stack's [ArmorOverrides]: [#model] is the model in effect
/// (override or the renderer's own) and [#texture] the base texture in effect. Layers that
/// derive assets from the base texture must use [#texture], not the renderer config, so
/// per-stack reskins carry through; asset-specific overrides (glowmask, trim) are on [#overrides].
public final class ArmorRenderContext {
    private final PoseStack matrices;
    private final SubmitNodeCollector queue;
    private final ItemStack stack;
    private final HumanoidRenderState state;
    private final EquipmentSlot slot;
    private final int light;
    private final GeoArmorRenderer renderer;
    private final GeoArmorBones model;
    private final PoseCopyingModel posedModel;
    private final Identifier texture;
    private final ArmorOverrides overrides;
    private int nextOrder;

    public ArmorRenderContext(
            PoseStack matrices,
            SubmitNodeCollector queue,
            ItemStack stack,
            HumanoidRenderState state,
            EquipmentSlot slot,
            int light,
            GeoArmorRenderer renderer,
            GeoArmorBones model,
            HumanoidModel<HumanoidRenderState> poseSource,
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
        // Both geo model classes are HumanoidModels (biped + player variant); the bones interface hides that
        this.posedModel = new PoseCopyingModel(poseSource, (HumanoidModel<?>) model);
        this.texture = texture;
        this.overrides = overrides;
    }

    public PoseStack matrices() { return matrices; }
    public SubmitNodeCollector queue() { return queue; }
    public ItemStack stack() { return stack; }
    public HumanoidRenderState state() { return state; }
    public EquipmentSlot slot() { return slot; }
    public int light() { return light; }
    public GeoArmorRenderer renderer() { return renderer; }
    /// The armor model ([GeoArmorModel] for bipeds, [GeoPlayerArmorModel] for players); also a `BipedEntityModel`.
    /// Its parts are posed at draw time, not when a layer runs.
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
    public void submit(RenderType layer, int light, int color, @Nullable TextureAtlasSprite sprite) {
        queue.order(nextOrder++)
                .submitModel(posedModel, state, matrices, layer, light, OverlayTexture.NO_OVERLAY, color, sprite, state.outlineColor, null);
    }

    /// Whether a trim pass applies to this entity. Vanilla draws no trims on baby humanoids (its
    /// baby armor meshes carry none), except on small armor stands - same rule here. The
    /// proportions themselves need no special case: the pose copy fits the parts to the baby.
    public boolean rendersTrims() {
        return !state.isBaby || state instanceof ArmorStandRenderState;
    }
}
