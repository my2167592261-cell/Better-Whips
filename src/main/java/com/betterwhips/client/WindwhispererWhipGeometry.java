package com.betterwhips.client;

import com.betterwhips.physics.WindwhispererWhipDimensions;

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

final class WindwhispererWhipGeometry {
    static final int SEGMENT_COUNT = WindwhispererWhipDimensions.SEGMENT_COUNT;
    static final float MODEL_UNIT = 1.0F / 16.0F;
    static final float TEXTURE_SIZE = 32.0F;
    static final float CHAIN_PIVOT_Y = 7.5F * MODEL_UNIT;

    private static final float HANDLE_VISUAL_CENTER_Y = 0.8F * MODEL_UNIT;

    static final float AUTHORED_ROOT_Y_ROTATION_DEGREES = 0.0F;
    static final float[] AUTHORED_PIVOT_Z = WindwhispererWhipDimensions.authoredPivotZFloatCopy();
    static final float[] REST_LENGTHS = buildRestLengths();

    static final double[] SEGMENT_COLLIDER_RADIUS = {
            0.16252D, 0.06225D, 0.18392D, 0.09763D, 0.16913D, 0.05976D, 0.15428D, 0.05862D, 0.17245D, 0.05717D,
            0.15717D, 0.05548D, 0.14223D, 0.05366D, 0.15776D, 0.05208D, 0.14270D, 0.08196D, 0.12815D, 0.04813D,
            0.14100D, 0.04630D, 0.12647D, 0.04433D, 0.11257D, 0.04219D, 0.12264D, 0.03989D, 0.11268D, 0.03786D,
            0.10389D, 0.05894D, 0.11814D, 0.03327D, 0.10930D, 0.03102D, 0.10045D, 0.02871D, 0.11465D, 0.02630D,
            0.10575D, 0.02373D, 0.09684D, 0.02136D, 0.11101D, 0.01888D, 0.10205D, 0.01631D, 0.01497D, 0.01374D
    };

    private static final String MODEL_RESOURCE =
            "/assets/better_whips/models/entity/windwhisperer_whip.bbmodel";
    static final WindwhispererWhipGeometry INSTANCE = load();

    private final List<CubeGeometry> handle;
    private final List<List<CubeGeometry>> segments;
    private final List<CubeGeometry> tip;

    private WindwhispererWhipGeometry(List<CubeGeometry> handle,
                              List<List<CubeGeometry>> segments,
                              List<CubeGeometry> tip) {
        this.handle = handle;
        this.segments = segments;
        this.tip = tip;
    }

    static void applyAuthoredRootTransform(PoseStack stack) {

    }

    void renderHandle(PoseStack stack, VertexConsumer consumer, int light, int overlay) {
        render(handle, false, stack, consumer, light, overlay);
    }

    void renderHandleScaledAboutCenter(PoseStack stack, VertexConsumer consumer,
                                       int light, int overlay, float scale,
                                       int red, int green, int blue, int alpha) {
        stack.pushPose();
        stack.translate(0.0F, HANDLE_VISUAL_CENTER_Y, 0.0F);
        stack.scale(scale, scale, scale);
        stack.translate(0.0F, -HANDLE_VISUAL_CENTER_Y, 0.0F);
        render(handle, false, stack, consumer, light, overlay, red, green, blue, alpha);
        stack.popPose();
    }

    void renderAuthoredLash(PoseStack stack, VertexConsumer consumer, int light, int overlay) {
        renderAuthoredLash(stack, consumer, light, overlay, 255, 255, 255, 255);
    }

    void renderAuthoredLash(PoseStack stack, VertexConsumer consumer, int light, int overlay,
                            int red, int green, int blue, int alpha) {
        stack.pushPose();
        stack.translate(0.0F, CHAIN_PIVOT_Y, 0.0F);
        stack.mulPose(Axis.XP.rotationDegrees(90.0F));
        stack.translate(0.0F, -CHAIN_PIVOT_Y, 0.0F);
        for (List<CubeGeometry> segment : segments) {
            render(segment, false, stack, consumer, light, overlay, red, green, blue, alpha);
        }
        render(tip, false, stack, consumer, light, overlay, red, green, blue, alpha);
        stack.popPose();
    }

