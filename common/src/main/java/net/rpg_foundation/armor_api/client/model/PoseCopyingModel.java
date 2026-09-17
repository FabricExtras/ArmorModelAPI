package net.rpg_foundation.armor_api.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Map;
import java.util.WeakHashMap;

/// The model that is actually submitted for an armor pass: the geo armor model's geometry, posed
/// at draw time from the entity's own body model.
///
/// Since 1.21.9 a submitted model is posed right before it is drawn (`setupAnim(state)` from the
/// feature renderer), so a pose copied at submit time would be overwritten. Wrapping the pair
/// moves the copy to draw time, the same way Fabric API's `TransformCopyingModel` does: reset the
/// armor's parts, pose the *source* model with the entity's state, then derive the biped parts
/// from it. The source is the render layer's parent model - the body model the entity renderer
/// already uses for this entity - so armor stands, zombies, piglins, players (with whatever a
/// player animation library did to the player model) all pose correctly without any per-entity
/// code. The armor model's own `setupAnim` is deliberately not run: `HumanoidModel`'s would
/// animate a living biped over the copied pose (idle arm bob and all).
///
/// **Adults**: the part transform (position, rotation, scale) is copied one to one. Geo armor
/// bones are children of the biped parts and follow; visibility stays the armor model's own,
/// which carries the slot visibility - except for **players**, whose biped part visibility is
/// copied too. `PlayerModel#setupAnim` resets it every call, and player-animation libraries
/// (PAL, used by Better Combat) hide the body in their first-person pass by clearing it there;
/// before the pose copy our player armor model *was* a `PlayerModel`, so that hiding applied to
/// it through its own `setupAnim`, and copying the flags keeps it that way. Mobs and armor stands
/// keep our visibility on purpose: an armor stand's body model hides its arms when the stand shows
/// none, while vanilla's stand armor still draws the sleeves.
///
/// **Babies**: vanilla has no single baby rule any more. Small armor stands are the adult mesh
/// with `BabyModelTransform` baked into the part poses (scale + offset), but zombies, piglins,
/// zombie villagers and drowned are hand-authored baby models (`BabyZombieModel`, ...) with part
/// scale 1, their own pivots and much smaller boxes, and vanilla draws hand-authored baby *armor*
/// meshes on them. We ship no baby geometry, so each armor part is **fitted** onto the source
/// part instead: per axis, the adult reference box (the vanilla biped box our geo bones were
/// authored around) is scaled to the source part's own cube box and its centre moved onto it,
/// with the offset applied in the part's rotated frame so the piece still rotates about the
/// source pivot. That gives the adult armor at the baby's proportions and positions for any baby
/// model that keeps its cubes on the standard parts; a source part without cubes of its own falls
/// back to the plain copy. Adults see no difference from the fit (their boxes are the reference).
public final class PoseCopyingModel extends Model<HumanoidRenderState> {
    private final HumanoidModel<HumanoidRenderState> source;
    private final HumanoidModel<?> armor;

    public PoseCopyingModel(HumanoidModel<HumanoidRenderState> source, HumanoidModel<?> armor) {
        super(armor.root(), armor::renderType);
        this.source = source;
        this.armor = armor;
    }

    @Override
    public void setupAnim(HumanoidRenderState state) {
        resetPose();
        source.setupAnim(state);
        if (state.isBaby) {
            fit(source.head, armor.head, Box.HEAD);
            fit(source.body, armor.body, Box.BODY);
            fit(source.rightArm, armor.rightArm, Box.RIGHT_ARM);
            fit(source.leftArm, armor.leftArm, Box.LEFT_ARM);
            fit(source.rightLeg, armor.rightLeg, Box.RIGHT_LEG);
            fit(source.leftLeg, armor.leftLeg, Box.LEFT_LEG);
        } else {
            copy(source.head, armor.head);
            copy(source.body, armor.body);
            copy(source.rightArm, armor.rightArm);
            copy(source.leftArm, armor.leftArm);
            copy(source.rightLeg, armor.rightLeg);
            copy(source.leftLeg, armor.leftLeg);
        }
        copy(source.hat, armor.hat); // relative to head, no cubes of its own in armor models
        if (state instanceof AvatarRenderState) {
            copyVisibility(source.head, armor.head);
            copyVisibility(source.body, armor.body);
            copyVisibility(source.rightArm, armor.rightArm);
            copyVisibility(source.leftArm, armor.leftArm);
            copyVisibility(source.rightLeg, armor.rightLeg);
            copyVisibility(source.leftLeg, armor.leftLeg);
        }
    }

