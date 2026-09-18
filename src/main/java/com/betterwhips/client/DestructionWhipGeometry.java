package com.betterwhips.client;

import com.betterwhips.physics.DestructionWhipDimensions;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntUnaryOperator;

final class DestructionWhipGeometry {
    static final int SEGMENT_COUNT = DestructionWhipDimensions.SEGMENT_COUNT;
    static final float MODEL_UNIT = 1.0F / 16.0F;
    static final float BASE_TEXTURE_SIZE = 512.0F;

    static final float PULSE_FRAME_TEXTURE_SIZE = 256.0F;
    static final int PULSE_FRAME_COUNT = 40;

    private static final float ENERGY_GRADIENT_HALF_WIDTH = 0.0500F;
    static final float CHAIN_PIVOT_Y = 6.5F * MODEL_UNIT;

    private static final float HANDLE_VISUAL_CENTER_Y = 1.85F * MODEL_UNIT;
    static final float[] AUTHORED_PIVOT_Z = DestructionWhipDimensions.authoredPivotZFloatCopy();
    static final float[] REST_LENGTHS = buildRestLengths();

    static final double[] SEGMENT_COLLIDER_RADIUS = {
            0.0800D, 0.0785D, 0.0770D, 0.0755D, 0.0740D, 0.0725D,
            0.0710D, 0.0695D, 0.0680D, 0.0665D, 0.0650D, 0.0635D,
            0.0620D, 0.0605D, 0.0590D, 0.0575D, 0.0560D, 0.0545D,
            0.0530D, 0.0515D, 0.0505D, 0.0498D, 0.0492D, 0.0487D
    };

    private static final String MODEL_RESOURCE =
            "/assets/better_whips/models/entity/destruction_whip.bbmodel";
    static final DestructionWhipGeometry INSTANCE = load();

    private final List<CubeGeometry> handle;
    private final List<CubeGeometry> handlePulse;
    private final List<List<CubeGeometry>> segments;
    private final List<List<CubeGeometry>> segmentsPulse;
    private final List<CubeGeometry> tip;
    private final List<CubeGeometry> tipPulse;
    private final GroupInfo rootGroup;
    private final GroupInfo handleGroup;
    private final GroupInfo chainGroup;
    private final List<GroupInfo> segmentGroups;
    private final GroupInfo tipGroup;
    private final Quaternionf dynamicParentRotation;
    private final Vector3f dynamicParentForward;

    private DestructionWhipGeometry(List<CubeGeometry> handle,
                              List<CubeGeometry> handlePulse,
                              List<List<CubeGeometry>> segments,
                              List<List<CubeGeometry>> segmentsPulse,
                              List<CubeGeometry> tip,
                              List<CubeGeometry> tipPulse,
                              GroupInfo rootGroup,
                              GroupInfo handleGroup,
                              GroupInfo chainGroup,
                              List<GroupInfo> segmentGroups,
                              GroupInfo tipGroup) {
        this.handle = handle;
        this.handlePulse = handlePulse;
        this.segments = segments;
        this.segmentsPulse = segmentsPulse;
        this.tip = tip;
        this.tipPulse = tipPulse;
        this.rootGroup = rootGroup;
        this.handleGroup = handleGroup;
        this.chainGroup = chainGroup;
        this.segmentGroups = segmentGroups;
        this.tipGroup = tipGroup;
        this.dynamicParentRotation = composeRotations(rootGroup, handleGroup);
        this.dynamicParentForward = new Vector3f(0.0F, 0.0F, -1.0F);
        this.dynamicParentRotation.transform(this.dynamicParentForward);
        if (this.dynamicParentForward.lengthSquared() > 1.0E-8F) {
            this.dynamicParentForward.normalize();
        }
    }

    static void applyAuthoredRootTransform(PoseStack stack) {

        INSTANCE.applyBoneTransform(stack, INSTANCE.rootGroup);
        INSTANCE.applyBoneTransform(stack, INSTANCE.handleGroup);
    }

