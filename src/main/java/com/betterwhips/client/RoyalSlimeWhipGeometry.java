package com.betterwhips.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class RoyalSlimeWhipGeometry {
    static final int SEGMENT_COUNT = 49;
    static final float MODEL_UNIT = 1.0F / 16.0F;
    static final float TEXTURE_SIZE = 256.0F;
    static final float CHAIN_PIVOT_Y = 7.5F * MODEL_UNIT;
    static final float[] AUTHORED_PIVOT_Z = {
            0.0000F, -3.2000F, -6.3734F, -9.5202F, -12.6404F, -15.7340F, -18.8011F,
            -21.8415F, -24.8553F, -27.8426F, -30.8032F, -33.7372F, -36.6447F, -39.5255F,
            -42.3798F, -45.2074F, -48.0085F, -50.7830F, -53.5309F, -56.2521F, -58.9468F,
            -61.6149F, -64.2564F, -66.8713F, -69.4596F, -72.0213F, -74.5564F, -77.0649F,
            -79.5468F, -82.0021F, -84.4309F, -86.8330F, -89.2085F, -91.5574F, -93.8798F,
            -96.1755F, -98.4447F, -100.6872F, -102.9032F, -105.0926F, -107.2553F, -109.3915F,
            -111.5011F, -113.5840F, -115.6404F, -117.6702F, -119.6734F, -121.6500F, -123.6000F,
            -128.2500F
    };
    static final float[] REST_LENGTHS = buildRestLengths();

    static final double[] SEGMENT_COLLIDER_RADIUS = {
            0.14512D, 0.12617D, 0.11806D, 0.11611D, 0.11999D,
            0.11220D, 0.13260D, 0.18302D, 0.10633D, 0.10438D,
            0.10766D, 0.10046D, 0.12009D, 0.10149D, 0.09459D,
            0.15608D, 0.09533D, 0.08872D, 0.10757D, 0.08916D,
            0.08286D, 0.08090D, 0.08299D, 0.12915D, 0.09505D,
            0.07682D, 0.07113D, 0.06917D, 0.07083D, 0.06526D,
            0.08253D, 0.10221D, 0.05939D, 0.05743D, 0.05912D,
            0.05352D, 0.07001D, 0.05327D, 0.04765D, 0.07528D,
            0.04741D, 0.04179D, 0.05812D, 0.04156D, 0.03592D,
            0.05508D, 0.03572D, 0.04836D, 0.09253D
    };

    private static final String MODEL_RESOURCE =
            "/assets/better_whips/models/entity/royal_slime_whip.bbmodel";
    static final RoyalSlimeWhipGeometry INSTANCE = load();

    private final List<CubeGeometry> handle;
    private final List<List<CubeGeometry>> segments;

    private RoyalSlimeWhipGeometry(List<CubeGeometry> handle,
                                   List<List<CubeGeometry>> segments) {
        this.handle = handle;
        this.segments = segments;
    }

    void renderHandle(PoseStack stack, VertexConsumer consumer, int light, int overlay) {
        render(handle, false, stack, consumer, light, overlay);
    }

    void renderAuthoredLash(PoseStack stack, VertexConsumer consumer, int light, int overlay) {
        stack.pushPose();
        stack.translate(0.0F, CHAIN_PIVOT_Y, 0.0F);
        stack.mulPose(Axis.XP.rotationDegrees(90.0F));
        stack.translate(0.0F, -CHAIN_PIVOT_Y, 0.0F);
        for (List<CubeGeometry> segment : segments) {
            render(segment, false, stack, consumer, light, overlay);
        }
        stack.popPose();
    }

    void renderStoredCoil(PoseStack stack, VertexConsumer consumer, int light, int overlay) {
        Vec3[] stored = new Vec3[REST_LENGTHS.length + 1];
        WhipCoilPose.buildStoredItem(new Vec3(0.0D, CHAIN_PIVOT_Y, 0.0D), REST_LENGTHS,
                1.0D, 3, 5.00D, 0.045D, stored);
        for (int i = 0; i < SEGMENT_COUNT; ++i) {
            Vec3 start = stored[i];
            Vec3 end = stored[i + 1];
            Vec3 chord = end.subtract(start);
            double length = chord.length();
            if (length < 1.0E-7D) {
                continue;
            }
            Vector3f direction = chord.scale(1.0D / length).toVector3f();
            Quaternionf rotation = new Quaternionf().rotationTo(
                    0.0F, 0.0F, -1.0F, direction.x, direction.y, direction.z);
            stack.pushPose();
            stack.translate(start.x, start.y, start.z);
            stack.mulPose(rotation);
            renderDynamicSegment(i, stack, consumer, light, overlay);
            stack.popPose();
        }
    }

    void renderDynamicSegment(int index, PoseStack stack, VertexConsumer consumer,
                              int light, int overlay) {
        renderDynamicSegment(index, stack, consumer, light, overlay, 255, 255, 255, 255);
    }

    void renderDynamicSegment(int index, PoseStack stack, VertexConsumer consumer,
                              int light, int overlay, int red, int green, int blue, int alpha) {
        if (index < 0 || index >= segments.size()) {
            return;
        }
        render(segments.get(index), true, stack, consumer, light, overlay,
                red, green, blue, alpha);
    }

    private static void render(List<CubeGeometry> cubes, boolean relative,
                               PoseStack stack, VertexConsumer consumer, int light, int overlay) {
        render(cubes, relative, stack, consumer, light, overlay, 255, 255, 255, 255);
    }

    private static void render(List<CubeGeometry> cubes, boolean relative,
                               PoseStack stack, VertexConsumer consumer, int light, int overlay,
                               int red, int green, int blue, int alpha) {
        for (int detailIndex = 0; detailIndex < cubes.size(); ++detailIndex) {
            if (!WhipPerformanceTuning.shouldRenderDetailElement(detailIndex, cubes.size())) {
                continue;
            }
            CubeGeometry cube = cubes.get(detailIndex);
            cube.render(relative, stack, consumer, light, overlay, red, green, blue, alpha);
        }
    }

    private static RoyalSlimeWhipGeometry load() {
        try (var stream = RoyalSlimeWhipGeometry.class.getResourceAsStream(MODEL_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing Royal Slime Whip model: " + MODEL_RESOURCE);
            }
            JsonObject root = JsonParser.parseReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();

            Map<String, GroupInfo> groups = new HashMap<>();
            for (JsonElement raw : root.getAsJsonArray("groups")) {
                JsonObject group = raw.getAsJsonObject();
                String name = group.get("name").getAsString();
                float[] origin = vec(group.getAsJsonArray("origin"));
                float[] rotation = group.has("rotation")
                        ? vec(group.getAsJsonArray("rotation"))
                        : new float[]{0.0F, 0.0F, 0.0F};
                groups.put(group.get("uuid").getAsString(),
                        new GroupInfo(name, origin, rotation));
            }
            validateAuthoredBones(groups);

            Map<String, GroupInfo> elementGroups = new HashMap<>();
            for (JsonElement node : root.getAsJsonArray("outliner")) {
                mapOutliner(node, null, groups, elementGroups);
            }

            List<CubeGeometry> handle = new ArrayList<>();
            List<List<CubeGeometry>> segments = new ArrayList<>(SEGMENT_COUNT);
            for (int i = 0; i < SEGMENT_COUNT; ++i) {
                segments.add(new ArrayList<>());
            }

            for (JsonElement raw : root.getAsJsonArray("elements")) {
                JsonObject element = raw.getAsJsonObject();
                if (!"cube".equals(element.get("type").getAsString())) {
                    continue;
                }
                GroupInfo group = elementGroups.get(element.get("uuid").getAsString());
                if (group == null) {
                    continue;
                }
                ensureElementHasNoIndependentRotation(element);
                CubeGeometry cube = readCube(element, group.origin);
                if ("handle".equals(group.name)) {
                    handle.add(cube);
                    continue;
                }
                int index = segmentIndex(group.name);
                if (index >= 0) {
                    segments.get(index).add(cube);
                }
            }

            if (handle.isEmpty()) {
                throw new IllegalStateException("Blockbench handle bone has no cubes");
            }
            for (int i = 0; i < SEGMENT_COUNT; ++i) {
                if (segments.get(i).isEmpty()) {
                    throw new IllegalStateException("Blockbench lash bone " + i + " has no cubes");
                }
            }

            List<List<CubeGeometry>> immutableSegments = new ArrayList<>(SEGMENT_COUNT);
            for (List<CubeGeometry> segment : segments) {
                immutableSegments.add(List.copyOf(segment));
            }
            return new RoyalSlimeWhipGeometry(List.copyOf(handle), List.copyOf(immutableSegments));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to load Royal Slime Whip Blockbench geometry",
                    exception);
        }
    }

    private static void mapOutliner(JsonElement node, GroupInfo inherited,
                                    Map<String, GroupInfo> groups,
                                    Map<String, GroupInfo> elementGroups) {
        if (node.isJsonPrimitive()) {
            if (inherited != null) {
                elementGroups.put(node.getAsString(), inherited);
            }
            return;
        }
        if (!node.isJsonObject()) {
            return;
        }
        JsonObject object = node.getAsJsonObject();
        GroupInfo current = inherited;
        if (object.has("uuid")) {
            GroupInfo own = groups.get(object.get("uuid").getAsString());
            if (own != null) {
                current = own;
            }
        }
        if (object.has("children")) {
            for (JsonElement child : object.getAsJsonArray("children")) {
                mapOutliner(child, current, groups, elementGroups);
            }
        }
    }

    private static int segmentIndex(String name) {
        if ("whip_tip".equals(name)) {
            return SEGMENT_COUNT - 1;
        }
        if (!name.startsWith("whip_segment_")) {
            return -1;
        }
        try {
            int authored = Integer.parseInt(name.substring("whip_segment_".length()));
            return authored >= 1 && authored <= 48 ? authored - 1 : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static void validateAuthoredBones(Map<String, GroupInfo> groups) {
        GroupInfo chain = findGroup(groups, "whip_chain");
        requireVector(chain.origin, 0.0F, 7.5F, 0.0F, "whip_chain pivot");
        requireVector(chain.rotation, 90.0F, 0.0F, 0.0F, "whip_chain rotation");

        for (int i = 0; i < SEGMENT_COUNT; ++i) {
            String name = i == SEGMENT_COUNT - 1
                    ? "whip_tip"
                    : String.format(java.util.Locale.ROOT, "whip_segment_%02d", i + 1);
            GroupInfo group = findGroup(groups, name);
            requireVector(group.origin, 0.0F, 7.5F, AUTHORED_PIVOT_Z[i], name + " pivot");
        }
    }

    private static GroupInfo findGroup(Map<String, GroupInfo> groups, String name) {
        for (GroupInfo group : groups.values()) {
            if (name.equals(group.name)) {
                return group;
            }
        }
        throw new IllegalStateException("Missing Blockbench bone " + name);
    }

    private static void requireVector(float[] value, float x, float y, float z, String label) {
        if (Math.abs(value[0] - x) > 1.0E-4F
                || Math.abs(value[1] - y) > 1.0E-4F
                || Math.abs(value[2] - z) > 1.0E-4F) {
            throw new IllegalStateException(label + " changed: [" + value[0] + ", "
                    + value[1] + ", " + value[2] + "]");
        }
    }

    private static void ensureElementHasNoIndependentRotation(JsonObject element) {
        if (!element.has("rotation")) {
            return;
        }
        JsonArray rotation = element.getAsJsonArray("rotation");
        if (Math.abs(rotation.get(0).getAsFloat()) > 1.0E-5F
                || Math.abs(rotation.get(1).getAsFloat()) > 1.0E-5F
                || Math.abs(rotation.get(2).getAsFloat()) > 1.0E-5F) {
            throw new IllegalStateException("Unsupported rotated whip cube "
                    + element.get("name").getAsString());
        }
    }

    private static CubeGeometry readCube(JsonObject element, float[] pivot) {
        float[] from = vec(element.getAsJsonArray("from"));
        float[] to = vec(element.getAsJsonArray("to"));
        List<FaceGeometry> faces = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry
                : element.getAsJsonObject("faces").entrySet()) {
            JsonObject face = entry.getValue().getAsJsonObject();
            if (face.has("uv")) {
                faces.add(cubeFace(entry.getKey(), from, to, pivot,
                        uvRect(face.getAsJsonArray("uv"))));
            }
        }
        return new CubeGeometry(List.copyOf(faces));
    }

    private static FaceGeometry cubeFace(String side, float[] a, float[] b,
                                         float[] pivot, float[] uv) {
        float x0 = a[0] * MODEL_UNIT;
        float y0 = a[1] * MODEL_UNIT;
        float z0 = a[2] * MODEL_UNIT;
        float x1 = b[0] * MODEL_UNIT;
        float y1 = b[1] * MODEL_UNIT;
        float z1 = b[2] * MODEL_UNIT;
        float[][] points;
        switch (side) {
            case "north" -> points = new float[][]{
                    {x1, y0, z0}, {x0, y0, z0}, {x0, y1, z0}, {x1, y1, z0}};
            case "south" -> points = new float[][]{
                    {x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}};
            case "west" -> points = new float[][]{
                    {x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}};
            case "east" -> points = new float[][]{
                    {x1, y0, z1}, {x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}};
            case "up" -> points = new float[][]{
                    {x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}, {x0, y1, z0}};
            case "down" -> points = new float[][]{
                    {x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}};
            default -> throw new IllegalArgumentException("Unknown cube face " + side);
        }

        float u0 = uv[0] / TEXTURE_SIZE;
        float v0 = uv[1] / TEXTURE_SIZE;
        float u1 = uv[2] / TEXTURE_SIZE;
        float v1 = uv[3] / TEXTURE_SIZE;
        VertexData[] absolute = new VertexData[4];
        VertexData[] relative = new VertexData[4];
        for (int i = 0; i < 4; ++i) {
            float u = i == 0 || i == 3 ? u1 : u0;
            float v = i < 2 ? v1 : v0;
            absolute[i] = new VertexData(points[i][0], points[i][1], points[i][2], u, v);
            relative[i] = new VertexData(
                    points[i][0] - pivot[0] * MODEL_UNIT,
                    points[i][1] - pivot[1] * MODEL_UNIT,
                    points[i][2] - pivot[2] * MODEL_UNIT,
                    u, v);
        }
        Vector3f absoluteNormal = normal(absolute[0], absolute[1], absolute[2]);
        return new FaceGeometry(absolute, relative,
                absoluteNormal.x, absoluteNormal.y, absoluteNormal.z);
    }

    private static Vector3f normal(VertexData a, VertexData b, VertexData c) {
        Vector3f edgeA = new Vector3f(b.x - a.x, b.y - a.y, b.z - a.z);
        Vector3f edgeB = new Vector3f(c.x - a.x, c.y - a.y, c.z - a.z);
        Vector3f normal = edgeA.cross(edgeB);
        return normal.lengthSquared() < 1.0E-8F
                ? new Vector3f(0.0F, 1.0F, 0.0F)
                : normal.normalize();
    }

    private static float[] vec(JsonArray array) {
        return new float[]{array.get(0).getAsFloat(), array.get(1).getAsFloat(),
                array.get(2).getAsFloat()};
    }

    private static float[] uvRect(JsonArray array) {
        return new float[]{array.get(0).getAsFloat(), array.get(1).getAsFloat(),
                array.get(2).getAsFloat(), array.get(3).getAsFloat()};
    }

    private static float[] buildRestLengths() {
        float[] result = new float[SEGMENT_COUNT];
        for (int i = 0; i < SEGMENT_COUNT; ++i) {
            result[i] = Math.abs(AUTHORED_PIVOT_Z[i + 1] - AUTHORED_PIVOT_Z[i]) * MODEL_UNIT;
        }
        return result;
    }

    private record GroupInfo(String name, float[] origin, float[] rotation) {}

    private record CubeGeometry(List<FaceGeometry> faces) {
        void render(boolean relative, PoseStack stack, VertexConsumer consumer,
                    int light, int overlay) {
            render(relative, stack, consumer, light, overlay, 255, 255, 255, 255);
        }

        void render(boolean relative, PoseStack stack, VertexConsumer consumer,
                    int light, int overlay, int red, int green, int blue, int alpha) {
            for (FaceGeometry face : faces) {
                face.render(relative, stack, consumer, light, overlay, red, green, blue, alpha);
            }
        }
    }

    private record FaceGeometry(VertexData[] absolute, VertexData[] relative,
                                float nx, float ny, float nz) {
        void render(boolean useRelative, PoseStack stack, VertexConsumer consumer,
                    int light, int overlay, int red, int green, int blue, int alpha) {
            PoseStack.Pose pose = stack.last();
            VertexData[] vertices = useRelative ? relative : absolute;
            for (VertexData vertex : vertices) {
                consumer.addVertex(pose.pose(), vertex.x, vertex.y, vertex.z)
                        .setColor(red, green, blue, alpha)
                        .setUv(vertex.u, vertex.v)
                        .setOverlay(overlay)
                        .setLight(light)
                        .setNormal(pose, nx, ny, nz);
            }
        }
    }

    private record VertexData(float x, float y, float z, float u, float v) {}
}
