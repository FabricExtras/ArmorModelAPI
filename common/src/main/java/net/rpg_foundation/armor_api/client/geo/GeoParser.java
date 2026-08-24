package net.rpg_foundation.armor_api.client.geo;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/// Parses a Bedrock `.geo.json` into [GeoModel] records.
///
/// Deliberately strict: anything outside the supported subset (per-face UV objects, poly meshes,
/// bone-level mirror/inflate) throws a [GeoParseException] naming the file and bone, so a broken
/// or unsupported asset fails loudly at load instead of rendering garbage.
public final class GeoParser {

    public static class GeoParseException extends RuntimeException {
        public GeoParseException(String source, String message) {
            super("Geo model '" + source + "': " + message);
        }
    }

    private GeoParser() { }

    /// @param source used in error messages only, typically the resource path
    public static GeoModel parse(JsonObject root, String source) {
        var geometries = root.getAsJsonArray("minecraft:geometry");
        if (geometries == null || geometries.isEmpty()) {
            throw new GeoParseException(source, "missing 'minecraft:geometry'");
        }
        if (geometries.size() > 1) {
            throw new GeoParseException(source, "multiple geometries per file are not supported");
        }
        var geometry = geometries.get(0).getAsJsonObject();

        var description = geometry.getAsJsonObject("description");
        if (description == null) {
            throw new GeoParseException(source, "missing geometry 'description'");
        }
        int textureWidth = getInt(description, "texture_width", source);
        int textureHeight = getInt(description, "texture_height", source);

        var bones = new ArrayList<GeoModel.GeoBone>();
        var bonesJson = geometry.getAsJsonArray("bones");
        if (bonesJson != null) {
            for (JsonElement boneElement : bonesJson) {
                bones.add(parseBone(boneElement.getAsJsonObject(), source));
            }
        }
        return new GeoModel(textureWidth, textureHeight, bones);
    }

    private static GeoModel.GeoBone parseBone(JsonObject bone, String source) {
        var name = bone.has("name") ? bone.get("name").getAsString() : null;
        if (name == null || name.isEmpty()) {
            throw new GeoParseException(source, "bone without a name");
        }
        if (bone.has("poly_mesh")) {
            throw new GeoParseException(source, "bone '" + name + "' uses poly_mesh (unsupported)");
        }
        if (bone.has("mirror")) {
            throw new GeoParseException(source, "bone '" + name + "' uses bone-level mirror (unsupported; set it per cube)");
        }
        if (bone.has("inflate")) {
            throw new GeoParseException(source, "bone '" + name + "' uses bone-level inflate (unsupported; set it per cube)");
        }
        var parent = bone.has("parent") ? bone.get("parent").getAsString() : null;
        var pivot = getVec3(bone, "pivot", name, source);
        if (pivot == null) {
            throw new GeoParseException(source, "bone '" + name + "' has no pivot");
        }
        var rotation = getVec3(bone, "rotation", name, source);

        var cubes = new ArrayList<GeoModel.GeoCube>();
        if (bone.has("cubes")) {
            for (JsonElement cubeElement : bone.getAsJsonArray("cubes")) {
                cubes.add(parseCube(cubeElement.getAsJsonObject(), name, source));
            }
        }
        return new GeoModel.GeoBone(name, parent, pivot, rotation, cubes);
    }

    private static GeoModel.GeoCube parseCube(JsonObject cube, String boneName, String source) {
        var origin = getVec3(cube, "origin", boneName, source);
        var size = getVec3(cube, "size", boneName, source);
        if (origin == null || size == null) {
            throw new GeoParseException(source, "cube in bone '" + boneName + "' is missing origin/size");
        }
        var uvElement = cube.get("uv");
        if (uvElement == null) {
            throw new GeoParseException(source, "cube in bone '" + boneName + "' has no uv");
        }
        if (!uvElement.isJsonArray()) {
            throw new GeoParseException(source, "cube in bone '" + boneName + "' uses per-face UV (unsupported; use box UV)");
        }
        var uv = toFloats(uvElement.getAsJsonArray(), 2, boneName, source);

        float inflate = cube.has("inflate") ? cube.get("inflate").getAsFloat() : 0f;
        boolean mirror = cube.has("mirror") && cube.get("mirror").getAsBoolean();
        var pivot = getVec3(cube, "pivot", boneName, source);
        var rotation = getVec3(cube, "rotation", boneName, source);
        if (rotation != null && pivot == null) {
            throw new GeoParseException(source, "rotated cube in bone '" + boneName + "' has no pivot");
        }
        return new GeoModel.GeoCube(origin, size, uv, inflate, mirror, pivot, rotation);
    }

    private static float @org.jetbrains.annotations.Nullable [] getVec3(JsonObject owner, String key, String boneName, String source) {
        if (!owner.has(key)) {
            return null;
        }
        return toFloats(owner.getAsJsonArray(key), 3, boneName, source);
    }

    private static float[] toFloats(JsonArray array, int expectedLength, String boneName, String source) {
        if (array.size() != expectedLength) {
            throw new GeoParseException(source, "bone '" + boneName + "': expected " + expectedLength + " numbers, got " + array.size());
        }
        var result = new float[expectedLength];
        for (int i = 0; i < expectedLength; i++) {
            result[i] = array.get(i).getAsFloat();
        }
        return result;
    }

    private static int getInt(JsonObject owner, String key, String source) {
        if (!owner.has(key)) {
            throw new GeoParseException(source, "missing '" + key + "'");
        }
        return owner.get(key).getAsInt();
    }
}
