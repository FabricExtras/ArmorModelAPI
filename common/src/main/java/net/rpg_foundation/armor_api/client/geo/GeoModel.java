package net.rpg_foundation.armor_api.client.geo;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/// Raw Bedrock geometry, restricted to the subset ArmorModelAPI supports: box-UV cuboids on a
/// bone tree. Parse output only - baked into a vanilla `TexturedModelData` by [GeoBaker] and
/// discarded; nothing renders from these records.
///
/// All coordinates are in Bedrock model space (y up from the ground plane, 16 units per block),
/// exactly as authored. The bedrock→vanilla conversion happens in the baker.
@Environment(EnvType.CLIENT)
public record GeoModel(
        int textureWidth,
        int textureHeight,
        List<GeoBone> bones // flat, in file order; parents referenced by name
) {

    @Environment(EnvType.CLIENT)
    public record GeoBone(
            String name,
            @Nullable String parent,
            float[] pivot,              // [x, y, z] absolute
            float @Nullable [] rotation, // [x, y, z] degrees, around pivot; null = none
            List<GeoCube> cubes
    ) { }

    @Environment(EnvType.CLIENT)
    public record GeoCube(
            float[] origin,             // [x, y, z] absolute min corner
            float[] size,               // [x, y, z]
            float[] uv,                 // [u, v] box-UV top-left
            float inflate,
            boolean mirror,
            float @Nullable [] pivot,    // per-cube rotation pivot, absolute; null = none
            float @Nullable [] rotation  // [x, y, z] degrees; null = none
    ) { }
}
