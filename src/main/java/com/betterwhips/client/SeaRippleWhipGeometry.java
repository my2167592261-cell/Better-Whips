package com.betterwhips.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class SeaRippleWhipGeometry {
    static final int TAIL_COUNT = 5;
    static final int SEGMENT_COUNT = 35;
    static final float MODEL_UNIT = 1.0F / 16.0F;
    static final float TEXTURE_SIZE = 128.0F;
    static final float CHAIN_PIVOT_Y = 7.5F * MODEL_UNIT;
    static final float[] REST_LENGTHS = buildRestLengths();
    private static final String MODEL_RESOURCE =
            "/assets/better_whips/models/entity/untamed_sea_whip.bbmodel";
    static final SeaRippleWhipGeometry INSTANCE = load();

    private final List<CubeGeometry> handle;
    private final List<List<List<CubeGeometry>>> tails;

    private final float[] tailPivotY;

    private final float[][] tailChainRotation;

    private final float[][] tailRootLocal;

    private final float[][] tailRootDirectionLocal;

    private final float[][] tailWidthAxisLocal;

    private SeaRippleWhipGeometry(List<CubeGeometry> handle,
                                 List<List<List<CubeGeometry>>> tails,
                                 float[] tailPivotY,
                                 float[][] tailChainRotation,
                                 float[][] tailRootLocal,
                                 float[][] tailRootDirectionLocal,
                                 float[][] tailWidthAxisLocal) {
        this.handle = handle;
        this.tails = tails;
        this.tailPivotY = tailPivotY;
        this.tailChainRotation = tailChainRotation;
        this.tailRootLocal = tailRootLocal;
        this.tailRootDirectionLocal = tailRootDirectionLocal;
        this.tailWidthAxisLocal = tailWidthAxisLocal;
    }

    static float tailPivotY(int tailIndex) {
        int index = Math.max(0, Math.min(TAIL_COUNT - 1, tailIndex));
        return INSTANCE.tailPivotY[index];
    }

    static float tailRootLocalX(int tailIndex) {
        int index = Math.max(0, Math.min(TAIL_COUNT - 1, tailIndex));
        return INSTANCE.tailRootLocal[index][0];
    }

    static float tailRootLocalY(int tailIndex) {
        int index = Math.max(0, Math.min(TAIL_COUNT - 1, tailIndex));
        return INSTANCE.tailRootLocal[index][1];
    }

    static float tailRootLocalZ(int tailIndex) {
        int index = Math.max(0, Math.min(TAIL_COUNT - 1, tailIndex));
        return INSTANCE.tailRootLocal[index][2];
    }

    static float tailRootDirectionLocalX(int tailIndex) {
        int index = Math.max(0, Math.min(TAIL_COUNT - 1, tailIndex));
        return INSTANCE.tailRootDirectionLocal[index][0];
    }

    static float tailRootDirectionLocalY(int tailIndex) {
        int index = Math.max(0, Math.min(TAIL_COUNT - 1, tailIndex));
        return INSTANCE.tailRootDirectionLocal[index][1];
    }

    static float tailRootDirectionLocalZ(int tailIndex) {
        int index = Math.max(0, Math.min(TAIL_COUNT - 1, tailIndex));
        return INSTANCE.tailRootDirectionLocal[index][2];
    }

    static float tailWidthAxisLocalX(int tailIndex) {
        int index = Math.max(0, Math.min(TAIL_COUNT - 1, tailIndex));
        return INSTANCE.tailWidthAxisLocal[index][0];
    }

    static float tailWidthAxisLocalY(int tailIndex) {
        int index = Math.max(0, Math.min(TAIL_COUNT - 1, tailIndex));
        return INSTANCE.tailWidthAxisLocal[index][1];
    }

    static float tailWidthAxisLocalZ(int tailIndex) {
        int index = Math.max(0, Math.min(TAIL_COUNT - 1, tailIndex));
        return INSTANCE.tailWidthAxisLocal[index][2];
    }

    void renderHandle(PoseStack stack, VertexConsumer consumer, int light, int overlay) {
        render(handle, false, stack, consumer, light, overlay);
    }

    void renderAuthoredLash(PoseStack stack, VertexConsumer consumer, int light, int overlay) {
        stack.pushPose();
        stack.translate(0.0F, CHAIN_PIVOT_Y, 0.0F);
        stack.mulPose(Axis.XP.rotationDegrees(90.0F));
        stack.translate(0.0F, -CHAIN_PIVOT_Y, 0.0F);
        for (int tailIndex = 0; tailIndex < tails.size(); ++tailIndex) {
            List<List<CubeGeometry>> tail = tails.get(tailIndex);
            float pivotY = tailPivotY[tailIndex];
            float[] rotation = tailChainRotation[tailIndex];
            stack.pushPose();
            stack.translate(0.0F, pivotY, 0.0F);
            stack.mulPose(new Quaternionf().rotationXYZ(
                    (float)Math.toRadians(rotation[0]),
                    (float)Math.toRadians(rotation[1]),
                    (float)Math.toRadians(rotation[2])));
            stack.translate(0.0F, -pivotY, 0.0F);
            for (List<CubeGeometry> segment : tail) {
                render(segment, false, stack, consumer, light, overlay);
            }
            stack.popPose();
        }
        stack.popPose();
    }

    void renderDynamicSegment(int tailIndex, int segmentIndex, PoseStack stack,
                              VertexConsumer consumer, int light, int overlay) {
        if (tailIndex < 0 || tailIndex >= tails.size()) return;
        List<List<CubeGeometry>> tail = tails.get(tailIndex);
        if (segmentIndex < 0 || segmentIndex >= tail.size()) return;
        render(tail.get(segmentIndex), true, stack, consumer, light, overlay);
    }

    private static void render(List<CubeGeometry> geometries, boolean relative,
                               PoseStack stack, VertexConsumer consumer, int light, int overlay) {
        for (int detailIndex = 0; detailIndex < geometries.size(); ++detailIndex) {
            if (!WhipPerformanceTuning.shouldRenderDetailElement(detailIndex, geometries.size())) continue;
            geometries.get(detailIndex).render(relative, stack, consumer, light, overlay);
        }
    }

    private static SeaRippleWhipGeometry load() {
        try (var stream = SeaRippleWhipGeometry.class.getResourceAsStream(MODEL_RESOURCE)) {
            if (stream == null) throw new IllegalStateException("Missing Untamed Sea model: " + MODEL_RESOURCE);
            JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, GroupInfo> groups = new HashMap<>();
            for (JsonElement raw : root.getAsJsonArray("groups")) {
                JsonObject group = raw.getAsJsonObject();
                float[] origin = vec(group.getAsJsonArray("origin"));
                float[] rotation = group.has("rotation") ? vec(group.getAsJsonArray("rotation")) : new float[]{0,0,0};
                groups.put(group.get("uuid").getAsString(), new GroupInfo(group.get("name").getAsString(), origin, rotation));
            }
            validateAuthoredBones(groups);
            float[] tailPivotY = new float[TAIL_COUNT];
            float[][] tailChainRotation = new float[TAIL_COUNT][3];
            float[][] tailRootLocal = new float[TAIL_COUNT][3];
            float[][] tailRootDirectionLocal = new float[TAIL_COUNT][3];
            float[][] tailWidthAxisLocal = new float[TAIL_COUNT][3];
            GroupInfo whipChain = findGroup(groups, "whip_chain");
            for (int t = 0; t < TAIL_COUNT; ++t) {
                String chainName = String.format(java.util.Locale.ROOT, "tail_%02d_chain", t + 1);
                GroupInfo chain = findGroup(groups, chainName);
                tailPivotY[t] = chain.origin[1] * MODEL_UNIT;
                tailChainRotation[t] = chain.rotation.clone();

                float[] relative = new float[]{
                        chain.origin[0] - whipChain.origin[0],
                        chain.origin[1] - whipChain.origin[1],
                        chain.origin[2] - whipChain.origin[2]};
                if (hasRotation(whipChain.rotation)) {
                    rotateLocalVector(relative, whipChain.rotation);
                }
                tailRootLocal[t][0] = (whipChain.origin[0] + relative[0]) * MODEL_UNIT;
                tailRootLocal[t][1] = (whipChain.origin[1] + relative[1]) * MODEL_UNIT;
                tailRootLocal[t][2] = (whipChain.origin[2] + relative[2]) * MODEL_UNIT;

                float[] direction = new float[]{0.0F, 0.0F, -1.0F};
                if (hasRotation(chain.rotation)) {
                    rotateLocalVector(direction, chain.rotation);
                }
                if (hasRotation(whipChain.rotation)) {
                    rotateLocalVector(direction, whipChain.rotation);
                }
                float directionLength = (float)Math.sqrt(
                        direction[0] * direction[0]
                                + direction[1] * direction[1]
                                + direction[2] * direction[2]);
                if (directionLength < 1.0E-6F) {
                    throw new IllegalStateException("Untamed Sea tail " + (t + 1)
                            + " has an invalid authored root direction");
                }
                tailRootDirectionLocal[t][0] = direction[0] / directionLength;
                tailRootDirectionLocal[t][1] = direction[1] / directionLength;
                tailRootDirectionLocal[t][2] = direction[2] / directionLength;

                float[] widthAxis = new float[]{1.0F, 0.0F, 0.0F};
                if (hasRotation(chain.rotation)) {
                    rotateLocalVector(widthAxis, chain.rotation);
                }
                if (hasRotation(whipChain.rotation)) {
                    rotateLocalVector(widthAxis, whipChain.rotation);
                }
                float widthLength = (float)Math.sqrt(
                        widthAxis[0] * widthAxis[0]
                                + widthAxis[1] * widthAxis[1]
                                + widthAxis[2] * widthAxis[2]);
                if (widthLength < 1.0E-6F) {
                    throw new IllegalStateException("Untamed Sea tail " + (t + 1)
                            + " has an invalid authored width axis");
                }
                tailWidthAxisLocal[t][0] = widthAxis[0] / widthLength;
                tailWidthAxisLocal[t][1] = widthAxis[1] / widthLength;
                tailWidthAxisLocal[t][2] = widthAxis[2] / widthLength;
            }
            Map<String, GroupInfo> elementGroups = new HashMap<>();
            for (JsonElement node : root.getAsJsonArray("outliner")) mapOutliner(node, null, groups, elementGroups);
            List<CubeGeometry> handle = new ArrayList<>();
            List<List<List<CubeGeometry>>> tails = new ArrayList<>(TAIL_COUNT);
            for (int t=0;t<TAIL_COUNT;++t) {
                List<List<CubeGeometry>> segs=new ArrayList<>(SEGMENT_COUNT);
                for(int i=0;i<SEGMENT_COUNT;++i) segs.add(new ArrayList<>());
                tails.add(segs);
            }
            for (JsonElement raw : root.getAsJsonArray("elements")) {
                JsonObject element=raw.getAsJsonObject();
                GroupInfo group=elementGroups.get(element.get("uuid").getAsString());
                if(group==null) continue;
                CubeGeometry geometry;
                String type=element.get("type").getAsString();
                if("cube".equals(type)) geometry=readCube(element, group.origin);
                else if("mesh".equals(type)) geometry=readMesh(element, group.origin);
                else continue;
                if("handle".equals(group.name)) { handle.add(geometry); continue; }
                int[] idx=tailSegmentIndex(group.name);
                if(idx!=null) tails.get(idx[0]).get(idx[1]).add(geometry);
            }
            if(handle.isEmpty()) throw new IllegalStateException("Untamed Sea handle has no geometry");
            List<List<List<CubeGeometry>>> immutableTails=new ArrayList<>(TAIL_COUNT);
            for(int t=0;t<TAIL_COUNT;++t){
                List<List<CubeGeometry>> isegs=new ArrayList<>(SEGMENT_COUNT);
                for(int i=0;i<SEGMENT_COUNT;++i){
                    List<CubeGeometry> segment=tails.get(t).get(i);
                    if(segment.isEmpty()) throw new IllegalStateException("Untamed Sea missing tail "+t+" segment "+i);
                    isegs.add(List.copyOf(segment));
                }
                immutableTails.add(List.copyOf(isegs));
            }
            return new SeaRippleWhipGeometry(List.copyOf(handle), List.copyOf(immutableTails),
                    tailPivotY, tailChainRotation, tailRootLocal, tailRootDirectionLocal,
                    tailWidthAxisLocal);
        } catch(Exception ex){ throw new IllegalStateException("Failed to load Untamed Sea Blockbench geometry", ex); }
    }

    private static void mapOutliner(JsonElement node, GroupInfo inherited, Map<String, GroupInfo> groups, Map<String, GroupInfo> elementGroups) {
        if(node.isJsonPrimitive()){ if(inherited!=null) elementGroups.put(node.getAsString(), inherited); return; }
        if(!node.isJsonObject()) return;
        JsonObject object=node.getAsJsonObject();
        GroupInfo current=inherited;
        if(object.has("uuid")){ GroupInfo own=groups.get(object.get("uuid").getAsString()); if(own!=null) current=own; }
        if(object.has("children")) for(JsonElement child:object.getAsJsonArray("children")) mapOutliner(child,current,groups,elementGroups);
    }

    private static int[] tailSegmentIndex(String name) {
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("tail_(\\d{2})_segment_(\\d{2})").matcher(name);
        if(!m.matches()) return null;
        int tail=Integer.parseInt(m.group(1))-1, segment=Integer.parseInt(m.group(2))-1;
        return tail>=0&&tail<TAIL_COUNT&&segment>=0&&segment<SEGMENT_COUNT ? new int[]{tail,segment} : null;
    }

    private static void validateAuthoredBones(Map<String, GroupInfo> groups) {

        GroupInfo chain = findGroup(groups, "whip_chain");
        requireVector(chain.origin, 0, 7.5F, 0, "whip_chain pivot");
        requireVector(chain.rotation, 90, 0, 0, "whip_chain rotation");
        for (int t = 0; t < TAIL_COUNT; ++t) {
            String prefix = String.format(java.util.Locale.ROOT, "tail_%02d_", t + 1);
            findGroup(groups, prefix + "chain");
            for (int i = 0; i < SEGMENT_COUNT; ++i) {
                String seg = prefix + String.format(java.util.Locale.ROOT, "segment_%02d", i + 1);
                findGroup(groups, seg);
            }
            findGroup(groups, prefix + "tip");
        }
    }

    private static GroupInfo findGroup(Map<String, GroupInfo> groups,String name){
        for(GroupInfo g:groups.values()) if(name.equals(g.name)) return g;
        throw new IllegalStateException("Missing Untamed Sea bone "+name);
    }
    private static void requireVector(float[] v,float x,float y,float z,String label){
        if(Math.abs(v[0]-x)>1e-4F||Math.abs(v[1]-y)>1e-4F||Math.abs(v[2]-z)>1e-4F)
            throw new IllegalStateException(label+" changed: ["+v[0]+", "+v[1]+", "+v[2]+"]");
    }
    private static float[] buildRestLengths(){ float[] out=new float[SEGMENT_COUNT]; java.util.Arrays.fill(out,2.0F*MODEL_UNIT); return out; }
    private record GroupInfo(String name,float[] origin,float[] rotation){}
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
                        atlasUvRect(face.getAsJsonArray("uv"), textureIndex(face))));
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

            float[] local = vec(entry.getValue().getAsJsonArray());
            local[0] *= MODEL_UNIT;
            local[1] *= MODEL_UNIT;
            local[2] *= MODEL_UNIT;
            if (hasRotation(elementRotation)) {
                rotateLocalVector(local, elementRotation);
            }
            float[] point = new float[]{
                    local[0] + elementOrigin[0] * MODEL_UNIT,
                    local[1] + elementOrigin[1] * MODEL_UNIT,
                    local[2] + elementOrigin[2] * MODEL_UNIT};
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
                faces.add(meshFace(order, uvObject, vertices, pivot, textureIndex(face)));
            } else {
                for (int i = 1; i < order.size() - 1; ++i) {
                    JsonArray triangle = new JsonArray();
                    triangle.add(order.get(0));
                    triangle.add(order.get(i));
                    triangle.add(order.get(i + 1));
                    faces.add(meshFace(triangle, uvObject, vertices, pivot, textureIndex(face)));
                }
            }
        }
        return new CubeGeometry(List.copyOf(faces));
    }

    private static FaceGeometry meshFace(JsonArray order, JsonObject uvObject,
                                         Map<String, float[]> vertices, float[] pivot,
                                         int textureIndex) {
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
                u = (uv.get(0).getAsFloat() + atlasUOffset(textureIndex)) / TEXTURE_SIZE;
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

    private static void rotateLocalVector(float[] point, float[] rotationDegrees) {
        Vector3f local = new Vector3f(point[0], point[1], point[2]);
        Quaternionf rotation = new Quaternionf().rotationXYZ(
                (float) Math.toRadians(rotationDegrees[0]),
                (float) Math.toRadians(rotationDegrees[1]),
                (float) Math.toRadians(rotationDegrees[2]));
        rotation.transform(local);
        point[0] = local.x;
        point[1] = local.y;
        point[2] = local.z;
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

    private static float[] atlasUvRect(JsonArray array, int textureIndex) {
        float offsetU = atlasUOffset(textureIndex);
        return new float[]{array.get(0).getAsFloat() + offsetU, array.get(1).getAsFloat(),
                array.get(2).getAsFloat() + offsetU, array.get(3).getAsFloat()};
    }

    private static int textureIndex(JsonObject face) {
        if (!face.has("texture") || face.get("texture").isJsonNull()) {
            return 0;
        }
        try {
            return face.get("texture").getAsInt();
        } catch (RuntimeException ignored) {
            try {
                return Integer.parseInt(face.get("texture").getAsString());
            } catch (RuntimeException ignoredAgain) {
                return 0;
            }
        }
    }

    private static float atlasUOffset(int textureIndex) {
        return 0.0F;
    }

    private record CubeGeometry(List<FaceGeometry> faces) {
        Vector3f center(boolean relative) {
            float minX = Float.POSITIVE_INFINITY;
            float minY = Float.POSITIVE_INFINITY;
            float minZ = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY;
            float maxY = Float.NEGATIVE_INFINITY;
            float maxZ = Float.NEGATIVE_INFINITY;
            for (FaceGeometry face : faces) {
                VertexData[] vertices = relative ? face.relative() : face.absolute();
                for (VertexData vertex : vertices) {
                    minX = Math.min(minX, vertex.x());
                    minY = Math.min(minY, vertex.y());
                    minZ = Math.min(minZ, vertex.z());
                    maxX = Math.max(maxX, vertex.x());
                    maxY = Math.max(maxY, vertex.y());
                    maxZ = Math.max(maxZ, vertex.z());
                }
            }
            if (!Float.isFinite(minX) || !Float.isFinite(minY) || !Float.isFinite(minZ)) {
                return new Vector3f();
            }
            return new Vector3f((minX + maxX) * 0.5F,
                    (minY + maxY) * 0.5F,
                    (minZ + maxZ) * 0.5F);
        }

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
