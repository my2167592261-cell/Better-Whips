package com.betterwhips.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import com.betterwhips.BetterWhipsMod;
import com.betterwhips.item.DestructionWhipItem;
import com.betterwhips.registry.ModItems;
import com.betterwhips.network.DestructionWhipNetwork;
import com.betterwhips.physics.DestructionWhipDimensions;
import com.betterwhips.physics.DestructionWhipAttackPath;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
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
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class DestructionWhipPhysics {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            BetterWhipsMod.MOD_ID, "textures/item/destruction_whip.png");
    private static final ResourceLocation PULSE_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            BetterWhipsMod.MOD_ID, "textures/misc/vfx_white.png");
    private static final RenderType RENDER_TYPE = RenderType.entityTranslucent(TEXTURE);
    private static final RenderType PULSE_RENDER_TYPE =
            NeoForgeRenderTypes.getUnlitTranslucent(PULSE_TEXTURE);

    private static final RenderType SPECTRAL_RENDER_TYPE =
            NeoForgeRenderTypes.getUnlitTranslucent(TEXTURE);

    private static final RenderType TIP_TRAIL_RENDER_TYPE = RenderType.create(
            "better_whips_destruction_whip_tip_trail",
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

    private static final int SEGMENTS = DestructionWhipGeometry.SEGMENT_COUNT;
    private static final int POINTS = SEGMENTS + 1;

    private static final int MIN_SUBSTEPS = 8;
    private static final int MAX_SUBSTEPS = 30;
    private static final int SOLVER_ITERATIONS = 7;
    private static final int HIGH_FIDELITY_TICKS = 28;
    private static final double TICK_SECONDS = 1.0D / 20.0D;

    private static final double PHYSICAL_REST_SCALE = DestructionWhipDimensions.DEPLOYED_REST_SCALE;

    private static final double STORED_REST_SCALE = DestructionWhipDimensions.STORED_REST_SCALE;
    private static final double MAX_SEGMENT_STRETCH = 1.0030D;

    private static final double LENGTH_COMPLIANCE = 6.0E-9D;

    private static final double BEND_COMPLIANCE = 5.2E-7D;

    private static final double TICK_VELOCITY_RETENTION = 0.9950D;
    private static final double GRAVITY = -6.5D;
    private static final double COLLISION_RADIUS = 0.050D;
    private static final int DEPENETRATION_PASSES = 8;
    private static final double DEPENETRATION_EPSILON = 1.0E-4D;
    private static final double SELF_COLLISION_DISTANCE = 0.088D;

    private static final double SURFACE_TANGENT_RETENTION_PER_TICK = 0.930D;
    private static final double HANDLE_ROOT_MAX_ANGLE = Math.toRadians(16.0D);

    private static final int HANDLE_BEND_SEGMENTS = 4;
    private static final double HANDLE_BEND_STIFFNESS_MULTIPLIER = 1.45D;
    private static final double MAX_CAPTURE_DISTANCE_SQR = 16.0D;

    private static final float ARM_REST_PITCH = -0.34F;
    private static final float ARM_SPRING = 0.17F;
    private static final float ARM_DAMPING = 0.76F;
    private static final int RIGHT_CHARGE_TICKS = DestructionWhipItem.RIGHT_CHARGE_TICKS;
    private static final int RIGHT_SLAM_TICKS = 14;
    private static final float RIGHT_SPIN_TURNS_AT_FULL_CHARGE = 4.0F;

    private static final double CHARGE_SPIN_SIGN = 1.0D;
    private static final double CHARGE_CENTRIFUGAL_SCALE = 0.16D;
    private static final double CHARGE_MAX_RADIAL_ACCEL = 185.0D;
    private static final double CHARGE_MAX_TANGENTIAL_ACCEL = 130.0D;
    private static final double RELEASE_FORCE_STIFFNESS = 238.0D;
    private static final double RELEASE_MAX_ACCEL = 360.0D;
    private static final float RIGHT_TIP_MAX_SCALE = 10.0F;

    private static final int PRECISION_WINDUP_TICKS = 5;
    private static final int PRECISION_STROKE_TICKS = 5;
    private static final float PRECISION_RELEASE_RAW = 0.26F;
    private static final float PRECISION_GUIDE_END = 1.00F;

    private static final double TARGET_SCREEN_HALF_EXTENT = 0.15D;

    private static final int COIL_IDLE_DELAY_TICKS = 20;

    private static final int COIL_DURATION_TICKS = 9;

    private static final int DEPLOY_DURATION_TICKS = 2;
    private static final float PRECISION_RETRACT_START_RAW_PROGRESS = 0.50F;
    private static final double COIL_POSITION_GAIN = 0.16D;
    private static final double COIL_MAX_PULL_PER_SUBSTEP = 0.11D;
    private static final double COIL_VELOCITY_DAMPING = 0.58D;

    private static final double COIL_SELF_COLLISION_SCALE = 0.46D;

    private static final long FIRST_PERSON_BRIDGE_MAX_AGE_TICKS = 1L;

    private static final int TIP_TRAIL_LIFETIME_TICKS = 5;
    private static final int TIP_TRAIL_MAX_SAMPLES = 32;
    private static final double TIP_TRAIL_MIN_SAMPLE_DISTANCE_SQR = 0.035D * 0.035D;
    private static final double TIP_TRAIL_MIN_SPEED = 2.5D;
    private static final float TIP_TRAIL_OUTER_HALF_WIDTH = 0.0725F;
    private static final float TIP_TRAIL_CORE_HALF_WIDTH = 0.026F;

    private static final float BLADE_LINK_OUTER_RADIUS = 0.012F;
    private static final float BLADE_LINK_CORE_RADIUS = 0.0050F;
    private static final int BLADE_LINK_CURVE_STEPS = 8;
    private static final int BLADE_LINK_RADIAL_SIDES = 8;
    private static final float SPECTRAL_OVERLAY_SCALE = 1.20F;
    private static final int SPECTRAL_OVERLAY_RED = 255;
    private static final int SPECTRAL_OVERLAY_GREEN = 92;
    private static final int SPECTRAL_OVERLAY_BLUE = 72;
    private static final int SPECTRAL_OVERLAY_ALPHA = 96;
    private static final int SPECTRAL_PULSE_ALPHA = 112;

    static final int REDSTONE_BASE_LEFT_RED = 255;
    static final int REDSTONE_BASE_LEFT_GREEN = 92;
    static final int REDSTONE_BASE_LEFT_BLUE = 24;
    static final int REDSTONE_BASE_RIGHT_RED = 208;
    static final int REDSTONE_BASE_RIGHT_GREEN = 22;
    static final int REDSTONE_BASE_RIGHT_BLUE = 14;
    static final int REDSTONE_BASE_ALPHA = 218;
    private static final int REDSTONE_ORANGE_RED = 255;
    private static final int REDSTONE_ORANGE_GREEN = 116;
    private static final int REDSTONE_ORANGE_BLUE = 12;
    private static final int REDSTONE_YELLOW_RED = 255;
    private static final int REDSTONE_YELLOW_GREEN = 238;
    private static final int REDSTONE_YELLOW_BLUE = 72;

    private static final float REDSTONE_YELLOW_HEAD_WIDTH = 0.075F;
    private static final float REDSTONE_ORANGE_HEAD_WIDTH = 0.13F;
    private static final float REDSTONE_ORANGE_TAIL_WIDTH = 0.34F;
    private static final int REDSTONE_YELLOW_MAX_ALPHA = 248;
    private static final int REDSTONE_ORANGE_MAX_ALPHA = 220;

    private static final Map<StateKey, WhipState> STATES = new HashMap<>();

    private static final Map<StateKey, AttackDrive> PENDING_ATTACKS = new HashMap<>();

    private static final int MAX_PENDING_PRECISION_INPUTS = 32;
    private static final Map<StateKey, ArrayDeque<ClientPrecisionRequest>> PENDING_PRECISION =
            new HashMap<>();
    private static final ThreadLocal<ArrayDeque<RenderContext>> RENDER_CONTEXT =
            ThreadLocal.withInitial(ArrayDeque::new);

    private DestructionWhipPhysics() {}

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
        if (!stack.is(ModItems.DESTRUCTION_WHIP.get())) {
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
        long motionSeed = player.getRandom().nextLong();
        Vec3 root = state.initialized
                ? state.points[0]
                : (state.capturedAnchor != null ? state.capturedAnchor
                : fallbackAnchor(player, player.getMainArm()));
        PrecisionTargetLock targetLock = acquirePrecisionTarget(player, root, clickDirection);
        WhipMomentumContinuity.Continuation continuation = state.precisionContinuation(
                clickDirection, player.level().getGameTime());
        queue.addLast(new ClientPrecisionRequest(clickDirection, handleAxis,
                continuation.active(), continuation.swingSign(), motionSeed,
                targetLock.targetEntityId(), targetLock.targetEyeOffset(), targetLock.endEyeOffset()));

        if (state.initialized) {
            DestructionWhipNetwork.sendPrecisionAttack(
                    state.points, state.previous, state.lastSubstepSeconds,
                    handleAxis, clickDirection, motionSeed,
                    targetLock.targetEntityId(), targetLock.targetEyeOffset(), targetLock.endEyeOffset());
        } else {
            DestructionWhipNetwork.sendPrecisionAttack(motionSeed,
                    targetLock.targetEntityId(), targetLock.targetEyeOffset(), targetLock.endEyeOffset());
        }

        player.resetAttackStrengthTicker();
    }

    private static PrecisionTargetLock acquirePrecisionTarget(Player player, Vec3 root, Vec3 viewDirection) {
        Vec3 view = finite(viewDirection) && viewDirection.lengthSqr() > 1.0E-10D
                ? viewDirection.normalize() : aimDirection(player);
        Vec3 eye = player.getEyePosition();
        Vec3 finalPoint = DestructionWhipAttackPath.crosshairEndpoint(
                eye, root, view, DestructionWhipDimensions.deployedLength());
        Vec3 endEyeOffset = finalPoint.subtract(eye);
        if (!(player.level() instanceof ClientLevel level)) {
            return new PrecisionTargetLock(-1, Vec3.ZERO, endEyeOffset);
        }

        Minecraft minecraft = Minecraft.getInstance();
        double verticalFov = Math.toRadians(Mth.clamp(
                minecraft.options.fov().get().doubleValue(), 1.0D, 179.0D));
        double tanVertical = Math.tan(verticalFov * 0.5D);
        double aspect = minecraft.getWindow().getHeight() <= 0
                ? 16.0D / 9.0D
                : minecraft.getWindow().getWidth() / (double)minecraft.getWindow().getHeight();
        double tanHorizontal = tanVertical * Math.max(0.25D, aspect);

        Vec3 worldUp = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 right = view.cross(worldUp);
        if (right.lengthSqr() < 1.0E-10D) {
            right = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            right = right.normalize();
        }
        Vec3 up = right.cross(view);
        if (up.lengthSqr() < 1.0E-10D) {
            up = worldUp;
        } else {
            up = up.normalize();
        }

        double reach = DestructionWhipDimensions.deployedLength();
        AABB search = player.getBoundingBox().inflate(reach + 1.0D);
        LivingEntity best = null;
        Vec3 bestPoint = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, search,
                entity -> entity != player && entity.isAlive() && entity.isPickable())) {
            if (clientFriendly(player, candidate) || !player.hasLineOfSight(candidate)) {
                continue;
            }
            Vec3 point = candidate.getBoundingBox().getCenter();
            if (root.distanceToSqr(point) > reach * reach) {
                continue;
            }
            Vec3 delta = point.subtract(eye);
            double depth = delta.dot(view);
            if (depth <= 0.05D) {
                continue;
            }

            double screenX = 0.5D * delta.dot(right)
                    / Math.max(1.0E-6D, depth * tanHorizontal);
            double screenY = 0.5D * delta.dot(up)
                    / Math.max(1.0E-6D, depth * tanVertical);
            if (Math.abs(screenX) > TARGET_SCREEN_HALF_EXTENT
                    || Math.abs(screenY) > TARGET_SCREEN_HALF_EXTENT) {
                continue;
            }
            double distance = root.distanceToSqr(point);
            if (distance < bestDistance) {
                best = candidate;
                bestPoint = point;
                bestDistance = distance;
            }
        }
        return best == null
                ? new PrecisionTargetLock(-1, Vec3.ZERO, endEyeOffset)
                : new PrecisionTargetLock(best.getId(), bestPoint.subtract(eye), endEyeOffset);
    }

    private static boolean clientFriendly(Player owner, LivingEntity target) {
        if (target == owner || owner.isAlliedTo(target) || target.isAlliedTo(owner)) {
            return true;
        }
        if (target instanceof Player other && Objects.equals(owner.getTeam(), other.getTeam())) {
            return true;
        }
        return target instanceof OwnableEntity ownable
                && owner.getUUID().equals(ownable.getOwnerUUID());
    }

    private static void tryHeldPrecisionAttack(Minecraft minecraft) {
        Player player = minecraft.player;
        if (player == null || minecraft.screen != null || !minecraft.options.keyAttack.isDown()
                || player.isUsingItem() || !player.isAlive() || player.isSpectator()
                || !player.getMainHandItem().is(ModItems.DESTRUCTION_WHIP.get())
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
        Entity cameraEntity = camera.getEntity();
        if (cameraEntity != holder) {
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

                state.renderTerminal(level, poseStack, consumer, camera, partialTick, false);
                continue;
            }
            state.render(level, poseStack, consumer, camera, partialTick);
        }
        buffers.endBatch(RENDER_TYPE);

        int pulseFrame = currentPulseFrame(null);
        VertexConsumer pulseConsumer = buffers.getBuffer(PULSE_RENDER_TYPE);
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
                state.renderTerminalPulse(level, poseStack, pulseConsumer, camera,
                        partialTick, pulseFrame, false);
                continue;
            }
            state.renderPulse(level, poseStack, pulseConsumer, camera, partialTick,
                    pulseFrame, false);
        }
        buffers.endBatch(PULSE_RENDER_TYPE);

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
                    && WhipPerformanceTuning.allowConnectors("destruction", state.holder, camera)) {
                state.renderBladeConnectors(poseStack, trailConsumer, camera, partialTick);
            }
            if (!localFirstPerson && shouldDisplayTipTrail(state.holder)
                    && WhipPerformanceTuning.allowTrail("destruction", state.holder, camera)) {
                state.renderTipTrail(poseStack, trailConsumer, camera, partialTick, gameTime);
            }
        }
        buffers.endBatch(TIP_TRAIL_RENDER_TYPE);
    }

    public static void onRenderArm(RenderArmEvent event) {

    }

    public static void applyFirstPersonItemTransform(PoseStack stack, Player player,
                                                     HumanoidArm arm, float partialTick,
                                                     float equipProgress) {
        float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        WhipFirstPersonFov.applyToViewModel(stack);
        stack.translate(side * 0.56F, -0.52F - equipProgress * 0.60F, -0.72F);
        stack.translate(side * 0.025F, 0.055F, -0.035F);
        stack.mulPose(Axis.YP.rotationDegrees(side * 42.0F));
        stack.mulPose(Axis.XP.rotationDegrees(-22.0F));
        stack.mulPose(Axis.ZP.rotationDegrees(-side * 8.0F));
    }

    public static ArmPose getArmPose(Player player, HumanoidArm arm, float partialTick) {
        float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        return new ArmPose(ARM_REST_PITCH, 0.0F, side * 0.08F);
    }

    public static ArmPose getThirdPersonArmPose(Player player, HumanoidArm arm,
                                                float partialTick) {
        return getArmPose(player, arm, partialTick);
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

    static float currentTangentialHorizontalDegrees(RenderContext context) {

        return 0.0F;
    }

    static float currentTangentialVerticalDegrees(RenderContext context) {
        return 0.0F;
    }

    static RenderContext currentRenderContext() {
        ArrayDeque<RenderContext> stack = RENDER_CONTEXT.get();
        return stack.isEmpty() ? null : stack.peek();
    }

    static Vec3 currentSegmentMidpoint(int ownerEntityId, int segmentIndex, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return null;
        }
        Entity rawOwner = level.getEntity(ownerEntityId);
        if (!(rawOwner instanceof Player player)) {
            return null;
        }
        int segment = Mth.clamp(segmentIndex, 0, SEGMENTS - 1);
        WhipState state = STATES.get(new StateKey(player.getUUID(), player.getMainArm()));
        if (state == null || !state.initialized) {
            HumanoidArm alternate = player.getMainArm() == HumanoidArm.RIGHT
                    ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
            state = STATES.get(new StateKey(player.getUUID(), alternate));
        }
        if (state == null || !state.initialized) {
            return null;
        }
        return state.attachmentAnchor(segment, 0.5F, partialTick);
    }

    static RenderType pulseRenderType() {
        return PULSE_RENDER_TYPE;
    }

    static int currentPulseFrame(RenderContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        float partialTick = minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        long gameTime = 0L;
        if (context != null && context.holder != null) {
            gameTime = context.holder.level().getGameTime();
        } else if (minecraft.level != null) {
            gameTime = minecraft.level.getGameTime();
        }
        return pulseFrame(gameTime, partialTick);
    }

    static int currentHandleOrangePulseAlpha(int pulseFrame) {
        return orangePulseAlphaForPosition(-0.03D, pulseFrame);
    }

    static int currentHandleYellowPulseAlpha(int pulseFrame) {
        return yellowPulseAlphaForPosition(-0.03D, pulseFrame);
    }

    private static int pulseFrame(long gameTime, float partialTick) {
        return Math.floorMod(Mth.floor((gameTime + partialTick) * 2.0F),
                DestructionWhipGeometry.PULSE_FRAME_COUNT);
    }

    private static float pulsePhase01(int pulseFrame) {
        int steps = Math.max(1, DestructionWhipGeometry.PULSE_FRAME_COUNT - 1);
        return Mth.clamp(pulseFrame / (float) steps, 0.0F, 1.0F);
    }

    private static int orangePulseAlphaForSegment(int segmentIndex, int pulseFrame) {
        double position = (segmentIndex + 0.5D) / (double) SEGMENTS;
        return orangePulseAlphaForPosition(position, pulseFrame);
    }

    private static int yellowPulseAlphaForSegment(int segmentIndex, int pulseFrame) {
        double position = (segmentIndex + 0.5D) / (double) SEGMENTS;
        return yellowPulseAlphaForPosition(position, pulseFrame);
    }

    private static int orangePulseAlphaForPosition(double normalizedPosition, int pulseFrame) {
        float phase = pulsePhase01(pulseFrame);
        float position = Mth.clamp((float) normalizedPosition, 0.0F, 1.0F);
        float head = Math.max(0.0F,
                1.0F - Math.abs(position - phase) / REDSTONE_ORANGE_HEAD_WIDTH);
        float tail = 0.0F;
        if (position <= phase) {
            tail = Math.max(0.0F,
                    1.0F - (phase - position) / REDSTONE_ORANGE_TAIL_WIDTH) * 0.82F;
        }
        float intensity = Mth.clamp(Math.max(head * 0.88F, tail), 0.0F, 1.0F);
        return Mth.clamp((int)Math.round(intensity * REDSTONE_ORANGE_MAX_ALPHA), 0, 255);
    }

    private static int yellowPulseAlphaForPosition(double normalizedPosition, int pulseFrame) {
        float phase = pulsePhase01(pulseFrame);
        float position = Mth.clamp((float) normalizedPosition, 0.0F, 1.0F);
        float intensity = Math.max(0.0F,
                1.0F - Math.abs(position - phase) / REDSTONE_YELLOW_HEAD_WIDTH);
        intensity *= intensity;
        return Mth.clamp((int)Math.round(intensity * REDSTONE_YELLOW_MAX_ALPHA), 0, 255);
    }

    static void captureRenderedSocket(PoseStack poseStack, RenderContext context) {
        if (!(context.holder instanceof Player player) || !holdsWhip(player, context.arm)) {
            return;
        }

        Vector3f transformedRoot = new Vector3f(0.0F,
                DestructionWhipGeometry.CHAIN_PIVOT_Y, 0.0F);
        Vector3f transformedAxisPoint = new Vector3f(0.0F,
                DestructionWhipGeometry.CHAIN_PIVOT_Y + 0.25F, 0.0F);
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

    static void renderFirstPersonHandPulseLash(PoseStack poseStack, RenderContext context,
                                               VertexConsumer consumer, int packedOverlay,
                                               int pulseFrame) {
        renderFirstPersonHandPulseLashPass(
                poseStack, context, consumer, LightTexture.FULL_BRIGHT,
                packedOverlay, pulseFrame, false);
    }

    static void renderFirstPersonHandPulseSpectralLash(PoseStack poseStack, RenderContext context,
                                                       VertexConsumer consumer, int packedOverlay,
                                                       int pulseFrame) {
        renderFirstPersonHandPulseLashPass(
                poseStack, context, consumer, LightTexture.FULL_BRIGHT,
                packedOverlay, pulseFrame, true);
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
        if (state == null || !state.initialized
                || !(player.level() instanceof ClientLevel level)) {
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
                DestructionWhipGeometry.INSTANCE.renderAuthoredLashScaledPerPart(
                        poseStack, consumer, LightTexture.FULL_BRIGHT, packedOverlay,
                        SPECTRAL_OVERLAY_SCALE, SPECTRAL_OVERLAY_RED, SPECTRAL_OVERLAY_GREEN,
                        SPECTRAL_OVERLAY_BLUE, SPECTRAL_OVERLAY_ALPHA);
            } else {
                DestructionWhipGeometry.INSTANCE.renderAuthoredLash(
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
        Vec3 displayAnchor = state.renderDisplayAnchor(interpolatedRoot, player.level().getGameTime());
        Vec3 rootCorrection = displayAnchor.subtract(interpolatedRoot);

        for (int i = 0; i < SEGMENTS - 1; ++i) {
            double startRootWeight = 1.0D - i / (double)(POINTS - 1);
            double endRootWeight = 1.0D - (i + 1.0D) / (POINTS - 1);
            Vec3 worldStart = state.renderPrevious[i].lerp(state.points[i], partialTick)
                    .add(rootCorrection.scale(startRootWeight));
            Vec3 worldEnd = state.renderPrevious[i + 1].lerp(state.points[i + 1], partialTick)
                    .add(rootCorrection.scale(endRootWeight));

            if (i == SEGMENTS - 1 && state.precisionReleasedPathLength >= 0.0D
                    && finite(state.precisionTipFacingDirection)
                    && state.precisionTipFacingDirection.lengthSqr() > 1.0E-10D) {
                Vec3 facing = state.precisionTipFacingDirection.normalize();
                worldStart = worldEnd.subtract(facing.scale(DestructionWhipGeometry.REST_LENGTHS[i]));
            }

            Vec3 localStart = i == 0
                    ? new Vec3(0.0D, DestructionWhipGeometry.CHAIN_PIVOT_Y, 0.0D)
                    : firstPersonWorldPointToLocal(worldStart, camera, inverseHand);
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
                DestructionWhipGeometry.INSTANCE.renderDynamicSegmentScaledAboutCenter(
                        i, poseStack, consumer, light, packedOverlay, SPECTRAL_OVERLAY_SCALE,
                        SPECTRAL_OVERLAY_RED, SPECTRAL_OVERLAY_GREEN,
                        SPECTRAL_OVERLAY_BLUE, SPECTRAL_OVERLAY_ALPHA);
            } else {
                DestructionWhipGeometry.INSTANCE.renderDynamicSegment(
                        i, poseStack, consumer, light, packedOverlay, 255, 255, 255, 255);
            }
            poseStack.popPose();
        }
    }

    private static void renderFirstPersonHandPulseLashPass(PoseStack poseStack,
                                                            RenderContext context,
                                                            VertexConsumer consumer,
                                                            int packedLight,
                                                            int packedOverlay,
                                                            int pulseFrame,
                                                            boolean spectralScale) {
        if (context == null || !context.firstPerson
                || !(context.holder instanceof Player player)
                || !holdsWhip(player, context.arm)) {
            return;
        }

        StateKey key = new StateKey(player.getUUID(), context.arm);
        WhipState state = STATES.get(key);
        if (state == null || !state.initialized
                || !(player.level() instanceof ClientLevel level)) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Camera camera = minecraft.gameRenderer.getMainCamera();
        if (!isAttachedLocalFirstPerson(minecraft, player, camera)) {
            return;
        }
        float partialTick = minecraft.getTimer().getGameTimeDeltaPartialTick(false);

        if (state.coilProgress >= 0.999F) {
            if (spectralScale) {
                DestructionWhipGeometry.INSTANCE.renderAuthoredLashPulseScaledPerPart(
                        poseStack, consumer, packedLight, packedOverlay,
                        SPECTRAL_OVERLAY_SCALE, pulseFrame, SPECTRAL_PULSE_ALPHA);
            } else {
                DestructionWhipGeometry.INSTANCE.renderAuthoredLashEnergyGradient(
                        poseStack, consumer, LightTexture.FULL_BRIGHT, packedOverlay,
                        REDSTONE_BASE_LEFT_RED, REDSTONE_BASE_LEFT_GREEN, REDSTONE_BASE_LEFT_BLUE,
                        REDSTONE_BASE_RIGHT_RED, REDSTONE_BASE_RIGHT_GREEN, REDSTONE_BASE_RIGHT_BLUE,
                        REDSTONE_BASE_ALPHA);
                DestructionWhipGeometry.INSTANCE.renderAuthoredLashEnergyBySegment(
                        poseStack, consumer, LightTexture.FULL_BRIGHT, packedOverlay,
                        REDSTONE_ORANGE_RED, REDSTONE_ORANGE_GREEN, REDSTONE_ORANGE_BLUE,
                        index -> orangePulseAlphaForSegment(index, pulseFrame));
                DestructionWhipGeometry.INSTANCE.renderAuthoredLashEnergyBySegment(
                        poseStack, consumer, LightTexture.FULL_BRIGHT, packedOverlay,
                        REDSTONE_YELLOW_RED, REDSTONE_YELLOW_GREEN, REDSTONE_YELLOW_BLUE,
                        index -> yellowPulseAlphaForSegment(index, pulseFrame));
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
        Vec3 displayAnchor = state.renderDisplayAnchor(interpolatedRoot, player.level().getGameTime());
        Vec3 rootCorrection = displayAnchor.subtract(interpolatedRoot);

        for (int i = 0; i < SEGMENTS - 1; ++i) {
            double startRootWeight = 1.0D - i / (double)(POINTS - 1);
            double endRootWeight = 1.0D - (i + 1.0D) / (POINTS - 1);
            Vec3 worldStart = state.renderPrevious[i].lerp(state.points[i], partialTick)
                    .add(rootCorrection.scale(startRootWeight));
            Vec3 worldEnd = state.renderPrevious[i + 1].lerp(state.points[i + 1], partialTick)
                    .add(rootCorrection.scale(endRootWeight));

            Vec3 localStart = i == 0
                    ? new Vec3(0.0D, DestructionWhipGeometry.CHAIN_PIVOT_Y, 0.0D)
                    : firstPersonWorldPointToLocal(worldStart, camera, inverseHand);
            Vec3 localEnd = firstPersonWorldPointToLocal(worldEnd, camera, inverseHand);
            Vec3 chord = localEnd.subtract(localStart);
            double localLength = chord.length();
            if (!Double.isFinite(localLength) || localLength < 1.0E-6D) {
                continue;
            }

            Vector3f direction = chord.scale(1.0D / localLength).toVector3f();
            Quaternionf rotation = new Quaternionf().rotationTo(
                    0.0F, 0.0F, -1.0F, direction.x, direction.y, direction.z);
            poseStack.pushPose();
            poseStack.translate(localStart.x, localStart.y, localStart.z);
            poseStack.mulPose(rotation);
            int orangeAlpha = orangePulseAlphaForSegment(i, pulseFrame);
            int yellowAlpha = yellowPulseAlphaForSegment(i, pulseFrame);
            if (spectralScale) {
                int spectralPulse = Math.max(orangeAlpha, yellowAlpha);
                DestructionWhipGeometry.INSTANCE.renderDynamicSegmentPulseScaledAboutCenter(
                        i, poseStack, consumer, packedLight, packedOverlay,
                        SPECTRAL_OVERLAY_SCALE, pulseFrame,
                        Math.min(spectralPulse, SPECTRAL_PULSE_ALPHA));
            } else {
                DestructionWhipGeometry.INSTANCE.renderDynamicSegmentEnergyGradient(
                        i, poseStack, consumer, LightTexture.FULL_BRIGHT, packedOverlay,
                        REDSTONE_BASE_LEFT_RED, REDSTONE_BASE_LEFT_GREEN, REDSTONE_BASE_LEFT_BLUE,
                        REDSTONE_BASE_RIGHT_RED, REDSTONE_BASE_RIGHT_GREEN, REDSTONE_BASE_RIGHT_BLUE,
                        REDSTONE_BASE_ALPHA);
                if (orangeAlpha > 0) {
                    DestructionWhipGeometry.INSTANCE.renderDynamicSegmentEnergy(
                            i, poseStack, consumer, LightTexture.FULL_BRIGHT, packedOverlay,
                            REDSTONE_ORANGE_RED, REDSTONE_ORANGE_GREEN, REDSTONE_ORANGE_BLUE,
                            orangeAlpha);
                }
                if (yellowAlpha > 0) {
                    DestructionWhipGeometry.INSTANCE.renderDynamicSegmentEnergy(
                            i, poseStack, consumer, LightTexture.FULL_BRIGHT, packedOverlay,
                            REDSTONE_YELLOW_RED, REDSTONE_YELLOW_GREEN, REDSTONE_YELLOW_BLUE,
                            yellowAlpha);
                }
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
        Vec3 displayAnchor = state.renderDisplayAnchor(interpolatedRoot, player.level().getGameTime());
        Vec3 rootCorrection = displayAnchor.subtract(interpolatedRoot);
        PoseStack.Pose pose = poseStack.last();
        for (int i = 0; i < SEGMENTS - 1; ++i) {
            double startRootWeight = 1.0D - i / (double)(POINTS - 1);
            double endRootWeight = 1.0D - (i + 1.0D) / (POINTS - 1);
            double nextRootWeight = 1.0D - (i + 2.0D) / (POINTS - 1);
            Vec3 worldStart = state.renderPrevious[i].lerp(state.points[i], partialTick)
                    .add(rootCorrection.scale(startRootWeight));
            Vec3 worldEnd = state.renderPrevious[i + 1].lerp(state.points[i + 1], partialTick)
                    .add(rootCorrection.scale(endRootWeight));
            Vec3 worldNext = state.renderPrevious[i + 2].lerp(state.points[i + 2], partialTick)
                    .add(rootCorrection.scale(nextRootWeight));
            Vec3 chord = worldEnd.subtract(worldStart);
            double length = chord.length();
            if (length < 1.0E-6D) {
                continue;
            }
            double authoredLength = Math.min(length, DestructionWhipGeometry.REST_LENGTHS[i]);
            Vec3 bladeEnd = worldStart.add(chord.scale(authoredLength / length));
            Vec3 localStart = firstPersonWorldPointToLocal(bladeEnd, camera, inverseHand);
            Vec3 localJoint = firstPersonWorldPointToLocal(worldEnd, camera, inverseHand);
            Vec3 localPrev = firstPersonWorldPointToLocal(worldStart, camera, inverseHand);
            Vec3 localNext = firstPersonWorldPointToLocal(worldNext, camera, inverseHand);
            renderPhysicalConnectorLocal(pose, consumer, localStart, localJoint, localPrev, localNext);
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
        int r = 255;
        int g = core ? 198 : 54;
        int b = core ? 168 : 36;
        int a0 = (int)(Mth.clamp(fadeStart, 0.0F, 1.0F) * (core ? 205 : 118));
        int a1 = (int)(Mth.clamp(fadeEnd, 0.0F, 1.0F) * (core ? 228 : 146));
        connectorVertex(pose, consumer, s0, r, g, b, a0);
        connectorVertex(pose, consumer, s1, r, g, b, a0);
        connectorVertex(pose, consumer, e1, r, g, b, a1);
        connectorVertex(pose, consumer, e0, r, g, b, a1);
    }

    private static Vec3 physicalConnectorPoint(Vec3 start, Vec3 end,
                                               Vec3 previousPoint, Vec3 nextPoint, float t) {
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
        incoming = incoming.normalize().scale(gapLength);
        outgoing = outgoing.normalize().scale(gapLength);

        double tt = t * t;
        double ttt = tt * t;
        double h00 = 2.0D * ttt - 3.0D * tt + 1.0D;
        double h10 = ttt - 2.0D * tt + t;
        double h01 = -2.0D * ttt + 3.0D * tt;
        double h11 = ttt - tt;
        return start.scale(h00)
                .add(incoming.scale(h10))
                .add(end.scale(h01))
                .add(outgoing.scale(h11));
    }

    private static void renderPhysicalConnectorLocal(PoseStack.Pose pose, VertexConsumer consumer,
                                                      Vec3 start, Vec3 end,
                                                      Vec3 previousPoint, Vec3 nextPoint) {
        Vec3[] samples = samplePhysicalConnector(start, end, previousPoint, nextPoint);
        emitLocalFiberTube(pose, consumer, samples,
                BLADE_LINK_OUTER_RADIUS, 255, 54, 36, 150);
        emitLocalFiberTube(pose, consumer, samples,
                BLADE_LINK_CORE_RADIUS, 255, 198, 168, 244);
    }

    private static Vec3[] samplePhysicalConnector(Vec3 start, Vec3 end,
                                                  Vec3 previousPoint, Vec3 nextPoint) {
        Vec3[] samples = new Vec3[BLADE_LINK_CURVE_STEPS + 1];
        for (int step = 0; step <= BLADE_LINK_CURVE_STEPS; ++step) {
            float t = step / (float) BLADE_LINK_CURVE_STEPS;
            samples[step] = physicalConnectorPoint(start, end, previousPoint, nextPoint, t);
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
                                                      Vec3 previousPoint, Vec3 nextPoint) {
        Vec3[] samples = samplePhysicalConnector(start, end, previousPoint, nextPoint);
        emitWorldFiberTube(pose, consumer, camera, samples,
                BLADE_LINK_OUTER_RADIUS, 255, 54, 36, 150);
        emitWorldFiberTube(pose, consumer, camera, samples,
                BLADE_LINK_CORE_RADIUS, 255, 198, 168, 244);
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

        Vec3 start = new Vec3(0.0D, DestructionWhipGeometry.CHAIN_PIVOT_Y, 0.0D);
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

        DestructionWhipGeometry.INSTANCE.renderDynamicSegment(
                0, poseStack, consumer, packedLight, packedOverlay);
        poseStack.popPose();
    }

    private static boolean holdsWhip(Player player, HumanoidArm arm) {
        return stackForArm(player, arm).is(ModItems.DESTRUCTION_WHIP.get());
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

    private static double precisionAttackTime(double rawProgress) {
        return DestructionWhipAttackPath.attackTime(rawProgress, PRECISION_RELEASE_RAW);
    }

    private static double precisionOutwardProgress(double rawProgress) {
        return DestructionWhipAttackPath.outwardProgress(rawProgress, PRECISION_RELEASE_RAW);
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

    private static Vec3 precisionHandAnchor(Player player, HumanoidArm arm,
                                            Vec3 attackDirection, double rawProgress,
                                            double swingSign) {
        Vec3 aim = attackDirection.lengthSqr() < 1.0E-10D
                ? aimDirection(player) : attackDirection.normalize();
        return handBase(player, arm, aim);
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
        return particle <= 0 ? 0.0D : 1.0D / particleMass(particle);
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
                                          boolean momentumCarry, double swingSign,
                                          long motionSeed, int targetEntityId,
                                          Vec3 targetEyeOffset, Vec3 endEyeOffset) {}

    private record PrecisionTargetLock(int targetEntityId, Vec3 targetEyeOffset,
                                       Vec3 endEyeOffset) {}

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

        private boolean precisionMomentumCarry;

        private Vec3 pendingPrecisionDirection = Vec3.ZERO;
        private long pendingPrecisionMotionSeed;
        private int pendingPrecisionTargetEntityId = -1;
        private Vec3 pendingPrecisionTargetEyeOffset = Vec3.ZERO;
        private Vec3 pendingPrecisionEndEyeOffset = Vec3.ZERO;
        private long precisionMotionSeed;

        private double precisionReleasedPathLength = -1.0D;

        private Vec3 precisionTipFacingDirection = Vec3.ZERO;
        private int precisionTargetEntityId = -1;
        private Vec3 precisionTargetEyeOffset = Vec3.ZERO;
        private Vec3 precisionTrackedTargetPoint = Vec3.ZERO;
        private boolean precisionTrackedTargetValid;
        private boolean precisionTargetPassed;
        private Vec3 precisionEndEyeOffset = Vec3.ZERO;

        private final Vec3[] precisionStartEyeOffsets = new Vec3[POINTS];
        private boolean precisionStartPoseValid;

        private final ArrayDeque<TipTrailSample> tipTrail = new ArrayDeque<>();
        private Vec3 lastTrailSample = null;
        private long lastFlameParticleTick = Long.MIN_VALUE;

        private float coilProgress = 1.0F;

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
                pendingPrecisionMotionSeed = precisionRequest.motionSeed();
                pendingPrecisionTargetEntityId = precisionRequest.targetEntityId();
                pendingPrecisionTargetEyeOffset = precisionRequest.targetEyeOffset();
                pendingPrecisionEndEyeOffset = precisionRequest.endEyeOffset();
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
                precisionMotionSeed = pendingPrecisionMotionSeed;
                precisionTargetEntityId = pendingPrecisionTargetEntityId;
                precisionTargetEyeOffset = finite(pendingPrecisionTargetEyeOffset)
                        ? pendingPrecisionTargetEyeOffset : Vec3.ZERO;
                precisionEndEyeOffset = finite(pendingPrecisionEndEyeOffset)
                        ? pendingPrecisionEndEyeOffset : Vec3.ZERO;
                Vec3 precisionEye = player.getEyePosition();
                precisionTrackedTargetPoint = precisionTargetEntityId >= 0
                        && precisionTargetEyeOffset.lengthSqr() > 1.0E-10D
                        ? precisionEye.add(precisionTargetEyeOffset) : Vec3.ZERO;
                precisionTrackedTargetValid = precisionTargetEntityId >= 0
                        && precisionTargetEyeOffset.lengthSqr() > 1.0E-10D;
                precisionTargetPassed = false;
                precisionStartPoseValid = initialized;
                if (precisionStartPoseValid) {
                    for (int i = 0; i < POINTS; ++i) {
                        precisionStartEyeOffsets[i] = points[i].subtract(precisionEye);
                    }
                }
                pendingPrecisionDirection = Vec3.ZERO;
                pendingPrecisionMotionSeed = 0L;
                pendingPrecisionTargetEntityId = -1;
                pendingPrecisionTargetEyeOffset = Vec3.ZERO;
                pendingPrecisionEndEyeOffset = Vec3.ZERO;
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
            if (!(handleDriveActive && driveMode == AttackDrive.PRECISION)) {
                precisionReleasedPathLength = -1.0D;
                precisionTipFacingDirection = Vec3.ZERO;
            }

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
                boolean precisionRetracting = handleDriveActive
                        && driveMode == AttackDrive.PRECISION
                        && precisionProgress > PRECISION_RETRACT_START_RAW_PROGRESS;
                if (coilProgress > 1.0E-4F && (!handleDriveActive || precisionRetracting)) {
                    applyCoilGuidance();
                }
                if (chargePhase) {
                    applyChargeForces(player, attackDirection, chargeTicks,
                            substepSeconds, substepSecondsSqr);
                } else if (releasePhase) {
                    applyReleaseForces(player, attackDirection, releaseProgress,
                            substepSeconds, substepSecondsSqr);
                } else if (handleDriveActive && driveMode == AttackDrive.PRECISION
                        && precisionProgress < PRECISION_GUIDE_END) {
                    applyPrecisionGuidance(player, substepAnchor, attackDirection, precisionProgress,
                            precisionMomentumCarry);
                }

                double collisionTipScale = chargePhase
                        ? chargedTipScaleForCharge(chargeTicks)
                        : (releasePhase ? RIGHT_TIP_MAX_SCALE : Math.max(1.0F, tipVisualScale));
                WhipBlockCollision.SegmentEnvironment[] blockBroadphase =
                        WhipBlockCollision.buildBroadphase(level, before, points,
                                DestructionWhipGeometry.SEGMENT_COLLIDER_RADIUS,
                                collisionTipScale);

                java.util.Arrays.fill(lengthLambda, 0.0D);
                java.util.Arrays.fill(bendLambda, 0.0D);
                for (int iteration = 0; iteration < SOLVER_ITERATIONS; ++iteration) {
                    points[0] = substepAnchor;
                    for (int i = 0; i < SEGMENTS; ++i) {
                        solveDistance(i, i + 1, currentRestLength(i),
                                LENGTH_COMPLIANCE, lengthLambda, i, false,
                                substepSecondsSqr);
                    }
                    enforceHandleAxis(substepAnchor);
                    enforceAntiFold(substepSecondsSqr);
                    enforceHandleBendZone();
                    enforceHandleContinuity();
                    enforceMaximumStretch();
                    if (iteration == 1) {
                        solveBlockCapsuleContacts(blockBroadphase, before, collisionTipScale,
                                1.0D, false);
                    }
                }
                points[0] = substepAnchor;

                if (!(handleDriveActive && driveMode == AttackDrive.PRECISION)) {
                    solveSelfCollision();
                }
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
            points[0] = targetAnchor;
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
                accumulatedLength += DestructionWhipGeometry.REST_LENGTHS[i - 1];
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

        private void applyPrecisionGuidance(Player player, Vec3 root, Vec3 attackDirection, double rawProgress,
                                            boolean momentumCarry) {
            double phase = precisionAttackTime(rawProgress);
            double outward = precisionOutwardProgress(rawProgress);
            double envelope = 0.20D + 0.80D * Math.sin(Math.PI * phase);
            if (momentumCarry) {
                double carryPhase = Mth.clamp(
                        (rawProgress - PRECISION_RELEASE_RAW)
                                / Math.max(1.0E-6D, PRECISION_GUIDE_END - PRECISION_RELEASE_RAW),
                        0.0D, 1.0D);
                double ease = carryPhase * carryPhase * (3.0D - 2.0D * carryPhase);
                envelope *= Mth.lerp(ease, 0.62D, 1.0D);
            }

            Vec3 eye = player.getEyePosition();
            Vec3 fallbackEnd = DestructionWhipAttackPath.crosshairEndpoint(
                    eye, root, attackDirection, DestructionWhipDimensions.deployedLength());
            Vec3 finalPoint = precisionEndEyeOffset.lengthSqr() > 1.0E-10D
                    ? eye.add(precisionEndEyeOffset) : fallbackEnd;
            boolean hasTarget = precisionTargetEntityId >= 0
                    && precisionTargetEyeOffset.lengthSqr() > 1.0E-10D;
            boolean returning = phase > 0.5D;
            Vec3 targetPoint = null;
            if (hasTarget) {
                if (!returning && !precisionTargetPassed) {
                    Vec3 livePoint = liveLockedTargetPoint(player, root);
                    if (livePoint != null) {
                        precisionTrackedTargetPoint = precisionTrackedTargetValid
                                ? moveToward(precisionTrackedTargetPoint, livePoint, 0.18D)
                                : livePoint;
                        precisionTrackedTargetValid = true;
                    }
                }
                targetPoint = precisionTrackedTargetValid
                        ? precisionTrackedTargetPoint : eye.add(precisionTargetEyeOffset);
            }
            Vec3[] startNodes = new Vec3[POINTS];
            if (precisionStartPoseValid) {
                Vec3 capturedRoot = precisionStartEyeOffsets[0] == null
                        ? root : eye.add(precisionStartEyeOffsets[0]);
                Vec3 rootShift = root.subtract(capturedRoot);
                for (int i = 0; i < POINTS; ++i) {
                    Vec3 offset = precisionStartEyeOffsets[i];
                    startNodes[i] = offset == null ? root : eye.add(offset).add(rootShift);
                }
                startNodes[0] = root;
            } else {
                startNodes[0] = root;
                Vec3 axis = finite(attackDirection) && attackDirection.lengthSqr() > 1.0E-10D
                        ? attackDirection.normalize() : aimDirection(player);
                Vec3 cursor = root;
                for (int i = 0; i < SEGMENTS; ++i) {
                    double stored = DestructionWhipGeometry.REST_LENGTHS[i] * STORED_REST_SCALE;
                    cursor = cursor.add(axis.scale(stored));
                    startNodes[i + 1] = cursor;
                }
            }
            Vec3 startTip = startNodes[POINTS - 1];
            Vec3 launchDirection = startTip.subtract(startNodes[POINTS - 2]);
            DestructionWhipAttackPath.SampledPath leaderPath = DestructionWhipAttackPath.sample(
                    startTip, targetPoint, finalPoint, hasTarget, precisionMotionSeed, launchDirection);
            DestructionWhipAttackPath.SampledPath historyPath =
                    DestructionWhipAttackPath.prependPolyline(startNodes, leaderPath);
            double startChainPathLength = historyPath.cumulative()[POINTS - 1];
            double requestedTraversed = leaderPath.lengthAtProgress(outward);

            double traversed = precisionReleasedPathLength < 0.0D
                    ? requestedTraversed
                    : (returning
                            ? Math.min(precisionReleasedPathLength, requestedTraversed)
                            : Math.max(precisionReleasedPathLength, requestedTraversed));
            precisionReleasedPathLength = traversed;
            double tipPathCoordinate = startChainPathLength + traversed;
            Vec3 desiredTip = leaderPath.pointAtLength(traversed);
            Vec3 tipTangent = leaderPath.tangentAtLength(traversed);

            Vec3 desiredFacing = tipTangent;
            if (!returning && hasTarget && targetPoint != null && !precisionTargetPassed) {
                Vec3 toTarget = targetPoint.subtract(desiredTip);
                if (toTarget.lengthSqr() > 1.0E-8D) {
                    desiredFacing = toTarget.normalize();
                }
            } else {
                Vec3 toFinal = finalPoint.subtract(desiredTip);
                if (toFinal.lengthSqr() > 1.0E-8D) {
                    desiredFacing = toFinal.normalize();
                }
            }
            if (finite(desiredFacing) && desiredFacing.lengthSqr() > 1.0E-10D) {
                Vec3 nextFacing = desiredFacing.normalize();
                if (finite(precisionTipFacingDirection)
                        && precisionTipFacingDirection.lengthSqr() > 1.0E-10D
                        && precisionTipFacingDirection.dot(nextFacing) > -0.25D) {
                    Vec3 blended = precisionTipFacingDirection.lerp(nextFacing, 0.62D);
                    precisionTipFacingDirection = blended.lengthSqr() > 1.0E-10D
                            ? blended.normalize() : nextFacing;
                } else {
                    precisionTipFacingDirection = nextFacing;
                }
            }

            if (!returning && hasTarget && targetPoint != null && !precisionTargetPassed) {
                double nominalHit = DestructionWhipAttackPath.targetProgress(startTip, targetPoint, finalPoint);
                boolean physicalTipReached = points[POINTS - 1].distanceToSqr(targetPoint) <= 0.85D * 0.85D;
                boolean lateSafetyRelease = outward >= Math.min(0.94D, nominalHit + 0.12D);
                if (physicalTipReached || lateSafetyRelease) {
                    precisionTargetPassed = true;
                }
            }
            double startBlend = smoothstep(Mth.clamp(outward / 0.075D, 0.0D, 1.0D));

            double[] distanceToTip = new double[POINTS];
            distanceToTip[POINTS - 1] = 0.0D;
            for (int i = POINTS - 2; i >= 0; --i) {
                distanceToTip[i] = distanceToTip[i + 1] + currentRestLength(i);
            }

            for (int i = 1; i < POINTS; ++i) {
                double taper = i / (double)(POINTS - 1);
                double weight = (0.58D + 0.42D * taper) * envelope;

                double incomingActivation = i == POINTS - 1
                        ? 1.0D : precisionSegmentActivation(i - 1);
                double servoActivation = smoothstep(incomingActivation);
                double nodePathCoordinate = Math.max(0.0D,
                        tipPathCoordinate - distanceToTip[i]);
                Vec3 guided = historyPath.pointAtLength(nodePathCoordinate);
                Vec3 desired = guided;
                if (precisionStartPoseValid && precisionStartEyeOffsets[i] != null
                        && startBlend < 0.999D) {
                    Vec3 startNode = eye.add(precisionStartEyeOffsets[i]);
                    desired = startNode.lerp(guided, startBlend);
                }

                Vec3 error = desired.subtract(points[i]);
                double errorLength = error.length();
                if (errorLength > 1.0E-9D) {
                    double nearTip = Mth.clamp((taper - 0.78D) / 0.22D, 0.0D, 1.0D);
                    double servo = i == POINTS - 1
                            ? 1.0D
                            : (0.30D + 0.70D * servoActivation) * (1.0D - 0.18D * nearTip);
                    double maxCorrection = (0.075D + 0.48D * taper) * weight * servo;
                    if (i == POINTS - 1) maxCorrection *= 1.55D;
                    Vec3 correction = error.scale(Math.min(
                            (0.62D + 0.30D * taper) * weight * servo,
                            maxCorrection / errorLength));
                    points[i] = points[i].add(correction);
                }

                if (i != POINTS - 1 && incomingActivation <= 0.015D) {
                    previous[i] = points[i];
                    continue;
                }

                Vec3 velocity = points[i].subtract(previous[i]);
                double speed = velocity.length();
                if (speed > 1.0E-7D) {
                    Vec3 nodeTangent = historyPath.tangentAtLength(nodePathCoordinate);
                    if (returning) nodeTangent = nodeTangent.scale(-1.0D);
                    Vec3 pathVelocity = nodeTangent.scale(speed);
                    double nearTip = Mth.clamp((taper - 0.78D) / 0.22D, 0.0D, 1.0D);
                    double turnScale = i == POINTS - 1
                            ? 0.90D
                            : (0.35D + 0.65D * servoActivation) * (1.0D - 0.38D * nearTip);
                    double turn = Mth.clamp((0.70D + 0.25D * taper) * weight * turnScale,
                            0.0D, i == POINTS - 1 ? 0.90D : 0.86D);
                    Vec3 guidedVelocity = velocity.lerp(pathVelocity, turn);
                    previous[i] = points[i].subtract(guidedVelocity);
                }
            }
        }

        private Vec3 liveLockedTargetPoint(Player player, Vec3 root) {
            if (precisionTargetEntityId < 0 || player.level() == null) {
                return null;
            }
            Entity raw = player.level().getEntity(precisionTargetEntityId);
            if (!(raw instanceof LivingEntity target) || target == player
                    || !target.isAlive() || !target.isPickable()) {
                return null;
            }
            Vec3 center = target.getBoundingBox().getCenter();
            double max = DestructionWhipDimensions.deployedLength() + 1.75D;
            return root.distanceToSqr(center) <= max * max ? center : null;
        }

        private static Vec3 moveToward(Vec3 current, Vec3 target, double maxStep) {
            Vec3 delta = target.subtract(current);
            double distance = delta.length();
            if (distance <= maxStep || distance < 1.0E-9D) {
                return target;
            }
            return current.add(delta.scale(maxStep / distance));
        }

        private void updateCoilState(Player player, long gameTime, boolean handleDriveActive,
                                     AttackDrive driveMode, float driveRawProgress, Vec3 root) {
            if (handleDriveActive) {
                lastAttackDriveGameTime = gameTime;
                if (driveMode == AttackDrive.PRECISION) {
                    float raw = Mth.clamp(driveRawProgress, 0.0F, 1.0F);
                    coilProgress = (float)(1.0D - precisionOutwardProgress(raw));
                    coilProgress = Mth.clamp(coilProgress, 0.0F, 1.0F);
                    highFidelityTicksRemaining = Math.max(highFidelityTicksRemaining, 24);
                } else {

                    if (coilProgress > 0.0F) {
                        coilProgress = Math.max(0.0F,
                                coilProgress - 1.0F / (float)DEPLOY_DURATION_TICKS);
                        highFidelityTicksRemaining = Math.max(highFidelityTicksRemaining, 24);
                    }
                }
            } else {
                boolean canRetract = lastAttackDriveGameTime == Long.MIN_VALUE
                        || gameTime - lastAttackDriveGameTime >= COIL_IDLE_DELAY_TICKS;
                if (coilProgress < 1.0F && canRetract) {
                    coilProgress = Math.min(1.0F,
                            coilProgress + 1.0F / (float)COIL_DURATION_TICKS);
                    highFidelityTicksRemaining = Math.max(highFidelityTicksRemaining, 16);
                }
            }

            if (coilProgress > 1.0E-4F) {
                buildStoredSwordTargets(root);
            }
        }

        private void buildStoredSwordTargets(Vec3 root) {
            Vec3 axis = capturedHandleAxis;
            if (!finite(axis) || axis.lengthSqr() < 1.0E-10D) {
                axis = new Vec3(0.0D, 1.0D, 0.0D);
            } else {
                axis = axis.normalize();
            }
            coilTargets[0] = root;
            Vec3 cursor = root;
            for (int i = 0; i < SEGMENTS; ++i) {
                cursor = cursor.add(axis.scale(DestructionWhipGeometry.REST_LENGTHS[i]));
                coilTargets[i + 1] = cursor;
            }
        }

        private double precisionSegmentActivation(int segmentIndex) {
            if (precisionReleasedPathLength < 0.0D) {
                return 1.0D;
            }

            double distalExtensionBefore = 0.0D;
            for (int i = SEGMENTS - 1; i > segmentIndex; --i) {
                double stored = DestructionWhipGeometry.REST_LENGTHS[i] * STORED_REST_SCALE;
                double full = DestructionWhipGeometry.REST_LENGTHS[i] * PHYSICAL_REST_SCALE;
                distalExtensionBefore += Math.max(0.0D, full - stored);
            }
            double stored = DestructionWhipGeometry.REST_LENGTHS[segmentIndex] * STORED_REST_SCALE;
            double full = DestructionWhipGeometry.REST_LENGTHS[segmentIndex] * PHYSICAL_REST_SCALE;
            double extension = Math.max(1.0E-6D, full - stored);
            return Mth.clamp((precisionReleasedPathLength - distalExtensionBefore)
                    / extension, 0.0D, 1.0D);
        }

        private double currentRestLength(int segmentIndex) {
            double authored = DestructionWhipGeometry.REST_LENGTHS[segmentIndex];
            if (precisionReleasedPathLength >= 0.0D) {
                double stored = authored * STORED_REST_SCALE;
                double full = authored * PHYSICAL_REST_SCALE;
                double activation = precisionSegmentActivation(segmentIndex);
                return Mth.lerp(activation, stored, full);
            }
            double storedAmount = Mth.clamp(coilProgress, 0.0F, 1.0F);
            double scale = Mth.lerp(storedAmount, PHYSICAL_REST_SCALE, STORED_REST_SCALE);
            return authored * scale;
        }

        private double currentPairRestLength(int leftSegmentIndex) {
            return currentRestLength(leftSegmentIndex) + currentRestLength(leftSegmentIndex + 1);
        }

        private void applyCoilGuidance() {
            if (coilProgress >= 0.999F) {

                for (int i = 1; i < POINTS; ++i) {
                    points[i] = coilTargets[i];
                    previous[i] = coilTargets[i];
                }
                return;
            }
            for (int i = 1; i < POINTS; ++i) {
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
            if (coilProgress <= 0.0F || pointIndex <= 0) {
                return 0.0D;
            }
            return Mth.clamp(coilProgress, 0.0F, 1.0F);
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
            lastAttackDriveGameTime = Long.MIN_VALUE;
            initialized = true;
        }

        private Vec3 initialDirection(ClientLevel level, Player player, Vec3 root, Vec3 forward) {
            double total = 0.0D;
            for (float restLength : DestructionWhipGeometry.REST_LENGTHS) {
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
            points[0] = anchor;
            previous[0] = anchor;
            for (int i = 1; i < POINTS; ++i) {
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
            Vec3 sourceAxis = precisionHandleAxisTicks > 0
                    && precisionHandleAxis.lengthSqr() > 1.0E-10D
                    ? precisionHandleAxis : capturedHandleAxis;
            if (sourceAxis.lengthSqr() < 1.0E-10D) {
                return;
            }
            Vec3 segment = points[1].subtract(root);
            double length = segment.length();
            if (length < 1.0E-9D) {
                return;
            }
            Vec3 axis = sourceAxis.normalize();
            Vec3 direction = segment.scale(1.0D / length);
            double dot = Mth.clamp(direction.dot(axis), -1.0D, 1.0D);
            double minimumDot = Math.cos(HANDLE_ROOT_MAX_ANGLE);
            if (dot >= minimumDot) {
                return;
            }

            Vec3 tangent = direction.subtract(axis.scale(dot));
            if (tangent.lengthSqr() < 1.0E-10D) {
                tangent = Math.abs(axis.y) < 0.95D
                        ? axis.cross(new Vec3(0.0D, 1.0D, 0.0D))
                        : axis.cross(new Vec3(1.0D, 0.0D, 0.0D));
            }
            tangent = tangent.normalize();
            Vec3 clampedDirection = axis.scale(minimumDot)
                    .add(tangent.scale(Math.sin(HANDLE_ROOT_MAX_ANGLE)))
                    .normalize();
            points[1] = root.add(clampedDirection.scale(length));
        }

        private void enforceAntiFold(double substepSecondsSqr) {
            for (int i = 1; i < SEGMENTS; ++i) {
                double adjacent = currentPairRestLength(i - 1);

                double taper = (i - 1.0D) / Math.max(1.0D, SEGMENTS - 2.0D);
                double minimumRatio = 0.28D + 0.62D * Math.pow(1.0D - taper, 1.55D);
                minimumRatio = Mth.lerp(coilActivationForPoint(i + 1),
                        minimumRatio, 0.40D);
                solveDistance(i - 1, i + 1, adjacent * minimumRatio,
                        BEND_COMPLIANCE, bendLambda, i - 1, true,
                        substepSecondsSqr);
            }
        }

        private void enforceHandleBendZone() {
            int reinforced = Math.min(HANDLE_BEND_SEGMENTS, SEGMENTS - 1);
            for (int i = 1; i <= reinforced; ++i) {
                double adjacent = currentPairRestLength(i - 1);
                double t = (i - 1.0D) / Math.max(1.0D, reinforced - 1.0D);
                double baseMinimumRatio = Mth.lerp(t, 0.97D, 0.78D);
                double minimumRatio = 1.0D
                        - (1.0D - baseMinimumRatio) / HANDLE_BEND_STIFFNESS_MULTIPLIER;
                minimumRatio = Mth.lerp(coilActivationForPoint(i + 1),
                        minimumRatio, 0.34D);
                enforceMinimumSpan(i - 1, i + 1, adjacent * minimumRatio);
            }
        }

        private void enforceHandleContinuity() {
            int[] checkpoints = {3, 4, 6, 8, 12, 16};
            double[] ratios = {0.70D, 0.58D, 0.50D, 0.42D, 0.36D, 0.32D};
            double accumulated = 0.0D;
            int next = 0;
            for (int i = 0; i < Math.min(HANDLE_BEND_SEGMENTS, SEGMENTS); ++i) {
                accumulated += currentRestLength(i);
                int count = i + 1;
                if (next < checkpoints.length && count == checkpoints[next]) {
                    double ratio = Mth.lerp(coilActivationForPoint(count),
                            ratios[next], 0.08D);
                    enforceMinimumSpan(0, count, accumulated * ratio);
                    ++next;
                }
            }
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
                double radius = DestructionWhipGeometry.SEGMENT_COLLIDER_RADIUS[i]
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

        Vec3 attachmentAnchor(int segmentIndex, float segmentU, float partialTick) {
            int segment = Mth.clamp(segmentIndex, 0, SEGMENTS - 1);
            double u = Mth.clamp(segmentU, 0.0F, 1.0F);
            double t = Mth.clamp(partialTick, 0.0F, 1.0F);
            Vec3 interpolatedRoot = renderPrevious[0].lerp(points[0], t);
            long gameTime = holder != null ? holder.level().getGameTime() : capturedAnchorGameTime;
            Vec3 displayAnchor = renderDisplayAnchor(interpolatedRoot, gameTime);
            Vec3 rootCorrection = displayAnchor.subtract(interpolatedRoot);

            double startRootWeight = 1.0D - segment / (double)(POINTS - 1);
            double endRootWeight = 1.0D - (segment + 1.0D) / (POINTS - 1);
            Vec3 start = renderPrevious[segment].lerp(points[segment], t)
                    .add(rootCorrection.scale(startRootWeight));
            Vec3 end = renderPrevious[segment + 1].lerp(points[segment + 1], t)
                    .add(rootCorrection.scale(endRootWeight));
            return start.lerp(end, u);
        }

        void render(ClientLevel level, PoseStack stack, VertexConsumer consumer,
                    Vec3 camera, float partialTick) {
            Vec3 interpolatedRoot = renderPrevious[0].lerp(points[0], partialTick);
            Vec3 displayAnchor = renderDisplayAnchor(interpolatedRoot, level.getGameTime());
            renderAtDisplayAnchor(level, stack, consumer, camera, partialTick, displayAnchor, false);
        }

        void renderSpectral(ClientLevel level, PoseStack stack, VertexConsumer consumer,
                            Vec3 camera, float partialTick) {
            Vec3 interpolatedRoot = renderPrevious[0].lerp(points[0], partialTick);
            Vec3 displayAnchor = renderDisplayAnchor(interpolatedRoot, level.getGameTime());
            renderAtDisplayAnchor(level, stack, consumer, camera, partialTick, displayAnchor, true);
        }

        void renderPulse(ClientLevel level, PoseStack stack, VertexConsumer consumer,
                         Vec3 camera, float partialTick, int pulseFrame,
                         boolean spectralScale) {
            Vec3 interpolatedRoot = renderPrevious[0].lerp(points[0], partialTick);
            Vec3 displayAnchor = renderDisplayAnchor(interpolatedRoot, level.getGameTime());
            renderAtDisplayAnchorPulse(level, stack, consumer, camera, partialTick,
                    displayAnchor, pulseFrame, spectralScale);
        }

        void renderTerminal(ClientLevel level, PoseStack stack, VertexConsumer consumer,
                            Vec3 camera, float partialTick, boolean spectral) {
            if (coilProgress >= 0.999F) {
                return;
            }
            Vec3 interpolatedRoot = renderPrevious[0].lerp(points[0], partialTick);
            Vec3 displayAnchor = renderDisplayAnchor(interpolatedRoot, level.getGameTime());
            Vec3 rootCorrection = displayAnchor.subtract(interpolatedRoot);
            renderSolvedShape(level, stack, consumer, camera, renderPrevious, points, partialTick,
                    rootCorrection, tipVisualScale, 255, SEGMENTS - 1, spectral);
        }

        void renderTerminalPulse(ClientLevel level, PoseStack stack, VertexConsumer consumer,
                                 Vec3 camera, float partialTick, int pulseFrame,
                                 boolean spectralScale) {
            if (coilProgress >= 0.999F) {
                return;
            }
            Vec3 interpolatedRoot = renderPrevious[0].lerp(points[0], partialTick);
            Vec3 displayAnchor = renderDisplayAnchor(interpolatedRoot, level.getGameTime());
            Vec3 rootCorrection = displayAnchor.subtract(interpolatedRoot);
            renderSolvedShapePulse(level, stack, consumer, camera, renderPrevious, points,
                    partialTick, rootCorrection, tipVisualScale, SEGMENTS - 1,
                    pulseFrame, spectralScale);
        }

        void renderFirstPersonWorld(ClientLevel level, PoseStack stack, VertexConsumer consumer,
                                    Camera viewCamera, float partialTick, long gameTime) {
            Vec3 interpolatedRoot = renderPrevious[0].lerp(points[0], partialTick);
            Vec3 fallback = renderDisplayAnchor(interpolatedRoot, gameTime);
            Vec3 displayAnchor = firstPersonDisplayAnchor(viewCamera, gameTime, partialTick, fallback);
            Vec3 rootCorrection = displayAnchor.subtract(interpolatedRoot);

            double pointOneRootWeight = 1.0D - 1.0D / (POINTS - 1.0D);
            firstPersonBridgeTargetWorld = renderPrevious[1].lerp(points[1], partialTick)
                    .add(rootCorrection.scale(pointOneRootWeight));
            firstPersonBridgeTargetGameTime = gameTime;

            renderSolvedShape(level, stack, consumer, viewCamera.getPosition(),
                    renderPrevious, points, partialTick, rootCorrection, tipVisualScale, 255, 1, false);
        }

        private void renderAtDisplayAnchor(ClientLevel level, PoseStack stack,
                                           VertexConsumer consumer, Vec3 camera,
                                           float partialTick, Vec3 displayAnchor,
                                           boolean spectral) {
            Vec3 interpolatedRoot = renderPrevious[0].lerp(points[0], partialTick);
            Vec3 rootCorrection = displayAnchor.subtract(interpolatedRoot);
            WhipPerformanceTuning.pushGeometryQuality(holder);
            try {
                renderSolvedShape(level, stack, consumer, camera, renderPrevious, points, partialTick,
                        rootCorrection, tipVisualScale, 255, 0, spectral);
            } finally {
                WhipPerformanceTuning.popGeometryQuality();
            }
        }

        private void renderAtDisplayAnchorPulse(ClientLevel level, PoseStack stack,
                                                VertexConsumer consumer, Vec3 camera,
                                                float partialTick, Vec3 displayAnchor,
                                                int pulseFrame, boolean spectralScale) {
            Vec3 interpolatedRoot = renderPrevious[0].lerp(points[0], partialTick);
            Vec3 rootCorrection = displayAnchor.subtract(interpolatedRoot);
            WhipPerformanceTuning.pushGeometryQuality(holder);
            try {
                renderSolvedShapePulse(level, stack, consumer, camera, renderPrevious, points,
                        partialTick, rootCorrection, tipVisualScale, 0,
                        pulseFrame, spectralScale);
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
                double startRootWeight = 1.0D - i / (double)(POINTS - 1);
                double endRootWeight = 1.0D - (i + 1.0D) / (POINTS - 1);
                Vec3 start = fromPoints[i].lerp(toPoints[i], partialTick)
                        .add(rootCorrection.scale(startRootWeight));
                Vec3 end = fromPoints[i + 1].lerp(toPoints[i + 1], partialTick)
                        .add(rootCorrection.scale(endRootWeight));
                if (i == SEGMENTS - 1 && precisionReleasedPathLength >= 0.0D
                        && finite(precisionTipFacingDirection)
                        && precisionTipFacingDirection.lengthSqr() > 1.0E-10D) {
                    Vec3 facing = precisionTipFacingDirection.normalize();
                    start = end.subtract(facing.scale(DestructionWhipGeometry.REST_LENGTHS[i]));
                }
                Vec3 chord = end.subtract(start);
                double length = chord.length();
                if (length < 1.0E-6D) {
                    continue;
                }

                Vector3f direction = chord.scale(1.0D / length).toVector3f();
                Quaternionf rotation = DestructionWhipGeometry.INSTANCE.dynamicRotationTo(direction);
                Vec3 midpoint = start.lerp(end, 0.5D);
                int light = LevelRenderer.getLightColor(level, BlockPos.containing(midpoint));
                double authoredLength = DestructionWhipGeometry.REST_LENGTHS[i];
                stack.pushPose();
                stack.translate(start.x - camera.x, start.y - camera.y, start.z - camera.z);
                stack.mulPose(rotation);

                if (i == SEGMENTS - 1 && renderedTipScale > 1.001F) {
                    stack.scale(renderedTipScale, renderedTipScale, renderedTipScale);
                }
                if (spectral) {
                    DestructionWhipGeometry.INSTANCE.renderDynamicSegmentScaledAboutCenter(
                            i, stack, consumer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                            SPECTRAL_OVERLAY_SCALE,
                            SPECTRAL_OVERLAY_RED, SPECTRAL_OVERLAY_GREEN,
                            SPECTRAL_OVERLAY_BLUE, Math.min(alpha, SPECTRAL_OVERLAY_ALPHA));
                } else {
                    DestructionWhipGeometry.INSTANCE.renderDynamicSegment(
                            i, stack, consumer, light, OverlayTexture.NO_OVERLAY,
                            255, 255, 255, alpha);
                }
                stack.popPose();
            }
        }

        private void renderSolvedShapePulse(ClientLevel level, PoseStack stack,
                                            VertexConsumer consumer, Vec3 camera,
                                            Vec3[] fromPoints, Vec3[] toPoints,
                                            float partialTick, Vec3 rootCorrection,
                                            float renderedTipScale, int firstSegment,
                                            int pulseFrame, boolean spectralScale) {
            for (int i = Math.max(0, firstSegment); i < SEGMENTS; ++i) {
                double startRootWeight = 1.0D - i / (double)(POINTS - 1);
                double endRootWeight = 1.0D - (i + 1.0D) / (POINTS - 1);
                Vec3 start = fromPoints[i].lerp(toPoints[i], partialTick)
                        .add(rootCorrection.scale(startRootWeight));
                Vec3 end = fromPoints[i + 1].lerp(toPoints[i + 1], partialTick)
                        .add(rootCorrection.scale(endRootWeight));
                if (i == SEGMENTS - 1 && precisionReleasedPathLength >= 0.0D
                        && finite(precisionTipFacingDirection)
                        && precisionTipFacingDirection.lengthSqr() > 1.0E-10D) {
                    Vec3 facing = precisionTipFacingDirection.normalize();
                    start = end.subtract(facing.scale(DestructionWhipGeometry.REST_LENGTHS[i]));
                }
                Vec3 chord = end.subtract(start);
                double length = chord.length();
                if (length < 1.0E-6D) {
                    continue;
                }
                Vector3f direction = chord.scale(1.0D / length).toVector3f();
                Quaternionf rotation = DestructionWhipGeometry.INSTANCE.dynamicRotationTo(direction);
                Vec3 midpoint = start.lerp(end, 0.5D);
                int light = LevelRenderer.getLightColor(level, BlockPos.containing(midpoint));
                stack.pushPose();
                stack.translate(start.x - camera.x, start.y - camera.y, start.z - camera.z);
                stack.mulPose(rotation);
                if (i == SEGMENTS - 1 && renderedTipScale > 1.001F) {
                    stack.scale(renderedTipScale, renderedTipScale, renderedTipScale);
                }
                int orangeAlpha = orangePulseAlphaForSegment(i, pulseFrame);
                int yellowAlpha = yellowPulseAlphaForSegment(i, pulseFrame);
                if (spectralScale) {
                    int spectralPulse = Math.max(orangeAlpha, yellowAlpha);
                    DestructionWhipGeometry.INSTANCE.renderDynamicSegmentPulseScaledAboutCenter(
                            i, stack, consumer, LightTexture.FULL_BRIGHT,
                            OverlayTexture.NO_OVERLAY, SPECTRAL_OVERLAY_SCALE,
                            pulseFrame, Math.min(spectralPulse, SPECTRAL_PULSE_ALPHA));
                } else {
                    DestructionWhipGeometry.INSTANCE.renderDynamicSegmentEnergyGradient(
                            i, stack, consumer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                            REDSTONE_BASE_LEFT_RED, REDSTONE_BASE_LEFT_GREEN, REDSTONE_BASE_LEFT_BLUE,
                            REDSTONE_BASE_RIGHT_RED, REDSTONE_BASE_RIGHT_GREEN, REDSTONE_BASE_RIGHT_BLUE,
                            REDSTONE_BASE_ALPHA);
                    if (orangeAlpha > 0) {
                        DestructionWhipGeometry.INSTANCE.renderDynamicSegmentEnergy(
                                i, stack, consumer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                                REDSTONE_ORANGE_RED, REDSTONE_ORANGE_GREEN, REDSTONE_ORANGE_BLUE,
                                orangeAlpha);
                    }
                    if (yellowAlpha > 0) {
                        DestructionWhipGeometry.INSTANCE.renderDynamicSegmentEnergy(
                                i, stack, consumer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                                REDSTONE_YELLOW_RED, REDSTONE_YELLOW_GREEN, REDSTONE_YELLOW_BLUE,
                                yellowAlpha);
                    }
                }
                stack.popPose();
            }
        }
        void renderBladeConnectors(PoseStack stack, VertexConsumer consumer, Vec3 camera,
                                   float partialTick) {
            PoseStack.Pose pose = stack.last();
            Vec3 interpolatedRoot = renderPrevious[0].lerp(points[0], partialTick);
            long gameTime = holder != null ? holder.level().getGameTime() : capturedAnchorGameTime;
            Vec3 displayAnchor = renderDisplayAnchor(interpolatedRoot, gameTime);
            Vec3 rootCorrection = displayAnchor.subtract(interpolatedRoot);
            for (int i = 0; i < SEGMENTS - 1; ++i) {
                double startRootWeight = 1.0D - i / (double)(POINTS - 1);
                double endRootWeight = 1.0D - (i + 1.0D) / (POINTS - 1);
                double nextRootWeight = 1.0D - (i + 2.0D) / (POINTS - 1);
                Vec3 start = renderPrevious[i].lerp(points[i], partialTick)
                        .add(rootCorrection.scale(startRootWeight));
                Vec3 end = renderPrevious[i + 1].lerp(points[i + 1], partialTick)
                        .add(rootCorrection.scale(endRootWeight));
                Vec3 next = renderPrevious[i + 2].lerp(points[i + 2], partialTick)
                        .add(rootCorrection.scale(nextRootWeight));
                Vec3 chord = end.subtract(start);
                double length = chord.length();
                if (length < 1.0E-6D) {
                    continue;
                }
                double authoredLength = Math.min(length, DestructionWhipGeometry.REST_LENGTHS[i]);
                Vec3 bladeEnd = start.add(chord.scale(authoredLength / length));
                renderPhysicalConnectorWorld(pose, consumer, camera,
                        bladeEnd, end, start, next);
            }
        }

        void renderTipTrail(PoseStack stack, VertexConsumer consumer, Vec3 camera,
                            float partialTick, long gameTime) {
            if (tipTrail.size() < 2) {
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

            int r = 255;
            int g = core ? 198 : 54;
            int b = core ? 168 : 36;
            int a0 = (int)(Mth.clamp(fadeStart, 0.0F, 1.0F) * (core ? 205 : 118));
            int a1 = (int)(Mth.clamp(fadeEnd, 0.0F, 1.0F) * (core ? 228 : 146));
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

        ArmPose leftGather = precisionArmPose(0.30F, side);
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
        float leftClickEquivalent = Mth.lerp(t, 0.30F, 1.0F);
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
                    && player.getUseItem().is(ModItems.DESTRUCTION_WHIP.get())
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

                precisionWindupTicks = PRECISION_WINDUP_TICKS;
                precisionStrokeTicks = PRECISION_STROKE_TICKS;
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