    private static void copyVisibility(ModelPart from, ModelPart to) {
        to.visible = from.visible;
    }

    private static void copy(ModelPart from, ModelPart to) {
        to.x = from.x;
        to.y = from.y;
        to.z = from.z;
        to.xRot = from.xRot;
        to.yRot = from.yRot;
        to.zRot = from.zRot;
        to.xScale = from.xScale;
        to.yScale = from.yScale;
        to.zScale = from.zScale;
    }

    /// Scales and offsets `to` so the adult reference box lands on `from`'s own cube box.
    /// With source scale `s`, fit factor `f = srcExtent / refExtent` and centres `Bc` (source) /
    /// `Rc` (reference), a cube point `c` of ours should end up where the source would put
    /// `(c - Rc) * f + Bc`; solving `pos' + R(s*f*c) = srcPos + R(s*((c - Rc)*f + Bc))` gives
    /// scale `s*f` and `pos' = srcPos + R(s*(Bc - f*Rc))`, `R` being the part's rotation.
    private static void fit(ModelPart from, ModelPart to, Box reference) {
        Box box = ownCubeBox(from);
        if (box == null) {
            copy(from, to);
            return;
        }
        float fx = factor(box.maxX - box.minX, reference.maxX - reference.minX);
        float fy = factor(box.maxY - box.minY, reference.maxY - reference.minY);
        float fz = factor(box.maxZ - box.minZ, reference.maxZ - reference.minZ);
        var delta = new Vector3f(
                from.xScale * (box.centerX() - fx * reference.centerX()),
                from.yScale * (box.centerY() - fy * reference.centerY()),
                from.zScale * (box.centerZ() - fz * reference.centerZ()));
        if (from.xRot != 0F || from.yRot != 0F || from.zRot != 0F) {
            new Quaternionf().rotationZYX(from.zRot, from.yRot, from.xRot).transform(delta); // same order as ModelPart#translateAndRotate
        }
        to.x = from.x + delta.x;
        to.y = from.y + delta.y;
        to.z = from.z + delta.z;
        to.xRot = from.xRot;
        to.yRot = from.yRot;
        to.zRot = from.zRot;
        to.xScale = from.xScale * fx;
        to.yScale = from.yScale * fy;
        to.zScale = from.zScale * fz;
    }

    private static float factor(float sourceExtent, float referenceExtent) {
        return sourceExtent > 0F && referenceExtent > 0F ? sourceExtent / referenceExtent : 1F;
    }

    /// Bounds of a part's own cubes (children excluded), in the part's local space; null when it
    /// has none. Cubes are final, so the result is cached per part for as long as the part lives.
    private static final Map<ModelPart, Box> OWN_CUBE_BOXES = new WeakHashMap<>(); // render thread only

    private static Box ownCubeBox(ModelPart part) {
        if (OWN_CUBE_BOXES.containsKey(part)) {
            return OWN_CUBE_BOXES.get(part);
        }
        float[] b = { Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE };
        part.visit(new PoseStack(), (pose, partPath, cubeIndex, cube) -> {
            if (partPath.isEmpty()) { // the part's own cubes; children carry a "/name" path
                b[0] = Math.min(b[0], cube.minX);
                b[1] = Math.min(b[1], cube.minY);
                b[2] = Math.min(b[2], cube.minZ);
                b[3] = Math.max(b[3], cube.maxX);
                b[4] = Math.max(b[4], cube.maxY);
                b[5] = Math.max(b[5], cube.maxZ);
            }
        });
        Box box = b[0] == Float.MAX_VALUE ? null : new Box(b[0], b[1], b[2], b[3], b[4], b[5]);
        OWN_CUBE_BOXES.put(part, box);
        return box;
    }

    /// An axis-aligned box in part-local model units. The constants are vanilla's adult biped
    /// boxes (`HumanoidModel#createMesh`, no deformation) - the frame geo armor bones are
    /// authored against (see `GeoBaker`'s standard pivots).
    private record Box(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        static final Box HEAD = new Box(-4, -8, -4, 4, 0, 4);
        static final Box BODY = new Box(-4, 0, -2, 4, 12, 2);
        static final Box RIGHT_ARM = new Box(-3, -2, -2, 1, 10, 2);
        static final Box LEFT_ARM = new Box(-1, -2, -2, 3, 10, 2);
        static final Box RIGHT_LEG = new Box(-2, 0, -2, 2, 12, 2);
        static final Box LEFT_LEG = RIGHT_LEG;

        float centerX() { return (minX + maxX) / 2F; }
        float centerY() { return (minY + maxY) / 2F; }
        float centerZ() { return (minZ + maxZ) / 2F; }
    }
}
