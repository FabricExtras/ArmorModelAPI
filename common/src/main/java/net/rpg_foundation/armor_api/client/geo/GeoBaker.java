package net.rpg_foundation.armor_api.client.geo;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.EntityModelPartNames;
import net.rpg_foundation.armor_api.ArmorModelApi;

import java.util.HashMap;
import java.util.Map;

/// Bakes a parsed [GeoModel] into a vanilla `TexturedModelData` whose root carries the seven
/// standard biped parts (head, hat, body, right_arm, left_arm, right_leg, left_leg) at their
/// vanilla pivots, with the geo bones attached underneath by the armor bone-name convention.
/// Vanilla pose copy (`copyBipedStateTo`) then animates the standard parts and everything
/// below follows - no custom render code anywhere.
///
/// ## Coordinate conversion
///
/// Bedrock space is y-up with the model standing on y=0; vanilla part space is y-down with
/// y=0 at the top of a 24-unit body (AzureLib bridged the two at render time with
/// `translate(0, 24/16, 0); scale(-1, -1, 1)` around geometry it had x-negated at bake).
/// Conjugating that mirror through every bone transform collapses the whole difference into
/// bake-time rules - signs verified against AzureLib's bake (negates x/y rotation, mirrors
/// pivot x) composed with its render root flip (which negates both back):
///
/// - absolute position:  V(x, y, z) = (x, 24 - y, z)
/// - rotation angles:    pitch = rad(rx), yaw = rad(ry), roll = rad(rz) - signs unchanged,
///   and vanilla's `rotationZYX(roll, yaw, pitch)` equals the bedrock Z·Y·X application order
/// - every pivot becomes relative to its parent's pivot; cuboid min corner within its bone is
///   (ox - px, py - oy - sy, oz - pz)
///
/// Rotated cubes get a synthesized wrapper part (vanilla cuboids cannot rotate); box UV and
/// per-cube mirror map 1:1 onto `ModelPartBuilder`; inflate maps onto `Dilation`.
@Environment(EnvType.CLIENT)
public final class GeoBaker {

    /// Conventional armor bone name → the standard biped part it attaches under.
    /// Covers both the empty `bipedX` wrappers (the usual top-level bones) and bare `armorX`
    /// bones at top level, so either authoring style bakes.
    private static final Map<String, String> PART_BY_TOP_LEVEL_BONE = Map.ofEntries(
            Map.entry("bipedHead", EntityModelPartNames.HEAD),
            Map.entry("armorHead", EntityModelPartNames.HEAD),
            Map.entry("bipedBody", EntityModelPartNames.BODY),
            Map.entry("armorBody", EntityModelPartNames.BODY),
            Map.entry("bipedRightArm", EntityModelPartNames.RIGHT_ARM),
            Map.entry("armorRightArm", EntityModelPartNames.RIGHT_ARM),
            Map.entry("bipedLeftArm", EntityModelPartNames.LEFT_ARM),
            Map.entry("armorLeftArm", EntityModelPartNames.LEFT_ARM),
            Map.entry("bipedRightLeg", EntityModelPartNames.RIGHT_LEG),
            Map.entry("armorRightLeg", EntityModelPartNames.RIGHT_LEG),
            Map.entry("armorRightBoot", EntityModelPartNames.RIGHT_LEG),
            Map.entry("bipedLeftLeg", EntityModelPartNames.LEFT_LEG),
            Map.entry("armorLeftLeg", EntityModelPartNames.LEFT_LEG),
            Map.entry("armorLeftBoot", EntityModelPartNames.LEFT_LEG)
    );

    /// Vanilla biped part pivots in vanilla space - must match `BipedEntityModel.getModelData`,
    /// because the vanilla pose is copied onto parts sitting exactly here.
    private static final Map<String, float[]> STANDARD_PART_PIVOTS = Map.of(
            EntityModelPartNames.HEAD, new float[] { 0, 0, 0 },
            EntityModelPartNames.HAT, new float[] { 0, 0, 0 },
            EntityModelPartNames.BODY, new float[] { 0, 0, 0 },
            EntityModelPartNames.RIGHT_ARM, new float[] { -5, 2, 0 },
            EntityModelPartNames.LEFT_ARM, new float[] { 5, 2, 0 },
            EntityModelPartNames.RIGHT_LEG, new float[] { -1.9f, 12, 0 },
            EntityModelPartNames.LEFT_LEG, new float[] { 1.9f, 12, 0 }
    );

    private GeoBaker() { }