    Quaternionf dynamicRotationTo(Vector3f direction) {
        Vector3f target = new Vector3f(direction);
        if (target.lengthSquared() < 1.0E-8F) {
            return new Quaternionf(dynamicParentRotation);
        }
        target.normalize();
        Quaternionf align = new Quaternionf().rotationTo(dynamicParentForward, target);
        return align.mul(new Quaternionf(dynamicParentRotation));
    }

    private static Quaternionf composeRotations(GroupInfo... groups) {
        Quaternionf result = new Quaternionf();
        for (GroupInfo group : groups) {
            if (group == null || !hasRotation(group.rotation)) {
                continue;
            }
            result.mul(new Quaternionf().rotationXYZ(
                    (float) Math.toRadians(group.rotation[0]),
                    (float) Math.toRadians(group.rotation[1]),
                    (float) Math.toRadians(group.rotation[2])));
        }
        return result;
    }

    private void applyBoneTransform(PoseStack stack, GroupInfo group) {
        if (group == null || !hasRotation(group.rotation)) {
            return;
        }
        float ox = group.origin[0] * MODEL_UNIT;
        float oy = group.origin[1] * MODEL_UNIT;
        float oz = group.origin[2] * MODEL_UNIT;
        stack.translate(ox, oy, oz);
        stack.mulPose(new Quaternionf().rotationXYZ(
                (float) Math.toRadians(group.rotation[0]),
                (float) Math.toRadians(group.rotation[1]),
                (float) Math.toRadians(group.rotation[2])));
        stack.translate(-ox, -oy, -oz);
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

    void renderHandlePulse(PoseStack stack, VertexConsumer consumer, int light, int overlay,
                           int frame) {
        renderHandlePulse(stack, consumer, light, overlay, frame, 220);
    }

    void renderHandlePulse(PoseStack stack, VertexConsumer consumer, int light, int overlay,
                           int frame, int alpha) {
        renderHandleEnergy(stack, consumer, light, overlay, 255, 142, 36, alpha);
    }

    void renderHandleEnergy(PoseStack stack, VertexConsumer consumer, int light, int overlay,
                            int red, int green, int blue, int alpha) {
        renderPulse(handlePulse, false, stack, consumer, light, overlay,
                0, alpha, red, green, blue);
    }

    void renderHandleEnergyGradient(PoseStack stack, VertexConsumer consumer, int light, int overlay,
                                    int leftRed, int leftGreen, int leftBlue,
                                    int rightRed, int rightGreen, int rightBlue, int alpha) {
        renderPulseGradient(handlePulse, false, stack, consumer, light, overlay, alpha,
                leftRed, leftGreen, leftBlue, rightRed, rightGreen, rightBlue);
    }

    void renderHandlePulseScaledAboutCenter(PoseStack stack, VertexConsumer consumer,
                                            int light, int overlay, float scale,
                                            int frame, int alpha) {
        stack.pushPose();
        stack.translate(0.0F, HANDLE_VISUAL_CENTER_Y, 0.0F);
        stack.scale(scale, scale, scale);
        stack.translate(0.0F, -HANDLE_VISUAL_CENTER_Y, 0.0F);
        renderPulse(handlePulse, false, stack, consumer, light, overlay, frame, alpha);
        stack.popPose();
    }

    void renderAuthoredLash(PoseStack stack, VertexConsumer consumer, int light, int overlay) {
        renderAuthoredLash(stack, consumer, light, overlay, 255, 255, 255, 255);
    }

    void renderAuthoredLash(PoseStack stack, VertexConsumer consumer, int light, int overlay,
                            int red, int green, int blue, int alpha) {
        stack.pushPose();
        applyBoneTransform(stack, chainGroup);

        for (int i = 0; i < segments.size(); ++i) {
            applyBoneTransform(stack, segmentGroups.get(i));
            render(segments.get(i), false, stack, consumer, light, overlay, red, green, blue, alpha);
        }
        applyBoneTransform(stack, tipGroup);
        render(tip, false, stack, consumer, light, overlay, red, green, blue, alpha);
        stack.popPose();
    }

    void renderAuthoredLashScaledPerPart(PoseStack stack, VertexConsumer consumer,
                                         int light, int overlay, float scale,
                                         int red, int green, int blue, int alpha) {
        stack.pushPose();
        applyBoneTransform(stack, chainGroup);
        for (int i = 0; i < segments.size(); ++i) {
            applyBoneTransform(stack, segmentGroups.get(i));
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
        applyBoneTransform(stack, tipGroup);
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

    void renderAuthoredLashPulse(PoseStack stack, VertexConsumer consumer, int light,
                                 int overlay, int frame) {
        renderAuthoredLashPulse(stack, consumer, light, overlay, frame, 220);
    }

    void renderAuthoredLashPulse(PoseStack stack, VertexConsumer consumer, int light,
                                 int overlay, int frame, int alpha) {
        renderAuthoredLashEnergy(stack, consumer, light, overlay, 255, 142, 36, alpha);
    }

    void renderAuthoredLashEnergy(PoseStack stack, VertexConsumer consumer, int light,
                                  int overlay, int red, int green, int blue, int alpha) {
        stack.pushPose();
        applyBoneTransform(stack, chainGroup);
        for (int i = 0; i < segmentsPulse.size(); ++i) {
            applyBoneTransform(stack, segmentGroups.get(i));
            renderPulse(segmentsPulse.get(i), false, stack, consumer, light, overlay,
                    0, alpha, red, green, blue);
        }
        applyBoneTransform(stack, tipGroup);
        renderPulse(tipPulse, false, stack, consumer, light, overlay,
                0, alpha, red, green, blue);
        stack.popPose();
    }

    void renderAuthoredLashEnergyGradient(PoseStack stack, VertexConsumer consumer, int light,
                                          int overlay, int leftRed, int leftGreen, int leftBlue,
                                          int rightRed, int rightGreen, int rightBlue, int alpha) {
        stack.pushPose();
        applyBoneTransform(stack, chainGroup);
        for (int i = 0; i < segmentsPulse.size(); ++i) {
            applyBoneTransform(stack, segmentGroups.get(i));
            renderPulseGradient(segmentsPulse.get(i), false, stack, consumer, light, overlay, alpha,
                    leftRed, leftGreen, leftBlue, rightRed, rightGreen, rightBlue);
        }
        applyBoneTransform(stack, tipGroup);
        renderPulseGradient(tipPulse, false, stack, consumer, light, overlay, alpha,
                leftRed, leftGreen, leftBlue, rightRed, rightGreen, rightBlue);
        stack.popPose();
    }

    void renderAuthoredLashEnergyBySegment(PoseStack stack, VertexConsumer consumer, int light,
                                           int overlay, int red, int green, int blue,
                                           IntUnaryOperator alphaBySegment) {
        stack.pushPose();
        applyBoneTransform(stack, chainGroup);
        int lastAlpha = 0;
        for (int i = 0; i < segmentsPulse.size(); ++i) {
            applyBoneTransform(stack, segmentGroups.get(i));
            int alpha = Math.max(0, Math.min(255, alphaBySegment.applyAsInt(i)));
            lastAlpha = alpha;
            if (alpha > 0) {
                renderPulse(segmentsPulse.get(i), false, stack, consumer, light, overlay,
                        0, alpha, red, green, blue);
            }
        }
        applyBoneTransform(stack, tipGroup);
        if (lastAlpha > 0) {
            renderPulse(tipPulse, false, stack, consumer, light, overlay,
                    0, lastAlpha, red, green, blue);
        }
        stack.popPose();
    }

    void renderAuthoredLashPulseScaledPerPart(PoseStack stack, VertexConsumer consumer,
                                              int light, int overlay, float scale,
                                              int frame, int alpha) {
        stack.pushPose();
        applyBoneTransform(stack, chainGroup);
        for (int i = 0; i < segmentsPulse.size(); ++i) {
            applyBoneTransform(stack, segmentGroups.get(i));
            float centerZ = (AUTHORED_PIVOT_Z[i] + AUTHORED_PIVOT_Z[i + 1])
                    * 0.5F * MODEL_UNIT;
            stack.pushPose();
            stack.translate(0.0F, CHAIN_PIVOT_Y, centerZ);
            stack.scale(scale, scale, scale);
            stack.translate(0.0F, -CHAIN_PIVOT_Y, -centerZ);
            renderPulse(segmentsPulse.get(i), false, stack, consumer,
                    light, overlay, frame, alpha);
            stack.popPose();
        }
        applyBoneTransform(stack, tipGroup);
        if (!tipPulse.isEmpty()) {
            float tipZ = AUTHORED_PIVOT_Z[SEGMENT_COUNT] * MODEL_UNIT;
            stack.pushPose();
            stack.translate(0.0F, CHAIN_PIVOT_Y, tipZ);
            stack.scale(scale, scale, scale);
            stack.translate(0.0F, -CHAIN_PIVOT_Y, -tipZ);
            renderPulse(tipPulse, false, stack, consumer, light, overlay, frame, alpha);
            stack.popPose();
        }
        stack.popPose();
    }

    void renderStoredCoil(PoseStack stack, VertexConsumer consumer, int light, int overlay) {
        renderAuthoredLash(stack, consumer, light, overlay);
    }

    void renderStoredCoilPulse(PoseStack stack, VertexConsumer consumer, int light, int overlay,
                               int frame) {
        renderStoredCoilPulse(stack, consumer, light, overlay, frame, 220);
    }

    void renderStoredCoilPulse(PoseStack stack, VertexConsumer consumer, int light, int overlay,
                               int frame, int alpha) {
        renderAuthoredLashPulse(stack, consumer, light, overlay, frame, alpha);
    }

    void renderStoredCoilPulseScaledPerPart(PoseStack stack, VertexConsumer consumer,
                                            int light, int overlay, float scale,
                                            int frame, int alpha) {
        renderAuthoredLashPulseScaledPerPart(
                stack, consumer, light, overlay, scale, frame, alpha);
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
        applyBoneTransformRelativeToPivot(stack, segmentGroups.get(index));
        render(segments.get(index), true, stack, consumer, light, overlay,
                red, green, blue, alpha);
        if (index == SEGMENT_COUNT - 1 && !tip.isEmpty()) {
            stack.pushPose();
            stack.translate(0.0D, 0.0D, -REST_LENGTHS[index]);
            render(tip, true, stack, consumer, light, overlay, red, green, blue, alpha);
            stack.popPose();
        }
    }

    void renderDynamicSegmentPulse(int index, PoseStack stack, VertexConsumer consumer,
                                  int light, int overlay, int frame) {
        renderDynamicSegmentPulse(index, stack, consumer, light, overlay, frame, 220);
    }

    void renderDynamicSegmentPulse(int index, PoseStack stack, VertexConsumer consumer,
                                   int light, int overlay, int frame, int alpha) {
        renderDynamicSegmentEnergy(index, stack, consumer, light, overlay,
                255, 142, 36, alpha);
    }

    void renderDynamicSegmentEnergy(int index, PoseStack stack, VertexConsumer consumer,
                                    int light, int overlay,
                                    int red, int green, int blue, int alpha) {
        if (index < 0 || index >= segmentsPulse.size()) {
            return;
        }
        stack.pushPose();

        applyBoneTransformRelativeToPivot(stack, segmentGroups.get(index));
        renderPulse(segmentsPulse.get(index), true, stack, consumer, light, overlay,
                0, alpha, red, green, blue);
        if (index == SEGMENT_COUNT - 1 && !tipPulse.isEmpty()) {
            stack.pushPose();
            stack.translate(0.0D, 0.0D, -REST_LENGTHS[index]);
            renderPulse(tipPulse, true, stack, consumer, light, overlay,
                    0, alpha, red, green, blue);
            stack.popPose();
        }
        stack.popPose();
    }

    void renderDynamicSegmentEnergyGradient(int index, PoseStack stack, VertexConsumer consumer,
                                            int light, int overlay,
                                            int leftRed, int leftGreen, int leftBlue,
                                            int rightRed, int rightGreen, int rightBlue, int alpha) {
        if (index < 0 || index >= segmentsPulse.size()) {
            return;
        }
        stack.pushPose();
        applyBoneTransformRelativeToPivot(stack, segmentGroups.get(index));
        renderPulseGradient(segmentsPulse.get(index), true, stack, consumer, light, overlay, alpha,
                leftRed, leftGreen, leftBlue, rightRed, rightGreen, rightBlue);
        if (index == SEGMENT_COUNT - 1 && !tipPulse.isEmpty()) {
            stack.pushPose();
            stack.translate(0.0D, 0.0D, -REST_LENGTHS[index]);
            renderPulseGradient(tipPulse, true, stack, consumer, light, overlay, alpha,
                    leftRed, leftGreen, leftBlue, rightRed, rightGreen, rightBlue);
            stack.popPose();
        }
        stack.popPose();
    }

    private void applyBoneTransformRelativeToPivot(PoseStack stack, GroupInfo group) {
        if (group == null || !hasRotation(group.rotation)) {
            return;
        }

        stack.mulPose(new Quaternionf().rotationXYZ(
                (float) Math.toRadians(group.rotation[0]),
                (float) Math.toRadians(group.rotation[1]),
                (float) Math.toRadians(group.rotation[2])));
    }

    void renderDynamicSegmentPulseScaledAboutCenter(int index, PoseStack stack,
                                                    VertexConsumer consumer, int light,
                                                    int overlay, float scale,
                                                    int frame, int alpha) {
        if (index < 0 || index >= segmentsPulse.size()) {
            return;
        }
        float centerZ = -REST_LENGTHS[index] * 0.5F;
        stack.pushPose();
        stack.translate(0.0F, 0.0F, centerZ);
        stack.scale(scale, scale, scale);
        stack.translate(0.0F, 0.0F, -centerZ);
        renderPulse(segmentsPulse.get(index), true, stack, consumer,
                light, overlay, frame, alpha);
        if (index == SEGMENT_COUNT - 1 && !tipPulse.isEmpty()) {
            stack.pushPose();
            stack.translate(0.0D, 0.0D, -REST_LENGTHS[index]);
            renderPulse(tipPulse, true, stack, consumer, light, overlay, frame, alpha);
            stack.popPose();
        }
        stack.popPose();
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

    private static void renderPulse(List<CubeGeometry> cubes, boolean relative,
                                    PoseStack stack, VertexConsumer consumer, int light,
                                    int overlay, int frame) {
        renderPulse(cubes, relative, stack, consumer, light, overlay, frame, 220);
    }

    private static void renderPulse(List<CubeGeometry> cubes, boolean relative,
                                    PoseStack stack, VertexConsumer consumer, int light,
                                    int overlay, int frame, int alpha) {
        renderPulse(cubes, relative, stack, consumer, light, overlay,
                frame, alpha, 255, 142, 36);
    }

    private static void renderPulse(List<CubeGeometry> cubes, boolean relative,
                                    PoseStack stack, VertexConsumer consumer, int light,
                                    int overlay, int frame, int alpha,
                                    int red, int green, int blue) {
        int wrappedFrame = Math.floorMod(frame, PULSE_FRAME_COUNT);
        for (int detailIndex = 0; detailIndex < cubes.size(); ++detailIndex) {
            if (!WhipPerformanceTuning.shouldRenderDetailElement(detailIndex, cubes.size())) {
                continue;
            }
            CubeGeometry cube = cubes.get(detailIndex);
            cube.renderPulse(relative, stack, consumer, light, overlay,
                    wrappedFrame, alpha, red, green, blue);
        }
    }

    private static void renderPulseGradient(List<CubeGeometry> cubes, boolean relative,
                                            PoseStack stack, VertexConsumer consumer, int light,
                                            int overlay, int alpha,
                                            int leftRed, int leftGreen, int leftBlue,
                                            int rightRed, int rightGreen, int rightBlue) {
        for (int detailIndex = 0; detailIndex < cubes.size(); ++detailIndex) {
            if (!WhipPerformanceTuning.shouldRenderDetailElement(detailIndex, cubes.size())) {
                continue;
            }
            CubeGeometry cube = cubes.get(detailIndex);
            cube.renderPulseGradient(relative, stack, consumer, light, overlay, alpha,
                    leftRed, leftGreen, leftBlue, rightRed, rightGreen, rightBlue);
        }
    }

    private static DestructionWhipGeometry load() {
        try (var stream = DestructionWhipGeometry.class.getResourceAsStream(MODEL_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing Destruction Whip model: " + MODEL_RESOURCE);
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
            List<CubeGeometry> handlePulse = new ArrayList<>();
            List<CubeGeometry> tip = new ArrayList<>();
            List<CubeGeometry> tipPulse = new ArrayList<>();
            List<List<CubeGeometry>> segments = new ArrayList<>(SEGMENT_COUNT);
            List<List<CubeGeometry>> segmentsPulse = new ArrayList<>(SEGMENT_COUNT);
            for (int i = 0; i < SEGMENT_COUNT; ++i) {
                segments.add(new ArrayList<>());
                segmentsPulse.add(new ArrayList<>());
            }

            for (JsonElement raw : root.getAsJsonArray("elements")) {
                JsonObject element = raw.getAsJsonObject();
                GroupInfo group = elementGroups.get(element.get("uuid").getAsString());
                if (group == null) {
                    continue;
                }
                String type = element.get("type").getAsString();
                CubeGeometry geometry;
                CubeGeometry pulseGeometry;
                if ("cube".equals(type)) {
                    geometry = readCube(element, group.origin, 0, BASE_TEXTURE_SIZE);
                    pulseGeometry = readCube(element, group.origin, 1, PULSE_FRAME_TEXTURE_SIZE);
                } else if ("mesh".equals(type)) {

                    geometry = readMesh(element, group.origin, 0, BASE_TEXTURE_SIZE);
                    pulseGeometry = readMesh(element, group.origin, 1, PULSE_FRAME_TEXTURE_SIZE);
                } else {
                    continue;
                }
                if ("handle".equals(group.name)) {
                    if (!geometry.isEmpty()) {
                        handle.add(geometry);
                    }
                    if (!pulseGeometry.isEmpty()) {
                        handlePulse.add(pulseGeometry);
                    }
                    continue;
                }
                if ("whip_tip".equals(group.name)) {
                    if (!geometry.isEmpty()) {
                        tip.add(geometry);
                    }
                    if (!pulseGeometry.isEmpty()) {
                        tipPulse.add(pulseGeometry);
                    }
                    continue;
                }
                int index = segmentIndex(group.name);
                if (index >= 0) {
                    if (!geometry.isEmpty()) {
                        segments.get(index).add(geometry);
                    }
                    if (!pulseGeometry.isEmpty()) {
                        segmentsPulse.get(index).add(pulseGeometry);
                    }
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
            List<List<CubeGeometry>> immutablePulseSegments = new ArrayList<>(SEGMENT_COUNT);
            for (int i = 0; i < SEGMENT_COUNT; ++i) {
                immutableSegments.add(List.copyOf(segments.get(i)));
                immutablePulseSegments.add(List.copyOf(segmentsPulse.get(i)));
            }
            GroupInfo rootGroup = findGroup(groups, "slime_whip_root");
            GroupInfo handleGroup = findGroup(groups, "handle");
            GroupInfo chainGroup = findGroup(groups, "whip_chain");
            List<GroupInfo> segmentGroups = new ArrayList<>(SEGMENT_COUNT);
            for (int i = 0; i < SEGMENT_COUNT; ++i) {
                segmentGroups.add(findGroup(groups,
                        String.format(java.util.Locale.ROOT, "whip_segment_%02d", i + 1)));
            }
            GroupInfo tipGroup = findGroup(groups, "whip_tip");
            return new DestructionWhipGeometry(List.copyOf(handle), List.copyOf(handlePulse),
                    List.copyOf(immutableSegments), List.copyOf(immutablePulseSegments),
                    List.copyOf(tip), List.copyOf(tipPulse),
                    rootGroup, handleGroup, chainGroup, List.copyOf(segmentGroups), tipGroup);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to load Destruction Whip Blockbench geometry",
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
        requireVector(chain.origin, 0.0F, 6.5F, 0.0F, "whip_chain pivot");

        for (int i = 0; i < SEGMENT_COUNT; ++i) {
            String name = String.format(java.util.Locale.ROOT, "whip_segment_%02d", i + 1);
            GroupInfo group = findGroup(groups, name);
            requireVector(group.origin, 0.0F, 6.5F, AUTHORED_PIVOT_Z[i], name + " pivot");
        }
        GroupInfo tipGroup = findGroup(groups, "whip_tip");
        requireVector(tipGroup.origin, 0.0F, 6.5F, AUTHORED_PIVOT_Z[SEGMENT_COUNT], "whip_tip pivot");
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

    private static CubeGeometry readCube(JsonObject element, float[] pivot, int textureId, float textureSize) {
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
                if (textureMatches(face, textureId)) {
                    faces.add(cubeFace(entry.getKey(), from, to, pivot,
                            elementOrigin, elementRotation,
                            uvRect(face.getAsJsonArray("uv")), textureSize));
                }
            }
        }
        return new CubeGeometry(List.copyOf(faces));
    }

    private static CubeGeometry readMesh(JsonObject element, float[] pivot,
                                         int textureId, float textureSize) {
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
                if (textureMatches(face, textureId)) {
                    faces.add(meshFace(order, uvObject, vertices, pivot, textureSize));
                }
            } else {
                for (int i = 1; i < order.size() - 1; ++i) {
                    JsonArray triangle = new JsonArray();
                    triangle.add(order.get(0));
                    triangle.add(order.get(i));
                    triangle.add(order.get(i + 1));
                    if (textureMatches(face, textureId)) {
                        faces.add(meshFace(triangle, uvObject, vertices, pivot, textureSize));
                    }
                }
            }
        }
        return new CubeGeometry(List.copyOf(faces));
    }

    private static FaceGeometry meshFace(JsonArray order, JsonObject uvObject,
                                         Map<String, float[]> vertices, float[] pivot,
                                         float textureSize) {
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
                u = uv.get(0).getAsFloat() / textureSize;
                v = uv.get(1).getAsFloat() / textureSize;
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
                                         float[] elementRotation, float[] uv,
                                         float textureSize) {
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

        float u0 = uv[0] / textureSize;
        float v0 = uv[1] / textureSize;
        float u1 = uv[2] / textureSize;
        float v1 = uv[3] / textureSize;
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

    private static boolean textureMatches(JsonObject face, int expectedTexture) {
        if (!face.has("texture")) {
            return expectedTexture == 0;
        }
        try {
            String raw = face.get("texture").getAsString();
            if (raw.startsWith("#")) {
                raw = raw.substring(1);
            }
            return Integer.parseInt(raw) == expectedTexture;
        } catch (Exception ignored) {
            return expectedTexture == 0;
        }
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
        boolean isEmpty() {
            return faces.isEmpty();
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

        void renderPulse(boolean relative, PoseStack stack, VertexConsumer consumer,
                         int light, int overlay, int frame) {
            renderPulse(relative, stack, consumer, light, overlay, frame, 220);
        }

        void renderPulse(boolean relative, PoseStack stack, VertexConsumer consumer,
                         int light, int overlay, int frame, int alpha) {
            renderPulse(relative, stack, consumer, light, overlay,
                    frame, alpha, 255, 142, 36);
        }

        void renderPulse(boolean relative, PoseStack stack, VertexConsumer consumer,
                         int light, int overlay, int frame, int alpha,
                         int red, int green, int blue) {
            for (FaceGeometry face : faces) {
                face.renderPulse(relative, stack, consumer, light, overlay,
                        frame, alpha, red, green, blue);
            }
        }

        void renderPulseGradient(boolean relative, PoseStack stack, VertexConsumer consumer,
                                 int light, int overlay, int alpha,
                                 int leftRed, int leftGreen, int leftBlue,
                                 int rightRed, int rightGreen, int rightBlue) {
            for (FaceGeometry face : faces) {
                face.renderPulseGradient(relative, stack, consumer, light, overlay, alpha,
                        leftRed, leftGreen, leftBlue, rightRed, rightGreen, rightBlue);
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

        void renderPulse(boolean useRelative, PoseStack stack, VertexConsumer consumer,
                         int light, int overlay, int frame) {
            renderPulse(useRelative, stack, consumer, light, overlay, frame, 220);
        }

        void renderPulse(boolean useRelative, PoseStack stack, VertexConsumer consumer,
                         int light, int overlay, int frame, int alpha) {
            renderPulse(useRelative, stack, consumer, light, overlay,
                    frame, alpha, 255, 142, 36);
        }

        void renderPulse(boolean useRelative, PoseStack stack, VertexConsumer consumer,
                         int light, int overlay, int frame, int alpha,
                         int red, int green, int blue) {
            PoseStack.Pose pose = stack.last();
            VertexData[] vertices = useRelative ? relative : absolute;
            int clampedAlpha = Math.max(0, Math.min(255, alpha));
            int r = Math.max(0, Math.min(255, red));
            int g = Math.max(0, Math.min(255, green));
            int b = Math.max(0, Math.min(255, blue));
            for (VertexData vertex : vertices) {
                consumer.addVertex(pose.pose(), vertex.x, vertex.y, vertex.z)
                        .setColor(r, g, b, clampedAlpha)
                        .setUv(0.5F, 0.5F)
                        .setOverlay(overlay)
                        .setLight(light)
                        .setNormal(pose, nx, ny, nz);
            }
        }

        void renderPulseGradient(boolean useRelative, PoseStack stack, VertexConsumer consumer,
                                 int light, int overlay, int alpha,
                                 int leftRed, int leftGreen, int leftBlue,
                                 int rightRed, int rightGreen, int rightBlue) {
            PoseStack.Pose pose = stack.last();
            VertexData[] vertices = useRelative ? relative : absolute;
            int clampedAlpha = Math.max(0, Math.min(255, alpha));
            for (VertexData vertex : vertices) {
                float t = Mth.clamp((vertex.x + ENERGY_GRADIENT_HALF_WIDTH)
                        / (ENERGY_GRADIENT_HALF_WIDTH * 2.0F), 0.0F, 1.0F);
                t = t * t * (3.0F - 2.0F * t);
                int r = Mth.clamp(Math.round(leftRed + (rightRed - leftRed) * t), 0, 255);
                int g = Mth.clamp(Math.round(leftGreen + (rightGreen - leftGreen) * t), 0, 255);
                int b = Mth.clamp(Math.round(leftBlue + (rightBlue - leftBlue) * t), 0, 255);
                consumer.addVertex(pose.pose(), vertex.x, vertex.y, vertex.z)
                        .setColor(r, g, b, clampedAlpha)
                        .setUv(0.5F, 0.5F)
                        .setOverlay(overlay)
                        .setLight(light)
                        .setNormal(pose, nx, ny, nz);
            }
        }
    }

    private record VertexData(float x, float y, float z, float u, float v) {}
}
