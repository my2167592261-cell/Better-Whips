package com.betterwhips.client;

import com.betterwhips.physics.LightningWhipDimensions;
import com.google.gson.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class LightningWhipGeometry {
    static final int SEGMENT_COUNT = LightningWhipDimensions.SEGMENTS;
    static final float CHAIN_PIVOT_Y = LightningWhipDimensions.SOCKET_Y;
    static final float[] REST_LENGTHS = LightningWhipDimensions.restLengths();
    static final double[] SEGMENT_COLLIDER_RADIUS = LightningWhipDimensions.colliderRadii();
    static final LightningWhipGeometry INSTANCE = load();
    private final List<Face> faces;
    private LightningWhipGeometry(List<Face> faces) { this.faces = List.copyOf(faces); }

    void renderHandle(PoseStack stack, VertexConsumer consumer, int light, int overlay) {
        PoseStack.Pose pose = stack.last();
        for (Face face : faces) {
            int faceLight = face.emissive ? LightTexture.FULL_BRIGHT : light;
            for (Vertex v : face.vertices) {
                consumer.addVertex(pose.pose(), v.x, v.y, v.z).setColor(255, 255, 255, 255)
                    .setUv(v.u, v.v).setOverlay(overlay).setLight(faceLight)
                    .setNormal(pose, face.normal.x, face.normal.y, face.normal.z);
            }
        }
    }

    private static LightningWhipGeometry load() {
        String path = "/assets/better_whips/models/entity/lightning_whip.bbmodel";
        try (InputStream stream = LightningWhipGeometry.class.getResourceAsStream(path)) {
            if (stream == null) throw new IOException("Missing lightning hilt: " + path);
            JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject texture = root.getAsJsonArray("textures").get(0).getAsJsonObject();
            float width = texture.get("uv_width").getAsFloat();
            float height = texture.get("uv_height").getAsFloat();
            ArrayList<Face> result = new ArrayList<>();
            for (JsonElement entry : root.getAsJsonArray("elements")) {
                JsonObject e = entry.getAsJsonObject();
                if ((e.has("visibility") && !e.get("visibility").getAsBoolean())
                        || (e.has("export") && !e.get("export").getAsBoolean())) continue;
                if (!"mesh".equals(e.get("type").getAsString())) throw new IOException("Unsupported hilt element: " + e.get("type"));
                Vector3f origin = vec(e.getAsJsonArray("origin"));
                Vector3f angles = vec(e.getAsJsonArray("rotation")).mul((float)(Math.PI / 180.0));
                Quaternionf rotation = new Quaternionf().rotationZYX(angles.z, angles.y, angles.x);
                Map<String, Vector3f> positions = new HashMap<>();
                for (Map.Entry<String, JsonElement> v : e.getAsJsonObject("vertices").entrySet()) {

                    positions.put(v.getKey(), rotation.transform(vec(v.getValue().getAsJsonArray())).add(origin).mul(1f / 16f));
                }
                String name = e.get("name").getAsString();
                boolean emissive = (name.contains("gem") || name.contains("jewel") || name.contains("sapphire") || name.contains("cut_crown"))
                    && !name.contains("gold") && !name.contains("socket") && !name.contains("frame");
                for (JsonElement rawFace : e.getAsJsonObject("faces").asMap().values()) {
                    JsonObject f = rawFace.getAsJsonObject();
                    if (!f.has("texture") || f.get("texture").isJsonNull()) continue;
                    JsonArray order = f.getAsJsonArray("vertices");
                    JsonObject uv = f.getAsJsonObject("uv");
                    int count = order.size();
                    if (count < 3) continue;
                    if (count == 4) addFace(result, positions, uv, order, new int[]{0,1,2,3}, width, height, emissive);
                    else for (int i = 1; i < count - 1; i++) addFace(result, positions, uv, order, new int[]{0,i,i+1,i+1}, width, height, emissive);
                }
            }
            if (result.isEmpty()) throw new IOException("Empty lightning hilt");
            return new LightningWhipGeometry(result);
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Could not load the supplied lightning hilt", e);
        }
    }

    private static void addFace(List<Face> out, Map<String, Vector3f> positions, JsonObject uv,
            JsonArray order, int[] indices, float width, float height, boolean emissive) {
        Vertex[] vertices = new Vertex[4];
        for (int i = 0; i < 4; i++) {
            String key = order.get(indices[i]).getAsString();
            Vector3f p = Objects.requireNonNull(positions.get(key));
            JsonArray coord = uv.getAsJsonArray(key);
            vertices[i] = new Vertex(p.x, p.y, p.z, coord.get(0).getAsFloat()/width, coord.get(1).getAsFloat()/height);
        }
        Vertex a = vertices[0], b = vertices[1], c = vertices[2];
        Vector3f normal = new Vector3f(b.x-a.x,b.y-a.y,b.z-a.z).cross(new Vector3f(c.x-a.x,c.y-a.y,c.z-a.z));
        if (normal.lengthSquared() < 1e-16f) return;
        out.add(new Face(vertices, normal.normalize(), emissive));
    }
    private static Vector3f vec(JsonArray a) { return a == null ? new Vector3f() : new Vector3f(a.get(0).getAsFloat(),a.get(1).getAsFloat(),a.get(2).getAsFloat()); }
    private record Vertex(float x, float y, float z, float u, float v) {}
    private record Face(Vertex[] vertices, Vector3f normal, boolean emissive) {}
}