    public static TexturedModelData bake(GeoModel model, String source) {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();

        var standardParts = new HashMap<String, ModelPartData>();
        for (var entry : STANDARD_PART_PIVOTS.entrySet()) {
            var pivot = entry.getValue();
            standardParts.put(entry.getKey(), root.addChild(
                    entry.getKey(),
                    ModelPartBuilder.create(),
                    ModelTransform.pivot(pivot[0], pivot[1], pivot[2])
            ));
        }

        var childrenByParent = new HashMap<String, java.util.List<GeoModel.GeoBone>>();
        for (var bone : model.bones()) {
            if (bone.parent() != null) {
                childrenByParent.computeIfAbsent(bone.parent(), key -> new java.util.ArrayList<>()).add(bone);
            }
        }

        for (var bone : model.bones()) {
            if (bone.parent() != null) {
                continue;
            }
            var partName = PART_BY_TOP_LEVEL_BONE.get(bone.name());
            if (partName == null) {
                ArmorModelApi.LOGGER.warn(
                        "Geo model '{}': top-level bone '{}' matches no armor bone convention; it will not be posed or rendered",
                        source, bone.name());
                continue;
            }
            var partPivot = STANDARD_PART_PIVOTS.get(partName);
            addBone(standardParts.get(partName), bone, partPivot[0], partPivot[1], partPivot[2],
                    childrenByParent, model, source);
        }
        return TexturedModelData.of(data, model.textureWidth(), model.textureHeight());
    }

    /// Adds `bone` under `parent`, whose pivot sits at (parentX, parentY, parentZ) in vanilla
    /// absolute space, then recurses into the bone's children.
    private static void addBone(
            ModelPartData parent,
            GeoModel.GeoBone bone,
            float parentX, float parentY, float parentZ,
            Map<String, java.util.List<GeoModel.GeoBone>> childrenByParent,
            GeoModel model,
            String source
    ) {
        // V(pivot), then relative to the parent's absolute pivot
        float absX = bone.pivot()[0];
        float absY = 24 - bone.pivot()[1];
        float absZ = bone.pivot()[2];

        var rotation = bone.rotation();
        float pitch = rotation != null ? (float) Math.toRadians(rotation[0]) : 0;
        float yaw = rotation != null ? (float) Math.toRadians(rotation[1]) : 0;
        float roll = rotation != null ? (float) Math.toRadians(rotation[2]) : 0;

        var builder = ModelPartBuilder.create();
        int syntheticCubes = 0;
        for (var cube : bone.cubes()) {
            if (cube.rotation() == null) {
                addCuboid(builder, cube, bone.pivot()[0], bone.pivot()[1], bone.pivot()[2], model, bone.name(), source);
            } else {
                syntheticCubes++;
            }
        }

        var part = parent.addChild(bone.name(), builder, ModelTransform.of(
                absX - parentX, absY - parentY, absZ - parentZ, pitch, yaw, roll));

        // Rotated cubes: vanilla cuboids cannot rotate, so each becomes a wrapper part carrying
        // the cube's own pivot and rotation, holding a single cuboid relative to that pivot.
        int cubeIndex = 0;
        for (var cube : bone.cubes()) {
            if (cube.rotation() == null) {
                continue;
            }
            var cubePivot = cube.pivot();
            var cubeRotation = cube.rotation();
            var cubeBuilder = ModelPartBuilder.create();
            addCuboid(cubeBuilder, cube, cubePivot[0], cubePivot[1], cubePivot[2], model, bone.name(), source);
            part.addChild("cube_" + cubeIndex++, cubeBuilder, ModelTransform.of(
                    cubePivot[0] - bone.pivot()[0],
                    bone.pivot()[1] - cubePivot[1],  // V() y-flip of the relative offset
                    cubePivot[2] - bone.pivot()[2],
                    (float) Math.toRadians(cubeRotation[0]),
                    (float) Math.toRadians(cubeRotation[1]),
                    (float) Math.toRadians(cubeRotation[2])
            ));
        }
        if (syntheticCubes > 0 && ArmorModelApi.LOGGER.isDebugEnabled()) {
            ArmorModelApi.LOGGER.debug("Geo model '{}': bone '{}' baked {} rotated cube(s) as wrapper parts",
                    source, bone.name(), syntheticCubes);
        }

        for (var child : childrenByParent.getOrDefault(bone.name(), java.util.List.of())) {
            addBone(part, child, absX, absY, absZ, childrenByParent, model, source);
        }
    }

    /// Adds one cuboid relative to a bedrock-space pivot (px, py, pz):
    /// min corner = (ox - px, py - oy - sy, oz - pz) per the V() conversion.
    private static void addCuboid(
            ModelPartBuilder builder,
            GeoModel.GeoCube cube,
            float px, float py, float pz,
            GeoModel model,
            String boneName,
            String source
    ) {
        float u = cube.uv()[0];
        float v = cube.uv()[1];
        if (u != (int) u || v != (int) v) {
            ArmorModelApi.LOGGER.warn(
                    "Geo model '{}': bone '{}' has fractional box UV ({}, {}); rounding - texture may be off by a pixel",
                    source, boneName, u, v);
        }
        builder.uv(Math.round(u), Math.round(v))
                .mirrored(cube.mirror())
                .cuboid(
                        cube.origin()[0] - px,
                        py - cube.origin()[1] - cube.size()[1],
                        cube.origin()[2] - pz,
                        cube.size()[0], cube.size()[1], cube.size()[2],
                        new Dilation(cube.inflate())
                )
                .mirrored(false);
    }
}
