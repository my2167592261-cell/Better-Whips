package com.betterwhips.client;

import com.betterwhips.client.LightningWhipGeometry;
import com.betterwhips.client.ShaderCompat;
import com.betterwhips.client.WhipCoilPose;
import com.betterwhips.client.WhipFirstPersonFov;
import com.betterwhips.client.WhipPerformanceTuning;
import com.betterwhips.item.LightningWhipItem;
import com.betterwhips.network.LightningWhipNetwork;
import com.betterwhips.physics.WhipBlockCollision;
import com.betterwhips.physics.WhipMomentumContinuity;
import com.betterwhips.registry.ModItems;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderArmEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class LightningWhipPhysics {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("better_whips", "textures/item/lightning_whip.png");
    private static final RenderType RENDER_TYPE = LightningWhipVfx.RENDER_TYPE;
    private static final RenderType TIP_TRAIL_RENDER_TYPE = LightningWhipVfx.RENDER_TYPE;
    private static final int SEGMENTS = 56;
    private static final int POINTS = 57;
    private static final int MIN_SUBSTEPS = 8;
    private static final int MAX_SUBSTEPS = 24;
    private static final int SOLVER_ITERATIONS = 5;
    private static final int HIGH_FIDELITY_TICKS = 28;
    private static final double TICK_SECONDS = 0.05;
    private static final double PHYSICAL_REST_SCALE = 1.0;
    private static final double MAX_SEGMENT_STRETCH = 1.0030;
    private static final double LENGTH_COMPLIANCE = 3.0E-8;
    private static final double BEND_COMPLIANCE = 5.2E-7;

    private static final double TICK_VELOCITY_RETENTION = 0.9975;
    private static final double GRAVITY = 0.0;
    private static final double COLLISION_RADIUS = 0.025;
    private static final int DEPENETRATION_PASSES = 8;
    private static final double DEPENETRATION_EPSILON = 1.0E-4;
    private static final double SELF_COLLISION_DISTANCE = 0.040;
    private static final double SURFACE_TANGENT_RETENTION_PER_TICK = 0.96;
    private static final double HANDLE_ROOT_MAX_ANGLE = Math.toRadians(16.0);
    private static final int HANDLE_BEND_SEGMENTS = 4;
    private static final double HANDLE_BEND_STIFFNESS_MULTIPLIER = 1.45;
    private static final double MAX_CAPTURE_DISTANCE_SQR = 16.0;
    private static final float ARM_REST_PITCH = -0.38f;
    private static final float ARM_SPRING = 0.2f;
    private static final float ARM_DAMPING = 0.72f;
    private static final int RIGHT_CHARGE_TICKS = 60;
    private static final int RIGHT_SLAM_TICKS = 14;
    private static final float RIGHT_SPIN_TURNS_AT_FULL_CHARGE = 4.0f;
    private static final double CHARGE_SPIN_SIGN = 1.0;
    private static final double CHARGE_CENTRIFUGAL_SCALE = 0.12;
    private static final double CHARGE_MAX_RADIAL_ACCEL = 150.0;
    private static final double CHARGE_MAX_TANGENTIAL_ACCEL = 90.0;
    private static final double RELEASE_FORCE_STIFFNESS = 150.0;
    private static final double RELEASE_MAX_ACCEL = 240.0;
    private static final float RIGHT_TIP_MAX_SCALE = 10.0f;
    private static final int PRECISION_WINDUP_TICKS = 1;
    private static final int PRECISION_STROKE_TICKS = 3;
    private static final float PRECISION_RELEASE_RAW = 0.3f;

    private static final float PRECISION_GUIDE_END = 0.72f;
    private static final double PRECISION_GUIDE_POSITION_GAIN = 0.2175;
    private static final double PRECISION_GUIDE_VELOCITY_TURN = 0.48;

    private static final double PRECISION_FOLLOW_TOTAL_DELAY_SECONDS = 0.12;

    private static final double PRECISION_FOLLOW_PER_LINK_TAU =
            PRECISION_FOLLOW_TOTAL_DELAY_SECONDS / SEGMENTS;

    private static final double PRECISION_FOLLOW_POSITION_ACCEL = 3400.0;
    private static final double PRECISION_FOLLOW_VELOCITY_ACCEL = 72.0;
    private static final double PRECISION_FOLLOW_RADIAL_ACCEL = 2200.0;
    private static final double PRECISION_FOLLOW_MAX_ACCEL = 24000.0;
    private static final double PRECISION_FOLLOW_TIP_GAIN = 1.38;
    private static final double PRECISION_CROSSHAIR_SOURCE_PROGRESS = 0.30;
    private static final double PRECISION_CROSSHAIR_SWEEP_HALF_SPAN_PROGRESS = 0.34;
    private static final double PRECISION_CROSSHAIR_SWEEP_HALF_ANGLE_RADIANS = Math.toRadians(68.0);
    private static final double PRECISION_ATTACK_SECONDS = 0.25;
    private static final double PRECISION_CROSSHAIR_GATE_WIDTH_PROGRESS = 0.095;
    private static final double PRECISION_CROSSHAIR_GATE_ACCEL = 14500.0;

    private static final double PRECISION_TANGENTIAL_ACCEL = 7600.0;

    private static final double PRECISION_CENTRIFUGAL_ACCEL = 3000.0;
    private static final double PRECISION_TAIL_CROSSHAIR_RELEASE = 0.86;

    private static final double FIRST_PERSON_LIGHTNING_DARK_DISTANCE = 0.28;
    private static final double FIRST_PERSON_LIGHTNING_FULL_DISTANCE = 1.10;
    private static final float FIRST_PERSON_LIGHTNING_DISTANCE_MIN_VISIBILITY = 0.08f;

    private static final double FIRST_PERSON_CROSSHAIR_DIM_INNER_ANGLE_RADIANS = Math.toRadians(30.0);
    private static final double FIRST_PERSON_CROSSHAIR_FADE_OUTER_ANGLE_RADIANS = Math.toRadians(45.0);
    private static final double FIRST_PERSON_CROSSHAIR_FADE_OUTER_COS = Math.cos(FIRST_PERSON_CROSSHAIR_FADE_OUTER_ANGLE_RADIANS);
    private static final float FIRST_PERSON_CROSSHAIR_MIN_VISIBILITY = 0.10f;
    private static final int COIL_IDLE_DELAY_TICKS = 100;
    private static final int COIL_DURATION_TICKS = 36;
    private static final int COIL_LEAD_SEGMENTS = 3;
    private static final double COIL_TURNS = 5.0;
    private static final double COIL_LAYER_PITCH = 0.05;
    private static final double COIL_POSITION_GAIN = 0.17;
    private static final double COIL_MAX_PULL_PER_SUBSTEP = 0.105;
    private static final double COIL_VELOCITY_DAMPING = 0.5;
    private static final double COIL_SELF_COLLISION_SCALE = 0.52;
    private static final long FIRST_PERSON_BRIDGE_MAX_AGE_TICKS = 1L;
    private static final int TIP_TRAIL_LIFETIME_TICKS = 5;
    private static final int TIP_TRAIL_MAX_SAMPLES = 32;
    private static final double TIP_TRAIL_MIN_SAMPLE_DISTANCE_SQR = 0.0012250000000000002;
    private static final double TIP_TRAIL_MIN_SPEED = 2.5;

    private static final double TIP_TRAIL_TANGENT_NECK_MIN = 0.10;
    private static final double TIP_TRAIL_TANGENT_NECK_MAX = 0.30;
    private static final double TIP_TRAIL_BRIDGE_MIN_SPAN = 0.22;
    private static final int TIP_TRAIL_BRIDGE_STEPS = 4;
    private static final float TIP_TRAIL_OUTER_HALF_WIDTH = 0.145f;
    private static final float TIP_TRAIL_CORE_HALF_WIDTH = 0.052f;
    private static final Map<StateKey, WhipState> STATES = new HashMap<StateKey, WhipState>();
    private static final Map<StateKey, AttackDrive> PENDING_ATTACKS = new HashMap<StateKey, AttackDrive>();
    private static final int MAX_PENDING_PRECISION_INPUTS = 1;
    private static final double PRECISION_REAR_PLANE_MARGIN = 0.15;
    private static final double PRECISION_REAR_REQUIRED_LENGTH_FRACTION = 0.50;
    private static final double PRECISION_RETRACTED_TIP_DISTANCE_SQR = 1.35 * 1.35;
    private static final Map<StateKey, ArrayDeque<ClientPrecisionRequest>> PENDING_PRECISION = new HashMap<StateKey, ArrayDeque<ClientPrecisionRequest>>();
    private static final ThreadLocal<ArrayDeque<RenderContext>> RENDER_CONTEXT = ThreadLocal.withInitial(ArrayDeque::new);

    private LightningWhipPhysics() {
    }

    public static void requestChargedSlam(Player player, InteractionHand hand) {
        if (player == null) {
            return;
        }
        HumanoidArm arm = LightningWhipPhysics.armForHand(player, hand);
        PENDING_ATTACKS.put(new StateKey(player.getUUID(), arm), AttackDrive.CHARGED_SLAM);
    }

    public static void cancelChargedSlam(Player player) {
        if (player == null) {
            return;
        }
        for (HumanoidArm arm : HumanoidArm.values()) {
            WhipState state;
            StateKey key = new StateKey(player.getUUID(), arm);
            if (PENDING_ATTACKS.get(key) == AttackDrive.CHARGED_SLAM) {
                PENDING_ATTACKS.remove(key);
            }
            if ((state = STATES.get(key)) == null) continue;
            state.motor.cancelChargedSlam();
        }
    }

    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        LocalPlayer player = minecraft.player;
        ItemStack stack = player.getMainHandItem();
        if (!stack.is((Item)ModItems.LIGHTNING_WHIP.get())) {
            return;
        }
        if (player.getAttackStrengthScale(0.0f) < 1.0f) {
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }
        LightningWhipPhysics.triggerPrecisionClient(player);
        event.setCanceled(true);
        event.setSwingHand(false);
    }

    private static void triggerPrecisionClient(Player player) {
        StateKey key = new StateKey(player.getUUID(), player.getMainArm());
        ArrayDeque<ClientPrecisionRequest> queue =
                PENDING_PRECISION.computeIfAbsent(key, ignored -> new ArrayDeque<>());

        if (queue.size() >= MAX_PENDING_PRECISION_INPUTS) {
            return;
        }
        WhipState state = STATES.computeIfAbsent(key,
                ignored -> new WhipState(player, player.getMainArm()));
        Vec3 clickDirection = LightningWhipPhysics.aimDirection(player);
        Vec3 handleAxis = state.capturedHandleAxis;
        WhipMomentumContinuity.Continuation continuation =
                state.precisionContinuation(clickDirection, player.level().getGameTime());
        queue.addLast(new ClientPrecisionRequest(clickDirection, handleAxis,
                continuation.active(), continuation.swingSign()));

    }

    private static void tryHeldPrecisionAttack(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.screen != null || !minecraft.options.keyAttack.isDown() || ((LivingEntity)player).isUsingItem() || !player.isAlive() || ((Player)player).isSpectator() || !player.getMainHandItem().is((Item)ModItems.LIGHTNING_WHIP.get()) || player.getAttackStrengthScale(0.0f) < 1.0f) {
            return;
        }
        LightningWhipPhysics.triggerPrecisionClient(player);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.isPaused()) {
            return;
        }
        LightningWhipPhysics.tryHeldPrecisionAttack(minecraft);
        long gameTime = level.getGameTime();
        HashSet<StateKey> seen = new HashSet<StateKey>();
        for (Player player : level.players()) {
            for (HumanoidArm arm : HumanoidArm.values()) {
                if (!LightningWhipPhysics.holdsWhip(player, arm)) continue;
                StateKey key = new StateKey(player.getUUID(), arm);
                seen.add(key);
                WhipState state = STATES.computeIfAbsent(key, ignored -> new WhipState(player, arm));
                state.tick(level, player, gameTime);
            }
        }
        Iterator<Map.Entry<StateKey, WhipState>> iterator = STATES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<StateKey, WhipState> entry = iterator.next();
            if (seen.contains(entry.getKey())) continue;
            PENDING_ATTACKS.remove(entry.getKey());
            PENDING_PRECISION.remove(entry.getKey());
            iterator.remove();
        }
    }

    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        STATES.clear();
        PENDING_ATTACKS.clear();
        PENDING_PRECISION.clear();
        RENDER_CONTEXT.remove();
    }

    private static boolean isAttachedLocalFirstPerson(Minecraft minecraft, Player holder, Camera camera) {
        if (minecraft == null || holder == null || camera == null || holder != minecraft.player || !minecraft.options.getCameraType().isFirstPerson()) {
            return false;
        }
        if (camera.getEntity() != holder) {
            return false;
        }
        return camera.getPosition().distanceToSqr(holder.getEyePosition()) <= 2.25;
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || ShaderCompat.isIrisShadowPass() || !LightningWhipVfx.ready()) return;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || STATES.isEmpty()) return;
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(RENDER_TYPE);
        long gameTime = level.getGameTime();
        for (WhipState state : STATES.values()) {
            if (!state.initialized || state.holder == null || !state.holder.isAlive()
                    || gameTime - state.lastSeenGameTime > 2L
                    || !WhipPerformanceTuning.shouldRenderMain(state.holder, camera)
                    || isAttachedLocalFirstPerson(minecraft, state.holder, event.getCamera())) continue;
            state.render(level, event.getPoseStack(), consumer, camera, partialTick);
            state.renderElectricTipTrail(event.getPoseStack(), consumer, camera, partialTick, gameTime);
        }
        buffers.endBatch(RENDER_TYPE);
}

    public static void onRenderArm(RenderArmEvent event) {
        HumanoidArm arm;
        AbstractClientPlayer player = event.getPlayer();
        if (!LightningWhipPhysics.holdsWhip(player, arm = event.getArm())) {
            return;
        }
        float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        ArmPose armPose = LightningWhipPhysics.getArmPose(player, arm, partialTick);
        float side = arm == HumanoidArm.RIGHT ? 1.0f : -1.0f;
        float pitchDelta = armPose.pitch - -0.38f;
        PoseStack stack = event.getPoseStack();
        WhipFirstPersonFov.applyToViewModel(stack);
        stack.translate(side * 0.025f, -0.035f, 0.0f);
        stack.mulPose(Axis.ZP.rotation(armPose.roll * 0.5f));
        stack.mulPose(Axis.YP.rotation(armPose.yaw * 0.45f));
        stack.mulPose(Axis.XP.rotation(pitchDelta * 0.55f));
    }

    public static void applyFirstPersonItemTransform(PoseStack stack, Player player, HumanoidArm arm, float partialTick, float equipProgress) {
        ArmPose armPose = LightningWhipPhysics.getArmPose(player, arm, partialTick);
        float side = arm == HumanoidArm.RIGHT ? 1.0f : -1.0f;
        float pitchDelta = armPose.pitch - -0.38f;
        WhipFirstPersonFov.applyToViewModel(stack);
        stack.translate(side * 0.56f, -0.52f - equipProgress * 0.6f, -0.72f);
        stack.translate(side * 0.025f, 0.055f, -0.035f);
        stack.mulPose(Axis.YP.rotationDegrees(side * 42.0f + 57.295776f * armPose.yaw * 0.7f));
        stack.mulPose(Axis.XP.rotationDegrees(-22.0f + 57.295776f * pitchDelta * 0.9f));
        stack.mulPose(Axis.ZP.rotationDegrees(-side * 8.0f + 57.295776f * armPose.roll * 0.82f));
    }

    public static ArmPose getArmPose(Player player, HumanoidArm arm, float partialTick) {
        StateKey key = new StateKey(player.getUUID(), arm);
        WhipState state = STATES.computeIfAbsent(key, ignored -> new WhipState(player, arm));
        return state.motor.sample(partialTick);
    }

    public static ArmPose getThirdPersonArmPose(Player player, HumanoidArm arm, float partialTick) {
        ArmPose pose = LightningWhipPhysics.getArmPose(player, arm, partialTick);
        float mirroredPitch = -0.38f - (pose.pitch - -0.38f);
        return new ArmPose(mirroredPitch, pose.yaw, pose.roll);
    }

    public static void pushRenderContext(LivingEntity holder, HumanoidArm arm, boolean firstPerson) {
        RENDER_CONTEXT.get().push(new RenderContext(holder, arm, firstPerson));
    }

    public static void popRenderContext() {
        ArrayDeque<RenderContext> stack = RENDER_CONTEXT.get();
        if (!stack.isEmpty()) {
            stack.pop();
        }
        if (stack.isEmpty()) {
            RENDER_CONTEXT.remove();
        }
    }

    static RenderContext currentRenderContext() {
        ArrayDeque<RenderContext> stack = RENDER_CONTEXT.get();
        return stack.isEmpty() ? null : stack.peek();
    }

    static void captureRenderedSocket(PoseStack poseStack, RenderContext context) {
        Vec3 world;
        Player player;
        LivingEntity livingEntity = context.holder;
        if (!(livingEntity instanceof Player) || !LightningWhipPhysics.holdsWhip(player = (Player)livingEntity, context.arm)) {
            return;
        }
        Vector3f transformedRoot = new Vector3f(0.0f, LightningWhipGeometry.CHAIN_PIVOT_Y, 0.0f);
        Vector3f transformedAxisPoint = new Vector3f(0.0f, LightningWhipGeometry.CHAIN_PIVOT_Y + 0.25f, 0.0f);
        poseStack.last().pose().transformPosition(transformedRoot);
        poseStack.last().pose().transformPosition(transformedAxisPoint);
        Minecraft minecraft = Minecraft.getInstance();
        Camera camera = minecraft.gameRenderer.getMainCamera();
        Vec3 cameraPosition = camera.getPosition();
        Vec3 capturedRoot = new Vec3(transformedRoot.x, transformedRoot.y, transformedRoot.z);
        Vec3 capturedAxisPoint = new Vec3(transformedAxisPoint.x, transformedAxisPoint.y, transformedAxisPoint.z);
        if (context.firstPerson) {
            capturedRoot = WhipFirstPersonFov.removeFromCapturedWorldVector(capturedRoot, camera);
            capturedAxisPoint = WhipFirstPersonFov.removeFromCapturedWorldVector(capturedAxisPoint, camera);
        }
        if ((world = cameraPosition.add(capturedRoot)).distanceToSqr(player.getEyePosition()) > 16.0) {
            return;
        }
        Vec3 handleAxis = capturedAxisPoint.subtract(capturedRoot);
        handleAxis = handleAxis.lengthSqr() > 1.0E-10 ? handleAxis.normalize() : Vec3.ZERO;
        StateKey key = new StateKey(player.getUUID(), context.arm);
        WhipState state = STATES.computeIfAbsent(key, ignored -> new WhipState(player, context.arm));
        long gameTime = player.level().getGameTime();
        state.captureAnchor(world, handleAxis, gameTime);
    }

    static void renderFirstPersonHandLash(PoseStack poseStack, RenderContext context, VertexConsumer consumer, int packedOverlay) {
        if (context == null || !context.firstPerson || !(context.holder instanceof Player player)
                || !holdsWhip(player, context.arm) || !LightningWhipVfx.ready()) return;
        WhipState state = STATES.get(new StateKey(player.getUUID(), context.arm));
        if (state == null || !state.initialized) return;
        Minecraft minecraft = Minecraft.getInstance();
        Camera camera = minecraft.gameRenderer.getMainCamera();
        if (!isAttachedLocalFirstPerson(minecraft, player, camera)) return;
        float partialTick = minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        Matrix4f inverse = new Matrix4f(poseStack.last().pose());
        float determinant = inverse.determinant();
        if (!Float.isFinite(determinant) || Math.abs(determinant) < 1.0e-8f) return;
        inverse.invert();
        Vec3 root = state.renderPrevious[0].lerp(state.points[0], partialTick);
        Vec3 correction = state.renderDisplayAnchor(root, player.level().getGameTime()).subtract(root);
        Vec3[] worldDisplay = state.buildLengthPreservingDisplayWorld(
                state.renderPrevious, state.points, partialTick, correction);
        Vec3[] local = new Vec3[POINTS];
        float[] visibility = new float[POINTS];
        Vec3 crosshairDirection = player.getViewVector(partialTick);
        if (crosshairDirection.lengthSqr() > 1.0E-10) {
            crosshairDirection = crosshairDirection.normalize();
        }
        for (int i = 0; i < local.length; i++) {
            Vec3 p = worldDisplay[i];
            local[i] = firstPersonWorldPointToLocal(p, camera, inverse);
            visibility[i] = firstPersonLightningVisibility(p, camera, crosshairDirection);
        }
        local[0] = new Vec3(0.0, LightningWhipGeometry.CHAIN_PIVOT_Y, 0.0);
        LightningWhipVfx.renderFirstPerson(poseStack, consumer, local,
                player.getId() * 31 + context.arm.ordinal(), visibility);
        state.renderFirstPersonElectricTipTrail(poseStack, consumer, camera, inverse, partialTick,
                player.level().getGameTime());
}

    private static Vec3 firstPersonWorldPointToLocal(Vec3 worldPoint, Camera camera, Matrix4f inverseHand) {
        Vec3 cameraRelative = worldPoint.subtract(camera.getPosition());
        Vec3 compensated = WhipFirstPersonFov.applyToCapturedWorldVector(cameraRelative, camera);
        Vector3f local = compensated.toVector3f();
        inverseHand.transformPosition(local);
        return new Vec3(local.x, local.y, local.z);
    }

    static void renderFirstPersonRootBridge(PoseStack poseStack, RenderContext context, VertexConsumer consumer, int packedLight, int packedOverlay) {

}

    private static boolean holdsWhip(Player player, HumanoidArm arm) {
        return LightningWhipPhysics.stackForArm(player, arm).is((Item)ModItems.LIGHTNING_WHIP.get());
    }

    private static boolean shouldDisplayTipTrail(Player player) {
        return player != null && player.isAlive();
    }

    private static float firstPersonLightningVisibility(Vec3 worldPoint, Camera camera,
                                                        Vec3 crosshairDirection) {
        if (!finite(worldPoint) || camera == null) {
            return 1.0f;
        }
        Vec3 fromCamera = worldPoint.subtract(camera.getPosition());
        double distanceSqr = fromCamera.lengthSqr();
        if (distanceSqr <= 1.0E-10) {
            return Math.min(FIRST_PERSON_LIGHTNING_DISTANCE_MIN_VISIBILITY,
                    FIRST_PERSON_CROSSHAIR_MIN_VISIBILITY);
        }

        double distance = Math.sqrt(distanceSqr);
        double distanceT = smoothstep((distance - FIRST_PERSON_LIGHTNING_DARK_DISTANCE)
                / (FIRST_PERSON_LIGHTNING_FULL_DISTANCE - FIRST_PERSON_LIGHTNING_DARK_DISTANCE));
        float distanceVisibility = (float)Mth.lerp(distanceT,
                FIRST_PERSON_LIGHTNING_DISTANCE_MIN_VISIBILITY, 1.0f);

        float crosshairVisibility = 1.0f;
        if (crosshairDirection != null && crosshairDirection.lengthSqr() > 1.0E-10) {
            Vec3 aim = crosshairDirection.normalize();
            double cosine = Mth.clamp(fromCamera.scale(1.0 / distance).dot(aim), -1.0, 1.0);
            if (cosine > FIRST_PERSON_CROSSHAIR_FADE_OUTER_COS) {
                double angle = Math.acos(cosine);
                if (angle <= FIRST_PERSON_CROSSHAIR_DIM_INNER_ANGLE_RADIANS) {
                    crosshairVisibility = FIRST_PERSON_CROSSHAIR_MIN_VISIBILITY;
                } else {
                    double angleT = smoothstep((angle - FIRST_PERSON_CROSSHAIR_DIM_INNER_ANGLE_RADIANS)
                            / (FIRST_PERSON_CROSSHAIR_FADE_OUTER_ANGLE_RADIANS
                            - FIRST_PERSON_CROSSHAIR_DIM_INNER_ANGLE_RADIANS));
                    crosshairVisibility = (float)Mth.lerp(angleT,
                            FIRST_PERSON_CROSSHAIR_MIN_VISIBILITY, 1.0f);
                }
            }
        }

        return Math.min(distanceVisibility, crosshairVisibility);
    }

    private static boolean hasFireAspect(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        for (Holder<Enchantment> enchantment : stack.getEnchantments().keySet()) {
            if (!enchantment.is(Enchantments.FIRE_ASPECT)) continue;
            return true;
        }
        return false;
    }

    private static ItemStack stackForArm(Player player, HumanoidArm arm) {
        return player.getItemInHand(LightningWhipPhysics.handForArm(player, arm));
    }

    private static InteractionHand handForArm(Player player, HumanoidArm arm) {
        return player.getMainArm() == arm ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
    }

    private static HumanoidArm armForHand(Player player, InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
    }

    private static Vec3 fallbackAnchor(Player player, HumanoidArm arm) {
        Vec3 look = player.getViewVector(1.0f).normalize();
        Vec3 horizontal = new Vec3(look.x, 0.0, look.z);
        if (horizontal.lengthSqr() < 1.0E-8) {
            horizontal = Vec3.directionFromRotation(0.0f, player.getYRot());
        }
        horizontal = new Vec3(horizontal.x, 0.0, horizontal.z).normalize();
        Vec3 right = new Vec3(-horizontal.z, 0.0, horizontal.x);
        double side = arm == HumanoidArm.RIGHT ? 1.0 : -1.0;
        return player.getEyePosition().add(horizontal.scale(0.22)).add(right.scale(side * 0.34)).add(0.0, -0.52, 0.0);
    }

    private static Vec3 physicalForward(Player player) {
        Vec3 forward = player.getViewVector(1.0f);
        forward = new Vec3(forward.x, 0.0, forward.z);
        if (forward.lengthSqr() < 1.0E-10) {
            forward = Vec3.directionFromRotation(0.0f, player.yBodyRot);
        }
        return new Vec3(forward.x, 0.0, forward.z).normalize();
    }

    private static double rearLengthFraction(Vec3[] points, Vec3 playerPosition, Vec3 forward, double rearMargin) {
        if (points == null || points.length < 2 || !finite(playerPosition) || !finite(forward)) {
            return 0.0;
        }
        double totalLength = 0.0;
        double rearLength = 0.0;
        for (int i = 1; i < points.length; ++i) {
            Vec3 a = points[i - 1];
            Vec3 b = points[i];
            if (!finite(a) || !finite(b)) continue;
            double segmentLength = a.distanceTo(b);
            if (!(segmentLength > 1.0E-8)) continue;
            totalLength += segmentLength;

            double da = a.subtract(playerPosition).dot(forward) + rearMargin;
            double db = b.subtract(playerPosition).dot(forward) + rearMargin;
            if (da <= 0.0 && db <= 0.0) {
                rearLength += segmentLength;
            } else if ((da <= 0.0) != (db <= 0.0)) {
                double t = da / (da - db);
                double behindFraction = da <= 0.0 ? t : 1.0 - t;
                rearLength += segmentLength * Mth.clamp(behindFraction, 0.0, 1.0);
            }
        }
        return totalLength > 1.0E-8 ? rearLength / totalLength : 0.0;
    }

    private static double smoothstep(double value) {
        double t = Mth.clamp(value, 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    private static double rightChargeProgress(double chargeTicks) {
        return Mth.clamp(chargeTicks / 60.0, 0.0, 1.0);
    }

    private static double rightSpinTurns(double chargeTicks) {
        double ticks = Math.max(0.0, chargeTicks);
        if (ticks <= 60.0) {
            double t = ticks / 60.0;
            return 4.0 * t * t;
        }
        double turnsPerTickAtFull = 0.13333333333333333;
        return 4.0 + (ticks - 60.0) * turnsPerTickAtFull;
    }

    private static double rightSpinOmega(double chargeTicks) {
        double ticks = Math.max(0.0, chargeTicks);
        double turnsPerTick = ticks < 60.0 ? 8.0 * ticks / 3600.0 : 0.13333333333333333;
        return turnsPerTick * 20.0 * Math.PI * 2.0;
    }

    private static float chargedTipScaleForCharge(double chargeTicks) {
        float charge = (float)LightningWhipPhysics.rightChargeProgress(chargeTicks);
        return Mth.lerp(charge, 1.0f, 10.0f);
    }

    private static float chargedTipScaleAfterRelease(double releaseTicks) {
        if (releaseTicks <= 14.0) {
            return 10.0f;
        }
        float shrink = (float)Mth.clamp((releaseTicks - 14.0) / 6.0, 0.0, 1.0);
        return Mth.lerp(shrink, 10.0f, 1.0f);
    }

    private static Vec3 aimDirection(Player player) {
        Vec3 aim = player.getViewVector(1.0f);
        if (!LightningWhipPhysics.finite(aim) || aim.lengthSqr() < 1.0E-10) {
            aim = Vec3.directionFromRotation(player.getXRot(), player.getYRot());
        }
        return aim.normalize();
    }

    private static Vec3 handBase(Player player, HumanoidArm arm, Vec3 attackDirection) {
        Vec3 horizontal = new Vec3(attackDirection.x, 0.0, attackDirection.z);
        horizontal = horizontal.lengthSqr() < 1.0E-10 ? LightningWhipPhysics.physicalForward(player) : horizontal.normalize();
        Vec3 right = new Vec3(-horizontal.z, 0.0, horizontal.x);
        double side = arm == HumanoidArm.RIGHT ? 1.0 : -1.0;
        return player.position().add(0.0, (double)player.getEyeHeight() - 0.58, 0.0).add(right.scale(side * 0.34)).add(horizontal.scale(0.1));
    }

    private static Vec3 chargedSpinCenter(Player player, HumanoidArm arm, Vec3 attackDirection) {
        Vec3 forward = new Vec3(attackDirection.x, 0.0, attackDirection.z);
        forward = forward.lengthSqr() < 1.0E-10 ? LightningWhipPhysics.physicalForward(player) : forward.normalize();
        return LightningWhipPhysics.handBase(player, arm, forward).add(forward.scale(0.32)).add(0.0, 0.68, 0.0);
    }

    private static Vec3 chargedSpinHandAnchor(Player player, HumanoidArm arm, Vec3 attackDirection, double chargeTicks) {
        Vec3 forward = new Vec3(attackDirection.x, 0.0, attackDirection.z);
        forward = forward.lengthSqr() < 1.0E-10 ? LightningWhipPhysics.physicalForward(player) : forward.normalize();
        Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
        double side = arm == HumanoidArm.RIGHT ? 1.0 : -1.0;
        double charge = LightningWhipPhysics.rightChargeProgress(chargeTicks);
        double lift = LightningWhipPhysics.smoothstep(Math.min(1.0, charge / 0.12));
        double theta = 1.0 * LightningWhipPhysics.rightSpinTurns(chargeTicks) * Math.PI * 2.0;
        double radius = 0.18 * LightningWhipPhysics.smoothstep(Math.min(1.0, charge / 0.18));
        Vec3 center = LightningWhipPhysics.chargedSpinCenter(player, arm, forward);
        Vec3 raisedOrbit = center.add(forward.scale(Math.cos(theta) * radius)).add(right.scale(Math.sin(theta) * radius * side));
        return LightningWhipPhysics.fallbackAnchor(player, arm).lerp(raisedOrbit, lift);
    }

    private static Vec3 chargedReleaseHandAnchor(Player player, HumanoidArm arm, Vec3 attackDirection, double releaseProgress) {
        Vec3 forward = new Vec3(attackDirection.x, 0.0, attackDirection.z);
        forward = forward.lengthSqr() < 1.0E-10 ? LightningWhipPhysics.physicalForward(player) : forward.normalize();
        Vec3 base = LightningWhipPhysics.handBase(player, arm, forward);
        double t = LightningWhipPhysics.smoothstep(releaseProgress);
        double theta = Mth.lerp(t, 1.5707963267948966, -0.36);
        double radius = 0.72;
        Vec3 center = base.add(forward.scale(0.34)).add(0.0, 0.05, 0.0);
        return center.add(forward.scale(Math.cos(theta) * radius)).add(0.0, Math.sin(theta) * radius, 0.0);
    }

    private static Vec3 precisionHandAnchor(Player player, HumanoidArm arm, Vec3 attackDirection, double rawProgress) {
        return LightningWhipPhysics.precisionHandAnchor(player, arm, attackDirection, rawProgress, 1.0);
    }

    private static Vec3 precisionHandAnchor(Player player, HumanoidArm arm, Vec3 attackDirection, double rawProgress, double swingSign) {
        double lateralOffset;
        double verticalOffset;
        double forwardOffset;
        Vec3 aim = attackDirection.lengthSqr() < 1.0E-10 ? LightningWhipPhysics.aimDirection(player) : attackDirection.normalize();
        Vec3 horizontal = new Vec3(aim.x, 0.0, aim.z);
        horizontal = horizontal.lengthSqr() < 1.0E-10 ? LightningWhipPhysics.physicalForward(player) : horizontal.normalize();
        Vec3 right = new Vec3(-horizontal.z, 0.0, horizontal.x);
        double side = arm == HumanoidArm.RIGHT ? 1.0 : -1.0;
        double sign = swingSign >= 0.0 ? 1.0 : -1.0;
        Vec3 base = LightningWhipPhysics.handBase(player, arm, aim);
        double raw = Mth.clamp(rawProgress, 0.0, 1.0);
        if (raw < 0.3) {
            double t = LightningWhipPhysics.smoothstep(raw / 0.3);
            forwardOffset = Mth.lerp(t, 0.0, 0.08);
            verticalOffset = Mth.lerp(t, 0.0, 0.68);
            lateralOffset = Mth.lerp(t, 0.0, side * sign * 0.16);
        } else if (raw < 0.72) {
            double t = (raw - 0.3) / 0.42;
            double accelerated = Math.pow(Mth.clamp(t, 0.0, 1.0), 0.82);
            forwardOffset = Mth.lerp(accelerated, 0.08, 1.16);
            verticalOffset = Mth.lerp(accelerated, 0.68, -0.18);
            lateralOffset = Mth.lerp(accelerated, side * sign * 0.18, -side * sign * 0.14);
        } else {
            double t = LightningWhipPhysics.smoothstep((raw - 0.72) / 0.28);
            forwardOffset = Mth.lerp(t, 1.16, 0.28);
            verticalOffset = Mth.lerp(t, -0.18, 0.0);
            lateralOffset = Mth.lerp(t, -side * sign * 0.14, 0.0);
        }
        return base.add(aim.scale(forwardOffset)).add(0.0, verticalOffset, 0.0).add(right.scale(lateralOffset));
    }

    private static Vec3 drivenHandAnchor(Player player, HumanoidArm arm, AttackDrive mode, Vec3 attackDirection, double rawProgress) {
        return mode == AttackDrive.CHARGED_SLAM ? LightningWhipPhysics.chargedSpinHandAnchor(player, arm, attackDirection, rawProgress * 60.0) : LightningWhipPhysics.precisionHandAnchor(player, arm, attackDirection, rawProgress);
    }

    private static double particleMass(int particle) {
        if (particle <= 0) {
            return Double.POSITIVE_INFINITY;
        }
        double taper = ((double)particle - 1.0) / Math.max(1.0, 55.0);
        double remaining = 1.0 - Mth.clamp(taper, 0.0, 1.0);
        return 0.035 + 0.300 * remaining * remaining;
    }

    private static double inverseMass(int particle) {
        return particle <= 0 ? 0.0 : 1.0 / LightningWhipPhysics.particleMass(particle);
    }

    private static double maximumVerletStep(int particle, double substepSeconds) {
        double taper = (double)particle / 56.0;

        double maximumSpeed = 220.0 + 380.0 * Math.pow(taper, 1.55);
        return maximumSpeed * substepSeconds;
    }

    private static float precisionRawProgressAtLightningTick(double lightningTick) {
        double emeraldTick = Math.max(0.0, lightningTick) * 2.0;
        if (emeraldTick < 3.0) {
            return (float)(PRECISION_RELEASE_RAW * Mth.clamp(emeraldTick / 3.0, 0.0, 1.0));
        }
        double stroke = Mth.clamp((emeraldTick - 3.0) / 4.0, 0.0, 1.0);
        return (float)Mth.lerp(stroke, PRECISION_RELEASE_RAW, 1.0F);
    }

    private static boolean finite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

    private static ArmPose chargedChargeArmPose(double chargeTicks, float side) {
        float charge = (float)LightningWhipPhysics.rightChargeProgress(chargeTicks);
        float lift = (float)LightningWhipPhysics.smoothstep(Math.min(1.0, (double)charge / 0.12));
        float theta = (float)(1.0 * LightningWhipPhysics.rightSpinTurns(chargeTicks) * 6.2831854820251465);
        ArmPose leftGather = LightningWhipPhysics.precisionArmPose(0.3f, side);
        float pitch = Mth.lerp(lift, -0.38f, leftGather.pitch()) + Mth.sin(theta) * 0.018f;
        float yaw = Mth.lerp(lift, 0.0f, leftGather.yaw()) + side * Mth.sin(theta) * 0.055f;
        float roll = Mth.lerp(lift, side * 0.08f, leftGather.roll()) + side * Mth.cos(theta) * 0.045f;
        return new ArmPose(pitch, yaw, roll);
    }

    private static ArmPose chargedReleaseArmPose(float releaseProgress, float side) {
        float t = Mth.clamp(releaseProgress, 0.0f, 1.0f);
        float leftClickEquivalent = Mth.lerp(t, 0.3f, 1.0f);
        return LightningWhipPhysics.precisionArmPose(leftClickEquivalent, side);
    }

    private static ArmPose precisionArmPose(float rawProgress, float side) {
        return LightningWhipPhysics.precisionArmPose(rawProgress, side, 1.0f);
    }

    private static ArmPose precisionArmPose(float rawProgress, float side, float swingSign) {
        float roll;
        float yaw;
        float pitch;
        float sign;
        float raw = Mth.clamp(rawProgress, 0.0f, 1.0f);
        float f = sign = swingSign >= 0.0f ? 1.0f : -1.0f;
        if (raw < 0.3f) {
            float t = (float)LightningWhipPhysics.smoothstep(raw / 0.3f);
            pitch = Mth.lerp(t, -0.38f, 1.52f);
            yaw = Mth.lerp(t, 0.0f, -side * sign * 0.22f);
            roll = Mth.lerp(t, side * 0.08f, -side * sign * 0.16f);
        } else if (raw < 0.72f) {
            float t = (raw - 0.3f) / 0.42f;
            float accelerated = (float)Math.pow(Mth.clamp(t, 0.0f, 1.0f), 1.55);
            pitch = Mth.lerp(accelerated, 1.52f, -0.58f);
            yaw = Mth.lerp(accelerated, -side * sign * 0.22f, side * sign * 0.24f);
            roll = Mth.lerp(accelerated, -side * sign * 0.16f, side * sign * 0.32f);
        } else {
            float t = (float)LightningWhipPhysics.smoothstep((raw - 0.72f) / 0.28f);
            pitch = Mth.lerp(t, -0.58f, -0.38f);
            yaw = Mth.lerp(t, side * sign * 0.24f, 0.0f);
            roll = Mth.lerp(t, side * sign * 0.32f, side * 0.08f);
        }
        return new ArmPose(pitch, yaw, roll);
    }

    private record StateKey(UUID player, HumanoidArm arm) {
    }

    private static enum AttackDrive {
        NONE,
        CHARGED_SLAM,
        PRECISION;

    }

    private static final class WhipState {
        private final HumanoidArm arm;
        private final Vec3[] points = new Vec3[57];
        private final Vec3[] previous = new Vec3[57];
        private final Vec3[] renderPrevious = new Vec3[57];
        private final double[] lengthLambda = new double[56];
        private final double[] bendLambda = new double[55];
        private final ArmMotor motor = new ArmMotor();
        private final Vec3[] coilTargets = new Vec3[57];

        private final Vec3[] precisionFollowDirections = new Vec3[POINTS];
        private final Vec3[] precisionFollowDirectionDelta = new Vec3[POINTS];
        private boolean precisionFollowInitialized;
        private Player holder;
        private Vec3 capturedAnchor;
        private Vec3 capturedFirstPersonViewAnchor;
        private Vec3 previousFirstPersonViewAnchor;
        private double capturedFirstPersonViewSampleTime = Double.NaN;
        private double previousFirstPersonViewSampleTime = Double.NaN;
        private long capturedFirstPersonViewGameTime = Long.MIN_VALUE;
        private Vec3 firstPersonBridgeTargetWorld;
        private long firstPersonBridgeTargetGameTime = Long.MIN_VALUE;
        private Vec3 capturedHandleAxis = Vec3.ZERO;
        private Vec3 precisionHandleAxis = Vec3.ZERO;
        private int precisionHandleAxisTicks;
        private Vec3 physicalDriveOffset = Vec3.ZERO;
        private long capturedAnchorGameTime = Long.MIN_VALUE;
        private long lastSeenGameTime = Long.MIN_VALUE;
        private int highFidelityTicksRemaining;
        private double lastSubstepSeconds = 0.00625;
        private float tipVisualScale = 1.0f;
        private double precisionSwingSign = 1.0;
        private boolean precisionMomentumCarry;
        private Vec3 pendingPrecisionDirection = Vec3.ZERO;
        private final ArrayDeque<TipTrailSample> tipTrail = new ArrayDeque();
        private Vec3 lastTrailSample = null;
        private long lastFlameParticleTick = Long.MIN_VALUE;
        private float coilProgress = 1.0f;
        private long lastAttackDriveGameTime = Long.MIN_VALUE;
        private boolean initialized;

        WhipState(Player player, HumanoidArm arm) {
            this.holder = player;
            this.arm = arm;
        }

        private WhipMomentumContinuity.Continuation precisionContinuation(Vec3 direction, long gameTime) {
            if (!this.initialized || this.coilProgress > 1.0E-4f || this.lastAttackDriveGameTime == Long.MIN_VALUE || gameTime - this.lastAttackDriveGameTime >= 100L) {
                return WhipMomentumContinuity.Continuation.NONE;
            }
            return WhipMomentumContinuity.analyze(this.points, this.previous, this.lastSubstepSeconds, direction, this.arm);
        }

        private boolean canStartPrecisionSwing(Player player) {
            if (!this.initialized) {
                return true;
            }
            Vec3 root = this.points[0];
            Vec3 tip = this.points[POINTS - 1];
            if (!LightningWhipPhysics.finite(root) || !LightningWhipPhysics.finite(tip)) {
                return false;
            }
            if (tip.distanceToSqr(root) <= PRECISION_RETRACTED_TIP_DISTANCE_SQR) {
                return true;
            }
            Vec3 forward = LightningWhipPhysics.physicalForward(player);
            return LightningWhipPhysics.rearLengthFraction(
                    this.points, player.position(), forward, PRECISION_REAR_PLANE_MARGIN)
                    >= PRECISION_REAR_REQUIRED_LENGTH_FRACTION;
        }

        void captureAnchor(Vec3 anchor, Vec3 handleAxis, long gameTime) {
            Level level;
            if (!LightningWhipPhysics.finite(anchor)) {
                return;
            }
            this.capturedAnchor = anchor;
            if (LightningWhipPhysics.finite(handleAxis) && handleAxis.lengthSqr() > 1.0E-10) {
                this.capturedHandleAxis = handleAxis.normalize();
            }
            this.capturedAnchorGameTime = gameTime;
            if (!this.initialized && this.holder != null && (level = this.holder.level()) instanceof ClientLevel) {
                ClientLevel level2 = (ClientLevel)level;
                this.initialize(level2, anchor, this.holder);
            }
        }

        private boolean hasFreshCapturedAnchor(long gameTime) {
            long age = gameTime - this.capturedAnchorGameTime;
            return this.capturedAnchor != null && age >= 0L && age <= 4L;
        }

        private Vec3 renderDisplayAnchor(Vec3 interpolatedRoot, long gameTime) {
            return this.hasFreshCapturedAnchor(gameTime) ? this.capturedAnchor : interpolatedRoot;
        }

        void captureFirstPersonViewAnchor(Vec3 cameraLocalAnchor, long gameTime, float partialTick) {
            if (!LightningWhipPhysics.finite(cameraLocalAnchor)) {
                return;
            }
            double sampleTime = (float)gameTime + Mth.clamp(partialTick, 0.0f, 1.0f);
            if (this.capturedFirstPersonViewAnchor != null && Double.isFinite(this.capturedFirstPersonViewSampleTime)) {
                if (sampleTime + 1.0E-4 < this.capturedFirstPersonViewSampleTime) {
                    this.previousFirstPersonViewAnchor = null;
                    this.previousFirstPersonViewSampleTime = Double.NaN;
                } else if (sampleTime > this.capturedFirstPersonViewSampleTime + 1.0E-4) {
                    this.previousFirstPersonViewAnchor = this.capturedFirstPersonViewAnchor;
                    this.previousFirstPersonViewSampleTime = this.capturedFirstPersonViewSampleTime;
                }
            }
            this.capturedFirstPersonViewAnchor = cameraLocalAnchor;
            this.capturedFirstPersonViewSampleTime = sampleTime;
            this.capturedFirstPersonViewGameTime = gameTime;
        }

        private Vec3 firstPersonDisplayAnchor(Camera camera, long gameTime, float partialTick, Vec3 fallback) {
            if (this.capturedFirstPersonViewAnchor == null || gameTime - this.capturedFirstPersonViewGameTime > 4L) {
                return fallback;
            }
            Vec3 cameraRelativeWorld = WhipFirstPersonFov.cameraVectorToWorld(this.capturedFirstPersonViewAnchor, camera);
            return camera.getPosition().add(cameraRelativeWorld);
        }

        void tick(ClientLevel level, Player player, long gameTime) {
            Vec3 renderedAnchor;
            boolean acceptedExplicitRequest;
            AttackDrive requested;
            ClientPrecisionRequest precisionRequest;
            this.holder = player;
            this.lastSeenGameTime = gameTime;
            this.pruneTipTrail(gameTime);
            StateKey key = new StateKey(player.getUUID(), this.arm);
            AttackDrive chargedRequest = PENDING_ATTACKS.get(key);
            ArrayDeque<ClientPrecisionRequest> precisionQueue = PENDING_PRECISION.get(key);
            ClientPrecisionRequest clientPrecisionRequest = precisionRequest =
                    precisionQueue == null ? null : precisionQueue.peekFirst();
            boolean precisionReady = precisionRequest != null && this.canStartPrecisionSwing(player);
            Vec3 precisionDirection = precisionRequest == null
                    ? Vec3.ZERO
                    : (precisionReady ? LightningWhipPhysics.aimDirection(player)
                            : precisionRequest.direction());
            requested = chargedRequest == AttackDrive.CHARGED_SLAM
                    ? AttackDrive.CHARGED_SLAM
                    : (precisionReady ? AttackDrive.PRECISION : AttackDrive.NONE);
            if (precisionRequest != null) {
                this.pendingPrecisionDirection = precisionDirection;
                this.precisionHandleAxis = LightningWhipPhysics.finite(precisionRequest.handleAxis()) && precisionRequest.handleAxis().lengthSqr() > 1.0E-10 ? precisionRequest.handleAxis().normalize() : this.capturedHandleAxis;
                this.precisionHandleAxisTicks = Math.max(this.precisionHandleAxisTicks, 10);
            }
            WhipMomentumContinuity.Continuation fallbackContinuation = precisionRequest == null ? this.precisionContinuation(LightningWhipPhysics.aimDirection(player), gameTime) : WhipMomentumContinuity.Continuation.NONE;
            double requestedSwingSign = precisionRequest != null ? precisionRequest.swingSign() : fallbackContinuation.swingSign();
            boolean requestedMomentumCarry = precisionRequest != null ? precisionRequest.momentumCarry() : fallbackContinuation.active();
            this.motor.tick(player, this.arm, requested, requestedSwingSign, requestedMomentumCarry);
            boolean bl = acceptedExplicitRequest = requested != AttackDrive.NONE && this.motor.requestAcceptedThisTick();
            if (acceptedExplicitRequest && requested == AttackDrive.CHARGED_SLAM) {
                PENDING_ATTACKS.remove(key, (Object)AttackDrive.CHARGED_SLAM);
            } else if (acceptedExplicitRequest && requested == AttackDrive.PRECISION
                    && precisionQueue != null && precisionRequest != null) {

                if (this.initialized) {
                    LightningWhipNetwork.sendPrecisionAttack(this.points, this.previous,
                            this.lastSubstepSeconds, precisionRequest.handleAxis(),
                            precisionDirection);
                } else {
                    LightningWhipNetwork.sendPrecisionAttack();
                }
                player.swing(InteractionHand.MAIN_HAND);
                player.resetAttackStrengthTicker();
                precisionQueue.pollFirst();
                if (precisionQueue.isEmpty()) {
                    PENDING_PRECISION.remove(key);
                }
            }
            if (this.motor.driveStartedThisTick() && this.motor.driveModeThisTick() == AttackDrive.PRECISION) {
                if (LightningWhipPhysics.finite(this.pendingPrecisionDirection) && this.pendingPrecisionDirection.lengthSqr() > 1.0E-10) {
                    this.motor.overridePrecisionDirection(this.pendingPrecisionDirection);
                }
                this.precisionSwingSign = this.motor.precisionSwingSign();
                this.precisionMomentumCarry = this.motor.precisionMomentumCarry();
                this.pendingPrecisionDirection = Vec3.ZERO;
            }
            boolean handleDriveActive = this.motor.wasDrivenThisTick();
            boolean chargePhase = this.motor.chargePhaseThisTick();
            boolean releasePhase = this.motor.releasePhaseThisTick();
            AttackDrive driveMode = this.motor.driveModeThisTick();
            Vec3 attackDirection = this.motor.attackDirection();
            this.tipVisualScale = chargePhase ? LightningWhipPhysics.chargedTipScaleForCharge(this.motor.chargeToTicks()) : (releasePhase ? 10.0f : (this.tipVisualScale > 1.001f ? Mth.lerp(0.28f, this.tipVisualScale, 1.0f) : 1.0f));
            Vec3 vec3 = renderedAnchor = this.capturedAnchor != null && gameTime - this.capturedAnchorGameTime <= 4L ? this.capturedAnchor : LightningWhipPhysics.fallbackAnchor(player, this.arm);
            if (!this.initialized) {
                this.initialize(level, renderedAnchor, player);
            }
            for (int i = 0; i < 57; ++i) {
                this.renderPrevious[i] = this.points[i];
            }
            if (this.motor.driveStartedThisTick()) {
                if (driveMode == AttackDrive.PRECISION) {
                    this.precisionFollowInitialized = false;
                    if (this.precisionHandleAxisTicks <= 0) {
                        this.precisionHandleAxis = this.capturedHandleAxis;
                    }
                    this.precisionHandleAxisTicks = Math.max(this.precisionHandleAxisTicks, 10);
                }
                Vec3 canonicalStart = driveMode == AttackDrive.CHARGED_SLAM ? LightningWhipPhysics.chargedSpinHandAnchor(player, this.arm, attackDirection, 0.0) : LightningWhipPhysics.precisionHandAnchor(player, this.arm, attackDirection, 0.0, this.precisionSwingSign);
                this.physicalDriveOffset = renderedAnchor.subtract(canonicalStart);
                this.highFidelityTicksRemaining = Math.max(28, 40);
            }
            if (this.motor.releaseStartedThisTick()) {
                Vec3 releaseStart = LightningWhipPhysics.chargedReleaseHandAnchor(player, this.arm, attackDirection, 0.0);
                this.physicalDriveOffset = this.points[0].subtract(releaseStart);
                this.highFidelityTicksRemaining = Math.max(this.highFidelityTicksRemaining, 36);
            }
            Vec3 targetAnchor = chargePhase ? LightningWhipPhysics.chargedSpinHandAnchor(player, this.arm, attackDirection, this.motor.chargeToTicks()).add(this.physicalDriveOffset) : (releasePhase ? LightningWhipPhysics.chargedReleaseHandAnchor(player, this.arm, attackDirection, this.motor.releaseToProgress()).add(this.physicalDriveOffset) : (handleDriveActive && driveMode == AttackDrive.PRECISION ? LightningWhipPhysics.precisionHandAnchor(player, this.arm, attackDirection, this.motor.driveToRawProgress(), this.precisionSwingSign).add(this.physicalDriveOffset) : renderedAnchor));
            this.updateCoilState(player, gameTime, handleDriveActive, targetAnchor);
            Vec3 startAnchor = this.points[0];
            Vec3 anchorDelta = targetAnchor.subtract(startAnchor);
            if (anchorDelta.lengthSqr() > 9.0) {
                for (int i = 0; i < 57; ++i) {
                    this.points[i] = this.points[i].add(anchorDelta);
                    this.previous[i] = this.previous[i].add(anchorDelta);
                    this.renderPrevious[i] = this.renderPrevious[i].add(anchorDelta);
                }
                startAnchor = targetAnchor;
            }
            int substeps = this.chooseAdaptiveSubsteps(handleDriveActive, chargePhase, releasePhase, driveMode);
            substeps = WhipPerformanceTuning.capRemoteSubsteps(this.holder, substeps);
            double substepSeconds = this.configureAdaptiveTimestep(substeps);
            double substepSecondsSqr = substepSeconds * substepSeconds;
            double velocityRetention = Math.pow(TICK_VELOCITY_RETENTION, 1.0 / (double)substeps);
            double surfaceTangentRetention = Math.pow(SURFACE_TANGENT_RETENTION_PER_TICK, 1.0 / (double)substeps);
            this.depenetrateFromBlocks(level);
            for (int substep = 0; substep < substeps; ++substep) {
                Vec3 substepAnchor;
                Vec3[] before = (Vec3[])this.points.clone();
                double alpha = ((double)substep + 1.0) / (double)substeps;
                double precisionProgress = 1.0;
                double chargeTicks = this.motor.chargeToTicks();
                double releaseProgress = this.motor.releaseToProgress();
                if (chargePhase) {
                    chargeTicks = Mth.lerp(alpha, (double)this.motor.chargeFromTicks(), (double)this.motor.chargeToTicks());
                    substepAnchor = LightningWhipPhysics.chargedSpinHandAnchor(player, this.arm, attackDirection, chargeTicks).add(this.physicalDriveOffset);
                } else if (releasePhase) {
                    releaseProgress = Mth.lerp(alpha, (double)this.motor.releaseFromProgress(), (double)this.motor.releaseToProgress());
                    substepAnchor = LightningWhipPhysics.chargedReleaseHandAnchor(player, this.arm, attackDirection, releaseProgress).add(this.physicalDriveOffset);
                } else if (handleDriveActive && driveMode == AttackDrive.PRECISION) {
                    precisionProgress = Mth.lerp(alpha, (double)this.motor.driveFromRawProgress(), (double)this.motor.driveToRawProgress());
                    substepAnchor = LightningWhipPhysics.precisionHandAnchor(player, this.arm, attackDirection, precisionProgress, this.precisionSwingSign).add(this.physicalDriveOffset);
                } else {
                    substepAnchor = startAnchor.lerp(targetAnchor, alpha);
                }
                this.predict(substepAnchor, substepSecondsSqr, velocityRetention, substepSeconds);
                if (!handleDriveActive && this.coilProgress > 1.0E-4f) {
                    this.applyCoilGuidance();
                }
                if (chargePhase) {
                    this.applyChargeForces(player, attackDirection, chargeTicks, substepSeconds, substepSecondsSqr);
                } else if (releasePhase) {
                    this.applyReleaseForces(player, attackDirection, releaseProgress, substepSeconds, substepSecondsSqr);
                } else if (handleDriveActive && driveMode == AttackDrive.PRECISION
                        && precisionProgress >= 0.0
                        && precisionProgress < PRECISION_GUIDE_END) {
                    this.applyPrecisionGuidance(player, substepAnchor, attackDirection, precisionProgress,
                            this.precisionMomentumCarry, substepSeconds, substepSecondsSqr);
                }
                double collisionTipScale = chargePhase ? (double)LightningWhipPhysics.chargedTipScaleForCharge(chargeTicks) : (double)(releasePhase ? 10.0f : Math.max(1.0f, this.tipVisualScale));
                WhipBlockCollision.SegmentEnvironment[] blockBroadphase = WhipBlockCollision.buildBroadphase(level, before, this.points, LightningWhipGeometry.SEGMENT_COLLIDER_RADIUS, collisionTipScale);
                Arrays.fill(this.lengthLambda, 0.0);
                Arrays.fill(this.bendLambda, 0.0);
                for (int iteration = 0; iteration < 5; ++iteration) {
                    this.points[0] = substepAnchor;
                    for (int i = 0; i < 56; ++i) {
                        this.solveDistance(i, i + 1, (double)LightningWhipGeometry.REST_LENGTHS[i] * 1.0, LENGTH_COMPLIANCE, this.lengthLambda, i, false, substepSecondsSqr);
                    }
                    if (!handleDriveActive || driveMode != AttackDrive.PRECISION) {
                        this.enforceHandleAxis(substepAnchor);
                        this.enforceHandleBendZone();
                        this.enforceHandleContinuity();
                    }
                    this.enforceAntiFold(substepSecondsSqr);
                    this.enforceMaximumStretch();
                    if (iteration != 1) continue;
                    this.solveBlockCapsuleContacts(blockBroadphase, before, collisionTipScale, 1.0, false);
                }
                this.points[0] = substepAnchor;
                this.solveSelfCollision();
                this.solveBlockCapsuleContacts(blockBroadphase, before, collisionTipScale, surfaceTangentRetention, true);
                Vec3 tipDelta = this.points[56].subtract(before[56]);
                double tipSpeed = tipDelta.length() / Math.max(1.0E-6, substepSeconds);
                if (this.coilProgress <= 1.0E-4f
                        && this.lastAttackDriveGameTime != Long.MIN_VALUE
                        && gameTime - this.lastAttackDriveGameTime <= 12L
                        && LightningWhipPhysics.shouldDisplayTipTrail(player)) {
                    this.recordTipTrail(this.points[56], gameTime + alpha, tipSpeed);
                    continue;
                }
                this.tipTrail.clear();
                this.lastTrailSample = null;
            }
            this.points[0] = targetAnchor;
            this.emitFireAspectTipParticle(level, player, gameTime);
            if (this.highFidelityTicksRemaining > 0) {
                --this.highFidelityTicksRemaining;
            }
            if (this.precisionHandleAxisTicks > 0) {
                --this.precisionHandleAxisTicks;
            }
            boolean valid = true;
            for (Vec3 point : this.points) {
                if (LightningWhipPhysics.finite(point)) continue;
                this.initialize(level, targetAnchor, player);
                this.highFidelityTicksRemaining = 0;
                this.tipVisualScale = 1.0f;
                this.tipTrail.clear();
                this.lastTrailSample = null;
                valid = false;
                break;
            }
        }

        private void pruneTipTrail(long gameTime) {
            while (!this.tipTrail.isEmpty() && gameTime - this.tipTrail.peekFirst().sampleTime() > TIP_TRAIL_LIFETIME_TICKS) {
                this.tipTrail.removeFirst();
            }
            if (this.tipTrail.isEmpty()) {
                this.lastTrailSample = null;
            }
        }

        private void recordTipTrail(Vec3 tip, double sampleTime, double tipSpeed) {
            if (!LightningWhipPhysics.finite(tip) || !Double.isFinite(sampleTime) || tipSpeed < TIP_TRAIL_MIN_SPEED) {
                return;
            }
            if (this.lastTrailSample != null && this.lastTrailSample.distanceToSqr(tip) < TIP_TRAIL_MIN_SAMPLE_DISTANCE_SQR) {
                return;
            }
            this.tipTrail.addLast(new TipTrailSample(tip, sampleTime));
            this.lastTrailSample = tip;
            while (this.tipTrail.size() > TIP_TRAIL_MAX_SAMPLES) {
                this.tipTrail.removeFirst();
            }
        }

        private void emitFireAspectTipParticle(ClientLevel level, Player player, long gameTime) {
            if (!this.initialized || gameTime == this.lastFlameParticleTick || !LightningWhipPhysics.hasFireAspect(LightningWhipPhysics.stackForArm(player, this.arm))) {
                return;
            }
            Vec3 tip = this.points[56];
            if (!LightningWhipPhysics.finite(tip)) {
                return;
            }
            this.lastFlameParticleTick = gameTime;
            double vx = (level.random.nextDouble() - 0.5) * 0.035;
            double vy = 0.025 + level.random.nextDouble() * 0.035;
            double vz = (level.random.nextDouble() - 0.5) * 0.035;
            level.addParticle(ParticleTypes.FLAME, tip.x, tip.y, tip.z, vx, vy, vz);
        }

        private void applyChargeForces(Player player, Vec3 forwardInput, double chargeTicks, double substepSeconds, double substepSecondsSqr) {
            Vec3 forward = new Vec3(forwardInput.x, 0.0, forwardInput.z);
            forward = forward.lengthSqr() < 1.0E-10 ? LightningWhipPhysics.physicalForward(player) : forward.normalize();
            Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
            double side = this.arm == HumanoidArm.RIGHT ? 1.0 : -1.0;
            Vec3 center = LightningWhipPhysics.chargedSpinCenter(player, this.arm, forward).add(this.physicalDriveOffset);
            double charge = LightningWhipPhysics.rightChargeProgress(chargeTicks);
            double omega = LightningWhipPhysics.rightSpinOmega(chargeTicks);
            double envelope = LightningWhipPhysics.smoothstep(charge);
            for (int i = 1; i < 57; ++i) {
                Vec3 radial = this.points[i].subtract(center);
                radial = new Vec3(radial.x, 0.0, radial.z);
                double radius = radial.length();
                if (radius < 1.0E-5) continue;
                Vec3 radialDir = radial.scale(1.0 / radius);
                double f = radialDir.dot(forward);
                double r = radialDir.dot(right);
                Vec3 tangentDir = forward.scale(-r).add(right.scale(f)).scale(1.0 * side);
                if (tangentDir.lengthSqr() > 1.0E-10) {
                    tangentDir = tangentDir.normalize();
                }
                double taper = (double)i / 56.0;
                double weight = Math.pow(taper, 1.3) * envelope;
                double radialAccel = Math.min(150.0, omega * omega * radius * 0.12) * weight;
                Vec3 velocity = this.points[i].subtract(this.previous[i]);
                double tangentSpeed = velocity.dot(tangentDir) / substepSeconds;
                double desiredSpeed = omega * radius;
                double tangentAccel = Mth.clamp((desiredSpeed - tangentSpeed) * 8.0, -90.0, 90.0) * weight;
                Vec3 acceleration = radialDir.scale(radialAccel).add(tangentDir.scale(tangentAccel));
                this.points[i] = this.points[i].add(acceleration.scale(substepSecondsSqr));
            }
        }

        private void applyReleaseForces(Player player, Vec3 forwardInput, double releaseProgress, double substepSeconds, double substepSecondsSqr) {
            Vec3 forward = new Vec3(forwardInput.x, 0.0, forwardInput.z);
            forward = forward.lengthSqr() < 1.0E-10 ? LightningWhipPhysics.physicalForward(player) : forward.normalize();
            Vec3 up = new Vec3(0.0, 1.0, 0.0);
            Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
            double release = LightningWhipPhysics.smoothstep(releaseProgress);
            double planeBlend = LightningWhipPhysics.smoothstep(Mth.clamp(releaseProgress / 0.3, 0.0, 1.0));
            Vec3 center = LightningWhipPhysics.chargedSpinCenter(player, this.arm, forward).add(this.physicalDriveOffset);
            double pendulumAngle = release * Math.PI * 0.82;
            Vec3 verticalRadialDir = up.scale(Math.cos(pendulumAngle)).add(forward.scale(Math.sin(pendulumAngle))).normalize();
            Vec3 verticalTangentDir = forward.scale(Math.cos(pendulumAngle)).add(up.scale(-Math.sin(pendulumAngle))).normalize();
            double accumulatedLength = 0.0;
            for (int i = 1; i < 57; ++i) {
                double centripetalAccel;
                Vec3 acceleration;
                double accelLength;
                accumulatedLength += (double)LightningWhipGeometry.REST_LENGTHS[i - 1];
                double taper = (double)i / 56.0;
                double weight = Math.pow(taper, 1.22);
                Vec3 relative = this.points[i].subtract(center);
                double worldRadius = relative.length();
                if (worldRadius < 1.0E-6) continue;
                Vec3 currentRadialDir = relative.scale(1.0 / worldRadius);
                Vec3 radialDir = currentRadialDir.lerp(verticalRadialDir, planeBlend);
                radialDir = radialDir.lengthSqr() < 1.0E-10 ? verticalRadialDir : radialDir.normalize();
                double targetRadius = Mth.clamp(worldRadius, accumulatedLength * 0.72, accumulatedLength);
                Vec3 targetPoint = center.add(radialDir.scale(targetRadius));
                Vec3 pathError = targetPoint.subtract(this.points[i]);
                double lateralOffset = relative.dot(right);
                Vec3 stepVelocity = this.points[i].subtract(this.previous[i]);
                double stepSpeed = stepVelocity.length();
                double speedPerSecond = stepSpeed / Math.max(1.0E-6, substepSeconds);
                Vec3 tangentDir = verticalTangentDir;
                if (stepSpeed > 1.0E-8) {
                    Vec3 currentVelocityDir = stepVelocity.scale(1.0 / stepSpeed);
                    tangentDir = currentVelocityDir.lerp(verticalTangentDir, planeBlend);
                    tangentDir = tangentDir.lengthSqr() < 1.0E-10 ? verticalTangentDir : tangentDir.normalize();
                    double steer = Mth.clamp(0.52 * planeBlend * weight + 0.1 * taper * planeBlend, 0.0, 0.78);
                    Vec3 guided = stepVelocity.lerp(tangentDir.scale(stepSpeed), steer);
                    double guidedLength = guided.length();
                    if (guidedLength > 1.0E-8) {
                        guided = guided.scale(stepSpeed / guidedLength);
                        this.previous[i] = this.points[i].subtract(guided);
                    }
                }
                if ((accelLength = (acceleration = radialDir.scale(-(centripetalAccel = Math.min(1450.0, speedPerSecond * speedPerSecond / Math.max(0.25, targetRadius))) * weight).add(tangentDir.scale((80.0 + 560.0 * release) * weight * planeBlend)).add(pathError.scale(360.0 * weight * planeBlend)).add(right.scale(-lateralOffset * 980.0 * weight * planeBlend)).add(up.scale(-GRAVITY * weight * planeBlend))).length()) > 1800.0) {
                    acceleration = acceleration.scale(1800.0 / accelLength);
                }
                this.points[i] = this.points[i].add(acceleration.scale(substepSecondsSqr));
            }
        }

        private void applyPrecisionGuidance(Player player, Vec3 root, Vec3 attackDirection,
                                            double rawProgress, boolean momentumCarry,
                                            double substepSeconds, double substepSecondsSqr) {
            Vec3 aim = attackDirection.lengthSqr() > 1.0E-10
                    ? attackDirection.normalize() : LightningWhipPhysics.aimDirection(player);
            Vec3 center = player.getEyePosition();
            Vec3 sourceDirection = this.precisionCrosshairSweepDirection(
                    player, aim, rawProgress, this.precisionSwingSign);

            if (!this.precisionFollowInitialized) {
                this.initializePrecisionFollowDirections(center, sourceDirection);
            }

            double dt = Math.max(1.0E-5, substepSeconds);
            double blend = 1.0 - Math.exp(-dt / PRECISION_FOLLOW_PER_LINK_TAU);
            blend = Mth.clamp(blend, 0.0, 1.0);
            for (int i = POINTS - 1; i >= 1; --i) {
                Vec3 oldDirection = this.precisionFollowDirections[i];
                Vec3 leaderDirection = this.precisionFollowDirections[i - 1];
                Vec3 mixed = oldDirection.lerp(leaderDirection, blend);
                if (mixed.lengthSqr() < 1.0E-10) {
                    mixed = leaderDirection;
                }
                Vec3 nextDirection = mixed.normalize();
                this.precisionFollowDirectionDelta[i] = nextDirection.subtract(oldDirection);
                this.precisionFollowDirections[i] = nextDirection;
            }
            this.precisionFollowDirectionDelta[0] = sourceDirection.subtract(this.precisionFollowDirections[0]);
            this.precisionFollowDirections[0] = sourceDirection;

            double rootRadius = Mth.clamp(this.points[0].distanceTo(center), 0.45, 1.15);
            double accumulatedLength = 0.0;
            double carryScale = momentumCarry
                    ? Mth.lerp(LightningWhipPhysics.smoothstep(Mth.clamp(rawProgress / 0.18, 0.0, 1.0)), 0.72, 1.0)
                    : 1.0;

            Vec3 rootTarget = center.add(sourceDirection.scale(rootRadius));
            Vec3 rootAcceleration = rootTarget.subtract(this.points[0])
                    .scale(PRECISION_FOLLOW_POSITION_ACCEL * 0.42);
            double rootAccelLength = rootAcceleration.length();
            if (rootAccelLength > PRECISION_FOLLOW_MAX_ACCEL * 0.55) {
                rootAcceleration = rootAcceleration.scale(
                        (PRECISION_FOLLOW_MAX_ACCEL * 0.55) / rootAccelLength);
            }
            this.points[0] = this.points[0].add(rootAcceleration.scale(substepSecondsSqr));

            for (int i = 1; i < POINTS; ++i) {
                accumulatedLength += LightningWhipGeometry.REST_LENGTHS[i - 1] * PHYSICAL_REST_SCALE;
                double taper = i / (double)(POINTS - 1);
                double targetRadius = rootRadius + accumulatedLength;
                double gate = this.precisionCrosshairGate(rawProgress, taper);

                Vec3 followedDirection = this.precisionFollowDirections[i];
                Vec3 desiredDirection = followedDirection.lerp(aim, gate);
                if (desiredDirection.lengthSqr() < 1.0E-10) {
                    desiredDirection = aim;
                } else {
                    desiredDirection = desiredDirection.normalize();
                }

                Vec3 targetPoint = center.add(desiredDirection.scale(targetRadius));
                Vec3 pathError = targetPoint.subtract(this.points[i]);
                Vec3 relative = this.points[i].subtract(center);
                double radialError = targetRadius - relative.length();
                Vec3 desiredAngularVelocity = this.precisionFollowDirectionDelta[i]
                        .scale(targetRadius / dt);
                Vec3 currentVelocity = this.points[i].subtract(this.previous[i]).scale(1.0 / dt);
                Vec3 velocityError = desiredAngularVelocity.subtract(currentVelocity);
                Vec3 fromEye = this.points[i].subtract(center);
                double rayDistance = Math.max(0.35, fromEye.dot(aim));
                Vec3 crosshairPoint = center.add(aim.scale(rayDistance));
                Vec3 crosshairError = crosshairPoint.subtract(this.points[i]);

                double tipGain = Mth.lerp(taper, 1.0, PRECISION_FOLLOW_TIP_GAIN);
                double outwardEnvelope = smoothstep(Mth.clamp((rawProgress - 0.22) / 0.20, 0.0, 1.0))
                        * (1.0 - smoothstep(Mth.clamp((rawProgress - 0.60) / 0.12, 0.0, 1.0)));
                double tailWeight = Math.pow(taper, 1.55);
                Vec3 sweepTangent = this.precisionSweepTangentDirection(aim, rawProgress, this.precisionSwingSign);
                Vec3 centrifugalDirection = relative.lengthSqr() > 1.0E-10
                        ? relative.normalize() : desiredDirection;
                double tailRelease = 1.0 - PRECISION_TAIL_CROSSHAIR_RELEASE
                        * tailWeight
                        * smoothstep(Mth.clamp((rawProgress - 0.38) / 0.26, 0.0, 1.0));
                Vec3 acceleration = pathError.scale(PRECISION_FOLLOW_POSITION_ACCEL)
                        .add(desiredDirection.scale(radialError * PRECISION_FOLLOW_RADIAL_ACCEL))
                        .add(velocityError.scale(PRECISION_FOLLOW_VELOCITY_ACCEL))
                        .add(crosshairError.scale(PRECISION_CROSSHAIR_GATE_ACCEL * gate * tailRelease))
                        .add(sweepTangent.scale(PRECISION_TANGENTIAL_ACCEL * outwardEnvelope * tailWeight))
                        .add(centrifugalDirection.scale(PRECISION_CENTRIFUGAL_ACCEL * outwardEnvelope * tailWeight))
                        .scale(carryScale * tipGain);
                double accelLength = acceleration.length();
                if (accelLength > PRECISION_FOLLOW_MAX_ACCEL) {
                    acceleration = acceleration.scale(PRECISION_FOLLOW_MAX_ACCEL / accelLength);
                }
                this.points[i] = this.points[i].add(acceleration.scale(substepSecondsSqr));
            }
        }

        private void initializePrecisionFollowDirections(Vec3 center, Vec3 fallbackDirection) {
            Vec3 last = fallbackDirection;
            for (int i = 0; i < POINTS; ++i) {
                Vec3 relative = this.points[i].subtract(center);
                Vec3 direction = relative.lengthSqr() > 1.0E-10 ? relative.normalize() : last;
                this.precisionFollowDirections[i] = direction;
                this.precisionFollowDirectionDelta[i] = Vec3.ZERO;
                last = direction;
            }
            this.precisionFollowInitialized = true;
        }

        private Vec3 precisionTrajectoryCenter(Player player, Vec3 attackDirection) {
            return player.getEyePosition();
        }

        private Vec3 precisionSweepTangentDirection(Vec3 aimDirection, double rawProgress, double swingSign) {
            Vec3 aim = aimDirection.lengthSqr() > 1.0E-10 ? aimDirection.normalize() : new Vec3(0.0, 0.0, 1.0);
            Vec3 referenceUp = Math.abs(aim.y) < 0.94
                    ? new Vec3(0.0, 1.0, 0.0) : new Vec3(0.0, 0.0, 1.0);
            Vec3 side = referenceUp.cross(aim);
            side = side.lengthSqr() > 1.0E-10 ? side.normalize() : new Vec3(1.0, 0.0, 0.0);
            double sign = swingSign >= 0.0 ? 1.0 : -1.0;
            double phase = Mth.clamp((rawProgress - PRECISION_CROSSHAIR_SOURCE_PROGRESS)
                    / PRECISION_CROSSHAIR_SWEEP_HALF_SPAN_PROGRESS, -1.0, 1.0);
            double angle = phase * PRECISION_CROSSHAIR_SWEEP_HALF_ANGLE_RADIANS * sign;
            Vec3 tangent = aim.scale(-Math.sin(angle)).add(side.scale(Math.cos(angle))).scale(sign);
            return tangent.lengthSqr() > 1.0E-10 ? tangent.normalize() : side.scale(sign);
        }

        private Vec3 precisionCrosshairSweepDirection(Player player, Vec3 attackDirection,
                                                       double rawProgress, double swingSign) {
            Vec3 aim = attackDirection.lengthSqr() > 1.0E-10
                    ? attackDirection.normalize() : LightningWhipPhysics.aimDirection(player);
            Vec3 referenceUp = Math.abs(aim.y) < 0.94
                    ? new Vec3(0.0, 1.0, 0.0) : new Vec3(0.0, 0.0, 1.0);
            Vec3 side = referenceUp.cross(aim);
            if (side.lengthSqr() < 1.0E-10) {
                side = new Vec3(1.0, 0.0, 0.0);
            } else {
                side = side.normalize();
            }
            double phase = Mth.clamp(
                    (rawProgress - PRECISION_CROSSHAIR_SOURCE_PROGRESS)
                            / PRECISION_CROSSHAIR_SWEEP_HALF_SPAN_PROGRESS,
                    -1.0, 1.0);
            double signedAngle = phase * PRECISION_CROSSHAIR_SWEEP_HALF_ANGLE_RADIANS
                    * (swingSign >= 0.0 ? 1.0 : -1.0);
            Vec3 direction = aim.scale(Math.cos(signedAngle)).add(side.scale(Math.sin(signedAngle)));
            return direction.lengthSqr() > 1.0E-10 ? direction.normalize() : aim;
        }

        private double precisionCrosshairGate(double rawProgress, double chainTaper) {
            double delayProgress = (PRECISION_FOLLOW_TOTAL_DELAY_SECONDS / PRECISION_ATTACK_SECONDS)
                    * Mth.clamp(chainTaper, 0.0, 1.0);
            double crossingProgress = PRECISION_CROSSHAIR_SOURCE_PROGRESS + delayProgress;
            double distance = (rawProgress - crossingProgress) / PRECISION_CROSSHAIR_GATE_WIDTH_PROGRESS;
            return Math.exp(-distance * distance);
        }

        private void updateCoilState(Player player, long gameTime, boolean handleDriveActive, Vec3 root) {
            if (handleDriveActive) {
                this.lastAttackDriveGameTime = gameTime;
                if (this.coilProgress > 0.0f) {
                    this.coilProgress = 0.0f;
                    this.highFidelityTicksRemaining = Math.max(this.highFidelityTicksRemaining, 24);
                }
                return;
            }
            if (this.lastAttackDriveGameTime == Long.MIN_VALUE) {
                this.coilProgress = 1.0f;
            } else if (gameTime - this.lastAttackDriveGameTime >= 100L) {
                if (this.coilProgress < 1.0f) {
                    this.coilProgress = Math.min(1.0f, this.coilProgress + 0.027777778f);
                    this.highFidelityTicksRemaining = Math.max(this.highFidelityTicksRemaining, 10);
                }
            } else {
                this.coilProgress = 0.0f;
            }
            if (this.coilProgress > 1.0E-4f) {
                WhipCoilPose.build(root, player, this.arm, this.capturedHandleAxis, LightningWhipGeometry.REST_LENGTHS, 1.0, 3, 5.0, 0.05, this.coilTargets);
            }
        }

        private void applyCoilGuidance() {
            for (int i = 1; i < 57; ++i) {
                double activation = this.coilActivationForPoint(i);
                if (activation <= 1.0E-6) continue;
                Vec3 delta = this.coilTargets[i].subtract(this.points[i]);
                double distance = delta.length();
                if (distance > 1.0E-8) {
                    double desired = Math.min(distance * 0.17 * activation, 0.105 * activation);
                    this.points[i] = this.points[i].add(delta.scale(desired / distance));
                }
                Vec3 velocity = this.points[i].subtract(this.previous[i]);
                double retention = 1.0 - 0.5 * activation;
                this.previous[i] = this.points[i].subtract(velocity.scale(retention));
            }
        }

        private double coilActivationForPoint(int pointIndex) {
            if (this.coilProgress <= 0.0f || pointIndex <= 0) {
                return 0.0;
            }
            double fromRoot = ((double)pointIndex - 1.0) / Math.max(1.0, 55.0);
            double activation = Mth.clamp(((double)this.coilProgress - fromRoot * 0.82) / 0.18, 0.0, 1.0);
            return activation * activation * (3.0 - 2.0 * activation);
        }

        private void initialize(ClientLevel level, Vec3 root, Player player) {
            WhipCoilPose.build(root, player, this.arm, this.capturedHandleAxis, LightningWhipGeometry.REST_LENGTHS, 1.0, 3, 5.0, 0.05, this.coilTargets);
            for (int i = 0; i < 57; ++i) {
                Vec3 point;
                this.points[i] = point = this.coilTargets[i];
                this.previous[i] = point;
                this.renderPrevious[i] = point;
            }
            this.coilProgress = 1.0f;
            this.lastAttackDriveGameTime = Long.MIN_VALUE;
            this.precisionFollowInitialized = false;
            this.initialized = true;
        }

        private Vec3 initialDirection(ClientLevel level, Player player, Vec3 root, Vec3 forward) {
            double clearance;
            double total = 0.0;
            for (float restLength : LightningWhipGeometry.REST_LENGTHS) {
                total += (double)restLength * 1.0;
            }
            BlockHitResult floorHit = level.clip(new ClipContext(root, root.add(0.0, -total - 0.25, 0.0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (floorHit.getType() != HitResult.Type.MISS && (clearance = Math.max(0.1, root.y - floorHit.getLocation().y - 0.05)) < total * 0.92) {
                double vertical = Mth.clamp(clearance / total * 0.7, 0.08, 0.3);
                return new Vec3(forward.x, -vertical, forward.z).normalize();
            }
            return new Vec3(forward.x * 0.16, -0.987, forward.z * 0.16).normalize();
        }

        private void predict(Vec3 anchor, double substepSecondsSqr, double velocityRetention, double substepSeconds) {
            this.points[0] = anchor;
            this.previous[0] = anchor;
            for (int i = 1; i < 57; ++i) {
                double maximumStep;
                Vec3 current = this.points[i];
                Vec3 velocity = current.subtract(this.previous[i]).scale(velocityRetention);
                double speed = velocity.length();
                if (speed > (maximumStep = LightningWhipPhysics.maximumVerletStep(i, substepSeconds))) {
                    velocity = velocity.scale(maximumStep / speed);
                }
                this.previous[i] = current;
                this.points[i] = current.add(velocity).add(0.0, GRAVITY * substepSecondsSqr, 0.0);
            }
        }

        private void solveDistance(int a, int b, double rest, double compliance, double[] lambdas, int lambdaIndex, boolean minimumOnly, double substepSecondsSqr) {
            Vec3 delta = this.points[b].subtract(this.points[a]);
            double distance = delta.length();
            if (distance < 1.0E-9 || minimumOnly && distance >= rest) {
                if (minimumOnly) {
                    lambdas[lambdaIndex] = 0.0;
                }
                return;
            }
            double weightA = LightningWhipPhysics.inverseMass(a);
            double weightB = LightningWhipPhysics.inverseMass(b);
            double alpha = compliance / substepSecondsSqr;
            double constraint = distance - rest;
            double deltaLambda = (-constraint - alpha * lambdas[lambdaIndex]) / (weightA + weightB + alpha);
            int n = lambdaIndex;
            lambdas[n] = lambdas[n] + deltaLambda;
            Vec3 direction = delta.scale(1.0 / distance);
            if (weightA > 0.0) {
                this.points[a] = this.points[a].add(direction.scale(-weightA * deltaLambda));
            }
            if (weightB > 0.0) {
                this.points[b] = this.points[b].add(direction.scale(weightB * deltaLambda));
            }
        }

        private void enforceMaximumStretch() {
            for (int i = 0; i < 56; ++i) {
                double weightB;
                double weightA;
                double totalWeight;
                double maximum;
                Vec3 delta = this.points[i + 1].subtract(this.points[i]);
                double distance = delta.length();
                if (distance <= (maximum = (double)LightningWhipGeometry.REST_LENGTHS[i] * PHYSICAL_REST_SCALE * MAX_SEGMENT_STRETCH) || distance < 1.0E-9 || (totalWeight = (weightA = LightningWhipPhysics.inverseMass(i)) + (weightB = LightningWhipPhysics.inverseMass(i + 1))) <= 0.0) continue;
                Vec3 correction = delta.scale((distance - maximum) / distance);
                if (weightA > 0.0) {
                    this.points[i] = this.points[i].add(correction.scale(weightA / totalWeight));
                }
                if (!(weightB > 0.0)) continue;
                this.points[i + 1] = this.points[i + 1].subtract(correction.scale(weightB / totalWeight));
            }
        }

        private void enforceHandleAxis(Vec3 root) {
            double minimumDot;
            Vec3 sourceAxis;
            Vec3 vec3 = sourceAxis = this.precisionHandleAxisTicks > 0 && this.precisionHandleAxis.lengthSqr() > 1.0E-10 ? this.precisionHandleAxis : this.capturedHandleAxis;
            if (sourceAxis.lengthSqr() < 1.0E-10) {
                return;
            }
            Vec3 segment = this.points[1].subtract(root);
            double length = segment.length();
            if (length < 1.0E-9) {
                return;
            }
            Vec3 axis = sourceAxis.normalize();
            Vec3 direction = segment.scale(1.0 / length);
            double dot = Mth.clamp(direction.dot(axis), -1.0, 1.0);
            if (dot >= (minimumDot = Math.cos(HANDLE_ROOT_MAX_ANGLE))) {
                return;
            }
            Vec3 tangent = direction.subtract(axis.scale(dot));
            if (tangent.lengthSqr() < 1.0E-10) {
                tangent = Math.abs(axis.y) < 0.95 ? axis.cross(new Vec3(0.0, 1.0, 0.0)) : axis.cross(new Vec3(1.0, 0.0, 0.0));
            }
            tangent = tangent.normalize();
            Vec3 clampedDirection = axis.scale(minimumDot).add(tangent.scale(Math.sin(HANDLE_ROOT_MAX_ANGLE))).normalize();
            this.points[1] = root.add(clampedDirection.scale(length));
        }

        private void enforceAntiFold(double substepSecondsSqr) {
            for (int i = 1; i < 56; ++i) {
                double adjacent = (double)(LightningWhipGeometry.REST_LENGTHS[i - 1] + LightningWhipGeometry.REST_LENGTHS[i]) * 1.0;
                double taper = ((double)i - 1.0) / Math.max(1.0, 54.0);
                double minimumRatio = 0.28 + 0.62 * Math.pow(1.0 - taper, 1.55);
                minimumRatio = Mth.lerp(this.coilActivationForPoint(i + 1), minimumRatio, 0.4);
                this.solveDistance(i - 1, i + 1, adjacent * minimumRatio, BEND_COMPLIANCE, this.bendLambda, i - 1, true, substepSecondsSqr);
            }
        }

        private void enforceHandleBendZone() {
            int reinforced = Math.min(20, 55);
            for (int i = 1; i <= reinforced; ++i) {
                double adjacent = (double)(LightningWhipGeometry.REST_LENGTHS[i - 1] + LightningWhipGeometry.REST_LENGTHS[i]) * 1.0;
                double t = ((double)i - 1.0) / Math.max(1.0, (double)reinforced - 1.0);
                double baseMinimumRatio = Mth.lerp(t, 0.97, 0.78);
                double minimumRatio = 1.0 - (1.0 - baseMinimumRatio) / HANDLE_BEND_STIFFNESS_MULTIPLIER;
                minimumRatio = Mth.lerp(this.coilActivationForPoint(i + 1), minimumRatio, 0.34);
                this.enforceMinimumSpan(i - 1, i + 1, adjacent * minimumRatio);
            }
        }

        private void enforceHandleContinuity() {
            int[] checkpoints = new int[]{3, 4, 6, 8, 12, 16};
            double[] ratios = new double[]{0.7, 0.58, 0.5, 0.42, 0.36, 0.32};
            double accumulated = 0.0;
            int next = 0;
            for (int i = 0; i < Math.min(20, 56); ++i) {
                accumulated += (double)LightningWhipGeometry.REST_LENGTHS[i] * 1.0;
                int count = i + 1;
                if (next >= checkpoints.length || count != checkpoints[next]) continue;
                double ratio = Mth.lerp(this.coilActivationForPoint(count), ratios[next], 0.08);
                this.enforceMinimumSpan(0, count, accumulated * ratio);
                ++next;
            }
        }

        private void enforceMinimumSpan(int a, int b, double minimumSpan) {
            Vec3 direction;
            Vec3 delta = this.points[b].subtract(this.points[a]);
            double distance = delta.length();
            if (distance >= minimumSpan) {
                return;
            }
            if (distance < 1.0E-8) {
                Vec3 vec3 = direction = b > 1 ? this.points[b - 1].subtract(this.points[a]) : new Vec3(0.0, -1.0, 0.0);
                if (direction.lengthSqr() < 1.0E-10) {
                    direction = new Vec3(0.0, -1.0, 0.0);
                }
                direction = direction.normalize();
            } else {
                direction = delta.scale(1.0 / distance);
            }
            double weightA = LightningWhipPhysics.inverseMass(a);
            double weightB = LightningWhipPhysics.inverseMass(b);
            double totalWeight = weightA + weightB;
            if (totalWeight <= 0.0) {
                return;
            }
            Vec3 correction = direction.scale(minimumSpan - distance);
            if (weightA > 0.0) {
                this.points[a] = this.points[a].add(correction.scale(-weightA / totalWeight));
            }
            if (weightB > 0.0) {
                this.points[b] = this.points[b].add(correction.scale(weightB / totalWeight));
            }
        }

        private void solveSelfCollision() {
            for (int i = 1; i < 57; ++i) {
                for (int j = i + 3; j < 57; ++j) {
                    double weightB;
                    double packed = Math.min(this.coilActivationForPoint(i), this.coilActivationForPoint(j));
                    double minimumDistance = Mth.lerp(packed, SELF_COLLISION_DISTANCE, SELF_COLLISION_DISTANCE * COIL_SELF_COLLISION_SCALE);
                    double minimumSqr = minimumDistance * minimumDistance;
                    Vec3 delta = this.points[j].subtract(this.points[i]);
                    double distanceSqr = delta.lengthSqr();
                    if (distanceSqr >= minimumSqr || distanceSqr < 1.0E-12) continue;
                    double distance = Math.sqrt(distanceSqr);
                    double weightA = LightningWhipPhysics.inverseMass(i);
                    double totalWeight = weightA + (weightB = LightningWhipPhysics.inverseMass(j));
                    if (totalWeight <= 0.0) continue;
                    Vec3 correction = delta.scale((minimumDistance - distance) / distance);
                    this.points[i] = this.points[i].add(correction.scale(-weightA / totalWeight));
                    this.points[j] = this.points[j].add(correction.scale(weightB / totalWeight));
                }
            }
        }

        private int chooseAdaptiveSubsteps(boolean handleDriveActive, boolean chargePhase, boolean releasePhase, AttackDrive driveMode) {
            double maxSpeed = 0.0;
            double dt = Math.max(1.0E-6, this.lastSubstepSeconds);
            for (int i = 1; i < 57; ++i) {
                maxSpeed = Math.max(maxSpeed, this.points[i].subtract(this.previous[i]).length() / dt);
            }
            int steps = maxSpeed < 28.0 ? 6 : (maxSpeed < 58.0 ? 8 : (maxSpeed < 105.0 ? 12 : (maxSpeed < 165.0 ? 18 : 24)));
            if (releasePhase) {
                steps = Math.max(steps, 18);
            } else if (chargePhase || handleDriveActive && driveMode == AttackDrive.PRECISION) {
                steps = Math.max(steps, 12);
            } else if (this.highFidelityTicksRemaining > 0) {
                steps = Math.max(steps, 8);
            }
            return Mth.clamp(steps, MIN_SUBSTEPS, MAX_SUBSTEPS);
        }

        private double configureAdaptiveTimestep(int substeps) {
            double newDt = 0.05 / (double)substeps;
            if (Math.abs(newDt - this.lastSubstepSeconds) > 1.0E-12) {
                double ratio = newDt / this.lastSubstepSeconds;
                for (int i = 1; i < 57; ++i) {
                    Vec3 displacement = this.points[i].subtract(this.previous[i]).scale(ratio);
                    this.previous[i] = this.points[i].subtract(displacement);
                }
            }
            this.lastSubstepSeconds = newDt;
            return newDt;
        }

        private void solveBlockCapsuleContacts(WhipBlockCollision.SegmentEnvironment[] broadphase, Vec3[] before, double tipScale, double tangentRetention, boolean updateVelocity) {
            for (int i = 0; i < 56; ++i) {
                double invB;
                double radius = LightningWhipGeometry.SEGMENT_COLLIDER_RADIUS[i] * (i == 55 ? Math.max(1.0, tipScale) : 1.0);
                WhipBlockCollision.SegmentContact contact = WhipBlockCollision.findContact(broadphase[i], before[i], before[i + 1], this.points[i], this.points[i + 1], radius, updateVelocity ? tangentRetention : 1.0);
                if (contact == null || contact.correction().lengthSqr() < 1.0E-16) continue;
                double u = Mth.clamp(contact.sample(), 0.0, 1.0);
                double ga = 1.0 - u;
                double gb = u;
                double invA = LightningWhipPhysics.inverseMass(i);
                double denominator = ga * ga * invA + gb * gb * (invB = LightningWhipPhysics.inverseMass(i + 1));
                if (denominator <= 1.0E-12) continue;
                if (invA > 0.0) {
                    this.points[i] = this.points[i].add(contact.correction().scale(ga * invA / denominator));
                }
                if (invB > 0.0) {
                    this.points[i + 1] = this.points[i + 1].add(contact.correction().scale(gb * invB / denominator));
                }
                if (!updateVelocity) continue;
                this.dampInwardVelocity(i, contact.normal(), tangentRetention);
                this.dampInwardVelocity(i + 1, contact.normal(), tangentRetention);
            }
        }

        private void dampInwardVelocity(int index, Vec3 normal, double tangentRetention) {
            if (index <= 0) {
                return;
            }
            Vec3 velocity = this.points[index].subtract(this.previous[index]);
            double inward = velocity.dot(normal);
            if (inward < 0.0) {
                velocity = velocity.subtract(normal.scale(inward));
            }
            velocity = velocity.scale(tangentRetention);
            this.previous[index] = this.points[index].subtract(velocity);
        }

        private void depenetrateFromBlocks(ClientLevel level) {
            for (int i = 1; i < 57; ++i) {
                Vec3 push;
                for (int pass = 0; pass < 8 && (push = this.nearestDepenetration(level, this.points[i], 0.025)) != null && !(push.lengthSqr() < 1.0E-14); ++pass) {
                    Vec3 normal;
                    Vec3 oldPoint = this.points[i];
                    Vec3 corrected = oldPoint.add(push);
                    Vec3 velocity = oldPoint.subtract(this.previous[i]);
                    double normalVelocity = velocity.dot(normal = push.normalize());
                    if (normalVelocity < 0.0) {
                        velocity = velocity.subtract(normal.scale(normalVelocity));
                    }
                    this.points[i] = corrected;
                    this.previous[i] = corrected.subtract(velocity);
                }
            }
        }

        private Vec3 nearestDepenetration(ClientLevel level, Vec3 point, double radius) {
            Vec3 best = null;
            int minX = Mth.floor(point.x - radius);
            int minY = Mth.floor(point.y - radius);
            int minZ = Mth.floor(point.z - radius);
            int maxX = Mth.floor(point.x + radius);
            int maxY = Mth.floor(point.y + radius);
            int maxZ = Mth.floor(point.z + radius);
            ArrayList<AABB> solids = new ArrayList<AABB>();
            for (BlockPos blockPos : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
                BlockState state = level.getBlockState(blockPos);
                VoxelShape shape = state.getCollisionShape(level, blockPos);
                if (shape.isEmpty()) continue;
                for (AABB local : shape.toAabbs()) {
                    solids.add(local.move(blockPos.getX(), blockPos.getY(), blockPos.getZ()).inflate(radius));
                }
            }
            ArrayList<Vec3> candidates = new ArrayList<Vec3>();
            for (AABB box : solids) {
                if (!box.contains(point)) continue;
                candidates.add(new Vec3(box.minX - point.x - 1.0E-4, 0.0, 0.0));
                candidates.add(new Vec3(box.maxX - point.x + 1.0E-4, 0.0, 0.0));
                candidates.add(new Vec3(0.0, box.minY - point.y - 1.0E-4, 0.0));
                candidates.add(new Vec3(0.0, box.maxY - point.y + 1.0E-4, 0.0));
                candidates.add(new Vec3(0.0, 0.0, box.minZ - point.z - 1.0E-4));
                candidates.add(new Vec3(0.0, 0.0, box.maxZ - point.z + 1.0E-4));
            }
            if (candidates.isEmpty()) {
                return null;
            }

            int bestOverlapCount = Integer.MAX_VALUE;
            double bestDistanceSqr = Double.MAX_VALUE;
            for (Vec3 candidate : candidates) {
                Vec3 moved = point.add(candidate);
                int overlaps = 0;
                for (AABB box : solids) {
                    if (!box.contains(moved)) continue;
                    ++overlaps;
                }
                double distanceSqr = candidate.lengthSqr();
                if (overlaps >= bestOverlapCount && (overlaps != bestOverlapCount || !(distanceSqr < bestDistanceSqr))) continue;
                bestOverlapCount = overlaps;
                bestDistanceSqr = distanceSqr;
                best = candidate;
            }
            return best;
        }

        Vec3 attachmentAnchor(int segmentIndex, float segmentU, float partialTick) {
            int segment = Mth.clamp(segmentIndex, 0, 55);
            double u = Mth.clamp(segmentU, 0.0f, 1.0f);
            double t = Mth.clamp(partialTick, 0.0f, 1.0f);
            Vec3 interpolatedRoot = this.renderPrevious[0].lerp(this.points[0], t);
            long gameTime = this.holder != null ? this.holder.level().getGameTime() : this.capturedAnchorGameTime;
            Vec3 displayAnchor = this.renderDisplayAnchor(interpolatedRoot, gameTime);
            Vec3 rootCorrection = displayAnchor.subtract(interpolatedRoot);
            double startRootWeight = 1.0 - (double)segment / 56.0;
            double endRootWeight = 1.0 - ((double)segment + 1.0) / 56.0;
            Vec3 start = this.renderPrevious[segment].lerp(this.points[segment], t).add(rootCorrection.scale(startRootWeight));
            Vec3 end = this.renderPrevious[segment + 1].lerp(this.points[segment + 1], t).add(rootCorrection.scale(endRootWeight));
            return start.lerp(end, u);
        }

        void render(ClientLevel level, PoseStack stack, VertexConsumer consumer, Vec3 camera, float partialTick) {
            Vec3 interpolatedRoot = this.renderPrevious[0].lerp(this.points[0], partialTick);
            Vec3 displayAnchor = this.renderDisplayAnchor(interpolatedRoot, level.getGameTime());
            this.renderAtDisplayAnchor(level, stack, consumer, camera, partialTick, displayAnchor);
        }

        void renderFirstPersonWorld(ClientLevel level, PoseStack stack, VertexConsumer consumer, Camera viewCamera, float partialTick, long gameTime) {
            Vec3 interpolatedRoot = this.renderPrevious[0].lerp(this.points[0], partialTick);
            Vec3 fallback = this.renderDisplayAnchor(interpolatedRoot, gameTime);
            Vec3 displayAnchor = this.firstPersonDisplayAnchor(viewCamera, gameTime, partialTick, fallback);
            Vec3 rootCorrection = displayAnchor.subtract(interpolatedRoot);
            double pointOneRootWeight = 0.9821428571428571;
            this.firstPersonBridgeTargetWorld = this.renderPrevious[1].lerp(this.points[1], partialTick).add(rootCorrection.scale(pointOneRootWeight));
            this.firstPersonBridgeTargetGameTime = gameTime;
            this.renderSolvedShape(level, stack, consumer, viewCamera.getPosition(), this.renderPrevious, this.points, partialTick, rootCorrection, this.tipVisualScale, 255, 1);
        }

        private void renderAtDisplayAnchor(ClientLevel level, PoseStack stack, VertexConsumer consumer, Vec3 camera, float partialTick, Vec3 displayAnchor) {
            Vec3 interpolatedRoot = this.renderPrevious[0].lerp(this.points[0], partialTick);
            Vec3 rootCorrection = displayAnchor.subtract(interpolatedRoot);
            WhipPerformanceTuning.pushGeometryQuality(this.holder);
            try {
                this.renderSolvedShape(level, stack, consumer, camera, this.renderPrevious, this.points, partialTick, rootCorrection, this.tipVisualScale, 255, 0);
            }
            finally {
                WhipPerformanceTuning.popGeometryQuality();
            }
        }

        private Vec3[] buildLengthPreservingDisplayWorld(Vec3[] fromPoints, Vec3[] toPoints,
                                                         float partialTick, Vec3 rootCorrection) {
            Vec3[] raw = new Vec3[POINTS];
            for (int i = 0; i < POINTS; ++i) {
                raw[i] = fromPoints[i].lerp(toPoints[i], partialTick)
                        .add(rootCorrection.scale(1.0 - i / (double)SEGMENTS));
            }

            Vec3[] display = new Vec3[POINTS];
            display[0] = raw[0];
            Vec3 lastDirection = Vec3.ZERO;
            for (int i = 0; i < SEGMENTS; ++i) {
                Vec3 direction = raw[i + 1].subtract(raw[i]);
                if (direction.lengthSqr() < 1.0E-10) {
                    direction = toPoints[i + 1].subtract(toPoints[i]);
                }
                if (direction.lengthSqr() < 1.0E-10) {
                    direction = fromPoints[i + 1].subtract(fromPoints[i]);
                }
                if (direction.lengthSqr() < 1.0E-10) {
                    direction = lastDirection.lengthSqr() > 1.0E-10 ? lastDirection : new Vec3(0.0, 0.0, 1.0);
                }
                direction = direction.normalize();
                lastDirection = direction;
                display[i + 1] = display[i].add(direction.scale(LightningWhipGeometry.REST_LENGTHS[i]));
            }
            return display;
        }

        private Vec3[] currentVisualWorldShape(float partialTick, long gameTime) {
            Vec3 interpolatedRoot = this.renderPrevious[0].lerp(this.points[0], partialTick);
            Vec3 displayAnchor = this.renderDisplayAnchor(interpolatedRoot, gameTime);
            Vec3 rootCorrection = displayAnchor.subtract(interpolatedRoot);
            return this.buildLengthPreservingDisplayWorld(this.renderPrevious, this.points, partialTick, rootCorrection);
        }

        private void renderSolvedShape(ClientLevel level, PoseStack stack, VertexConsumer consumer, Vec3 camera, Vec3[] fromPoints, Vec3[] toPoints, float partialTick, Vec3 rootCorrection, float renderedTipScale, int alpha, int firstSegment) {
            Vec3[] worldDisplay = this.buildLengthPreservingDisplayWorld(fromPoints, toPoints, partialTick, rootCorrection);
            Vec3[] cameraRelative = new Vec3[POINTS];
            for (int i = 0; i < POINTS; ++i) {
                cameraRelative[i] = worldDisplay[i].subtract(camera);
            }
            LightningWhipVfx.render(stack, consumer, cameraRelative,
                    this.holder.getId() * 31 + this.arm.ordinal());
        }

        private TrailRenderData buildElectricTipTrail(float partialTick, long gameTime) {
            if (this.tipTrail.isEmpty()) return null;
            double renderTime = gameTime + Mth.clamp(partialTick, 0.0f, 1.0f);
            ArrayList<TipTrailSample> visibleSamples = new ArrayList<>();
            for (TipTrailSample sample : this.tipTrail) {
                if (sample.sampleTime() > renderTime + 1.0E-4) continue;
                double age = renderTime - sample.sampleTime();
                if (age < -1.0E-4 || age > TIP_TRAIL_LIFETIME_TICKS + 0.75) continue;
                visibleSamples.add(sample);
            }
            if (visibleSamples.isEmpty()) return null;

            Vec3[] lash = this.currentVisualWorldShape(partialTick, gameTime);
            Vec3 tip = lash[SEGMENTS];
            Vec3 lashTangent = tip.subtract(lash[SEGMENTS - 1]);
            if (lashTangent.lengthSqr() < 1.0E-10) return null;
            lashTangent = lashTangent.normalize();

            int anchorIndex = visibleSamples.size() - 1;
            for (int i = visibleSamples.size() - 1; i >= 0; --i) {
                anchorIndex = i;
                if (visibleSamples.get(i).position().distanceTo(tip) >= TIP_TRAIL_BRIDGE_MIN_SPAN) break;
            }

            ArrayList<Vec3> arc = new ArrayList<>();
            ArrayList<Float> visibility = new ArrayList<>();
            for (int i = 0; i <= anchorIndex; ++i) {
                TipTrailSample sample = visibleSamples.get(i);
                arc.add(sample.position());
                visibility.add(this.tipTrailAgeFade(sample, renderTime));
            }

            Vec3 anchor = visibleSamples.get(anchorIndex).position();
            Vec3 anchorTangent;
            if (anchorIndex > 0) {
                anchorTangent = anchor.subtract(visibleSamples.get(anchorIndex - 1).position());
            } else {
                anchorTangent = tip.subtract(anchor);
            }
            if (anchorTangent.lengthSqr() < 1.0E-10) anchorTangent = lashTangent;
            else anchorTangent = anchorTangent.normalize();

            double gap = anchor.distanceTo(tip);
            double neckLength = Mth.clamp(gap * 0.34, TIP_TRAIL_TANGENT_NECK_MIN, TIP_TRAIL_TANGENT_NECK_MAX);

            Vec3 neck = tip.add(lashTangent.scale(neckLength));
            double curveSpan = Math.max(0.08, Math.min(0.42, anchor.distanceTo(neck) * 0.45));
            Vec3 c1 = anchor.add(anchorTangent.scale(curveSpan));

            Vec3 c2 = neck.add(lashTangent.scale(curveSpan * 0.72));
            float anchorFade = this.tipTrailAgeFade(visibleSamples.get(anchorIndex), renderTime);

            for (int step = 1; step <= TIP_TRAIL_BRIDGE_STEPS; ++step) {
                double t = step / (double)(TIP_TRAIL_BRIDGE_STEPS + 1);
                Vec3 p = cubicBezier(anchor, c1, c2, neck, t);
                arc.add(p);
                visibility.add(Mth.lerp((float)t, anchorFade, 1.0f));
            }
            arc.add(neck);
            visibility.add(1.0f);

            arc.add(tip);
            visibility.add(1.0f);

            return new TrailRenderData(arc.toArray(Vec3[]::new), toFloatArray(visibility));
        }

        private float tipTrailAgeFade(TipTrailSample sample, double renderTime) {
            double age = Math.max(0.0, renderTime - sample.sampleTime());
            return (float)Math.pow(Mth.clamp(1.0 - age / 5.5, 0.0, 1.0), 1.35);
        }

        private static Vec3 cubicBezier(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double t) {
            double u = 1.0 - t;
            return p0.scale(u * u * u)
                    .add(p1.scale(3.0 * u * u * t))
                    .add(p2.scale(3.0 * u * t * t))
                    .add(p3.scale(t * t * t));
        }

        private static float[] toFloatArray(ArrayList<Float> values) {
            float[] out = new float[values.size()];
            for (int i = 0; i < out.length; ++i) out[i] = values.get(i);
            return out;
        }

        void renderElectricTipTrail(PoseStack stack, VertexConsumer consumer, Vec3 camera,
                                    float partialTick, long gameTime) {
            TrailRenderData trail = this.buildElectricTipTrail(partialTick, gameTime);
            if (trail == null || trail.points().length < 2) return;
            Vec3[] cameraRelative = new Vec3[trail.points().length];
            for (int i = 0; i < cameraRelative.length; ++i) {
                cameraRelative[i] = trail.points()[i].subtract(camera);
            }

            LightningWhipVfx.renderArc(stack, consumer, cameraRelative,
                    this.holder.getId() * 131 + this.arm.ordinal() * 17 + 7,
                    0.92f, 0.494f, LightningWhipVfx.Style.LINK, trail.visibility());
        }

        void renderFirstPersonElectricTipTrail(PoseStack stack, VertexConsumer consumer, Camera camera,
                                               Matrix4f inverseHand, float partialTick, long gameTime) {
            TrailRenderData trail = this.buildElectricTipTrail(partialTick, gameTime);
            if (trail == null || trail.points().length < 2) return;
            Vec3[] local = new Vec3[trail.points().length];
            float[] visibility = trail.visibility().clone();
            Vec3 crosshairDirection = this.holder != null
                    ? this.holder.getViewVector(partialTick) : new Vec3(0.0, 0.0, 1.0);
            if (crosshairDirection.lengthSqr() > 1.0E-10) {
                crosshairDirection = crosshairDirection.normalize();
            }
            for (int i = 0; i < local.length; ++i) {
                Vec3 world = trail.points()[i];
                local[i] = firstPersonWorldPointToLocal(world, camera, inverseHand);
                visibility[i] *= firstPersonLightningVisibility(world, camera, crosshairDirection);
            }
            LightningWhipVfx.renderArc(stack, consumer, local,
                    this.holder.getId() * 131 + this.arm.ordinal() * 17 + 7,
                    0.92f, 0.494f, LightningWhipVfx.Style.LINK, visibility);
        }

        void renderTipTrail(PoseStack stack, VertexConsumer consumer, Vec3 camera, float partialTick, long gameTime) {
            if (this.tipTrail.size() < 2) {
                return;
            }
            ArrayList<TipTrailSample> samples = new ArrayList<TipTrailSample>(this.tipTrail);
            int count = samples.size();
            PoseStack.Pose pose = stack.last();
            for (int i = 0; i < count - 1; ++i) {
                TipTrailSample a = (TipTrailSample)samples.get(i);
                TipTrailSample b = (TipTrailSample)samples.get(i + 1);
                Vec3 start = a.position();
                Vec3 end = b.position();
                Vec3 tangent = end.subtract(start);
                double length = tangent.length();
                if (length < 1.0E-5) continue;
                Vec3 midpoint = start.lerp(end, 0.5);
                Vec3 toCamera = camera.subtract(midpoint);
                Vec3 side = tangent.cross(toCamera);
                if (side.lengthSqr() < 1.0E-10) {
                    side = tangent.cross(new Vec3(0.0, 1.0, 0.0));
                }
                if (side.lengthSqr() < 1.0E-10) continue;
                side = side.normalize();
                float ageFadeA = this.trailFade(a, i, count, gameTime, partialTick);
                float ageFadeB = this.trailFade(b, i + 1, count, gameTime, partialTick);
                if (ageFadeA <= 0.01f && ageFadeB <= 0.01f) continue;
                this.emitTrailQuad(pose, consumer, camera, start, end, side, 0.145f, ageFadeA, ageFadeB, false);
                this.emitTrailQuad(pose, consumer, camera, start, end, side, 0.052f, ageFadeA, ageFadeB, true);
            }
        }

        private float trailFade(TipTrailSample sample, int index, int count, long gameTime, float partialTick) {
            float age = (float)(gameTime - sample.sampleTime()) + Mth.clamp(partialTick, 0.0f, 1.0f);
            float timeFade = 1.0f - Mth.clamp(age / 5.0f, 0.0f, 1.0f);
            float indexFade = count <= 1 ? 1.0f : (float)index / (float)(count - 1);
            return Mth.clamp(timeFade * (0.22f + 0.78f * indexFade), 0.0f, 1.0f);
        }

        private void emitTrailQuad(PoseStack.Pose pose, VertexConsumer consumer, Vec3 camera, Vec3 start, Vec3 end, Vec3 side, float halfWidth, float fadeStart, float fadeEnd, boolean core) {
            Vec3 s0 = start.add(side.scale(halfWidth));
            Vec3 s1 = start.add(side.scale(-halfWidth));
            Vec3 e1 = end.add(side.scale(-halfWidth));
            Vec3 e0 = end.add(side.scale(halfWidth));
            int r = core ? 235 : 108;
            int g = core ? 255 : 255;
            int b = core ? 224 : 156;
            int a0 = (int)(Mth.clamp(fadeStart, 0.0f, 1.0f) * (float)(core ? 185 : 105));
            int a1 = (int)(Mth.clamp(fadeEnd, 0.0f, 1.0f) * (float)(core ? 205 : 128));
            this.trailVertex(pose, consumer, camera, s0, r, g, b, a0);
            this.trailVertex(pose, consumer, camera, s1, r, g, b, a0);
            this.trailVertex(pose, consumer, camera, e1, r, g, b, a1);
            this.trailVertex(pose, consumer, camera, e0, r, g, b, a1);
        }

        private void trailVertex(PoseStack.Pose pose, VertexConsumer consumer, Vec3 camera, Vec3 point, int red, int green, int blue, int alpha) {
            consumer.addVertex(pose.pose(), (float)(point.x - camera.x), (float)(point.y - camera.y), (float)(point.z - camera.z)).setColor(red, green, blue, Mth.clamp(alpha, 0, 255));
        }
    }

    private static final class ArmMotor {
        private float oldPitch = -0.38f;
        private float oldYaw;
        private float oldRoll;
        private float pitch = -0.38f;
        private float yaw;
        private float roll;
        private float pitchVelocity;
        private float yawVelocity;
        private float rollVelocity;
        private boolean wasSwinging;
        private int previousSwingTime;
        private int driveTick = -1;
        private int rightChargeTicks;
        private int rightSlamTick = -1;
        private AttackDrive activeMode = AttackDrive.NONE;
        private AttackDrive driveModeThisTick = AttackDrive.NONE;
        private Vec3 attackDirection = new Vec3(0.0, 0.0, 1.0);
        private float precisionSwingSign = 1.0f;
        private boolean precisionMomentumCarry;
        private boolean drivenThisTick;
        private boolean driveStartedThisTick;
        private boolean chargePhaseThisTick;
        private boolean releasePhaseThisTick;
        private boolean releaseStartedThisTick;
        private float chargeFromTicks;
        private float chargeToTicks;
        private float releaseFromProgress;
        private float releaseToProgress;
        private float driveFromRawProgress;
        private float driveToRawProgress;
        private int precisionWindupTicks = 3;
        private int precisionStrokeTicks = 4;
        private boolean requestAcceptedThisTick;
        private Vec3 previousPlayerVelocity = Vec3.ZERO;

        private ArmMotor() {
        }

        void tick(Player player, HumanoidArm arm, AttackDrive requestedMode, double requestedSwingSign, boolean requestedMomentumCarry) {
            boolean precisionRequested;
            float side = arm == HumanoidArm.RIGHT ? 1.0f : -1.0f;
            this.drivenThisTick = false;
            this.driveStartedThisTick = false;
            this.requestAcceptedThisTick = false;
            this.chargePhaseThisTick = false;
            this.releasePhaseThisTick = false;
            this.releaseStartedThisTick = false;
            this.driveModeThisTick = AttackDrive.NONE;
            this.chargeToTicks = 0.0f;
            this.chargeFromTicks = 0.0f;
            this.releaseToProgress = 0.0f;
            this.releaseFromProgress = 0.0f;
            this.driveToRawProgress = 0.0f;
            this.driveFromRawProgress = 0.0f;
            this.oldPitch = this.pitch;
            this.oldYaw = this.yaw;
            this.oldRoll = this.roll;
            boolean correctSwingArm = player.swinging && LightningWhipPhysics.armForHand(player, player.swingingArm) == arm;
            boolean swingTrigger = correctSwingArm && (!this.wasSwinging || player.swingTime < this.previousSwingTime);
            boolean rightUse = player.isUsingItem() && player.getUseItem().is((Item)ModItems.LIGHTNING_WHIP.get()) && LightningWhipPhysics.armForHand(player, player.getUsedItemHand()) == arm;
            boolean rightRequested = requestedMode == AttackDrive.CHARGED_SLAM;
            boolean bl = precisionRequested = requestedMode == AttackDrive.PRECISION;
            if ((rightRequested || rightUse) && this.activeMode == AttackDrive.NONE) {
                this.startDrive(AttackDrive.CHARGED_SLAM, player, side, 1.0, false);
                this.requestAcceptedThisTick = rightRequested;
            } else if ((precisionRequested || swingTrigger) && this.activeMode == AttackDrive.NONE) {
                this.startDrive(AttackDrive.PRECISION, player, side, requestedSwingSign, requestedMomentumCarry);
                this.requestAcceptedThisTick = precisionRequested;
            }
            Vec3 playerVelocity = player.getDeltaMovement();
            Vec3 acceleration = playerVelocity.subtract(this.previousPlayerVelocity);
            this.previousPlayerVelocity = playerVelocity;
            float inertial = (float)Mth.clamp(acceleration.horizontalDistance() * 0.22, -0.12, 0.12);
            float bodyTurn = Mth.wrapDegrees(player.yBodyRot - player.yBodyRotO) * ((float)Math.PI / 180);
            if (this.activeMode == AttackDrive.CHARGED_SLAM) {
                if (this.rightSlamTick < 0) {
                    if (rightUse || rightRequested) {
                        this.drivenThisTick = true;
                        this.driveModeThisTick = AttackDrive.CHARGED_SLAM;
                        this.chargePhaseThisTick = true;
                        this.attackDirection = LightningWhipPhysics.physicalForward(player);
                        this.chargeFromTicks = this.rightChargeTicks;
                        this.chargeToTicks = (float)this.rightChargeTicks + 1.0f;
                        ++this.rightChargeTicks;
                        ArmPose pose = LightningWhipPhysics.chargedChargeArmPose(this.chargeToTicks, side);
                        this.pitch = pose.pitch();
                        this.yaw = pose.yaw();
                        this.roll = pose.roll();
                    } else if (this.rightChargeTicks >= 60) {
                        this.attackDirection = LightningWhipPhysics.physicalForward(player);
                        this.rightSlamTick = 0;
                        this.releaseStartedThisTick = true;
                        this.drivenThisTick = true;
                        this.driveModeThisTick = AttackDrive.CHARGED_SLAM;
                        this.releasePhaseThisTick = true;
                        this.releaseFromProgress = 0.0f;
                        this.releaseToProgress = 0.071428575f;
                        ++this.rightSlamTick;
                        ArmPose pose = LightningWhipPhysics.chargedReleaseArmPose(this.releaseToProgress, side);
                        this.pitch = pose.pitch();
                        this.yaw = pose.yaw();
                        this.roll = pose.roll();
                    } else {
                        this.cancelChargedSlam();
                    }
                } else if (this.rightSlamTick < 14) {
                    this.drivenThisTick = true;
                    this.driveModeThisTick = AttackDrive.CHARGED_SLAM;
                    this.releasePhaseThisTick = true;
                    this.releaseFromProgress = (float)this.rightSlamTick / 14.0f;
                    this.releaseToProgress = ((float)this.rightSlamTick + 1.0f) / 14.0f;
                    ++this.rightSlamTick;
                    ArmPose pose = LightningWhipPhysics.chargedReleaseArmPose(this.releaseToProgress, side);
                    this.pitch = pose.pitch();
                    this.yaw = pose.yaw();
                    this.roll = pose.roll();
                } else {
                    this.finishDrive(side);
                }
            } else if (this.activeMode == AttackDrive.PRECISION && this.driveTick >= 0) {
                this.drivenThisTick = true;
                this.driveModeThisTick = AttackDrive.PRECISION;
                int totalPrecisionTicks = this.precisionWindupTicks + this.precisionStrokeTicks;
                this.driveFromRawProgress = LightningWhipPhysics.precisionRawProgressAtLightningTick(this.driveTick);
                this.driveToRawProgress = LightningWhipPhysics.precisionRawProgressAtLightningTick(this.driveTick + 1.0);
                ArmPose stroke = LightningWhipPhysics.precisionArmPose(this.driveToRawProgress, side, this.precisionSwingSign);
                this.pitch = stroke.pitch();
                this.yaw = stroke.yaw();
                this.roll = stroke.roll();
                if (++this.driveTick >= totalPrecisionTicks) {
                    this.finishDrive(side);
                }
            } else {
                float targetPitch = -0.38f;
                float targetYaw = 0.0f;
                float targetRoll = side * 0.08f;
                this.yawVelocity -= bodyTurn * 0.18f;
                this.rollVelocity += side * inertial;
                this.pitchVelocity = (this.pitchVelocity + (targetPitch - this.pitch) * 0.2f) * 0.72f;
                this.yawVelocity = (this.yawVelocity + (targetYaw - this.yaw) * 0.184f) * 0.72f;
                this.rollVelocity = (this.rollVelocity + (targetRoll - this.roll) * 0.176f) * 0.72f;
                this.pitch += this.pitchVelocity;
                this.yaw += this.yawVelocity;
                this.roll += this.rollVelocity;
                this.pitch = this.clampWithBounce(this.pitch, -3.08f, 1.1f, AxisComponent.PITCH);
                this.yaw = this.clampWithBounce(this.yaw, -1.35f, 1.35f, AxisComponent.YAW);
                this.roll = this.clampWithBounce(this.roll, -1.35f, 1.35f, AxisComponent.ROLL);
            }
            this.wasSwinging = correctSwingArm;
            this.previousSwingTime = player.swingTime;
        }

        private void startDrive(AttackDrive mode, Player player, float side, double requestedSwingSign, boolean requestedMomentumCarry) {
            this.activeMode = mode;
            this.attackDirection = mode == AttackDrive.PRECISION ? LightningWhipPhysics.aimDirection(player) : LightningWhipPhysics.physicalForward(player);
            this.precisionSwingSign = mode == AttackDrive.PRECISION && requestedSwingSign < 0.0 ? -1.0f : 1.0f;
            this.precisionMomentumCarry = mode == AttackDrive.PRECISION && requestedMomentumCarry;
            this.pitch = -0.38f;
            this.yaw = 0.0f;
            this.roll = side * 0.08f;
            this.oldPitch = this.pitch;
            this.oldYaw = this.yaw;
            this.oldRoll = this.roll;
            this.rollVelocity = 0.0f;
            this.yawVelocity = 0.0f;
            this.pitchVelocity = 0.0f;
            if (mode == AttackDrive.CHARGED_SLAM) {
                this.rightChargeTicks = 0;
                this.rightSlamTick = -1;
                this.driveTick = -1;
            } else {
                int attackPeriod = LightningWhipItem.attackPeriodTicks(player);

                this.precisionWindupTicks = Mth.clamp(
                        Math.min(PRECISION_WINDUP_TICKS, Math.max(1, attackPeriod - 2)),
                        1, PRECISION_WINDUP_TICKS);
                int availableStrokeTicks = Math.max(1, attackPeriod - this.precisionWindupTicks - 1);
                this.precisionStrokeTicks = Mth.clamp(
                        availableStrokeTicks, 1, PRECISION_STROKE_TICKS);
                this.driveTick = 0;
                this.rightChargeTicks = 0;
                this.rightSlamTick = -1;
            }
            this.driveStartedThisTick = true;
        }

        private void finishDrive(float side) {
            this.pitch = -0.38f;
            this.yaw = 0.0f;
            this.roll = side * 0.08f;
            this.rollVelocity = 0.0f;
            this.yawVelocity = 0.0f;
            this.pitchVelocity = 0.0f;
            this.driveTick = -1;
            this.rightChargeTicks = 0;
            this.rightSlamTick = -1;
            this.activeMode = AttackDrive.NONE;
            this.precisionMomentumCarry = false;
        }

        void cancelChargedSlam() {
            if (this.activeMode != AttackDrive.CHARGED_SLAM || this.rightSlamTick >= 0) {
                return;
            }
            this.driveTick = -1;
            this.rightChargeTicks = 0;
            this.rightSlamTick = -1;
            this.activeMode = AttackDrive.NONE;
            this.precisionMomentumCarry = false;
            this.rollVelocity = 0.0f;
            this.yawVelocity = 0.0f;
            this.pitchVelocity = 0.0f;
        }

        boolean wasDrivenThisTick() {
            return this.drivenThisTick;
        }

        boolean driveStartedThisTick() {
            return this.driveStartedThisTick;
        }

        boolean requestAcceptedThisTick() {
            return this.requestAcceptedThisTick;
        }

        boolean chargePhaseThisTick() {
            return this.chargePhaseThisTick;
        }

        boolean releasePhaseThisTick() {
            return this.releasePhaseThisTick;
        }

        boolean releaseStartedThisTick() {
            return this.releaseStartedThisTick;
        }

        AttackDrive driveModeThisTick() {
            return this.driveModeThisTick;
        }

        Vec3 attackDirection() {
            return this.attackDirection;
        }

        void overridePrecisionDirection(Vec3 direction) {
            if (this.activeMode == AttackDrive.PRECISION && LightningWhipPhysics.finite(direction) && direction.lengthSqr() > 1.0E-10) {
                this.attackDirection = direction.normalize();
            }
        }

        double precisionSwingSign() {
            return this.precisionSwingSign;
        }

        boolean precisionMomentumCarry() {
            return this.precisionMomentumCarry;
        }

        float chargeFromTicks() {
            return this.chargeFromTicks;
        }

        float chargeToTicks() {
            return this.chargeToTicks;
        }

        float releaseFromProgress() {
            return this.releaseFromProgress;
        }

        float releaseToProgress() {
            return this.releaseToProgress;
        }

        float driveFromRawProgress() {
            return this.driveFromRawProgress;
        }

        float driveToRawProgress() {
            return this.driveToRawProgress;
        }

        private float clampWithBounce(float value, float minimum, float maximum, AxisComponent component) {
            if (value < minimum) {
                this.bounce(component);
                return minimum;
            }
            if (value > maximum) {
                this.bounce(component);
                return maximum;
            }
            return value;
        }

        private void bounce(AxisComponent component) {
            switch (component.ordinal()) {
                case 0: {
                    this.pitchVelocity *= -0.24f;
                    break;
                }
                case 1: {
                    this.yawVelocity *= -0.24f;
                    break;
                }
                case 2: {
                    this.rollVelocity *= -0.24f;
                }
            }
        }

        ArmPose sample(float partialTick) {
            float partial = Mth.clamp(partialTick, 0.0f, 1.0f);
            return new ArmPose(Mth.lerp(partial, this.oldPitch, this.pitch), Mth.lerp(partial, this.oldYaw, this.yaw), Mth.lerp(partial, this.oldRoll, this.roll));
        }

        private static enum AxisComponent {
            PITCH,
            YAW,
            ROLL;

        }
    }

    private record ClientPrecisionRequest(Vec3 direction, Vec3 handleAxis, boolean momentumCarry, double swingSign) {
    }

    public record ArmPose(float pitch, float yaw, float roll) {
    }

    static final class RenderContext {
        final LivingEntity holder;
        final HumanoidArm arm;
        final boolean firstPerson;

        RenderContext(LivingEntity holder, HumanoidArm arm, boolean firstPerson) {
            this.holder = holder;
            this.arm = arm;
            this.firstPerson = firstPerson;
        }
    }

    private record TipTrailSample(Vec3 position, double sampleTime) {
    }

    private record TrailRenderData(Vec3[] points, float[] visibility) {
    }
}
