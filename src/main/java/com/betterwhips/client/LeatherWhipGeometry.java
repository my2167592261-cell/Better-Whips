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

final class LeatherWhipGeometry {
    static final int SEGMENT_COUNT = 56;
    static final float MODEL_UNIT = 1.0F / 16.0F;
    static final float TEXTURE_SIZE = 256.0F;
    static final float CHAIN_PIVOT_Y = 7.5F * MODEL_UNIT;
    static final float[] AUTHORED_PIVOT_Z = {
            0.0000F, -3.0266F, -6.0279F, -9.0037F, -11.9541F, -14.8792F, -17.7788F,
            -20.6531F, -23.5019F, -26.3254F, -29.1234F, -31.8961F, -34.6434F, -37.3652F,
            -40.0617F, -42.7328F, -45.3784F, -47.9987F, -50.5936F, -53.1631F, -55.7072F,
            -58.2258F, -60.7191F, -63.1870F, -65.6295F, -68.0466F, -70.4383F, -72.8046F,
            -75.1455F, -77.4611F, -79.7512F, -82.0159F, -84.2552F, -86.4691F, -88.6577F,
            -90.8208F, -92.9585F, -95.0709F, -97.1578F, -99.2193F, -101.2555F, -103.2662F,
            -105.2516F, -107.2115F, -109.1461F, -111.0552F, -112.9390F, -114.7974F, -116.6303F,
            -118.4379F, -120.2201F, -121.9769F, -123.7082F, -125.4142F, -127.0948F, -128.7500F,
            -135.7700F
    };
    static final float[] REST_LENGTHS = buildRestLengths();

    static final double[] SEGMENT_COLLIDER_RADIUS = {
            0.05913D, 0.05831D, 0.05750D, 0.05668D, 0.05587D, 0.05505D,
            0.05424D, 0.05342D, 0.05261D, 0.05179D, 0.05098D, 0.05016D,
            0.04935D, 0.04853D, 0.04772D, 0.04690D, 0.04609D, 0.04527D,
            0.04446D, 0.04364D, 0.04283D, 0.04201D, 0.04120D, 0.04038D,
            0.03957D, 0.03875D, 0.03794D, 0.03713D, 0.03631D, 0.03550D,
            0.03468D, 0.03387D, 0.03305D, 0.03224D, 0.03142D, 0.03061D,
            0.02979D, 0.02898D, 0.02816D, 0.02735D, 0.02653D, 0.02572D,
            0.02490D, 0.02409D, 0.02327D, 0.02246D, 0.02164D, 0.02083D,
            0.02001D, 0.01920D, 0.01838D, 0.01800D, 0.01800D, 0.01800D,
            0.01800D, 0.03884D
    };

    private static final String MODEL_RESOURCE =
            "/assets/better_whips/models/entity/leather_whip.bbmodel";
    static final LeatherWhipGeometry INSTANCE = load();

    private final List<CubeGeometry> handle;
    private final List<List<CubeGeometry>> segments;

    private LeatherWhipGeometry(List<CubeGeometry> handle,
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
                1.0D, 3, 5.00D, 0.050D, stored);
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

    private static LeatherWhipGeometry load() {
        try (var stream = LeatherWhipGeometry.class.getResourceAsStream(MODEL_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing Leather Whip model: " + MODEL_RESOURCE);
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
            return new LeatherWhipGeometry(List.copyOf(handle), List.copyOf(immutableSegments));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to load Leather Whip Blockbench geometry",
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
            return authored >= 1 && authored <= 55 ? authored - 1 : -1;
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

    private static CubeGeometry readCube(JsonObject element, float[] pivot) {
        float[] from = vec(element.getAsJsonArray("from"));
        float[] to = vec(element.getAsJsonArray("to"));
        float[] elementOrigin = element.has("origin")
                ? vec(element.getAsJsonArray("origin"))
                : new float[]{
                        (from[0] + to[0]) * 0.5F,
                        (from[1] + to[1]) * 0.5F,
                        (from[2] + to[2]) * 0.5F};
        float[] elementRotation = element.has("rotation")
                ? vec(element.getAsJsonArray("rotation"))
                : new float[]{0.0F, 0.0F, 0.0F};
        List<FaceGeometry> faces = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry
                : element.getAsJsonObject("faces").entrySet()) {
            JsonObject face = entry.getValue().getAsJsonObject();
            if (face.has("uv")) {
                faces.add(cubeFace(entry.getKey(), from, to, pivot,
                        elementOrigin, elementRotation,
                        uvRect(face.getAsJsonArray("uv"))));
            }
        }
        return new CubeGeometry(List.copyOf(faces));
    }

    private static FaceGeometry cubeFace(String side, float[] a, float[] b,
                                         float[] pivot, float[] elementOrigin,
                                         float[] elementRotation, float[] uv) {
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

        if (hasRotation(elementRotation)) {
            for (float[] point : points) {
                rotatePointAroundOrigin(point, elementOrigin, elementRotation);
            }
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

    private static boolean hasRotation(float[] rotation) {
        return Math.abs(rotation[0]) > 1.0E-5F
                || Math.abs(rotation[1]) > 1.0E-5F
                || Math.abs(rotation[2]) > 1.0E-5F;
    }

    private static void rotatePointAroundOrigin(float[] point, float[] origin,
                                                float[] rotationDegrees) {
        float ox = origin[0] * MODEL_UNIT;
        float oy = origin[1] * MODEL_UNIT;
        float oz = origin[2] * MODEL_UNIT;
        Vector3f local = new Vector3f(point[0] - ox, point[1] - oy, point[2] - oz);
        Quaternionf rotation = new Quaternionf().rotationXYZ(
                (float) Math.toRadians(rotationDegrees[0]),
                (float) Math.toRadians(rotationDegrees[1]),
                (float) Math.toRadians(rotationDegrees[2]));
        rotation.transform(local);
        point[0] = local.x + ox;
        point[1] = local.y + oy;
        point[2] = local.z + oz;
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