    void renderAuthoredLashScaledPerPart(PoseStack stack, VertexConsumer consumer,
                                         int light, int overlay, float scale,
                                         int red, int green, int blue, int alpha) {
        stack.pushPose();
        stack.translate(0.0F, CHAIN_PIVOT_Y, 0.0F);
        stack.mulPose(Axis.XP.rotationDegrees(90.0F));
        stack.translate(0.0F, -CHAIN_PIVOT_Y, 0.0F);
        for (int i = 0; i < segments.size(); ++i) {
            float centerZ = (AUTHORED_PIVOT_Z[i] + AUTHORED_PIVOT_Z[i + 1])
                    * 0.5F * MODEL_UNIT;
            stack.pushPose();
            stack.translate(0.0F, CHAIN_PIVOT_Y, centerZ);
            stack.scale(scale, scale, scale);
            stack.translate(0.0F, -CHAIN_PIVOT_Y, -centerZ);
            render(segments.get(i), false, stack, consumer, light, overlay,
                    red, green, blue, alpha);
            stack.popPose();
        }
        if (!tip.isEmpty()) {
            float tipZ = AUTHORED_PIVOT_Z[SEGMENT_COUNT] * MODEL_UNIT;
            stack.pushPose();
            stack.translate(0.0F, CHAIN_PIVOT_Y, tipZ);
            stack.scale(scale, scale, scale);
            stack.translate(0.0F, -CHAIN_PIVOT_Y, -tipZ);
            render(tip, false, stack, consumer, light, overlay, red, green, blue, alpha);
            stack.popPose();
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
            if (length < 1.0E-7D) continue;
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
        if (index == SEGMENT_COUNT - 1 && !tip.isEmpty()) {
            stack.pushPose();
            stack.translate(0.0D, 0.0D, -REST_LENGTHS[index]);
            render(tip, true, stack, consumer, light, overlay, red, green, blue, alpha);
            stack.popPose();
        }
    }

    void renderDynamicSegmentScaledAboutCenter(int index, PoseStack stack, VertexConsumer consumer,
                                               int light, int overlay, float scale,
                                               int red, int green, int blue, int alpha) {
        if (index < 0 || index >= segments.size()) {
            return;
        }
        float centerZ = -REST_LENGTHS[index] * 0.5F;
        stack.pushPose();
        stack.translate(0.0F, 0.0F, centerZ);
        stack.scale(scale, scale, scale);
        stack.translate(0.0F, 0.0F, -centerZ);
        renderDynamicSegment(index, stack, consumer, light, overlay,
                red, green, blue, alpha);
        stack.popPose();
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

    private static WindwhispererWhipGeometry load() {
        try (var stream = WindwhispererWhipGeometry.class.getResourceAsStream(MODEL_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing Windwhisperer Whip model: " + MODEL_RESOURCE);
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
            List<CubeGeometry> tip = new ArrayList<>();
            List<List<CubeGeometry>> segments = new ArrayList<>(SEGMENT_COUNT);
            for (int i = 0; i < SEGMENT_COUNT; ++i) {
                segments.add(new ArrayList<>());
            }

            for (JsonElement raw : root.getAsJsonArray("elements")) {
                JsonObject element = raw.getAsJsonObject();
                GroupInfo group = elementGroups.get(element.get("uuid").getAsString());
                if (group == null) {
                    continue;
                }
                String type = element.get("type").getAsString();
                CubeGeometry geometry;
                if ("cube".equals(type)) {
                    geometry = readCube(element, group.origin);
                } else if ("mesh".equals(type)) {

                    geometry = readMesh(element, group.origin);
                } else {
                    continue;
                }
                if ("handle".equals(group.name)) {
                    handle.add(geometry);
                    continue;
                }
                if ("whip_tip".equals(group.name)) {
                    tip.add(geometry);
                    continue;
                }
                int index = segmentIndex(group.name);
                if (index >= 0) {
                    segments.get(index).add(geometry);
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
            return new WindwhispererWhipGeometry(List.copyOf(handle), List.copyOf(immutableSegments), List.copyOf(tip));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to load Windwhisperer Whip Blockbench geometry",
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
        if (!name.startsWith("whip_segment_")) {
            return -1;
        }
        try {
            int authored = Integer.parseInt(name.substring("whip_segment_".length()));
            return authored >= 1 && authored <= SEGMENT_COUNT ? authored - 1 : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static void validateAuthoredBones(Map<String, GroupInfo> groups) {
        GroupInfo chain = findGroup(groups, "whip_chain");
        requireVector(chain.origin, 0.0F, 7.5F, 0.0F, "whip_chain pivot");
        requireVector(chain.rotation, 90.0F, 0.0F, 0.0F, "blade_chain rotation");

        for (int i = 0; i < SEGMENT_COUNT; ++i) {
            String name = String.format(java.util.Locale.ROOT, "whip_segment_%02d", i + 1);
            GroupInfo group = findGroup(groups, name);
            requireVector(group.origin, 0.0F, 7.5F, AUTHORED_PIVOT_Z[i], name + " pivot");
        }
        GroupInfo tipGroup = findGroup(groups, "whip_tip");
        requireVector(tipGroup.origin, 0.0F, 7.5F, AUTHORED_PIVOT_Z[SEGMENT_COUNT], "whip_tip pivot");
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

    private static CubeGeometry readMesh(JsonObject element, float[] pivot) {
        JsonObject vertexObject = element.getAsJsonObject("vertices");
        JsonObject faceObject = element.getAsJsonObject("faces");
        float[] elementOrigin = element.has("origin")
                ? vec(element.getAsJsonArray("origin"))
                : new float[]{0.0F, 0.0F, 0.0F};
        float[] elementRotation = element.has("rotation")
                ? vec(element.getAsJsonArray("rotation"))
                : new float[]{0.0F, 0.0F, 0.0F};

        Map<String, float[]> vertices = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : vertexObject.entrySet()) {
            float[] point = vec(entry.getValue().getAsJsonArray());
            point[0] *= MODEL_UNIT;
            point[1] *= MODEL_UNIT;
            point[2] *= MODEL_UNIT;
            if (hasRotation(elementRotation)) {
                rotatePointAroundOrigin(point, elementOrigin, elementRotation);
            }
            vertices.put(entry.getKey(), point);
        }

        List<FaceGeometry> faces = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : faceObject.entrySet()) {
            JsonObject face = entry.getValue().getAsJsonObject();
            if (!face.has("vertices")) {
                continue;
            }
            JsonArray order = face.getAsJsonArray("vertices");
            if (order.size() < 3) {
                continue;
            }
            JsonObject uvObject = face.has("uv") && face.get("uv").isJsonObject()
                    ? face.getAsJsonObject("uv") : new JsonObject();

            if (order.size() == 3 || order.size() == 4) {
                faces.add(meshFace(order, uvObject, vertices, pivot));
            } else {
                for (int i = 1; i < order.size() - 1; ++i) {
                    JsonArray triangle = new JsonArray();
                    triangle.add(order.get(0));
                    triangle.add(order.get(i));
                    triangle.add(order.get(i + 1));
                    faces.add(meshFace(triangle, uvObject, vertices, pivot));
                }
            }
        }
        return new CubeGeometry(List.copyOf(faces));
    }

    private static FaceGeometry meshFace(JsonArray order, JsonObject uvObject,
                                         Map<String, float[]> vertices, float[] pivot) {
        int count = Math.min(order.size(), 4);
        VertexData[] absolute = new VertexData[4];
        VertexData[] relative = new VertexData[4];
        for (int i = 0; i < 4; ++i) {
            int source = Math.min(i, count - 1);
            String key = order.get(source).getAsString();
            float[] point = vertices.get(key);
            if (point == null) {
                throw new IllegalStateException("Missing mesh vertex " + key);
            }
            float u = 0.0F;
            float v = 0.0F;
            if (uvObject.has(key)) {
                JsonArray uv = uvObject.getAsJsonArray(key);
                u = uv.get(0).getAsFloat() / TEXTURE_SIZE;
                v = uv.get(1).getAsFloat() / TEXTURE_SIZE;
            }
            absolute[i] = new VertexData(point[0], point[1], point[2], u, v);
            relative[i] = new VertexData(
                    point[0] - pivot[0] * MODEL_UNIT,
                    point[1] - pivot[1] * MODEL_UNIT,
                    point[2] - pivot[2] * MODEL_UNIT,
                    u, v);
        }
        Vector3f n = normal(absolute[0], absolute[1], absolute[2]);
        return new FaceGeometry(absolute, relative, n.x, n.y, n.z);
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
