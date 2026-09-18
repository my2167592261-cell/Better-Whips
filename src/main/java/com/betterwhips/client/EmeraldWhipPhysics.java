package com.betterwhips.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import com.betterwhips.BetterWhipsMod;
import com.betterwhips.item.EmeraldWhipItem;
import com.betterwhips.registry.ModItems;
import com.betterwhips.network.EmeraldWhipNetwork;
import com.betterwhips.physics.EmeraldWhipDimensions;
import com.betterwhips.physics.WhipBlockCollision;
import com.betterwhips.physics.WhipMomentumContinuity;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.NeoForgeRenderTypes;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderArmEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class EmeraldWhipPhysics {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            BetterWhipsMod.MOD_ID, "textures/item/emerald_whip.png");
    private static final RenderType RENDER_TYPE = RenderType.entityTranslucent(TEXTURE);

    private static final RenderType SPECTRAL_RENDER_TYPE =
            NeoForgeRenderTypes.getUnlitTranslucent(TEXTURE);

    private static final RenderType TIP_TRAIL_RENDER_TYPE = RenderType.create(
            "better_whips_emerald_whip_tip_trail",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            16_384,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderType.RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(RenderType.LIGHTNING_TRANSPARENCY)
                    .setCullState(RenderType.NO_CULL)
                    .setDepthTestState(RenderType.LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(RenderType.COLOR_WRITE)
                    .setOutputState(RenderType.MAIN_TARGET)
                    .createCompositeState(false));

    private static final int SEGMENTS = EmeraldWhipGeometry.SEGMENT_COUNT;
    private static final int POINTS = SEGMENTS + 1;

    private static final int MIN_SUBSTEPS = 8;
    private static final int MAX_SUBSTEPS = 30;
    private static final int SOLVER_ITERATIONS = 7;
    private static final int HIGH_FIDELITY_TICKS = 28;
    private static final double TICK_SECONDS = 1.0D / 20.0D;

    private static final double PHYSICAL_REST_SCALE = EmeraldWhipDimensions.DEPLOYED_REST_SCALE;

    private static final double STORED_REST_SCALE = EmeraldWhipDimensions.STORED_REST_SCALE;
    private static final double MAX_SEGMENT_STRETCH = 1.0030D;

    private static final double LENGTH_COMPLIANCE = 3.0E-8D;

    private static final double BEND_COMPLIANCE = 5.2E-7D;

    private static final double TICK_VELOCITY_RETENTION = 0.9950D;
    private static final double GRAVITY = -21.5D;
    private static final double COLLISION_RADIUS = 0.050D;
    private static final int DEPENETRATION_PASSES = 8;
    private static final double DEPENETRATION_EPSILON = 1.0E-4D;
    private static final double SELF_COLLISION_DISTANCE = 0.088D;

    private static final double SURFACE_TANGENT_RETENTION_PER_TICK = 0.930D;
    private static final double HANDLE_ROOT_MAX_ANGLE = Math.toRadians(16.0D);

    private static final double ROOT_FIBRE_MAX_LENGTH =
            EmeraldWhipGeometry.REST_LENGTHS[0]
                    * (PHYSICAL_REST_SCALE - STORED_REST_SCALE);

    private static final int HANDLE_BEND_SEGMENTS = 4;
    private static final double HANDLE_BEND_STIFFNESS_MULTIPLIER = 1.45D;
    private static final double MAX_CAPTURE_DISTANCE_SQR = 16.0D;

    private static final float ARM_REST_PITCH = -0.34F;
    private static final float ARM_SPRING = 0.17F;
    private static final float ARM_DAMPING = 0.76F;
    private static final int RIGHT_CHARGE_TICKS = EmeraldWhipItem.RIGHT_CHARGE_TICKS;
    private static final int RIGHT_SLAM_TICKS = 14;
    private static final float RIGHT_SPIN_TURNS_AT_FULL_CHARGE = 4.0F;

    private static final double CHARGE_SPIN_SIGN = 1.0D;
    private static final double CHARGE_CENTRIFUGAL_SCALE = 0.16D;
    private static final double CHARGE_MAX_RADIAL_ACCEL = 185.0D;
    private static final double CHARGE_MAX_TANGENTIAL_ACCEL = 130.0D;
    private static final double RELEASE_FORCE_STIFFNESS = 238.0D;
    private static final double RELEASE_MAX_ACCEL = 360.0D;
    private static final float RIGHT_TIP_MAX_SCALE = 10.0F;

    private static final int PRECISION_WINDUP_TICKS = 3;
    private static final int PRECISION_STROKE_TICKS = 4;
    private static final float PRECISION_RELEASE_RAW = 0.30F;

    private static final float PRECISION_GUIDE_END = 1.00F;

    private static final double PRECISION_FOLLOW_TOTAL_DELAY_SECONDS = 0.22D;
    private static final double PRECISION_FOLLOW_PER_LINK_TAU =
            PRECISION_FOLLOW_TOTAL_DELAY_SECONDS / Math.max(1.0D, SEGMENTS);

    private static final double PRECISION_FOLLOW_POSITION_ACCEL = 640.0D;
    private static final double PRECISION_FOLLOW_VELOCITY_ACCEL = 28.0D;
    private static final double PRECISION_FOLLOW_RADIAL_ACCEL = 430.0D;
    private static final double PRECISION_FOLLOW_MAX_ACCEL = 4400.0D;

    private static final double PRECISION_FOLLOW_TIP_GAIN = 1.18D;

    private static final double PRECISION_CROSSHAIR_SOURCE_PROGRESS = 0.30D;
    private static final double PRECISION_CROSSHAIR_SWEEP_HALF_SPAN_PROGRESS = 0.34D;
    private static final double PRECISION_CROSSHAIR_SWEEP_HALF_ANGLE_RADIANS = Math.toRadians(68.0D);

    private static final double PRECISION_ATTACK_SECONDS = 0.50D;

    private static final double PRECISION_CROSSHAIR_GATE_WIDTH_PROGRESS = 0.060D;
    private static final double PRECISION_CROSSHAIR_GATE_ACCEL = 3100.0D;

    private static final int COIL_IDLE_DELAY_TICKS = 20;

    private static final int COIL_DURATION_TICKS = 20;

    private static final int DEPLOY_DURATION_TICKS = 2;
    private static final float PRECISION_DEPLOY_END_RAW_PROGRESS = 0.44F;
    private static final double COIL_POSITION_GAIN = 0.16D;
    private static final double COIL_MAX_PULL_PER_SUBSTEP = 0.11D;
    private static final double COIL_VELOCITY_DAMPING = 0.58D;

    private static final int COIL_LEAD_SEGMENTS = 3;
    private static final double COIL_TURNS = 5.00D;
    private static final double COIL_LAYER_PITCH = 0.050D;

    private static final double COIL_SELF_COLLISION_SCALE = 0.46D;

    private static final long FIRST_PERSON_BRIDGE_MAX_AGE_TICKS = 1L;

    private static final int TIP_TRAIL_LIFETIME_TICKS = 5;
    private static final int TIP_TRAIL_MAX_SAMPLES = 32;
    private static final double TIP_TRAIL_MIN_SAMPLE_DISTANCE_SQR = 0.035D * 0.035D;
    private static final double TIP_TRAIL_MIN_SPEED = 2.5D;
    static final float TIP_TRAIL_OUTER_HALF_WIDTH = 0.03625F;
    static final float TIP_TRAIL_CORE_HALF_WIDTH = 0.013F;

    private static final float BLADE_LINK_OUTER_RADIUS = 0.012F;
    private static final float BLADE_LINK_CORE_RADIUS = 0.0050F;
    private static final int BLADE_LINK_CURVE_STEPS = 8;
    private static final double BLADE_LINK_MAX_SAG_RATIO = 0.46D;
    private static final int BLADE_LINK_RADIAL_SIDES = 8;
    private static final float SPECTRAL_OVERLAY_SCALE = 1.20F;
    private static final int SPECTRAL_OVERLAY_RED = 112;
    private static final int SPECTRAL_OVERLAY_GREEN = 255;
    private static final int SPECTRAL_OVERLAY_BLUE = 170;
    private static final int SPECTRAL_OVERLAY_ALPHA = 96;

    private static final Map<StateKey, WhipState> STATES = new HashMap<>();

    private static final Map<StateKey, AttackDrive> PENDING_ATTACKS = new HashMap<>();

    private static final int MAX_PENDING_PRECISION_INPUTS = 32;
    private static final Map<StateKey, ArrayDeque<ClientPrecisionRequest>> PENDING_PRECISION =
            new HashMap<>();
    private static final ThreadLocal<ArrayDeque<RenderContext>> RENDER_CONTEXT =
            ThreadLocal.withInitial(ArrayDeque::new);

    private EmeraldWhipPhysics() {}

    public static void requestChargedSlam(Player player, InteractionHand hand) {
        if (player == null) {
            return;
        }
        HumanoidArm arm = armForHand(player, hand);
        PENDING_ATTACKS.put(new StateKey(player.getUUID(), arm), AttackDrive.CHARGED_SLAM);
    }

    public static void cancelChargedSlam(Player player) {
        if (player == null) return;
        for (HumanoidArm arm : HumanoidArm.values()) {
            StateKey key = new StateKey(player.getUUID(), arm);
            if (PENDING_ATTACKS.get(key) == AttackDrive.CHARGED_SLAM) {
                PENDING_ATTACKS.remove(key);
            }
            WhipState state = STATES.get(key);
            if (state != null) {
                state.motor.cancelChargedSlam();
            }
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
        Player player = minecraft.player;
        ItemStack stack = player.getMainHandItem();
        if (!stack.is(ModItems.EMERALD_WHIP.get())) {
            return;
        }

        if (player.getAttackStrengthScale(0.0F) < 1.0F) {
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }

        triggerPrecisionClient(player);
        event.setCanceled(true);

        event.setSwingHand(false);
    }

    private static void triggerPrecisionClient(Player player) {
        StateKey key = new StateKey(player.getUUID(), player.getMainArm());
        ArrayDeque<ClientPrecisionRequest> queue = PENDING_PRECISION.computeIfAbsent(
                key, ignored -> new ArrayDeque<>());
        if (queue.size() >= MAX_PENDING_PRECISION_INPUTS) {
            return;
        }

        WhipState state = STATES.computeIfAbsent(key,
                ignored -> new WhipState(player, player.getMainArm()));
        Vec3 clickDirection = aimDirection(player);
        Vec3 handleAxis = state.capturedHandleAxis;
        WhipMomentumContinuity.Continuation continuation = state.precisionContinuation(
                clickDirection, player.level().getGameTime());
        double swingSign = continuation.active()
                ? continuation.swingSign() : state.consumeFallbackSwingSign();
        queue.addLast(new ClientPrecisionRequest(clickDirection, handleAxis,
                continuation.active(), swingSign));

        if (state.initialized) {
            EmeraldWhipNetwork.sendPrecisionAttack(
                    state.points, state.previous, state.lastSubstepSeconds,
                    handleAxis, clickDirection);
        } else {
            EmeraldWhipNetwork.sendPrecisionAttack();
        }

        player.swing(InteractionHand.MAIN_HAND);
        player.resetAttackStrengthTicker();
    }

    private static void tryHeldPrecisionAttack(Minecraft minecraft) {
        Player player = minecraft.player;
        if (player == null || minecraft.screen != null || !minecraft.options.keyAttack.isDown()
                || player.isUsingItem() || !player.isAlive() || player.isSpectator()
                || !player.getMainHandItem().is(ModItems.EMERALD_WHIP.get())
                || player.getAttackStrengthScale(0.0F) < 1.0F) {
            return;
        }
        triggerPrecisionClient(player);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.isPaused()) {
            return;
        }

        tryHeldPrecisionAttack(minecraft);

        long gameTime = level.getGameTime();
        Set<StateKey> seen = new HashSet<>();
        for (Player player : level.players()) {
            for (HumanoidArm arm : HumanoidArm.values()) {
                if (!holdsWhip(player, arm)) {
                    continue;
                }
                StateKey key = new StateKey(player.getUUID(), arm);
                seen.add(key);
                WhipState state = STATES.computeIfAbsent(key,
                        ignored -> new WhipState(player, arm));
                state.tick(level, player, gameTime);
            }
        }

        Iterator<Map.Entry<StateKey, WhipState>> iterator = STATES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<StateKey, WhipState> entry = iterator.next();
            if (!seen.contains(entry.getKey())) {

                PENDING_ATTACKS.remove(entry.getKey());
                PENDING_PRECISION.remove(entry.getKey());
                iterator.remove();
            }
        }
    }
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        STATES.clear();
        PENDING_ATTACKS.clear();
        PENDING_PRECISION.clear();
        RENDER_CONTEXT.remove();
    }

    private static boolean isAttachedLocalFirstPerson(Minecraft minecraft, Player holder, Camera camera) {
        if (minecraft == null || holder == null || camera == null
                || holder != minecraft.player
                || !minecraft.options.getCameraType().isFirstPerson()) {
            return false;
        }
        if (camera.getEntity() != holder) {
            return false;
        }
        return camera.getPosition().distanceToSqr(holder.getEyePosition()) <= 2.25D;
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES
                || ShaderCompat.isIrisShadowPass()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || STATES.isEmpty()) {
            return;
        }

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        long gameTime = level.getGameTime();
        List<WhipState> snapshot = new ArrayList<>(STATES.values());
        snapshot.sort(java.util.Comparator.comparingDouble(state ->
                state.holder == null ? Double.POSITIVE_INFINITY : state.holder.position().distanceToSqr(camera)));

        VertexConsumer consumer = buffers.getBuffer(RENDER_TYPE);
        for (WhipState state : snapshot) {
            if (!state.initialized || state.holder == null || !state.holder.isAlive()
                    || gameTime - state.lastSeenGameTime > 2L) {
                continue;
            }
            if (!WhipPerformanceTuning.shouldRenderMain(state.holder, camera)) {
                continue;
            }
            boolean localFirstPerson = isAttachedLocalFirstPerson(
                    minecraft, state.holder, event.getCamera());
            if (localFirstPerson) {
                continue;
            }
            state.render(level, poseStack, consumer, camera, partialTick);
        }
        buffers.endBatch(RENDER_TYPE);

        VertexConsumer spectralConsumer = buffers.getBuffer(SPECTRAL_RENDER_TYPE);
        for (WhipState state : snapshot) {
            if (!state.initialized || state.holder == null || !state.holder.isAlive()
                    || gameTime - state.lastSeenGameTime > 2L) {
                continue;
            }
            if (!WhipPerformanceTuning.shouldRenderMain(state.holder, camera)) {
                continue;
            }
            if (!WhipPerformanceTuning.allowFancyLayer("emerald", state.holder, camera)) {
                continue;
            }
            boolean localFirstPerson = isAttachedLocalFirstPerson(
                    minecraft, state.holder, event.getCamera());
            if (!localFirstPerson) {
                state.renderSpectral(level, poseStack, spectralConsumer, camera, partialTick);
            }
        }
        buffers.endBatch(SPECTRAL_RENDER_TYPE);

        VertexConsumer trailConsumer = buffers.getBuffer(TIP_TRAIL_RENDER_TYPE);
        for (WhipState state : snapshot) {
            if (!state.initialized || state.holder == null || !state.holder.isAlive()
                    || gameTime - state.lastSeenGameTime > 2L) {
                continue;
            }
            if (!WhipPerformanceTuning.shouldRenderMain(state.holder, camera)) {
                continue;
            }
            boolean localFirstPerson = isAttachedLocalFirstPerson(
                    minecraft, state.holder, event.getCamera());
            if (!localFirstPerson
                    && WhipPerformanceTuning.allowConnectors("emerald", state.holder, camera)) {
                state.renderBladeConnectors(poseStack, trailConsumer, camera, partialTick);
            }
            if (!localFirstPerson && shouldDisplayTipTrail(state.holder)
                    && WhipPerformanceTuning.allowTrail("emerald", state.holder, camera)) {
                state.renderTipTrail(poseStack, trailConsumer, camera, partialTick, gameTime);
            }
        }
        buffers.endBatch(TIP_TRAIL_RENDER_TYPE);
    }

    public static void onRenderArm(RenderArmEvent event) {
        Player player = event.getPlayer();
        HumanoidArm arm = event.getArm();
        if (!holdsWhip(player, arm)) {
            return;
        }
        float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        ArmPose armPose = getArmPose(player, arm, partialTick);
        float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        float pitchDelta = armPose.pitch - ARM_REST_PITCH;

        PoseStack stack = event.getPoseStack();
        WhipFirstPersonFov.applyToViewModel(stack);
        stack.translate(side * 0.025F, -0.035F, 0.0F);
        stack.mulPose(Axis.ZP.rotation(armPose.roll * 0.50F));
        stack.mulPose(Axis.YP.rotation(armPose.yaw * 0.45F));
        stack.mulPose(Axis.XP.rotation(pitchDelta * 0.55F));
    }

    public static void applyFirstPersonItemTransform(PoseStack stack, Player player,
                                                     HumanoidArm arm, float partialTick,
                                                     float equipProgress) {
        ArmPose armPose = getArmPose(player, arm, partialTick);
        float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        float pitchDelta = armPose.pitch - ARM_REST_PITCH;

        WhipFirstPersonFov.applyToViewModel(stack);
        stack.translate(side * 0.56F, -0.52F - equipProgress * 0.60F, -0.72F);
        stack.translate(side * 0.025F, 0.055F, -0.035F);
        stack.mulPose(Axis.YP.rotationDegrees(side * 42.0F
                + Mth.RAD_TO_DEG * armPose.yaw * 0.70F));
        stack.mulPose(Axis.XP.rotationDegrees(-22.0F
                + Mth.RAD_TO_DEG * pitchDelta * 0.90F));
        stack.mulPose(Axis.ZP.rotationDegrees(-side * 8.0F
                + Mth.RAD_TO_DEG * armPose.roll * 0.82F));
    }

    public static ArmPose getArmPose(Player player, HumanoidArm arm, float partialTick) {
        StateKey key = new StateKey(player.getUUID(), arm);
        WhipState state = STATES.computeIfAbsent(key, ignored -> new WhipState(player, arm));
        return state.motor.sample(partialTick);
    }

    public static ArmPose getThirdPersonArmPose(Player player, HumanoidArm arm,
                                                float partialTick) {
        ArmPose pose = getArmPose(player, arm, partialTick);
        float mirroredPitch = ARM_REST_PITCH - (pose.pitch - ARM_REST_PITCH);
        return new ArmPose(mirroredPitch, pose.yaw, pose.roll);
    }

    public static void pushRenderContext(LivingEntity holder, HumanoidArm arm,
                                         boolean firstPerson) {
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

    static boolean shouldRenderAuthoredStoredLash(RenderContext context) {
        if (context == null || !(context.holder instanceof Player player)) {
            return true;
        }
        WhipState state = STATES.get(new StateKey(player.getUUID(), context.arm));
        return state == null || !state.initialized || state.coilProgress >= 0.999F;
    }

    static void captureRenderedSocket(PoseStack poseStack, RenderContext context) {
        if (!(context.holder instanceof Player player) || !holdsWhip(player, context.arm)) {
            return;
        }

        Vector3f transformedRoot = new Vector3f(0.0F,
                EmeraldWhipGeometry.CHAIN_PIVOT_Y, 0.0F);
        Vector3f transformedAxisPoint = new Vector3f(0.0F,
                EmeraldWhipGeometry.CHAIN_PIVOT_Y + 0.25F, 0.0F);
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
        Vec3 world = cameraPosition.add(capturedRoot);

        if (world.distanceToSqr(player.getEyePosition()) > MAX_CAPTURE_DISTANCE_SQR) {
            return;
        }
        Vec3 handleAxis = capturedAxisPoint.subtract(capturedRoot);
        if (handleAxis.lengthSqr() > 1.0E-10D) {
            handleAxis = handleAxis.normalize();
        } else {
            handleAxis = Vec3.ZERO;
        }

        StateKey key = new StateKey(player.getUUID(), context.arm);
        WhipState state = STATES.computeIfAbsent(key, ignored -> new WhipState(player, context.arm));
        long gameTime = player.level().getGameTime();
        state.captureAnchor(world, handleAxis, gameTime);
    }

    static void renderFirstPersonHandLash(PoseStack poseStack, RenderContext context,
                                          VertexConsumer consumer, int packedLight, int packedOverlay) {
        renderFirstPersonHandLashPass(
                poseStack, context, consumer, packedLight, packedOverlay, false);
    }

    static void renderFirstPersonHandSpectralLash(PoseStack poseStack, RenderContext context,
                                                  VertexConsumer consumer, int packedOverlay) {
        renderFirstPersonHandLashPass(
                poseStack, context, consumer, LightTexture.FULL_BRIGHT, packedOverlay, true);
    }

    private static void renderFirstPersonHandLashPass(PoseStack poseStack, RenderContext context,
                                                       VertexConsumer consumer, int packedLight,
                                                       int packedOverlay, boolean spectral) {
        if (context == null || !context.firstPerson
                || !(context.holder instanceof Player player)
                || !holdsWhip(player, context.arm)) {
            return;
        }

        StateKey key = new StateKey(player.getUUID(), context.arm);
        WhipState state = STATES.get(key);
        if (!(player.level() instanceof ClientLevel level)) {
            return;
        }

        if (state == null || !state.initialized) {
            if (spectral) {
                EmeraldWhipGeometry.INSTANCE.renderAuthoredLashScaledPerPart(
                        poseStack, consumer, LightTexture.FULL_BRIGHT, packedOverlay,
                        SPECTRAL_OVERLAY_SCALE, SPECTRAL_OVERLAY_RED, SPECTRAL_OVERLAY_GREEN,
                        SPECTRAL_OVERLAY_BLUE, SPECTRAL_OVERLAY_ALPHA);
            } else {
                EmeraldWhipGeometry.INSTANCE.renderAuthoredLash(
                        poseStack, consumer, packedLight, packedOverlay);
            }
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Camera camera = minecraft.gameRenderer.getMainCamera();
        if (!isAttachedLocalFirstPerson(minecraft, player, camera)) {
            return;
        }
        float partialTick = minecraft.getTimer().getGameTimeDeltaPartialTick(false);

        if (state.coilProgress >= 0.999F) {
            if (spectral) {
                EmeraldWhipGeometry.INSTANCE.renderAuthoredLashScaledPerPart(
                        poseStack, consumer, LightTexture.FULL_BRIGHT, packedOverlay,
                        SPECTRAL_OVERLAY_SCALE, SPECTRAL_OVERLAY_RED, SPECTRAL_OVERLAY_GREEN,
                        SPECTRAL_OVERLAY_BLUE, SPECTRAL_OVERLAY_ALPHA);
            } else {
                EmeraldWhipGeometry.INSTANCE.renderAuthoredLash(
                        poseStack, consumer, packedLight, packedOverlay);
            }
            return;
        }

        Matrix4f inverseHand = new Matrix4f(poseStack.last().pose());
        float determinant = inverseHand.determinant();
        if (!Float.isFinite(determinant) || Math.abs(determinant) < 1.0E-8F) {
            return;
        }
        inverseHand.invert();

        Vec3 interpolatedRoot = state.renderPrevious[0].lerp(state.points[0], partialTick);
        Vec3 displayAnchor = state.renderDisplayAnchor(
                interpolatedRoot, player.level().getGameTime());
        Vec3 rootCorrection = state.visualMorphActive()
                ? displayAnchor.subtract(interpolatedRoot) : Vec3.ZERO;

        for (int i = 0; i < SEGMENTS; ++i) {
            Vec3 worldStart = state.storedVisualPoint(i, partialTick, rootCorrection);
            Vec3 worldEnd = state.storedVisualPoint(i + 1, partialTick, rootCorrection);

            Vec3 localStart = firstPersonWorldPointToLocal(worldStart, camera, inverseHand);
            Vec3 localEnd = firstPersonWorldPointToLocal(worldEnd, camera, inverseHand);
            Vec3 chord = localEnd.subtract(localStart);
            double localLength = chord.length();
            if (!Double.isFinite(localLength) || localLength < 1.0E-6D) {
                continue;
            }

            Vector3f direction = chord.scale(1.0D / localLength).toVector3f();
            Quaternionf rotation = new Quaternionf().rotationTo(
                    0.0F, 0.0F, -1.0F, direction.x, direction.y, direction.z);
            Vec3 midpoint = worldStart.lerp(worldEnd, 0.5D);
            int light = spectral
                    ? LightTexture.FULL_BRIGHT
                    : LevelRenderer.getLightColor(level, BlockPos.containing(midpoint));
            poseStack.pushPose();
            poseStack.translate(localStart.x, localStart.y, localStart.z);
            poseStack.mulPose(rotation);
            if (i == SEGMENTS - 1 && state.tipVisualScale > 1.001F) {
                poseStack.scale(state.tipVisualScale, state.tipVisualScale, state.tipVisualScale);
            }
            if (spectral) {
                EmeraldWhipGeometry.INSTANCE.renderDynamicSegmentScaledAboutCenter(
                        i, poseStack, consumer, light, packedOverlay, SPECTRAL_OVERLAY_SCALE,
                        SPECTRAL_OVERLAY_RED, SPECTRAL_OVERLAY_GREEN,
                        SPECTRAL_OVERLAY_BLUE, SPECTRAL_OVERLAY_ALPHA);
            } else {
                EmeraldWhipGeometry.INSTANCE.renderDynamicSegment(
                        i, poseStack, consumer, light, packedOverlay, 255, 255, 255, 255);
            }
            poseStack.popPose();
        }
    }

    private static Vec3 firstPersonWorldPointToLocal(Vec3 worldPoint, Camera camera,
                                                      Matrix4f inverseHand) {
        Vec3 cameraRelative = worldPoint.subtract(camera.getPosition());
        Vec3 compensated = WhipFirstPersonFov.applyToCapturedWorldVector(cameraRelative, camera);
        Vector3f local = compensated.toVector3f();
        inverseHand.transformPosition(local);
        return new Vec3(local.x, local.y, local.z);
    }

    static RenderType spectralRenderType() {
        return SPECTRAL_RENDER_TYPE;
    }

    static RenderType bladeMagicRenderType() {
        return TIP_TRAIL_RENDER_TYPE;
    }

    static void renderFirstPersonBladeConnectors(PoseStack poseStack, RenderContext context,
                                                  VertexConsumer consumer) {
        if (context == null || !context.firstPerson
                || !(context.holder instanceof Player player)
                || !holdsWhip(player, context.arm)) {
            return;
        }
        WhipState state = STATES.get(new StateKey(player.getUUID(), context.arm));
        if (state == null || !state.initialized || state.coilProgress >= 0.999F) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Camera camera = minecraft.gameRenderer.getMainCamera();
        if (!isAttachedLocalFirstPerson(minecraft, player, camera)) {
            return;
        }
        float partialTick = minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        Matrix4f inverseHand = new Matrix4f(poseStack.last().pose());
        float determinant = inverseHand.determinant();
        if (!Float.isFinite(determinant) || Math.abs(determinant) < 1.0E-8F) {
            return;
        }
        inverseHand.invert();
        Vec3 interpolatedRoot = state.renderPrevious[0].lerp(state.points[0], partialTick);
        Vec3 displayAnchor = state.renderDisplayAnchor(
                interpolatedRoot, player.level().getGameTime());
        Vec3 rootCorrection = state.visualMorphActive()
                ? displayAnchor.subtract(interpolatedRoot) : Vec3.ZERO;
        PoseStack.Pose pose = poseStack.last();
        Vec3 firstWorld = state.storedVisualPoint(0, partialTick, rootCorrection);
        Vec3 firstLocal = firstPersonWorldPointToLocal(firstWorld, camera, inverseHand);
        Vec3 handleLocal = new Vec3(0.0D, EmeraldWhipGeometry.CHAIN_PIVOT_Y, 0.0D);
        if (state.coilProgress < 0.999F && firstLocal.distanceToSqr(handleLocal) > 1.0E-7D) {
            Vec3[] rootSamples = samplePhysicalConnector(
                    handleLocal, firstLocal, handleLocal, firstLocal, ROOT_FIBRE_MAX_LENGTH);
            emitLocalFiberTube(pose, consumer, rootSamples,
                    BLADE_LINK_OUTER_RADIUS, 48, 205, 104, 140);
            emitLocalFiberTube(pose, consumer, rootSamples,
                    BLADE_LINK_CORE_RADIUS, 194, 255, 214, 238);
        }
        for (int i = 0; i < SEGMENTS - 1; ++i) {
            Vec3 worldStart = state.storedVisualPoint(i, partialTick, rootCorrection);
            Vec3 worldEnd = state.storedVisualPoint(i + 1, partialTick, rootCorrection);
            Vec3 worldNext = state.storedVisualPoint(i + 2, partialTick, rootCorrection);
            Vec3 chord = worldEnd.subtract(worldStart);
            double length = chord.length();
            if (length < 1.0E-6D) {
                continue;
            }
            double authoredLength = Math.min(length, EmeraldWhipGeometry.REST_LENGTHS[i]);
            Vec3 bladeEnd = worldStart.add(chord.scale(authoredLength / length));
            Vec3 localStart = firstPersonWorldPointToLocal(bladeEnd, camera, inverseHand);
            Vec3 localJoint = firstPersonWorldPointToLocal(worldEnd, camera, inverseHand);
            Vec3 localPrev = firstPersonWorldPointToLocal(worldStart, camera, inverseHand);
            Vec3 localNext = firstPersonWorldPointToLocal(worldNext, camera, inverseHand);
            double storedWeight = state.deployingFromStored && state.deployVisualProgress < 0.999F
                    ? 1.0D - state.deploymentActivationForPoint(i + 1)
                    : state.storedVisualActivationForPoint(i + 1);
            double visibleScale = Mth.lerp(storedWeight,
                    PHYSICAL_REST_SCALE, STORED_REST_SCALE);
            double cableLength = Math.max(0.0D,
                    EmeraldWhipGeometry.REST_LENGTHS[i] * visibleScale
                            - EmeraldWhipGeometry.REST_LENGTHS[i]);
            renderPhysicalConnectorLocal(pose, consumer, localStart, localJoint,
                    localPrev, localNext, cableLength);
        }
    }

    static void renderFirstPersonTipTrail(PoseStack poseStack, RenderContext context,
                                                VertexConsumer consumer) {
        if (context == null || !context.firstPerson
                || !(context.holder instanceof Player player)
                || !holdsWhip(player, context.arm)) {
            return;
        }
        WhipState state = STATES.get(new StateKey(player.getUUID(), context.arm));
        if (state == null || state.tipTrail.size() < 2) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Camera camera = minecraft.gameRenderer.getMainCamera();
        if (!isAttachedLocalFirstPerson(minecraft, player, camera)) {
            return;
        }
        float partialTick = minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        long gameTime = player.level().getGameTime();
        Matrix4f inverseHand = new Matrix4f(poseStack.last().pose());
        float determinant = inverseHand.determinant();
        if (!Float.isFinite(determinant) || Math.abs(determinant) < 1.0E-8F) {
            return;
        }
        inverseHand.invert();
        List<TipTrailSample> samples = new ArrayList<>(state.tipTrail);
        PoseStack.Pose pose = poseStack.last();
        for (int i = 0; i < samples.size() - 1; ++i) {
            TipTrailSample a = samples.get(i);
            TipTrailSample b = samples.get(i + 1);
            Vec3 start = a.position();
            Vec3 end = b.position();
            Vec3 tangent = end.subtract(start);
            if (tangent.lengthSqr() < 1.0E-10D) {
                continue;
            }
            Vec3 midpoint = start.lerp(end, 0.5D);
            Vec3 side = tangent.cross(camera.getPosition().subtract(midpoint));
            if (side.lengthSqr() < 1.0E-10D) {
                side = tangent.cross(new Vec3(0.0D, 1.0D, 0.0D));
            }
            if (side.lengthSqr() < 1.0E-10D) {
                continue;
            }
            side = side.normalize();
            float fadeA = state.trailFade(a, i, samples.size(), gameTime, partialTick);
            float fadeB = state.trailFade(b, i + 1, samples.size(), gameTime, partialTick);
            if (fadeA <= 0.01F && fadeB <= 0.01F) {
                continue;
            }
            emitFirstPersonTrailQuad(pose, consumer, camera, inverseHand,
                    start, end, side, TIP_TRAIL_OUTER_HALF_WIDTH, fadeA, fadeB, false);
            emitFirstPersonTrailQuad(pose, consumer, camera, inverseHand,
                    start, end, side, TIP_TRAIL_CORE_HALF_WIDTH, fadeA, fadeB, true);
        }
    }

    private static void emitFirstPersonTrailQuad(PoseStack.Pose pose, VertexConsumer consumer,
                                                 Camera camera, Matrix4f inverseHand,
                                                 Vec3 start, Vec3 end, Vec3 side, float halfWidth,
                                                 float fadeStart, float fadeEnd, boolean core) {
        Vec3 worldS0 = start.add(side.scale(halfWidth));
        Vec3 worldS1 = start.add(side.scale(-halfWidth));
        Vec3 worldE1 = end.add(side.scale(-halfWidth));
        Vec3 worldE0 = end.add(side.scale(halfWidth));
        Vec3 s0 = firstPersonWorldPointToLocal(worldS0, camera, inverseHand);
        Vec3 s1 = firstPersonWorldPointToLocal(worldS1, camera, inverseHand);
        Vec3 e1 = firstPersonWorldPointToLocal(worldE1, camera, inverseHand);
        Vec3 e0 = firstPersonWorldPointToLocal(worldE0, camera, inverseHand);
        int r = core ? 190 : 48;
        int g = core ? 255 : 205;
        int b = core ? 214 : 104;
        int a0 = (int)(Mth.clamp(fadeStart, 0.0F, 1.0F) * (core ? 185 : 105));
        int a1 = (int)(Mth.clamp(fadeEnd, 0.0F, 1.0F) * (core ? 205 : 128));
        connectorVertex(pose, consumer, s0, r, g, b, a0);
        connectorVertex(pose, consumer, s1, r, g, b, a0);
        connectorVertex(pose, consumer, e1, r, g, b, a1);
        connectorVertex(pose, consumer, e0, r, g, b, a1);
    }

    private static Vec3 physicalConnectorPoint(Vec3 start, Vec3 end,
                                               Vec3 previousPoint, Vec3 nextPoint,
                                               double cableLength, float t) {
        double gapLength = start.distanceTo(end);
        if (gapLength < 1.0E-8D) {
            return start;
        }
        Vec3 incoming = start.subtract(previousPoint);
        if (incoming.lengthSqr() < 1.0E-10D) {
            incoming = end.subtract(start);
        }
        Vec3 outgoing = nextPoint.subtract(end);
        if (outgoing.lengthSqr() < 1.0E-10D) {
            outgoing = end.subtract(start);
        }
        incoming = incoming.normalize().scale(Math.min(gapLength, cableLength));
        outgoing = outgoing.normalize().scale(Math.min(gapLength, cableLength));

        double tt = t * t;
        double ttt = tt * t;
        double h00 = 2.0D * ttt - 3.0D * tt + 1.0D;
        double h10 = ttt - 2.0D * tt + t;
        double h01 = -2.0D * ttt + 3.0D * tt;
        double h11 = ttt - tt;
        Vec3 hermite = start.scale(h00)
                .add(incoming.scale(h10))
                .add(end.scale(h01))
                .add(outgoing.scale(h11));

        double slack = Math.max(0.0D, cableLength - gapLength);
        double sag = Math.min(cableLength * BLADE_LINK_MAX_SAG_RATIO,
                Math.sqrt(Math.max(0.0D, slack * Math.max(cableLength, 1.0E-6D))) * 0.70D);
        double bow = 4.0D * t * (1.0D - t);
        return hermite.add(0.0D, -sag * bow, 0.0D);
    }

    private static void renderPhysicalConnectorLocal(PoseStack.Pose pose, VertexConsumer consumer,
                                                      Vec3 start, Vec3 end,
                                                      Vec3 previousPoint, Vec3 nextPoint,
                                                      double cableLength) {
        Vec3[] samples = samplePhysicalConnector(start, end, previousPoint, nextPoint, cableLength);
        emitLocalFiberTube(pose, consumer, samples,
                BLADE_LINK_OUTER_RADIUS, 48, 205, 104, 140);
        emitLocalFiberTube(pose, consumer, samples,
                BLADE_LINK_CORE_RADIUS, 194, 255, 214, 238);
    }

    private static Vec3[] samplePhysicalConnector(Vec3 start, Vec3 end,
                                                  Vec3 previousPoint, Vec3 nextPoint,
                                                  double cableLength) {
        Vec3[] samples = new Vec3[BLADE_LINK_CURVE_STEPS + 1];
        for (int step = 0; step <= BLADE_LINK_CURVE_STEPS; ++step) {
            float t = step / (float) BLADE_LINK_CURVE_STEPS;
            samples[step] = physicalConnectorPoint(
                    start, end, previousPoint, nextPoint, cableLength, t);
        }
        return samples;
    }

    private static Vec3 tubeTangent(Vec3[] samples, int index) {
        Vec3 tangent;
        if (index <= 0) {
            tangent = samples[1].subtract(samples[0]);
        } else if (index >= samples.length - 1) {
            tangent = samples[samples.length - 1].subtract(samples[samples.length - 2]);
        } else {
            tangent = samples[index + 1].subtract(samples[index - 1]);
        }
        if (tangent.lengthSqr() < 1.0E-12D) {
            tangent = new Vec3(0.0D, 1.0D, 0.0D);
        }
        return tangent.normalize();
    }

    private static Vec3 initialTubeNormal(Vec3 tangent) {
        Vec3 reference = Math.abs(tangent.y) < 0.90D
                ? new Vec3(0.0D, 1.0D, 0.0D)
                : new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 normal = tangent.cross(reference);
        if (normal.lengthSqr() < 1.0E-12D) {
            normal = tangent.cross(new Vec3(0.0D, 0.0D, 1.0D));
        }
        return normal.lengthSqr() < 1.0E-12D
                ? new Vec3(1.0D, 0.0D, 0.0D)
                : normal.normalize();
    }

    private static Vec3 transportTubeNormal(Vec3 previousNormal, Vec3 tangent) {
        Vec3 projected = previousNormal.subtract(tangent.scale(previousNormal.dot(tangent)));
        if (projected.lengthSqr() < 1.0E-12D) {
            return initialTubeNormal(tangent);
        }
        return projected.normalize();
    }

    private static void emitLocalFiberTube(PoseStack.Pose pose, VertexConsumer consumer,
                                           Vec3[] samples, float radius,
                                           int red, int green, int blue, int alpha) {
        if (samples.length < 2 || radius <= 0.0F) {
            return;
        }
        Vec3[] normals = new Vec3[samples.length];
        Vec3[] binormals = new Vec3[samples.length];
        Vec3 firstTangent = tubeTangent(samples, 0);
        normals[0] = initialTubeNormal(firstTangent);
        binormals[0] = firstTangent.cross(normals[0]).normalize();
        for (int i = 1; i < samples.length; ++i) {
            Vec3 tangent = tubeTangent(samples, i);
            normals[i] = transportTubeNormal(normals[i - 1], tangent);
            binormals[i] = tangent.cross(normals[i]).normalize();
        }
        for (int i = 0; i < samples.length - 1; ++i) {
            for (int side = 0; side < BLADE_LINK_RADIAL_SIDES; ++side) {
                double a0 = Math.PI * 2.0D * side / BLADE_LINK_RADIAL_SIDES;
                double a1 = Math.PI * 2.0D * (side + 1) / BLADE_LINK_RADIAL_SIDES;
                Vec3 p00 = fiberRingPoint(samples[i], normals[i], binormals[i], radius, a0);
                Vec3 p01 = fiberRingPoint(samples[i], normals[i], binormals[i], radius, a1);
                Vec3 p11 = fiberRingPoint(samples[i + 1], normals[i + 1], binormals[i + 1], radius, a1);
                Vec3 p10 = fiberRingPoint(samples[i + 1], normals[i + 1], binormals[i + 1], radius, a0);
                connectorVertex(pose, consumer, p00, red, green, blue, alpha);
                connectorVertex(pose, consumer, p01, red, green, blue, alpha);
                connectorVertex(pose, consumer, p11, red, green, blue, alpha);
                connectorVertex(pose, consumer, p10, red, green, blue, alpha);
            }
        }
    }

    private static Vec3 fiberRingPoint(Vec3 center, Vec3 normal, Vec3 binormal,
                                       float radius, double angle) {
        return center
                .add(normal.scale(Math.cos(angle) * radius))
                .add(binormal.scale(Math.sin(angle) * radius));
    }

    private static void connectorVertex(PoseStack.Pose pose, VertexConsumer consumer, Vec3 point,
                                        int red, int green, int blue, int alpha) {
        consumer.addVertex(pose.pose(), (float) point.x, (float) point.y, (float) point.z)
                .setColor(red, green, blue, Mth.clamp(alpha, 0, 255));
    }

    private static void renderPhysicalConnectorWorld(PoseStack.Pose pose, VertexConsumer consumer,
                                                      Vec3 camera, Vec3 start, Vec3 end,
                                                      Vec3 previousPoint, Vec3 nextPoint,
                                                      double cableLength) {
        Vec3[] samples = samplePhysicalConnector(
                start, end, previousPoint, nextPoint, cableLength);
        emitWorldFiberTube(pose, consumer, camera, samples,
                BLADE_LINK_OUTER_RADIUS, 48, 205, 104, 140);
        emitWorldFiberTube(pose, consumer, camera, samples,
                BLADE_LINK_CORE_RADIUS, 194, 255, 214, 238);
    }

    private static void emitWorldFiberTube(PoseStack.Pose pose, VertexConsumer consumer,
                                           Vec3 camera, Vec3[] samples, float radius,
                                           int red, int green, int blue, int alpha) {
        if (samples.length < 2 || radius <= 0.0F) {
            return;
        }
        Vec3[] normals = new Vec3[samples.length];
        Vec3[] binormals = new Vec3[samples.length];
        Vec3 firstTangent = tubeTangent(samples, 0);
        normals[0] = initialTubeNormal(firstTangent);
        binormals[0] = firstTangent.cross(normals[0]).normalize();
        for (int i = 1; i < samples.length; ++i) {
            Vec3 tangent = tubeTangent(samples, i);
            normals[i] = transportTubeNormal(normals[i - 1], tangent);
            binormals[i] = tangent.cross(normals[i]).normalize();
        }
        for (int i = 0; i < samples.length - 1; ++i) {
            for (int side = 0; side < BLADE_LINK_RADIAL_SIDES; ++side) {
                double a0 = Math.PI * 2.0D * side / BLADE_LINK_RADIAL_SIDES;
                double a1 = Math.PI * 2.0D * (side + 1) / BLADE_LINK_RADIAL_SIDES;
                Vec3 p00 = fiberRingPoint(samples[i], normals[i], binormals[i], radius, a0);
                Vec3 p01 = fiberRingPoint(samples[i], normals[i], binormals[i], radius, a1);
                Vec3 p11 = fiberRingPoint(samples[i + 1], normals[i + 1], binormals[i + 1], radius, a1);
                Vec3 p10 = fiberRingPoint(samples[i + 1], normals[i + 1], binormals[i + 1], radius, a0);
                worldConnectorVertex(pose, consumer, camera, p00, red, green, blue, alpha);
                worldConnectorVertex(pose, consumer, camera, p01, red, green, blue, alpha);
                worldConnectorVertex(pose, consumer, camera, p11, red, green, blue, alpha);
                worldConnectorVertex(pose, consumer, camera, p10, red, green, blue, alpha);
            }
        }
    }

    private static void worldConnectorVertex(PoseStack.Pose pose, VertexConsumer consumer,
                                             Vec3 camera, Vec3 point,
                                             int red, int green, int blue, int alpha) {
        consumer.addVertex(pose.pose(),
                        (float)(point.x - camera.x),
                        (float)(point.y - camera.y),
                        (float)(point.z - camera.z))
                .setColor(red, green, blue, Mth.clamp(alpha, 0, 255));
    }

    static void renderFirstPersonRootBridge(PoseStack poseStack, RenderContext context,
                                            VertexConsumer consumer, int packedLight,
                                            int packedOverlay) {
        if (context == null || !context.firstPerson
                || !(context.holder instanceof Player player)
                || !holdsWhip(player, context.arm)) {
            return;
        }

        StateKey key = new StateKey(player.getUUID(), context.arm);
        WhipState state = STATES.get(key);
        if (state == null || state.firstPersonBridgeTargetWorld == null) {
            return;
        }

        long gameTime = player.level().getGameTime();
        if (gameTime - state.firstPersonBridgeTargetGameTime > FIRST_PERSON_BRIDGE_MAX_AGE_TICKS) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Camera camera = minecraft.gameRenderer.getMainCamera();
        if (!isAttachedLocalFirstPerson(minecraft, player, camera)) {
            return;
        }
        Vec3 cameraRelativeTarget = state.firstPersonBridgeTargetWorld
                .subtract(camera.getPosition());
        Vec3 compensatedTarget = WhipFirstPersonFov.applyToCapturedWorldVector(
                cameraRelativeTarget, camera);

        Matrix4f inverse = new Matrix4f(poseStack.last().pose());
        float determinant = inverse.determinant();
        if (!Float.isFinite(determinant) || Math.abs(determinant) < 1.0E-8F) {
            return;
        }
        inverse.invert();
        Vector3f localTarget = compensatedTarget.toVector3f();
        inverse.transformPosition(localTarget);

        Vec3 start = new Vec3(0.0D, EmeraldWhipGeometry.CHAIN_PIVOT_Y, 0.0D);
        Vec3 end = new Vec3(localTarget.x, localTarget.y, localTarget.z);
        Vec3 chord = end.subtract(start);
        double localLength = chord.length();
        if (!Double.isFinite(localLength) || localLength < 1.0E-5D) {
            return;
        }

        Vector3f direction = chord.scale(1.0D / localLength).toVector3f();
        Quaternionf rotation = new Quaternionf().rotationTo(
                0.0F, 0.0F, -1.0F, direction.x, direction.y, direction.z);
        poseStack.pushPose();
        poseStack.translate(start.x, start.y, start.z);
        poseStack.mulPose(rotation);

        EmeraldWhipGeometry.INSTANCE.renderDynamicSegment(
                0, poseStack, consumer, packedLight, packedOverlay);
        poseStack.popPose();
    }

    private static boolean holdsWhip(Player player, HumanoidArm arm) {
        return stackForArm(player, arm).is(ModItems.EMERALD_WHIP.get());
    }

    private static boolean shouldDisplayTipTrail(Player player) {
        return true;
    }

    private static boolean hasFireAspect(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        for (var enchantment : stack.getEnchantments().keySet()) {
            if (enchantment.is(Enchantments.FIRE_ASPECT)) {
                return true;
            }
        }
        return false;
    }

    private static ItemStack stackForArm(Player player, HumanoidArm arm) {
        return player.getItemInHand(handForArm(player, arm));
    }

    private static InteractionHand handForArm(Player player, HumanoidArm arm) {
        return player.getMainArm() == arm ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
    }

    private static HumanoidArm armForHand(Player player, InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND
                ? player.getMainArm()
                : player.getMainArm().getOpposite();
    }

    private static Vec3 fallbackAnchor(Player player, HumanoidArm arm) {
        Vec3 look = player.getViewVector(1.0F).normalize();
        Vec3 horizontal = new Vec3(look.x, 0.0D, look.z);
        if (horizontal.lengthSqr() < 1.0E-8D) {
            horizontal = Vec3.directionFromRotation(0.0F, player.getYRot());
        }
        horizontal = new Vec3(horizontal.x, 0.0D, horizontal.z).normalize();
        Vec3 right = new Vec3(-horizontal.z, 0.0D, horizontal.x);
        double side = arm == HumanoidArm.RIGHT ? 1.0D : -1.0D;
        return player.getEyePosition()
                .add(horizontal.scale(0.22D))
                .add(right.scale(side * 0.34D))
                .add(0.0D, -0.52D, 0.0D);
    }

    private static Vec3 physicalForward(Player player) {
        Vec3 forward = player.getViewVector(1.0F);
        forward = new Vec3(forward.x, 0.0D, forward.z);
        if (forward.lengthSqr() < 1.0E-10D) {
            forward = Vec3.directionFromRotation(0.0F, player.yBodyRot);
        }
        return new Vec3(forward.x, 0.0D, forward.z).normalize();
    }

    private static double smoothstep(double value) {
        double t = Mth.clamp(value, 0.0D, 1.0D);
        return t * t * (3.0D - 2.0D * t);
    }

    private static double rightChargeProgress(double chargeTicks) {
        return Mth.clamp(chargeTicks / RIGHT_CHARGE_TICKS, 0.0D, 1.0D);
    }

    private static double rightSpinTurns(double chargeTicks) {
        double ticks = Math.max(0.0D, chargeTicks);
        if (ticks <= RIGHT_CHARGE_TICKS) {
            double t = ticks / RIGHT_CHARGE_TICKS;
            return RIGHT_SPIN_TURNS_AT_FULL_CHARGE * t * t;
        }
        double turnsPerTickAtFull = 2.0D * RIGHT_SPIN_TURNS_AT_FULL_CHARGE
                / RIGHT_CHARGE_TICKS;
        return RIGHT_SPIN_TURNS_AT_FULL_CHARGE
                + (ticks - RIGHT_CHARGE_TICKS) * turnsPerTickAtFull;
    }

    private static double rightSpinOmega(double chargeTicks) {
        double ticks = Math.max(0.0D, chargeTicks);
        double turnsPerTick;
        if (ticks < RIGHT_CHARGE_TICKS) {
            turnsPerTick = 2.0D * RIGHT_SPIN_TURNS_AT_FULL_CHARGE * ticks
                    / (RIGHT_CHARGE_TICKS * RIGHT_CHARGE_TICKS);
        } else {
            turnsPerTick = 2.0D * RIGHT_SPIN_TURNS_AT_FULL_CHARGE / RIGHT_CHARGE_TICKS;
        }
        return turnsPerTick * 20.0D * Math.PI * 2.0D;
    }

    private static float chargedTipScaleForCharge(double chargeTicks) {
        float charge = (float)rightChargeProgress(chargeTicks);
        return Mth.lerp(charge, 1.0F, RIGHT_TIP_MAX_SCALE);
    }

    private static float chargedTipScaleAfterRelease(double releaseTicks) {
        if (releaseTicks <= RIGHT_SLAM_TICKS) {
            return RIGHT_TIP_MAX_SCALE;
        }
        float shrink = (float)Mth.clamp(
                (releaseTicks - RIGHT_SLAM_TICKS) / 6.0D, 0.0D, 1.0D);
        return Mth.lerp(shrink, RIGHT_TIP_MAX_SCALE, 1.0F);
    }

    private static Vec3 aimDirection(Player player) {
        Vec3 aim = player.getViewVector(1.0F);
        if (!finite(aim) || aim.lengthSqr() < 1.0E-10D) {
            aim = Vec3.directionFromRotation(player.getXRot(), player.getYRot());
        }
        return aim.normalize();
    }

    private static Vec3 precisionCrosshairDirection(Player player, Vec3 root, Vec3 viewDirection) {
        Vec3 view = viewDirection;
        if (!finite(view) || view.lengthSqr() < 1.0E-10D) {
            view = aimDirection(player);
        } else {
            view = view.normalize();
        }
        Vec3 crosshairPoint = player.getEyePosition()
                .add(view.scale(EmeraldWhipDimensions.deployedLength()));
        Vec3 fromRoot = crosshairPoint.subtract(root);
        return finite(fromRoot) && fromRoot.lengthSqr() > 1.0E-10D
                ? fromRoot.normalize() : view;
    }

    private static Vec3 handBase(Player player, HumanoidArm arm, Vec3 attackDirection) {
        Vec3 horizontal = new Vec3(attackDirection.x, 0.0D, attackDirection.z);
        if (horizontal.lengthSqr() < 1.0E-10D) {
            horizontal = physicalForward(player);
        } else {
            horizontal = horizontal.normalize();
        }

        Vec3 right = new Vec3(-horizontal.z, 0.0D, horizontal.x);
        double side = arm == HumanoidArm.RIGHT ? 1.0D : -1.0D;
        return player.position()
                .add(0.0D, player.getEyeHeight() - 0.58D, 0.0D)
                .add(right.scale(side * 0.34D))
                .add(horizontal.scale(0.10D));
    }

    private static Vec3 chargedSpinCenter(Player player, HumanoidArm arm, Vec3 attackDirection) {
        Vec3 forward = new Vec3(attackDirection.x, 0.0D, attackDirection.z);
        if (forward.lengthSqr() < 1.0E-10D) {
            forward = physicalForward(player);
        } else {
            forward = forward.normalize();
        }
        return handBase(player, arm, forward)
                .add(forward.scale(0.32D))
                .add(0.0D, 0.68D, 0.0D);
    }

    private static Vec3 chargedSpinHandAnchor(Player player, HumanoidArm arm,
                                              Vec3 attackDirection, double chargeTicks) {
        Vec3 forward = new Vec3(attackDirection.x, 0.0D, attackDirection.z);
        if (forward.lengthSqr() < 1.0E-10D) {
            forward = physicalForward(player);
        } else {
            forward = forward.normalize();
        }
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        double side = arm == HumanoidArm.RIGHT ? 1.0D : -1.0D;
        double charge = rightChargeProgress(chargeTicks);
        double lift = smoothstep(Math.min(1.0D, charge / 0.12D));
        double theta = CHARGE_SPIN_SIGN * rightSpinTurns(chargeTicks) * Math.PI * 2.0D;
        double radius = 0.18D * smoothstep(Math.min(1.0D, charge / 0.18D));
        Vec3 center = chargedSpinCenter(player, arm, forward);
        Vec3 raisedOrbit = center
                .add(forward.scale(Math.cos(theta) * radius))
                .add(right.scale(Math.sin(theta) * radius * side));
        return fallbackAnchor(player, arm).lerp(raisedOrbit, lift);
    }

    private static Vec3 chargedReleaseHandAnchor(Player player, HumanoidArm arm,
                                                 Vec3 attackDirection, double releaseProgress) {
        Vec3 forward = new Vec3(attackDirection.x, 0.0D, attackDirection.z);
        if (forward.lengthSqr() < 1.0E-10D) {
            forward = physicalForward(player);
        } else {
            forward = forward.normalize();
        }
        Vec3 base = handBase(player, arm, forward);
        double t = smoothstep(releaseProgress);
        double theta = Mth.lerp(t, Math.PI * 0.5D, -0.36D);
        double radius = 0.72D;
        Vec3 center = base.add(forward.scale(0.34D)).add(0.0D, 0.05D, 0.0D);
        return center
                .add(forward.scale(Math.cos(theta) * radius))
                .add(0.0D, Math.sin(theta) * radius, 0.0D);
    }

    private static Vec3 precisionHandAnchor(Player player, HumanoidArm arm,
                                            Vec3 attackDirection, double rawProgress) {
        return precisionHandAnchor(player, arm, attackDirection, rawProgress, 1.0D);
    }

    private static double precisionHalfProgress(double rawProgress) {
        return Mth.clamp(rawProgress, 0.0D, 1.0D);
    }

    private static double precisionHalfSwingSign(double rawProgress, double swingSign) {
        return swingSign >= 0.0D ? 1.0D : -1.0D;
    }

    private static boolean precisionGhostMirrorHalf(double rawProgress) {
        return false;
    }

    private static Vec3 precisionHandAnchor(Player player, HumanoidArm arm,
                                            Vec3 attackDirection, double rawProgress,
                                            double swingSign) {
        Vec3 aim = attackDirection.lengthSqr() < 1.0E-10D
                ? aimDirection(player) : attackDirection.normalize();
        Vec3 horizontal = new Vec3(aim.x, 0.0D, aim.z);
        if (horizontal.lengthSqr() < 1.0E-10D) {
            horizontal = physicalForward(player);
        } else {
            horizontal = horizontal.normalize();
        }
        Vec3 right = new Vec3(-horizontal.z, 0.0D, horizontal.x);
        double side = arm == HumanoidArm.RIGHT ? 1.0D : -1.0D;
        double sign = swingSign >= 0.0D ? 1.0D : -1.0D;
        Vec3 base = handBase(player, arm, aim);
        double raw = Mth.clamp(rawProgress, 0.0D, 1.0D);
        double forwardOffset;
        double verticalOffset;
        double lateralOffset;
        if (raw < 0.30D) {
            double t = smoothstep(raw / 0.30D);
            forwardOffset = Mth.lerp(t, 0.0D, 0.08D);
            verticalOffset = Mth.lerp(t, 0.0D, 0.68D);
            lateralOffset = Mth.lerp(t, 0.0D, side * sign * 0.16D);
        } else if (raw < 0.72D) {
            double t = (raw - 0.30D) / 0.42D;
            double accelerated = Math.pow(Mth.clamp(t, 0.0D, 1.0D), 1.55D);
            forwardOffset = Mth.lerp(accelerated, 0.08D, 0.86D);
            verticalOffset = Mth.lerp(accelerated, 0.68D, -0.08D);
            lateralOffset = Mth.lerp(accelerated, side * sign * 0.16D,
                    -side * sign * 0.10D);
        } else {
            double t = smoothstep((raw - 0.72D) / 0.28D);
            forwardOffset = Mth.lerp(t, 0.86D, 0.20D);
            verticalOffset = Mth.lerp(t, -0.08D, 0.0D);
            lateralOffset = Mth.lerp(t, -side * sign * 0.10D, 0.0D);
        }
        return base.add(aim.scale(forwardOffset))
                .add(0.0D, verticalOffset, 0.0D)
                .add(right.scale(lateralOffset));
    }

    private static Vec3 drivenHandAnchor(Player player, HumanoidArm arm, AttackDrive mode,
                                         Vec3 attackDirection, double rawProgress) {
        return mode == AttackDrive.CHARGED_SLAM
                ? chargedSpinHandAnchor(player, arm, attackDirection, rawProgress * RIGHT_CHARGE_TICKS)
                : precisionHandAnchor(player, arm, attackDirection, rawProgress);
    }

    private static double particleMass(int particle) {
        if (particle <= 0) {
            return Double.POSITIVE_INFINITY;
        }

        double taper = (particle - 1.0D) / Math.max(1.0D, POINTS - 2.0D);
        double remaining = 1.0D - Mth.clamp(taper, 0.0D, 1.0D);
        return 0.065D + 0.935D * remaining * remaining;
    }

    private static double inverseMass(int particle) {

        return 1.0D / particleMass(Math.max(0, particle));
    }

    private static double maximumVerletStep(int particle, double substepSeconds) {
        double taper = particle / (double) (POINTS - 1);

        double maximumSpeed = 82.0D + 108.0D * Math.pow(taper, 1.65D);
        return maximumSpeed * substepSeconds;
    }

    private static boolean finite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y)
                && Double.isFinite(value.z);
    }

    public record ArmPose(float pitch, float yaw, float roll) {}

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

    private enum AttackDrive { NONE, CHARGED_SLAM, PRECISION }

    private record StateKey(UUID player, HumanoidArm arm) {}

    private record ClientPrecisionRequest(Vec3 direction, Vec3 handleAxis,
                                          boolean momentumCarry, double swingSign) {}

    private record TipTrailSample(Vec3 position, long gameTime) {}

    private static final class WhipState {
        private final HumanoidArm arm;
        private final Vec3[] points = new Vec3[POINTS];
        private final Vec3[] previous = new Vec3[POINTS];
        private final Vec3[] renderPrevious = new Vec3[POINTS];
        private final double[] lengthLambda = new double[SEGMENTS];
        private final double[] bendLambda = new double[SEGMENTS - 1];
        private final ArmMotor motor = new ArmMotor();
        private final Vec3[] coilTargets = new Vec3[POINTS];

        private final Vec3[] authoredStoredTargets = new Vec3[POINTS];

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
        private double lastSubstepSeconds = TICK_SECONDS / 8.0D;
        private float tipVisualScale = 1.0F;

        private double precisionSwingSign = 1.0D;

        private double nextFallbackSwingSign = 1.0D;

        private boolean precisionMomentumCarry;

        private float precisionStartCoilProgress = 1.0F;

        private Vec3 pendingPrecisionDirection = Vec3.ZERO;

        private final ArrayDeque<TipTrailSample> tipTrail = new ArrayDeque<>();
        private Vec3 lastTrailSample = null;
        private long lastFlameParticleTick = Long.MIN_VALUE;

        private float coilProgress = 1.0F;

        private float deployVisualProgress = 1.0F;
        private boolean deployingFromStored;

        private long lastAttackDriveGameTime = Long.MIN_VALUE;
        private boolean initialized;

        WhipState(Player player, HumanoidArm arm) {
            this.holder = player;
            this.arm = arm;
        }

        private WhipMomentumContinuity.Continuation precisionContinuation(Vec3 direction, long gameTime) {
            if (!initialized || coilProgress > 1.0E-4F
                    || lastAttackDriveGameTime == Long.MIN_VALUE) {
                return WhipMomentumContinuity.Continuation.NONE;
            }
            return WhipMomentumContinuity.analyze(
                    points, previous, lastSubstepSeconds, direction, arm);
        }

        private double consumeFallbackSwingSign() {
            double sign = nextFallbackSwingSign;
            nextFallbackSwingSign = -nextFallbackSwingSign;
            return sign;
        }

        void captureAnchor(Vec3 anchor, Vec3 handleAxis, long gameTime) {
            if (!finite(anchor)) {
                return;
            }
            this.capturedAnchor = anchor;
            if (finite(handleAxis) && handleAxis.lengthSqr() > 1.0E-10D) {
                this.capturedHandleAxis = handleAxis.normalize();
            }
            this.capturedAnchorGameTime = gameTime;
            if (!initialized && holder != null && holder.level() instanceof ClientLevel level) {
                initialize(level, anchor, holder);
            }
        }

        private boolean hasFreshCapturedAnchor(long gameTime) {
            long age = gameTime - capturedAnchorGameTime;
            return capturedAnchor != null && age >= 0L && age <= 4L;
        }

        private Vec3 renderDisplayAnchor(Vec3 interpolatedRoot, long gameTime) {
            return hasFreshCapturedAnchor(gameTime) ? capturedAnchor : interpolatedRoot;
        }

        void captureFirstPersonViewAnchor(Vec3 cameraLocalAnchor, long gameTime,
                                              float partialTick) {
            if (!finite(cameraLocalAnchor)) {
                return;
            }
            double sampleTime = gameTime + Mth.clamp(partialTick, 0.0F, 1.0F);
            if (capturedFirstPersonViewAnchor != null
                    && Double.isFinite(capturedFirstPersonViewSampleTime)) {
                if (sampleTime + 1.0E-4D < capturedFirstPersonViewSampleTime) {

                    previousFirstPersonViewAnchor = null;
                    previousFirstPersonViewSampleTime = Double.NaN;
                } else if (sampleTime > capturedFirstPersonViewSampleTime + 1.0E-4D) {
                    previousFirstPersonViewAnchor = capturedFirstPersonViewAnchor;
                    previousFirstPersonViewSampleTime = capturedFirstPersonViewSampleTime;
                }
            }
            this.capturedFirstPersonViewAnchor = cameraLocalAnchor;
            this.capturedFirstPersonViewSampleTime = sampleTime;
            this.capturedFirstPersonViewGameTime = gameTime;
        }

        private Vec3 firstPersonDisplayAnchor(Camera camera, long gameTime, float partialTick,
                                              Vec3 fallback) {
            if (capturedFirstPersonViewAnchor == null
                    || gameTime - capturedFirstPersonViewGameTime > 4L) {
                return fallback;
            }

            Vec3 cameraRelativeWorld = WhipFirstPersonFov.cameraVectorToWorld(
                    capturedFirstPersonViewAnchor, camera);
            return camera.getPosition().add(cameraRelativeWorld);
        }

        void tick(ClientLevel level, Player player, long gameTime) {
            this.holder = player;
            this.lastSeenGameTime = gameTime;
            pruneTipTrail(gameTime);
            StateKey key = new StateKey(player.getUUID(), arm);

            AttackDrive chargedRequest = PENDING_ATTACKS.get(key);
            ArrayDeque<ClientPrecisionRequest> precisionQueue = PENDING_PRECISION.get(key);
            ClientPrecisionRequest precisionRequest = precisionQueue == null
                    ? null : precisionQueue.peekFirst();
            AttackDrive requested = chargedRequest == AttackDrive.CHARGED_SLAM
                    ? AttackDrive.CHARGED_SLAM
                    : (precisionRequest != null ? AttackDrive.PRECISION : AttackDrive.NONE);

            if (precisionRequest != null) {
                pendingPrecisionDirection = precisionRequest.direction();
                precisionHandleAxis = finite(precisionRequest.handleAxis())
                        && precisionRequest.handleAxis().lengthSqr() > 1.0E-10D
                        ? precisionRequest.handleAxis().normalize() : capturedHandleAxis;
                precisionHandleAxisTicks = Math.max(precisionHandleAxisTicks, 10);
            }

            WhipMomentumContinuity.Continuation fallbackContinuation = precisionRequest == null
                    ? precisionContinuation(aimDirection(player), gameTime)
                    : WhipMomentumContinuity.Continuation.NONE;
            double requestedSwingSign = precisionRequest != null
                    ? precisionRequest.swingSign() : fallbackContinuation.swingSign();
            boolean requestedMomentumCarry = precisionRequest != null
                    ? precisionRequest.momentumCarry() : fallbackContinuation.active();
            motor.tick(player, arm, requested, requestedSwingSign, requestedMomentumCarry);
            boolean acceptedExplicitRequest = requested != AttackDrive.NONE
                    && motor.requestAcceptedThisTick();
            if (acceptedExplicitRequest && requested == AttackDrive.CHARGED_SLAM) {
                PENDING_ATTACKS.remove(key, AttackDrive.CHARGED_SLAM);
            } else if (acceptedExplicitRequest && requested == AttackDrive.PRECISION
                    && precisionQueue != null) {
                precisionQueue.pollFirst();
                if (precisionQueue.isEmpty()) {
                    PENDING_PRECISION.remove(key);
                }
            }

            if (motor.driveStartedThisTick() && motor.driveModeThisTick() == AttackDrive.PRECISION) {
                if (finite(pendingPrecisionDirection)
                        && pendingPrecisionDirection.lengthSqr() > 1.0E-10D) {
                    motor.overridePrecisionDirection(pendingPrecisionDirection);
                }
                precisionSwingSign = motor.precisionSwingSign();
                precisionMomentumCarry = motor.precisionMomentumCarry();
                pendingPrecisionDirection = Vec3.ZERO;
            }
            boolean handleDriveActive = motor.wasDrivenThisTick();
            boolean chargePhase = motor.chargePhaseThisTick();
            boolean releasePhase = motor.releasePhaseThisTick();
            AttackDrive driveMode = motor.driveModeThisTick();
            Vec3 attackDirection = motor.attackDirection();

            if (chargePhase) {
                tipVisualScale = chargedTipScaleForCharge(motor.chargeToTicks());
            } else if (releasePhase) {
                tipVisualScale = RIGHT_TIP_MAX_SCALE;
            } else if (tipVisualScale > 1.001F) {
                tipVisualScale = Mth.lerp(0.28F, tipVisualScale, 1.0F);
            } else {
                tipVisualScale = 1.0F;
            }

            Vec3 renderedAnchor = capturedAnchor != null
                    && gameTime - capturedAnchorGameTime <= 4L
                    ? capturedAnchor
                    : fallbackAnchor(player, arm);
            if (!initialized) {
                initialize(level, renderedAnchor, player);
            }

            for (int i = 0; i < POINTS; ++i) {
                renderPrevious[i] = points[i];
            }

            if (motor.driveStartedThisTick()) {
                if (driveMode == AttackDrive.PRECISION) {
                    precisionStartCoilProgress = Mth.clamp(coilProgress, 0.0F, 1.0F);
                    deployingFromStored = precisionStartCoilProgress > 1.0E-4F;
                    deployVisualProgress = deployingFromStored ? 0.0F : 1.0F;
                    precisionFollowInitialized = false;
                    if (precisionHandleAxisTicks <= 0) {
                        precisionHandleAxis = capturedHandleAxis;
                    }
                    precisionHandleAxisTicks = Math.max(precisionHandleAxisTicks, 10);
                }
                Vec3 canonicalStart = driveMode == AttackDrive.CHARGED_SLAM
                        ? chargedSpinHandAnchor(player, arm, attackDirection, 0.0D)
                        : precisionHandAnchor(player, arm, attackDirection, 0.0D, precisionSwingSign);
                physicalDriveOffset = renderedAnchor.subtract(canonicalStart);
                highFidelityTicksRemaining = Math.max(HIGH_FIDELITY_TICKS, 40);
            }
            if (motor.releaseStartedThisTick()) {
                Vec3 releaseStart = chargedReleaseHandAnchor(
                        player, arm, attackDirection, 0.0D);
                physicalDriveOffset = points[0].subtract(releaseStart);
                highFidelityTicksRemaining = Math.max(highFidelityTicksRemaining, 36);
            }

            Vec3 targetAnchor;
            if (chargePhase) {
                targetAnchor = chargedSpinHandAnchor(player, arm, attackDirection,
                        motor.chargeToTicks()).add(physicalDriveOffset);
            } else if (releasePhase) {
                targetAnchor = chargedReleaseHandAnchor(player, arm, attackDirection,
                        motor.releaseToProgress()).add(physicalDriveOffset);
            } else if (handleDriveActive && driveMode == AttackDrive.PRECISION) {
                targetAnchor = precisionHandAnchor(player, arm, attackDirection,
                        motor.driveToRawProgress(), precisionSwingSign).add(physicalDriveOffset);
            } else {
                targetAnchor = renderedAnchor;
            }

            updateCoilState(player, gameTime, handleDriveActive, driveMode,
                    motor.driveToRawProgress(), targetAnchor);

            Vec3 startAnchor = points[0];
            Vec3 anchorDelta = targetAnchor.subtract(startAnchor);
            if (anchorDelta.lengthSqr() > 9.0D) {
                for (int i = 0; i < POINTS; ++i) {
                    points[i] = points[i].add(anchorDelta);
                    previous[i] = previous[i].add(anchorDelta);
                    renderPrevious[i] = renderPrevious[i].add(anchorDelta);
                }
                startAnchor = targetAnchor;
            }

            int substeps = chooseAdaptiveSubsteps(handleDriveActive, chargePhase,
                    releasePhase, driveMode);
            substeps = WhipPerformanceTuning.capRemoteSubsteps(holder, substeps);
            double substepSeconds = configureAdaptiveTimestep(substeps);
            double substepSecondsSqr = substepSeconds * substepSeconds;
            double velocityRetention = Math.pow(TICK_VELOCITY_RETENTION, 1.0D / substeps);
            double surfaceTangentRetention = Math.pow(
                    SURFACE_TANGENT_RETENTION_PER_TICK, 1.0D / substeps);

            depenetrateFromBlocks(level);

            for (int substep = 0; substep < substeps; ++substep) {
                Vec3[] before = points.clone();
                double alpha = (substep + 1.0D) / substeps;
                double precisionProgress = 1.0D;
                double chargeTicks = motor.chargeToTicks();
                double releaseProgress = motor.releaseToProgress();
                Vec3 substepAnchor;
                if (chargePhase) {
                    chargeTicks = Mth.lerp(alpha,
                            motor.chargeFromTicks(), motor.chargeToTicks());
                    substepAnchor = chargedSpinHandAnchor(player, arm, attackDirection,
                            chargeTicks).add(physicalDriveOffset);
                } else if (releasePhase) {
                    releaseProgress = Mth.lerp(alpha,
                            motor.releaseFromProgress(), motor.releaseToProgress());
                    substepAnchor = chargedReleaseHandAnchor(player, arm, attackDirection,
                            releaseProgress).add(physicalDriveOffset);
                } else if (handleDriveActive && driveMode == AttackDrive.PRECISION) {
                    precisionProgress = Mth.lerp(alpha,
                            motor.driveFromRawProgress(), motor.driveToRawProgress());
                    substepAnchor = precisionHandAnchor(player, arm, attackDirection,
                            precisionProgress, precisionSwingSign).add(physicalDriveOffset);
                } else {
                    substepAnchor = startAnchor.lerp(targetAnchor, alpha);
                }

                predict(substepAnchor, substepSecondsSqr, velocityRetention, substepSeconds);
                if (coilProgress > 1.0E-4F && !handleDriveActive) {
                    applyCoilGuidance();
                }
                if (chargePhase) {
                    applyChargeForces(player, attackDirection, chargeTicks,
                            substepSeconds, substepSecondsSqr);
                } else if (releasePhase) {
                    applyReleaseForces(player, attackDirection, releaseProgress,
                            substepSeconds, substepSecondsSqr);
                } else if (handleDriveActive && driveMode == AttackDrive.PRECISION
                        && precisionProgress >= 0.0D
                        && precisionProgress < PRECISION_GUIDE_END) {
                    applyPrecisionGuidance(player, substepAnchor, attackDirection, precisionProgress,
                            precisionMomentumCarry, substepSeconds, substepSecondsSqr);
                }

                double collisionTipScale = chargePhase
                        ? chargedTipScaleForCharge(chargeTicks)
                        : (releasePhase ? RIGHT_TIP_MAX_SCALE : Math.max(1.0F, tipVisualScale));
                WhipBlockCollision.SegmentEnvironment[] blockBroadphase =
                        WhipBlockCollision.buildBroadphase(level, before, points,
                                EmeraldWhipGeometry.SEGMENT_COLLIDER_RADIUS,
                                collisionTipScale);

                java.util.Arrays.fill(lengthLambda, 0.0D);
                java.util.Arrays.fill(bendLambda, 0.0D);
                for (int iteration = 0; iteration < SOLVER_ITERATIONS; ++iteration) {

                    solveRootFibreTether(substepAnchor);

                    for (int i = 0; i < SEGMENTS; ++i) {
                        solveDistance(i, i + 1, currentRestLength(i),
                                LENGTH_COMPLIANCE, lengthLambda, i, false,
                                substepSecondsSqr);
                    }
                    enforceMaximumStretch();
                    if (iteration == 1) {
                        solveBlockCapsuleContacts(blockBroadphase, before, collisionTipScale,
                                1.0D, false);
                    }
                }
                solveRootFibreTether(substepAnchor);
                solveSelfCollision();
                solveBlockCapsuleContacts(blockBroadphase, before, collisionTipScale,
                        surfaceTangentRetention, true);
                Vec3 tipDelta = points[POINTS - 1].subtract(before[POINTS - 1]);
                double tipSpeed = tipDelta.length() / Math.max(1.0E-6D, substepSeconds);
                if ((coilProgress <= 1.0E-4F
                        || (handleDriveActive && driveMode == AttackDrive.PRECISION))
                        && shouldDisplayTipTrail(player)) {
                    recordTipTrail(points[POINTS - 1], gameTime, tipSpeed);
                } else {
                    tipTrail.clear();
                    lastTrailSample = null;
                }
            }
            solveRootFibreTether(targetAnchor);
            emitFireAspectTipParticle(level, player, gameTime);
            if (highFidelityTicksRemaining > 0) {
                --highFidelityTicksRemaining;
            }
            if (precisionHandleAxisTicks > 0) {
                --precisionHandleAxisTicks;
            }
            boolean valid = true;
            for (Vec3 point : points) {
                if (!finite(point)) {
                    initialize(level, targetAnchor, player);
                    highFidelityTicksRemaining = 0;
                    tipVisualScale = 1.0F;
                    tipTrail.clear();
                    lastTrailSample = null;
                    valid = false;
                    break;
                }
            }
        }

        private void pruneTipTrail(long gameTime) {
            while (!tipTrail.isEmpty()
                    && gameTime - tipTrail.peekFirst().gameTime() > TIP_TRAIL_LIFETIME_TICKS) {
                tipTrail.removeFirst();
            }
            if (tipTrail.isEmpty()) {
                lastTrailSample = null;
            }
        }

        private void recordTipTrail(Vec3 tip, long gameTime, double tipSpeed) {
            if (!finite(tip) || tipSpeed < TIP_TRAIL_MIN_SPEED) {
                return;
            }
            if (lastTrailSample != null
                    && lastTrailSample.distanceToSqr(tip) < TIP_TRAIL_MIN_SAMPLE_DISTANCE_SQR) {
                return;
            }
            tipTrail.addLast(new TipTrailSample(tip, gameTime));
            lastTrailSample = tip;
            while (tipTrail.size() > TIP_TRAIL_MAX_SAMPLES) {
                tipTrail.removeFirst();
            }
        }

        private void emitFireAspectTipParticle(ClientLevel level, Player player, long gameTime) {
            if (!initialized || gameTime == lastFlameParticleTick
                    || !hasFireAspect(stackForArm(player, arm))) {
                return;
            }
            Vec3 tip = points[POINTS - 1];
            if (!finite(tip)) {
                return;
            }
            lastFlameParticleTick = gameTime;
            double vx = (level.random.nextDouble() - 0.5D) * 0.035D;
            double vy = 0.025D + level.random.nextDouble() * 0.035D;
            double vz = (level.random.nextDouble() - 0.5D) * 0.035D;
            level.addParticle(ParticleTypes.FLAME, tip.x, tip.y, tip.z, vx, vy, vz);
        }

        private void applyChargeForces(Player player, Vec3 forwardInput, double chargeTicks,
                                       double substepSeconds, double substepSecondsSqr) {
            Vec3 forward = new Vec3(forwardInput.x, 0.0D, forwardInput.z);
            if (forward.lengthSqr() < 1.0E-10D) {
                forward = physicalForward(player);
            } else {
                forward = forward.normalize();
            }
            Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
            double side = arm == HumanoidArm.RIGHT ? 1.0D : -1.0D;
            Vec3 center = chargedSpinCenter(player, arm, forward).add(physicalDriveOffset);
            double charge = rightChargeProgress(chargeTicks);
            double omega = rightSpinOmega(chargeTicks);
            double envelope = smoothstep(charge);

            for (int i = 1; i < POINTS; ++i) {
                Vec3 radial = points[i].subtract(center);
                radial = new Vec3(radial.x, 0.0D, radial.z);
                double radius = radial.length();
                if (radius < 1.0E-5D) continue;
                Vec3 radialDir = radial.scale(1.0D / radius);
                double f = radialDir.dot(forward);
                double r = radialDir.dot(right);
                Vec3 tangentDir = forward.scale(-r).add(right.scale(f)).scale(CHARGE_SPIN_SIGN * side);
                if (tangentDir.lengthSqr() > 1.0E-10D) tangentDir = tangentDir.normalize();

                double taper = i / (double)(POINTS - 1);
                double weight = Math.pow(taper, 1.30D) * envelope;
                double radialAccel = Math.min(CHARGE_MAX_RADIAL_ACCEL,
                        omega * omega * radius * CHARGE_CENTRIFUGAL_SCALE) * weight;
                Vec3 velocity = points[i].subtract(previous[i]);
                double tangentSpeed = velocity.dot(tangentDir) / substepSeconds;
                double desiredSpeed = omega * radius;
                double tangentAccel = Mth.clamp((desiredSpeed - tangentSpeed) * 8.0D,
                        -CHARGE_MAX_TANGENTIAL_ACCEL, CHARGE_MAX_TANGENTIAL_ACCEL) * weight;
                Vec3 acceleration = radialDir.scale(radialAccel)
                        .add(tangentDir.scale(tangentAccel));
                points[i] = points[i].add(acceleration.scale(substepSecondsSqr));
            }
        }

        private void applyReleaseForces(Player player, Vec3 forwardInput, double releaseProgress,
                                        double substepSeconds, double substepSecondsSqr) {
            Vec3 forward = new Vec3(forwardInput.x, 0.0D, forwardInput.z);
            if (forward.lengthSqr() < 1.0E-10D) {
                forward = physicalForward(player);
            } else {
                forward = forward.normalize();
            }
            Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
            Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
            double release = smoothstep(releaseProgress);

            double planeBlend = smoothstep(Mth.clamp(releaseProgress / 0.30D, 0.0D, 1.0D));

            Vec3 center = chargedSpinCenter(player, arm, forward).add(physicalDriveOffset);

            double pendulumAngle = release * Math.PI * 0.82D;
            Vec3 verticalRadialDir = up.scale(Math.cos(pendulumAngle))
                    .add(forward.scale(Math.sin(pendulumAngle))).normalize();
            Vec3 verticalTangentDir = forward.scale(Math.cos(pendulumAngle))
                    .add(up.scale(-Math.sin(pendulumAngle))).normalize();

            double accumulatedLength = 0.0D;
            for (int i = 1; i < POINTS; ++i) {
                accumulatedLength += EmeraldWhipGeometry.REST_LENGTHS[i - 1];
                double taper = i / (double)(POINTS - 1);
                double weight = Math.pow(taper, 1.22D);

                Vec3 relative = points[i].subtract(center);
                double worldRadius = relative.length();
                if (worldRadius < 1.0E-6D) {
                    continue;
                }
                Vec3 currentRadialDir = relative.scale(1.0D / worldRadius);
                Vec3 radialDir = currentRadialDir.lerp(verticalRadialDir, planeBlend);
                if (radialDir.lengthSqr() < 1.0E-10D) {
                    radialDir = verticalRadialDir;
                } else {
                    radialDir = radialDir.normalize();
                }

                double targetRadius = Mth.clamp(worldRadius,
                        accumulatedLength * 0.72D, accumulatedLength);
                Vec3 targetPoint = center.add(radialDir.scale(targetRadius));
                Vec3 pathError = targetPoint.subtract(points[i]);
                double lateralOffset = relative.dot(right);

                Vec3 stepVelocity = points[i].subtract(previous[i]);
                double stepSpeed = stepVelocity.length();
                double speedPerSecond = stepSpeed / Math.max(1.0E-6D, substepSeconds);
                Vec3 tangentDir = verticalTangentDir;
                if (stepSpeed > 1.0E-8D) {
                    Vec3 currentVelocityDir = stepVelocity.scale(1.0D / stepSpeed);
                    tangentDir = currentVelocityDir.lerp(verticalTangentDir, planeBlend);
                    if (tangentDir.lengthSqr() < 1.0E-10D) {
                        tangentDir = verticalTangentDir;
                    } else {
                        tangentDir = tangentDir.normalize();
                    }

                    double steer = Mth.clamp((0.52D * planeBlend) * weight
                            + 0.10D * taper * planeBlend, 0.0D, 0.78D);
                    Vec3 guided = stepVelocity.lerp(tangentDir.scale(stepSpeed), steer);
                    double guidedLength = guided.length();
                    if (guidedLength > 1.0E-8D) {
                        guided = guided.scale(stepSpeed / guidedLength);
                        previous[i] = points[i].subtract(guided);
                    }
                }

                double centripetalAccel = Math.min(1450.0D,
                        speedPerSecond * speedPerSecond / Math.max(0.25D, targetRadius));
                Vec3 acceleration = radialDir.scale(-centripetalAccel * weight)
                        .add(tangentDir.scale((80.0D + 560.0D * release)
                                * weight * planeBlend))
                        .add(pathError.scale(360.0D * weight * planeBlend))
                        .add(right.scale(-lateralOffset * 980.0D * weight * planeBlend))
                        .add(up.scale(-GRAVITY * weight * planeBlend));

                double accelLength = acceleration.length();
                if (accelLength > 1800.0D) {
                    acceleration = acceleration.scale(1800.0D / accelLength);
                }
                points[i] = points[i].add(acceleration.scale(substepSecondsSqr));
            }
        }

        private void applyPrecisionGuidance(Player player, Vec3 root, Vec3 attackDirection,
                                            double rawProgress, boolean momentumCarry,
                                            double substepSeconds, double substepSecondsSqr) {
            Vec3 aim = attackDirection.lengthSqr() > 1.0E-10D
                    ? attackDirection.normalize() : aimDirection(player);
            Vec3 center = precisionTrajectoryCenter(player, aim);
            Vec3 sourceDirection = precisionCrosshairSweepDirection(
                    player, aim, rawProgress, precisionSwingSign);

            if (!precisionFollowInitialized) {
                initializePrecisionFollowDirections(center, sourceDirection);
            }

            double dt = Math.max(1.0E-5D, substepSeconds);
            double blend = 1.0D - Math.exp(-dt / PRECISION_FOLLOW_PER_LINK_TAU);
            blend = Mth.clamp(blend, 0.0D, 1.0D);

            for (int i = POINTS - 1; i >= 1; --i) {
                Vec3 oldDirection = precisionFollowDirections[i];
                Vec3 leaderDirection = precisionFollowDirections[i - 1];
                Vec3 mixed = oldDirection.lerp(leaderDirection, blend);
                if (mixed.lengthSqr() < 1.0E-10D) {
                    mixed = leaderDirection;
                }
                Vec3 nextDirection = mixed.normalize();
                precisionFollowDirectionDelta[i] = nextDirection.subtract(oldDirection);
                precisionFollowDirections[i] = nextDirection;
            }
            precisionFollowDirectionDelta[0] = sourceDirection.subtract(precisionFollowDirections[0]);
            precisionFollowDirections[0] = sourceDirection;

            double rootRadius = Mth.clamp(points[0].distanceTo(center), 0.45D, 1.15D);
            double accumulatedLength = 0.0D;
            double carryScale = momentumCarry
                    ? Mth.lerp(smoothstep(Mth.clamp(rawProgress / 0.18D, 0.0D, 1.0D)), 0.72D, 1.0D)
                    : 1.0D;

            Vec3 rootTarget = center.add(sourceDirection.scale(rootRadius));
            Vec3 rootError = rootTarget.subtract(points[0]);
            double rootGate = precisionCrosshairGate(rawProgress, 0.0D);
            Vec3 rootCrosshairPoint = center.add(aim.scale(rootRadius));
            Vec3 rootAcceleration = rootError.scale(PRECISION_FOLLOW_POSITION_ACCEL * 0.42D)
                    .add(rootCrosshairPoint.subtract(points[0])
                            .scale(PRECISION_CROSSHAIR_GATE_ACCEL * 0.38D * rootGate));
            double rootAccelLength = rootAcceleration.length();
            if (rootAccelLength > PRECISION_FOLLOW_MAX_ACCEL * 0.55D) {
                rootAcceleration = rootAcceleration.scale(
                        (PRECISION_FOLLOW_MAX_ACCEL * 0.55D) / rootAccelLength);
            }
            points[0] = points[0].add(rootAcceleration.scale(substepSecondsSqr));

            for (int i = 1; i < POINTS; ++i) {
                accumulatedLength += currentRestLength(i - 1);
                double taper = i / (double)(POINTS - 1);
                double targetRadius = rootRadius + accumulatedLength;
                double gate = precisionCrosshairGate(rawProgress, taper);

                Vec3 followedDirection = precisionFollowDirections[i];
                Vec3 desiredDirection = followedDirection.lerp(aim, gate);
                if (desiredDirection.lengthSqr() < 1.0E-10D) {
                    desiredDirection = aim;
                } else {
                    desiredDirection = desiredDirection.normalize();
                }
                Vec3 targetPoint = center.add(desiredDirection.scale(targetRadius));
                Vec3 pathError = targetPoint.subtract(points[i]);

                Vec3 relative = points[i].subtract(center);
                double currentRadius = relative.length();
                double radialError = targetRadius - currentRadius;

                Vec3 desiredAngularVelocity = precisionFollowDirectionDelta[i]
                        .scale(targetRadius / dt);
                Vec3 currentVelocity = points[i].subtract(previous[i]).scale(1.0D / dt);
                Vec3 velocityError = desiredAngularVelocity.subtract(currentVelocity);

                Vec3 crosshairPoint = center.add(aim.scale(targetRadius));
                Vec3 crosshairError = crosshairPoint.subtract(points[i]);
                double tipGain = Mth.lerp(taper, 1.0D, PRECISION_FOLLOW_TIP_GAIN);
                Vec3 acceleration = pathError.scale(PRECISION_FOLLOW_POSITION_ACCEL)
                        .add(desiredDirection.scale(radialError * PRECISION_FOLLOW_RADIAL_ACCEL))
                        .add(velocityError.scale(PRECISION_FOLLOW_VELOCITY_ACCEL))
                        .add(crosshairError.scale(PRECISION_CROSSHAIR_GATE_ACCEL * gate))
                        .scale(carryScale * tipGain);

                double accelLength = acceleration.length();
                if (accelLength > PRECISION_FOLLOW_MAX_ACCEL) {
                    acceleration = acceleration.scale(PRECISION_FOLLOW_MAX_ACCEL / accelLength);
                }
                points[i] = points[i].add(acceleration.scale(substepSecondsSqr));
            }
        }

        private void initializePrecisionFollowDirections(Vec3 center, Vec3 fallbackDirection) {
            Vec3 last = fallbackDirection;
            for (int i = 0; i < POINTS; ++i) {
                Vec3 relative = points[i].subtract(center);
                Vec3 direction = relative.lengthSqr() > 1.0E-10D ? relative.normalize() : last;
                precisionFollowDirections[i] = direction;
                precisionFollowDirectionDelta[i] = Vec3.ZERO;
                last = direction;
            }
            precisionFollowInitialized = true;
        }

        private Vec3 precisionTrajectoryCenter(Player player, Vec3 attackDirection) {

            return player.getEyePosition();
        }

        private Vec3 precisionCrosshairSweepDirection(Player player, Vec3 attackDirection,
                                                       double rawProgress, double swingSign) {
            Vec3 aim = attackDirection.lengthSqr() > 1.0E-10D
                    ? attackDirection.normalize() : aimDirection(player);
            Vec3 referenceUp = Math.abs(aim.y) < 0.94D
                    ? new Vec3(0.0D, 1.0D, 0.0D)
                    : new Vec3(0.0D, 0.0D, 1.0D);
            Vec3 side = referenceUp.cross(aim);
            if (side.lengthSqr() < 1.0E-10D) {
                side = new Vec3(1.0D, 0.0D, 0.0D);
            } else {
                side = side.normalize();
            }
            double phase = Mth.clamp(
                    (rawProgress - PRECISION_CROSSHAIR_SOURCE_PROGRESS)
                            / PRECISION_CROSSHAIR_SWEEP_HALF_SPAN_PROGRESS,
                    -1.0D, 1.0D);
            double signedAngle = phase * PRECISION_CROSSHAIR_SWEEP_HALF_ANGLE_RADIANS
                    * (swingSign >= 0.0D ? 1.0D : -1.0D);
            Vec3 direction = aim.scale(Math.cos(signedAngle))
                    .add(side.scale(Math.sin(signedAngle)));
            return direction.lengthSqr() > 1.0E-10D ? direction.normalize() : aim;
        }

        private double precisionCrosshairGate(double rawProgress, double chainTaper) {
            double delayProgress = (PRECISION_FOLLOW_TOTAL_DELAY_SECONDS / PRECISION_ATTACK_SECONDS)
                    * Mth.clamp(chainTaper, 0.0D, 1.0D);
            double crossingProgress = PRECISION_CROSSHAIR_SOURCE_PROGRESS + delayProgress;
            double distance = (rawProgress - crossingProgress)
                    / PRECISION_CROSSHAIR_GATE_WIDTH_PROGRESS;
            return Math.exp(-distance * distance);
        }

        private void updateCoilState(Player player, long gameTime, boolean handleDriveActive,
                                     AttackDrive driveMode, float driveRawProgress, Vec3 root) {
            if (handleDriveActive) {
                lastAttackDriveGameTime = gameTime;
                if (deployingFromStored) {
                    deployVisualProgress = Mth.clamp(
                            driveRawProgress / Math.max(1.0E-4F, PRECISION_DEPLOY_END_RAW_PROGRESS),
                            0.0F, 1.0F);
                    if (deployVisualProgress >= 0.999F) {
                        deployVisualProgress = 1.0F;
                        deployingFromStored = false;
                    }
                } else {
                    deployVisualProgress = 1.0F;
                }
                if (coilProgress > 0.0F) {

                    coilProgress = 0.0F;
                    highFidelityTicksRemaining = Math.max(highFidelityTicksRemaining, 24);
                }
                return;
            }

            deployVisualProgress = 1.0F;
            deployingFromStored = false;

            if (lastAttackDriveGameTime == Long.MIN_VALUE) {
                coilProgress = 1.0F;
            } else if (gameTime - lastAttackDriveGameTime >= COIL_IDLE_DELAY_TICKS) {
                if (coilProgress < 1.0F) {
                    coilProgress = Math.min(1.0F,
                            coilProgress + 1.0F / (float)COIL_DURATION_TICKS);
                    highFidelityTicksRemaining = Math.max(highFidelityTicksRemaining, 10);
                }
            } else {
                coilProgress = 0.0F;
            }

            if (coilProgress > 1.0E-4F) {
                buildStoredSwordTargets(root);
            }
        }

        private void buildStoredSwordTargets(Vec3 root) {

            WhipCoilPose.build(root, holder, arm, capturedHandleAxis,
                    EmeraldWhipGeometry.REST_LENGTHS, PHYSICAL_REST_SCALE,
                    COIL_LEAD_SEGMENTS, COIL_TURNS, COIL_LAYER_PITCH, coilTargets);

            Vec3 axis = capturedHandleAxis;
            if (!finite(axis) || axis.lengthSqr() < 1.0E-10D) {
                axis = new Vec3(0.0D, 1.0D, 0.0D);
            } else {
                axis = axis.normalize();
            }
            authoredStoredTargets[0] = root;
            Vec3 cursor = root;
            for (int i = 0; i < SEGMENTS; ++i) {
                cursor = cursor.add(axis.scale(
                        EmeraldWhipGeometry.REST_LENGTHS[i] * STORED_REST_SCALE));
                authoredStoredTargets[i + 1] = cursor;
            }
        }

        private double currentRestLength(int segmentIndex) {

            return EmeraldWhipGeometry.REST_LENGTHS[segmentIndex] * PHYSICAL_REST_SCALE;
        }

        private double currentPairRestLength(int leftSegmentIndex) {
            return currentRestLength(leftSegmentIndex) + currentRestLength(leftSegmentIndex + 1);
        }

        private void applyCoilGuidance() {
            for (int i = 0; i < POINTS; ++i) {
                double activation = coilActivationForPoint(i);
                if (activation <= 1.0E-6D) {
                    continue;
                }
                Vec3 delta = coilTargets[i].subtract(points[i]);
                double distance = delta.length();
                if (distance > 1.0E-8D) {
                    double desired = Math.min(distance * COIL_POSITION_GAIN * activation,
                            COIL_MAX_PULL_PER_SUBSTEP * activation);
                    points[i] = points[i].add(delta.scale(desired / distance));
                }
                Vec3 velocity = points[i].subtract(previous[i]);
                double retention = 1.0D - COIL_VELOCITY_DAMPING * activation;
                previous[i] = points[i].subtract(velocity.scale(retention));
            }
        }

        private double coilActivationForPoint(int pointIndex) {
            return rootToTipTakeupActivation(pointIndex, coilProgress, 1.65D);
        }

        private double storedVisualActivationForPoint(int pointIndex) {
            return rootToTipTakeupActivation(pointIndex, coilProgress, 1.15D);
        }

        private double rootToTipTakeupActivation(int pointIndex, double progress,
                                                 double transitionSegments) {
            if (progress <= 0.0D || pointIndex < 0) {
                return 0.0D;
            }
            if (pointIndex == 0) {
                return 1.0D;
            }
            double start = (pointIndex - 1.0D) / (double)SEGMENTS;
            if (progress <= start) {
                return 0.0D;
            }
            double end = Math.min(1.0D, start + transitionSegments / (double)SEGMENTS);
            if (end <= start + 1.0E-9D) {
                return 1.0D;
            }
            double activation = Mth.clamp((progress - start) / (end - start), 0.0D, 1.0D);
            return activation * activation * (3.0D - 2.0D * activation);
        }

        private double deploymentActivationForPoint(int pointIndex) {
            if (!deployingFromStored || deployVisualProgress >= 0.999F) {
                return 1.0D;
            }

            double rootLead = 0.0D;
            double total = rootLead;
            for (float rest : EmeraldWhipGeometry.REST_LENGTHS) {
                total += rest;
            }
            double distance = rootLead;
            for (int i = 0; i < Math.min(pointIndex, SEGMENTS); ++i) {
                distance += EmeraldWhipGeometry.REST_LENGTHS[i];
            }
            double start = distance / Math.max(1.0E-6D, total);
            double span = Math.max(1.0D / (double)(SEGMENTS + 1),
                    (pointIndex < SEGMENTS ? EmeraldWhipGeometry.REST_LENGTHS[pointIndex] :
                            EmeraldWhipGeometry.REST_LENGTHS[SEGMENTS - 1]) / Math.max(1.0E-6D, total));
            if (deployVisualProgress <= start) {
                return 0.0D;
            }
            double t = Mth.clamp((deployVisualProgress - start) / span, 0.0D, 1.0D);
            return t * t * (3.0D - 2.0D * t);
        }

        private boolean visualMorphActive() {
            return coilProgress > 1.0E-4F || (deployingFromStored && deployVisualProgress < 0.999F);
        }

        private Vec3 storedVisualPoint(int pointIndex, float partialTick, Vec3 rootCorrection) {
            double rootWeight = 1.0D - pointIndex / (double)(POINTS - 1);
            Vec3 physical = renderPrevious[pointIndex].lerp(points[pointIndex], partialTick)
                    .add(rootCorrection.scale(rootWeight));
            if (authoredStoredTargets[pointIndex] == null) {
                return physical;
            }
            Vec3 authored = authoredStoredTargets[pointIndex].add(rootCorrection);
            if (deployingFromStored && deployVisualProgress < 0.999F) {
                double deployment = deploymentActivationForPoint(pointIndex);
                return authored.lerp(physical, deployment);
            }
            if (coilProgress <= 1.0E-4F) {
                return physical;
            }
            double activation = pointIndex == 0 ? 1.0D : storedVisualActivationForPoint(pointIndex);
            return physical.lerp(authored, activation);
        }

        private void initialize(ClientLevel level, Vec3 root, Player player) {
            buildStoredSwordTargets(root);
            for (int i = 0; i < POINTS; ++i) {

                Vec3 point = coilTargets[i];
                points[i] = point;
                previous[i] = point;
                renderPrevious[i] = point;
            }
            coilProgress = 1.0F;
            deployVisualProgress = 1.0F;
            deployingFromStored = false;
            lastAttackDriveGameTime = Long.MIN_VALUE;
            precisionFollowInitialized = false;
            initialized = true;
        }

        private Vec3 initialDirection(ClientLevel level, Player player, Vec3 root, Vec3 forward) {
            double total = 0.0D;
            for (float restLength : EmeraldWhipGeometry.REST_LENGTHS) {
                total += restLength * PHYSICAL_REST_SCALE;
            }
            BlockHitResult floorHit = level.clip(new ClipContext(
                    root, root.add(0.0D, -total - 0.25D, 0.0D),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (floorHit.getType() != HitResult.Type.MISS) {
                double clearance = Math.max(0.10D,
                        root.y - floorHit.getLocation().y - COLLISION_RADIUS * 2.0D);
                if (clearance < total * 0.92D) {
                    double vertical = Mth.clamp(clearance / total * 0.70D, 0.08D, 0.30D);
                    return new Vec3(forward.x, -vertical, forward.z).normalize();
                }
            }
            return new Vec3(forward.x * 0.16D, -0.987D,
                    forward.z * 0.16D).normalize();
        }

        private void predict(Vec3 anchor, double substepSecondsSqr,
                             double velocityRetention, double substepSeconds) {
            for (int i = 0; i < POINTS; ++i) {
                Vec3 current = points[i];
                Vec3 velocity = current.subtract(previous[i]).scale(velocityRetention);
                double speed = velocity.length();
                double maximumStep = maximumVerletStep(i, substepSeconds);
                if (speed > maximumStep) {
                    velocity = velocity.scale(maximumStep / speed);
                }
                previous[i] = current;
                points[i] = current.add(velocity)
                        .add(0.0D, GRAVITY * substepSecondsSqr, 0.0D);
            }
        }

        private void solveRootFibreTether(Vec3 handleAnchor) {
            Vec3 delta = points[0].subtract(handleAnchor);
            double distance = delta.length();
            double maximum = Math.max(0.035D, ROOT_FIBRE_MAX_LENGTH);
            if (distance <= maximum || distance < 1.0E-9D) {
                return;
            }
            Vec3 corrected = handleAnchor.add(delta.scale(maximum / distance));
            points[0] = corrected;
        }

        private void solveDistance(int a, int b, double rest, double compliance,
                                   double[] lambdas, int lambdaIndex, boolean minimumOnly,
                                   double substepSecondsSqr) {
            Vec3 delta = points[b].subtract(points[a]);
            double distance = delta.length();
            if (distance < 1.0E-9D || (minimumOnly && distance >= rest)) {
                if (minimumOnly) {
                    lambdas[lambdaIndex] = 0.0D;
                }
                return;
            }

            double weightA = inverseMass(a);
            double weightB = inverseMass(b);
            double alpha = compliance / substepSecondsSqr;
            double constraint = distance - rest;
            double deltaLambda = (-constraint - alpha * lambdas[lambdaIndex])
                    / (weightA + weightB + alpha);
            lambdas[lambdaIndex] += deltaLambda;
            Vec3 direction = delta.scale(1.0D / distance);
            if (weightA > 0.0D) {
                points[a] = points[a].add(direction.scale(-weightA * deltaLambda));
            }
            if (weightB > 0.0D) {
                points[b] = points[b].add(direction.scale(weightB * deltaLambda));
            }
        }

        private void enforceMinimumBladeSeparation() {
            for (int i = 0; i < SEGMENTS; ++i) {
                double authoredBlade = EmeraldWhipGeometry.REST_LENGTHS[i];
                double minimum = Math.min(currentRestLength(i), authoredBlade);
                enforceMinimumSpan(i, i + 1, minimum);
            }
        }

        private void enforceMaximumStretch() {
            for (int i = 0; i < SEGMENTS; ++i) {
                Vec3 delta = points[i + 1].subtract(points[i]);
                double distance = delta.length();
                double maximum = currentRestLength(i) * MAX_SEGMENT_STRETCH;
                if (distance <= maximum || distance < 1.0E-9D) {
                    continue;
                }
                double weightA = inverseMass(i);
                double weightB = inverseMass(i + 1);
                double totalWeight = weightA + weightB;
                if (totalWeight <= 0.0D) {
                    continue;
                }
                Vec3 correction = delta.scale((distance - maximum) / distance);
                if (weightA > 0.0D) {
                    points[i] = points[i].add(correction.scale(weightA / totalWeight));
                }
                if (weightB > 0.0D) {
                    points[i + 1] = points[i + 1]
                            .subtract(correction.scale(weightB / totalWeight));
                }
            }
        }

        private void enforceHandleAxis(Vec3 root) {

        }

        private void enforceAntiFold(double substepSecondsSqr) {

        }

        private void enforceHandleBendZone() {

        }

        private void enforceHandleContinuity() {

        }

        private void enforceMinimumSpan(int a, int b, double minimumSpan) {
            Vec3 delta = points[b].subtract(points[a]);
            double distance = delta.length();
            if (distance >= minimumSpan) {
                return;
            }

            Vec3 direction;
            if (distance < 1.0E-8D) {
                direction = b > 1 ? points[b - 1].subtract(points[a]) : new Vec3(0.0D, -1.0D, 0.0D);
                if (direction.lengthSqr() < 1.0E-10D) {
                    direction = new Vec3(0.0D, -1.0D, 0.0D);
                }
                direction = direction.normalize();
            } else {
                direction = delta.scale(1.0D / distance);
            }

            double weightA = inverseMass(a);
            double weightB = inverseMass(b);
            double totalWeight = weightA + weightB;
            if (totalWeight <= 0.0D) {
                return;
            }
            Vec3 correction = direction.scale(minimumSpan - distance);
            if (weightA > 0.0D) {
                points[a] = points[a].add(correction.scale(-weightA / totalWeight));
            }
            if (weightB > 0.0D) {
                points[b] = points[b].add(correction.scale(weightB / totalWeight));
            }
        }

        private void solveSelfCollision() {
            for (int i = 1; i < POINTS; ++i) {
                for (int j = i + 3; j < POINTS; ++j) {

                    double packed = Math.min(coilActivationForPoint(i), coilActivationForPoint(j));
                    double minimumDistance = Mth.lerp(packed, SELF_COLLISION_DISTANCE,
                            SELF_COLLISION_DISTANCE * COIL_SELF_COLLISION_SCALE);
                    double minimumSqr = minimumDistance * minimumDistance;
                    Vec3 delta = points[j].subtract(points[i]);
                    double distanceSqr = delta.lengthSqr();
                    if (distanceSqr >= minimumSqr || distanceSqr < 1.0E-12D) {
                        continue;
                    }
                    double distance = Math.sqrt(distanceSqr);
                    double weightA = inverseMass(i);
                    double weightB = inverseMass(j);
                    double totalWeight = weightA + weightB;
                    if (totalWeight <= 0.0D) {
                        continue;
                    }
                    Vec3 correction = delta.scale((minimumDistance - distance) / distance);
                    points[i] = points[i].add(correction.scale(-weightA / totalWeight));
                    points[j] = points[j].add(correction.scale(weightB / totalWeight));
                }
            }
        }

        private int chooseAdaptiveSubsteps(boolean handleDriveActive, boolean chargePhase,
                                           boolean releasePhase, AttackDrive driveMode) {
            double maxSpeed = 0.0D;
            double dt = Math.max(1.0E-6D, lastSubstepSeconds);
            for (int i = 1; i < POINTS; ++i) {
                maxSpeed = Math.max(maxSpeed,
                        points[i].subtract(previous[i]).length() / dt);
            }
            int steps;
            if (maxSpeed < 28.0D) steps = 6;
            else if (maxSpeed < 58.0D) steps = 8;
            else if (maxSpeed < 105.0D) steps = 12;
            else if (maxSpeed < 165.0D) steps = 18;
            else steps = 24;

            if (releasePhase) steps = Math.max(steps, 18);
            else if (chargePhase || (handleDriveActive && driveMode == AttackDrive.PRECISION)) {
                steps = Math.max(steps, 12);
            } else if (highFidelityTicksRemaining > 0) {
                steps = Math.max(steps, 8);
            }
            return Mth.clamp(steps, MIN_SUBSTEPS, MAX_SUBSTEPS);
        }

        private double configureAdaptiveTimestep(int substeps) {
            double newDt = TICK_SECONDS / substeps;
            if (Math.abs(newDt - lastSubstepSeconds) > 1.0E-12D) {
                double ratio = newDt / lastSubstepSeconds;
                for (int i = 1; i < POINTS; ++i) {
                    Vec3 displacement = points[i].subtract(previous[i]).scale(ratio);
                    previous[i] = points[i].subtract(displacement);
                }
            }
            lastSubstepSeconds = newDt;
            return newDt;
        }
        private void solveBlockCapsuleContacts(
                WhipBlockCollision.SegmentEnvironment[] broadphase,
                Vec3[] before, double tipScale, double tangentRetention,
                boolean updateVelocity) {
            for (int i = 0; i < SEGMENTS; ++i) {
                double radius = EmeraldWhipGeometry.SEGMENT_COLLIDER_RADIUS[i]
                        * (i == SEGMENTS - 1 ? Math.max(1.0D, tipScale) : 1.0D);
                WhipBlockCollision.SegmentContact contact = WhipBlockCollision.findContact(
                        broadphase[i], before[i], before[i + 1], points[i], points[i + 1],
                        radius, updateVelocity ? tangentRetention : 1.0D);
                if (contact == null || contact.correction().lengthSqr() < 1.0E-16D) {
                    continue;
                }
                double u = Mth.clamp(contact.sample(), 0.0D, 1.0D);
                double ga = 1.0D - u;
                double gb = u;
                double invA = inverseMass(i);
                double invB = inverseMass(i + 1);
                double denominator = ga * ga * invA + gb * gb * invB;
                if (denominator <= 1.0E-12D) continue;
                if (invA > 0.0D) {
                    points[i] = points[i].add(contact.correction().scale(ga * invA / denominator));
                }
                if (invB > 0.0D) {
                    points[i + 1] = points[i + 1].add(contact.correction().scale(gb * invB / denominator));
                }
                if (updateVelocity) {
                    dampInwardVelocity(i, contact.normal(), tangentRetention);
                    dampInwardVelocity(i + 1, contact.normal(), tangentRetention);
                }
            }
        }

        private void dampInwardVelocity(int index, Vec3 normal, double tangentRetention) {
            if (index <= 0) return;
            Vec3 velocity = points[index].subtract(previous[index]);
            double inward = velocity.dot(normal);
            if (inward < 0.0D) {
                velocity = velocity.subtract(normal.scale(inward));
            }
            velocity = velocity.scale(tangentRetention);
            previous[index] = points[index].subtract(velocity);
        }

        private void depenetrateFromBlocks(ClientLevel level) {
            for (int i = 1; i < POINTS; ++i) {
                for (int pass = 0; pass < DEPENETRATION_PASSES; ++pass) {
                    Vec3 push = nearestDepenetration(level, points[i], COLLISION_RADIUS);
                    if (push == null || push.lengthSqr() < 1.0E-14D) {
                        break;
                    }
                    Vec3 oldPoint = points[i];
                    Vec3 corrected = oldPoint.add(push);
                    Vec3 velocity = oldPoint.subtract(previous[i]);
                    Vec3 normal = push.normalize();
                    double normalVelocity = velocity.dot(normal);
                    if (normalVelocity < 0.0D) {
                        velocity = velocity.subtract(normal.scale(normalVelocity));
                    }
                    points[i] = corrected;
                    previous[i] = corrected.subtract(velocity);
                }
            }
        }

        private Vec3 nearestDepenetration(ClientLevel level, Vec3 point, double radius) {
            int minX = Mth.floor(point.x - radius);
            int minY = Mth.floor(point.y - radius);
            int minZ = Mth.floor(point.z - radius);
            int maxX = Mth.floor(point.x + radius);
            int maxY = Mth.floor(point.y + radius);
            int maxZ = Mth.floor(point.z + radius);

            List<AABB> solids = new ArrayList<>();
            for (BlockPos pos : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
                BlockState state = level.getBlockState(pos);
                VoxelShape shape = state.getCollisionShape(level, pos);
                if (shape.isEmpty()) {
                    continue;
                }
                for (AABB local : shape.toAabbs()) {
                    solids.add(local.move(pos.getX(), pos.getY(), pos.getZ()).inflate(radius));
                }
            }

            List<Vec3> candidates = new ArrayList<>();
            for (AABB box : solids) {
                if (!box.contains(point)) {
                    continue;
                }
                candidates.add(new Vec3(box.minX - point.x - DEPENETRATION_EPSILON, 0.0D, 0.0D));
                candidates.add(new Vec3(box.maxX - point.x + DEPENETRATION_EPSILON, 0.0D, 0.0D));
                candidates.add(new Vec3(0.0D, box.minY - point.y - DEPENETRATION_EPSILON, 0.0D));
                candidates.add(new Vec3(0.0D, box.maxY - point.y + DEPENETRATION_EPSILON, 0.0D));
                candidates.add(new Vec3(0.0D, 0.0D, box.minZ - point.z - DEPENETRATION_EPSILON));
                candidates.add(new Vec3(0.0D, 0.0D, box.maxZ - point.z + DEPENETRATION_EPSILON));
            }
            if (candidates.isEmpty()) {
                return null;
            }

            Vec3 best = null;
            int bestOverlapCount = Integer.MAX_VALUE;
            double bestDistanceSqr = Double.MAX_VALUE;
            for (Vec3 candidate : candidates) {
                Vec3 moved = point.add(candidate);
                int overlaps = 0;
                for (AABB box : solids) {
                    if (box.contains(moved)) {
                        overlaps++;
                    }
                }
                double distanceSqr = candidate.lengthSqr();
                if (overlaps < bestOverlapCount
                        || (overlaps == bestOverlapCount && distanceSqr < bestDistanceSqr)) {
                    bestOverlapCount = overlaps;
                    bestDistanceSqr = distanceSqr;
                    best = candidate;
                }
            }
            return best;
        }

        private boolean precisionGhostMirrorVisualHalf() {
            return motor.driveModeThisTick() == AttackDrive.PRECISION
                    && motor.wasDrivenThisTick()
                    && precisionGhostMirrorHalf(motor.driveToRawProgress());
        }

        Vec3 attachmentAnchor(int segmentIndex, float segmentU, float partialTick) {
            int segment = Mth.clamp(segmentIndex, 0, SEGMENTS - 1);
            double u = Mth.clamp(segmentU, 0.0F, 1.0F);
            double t = Mth.clamp(partialTick, 0.0F, 1.0F);
            Vec3 interpolatedRoot = renderPrevious[0].lerp(points[0], t);
            long gameTime = holder != null ? holder.level().getGameTime() : capturedAnchorGameTime;
            Vec3 displayAnchor = renderDisplayAnchor(interpolatedRoot, gameTime);
            Vec3 rootCorrection = visualMorphActive()
                    ? displayAnchor.subtract(interpolatedRoot) : Vec3.ZERO;

            Vec3 start = storedVisualPoint(segment, (float)t, rootCorrection);
            Vec3 end = storedVisualPoint(segment + 1, (float)t, rootCorrection);
            return start.lerp(end, u);
        }

        void render(ClientLevel level, PoseStack stack, VertexConsumer consumer,
                    Vec3 camera, float partialTick) {
            if (coilProgress >= 0.999F) {
                return;
            }
            Vec3 interpolatedRoot = renderPrevious[0].lerp(points[0], partialTick);
            Vec3 displayAnchor = renderDisplayAnchor(interpolatedRoot, level.getGameTime());
            renderAtDisplayAnchor(level, stack, consumer, camera, partialTick, displayAnchor, false);
        }

        void renderSpectral(ClientLevel level, PoseStack stack, VertexConsumer consumer,
                            Vec3 camera, float partialTick) {
            if (coilProgress >= 0.999F) {
                return;
            }
            Vec3 interpolatedRoot = renderPrevious[0].lerp(points[0], partialTick);
            Vec3 displayAnchor = renderDisplayAnchor(interpolatedRoot, level.getGameTime());
            renderAtDisplayAnchor(level, stack, consumer, camera, partialTick, displayAnchor, true);
        }

        void renderFirstPersonWorld(ClientLevel level, PoseStack stack, VertexConsumer consumer,
                                    Camera viewCamera, float partialTick, long gameTime) {
            Vec3 interpolatedRoot = renderPrevious[0].lerp(points[0], partialTick);
            Vec3 fallback = renderDisplayAnchor(interpolatedRoot, gameTime);
            Vec3 displayAnchor = firstPersonDisplayAnchor(viewCamera, gameTime, partialTick, fallback);
            Vec3 rootCorrection = visualMorphActive()
                    ? displayAnchor.subtract(interpolatedRoot) : Vec3.ZERO;

            firstPersonBridgeTargetWorld = storedVisualPoint(0, partialTick, rootCorrection);
            firstPersonBridgeTargetGameTime = gameTime;

            renderSolvedShape(level, stack, consumer, viewCamera.getPosition(),
                    renderPrevious, points, partialTick, rootCorrection, tipVisualScale, 255, 0, false);
        }

        private void renderAtDisplayAnchor(ClientLevel level, PoseStack stack,
                                           VertexConsumer consumer, Vec3 camera,
                                           float partialTick, Vec3 displayAnchor,
                                           boolean spectral) {
            Vec3 interpolatedRoot = renderPrevious[0].lerp(points[0], partialTick);
            Vec3 rootCorrection = visualMorphActive()
                    ? displayAnchor.subtract(interpolatedRoot) : Vec3.ZERO;
            WhipPerformanceTuning.pushGeometryQuality(holder);
            try {
                renderSolvedShape(level, stack, consumer, camera, renderPrevious, points, partialTick,
                        rootCorrection, tipVisualScale, 255, 0, spectral);
            } finally {
                WhipPerformanceTuning.popGeometryQuality();
            }
        }

        private void renderSolvedShape(ClientLevel level, PoseStack stack, VertexConsumer consumer,
                                       Vec3 camera, Vec3[] fromPoints, Vec3[] toPoints,
                                       float partialTick, Vec3 rootCorrection,
                                       float renderedTipScale, int alpha, int firstSegment,
                                       boolean spectral) {
            for (int i = Math.max(0, firstSegment); i < SEGMENTS; ++i) {
                Vec3 start = storedVisualPoint(i, partialTick, rootCorrection);
                Vec3 end = storedVisualPoint(i + 1, partialTick, rootCorrection);
                Vec3 chord = end.subtract(start);
                double length = chord.length();
                if (length < 1.0E-6D) {
                    continue;
                }

                Vector3f direction = chord.scale(1.0D / length).toVector3f();
                Quaternionf rotation = new Quaternionf().rotationTo(
                        0.0F, 0.0F, -1.0F,
                        direction.x, direction.y, direction.z);
                Vec3 midpoint = start.lerp(end, 0.5D);
                int light = LevelRenderer.getLightColor(level, BlockPos.containing(midpoint));
                double authoredLength = EmeraldWhipGeometry.REST_LENGTHS[i];
                stack.pushPose();
                stack.translate(start.x - camera.x, start.y - camera.y, start.z - camera.z);
                stack.mulPose(rotation);

                stack.mulPose(Axis.ZP.rotationDegrees(90.0F));
                if (i == SEGMENTS - 1 && renderedTipScale > 1.001F) {
                    stack.scale(renderedTipScale, renderedTipScale, renderedTipScale);
                }
                if (spectral) {
                    EmeraldWhipGeometry.INSTANCE.renderDynamicSegmentScaledAboutCenter(
                            i, stack, consumer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                            SPECTRAL_OVERLAY_SCALE,
                            SPECTRAL_OVERLAY_RED, SPECTRAL_OVERLAY_GREEN,
                            SPECTRAL_OVERLAY_BLUE, Math.min(alpha, SPECTRAL_OVERLAY_ALPHA));
                } else {
                    EmeraldWhipGeometry.INSTANCE.renderDynamicSegment(
                            i, stack, consumer, light, OverlayTexture.NO_OVERLAY,
                            255, 255, 255, alpha);
                }
                stack.popPose();
            }
        }
        void renderBladeConnectors(PoseStack stack, VertexConsumer consumer, Vec3 camera,
                                   float partialTick) {
            if (coilProgress >= 0.999F) {
                return;
            }
            PoseStack.Pose pose = stack.last();
            Vec3 interpolatedRoot = renderPrevious[0].lerp(points[0], partialTick);
            long gameTime = holder != null ? holder.level().getGameTime() : capturedAnchorGameTime;
            Vec3 displayAnchor = renderDisplayAnchor(interpolatedRoot, gameTime);
            Vec3 rootCorrection = visualMorphActive()
                    ? displayAnchor.subtract(interpolatedRoot) : Vec3.ZERO;
            Vec3 firstRoot = storedVisualPoint(0, partialTick, rootCorrection);
            if (coilProgress < 0.999F && firstRoot.distanceToSqr(displayAnchor) > 1.0E-6D) {
                Vec3 next = storedVisualPoint(1, partialTick, rootCorrection);
                renderPhysicalConnectorWorld(pose, consumer, camera,
                        displayAnchor, firstRoot, displayAnchor, next, ROOT_FIBRE_MAX_LENGTH);
            }
            for (int i = 0; i < SEGMENTS - 1; ++i) {
                Vec3 start = storedVisualPoint(i, partialTick, rootCorrection);
                Vec3 end = storedVisualPoint(i + 1, partialTick, rootCorrection);
                Vec3 next = storedVisualPoint(i + 2, partialTick, rootCorrection);
                Vec3 chord = end.subtract(start);
                double length = chord.length();
                if (length < 1.0E-6D) {
                    continue;
                }
                double authoredLength = Math.min(length, EmeraldWhipGeometry.REST_LENGTHS[i]);
                Vec3 bladeEnd = start.add(chord.scale(authoredLength / length));
                double storedWeight = deployingFromStored && deployVisualProgress < 0.999F
                        ? 1.0D - deploymentActivationForPoint(i + 1)
                        : storedVisualActivationForPoint(i + 1);
                double visibleScale = Mth.lerp(storedWeight,
                        PHYSICAL_REST_SCALE, STORED_REST_SCALE);
                double cableLength = Math.max(0.0D,
                        EmeraldWhipGeometry.REST_LENGTHS[i] * visibleScale
                                - EmeraldWhipGeometry.REST_LENGTHS[i]);
                renderPhysicalConnectorWorld(pose, consumer, camera,
                        bladeEnd, end, start, next, cableLength);
            }
        }

        void renderTipTrail(PoseStack stack, VertexConsumer consumer, Vec3 camera,
                            float partialTick, long gameTime) {
            if (coilProgress >= 0.999F || tipTrail.size() < 2) {
                return;
            }
            List<TipTrailSample> samples = new ArrayList<>(tipTrail);
            int count = samples.size();
            PoseStack.Pose pose = stack.last();
            for (int i = 0; i < count - 1; ++i) {
                TipTrailSample a = samples.get(i);
                TipTrailSample b = samples.get(i + 1);
                Vec3 start = a.position();
                Vec3 end = b.position();
                Vec3 tangent = end.subtract(start);
                double length = tangent.length();
                if (length < 1.0E-5D) {
                    continue;
                }
                Vec3 midpoint = start.lerp(end, 0.5D);
                Vec3 toCamera = camera.subtract(midpoint);
                Vec3 side = tangent.cross(toCamera);
                if (side.lengthSqr() < 1.0E-10D) {
                    side = tangent.cross(new Vec3(0.0D, 1.0D, 0.0D));
                }
                if (side.lengthSqr() < 1.0E-10D) {
                    continue;
                }
                side = side.normalize();

                float ageFadeA = trailFade(a, i, count, gameTime, partialTick);
                float ageFadeB = trailFade(b, i + 1, count, gameTime, partialTick);
                if (ageFadeA <= 0.01F && ageFadeB <= 0.01F) {
                    continue;
                }
                emitTrailQuad(pose, consumer, camera, start, end, side,
                        TIP_TRAIL_OUTER_HALF_WIDTH, ageFadeA, ageFadeB, false);
                emitTrailQuad(pose, consumer, camera, start, end, side,
                        TIP_TRAIL_CORE_HALF_WIDTH, ageFadeA, ageFadeB, true);
            }
        }

        private float trailFade(TipTrailSample sample, int index, int count,
                                long gameTime, float partialTick) {
            float age = (float)(gameTime - sample.gameTime()) + Mth.clamp(partialTick, 0.0F, 1.0F);
            float timeFade = 1.0F - Mth.clamp(age / TIP_TRAIL_LIFETIME_TICKS, 0.0F, 1.0F);
            float indexFade = count <= 1 ? 1.0F : index / (float)(count - 1);
            return Mth.clamp(timeFade * (0.22F + 0.78F * indexFade), 0.0F, 1.0F);
        }

        private void emitTrailQuad(PoseStack.Pose pose, VertexConsumer consumer, Vec3 camera,
                                   Vec3 start, Vec3 end, Vec3 side, float halfWidth,
                                   float fadeStart, float fadeEnd, boolean core) {
            Vec3 s0 = start.add(side.scale(halfWidth));
            Vec3 s1 = start.add(side.scale(-halfWidth));
            Vec3 e1 = end.add(side.scale(-halfWidth));
            Vec3 e0 = end.add(side.scale(halfWidth));

            int r = core ? 190 : 48;
            int g = core ? 255 : 205;
            int b = core ? 214 : 104;
            int a0 = (int)(Mth.clamp(fadeStart, 0.0F, 1.0F) * (core ? 185 : 105));
            int a1 = (int)(Mth.clamp(fadeEnd, 0.0F, 1.0F) * (core ? 205 : 128));
            trailVertex(pose, consumer, camera, s0, r, g, b, a0);
            trailVertex(pose, consumer, camera, s1, r, g, b, a0);
            trailVertex(pose, consumer, camera, e1, r, g, b, a1);
            trailVertex(pose, consumer, camera, e0, r, g, b, a1);
        }

        private void trailVertex(PoseStack.Pose pose, VertexConsumer consumer, Vec3 camera,
                                 Vec3 point, int red, int green, int blue, int alpha) {
            consumer.addVertex(pose.pose(),
                            (float)(point.x - camera.x),
                            (float)(point.y - camera.y),
                            (float)(point.z - camera.z))
                    .setColor(red, green, blue, Mth.clamp(alpha, 0, 255));
        }
    }

    private static ArmPose chargedChargeArmPose(double chargeTicks, float side) {
        float charge = (float)rightChargeProgress(chargeTicks);
        float lift = (float)smoothstep(Math.min(1.0D, charge / 0.12D));
        float theta = (float)(CHARGE_SPIN_SIGN * rightSpinTurns(chargeTicks) * Mth.TWO_PI);

        ArmPose leftGather = precisionArmPose(0.15F, side);
        float pitch = Mth.lerp(lift, ARM_REST_PITCH, leftGather.pitch())
                + Mth.sin(theta) * 0.018F;
        float yaw = Mth.lerp(lift, 0.0F, leftGather.yaw())
                + side * Mth.sin(theta) * 0.055F;
        float roll = Mth.lerp(lift, side * 0.08F, leftGather.roll())
                + side * Mth.cos(theta) * 0.045F;
        return new ArmPose(pitch, yaw, roll);
    }

    private static ArmPose chargedReleaseArmPose(float releaseProgress, float side) {

        float t = Mth.clamp(releaseProgress, 0.0F, 1.0F);
        float leftClickEquivalent = Mth.lerp(t, 0.15F, 0.50F);
        return precisionArmPose(leftClickEquivalent, side);
    }

    private static ArmPose precisionArmPose(float rawProgress, float side) {
        return precisionArmPose(rawProgress, side, 1.0F);
    }

    private static ArmPose precisionArmPose(float rawProgress, float side, float swingSign) {
        float raw = Mth.clamp(rawProgress, 0.0F, 1.0F);
        float sign = swingSign >= 0.0F ? 1.0F : -1.0F;
        float pitch;
        float yaw;
        float roll;
        if (raw < 0.30F) {
            float t = (float)smoothstep(raw / 0.30F);
            pitch = Mth.lerp(t, ARM_REST_PITCH, 1.52F);
            yaw = Mth.lerp(t, 0.0F, -side * sign * 0.22F);
            roll = Mth.lerp(t, side * 0.08F, -side * sign * 0.16F);
        } else if (raw < 0.72F) {
            float t = (raw - 0.30F) / 0.42F;
            float accelerated = (float)Math.pow(Mth.clamp(t, 0.0F, 1.0F), 1.55D);
            pitch = Mth.lerp(accelerated, 1.52F, -0.58F);
            yaw = Mth.lerp(accelerated, -side * sign * 0.22F, side * sign * 0.24F);
            roll = Mth.lerp(accelerated, -side * sign * 0.16F, side * sign * 0.32F);
        } else {
            float t = (float)smoothstep((raw - 0.72F) / 0.28F);
            pitch = Mth.lerp(t, -0.58F, ARM_REST_PITCH);
            yaw = Mth.lerp(t, side * sign * 0.24F, 0.0F);
            roll = Mth.lerp(t, side * sign * 0.32F, side * 0.08F);
        }
        return new ArmPose(pitch, yaw, roll);
    }

    private static final class ArmMotor {
        private float oldPitch = ARM_REST_PITCH;
        private float oldYaw;
        private float oldRoll;
        private float pitch = ARM_REST_PITCH;
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
        private Vec3 attackDirection = new Vec3(0.0D, 0.0D, 1.0D);
        private float precisionSwingSign = 1.0F;
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

        private int precisionWindupTicks = PRECISION_WINDUP_TICKS;

        private int precisionStrokeTicks = PRECISION_STROKE_TICKS;

        private boolean requestAcceptedThisTick;
        private Vec3 previousPlayerVelocity = Vec3.ZERO;

        void tick(Player player, HumanoidArm arm, AttackDrive requestedMode,
                  double requestedSwingSign, boolean requestedMomentumCarry) {
            float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
            drivenThisTick = false;
            driveStartedThisTick = false;
            requestAcceptedThisTick = false;
            chargePhaseThisTick = false;
            releasePhaseThisTick = false;
            releaseStartedThisTick = false;
            driveModeThisTick = AttackDrive.NONE;
            chargeFromTicks = chargeToTicks = 0.0F;
            releaseFromProgress = releaseToProgress = 0.0F;
            driveFromRawProgress = driveToRawProgress = 0.0F;

            oldPitch = pitch;
            oldYaw = yaw;
            oldRoll = roll;

            boolean correctSwingArm = player.swinging
                    && armForHand(player, player.swingingArm) == arm;
            boolean swingTrigger = correctSwingArm
                    && (!wasSwinging || player.swingTime < previousSwingTime);
            boolean rightUse = player.isUsingItem()
                    && player.getUseItem().is(ModItems.EMERALD_WHIP.get())
                    && armForHand(player, player.getUsedItemHand()) == arm;
            boolean rightRequested = requestedMode == AttackDrive.CHARGED_SLAM;

            boolean precisionRequested = requestedMode == AttackDrive.PRECISION;

            if ((rightRequested || rightUse) && activeMode == AttackDrive.NONE) {
                startDrive(AttackDrive.CHARGED_SLAM, player, side, 1.0D, false);
                requestAcceptedThisTick = rightRequested;
            } else if ((precisionRequested || swingTrigger) && activeMode == AttackDrive.NONE) {
                startDrive(AttackDrive.PRECISION, player, side,
                        requestedSwingSign, requestedMomentumCarry);
                requestAcceptedThisTick = precisionRequested;
            }

            Vec3 playerVelocity = player.getDeltaMovement();
            Vec3 acceleration = playerVelocity.subtract(previousPlayerVelocity);
            previousPlayerVelocity = playerVelocity;
            float inertial = (float)Mth.clamp(acceleration.horizontalDistance() * 0.22D,
                    -0.12D, 0.12D);
            float bodyTurn = Mth.wrapDegrees(player.yBodyRot - player.yBodyRotO)
                    * Mth.DEG_TO_RAD;

            if (activeMode == AttackDrive.CHARGED_SLAM) {
                if (rightSlamTick < 0) {
                    if (rightUse || rightRequested) {
                        drivenThisTick = true;
                        driveModeThisTick = AttackDrive.CHARGED_SLAM;
                        chargePhaseThisTick = true;
                        attackDirection = physicalForward(player);
                        chargeFromTicks = rightChargeTicks;
                        chargeToTicks = rightChargeTicks + 1.0F;
                        ++rightChargeTicks;
                        ArmPose pose = chargedChargeArmPose(chargeToTicks, side);
                        pitch = pose.pitch(); yaw = pose.yaw(); roll = pose.roll();
                    } else if (rightChargeTicks >= RIGHT_CHARGE_TICKS) {

                        attackDirection = physicalForward(player);
                        rightSlamTick = 0;
                        releaseStartedThisTick = true;
                        drivenThisTick = true;
                        driveModeThisTick = AttackDrive.CHARGED_SLAM;
                        releasePhaseThisTick = true;
                        releaseFromProgress = 0.0F;
                        releaseToProgress = 1.0F / RIGHT_SLAM_TICKS;
                        ++rightSlamTick;
                        ArmPose pose = chargedReleaseArmPose(releaseToProgress, side);
                        pitch = pose.pitch(); yaw = pose.yaw(); roll = pose.roll();
                    } else {
                        cancelChargedSlam();
                    }
                } else if (rightSlamTick < RIGHT_SLAM_TICKS) {
                    drivenThisTick = true;
                    driveModeThisTick = AttackDrive.CHARGED_SLAM;
                    releasePhaseThisTick = true;
                    releaseFromProgress = rightSlamTick / (float)RIGHT_SLAM_TICKS;
                    releaseToProgress = (rightSlamTick + 1.0F) / RIGHT_SLAM_TICKS;
                    ++rightSlamTick;
                    ArmPose pose = chargedReleaseArmPose(releaseToProgress, side);
                    pitch = pose.pitch(); yaw = pose.yaw(); roll = pose.roll();
                } else {
                    finishDrive(side);
                }
            } else if (activeMode == AttackDrive.PRECISION && driveTick >= 0) {
                drivenThisTick = true;
                driveModeThisTick = AttackDrive.PRECISION;
                int totalPrecisionTicks = precisionWindupTicks + precisionStrokeTicks;
                if (driveTick < precisionWindupTicks) {
                    float from = driveTick / (float)precisionWindupTicks;
                    float to = (driveTick + 1.0F) / precisionWindupTicks;
                    driveFromRawProgress = PRECISION_RELEASE_RAW * Mth.clamp(from, 0.0F, 1.0F);
                    driveToRawProgress = PRECISION_RELEASE_RAW * Mth.clamp(to, 0.0F, 1.0F);
                } else {
                    int lashTick = driveTick - precisionWindupTicks;
                    float from = Mth.clamp(lashTick / (float)precisionStrokeTicks, 0.0F, 1.0F);
                    float to = Mth.clamp((lashTick + 1.0F) / precisionStrokeTicks, 0.0F, 1.0F);
                    driveFromRawProgress = Mth.lerp(from, PRECISION_RELEASE_RAW, 1.0F);
                    driveToRawProgress = Mth.lerp(to, PRECISION_RELEASE_RAW, 1.0F);
                }
                ArmPose stroke = precisionArmPose(driveToRawProgress, side, precisionSwingSign);
                pitch = stroke.pitch(); yaw = stroke.yaw(); roll = stroke.roll();
                if (++driveTick >= totalPrecisionTicks) {
                    finishDrive(side);
                }
            } else {
                float targetPitch = ARM_REST_PITCH;
                float targetYaw = 0.0F;
                float targetRoll = side * 0.08F;
                yawVelocity -= bodyTurn * 0.18F;
                rollVelocity += side * inertial;

                pitchVelocity = (pitchVelocity + (targetPitch - pitch) * ARM_SPRING) * ARM_DAMPING;
                yawVelocity = (yawVelocity + (targetYaw - yaw) * (ARM_SPRING * 0.92F)) * ARM_DAMPING;
                rollVelocity = (rollVelocity + (targetRoll - roll) * (ARM_SPRING * 0.88F)) * ARM_DAMPING;

                pitch += pitchVelocity;
                yaw += yawVelocity;
                roll += rollVelocity;
                pitch = clampWithBounce(pitch, ARM_REST_PITCH - 2.70F,
                        ARM_REST_PITCH + 1.48F, AxisComponent.PITCH);
                yaw = clampWithBounce(yaw, -1.35F, 1.35F, AxisComponent.YAW);
                roll = clampWithBounce(roll, -1.35F, 1.35F, AxisComponent.ROLL);
            }

            wasSwinging = correctSwingArm;
            previousSwingTime = player.swingTime;
        }

        private void startDrive(AttackDrive mode, Player player, float side,
                                double requestedSwingSign, boolean requestedMomentumCarry) {
            activeMode = mode;
            attackDirection = mode == AttackDrive.PRECISION
                    ? aimDirection(player) : physicalForward(player);
            precisionSwingSign = mode == AttackDrive.PRECISION && requestedSwingSign < 0.0D
                    ? -1.0F : 1.0F;
            precisionMomentumCarry = mode == AttackDrive.PRECISION && requestedMomentumCarry;
            pitch = ARM_REST_PITCH;
            yaw = 0.0F;
            roll = side * 0.08F;
            oldPitch = pitch; oldYaw = yaw; oldRoll = roll;
            pitchVelocity = yawVelocity = rollVelocity = 0.0F;
            if (mode == AttackDrive.CHARGED_SLAM) {
                rightChargeTicks = 0;
                rightSlamTick = -1;
                driveTick = -1;
            } else {

                int attackPeriod = EmeraldWhipItem.attackPeriodTicks(player);
                precisionWindupTicks = Mth.clamp(
                        Math.min(PRECISION_WINDUP_TICKS, Math.max(1, attackPeriod - 2)),
                        1, PRECISION_WINDUP_TICKS);
                int availableStrokeTicks = Math.max(1, attackPeriod - precisionWindupTicks - 1);
                precisionStrokeTicks = Mth.clamp(availableStrokeTicks, 1, PRECISION_STROKE_TICKS);
                driveTick = 0;
                rightChargeTicks = 0;
                rightSlamTick = -1;
            }
            driveStartedThisTick = true;
        }

        private void finishDrive(float side) {
            pitch = ARM_REST_PITCH;
            yaw = 0.0F;
            roll = side * 0.08F;
            pitchVelocity = yawVelocity = rollVelocity = 0.0F;
            driveTick = -1;
            rightChargeTicks = 0;
            rightSlamTick = -1;
            activeMode = AttackDrive.NONE;
            precisionMomentumCarry = false;
        }

        void cancelChargedSlam() {
            if (activeMode != AttackDrive.CHARGED_SLAM || rightSlamTick >= 0) {
                return;
            }
            driveTick = -1;
            rightChargeTicks = 0;
            rightSlamTick = -1;
            activeMode = AttackDrive.NONE;
            precisionMomentumCarry = false;
            pitchVelocity = yawVelocity = rollVelocity = 0.0F;
        }

        boolean wasDrivenThisTick() { return drivenThisTick; }
        boolean driveStartedThisTick() { return driveStartedThisTick; }
        boolean requestAcceptedThisTick() { return requestAcceptedThisTick; }
        boolean chargePhaseThisTick() { return chargePhaseThisTick; }
        boolean releasePhaseThisTick() { return releasePhaseThisTick; }
        boolean releaseStartedThisTick() { return releaseStartedThisTick; }
        AttackDrive driveModeThisTick() { return driveModeThisTick; }
        Vec3 attackDirection() { return attackDirection; }
        void overridePrecisionDirection(Vec3 direction) {
            if (activeMode == AttackDrive.PRECISION && finite(direction)
                    && direction.lengthSqr() > 1.0E-10D) {
                attackDirection = direction.normalize();
            }
        }

        double precisionSwingSign() { return precisionSwingSign; }
        boolean precisionMomentumCarry() { return precisionMomentumCarry; }
        float chargeFromTicks() { return chargeFromTicks; }
        float chargeToTicks() { return chargeToTicks; }
        float releaseFromProgress() { return releaseFromProgress; }
        float releaseToProgress() { return releaseToProgress; }
        float driveFromRawProgress() { return driveFromRawProgress; }
        float driveToRawProgress() { return driveToRawProgress; }

        private float clampWithBounce(float value, float minimum, float maximum,
                                      AxisComponent component) {
            if (value < minimum) { bounce(component); return minimum; }
            if (value > maximum) { bounce(component); return maximum; }
            return value;
        }

        private void bounce(AxisComponent component) {
            switch (component) {
                case PITCH -> pitchVelocity *= -0.24F;
                case YAW -> yawVelocity *= -0.24F;
                case ROLL -> rollVelocity *= -0.24F;
            }
        }

        ArmPose sample(float partialTick) {
            float partial = Mth.clamp(partialTick, 0.0F, 1.0F);
            return new ArmPose(
                    Mth.lerp(partial, oldPitch, pitch),
                    Mth.lerp(partial, oldYaw, yaw),
                    Mth.lerp(partial, oldRoll, roll));
        }

        private enum AxisComponent { PITCH, YAW, ROLL }
    }
}
