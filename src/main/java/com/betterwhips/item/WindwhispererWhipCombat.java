package com.betterwhips.item;

import com.betterwhips.registry.ModDamageTypes;
import com.betterwhips.registry.ModItems;
import com.betterwhips.registry.ModSounds;
import com.betterwhips.network.WindwhispererWhipNetwork;
import com.betterwhips.physics.WindwhispererWhipDimensions;
import com.betterwhips.physics.WhipBlockCollision;
import com.betterwhips.physics.WhipMomentumContinuity;
import com.betterwhips.physics.TrainerStylePrecisionGuide;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class WindwhispererWhipCombat {
    private static final int SEGMENTS = WindwhispererWhipDimensions.SEGMENT_COUNT;
    private static final int POINTS = SEGMENTS + 1;

    private static final int MIN_SUBSTEPS = 8;
    private static final int MAX_SUBSTEPS = 30;
    private static final int SOLVER_ITERATIONS = 7;
    private static final int RIGHT_CHARGE_TICKS = WindwhispererWhipItem.RIGHT_CHARGE_TICKS;
    private static final int RIGHT_SLAM_TICKS = 14;
    private static final int PRECISION_WINDUP_TICKS = 3;
    private static final int PRECISION_STROKE_TICKS = 4;
    private static final double PRECISION_RELEASE_RAW = 0.30D;

    private static final int LEFT_DAMAGE_WINDOW_TICKS = 10;
    private static final int LINGER_AFTER_COOLDOWN_TICKS = 12;
    private static final double RIGHT_SPIN_TURNS_AT_FULL_CHARGE = 4.0D;

    private static final double CHARGE_SPIN_SIGN = 1.0D;
    private static final double CHARGE_CENTRIFUGAL_SCALE = 0.12D;
    private static final double CHARGE_MAX_RADIAL_ACCEL = 150.0D;
    private static final double CHARGE_MAX_TANGENTIAL_ACCEL = 90.0D;
    private static final double RELEASE_FORCE_STIFFNESS = 238.0D;
    private static final double RELEASE_MAX_ACCEL = 360.0D;
    private static final double RIGHT_TIP_MAX_SCALE = 10.0D;
    private static final double WHIP_RIPPLE_RADIUS = 80.0D;
    private static final double WHIP_RIPPLE_TICKS_PER_BLOCK = 0.30D;
    private static final double WHIP_RIPPLE_HEIGHT = 0.75D;
    private static final double PET_COMMAND_RADIUS = 64.0D;
    private static final double WHIP_RIPPLE_WIDTH_TICKS = 4.0D;

    private static final double TICK_SECONDS = 1.0D / 20.0D;

    private static final double MAX_SEGMENT_STRETCH = 1.0030D;
    private static final double PHYSICAL_REST_SCALE = WindwhispererWhipDimensions.DEPLOYED_REST_SCALE;
    private static final double STORED_REST_SCALE = WindwhispererWhipDimensions.STORED_REST_SCALE;
    private static final double LENGTH_COMPLIANCE = 3.0E-8D;
    private static final double BEND_COMPLIANCE = 1.6E-7D;
    private static final double TICK_VELOCITY_RETENTION = 0.989D;
    private static final double GRAVITY = -21.5D;
    private static final double COLLISION_RADIUS = 0.050D;

    private static final BlockState[] HIT_LEAF_STATES = {
            Blocks.OAK_LEAVES.defaultBlockState(), Blocks.SPRUCE_LEAVES.defaultBlockState(),
            Blocks.BIRCH_LEAVES.defaultBlockState(), Blocks.JUNGLE_LEAVES.defaultBlockState(),
            Blocks.ACACIA_LEAVES.defaultBlockState(), Blocks.DARK_OAK_LEAVES.defaultBlockState(),
            Blocks.MANGROVE_LEAVES.defaultBlockState(), Blocks.CHERRY_LEAVES.defaultBlockState(),
            Blocks.AZALEA_LEAVES.defaultBlockState(), Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState()
    };

    private static final int DEPENETRATION_PASSES = 8;
    private static final double DEPENETRATION_EPSILON = 1.0E-4D;
    private static final double SELF_COLLISION_DISTANCE = 0.055D;

    private static final double HANDLE_ROOT_MAX_ANGLE = Math.toRadians(11.0D);

    private static final double ROOT_FIBRE_MAX_LENGTH =
            WindwhispererWhipDimensions.restLength(0) * (PHYSICAL_REST_SCALE - STORED_REST_SCALE);
    private static final double SNAPSHOT_ROOT_MAX_DISTANCE_SQR = 4.5D * 4.5D;
    private static final double SNAPSHOT_SEGMENT_STRETCH_LIMIT = 1.12D;
    private static final double SNAPSHOT_REACH_SLACK = 1.60D;

    private static final int HANDLE_BEND_SEGMENTS = 20;

    private static final double HANDLE_BEND_STIFFNESS_MULTIPLIER = 3.4D;

    private static final double SURFACE_TANGENT_RETENTION_PER_TICK = 0.955D;

    private static final double PRECISION_GUIDE_END = 1.00D;

    private static final double PRECISION_DEPLOY_END_RAW_PROGRESS = 0.10D;

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

    private static final double[] SEGMENT_COLLIDER_RADIUS = {
            0.16252D, 0.06225D, 0.18392D, 0.09763D, 0.16913D, 0.05976D, 0.15428D, 0.05862D, 0.17245D, 0.05717D,
            0.15717D, 0.05548D, 0.14223D, 0.05366D, 0.15776D, 0.05208D, 0.14270D, 0.08196D, 0.12815D, 0.04813D,
            0.14100D, 0.04630D, 0.12647D, 0.04433D, 0.11257D, 0.04219D, 0.12264D, 0.03989D, 0.11268D, 0.03786D,
            0.10389D, 0.05894D, 0.11814D, 0.03327D, 0.10930D, 0.03102D, 0.10045D, 0.02871D, 0.11465D, 0.02630D,
            0.10575D, 0.02373D, 0.09684D, 0.02136D, 0.11101D, 0.01888D, 0.10205D, 0.01631D, 0.01497D, 0.01374D
    };
    private static final double MAX_SEGMENT_COLLIDER_RADIUS = 0.18392D;

    private static final double ENTITY_QUERY_CACHE_MARGIN = 0.85D;

    private static final double[] REST_LENGTHS = WindwhispererWhipDimensions.restLengthsCopy();
    private static final Map<UUID, ServerLashState> ACTIVE = new HashMap<>();

    private static final int MAX_PENDING_PRECISION_INPUTS = 32;
    private static final Map<UUID, ArrayDeque<PendingPrecisionRequest>> PENDING_PRECISION =
            new HashMap<>();

    private static final Map<UUID, Long> NEXT_PRECISION_TICK = new HashMap<>();
    private static final List<WhipShockwavePulse> ACTIVE_SHOCKWAVES = new ArrayList<>();

    private static final Set<UUID> TIP_SPEED_DEBUG = new HashSet<>();

    private WindwhispererWhipCombat() {}

    public static boolean setTipSpeedDebug(ServerPlayer player, boolean enabled) {
        if (player == null) {
            return false;
        }
        if (enabled) {
            TIP_SPEED_DEBUG.add(player.getUUID());
        } else {
            TIP_SPEED_DEBUG.remove(player.getUUID());
        }
        return enabled;
    }

    public static boolean toggleTipSpeedDebug(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        UUID id = player.getUUID();
        if (TIP_SPEED_DEBUG.remove(id)) {
            return false;
        }
        TIP_SPEED_DEBUG.add(id);
        return true;
    }

    public static void begin(ServerLevel level, Player player, InteractionHand hand) {
        beginChargedSlam(level, player, hand);
    }

    public static void beginChargedSlam(ServerLevel level, Player player, InteractionHand hand) {
        HumanoidArm arm = hand == InteractionHand.MAIN_HAND
                ? player.getMainArm() : player.getMainArm().getOpposite();
        startOrRetarget(level, player, hand, arm, LashMode.CHARGED_SLAM, 0);
    }

    public static void cancelChargedSlam(Player player) {
        if (player == null) return;
        ServerLashState state = ACTIVE.get(player.getUUID());
        if (state != null && state.mode == LashMode.CHARGED_SLAM
                && state.ageTicks < RIGHT_CHARGE_TICKS) {
            ACTIVE.remove(player.getUUID());
        }
    }

    public record PrecisionPoseSnapshot(Vec3[] points, Vec3[] previous,
                                        double substepSeconds, Vec3 handleAxis,
                                        Vec3 attackDirection) {}

    public static void tryBeginPrecision(ServerPlayer player) {
        tryBeginPrecision(player, null);
    }

    public static void tryBeginPrecision(ServerPlayer player, PrecisionPoseSnapshot snapshot) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUUID();
        if (!canStartPrecision(player)) {
            PENDING_PRECISION.remove(playerId);
            NEXT_PRECISION_TICK.remove(playerId);
            return;
        }

        ArrayDeque<PendingPrecisionRequest> queue = PENDING_PRECISION.computeIfAbsent(
                playerId, ignored -> new ArrayDeque<>());

        if (queue.size() < MAX_PENDING_PRECISION_INPUTS) {
            queue.addLast(new PendingPrecisionRequest(snapshot));
        }

        long now = player.serverLevel().getGameTime();
        long nextAllowed = NEXT_PRECISION_TICK.getOrDefault(playerId, Long.MIN_VALUE);
        if (now >= nextAllowed && !queue.isEmpty()) {
            PendingPrecisionRequest next = queue.removeFirst();
            if (queue.isEmpty()) {
                PENDING_PRECISION.remove(playerId);
            }
            beginPrecisionNow(player, next.snapshot);
        }
    }

    private static boolean canStartPrecision(ServerPlayer player) {
        return player.isAlive()
                && !player.isSpectator()
                && player.getMainHandItem().is(ModItems.WINDWHISPERER_WHIP.get());
    }

    private static void beginPrecisionNow(ServerPlayer player, PrecisionPoseSnapshot snapshot) {

        player.resetAttackStrengthTicker();
        int period = Math.max(1, WindwhispererWhipItem.attackPeriodTicks(player));
        NEXT_PRECISION_TICK.put(player.getUUID(), player.serverLevel().getGameTime() + period);
        startOrRetarget(player.serverLevel(), player, InteractionHand.MAIN_HAND, player.getMainArm(),
                LashMode.PRECISION, LEFT_DAMAGE_WINDOW_TICKS, snapshot);
    }

    private static void tickPendingPrecisionAttacks(ServerTickEvent.Post event) {
        Iterator<Map.Entry<UUID, ArrayDeque<PendingPrecisionRequest>>> iterator =
                PENDING_PRECISION.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ArrayDeque<PendingPrecisionRequest>> entry = iterator.next();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            ArrayDeque<PendingPrecisionRequest> queue = entry.getValue();
            if (player == null || !canStartPrecision(player) || queue.isEmpty()) {
                NEXT_PRECISION_TICK.remove(entry.getKey());
                iterator.remove();
                continue;
            }
            long now = player.serverLevel().getGameTime();
            long nextAllowed = NEXT_PRECISION_TICK.getOrDefault(entry.getKey(), Long.MIN_VALUE);
            if (now >= nextAllowed) {
                PendingPrecisionRequest pending = queue.removeFirst();
                if (queue.isEmpty()) {
                    iterator.remove();
                }
                beginPrecisionNow(player, pending.snapshot);
            }
        }
    }

    private static final class PendingPrecisionRequest {
        private final PrecisionPoseSnapshot snapshot;

        private PendingPrecisionRequest(PrecisionPoseSnapshot snapshot) {
            this.snapshot = snapshot;
        }
    }

    private static void startOrRetarget(ServerLevel level, Player player, InteractionHand hand,
                                        HumanoidArm arm, LashMode mode, int cooldownTicks) {
        startOrRetarget(level, player, hand, arm, mode, cooldownTicks, null);
    }

    private static void startOrRetarget(ServerLevel level, Player player, InteractionHand hand,
                                        HumanoidArm arm, LashMode mode, int cooldownTicks,
                                        PrecisionPoseSnapshot snapshot) {
        ServerLashState state = ACTIVE.get(player.getUUID());
        if (state == null || !state.matches(level, hand, arm)) {
            state = new ServerLashState(level, player, hand, arm);
            ACTIVE.put(player.getUUID(), state);
        }
        if (mode == LashMode.PRECISION) {
            if (snapshot != null) {
                state.importPrecisionPose(player, snapshot);
            } else {
                state.clearSyncedPrecisionPose();
            }
        }
        state.startAttack(player, mode, cooldownTicks);
    }

    public static void onServerTick(ServerTickEvent.Post event) {

        tickPendingPrecisionAttacks(event);

        Iterator<Map.Entry<UUID, ServerLashState>> iterator = ACTIVE.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ServerLashState> entry = iterator.next();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                iterator.remove();
            } else if (entry.getValue().tick(player)) {
                iterator.remove();
            }
        }

        tickWhipShockwaves();

        Iterator<UUID> debugIterator = TIP_SPEED_DEBUG.iterator();
        while (debugIterator.hasNext()) {
            UUID playerId = debugIterator.next();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(playerId);
            if (player == null) {
                debugIterator.remove();
                continue;
            }
            ServerLashState state = ACTIVE.get(playerId);
            double speed = state == null ? 0.0D : state.tipSpeedBlocksPerSecond();
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    String.format(Locale.ROOT, "鞭梢速度: %.2f 格/s", speed)), true);
        }
    }

    private enum LashMode { CHARGED_SLAM, PRECISION }

    private static boolean finite(Vec3 value) {
        return value != null && Double.isFinite(value.x)
                && Double.isFinite(value.y) && Double.isFinite(value.z);
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

    private static Vec3 basisForward(Player player) {
        Vec3 forward = player.getViewVector(1.0F);
        forward = new Vec3(forward.x, 0.0D, forward.z);
        if (forward.lengthSqr() < 1.0E-10D) {
            forward = Vec3.directionFromRotation(0.0F, player.yBodyRot);
        }
        return new Vec3(forward.x, 0.0D, forward.z).normalize();
    }

    private static Vec3 aimDirection(Player player) {
        Vec3 aim = player.getViewVector(1.0F);
        if (aim.lengthSqr() < 1.0E-10D) {
            aim = Vec3.directionFromRotation(player.getXRot(), player.getYRot());
        }
        return aim.normalize();
    }

    private static Vec3 precisionCrosshairDirection(Player player, Vec3 root, Vec3 viewDirection) {
        Vec3 view = viewDirection;
        if (view == null || view.lengthSqr() < 1.0E-10D) {
            view = aimDirection(player);
        } else {
            view = view.normalize();
        }
        Vec3 crosshairPoint = player.getEyePosition()
                .add(view.scale(WindwhispererWhipDimensions.deployedLength()));
        Vec3 fromRoot = crosshairPoint.subtract(root);
        return fromRoot.lengthSqr() > 1.0E-10D ? fromRoot.normalize() : view;
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

    private static double chargedTipScaleForCharge(double chargeTicks) {
        double charge = rightChargeProgress(chargeTicks);
        return 1.0D + (RIGHT_TIP_MAX_SCALE - 1.0D) * charge;
    }

    private static double chargedTipScaleAfterRelease(double releaseTicks) {
        if (releaseTicks <= RIGHT_SLAM_TICKS) {
            return RIGHT_TIP_MAX_SCALE;
        }
        double shrink = Mth.clamp((releaseTicks - RIGHT_SLAM_TICKS) / 6.0D, 0.0D, 1.0D);
        return Mth.lerp(shrink, RIGHT_TIP_MAX_SCALE, 1.0D);
    }

    private static Vec3 handBase(Player player, HumanoidArm arm, Vec3 attackDirection) {
        Vec3 horizontal = new Vec3(attackDirection.x, 0.0D, attackDirection.z);
        if (horizontal.lengthSqr() < 1.0E-10D) {
            horizontal = basisForward(player);
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

    private static Vec3 restHandAnchor(Player player, HumanoidArm arm) {
        return handBase(player, arm, basisForward(player));
    }

    private static Vec3 chargedSpinCenter(Player player, HumanoidArm arm, Vec3 attackDirection) {
        Vec3 forward = new Vec3(attackDirection.x, 0.0D, attackDirection.z);
        if (forward.lengthSqr() < 1.0E-10D) {
            forward = basisForward(player);
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
            forward = basisForward(player);
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
        return restHandAnchor(player, arm).lerp(raisedOrbit, lift);
    }

    private static Vec3 chargedReleaseHandAnchor(Player player, HumanoidArm arm,
                                                 Vec3 attackDirection, double releaseProgress) {
        Vec3 forward = new Vec3(attackDirection.x, 0.0D, attackDirection.z);
        if (forward.lengthSqr() < 1.0E-10D) {
            forward = basisForward(player);
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

    private static Vec3 precisionHandAnchor(Player player, HumanoidArm arm,
                                            Vec3 attackDirection, double rawProgress,
                                            double swingSign) {
        Vec3 aim = attackDirection.lengthSqr() < 1.0E-10D
                ? aimDirection(player) : attackDirection.normalize();
        Vec3 horizontal = new Vec3(aim.x, 0.0D, aim.z);
        if (horizontal.lengthSqr() < 1.0E-10D) {
            horizontal = basisForward(player);
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

    private static Vec3 drivenHandAnchor(Player player, HumanoidArm arm, LashMode mode,
                                         Vec3 attackDirection, double rawProgress) {
        return mode == LashMode.CHARGED_SLAM
                ? chargedSpinHandAnchor(player, arm, attackDirection, rawProgress * RIGHT_CHARGE_TICKS)
                : precisionHandAnchor(player, arm, attackDirection, rawProgress);
    }

    private static final class TickEntityQueryCache {
        private AABB livingBounds;
        private List<Entity> living = List.of();

        List<Entity> living(ServerLevel level, Player owner, AABB requested) {
            if (!containsBounds(livingBounds, requested)) {
                livingBounds = refreshBounds(livingBounds, requested);
                living = WhipEntityTargeting.query(level, owner, livingBounds);
            }
            return living;
        }

        private static AABB refreshBounds(AABB current, AABB requested) {
            AABB combined = current == null ? requested : current.minmax(requested);
            return combined.inflate(ENTITY_QUERY_CACHE_MARGIN);
        }

        private static boolean containsBounds(AABB outer, AABB inner) {
            return outer != null
                    && inner.minX >= outer.minX && inner.maxX <= outer.maxX
                    && inner.minY >= outer.minY && inner.maxY <= outer.maxY
                    && inner.minZ >= outer.minZ && inner.maxZ <= outer.maxZ;
        }
    }

    private static final class ServerLashState {
        private final ServerLevel originalLevel;
        private final InteractionHand hand;
        private final HumanoidArm arm;
        private final int ownerEntityId;
        private final Vec3[] points = new Vec3[POINTS];
        private final Vec3[] previous = new Vec3[POINTS];
        private final double[] lengthLambda = new double[SEGMENTS];
        private final double[] bendLambda = new double[SEGMENTS - 1];

        private final Vec3[] precisionFollowDirections = new Vec3[POINTS];
        private final Vec3[] precisionFollowDirectionDelta = new Vec3[POINTS];
        private boolean precisionFollowInitialized;

        private LashMode mode = LashMode.PRECISION;
        private Vec3 attackDirection = new Vec3(0.0D, 0.0D, 1.0D);

        private double precisionSwingSign = 1.0D;

        private double nextFallbackSwingSign = 1.0D;

        private boolean precisionMomentumCarry;
        private final TrainerStylePrecisionGuide.State trainerStylePrecision =
                new TrainerStylePrecisionGuide.State(POINTS);

        private double precisionStartStoredAmount = 1.0D;
        private Vec3 driveOffset = Vec3.ZERO;

        private Vec3 syncedHandleAxis = Vec3.ZERO;

        private Vec3 syncedPrecisionDirection = Vec3.ZERO;
        private int ageTicks;

        private int precisionWindupTicks = PRECISION_WINDUP_TICKS;

        private int precisionStrokeTicks = PRECISION_STROKE_TICKS;
        private int attackWindowTicks = 20;
        private int lifetimeTicks = 32;
        private int rightCooldownTicks;
        private boolean rightCooldownApplied;

        private boolean rightReleased;
        private int rightReleaseTicks = -1;
        private boolean shockwaveTriggered;
        private boolean crackPlayed;

        private boolean impactSoundPlayed;
        private double tipSpeedBlocksPerSecond;

        private double substepSeconds = TICK_SECONDS / 8.0D;
        private double substepSecondsSqr = substepSeconds * substepSeconds;
        private double substepVelocityRetention = Math.pow(TICK_VELOCITY_RETENTION, 1.0D / 8.0D);
        private double surfaceTangentRetention = Math.pow(SURFACE_TANGENT_RETENTION_PER_TICK, 1.0D / 8.0D);

        private final Set<UUID> latchedContactTargets = new HashSet<>();

        private int resolvedPhysicalHits;

        ServerLashState(ServerLevel level, Player player, InteractionHand hand, HumanoidArm arm) {
            this.originalLevel = level;
            this.hand = hand;
            this.arm = arm;
            this.ownerEntityId = player.getId();
            initialize(restHandAnchor(player, arm), player);
        }

        boolean matches(ServerLevel level, InteractionHand hand, HumanoidArm arm) {
            return originalLevel == level && this.hand == hand && this.arm == arm;
        }

        double tipSpeedBlocksPerSecond() {
            return tipSpeedBlocksPerSecond;
        }

        void clearSyncedPrecisionPose() {
            syncedHandleAxis = Vec3.ZERO;
            syncedPrecisionDirection = Vec3.ZERO;
        }

        void importPrecisionPose(Player player, PrecisionPoseSnapshot snapshot) {
            clearSyncedPrecisionPose();
            if (snapshot == null || snapshot.points() == null || snapshot.previous() == null
                    || snapshot.points().length != POINTS || snapshot.previous().length != POINTS) {
                return;
            }
            double dt = snapshot.substepSeconds();
            if (!Double.isFinite(dt)
                    || dt < TICK_SECONDS / MAX_SUBSTEPS - 1.0E-6D
                    || dt > TICK_SECONDS / MIN_SUBSTEPS + 1.0E-6D) {
                return;
            }
            Vec3[] incoming = snapshot.points();
            Vec3[] incomingPrevious = snapshot.previous();
            for (int i = 0; i < POINTS; ++i) {
                if (!finite(incoming[i]) || !finite(incomingPrevious[i])) {
                    return;
                }
            }
            if (incoming[0].distanceToSqr(player.getEyePosition()) > SNAPSHOT_ROOT_MAX_DISTANCE_SQR) {
                return;
            }

            double cumulative = 0.0D;
            Vec3 root = incoming[0];
            for (int i = 0; i < SEGMENTS; ++i) {
                double segmentLength = incoming[i].distanceTo(incoming[i + 1]);
                double maximumPhysicalLength = REST_LENGTHS[i] * PHYSICAL_REST_SCALE;
                if (segmentLength > maximumPhysicalLength * SNAPSHOT_SEGMENT_STRETCH_LIMIT + 0.015D) {
                    return;
                }
                cumulative += maximumPhysicalLength;
                if (incoming[i + 1].distanceTo(root) > cumulative + SNAPSHOT_REACH_SLACK) {
                    return;
                }
            }
            for (int i = 1; i < POINTS; ++i) {
                double storedStep = incoming[i].distanceTo(incomingPrevious[i]);
                double maxStoredStep = maximumVerletStep(i, dt) * 1.35D + 0.08D;
                if (storedStep > maxStoredStep) {
                    return;
                }
            }

            for (int i = 0; i < POINTS; ++i) {
                points[i] = incoming[i];
                previous[i] = incomingPrevious[i];
            }
            substepSeconds = dt;
            substepSecondsSqr = dt * dt;
            int steps = Mth.clamp((int)Math.round(TICK_SECONDS / dt), MIN_SUBSTEPS, MAX_SUBSTEPS);
            substepVelocityRetention = Math.pow(TICK_VELOCITY_RETENTION, 1.0D / steps);
            surfaceTangentRetention = Math.pow(
                    SURFACE_TANGENT_RETENTION_PER_TICK, 1.0D / steps);

            Vec3 axis = snapshot.handleAxis();
            syncedHandleAxis = finite(axis) && axis.lengthSqr() > 1.0E-10D
                    ? axis.normalize() : Vec3.ZERO;
            Vec3 direction = snapshot.attackDirection();
            syncedPrecisionDirection = finite(direction) && direction.lengthSqr() > 1.0E-8D
                    ? direction.normalize() : Vec3.ZERO;
        }

        private double estimateStoredAmountFromCurrentShape() {
            double scaleSum = 0.0D;
            int samples = 0;
            for (int i = 0; i < SEGMENTS; ++i) {
                double authored = REST_LENGTHS[i];
                if (authored <= 1.0E-9D) continue;
                scaleSum += points[i].distanceTo(points[i + 1]) / authored;
                ++samples;
            }
            if (samples <= 0) return 1.0D;
            double scale = scaleSum / samples;
            double denominator = STORED_REST_SCALE - PHYSICAL_REST_SCALE;
            if (Math.abs(denominator) < 1.0E-9D) return 0.0D;
            return Mth.clamp((scale - PHYSICAL_REST_SCALE) / denominator, 0.0D, 1.0D);
        }

        void startAttack(Player player, LashMode mode, int cooldownTicks) {
            this.mode = mode;
            this.attackDirection = mode == LashMode.PRECISION
                    ? (syncedPrecisionDirection.lengthSqr() > 1.0E-10D
                            ? syncedPrecisionDirection : aimDirection(player))
                    : basisForward(player);
            WhipMomentumContinuity.Continuation continuation = mode == LashMode.PRECISION
                    && syncedPrecisionDirection.lengthSqr() > 1.0E-10D
                    ? WhipMomentumContinuity.analyze(
                            points, previous, substepSeconds, attackDirection, arm)
                    : WhipMomentumContinuity.Continuation.NONE;
            this.precisionSwingSign = continuation.active()
                    ? continuation.swingSign() : nextFallbackSwingSign;
            nextFallbackSwingSign = -nextFallbackSwingSign;
            this.precisionMomentumCarry = continuation.active();
            this.ageTicks = 0;
            this.precisionFollowInitialized = false;
            if (mode == LashMode.PRECISION) {

                this.precisionStartStoredAmount = 0.0D;
                int attackPeriod = WindwhispererWhipItem.attackPeriodTicks(player);
                this.precisionWindupTicks = Mth.clamp(
                        Math.min(PRECISION_WINDUP_TICKS, Math.max(1, attackPeriod - 2)),
                        1, PRECISION_WINDUP_TICKS);
                int availableStrokeTicks = Math.max(1,
                        attackPeriod - this.precisionWindupTicks - 1);
                this.precisionStrokeTicks = Mth.clamp(
                        availableStrokeTicks, 1, PRECISION_STROKE_TICKS);
            } else {
                this.precisionWindupTicks = PRECISION_WINDUP_TICKS;
                this.precisionStrokeTicks = PRECISION_STROKE_TICKS;
            }
            this.attackWindowTicks = mode == LashMode.PRECISION ? LEFT_DAMAGE_WINDOW_TICKS : 0;
            this.rightCooldownTicks = 0;
            this.rightCooldownApplied = false;
            this.rightReleased = false;
            this.rightReleaseTicks = -1;
            this.shockwaveTriggered = false;
            this.lifetimeTicks = mode == LashMode.PRECISION
                    ? this.precisionWindupTicks + this.attackWindowTicks + LINGER_AFTER_COOLDOWN_TICKS
                    : Integer.MAX_VALUE;
            this.crackPlayed = false;
            this.impactSoundPlayed = false;
            this.latchedContactTargets.clear();
            this.resolvedPhysicalHits = 0;
            Vec3 canonicalStart = mode == LashMode.CHARGED_SLAM
                    ? chargedSpinHandAnchor(player, arm, attackDirection, 0.0D)
                    : precisionHandAnchor(player, arm, attackDirection, 0.0D, precisionSwingSign);
            this.driveOffset = points[0].subtract(canonicalStart);
        }

        boolean tick(ServerPlayer player) {
            if (player.serverLevel() != originalLevel || !player.isAlive()) {
                return true;
            }
            if (!player.getItemInHand(hand).is(ModItems.WINDWHISPERER_WHIP.get())) {
                return true;
            }

            boolean chargingRight = false;
            if (mode == LashMode.CHARGED_SLAM && !rightReleased) {
                boolean stillUsing = player.isUsingItem()
                        && player.getUsedItemHand() == hand
                        && player.getUseItem().is(ModItems.WINDWHISPERER_WHIP.get());
                if (!stillUsing) {
                    if (ageTicks < RIGHT_CHARGE_TICKS) {
                        return true;
                    }

                    rightReleased = true;
                    rightReleaseTicks = 0;
                    attackDirection = basisForward(player);
                    Vec3 releaseStart = chargedReleaseHandAnchor(
                            player, arm, attackDirection, 0.0D);
                    driveOffset = points[0].subtract(releaseStart);
                    rightCooldownApplied = true;
                    rightCooldownTicks = WindwhispererWhipItem.attackPeriodTicks(player);
                    player.getCooldowns().addCooldown(
                            ModItems.WINDWHISPERER_WHIP.get(), rightCooldownTicks);
                    player.resetAttackStrengthTicker();
                    lifetimeTicks = Math.max(RIGHT_SLAM_TICKS, rightCooldownTicks)
                            + LINGER_AFTER_COOLDOWN_TICKS;
                    latchedContactTargets.clear();
                    resolvedPhysicalHits = 0;
                } else {
                    ++ageTicks;
                    chargingRight = true;

                    attackDirection = basisForward(player);
                }
            } else if (mode == LashMode.PRECISION) {
                ++ageTicks;
            }

            if (mode == LashMode.CHARGED_SLAM && rightReleased) {
                ++rightReleaseTicks;
            }

            boolean chargeDrive = mode == LashMode.CHARGED_SLAM && chargingRight;
            boolean releaseDrive = mode == LashMode.CHARGED_SLAM && rightReleased
                    && rightReleaseTicks <= RIGHT_SLAM_TICKS;
            boolean precisionMotion = mode == LashMode.PRECISION
                    && ageTicks <= precisionWindupTicks + precisionStrokeTicks;
            boolean precisionDrive = precisionMotion;
            boolean driveActive = chargeDrive || releaseDrive || precisionMotion;

            double previousPrecision;
            double currentPrecision;
            if (mode == LashMode.PRECISION && ageTicks <= precisionWindupTicks) {
                previousPrecision = PRECISION_RELEASE_RAW * Mth.clamp(
                        (ageTicks - 1.0D) / precisionWindupTicks, 0.0D, 1.0D);
                currentPrecision = PRECISION_RELEASE_RAW * Mth.clamp(
                        ageTicks / (double)precisionWindupTicks, 0.0D, 1.0D);
            } else {
                double previousLash = Mth.clamp(
                        (ageTicks - precisionWindupTicks - 1.0D) / precisionStrokeTicks,
                        0.0D, 1.0D);
                double currentLash = Mth.clamp(
                        (ageTicks - precisionWindupTicks) / (double)precisionStrokeTicks,
                        0.0D, 1.0D);
                previousPrecision = Mth.lerp(previousLash, PRECISION_RELEASE_RAW, 1.0D);
                currentPrecision = Mth.lerp(currentLash, PRECISION_RELEASE_RAW, 1.0D);
            }
            double chargeFromTicks = Math.max(0.0D, ageTicks - 1.0D);
            double chargeToTicks = Math.max(0.0D, ageTicks);
            double releaseFrom = Mth.clamp(
                    (rightReleaseTicks - 1.0D) / RIGHT_SLAM_TICKS, 0.0D, 1.0D);
            double releaseTo = Mth.clamp(
                    rightReleaseTicks / (double)RIGHT_SLAM_TICKS, 0.0D, 1.0D);

            Vec3 targetRoot;
            if (chargeDrive) {
                targetRoot = chargedSpinHandAnchor(
                        player, arm, attackDirection, chargeToTicks).add(driveOffset);
            } else if (releaseDrive) {
                targetRoot = chargedReleaseHandAnchor(
                        player, arm, attackDirection, releaseTo).add(driveOffset);
            } else if (precisionMotion) {
                targetRoot = precisionHandAnchor(
                        player, arm, attackDirection, currentPrecision, precisionSwingSign)
                        .add(driveOffset);
            } else {
                targetRoot = restHandAnchor(player, arm);
            }

            Vec3 startRoot = points[0];
            Vec3 rootDelta = targetRoot.subtract(startRoot);
            if (rootDelta.lengthSqr() > 9.0D) {
                for (int i = 0; i < POINTS; ++i) {
                    points[i] = points[i].add(rootDelta);
                    previous[i] = previous[i].add(rootDelta);
                }
                startRoot = targetRoot;
            }

            boolean damageActive = mode == LashMode.PRECISION
                    ? ageTicks > precisionWindupTicks
                            && ageTicks <= precisionWindupTicks + attackWindowTicks
                    : rightReleased && rightCooldownApplied
                            && rightReleaseTicks <= rightCooldownTicks;
            double tipTravelThisTick = 0.0D;
            WhipBlockCollision.TickCache blockTickCache = new WhipBlockCollision.TickCache();
            TickEntityQueryCache entityQueries = new TickEntityQueryCache();
            depenetrateFromBlocks(originalLevel, points, previous, blockTickCache);

            int substeps = chooseAdaptiveSubsteps(chargeDrive, releaseDrive, precisionMotion);
            configureAdaptiveTimestep(substeps);

            for (int substep = 0; substep < substeps; ++substep) {
                Vec3[] before = points.clone();
                double alpha = (substep + 1.0D) / substeps;
                double precisionProgress = 1.0D;
                double chargeTicks = chargeToTicks;
                double releaseProgress = releaseTo;
                Vec3 substepRoot;
                if (chargeDrive) {
                    chargeTicks = Mth.lerp(alpha, chargeFromTicks, chargeToTicks);
                    substepRoot = chargedSpinHandAnchor(
                            player, arm, attackDirection, chargeTicks).add(driveOffset);
                } else if (releaseDrive) {
                    releaseProgress = Mth.lerp(alpha, releaseFrom, releaseTo);
                    substepRoot = chargedReleaseHandAnchor(
                            player, arm, attackDirection, releaseProgress).add(driveOffset);
                } else if (precisionMotion) {
                    precisionProgress = Mth.lerp(alpha, previousPrecision, currentPrecision);
                    substepRoot = precisionHandAnchor(
                            player, arm, attackDirection, precisionProgress, precisionSwingSign)
                            .add(driveOffset);
                } else {
                    substepRoot = startRoot.lerp(targetRoot, alpha);
                }

                predict(substepRoot);
                if (chargeDrive) {
                    applyChargeForces(player, attackDirection, chargeTicks);
                } else if (releaseDrive) {
                    applyReleaseForces(player, attackDirection, releaseProgress);
                } else if (precisionDrive
                        && precisionProgress >= PRECISION_RELEASE_RAW
                        ) {
                    applyPrecisionGuidance(player, substepRoot, precisionProgress,
                            precisionMomentumCarry);
                }

                double tipScale = 1.0D;
                if (mode == LashMode.CHARGED_SLAM) {
                    tipScale = rightReleased
                            ? chargedTipScaleAfterRelease(rightReleaseTicks - 1.0D + alpha)
                            : chargedTipScaleForCharge(chargeTicks);
                }

                WhipBlockCollision.SegmentEnvironment[] blockBroadphase =
                        WhipBlockCollision.buildBroadphase(originalLevel, before, points,
                                SEGMENT_COLLIDER_RADIUS, tipScale, blockTickCache);

                Arrays.fill(lengthLambda, 0.0D);
                Arrays.fill(bendLambda, 0.0D);
                WhipBlockCollision.SegmentContact tipBlockContact = null;
                Vec3 tipBeforeBlockSolve = points[POINTS - 1];
                for (int iteration = 0; iteration < SOLVER_ITERATIONS; ++iteration) {
                    points[0] = substepRoot;

                    for (int i = 0; i < SEGMENTS; ++i) {
                        double rest = REST_LENGTHS[i] * PHYSICAL_REST_SCALE;
                        solveDistance(i, i + 1, rest,
                                LENGTH_COMPLIANCE, lengthLambda, i, false);
                    }

                    if (!precisionDrive) {
                        enforceHandleAxis(substepRoot);
                        enforceHandleBendZone();
                        enforceHandleContinuity();
                    }
                    enforceAntiFold();
                    enforceMaximumStretch();
                    if (iteration == 1) {
                        tipBeforeBlockSolve = points[POINTS - 1];
                        WhipBlockCollision.SegmentContact contact = solveBlockCapsuleContacts(
                                blockBroadphase, before, tipScale, false);
                        if (contact != null) tipBlockContact = contact;
                    }
                }
                points[0] = substepRoot;
                solveSelfCollision();
                WhipBlockCollision.SegmentContact finalTipContact = solveBlockCapsuleContacts(
                        blockBroadphase, before, tipScale, true);
                if (finalTipContact != null) {
                    tipBlockContact = finalTipContact;
                }
                Vec3 tipMotionBeforeCollision = tipBeforeBlockSolve.subtract(before[POINTS - 1]);
                ProjectileContact enlargedTipLivingHit = mode == LashMode.CHARGED_SLAM && rightReleased
                        ? findTipLivingImpact(originalLevel, player,
                                before[POINTS - 2], before[POINTS - 1],
                                points[POINTS - 2], points[POINTS - 1], tipScale, entityQueries)
                        : null;
                double currentTipSpeed = tipMotionBeforeCollision.length() / substepSeconds;
                tipTravelThisTick += before[POINTS - 1].distanceTo(points[POINTS - 1]);

                if (mode == LashMode.CHARGED_SLAM
                        && rightReleased
                        && rightReleaseTicks <= RIGHT_SLAM_TICKS
                        && !shockwaveTriggered
                        && (tipBlockContact != null || enlargedTipLivingHit != null)) {
                    shockwaveTriggered = true;
                    float impactDamage = WindwhispererWhipItem.damageForSpeed(currentTipSpeed);
                    Vec3 impact;
                    if (tipBlockContact != null
                            && (enlargedTipLivingHit == null
                            || tipBlockContact.toi() <= enlargedTipLivingHit.score())) {
                        impact = tipBlockContact.surfacePoint();
                    } else {
                        impact = enlargedTipLivingHit.contact();
                    }
                    startWhipShockwave(originalLevel, player, impact, impactDamage);
                }

                if (damageActive) {
                    ProjectileBatch projectileBatch = buildProjectileBatch(before, points, tipScale);
                    List<Strike> physicalContacts = findProjectileContacts(
                            originalLevel, player, projectileBatch, entityQueries);
                    Set<UUID> touchingThisSubstep = new HashSet<>();
                    for (Strike strike : physicalContacts) {
                        UUID targetId = WhipEntityTargeting.contactKey(strike.target);
                        touchingThisSubstep.add(targetId);

                        if (latchedContactTargets.contains(targetId)) {
                            continue;
                        }
                        latchedContactTargets.add(targetId);

                        double sectionSpeed = projectileBatch.colliders()[strike.segmentIndex()]
                                .averageMaterialSpeed(substepSeconds);
                        float unscaledContactDamage = WindwhispererWhipItem.damageForSpeed(sectionSpeed);
                        float contactDamage = WhipMultiHitDamage.scale(
                                unscaledContactDamage, resolvedPhysicalHits);
                        if (contactDamage > 0.0F) {
                            ++resolvedPhysicalHits;
                        }
                        if (!impactSoundPlayed) {
                            impactSoundPlayed = true;
                            originalLevel.playSound(null, strike.contact.x, strike.contact.y, strike.contact.z,
                                    ModSounds.WHIP_CRACK.get(), SoundSource.PLAYERS, 1.0F,
                                    0.96F + originalLevel.random.nextFloat() * 0.08F);
                        }
                        damage(originalLevel, player, strike.target, strike.contact,
                                strike.slashDirection, contactDamage, unscaledContactDamage);
                    }

                    latchedContactTargets.retainAll(touchingThisSubstep);
                }

                if (mode == LashMode.PRECISION && !impactSoundPlayed
                        && currentPrecision >= 0.78D) {
                    impactSoundPlayed = true;
                    Vec3 crackPosition = points[POINTS - 1];
                    originalLevel.playSound(null, crackPosition.x, crackPosition.y, crackPosition.z,
                            ModSounds.WHIP_CRACK.get(), SoundSource.PLAYERS, 1.0F,
                            0.96F + originalLevel.random.nextFloat() * 0.08F);
                }

                if (damageActive && !crackPlayed) {
                    crackPlayed = true;
                    originalLevel.playSound(null, player.blockPosition(),
                            ModSounds.WHIP_SWING.get(), SoundSource.PLAYERS,
                            mode == LashMode.CHARGED_SLAM ? 1.05F : 0.95F,
                            mode == LashMode.CHARGED_SLAM ? 0.82F : 1.32F);
                }
            }
            tipSpeedBlocksPerSecond = tipTravelThisTick / TICK_SECONDS;
            if (mode == LashMode.CHARGED_SLAM) {
                if (!rightReleased) {
                    return false;
                }
                return rightReleaseTicks >= lifetimeTicks;
            }
            return ageTicks >= lifetimeTicks;
        }

        private int chooseAdaptiveSubsteps(boolean chargeDrive, boolean releaseDrive,
                                           boolean precisionDrive) {
            double maxSpeed = 0.0D;
            double previousDt = Math.max(1.0E-6D, substepSeconds);
            for (int i = 1; i < POINTS; ++i) {
                maxSpeed = Math.max(maxSpeed,
                        points[i].subtract(previous[i]).length() / previousDt);
            }
            int steps;
            if (maxSpeed < 28.0D) steps = 6;
            else if (maxSpeed < 58.0D) steps = 8;
            else if (maxSpeed < 105.0D) steps = 12;
            else if (maxSpeed < 165.0D) steps = 18;
            else steps = 24;

            if (releaseDrive) steps = Math.max(steps, 18);
            else if (chargeDrive || precisionDrive) steps = Math.max(steps, 12);
            return Mth.clamp(steps, MIN_SUBSTEPS, MAX_SUBSTEPS);
        }

        private void configureAdaptiveTimestep(int substeps) {
            double newDt = TICK_SECONDS / substeps;
            if (Math.abs(newDt - substepSeconds) > 1.0E-12D) {
                double ratio = newDt / substepSeconds;
                for (int i = 1; i < POINTS; ++i) {
                    Vec3 velocityStep = points[i].subtract(previous[i]).scale(ratio);
                    previous[i] = points[i].subtract(velocityStep);
                }
            }
            substepSeconds = newDt;
            substepSecondsSqr = newDt * newDt;
            substepVelocityRetention = Math.pow(TICK_VELOCITY_RETENTION, 1.0D / substeps);
            surfaceTangentRetention = Math.pow(
                    SURFACE_TANGENT_RETENTION_PER_TICK, 1.0D / substeps);
        }

        private WhipBlockCollision.SegmentContact solveBlockCapsuleContacts(
                WhipBlockCollision.SegmentEnvironment[] broadphase,
                Vec3[] before, double tipScale, boolean updateVelocity) {
            WhipBlockCollision.SegmentContact tipContact = null;
            for (int i = 0; i < SEGMENTS; ++i) {
                double radius = SEGMENT_COLLIDER_RADIUS[i]
                        * (i == SEGMENTS - 1 ? Math.max(1.0D, tipScale) : 1.0D);
                WhipBlockCollision.SegmentContact contact = WhipBlockCollision.findContact(
                        broadphase[i], before[i], before[i + 1], points[i], points[i + 1],
                        radius, updateVelocity ? surfaceTangentRetention : 1.0D);
                if (contact == null || contact.correction().lengthSqr() < 1.0E-16D) {
                    continue;
                }

                double u = Mth.clamp(contact.sample(), 0.0D, 1.0D);
                double gradientA = 1.0D - u;
                double gradientB = u;
                double invA = inverseMass(i);
                double invB = inverseMass(i + 1);
                double denominator = gradientA * gradientA * invA
                        + gradientB * gradientB * invB;
                if (denominator <= 1.0E-12D) {
                    continue;
                }
                if (invA > 0.0D) {
                    points[i] = points[i].add(contact.correction()
                            .scale(gradientA * invA / denominator));
                }
                if (invB > 0.0D) {
                    points[i + 1] = points[i + 1].add(contact.correction()
                            .scale(gradientB * invB / denominator));
                }

                if (updateVelocity) {
                    dampInwardVelocity(i, contact.normal());
                    dampInwardVelocity(i + 1, contact.normal());
                }
                if (i == SEGMENTS - 1
                        && (tipContact == null || contact.toi() < tipContact.toi())) {
                    tipContact = contact;
                }
            }
            return tipContact;
        }

        private void dampInwardVelocity(int index, Vec3 normal) {
            if (index <= 0) return;
            Vec3 velocity = points[index].subtract(previous[index]);
            double inward = velocity.dot(normal);
            if (inward < 0.0D) {
                velocity = velocity.subtract(normal.scale(inward));
            }
            velocity = velocity.scale(surfaceTangentRetention);
            previous[index] = points[index].subtract(velocity);
        }

        private void initialize(Vec3 root, Player player) {
            Vec3 forward = basisForward(player);
            Vec3 hanging = initialDirection(originalLevel, player, root, forward);
            points[0] = root;
            previous[0] = root;
            Vec3 cursor = root;
            for (int i = 0; i < SEGMENTS; ++i) {
                cursor = cursor.add(hanging.scale(REST_LENGTHS[i] * PHYSICAL_REST_SCALE));
                points[i + 1] = cursor;
                previous[i + 1] = cursor;
            }
            precisionFollowInitialized = false;
        }

        private Vec3 initialDirection(ServerLevel level, Player player,
                                      Vec3 root, Vec3 forward) {
            double total = 0.0D;
            for (double restLength : REST_LENGTHS) {
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

        private void applyChargeForces(Player player, Vec3 forwardInput, double chargeTicks) {
            Vec3 forward = new Vec3(forwardInput.x, 0.0D, forwardInput.z);
            if (forward.lengthSqr() < 1.0E-10D) {
                forward = basisForward(player);
            } else {
                forward = forward.normalize();
            }
            Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
            double side = arm == HumanoidArm.RIGHT ? 1.0D : -1.0D;
            Vec3 center = chargedSpinCenter(player, arm, forward).add(driveOffset);
            double charge = rightChargeProgress(chargeTicks);
            double omega = rightSpinOmega(chargeTicks);
            double envelope = smoothstep(charge);

            for (int i = 1; i < POINTS; ++i) {
                Vec3 radial = points[i].subtract(center);
                radial = new Vec3(radial.x, 0.0D, radial.z);
                double radius = radial.length();
                if (radius < 1.0E-5D) {
                    continue;
                }
                Vec3 radialDir = radial.scale(1.0D / radius);
                double forwardPart = radialDir.dot(forward);
                double rightPart = radialDir.dot(right);
                Vec3 tangentDir = forward.scale(-rightPart)
                        .add(right.scale(forwardPart))
                        .scale(CHARGE_SPIN_SIGN * side);
                if (tangentDir.lengthSqr() > 1.0E-10D) {
                    tangentDir = tangentDir.normalize();
                }

                double taper = i / (double)(POINTS - 1);
                double weight = Math.pow(taper, 1.30D) * envelope;
                double radialAccel = Math.min(CHARGE_MAX_RADIAL_ACCEL,
                        omega * omega * radius * CHARGE_CENTRIFUGAL_SCALE) * weight;

                Vec3 velocity = points[i].subtract(previous[i]);
                double tangentSpeed = velocity.dot(tangentDir) / substepSeconds;
                double desiredTangentSpeed = omega * radius;
                double tangentAccel = Mth.clamp(
                        (desiredTangentSpeed - tangentSpeed) * 8.0D,
                        -CHARGE_MAX_TANGENTIAL_ACCEL, CHARGE_MAX_TANGENTIAL_ACCEL) * weight;

                Vec3 acceleration = radialDir.scale(radialAccel)
                        .add(tangentDir.scale(tangentAccel));
                points[i] = points[i].add(acceleration.scale(substepSecondsSqr));
            }
        }

        private void applyReleaseForces(Player player, Vec3 forwardInput, double releaseProgress) {
            Vec3 forward = new Vec3(forwardInput.x, 0.0D, forwardInput.z);
            if (forward.lengthSqr() < 1.0E-10D) {
                forward = basisForward(player);
            } else {
                forward = forward.normalize();
            }
            Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
            Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
            double release = smoothstep(releaseProgress);

            double planeBlend = smoothstep(Mth.clamp(releaseProgress / 0.30D, 0.0D, 1.0D));

            Vec3 center = chargedSpinCenter(player, arm, forward).add(driveOffset);

            double pendulumAngle = release * Math.PI * 0.82D;
            Vec3 verticalRadialDir = up.scale(Math.cos(pendulumAngle))
                    .add(forward.scale(Math.sin(pendulumAngle))).normalize();
            Vec3 verticalTangentDir = forward.scale(Math.cos(pendulumAngle))
                    .add(up.scale(-Math.sin(pendulumAngle))).normalize();

            double accumulatedLength = 0.0D;
            for (int i = 1; i < POINTS; ++i) {
                accumulatedLength += REST_LENGTHS[i - 1];
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

        private void predict(Vec3 root) {
            points[0] = root;
            previous[0] = root;
            for (int i = 1; i < POINTS; ++i) {
                Vec3 current = points[i];
                Vec3 velocity = current.subtract(previous[i])
                        .scale(substepVelocityRetention);
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

        private void applyPrecisionGuidance(Player player, Vec3 root, double rawProgress,
                                            boolean momentumCarry) {
            TrainerStylePrecisionGuide.apply(
                    trainerStylePrecision, player, points, previous, attackDirection, rawProgress,
                    precisionSwingSign, momentumCarry, substepSeconds, substepSecondsSqr,
                    REST_LENGTHS, PHYSICAL_REST_SCALE);
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

        private void solveDistance(int a, int b, double rest, double compliance,
                                   double[] lambdas, int lambdaIndex, boolean minimumOnly) {
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

        private void enforceHandleAxis(Vec3 root) {
            if (syncedHandleAxis.lengthSqr() < 1.0E-10D) {
                return;
            }
            Vec3 segment = points[1].subtract(root);
            double length = segment.length();
            if (length < 1.0E-9D) {
                return;
            }
            Vec3 axis = syncedHandleAxis.normalize();
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

        private void enforceAntiFold() {
            for (int i = 1; i < SEGMENTS; ++i) {
                double adjacent = (REST_LENGTHS[i - 1] + REST_LENGTHS[i]) * PHYSICAL_REST_SCALE;
                double taper = (i - 1.0D) / Math.max(1.0D, SEGMENTS - 2.0D);
                double minimumRatio = 0.28D + 0.62D * Math.pow(1.0D - taper, 1.55D);
                solveDistance(i - 1, i + 1, adjacent * minimumRatio,
                        BEND_COMPLIANCE, bendLambda, i - 1, true);
            }
        }

        private void enforceHandleBendZone() {
            int reinforced = Math.min(HANDLE_BEND_SEGMENTS, SEGMENTS - 1);
            for (int i = 1; i <= reinforced; ++i) {
                double adjacent = (REST_LENGTHS[i - 1] + REST_LENGTHS[i]) * PHYSICAL_REST_SCALE;
                double t = (i - 1.0D) / Math.max(1.0D, reinforced - 1.0D);
                double baseMinimumRatio = Mth.lerp(t, 0.97D, 0.78D);
                double minimumRatio = 1.0D
                        - (1.0D - baseMinimumRatio) / HANDLE_BEND_STIFFNESS_MULTIPLIER;
                enforceMinimumSpan(i - 1, i + 1, adjacent * minimumRatio);
            }
        }

        private void enforceHandleContinuity() {
            int[] checkpoints = {3, 4, 6, 8, 12, 16};
            double[] ratios = {0.70D, 0.58D, 0.50D, 0.42D, 0.36D, 0.32D};
            double accumulated = 0.0D;
            int next = 0;
            for (int i = 0; i < Math.min(HANDLE_BEND_SEGMENTS, SEGMENTS); ++i) {
                accumulated += REST_LENGTHS[i] * PHYSICAL_REST_SCALE;
                int count = i + 1;
                if (next < checkpoints.length && count == checkpoints[next]) {
                    enforceMinimumSpan(0, count, accumulated * ratios[next]);
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

        private void enforceMaximumStretch() {

            for (int i = 0; i < SEGMENTS; ++i) {
                Vec3 delta = points[i + 1].subtract(points[i]);
                double distance = delta.length();
                double maximum = REST_LENGTHS[i] * PHYSICAL_REST_SCALE * MAX_SEGMENT_STRETCH;
                if (distance <= maximum || distance < 1.0E-9D) {
                    continue;
                }
                double weightA = inverseMass(i);
                double weightB = inverseMass(i + 1);
                double totalWeight = weightA + weightB;
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

        private void solveSelfCollision() {
            double minimum = SELF_COLLISION_DISTANCE;
            double minimumSqr = minimum * minimum;
            for (int i = 1; i < POINTS; ++i) {
                Vec3 a = points[i];
                for (int j = i + 3; j < POINTS; ++j) {
                    Vec3 b = points[j];

                    double dx = b.x - a.x;
                    if (Math.abs(dx) >= minimum) continue;
                    double dy = b.y - a.y;
                    if (Math.abs(dy) >= minimum) continue;
                    double dz = b.z - a.z;
                    if (Math.abs(dz) >= minimum) continue;
                    double distanceSqr = dx * dx + dy * dy + dz * dz;
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
                    double scale = (minimum - distance) / distance;
                    Vec3 correction = new Vec3(dx * scale, dy * scale, dz * scale);
                    points[i] = points[i].add(correction.scale(-weightA / totalWeight));
                    points[j] = points[j].add(correction.scale(weightB / totalWeight));
                    a = points[i];
                }
            }
        }
    }

    private static void depenetrateFromBlocks(ServerLevel level, Vec3[] points, Vec3[] previous,
                                              WhipBlockCollision.TickCache blockTickCache) {
        for (int i = 1; i < POINTS; ++i) {
            for (int pass = 0; pass < DEPENETRATION_PASSES; ++pass) {
                Vec3 push = nearestDepenetration(
                        level, points[i], COLLISION_RADIUS, blockTickCache);
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

    private static Vec3 nearestDepenetration(ServerLevel level, Vec3 point, double radius,
                                              WhipBlockCollision.TickCache blockTickCache) {
        int minX = Mth.floor(point.x - radius);
        int minY = Mth.floor(point.y - radius);
        int minZ = Mth.floor(point.z - radius);
        int maxX = Mth.floor(point.x + radius);
        int maxY = Mth.floor(point.y + radius);
        int maxZ = Mth.floor(point.z + radius);

        List<AABB> solids = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
            for (AABB world : blockTickCache.collisionBoxes(level, pos)) {
                solids.add(world.inflate(radius));
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

    private static ProjectileContact findTipLivingImpact(ServerLevel level, Player player,
                                             Vec3 beforeStart, Vec3 beforeTip,
                                             Vec3 afterStart, Vec3 afterTip,
                                             double tipScale,
                                             TickEntityQueryCache entityQueries) {
        double radius = SEGMENT_COLLIDER_RADIUS[SEGMENTS - 1] * Math.max(1.0D, tipScale);
        AABB search = new AABB(beforeStart, beforeTip).minmax(new AABB(afterStart, afterTip))
                .inflate(radius + 0.25D);
        SegmentProjectileCollider tipProjectile = new SegmentProjectileCollider(
                SEGMENTS - 1, beforeStart, beforeTip, afterStart, afterTip, radius);
        ProjectileContact best = null;
        for (Entity living : entityQueries.living(level, player, search)) {
            if (!WhipEntityTargeting.canContact(player, living)) {
                continue;
            }
            AABB targetBox = living.getBoundingBox();
            if (!targetBox.intersects(search)) {
                continue;
            }
            ProjectileContact contact = tipProjectile.findContact(targetBox);
            if (contact != null
                    && WhipEntityTargeting.mayDamageAtContact(player, living, contact.contact())
                    && (best == null || contact.score < best.score)) {
                best = contact;
            }
        }
        return best;
    }

    private static ProjectileBatch buildProjectileBatch(Vec3[] before, Vec3[] after,
                                                         double tipScale) {
        SegmentProjectileCollider[] colliders = new SegmentProjectileCollider[SEGMENTS];
        AABB[] segmentSweeps = new AABB[SEGMENTS];
        double maxRadius = Math.max(MAX_SEGMENT_COLLIDER_RADIUS,
                SEGMENT_COLLIDER_RADIUS[SEGMENTS - 1] * tipScale);
        for (int i = 0; i < SEGMENTS; ++i) {
            double radius = SEGMENT_COLLIDER_RADIUS[i]
                    * (i == SEGMENTS - 1 ? tipScale : 1.0D);
            colliders[i] = new SegmentProjectileCollider(
                    i, before[i], before[i + 1], after[i], after[i + 1], radius);
            segmentSweeps[i] = new AABB(before[i], before[i + 1])
                    .minmax(new AABB(after[i], after[i + 1]))
                    .inflate(radius + 0.006D);
        }
        AABB core = sweptBounds(before, after);
        return new ProjectileBatch(colliders, segmentSweeps, core.inflate(maxRadius));
    }

    private static List<Strike> findProjectileContacts(ServerLevel level, Player player,
                                                       ProjectileBatch batch,
                                                       TickEntityQueryCache entityQueries) {
        AABB search = batch.livingSearch();
        List<Strike> strikes = new ArrayList<>();
        for (Entity candidate : entityQueries.living(level, player, search)) {
            if (!WhipEntityTargeting.canContact(player, candidate)) {
                continue;
            }
            AABB targetBox = candidate.getBoundingBox();
            if (!targetBox.intersects(search)) {
                continue;
            }
            ProjectileContact best = null;
            int bestSegment = -1;
            for (int i = 0; i < SEGMENTS; ++i) {

                if (!overlapsInclusive(batch.segmentSweeps()[i], targetBox)) {
                    continue;
                }
                ProjectileContact contact = batch.colliders()[i].findContact(targetBox);
                if (contact == null) {
                    continue;
                }
                if (best == null || contact.score < best.score) {
                    best = contact;
                    bestSegment = i;
                    if (best.score <= 0.0D) {
                        break;
                    }
                }
            }
            if (best != null) {
                SegmentProjectileCollider hitCollider = batch.colliders()[bestSegment];
                Vec3 visualContact = visualContactForStrike(targetBox, hitCollider, best);
                if (!WhipEntityTargeting.mayDamageAtContact(player, candidate, visualContact)) {
                    continue;
                }
                strikes.add(new Strike(candidate, visualContact, bestSegment, best.score,
                        contactSlashDirection(hitCollider)));
            }
        }

        strikes.sort(java.util.Comparator.comparingDouble(Strike::score));
        Set<UUID> uniqueContactTargets = new HashSet<>();
        strikes.removeIf(strike -> !uniqueContactTargets.add(WhipEntityTargeting.contactKey(strike.target())));
        return strikes;
    }

    private record ProjectileBatch(SegmentProjectileCollider[] colliders,
                                   AABB[] segmentSweeps,
                                   AABB livingSearch) {}

    private record SegmentProjectileCollider(int segmentIndex,
                                             Vec3 beforeStart, Vec3 beforeEnd,
                                             Vec3 afterStart, Vec3 afterEnd,
                                             double radius) {
        private static final double[] MATERIAL_SAMPLES = {
                0.0D, 0.125D, 0.25D, 0.375D, 0.50D,
                0.625D, 0.75D, 0.875D, 1.0D
        };

        double averageMaterialSpeed(double dtSeconds) {
            double dt = Math.max(1.0E-6D, dtSeconds);
            double startSpeed = beforeStart.distanceTo(afterStart) / dt;
            double endSpeed = beforeEnd.distanceTo(afterEnd) / dt;
            return (startSpeed + endSpeed) * 0.5D;
        }

        ProjectileContact findContact(AABB target) {
            AABB swept = new AABB(beforeStart, beforeEnd)
                    .minmax(new AABB(afterStart, afterEnd))
                    .inflate(radius + 0.006D);
            if (!overlapsInclusive(swept, target)) {
                return null;
            }
            AABB expanded = target.inflate(radius + 0.006D);
            ProjectileContact best = null;

            double beforeEntry = segmentAabbEntry(beforeStart, beforeEnd, expanded);
            if (Double.isFinite(beforeEntry)) {
                Vec3 contact = beforeStart.lerp(beforeEnd, beforeEntry);
                return new ProjectileContact(beforeStart, contact, 0.0D);
            }
            double currentEntry = segmentAabbEntry(afterStart, afterEnd, expanded);
            if (Double.isFinite(currentEntry)) {
                Vec3 contact = afterStart.lerp(afterEnd, currentEntry);
                ProjectileContact current = new ProjectileContact(afterStart, contact, 1.0D);
                if (best == null || current.score < best.score) {
                    best = current;
                }
            }

            for (double sample : MATERIAL_SAMPLES) {
                Vec3 from = beforeStart.lerp(beforeEnd, sample);
                Vec3 to = afterStart.lerp(afterEnd, sample);
                double entry = segmentAabbEntry(from, to, expanded);
                if (!Double.isFinite(entry)) {
                    continue;
                }
                Vec3 contact = from.lerp(to, entry);
                if (entry <= 0.0D) {
                    return new ProjectileContact(from, contact, 0.0D);
                }
                if (best == null || entry < best.score) {
                    best = new ProjectileContact(from, contact, entry);
                }
            }

            double endpointTravel = Math.max(
                    beforeStart.distanceTo(afterStart), beforeEnd.distanceTo(afterEnd));
            double sliceSpacing = Math.max(0.035D, radius * 0.85D);
            int temporalSlices = Mth.clamp((int)Math.ceil(endpointTravel / sliceSpacing), 1, 10);
            for (int slice = 1; slice < temporalSlices; ++slice) {
                double t = slice / (double)temporalSlices;
                if (best != null && best.score <= t) {
                    break;
                }
                Vec3 start = beforeStart.lerp(afterStart, t);
                Vec3 end = beforeEnd.lerp(afterEnd, t);
                double entry = segmentAabbEntry(start, end, expanded);
                if (!Double.isFinite(entry)) {
                    continue;
                }
                Vec3 contact = start.lerp(end, entry);
                if (best == null || t < best.score) {
                    best = new ProjectileContact(start, contact, t);
                }
            }
            return best;
        }
    }

    private record ProjectileContact(Vec3 traceStart, Vec3 contact, double score) {}

    private static Vec3 visualContactForStrike(AABB targetBox,
                                               SegmentProjectileCollider collider,
                                               ProjectileContact contact) {
        if (targetBox == null || collider == null || contact == null) {
            return contact == null ? Vec3.ZERO : contact.contact();
        }
        double time = Mth.clamp(contact.score(), 0.0D, 1.0D);
        Vec3 axisStart = collider.beforeStart().lerp(collider.afterStart(), time);
        Vec3 axisEnd = collider.beforeEnd().lerp(collider.afterEnd(), time);
        double entry = segmentAabbEntry(axisStart, axisEnd, targetBox);
        if (Double.isFinite(entry)) {
            return axisStart.lerp(axisEnd, Mth.clamp(entry, 0.0D, 1.0D));
        }
        return projectToAabbSurface(contact.contact(), targetBox);
    }

    private static Vec3 projectToAabbSurface(Vec3 point, AABB box) {
        double x = Mth.clamp(point.x, box.minX, box.maxX);
        double y = Mth.clamp(point.y, box.minY, box.maxY);
        double z = Mth.clamp(point.z, box.minZ, box.maxZ);
        boolean inside = point.x >= box.minX && point.x <= box.maxX
                && point.y >= box.minY && point.y <= box.maxY
                && point.z >= box.minZ && point.z <= box.maxZ;
        if (!inside) {
            return new Vec3(x, y, z);
        }

        double dMinX = point.x - box.minX;
        double dMaxX = box.maxX - point.x;
        double dMinY = point.y - box.minY;
        double dMaxY = box.maxY - point.y;
        double dMinZ = point.z - box.minZ;
        double dMaxZ = box.maxZ - point.z;
        double best = dMinX;
        int face = 0;
        if (dMaxX < best) { best = dMaxX; face = 1; }
        if (dMinY < best) { best = dMinY; face = 2; }
        if (dMaxY < best) { best = dMaxY; face = 3; }
        if (dMinZ < best) { best = dMinZ; face = 4; }
        if (dMaxZ < best) { face = 5; }
        return switch (face) {
            case 0 -> new Vec3(box.minX, y, z);
            case 1 -> new Vec3(box.maxX, y, z);
            case 2 -> new Vec3(x, box.minY, z);
            case 3 -> new Vec3(x, box.maxY, z);
            case 4 -> new Vec3(x, y, box.minZ);
            default -> new Vec3(x, y, box.maxZ);
        };
    }

    private static Vec3 contactSlashDirection(SegmentProjectileCollider collider) {
        if (collider == null) {
            return Vec3.ZERO;
        }
        Vec3 beforeMid = collider.beforeStart().lerp(collider.beforeEnd(), 0.5D);
        Vec3 afterMid = collider.afterStart().lerp(collider.afterEnd(), 0.5D);
        Vec3 trajectory = afterMid.subtract(beforeMid);
        if (trajectory.lengthSqr() > 1.0E-8D) {
            return trajectory.normalize();
        }
        Vec3 axis = collider.afterEnd().subtract(collider.afterStart());
        return axis.lengthSqr() > 1.0E-8D ? axis.normalize() : Vec3.ZERO;
    }

    private static AABB sweptBounds(Vec3[] before, Vec3[] after) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < POINTS; ++i) {
            minX = Math.min(minX, Math.min(before[i].x, after[i].x));
            minY = Math.min(minY, Math.min(before[i].y, after[i].y));
            minZ = Math.min(minZ, Math.min(before[i].z, after[i].z));
            maxX = Math.max(maxX, Math.max(before[i].x, after[i].x));
            maxY = Math.max(maxY, Math.max(before[i].y, after[i].y));
            maxZ = Math.max(maxZ, Math.max(before[i].z, after[i].z));
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static boolean overlapsInclusive(AABB a, AABB b) {
        return a.maxX >= b.minX && a.minX <= b.maxX
                && a.maxY >= b.minY && a.minY <= b.maxY
                && a.maxZ >= b.minZ && a.minZ <= b.maxZ;
    }

    private static double segmentAabbEntry(Vec3 start, Vec3 end, AABB box) {
        double[] range = {0.0D, 1.0D};
        Vec3 delta = end.subtract(start);
        if (!clipAxis(start.x, delta.x, box.minX, box.maxX, range)
                || !clipAxis(start.y, delta.y, box.minY, box.maxY, range)
                || !clipAxis(start.z, delta.z, box.minZ, box.maxZ, range)) {
            return Double.NaN;
        }
        return range[0];
    }

    private static boolean clipAxis(double start, double delta, double minimum, double maximum,
                                    double[] range) {
        if (Math.abs(delta) < 1.0E-10D) {
            return start >= minimum && start <= maximum;
        }
        double first = (minimum - start) / delta;
        double second = (maximum - start) / delta;
        if (first > second) {
            double swap = first;
            first = second;
            second = swap;
        }
        range[0] = Math.max(range[0], first);
        range[1] = Math.min(range[1], second);
        return range[0] <= range[1];
    }

    private static Vec3 resolveShockwaveOrigin(ServerLevel level, Vec3 impact) {
        double surface = findWhipRippleSurfaceY(level, Mth.floor(impact.x), Mth.floor(impact.z),
                impact.y, false);
        if (Double.isNaN(surface)) {
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    Mth.floor(impact.x), Mth.floor(impact.z));
            if (top > level.getMinBuildHeight() && top < level.getMaxBuildHeight()) {
                surface = top;
            }
        }
        return Double.isNaN(surface) ? impact : new Vec3(impact.x, surface, impact.z);
    }

    private static void startWhipShockwave(ServerLevel level, Player owner,
                                           Vec3 impact, float impactDamage) {

        Vec3 origin = resolveShockwaveOrigin(level, impact);
        long seed = owner.getUUID().getMostSignificantBits()
                ^ Long.rotateLeft(owner.getUUID().getLeastSignificantBits(), 19)
                ^ level.getGameTime() * 0x9E3779B97F4A7C15L;
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Mth.floor(origin.x), Mth.floor(origin.z));
        BlockPos skyProbe = BlockPos.containing(origin.x, origin.y + 1.0D, origin.z);
        boolean preferHeightmap = level.canSeeSky(skyProbe)
                || Math.abs(surfaceY - origin.y) <= 3.0D;
        ACTIVE_SHOCKWAVES.add(new WhipShockwavePulse(
                level, owner.getUUID(), level.getGameTime(), origin,
                impactDamage, preferHeightmap));
        WindwhispererWhipNetwork.sendShockwave(level, origin, seed);
        var landingSound = (((level.getGameTime() + owner.getId()) & 1L) == 0L)
                ? ModSounds.LANDING_QUAKE_1.get() : ModSounds.LANDING_QUAKE_2.get();

        level.playSound(null, origin.x, origin.y, origin.z, landingSound,
                SoundSource.PLAYERS, 32.0F, 1.0F);
        level.playSound(null, BlockPos.containing(origin), ModSounds.SHOCKWAVE.get(),
                SoundSource.PLAYERS, 0.75F, 1.12F);
    }

    private static void tickWhipShockwaves() {
        Iterator<WhipShockwavePulse> iterator = ACTIVE_SHOCKWAVES.iterator();
        while (iterator.hasNext()) {
            WhipShockwavePulse pulse = iterator.next();
            ServerLevel level = pulse.level;
            Entity ownerRaw = level.getEntity(pulse.ownerId);
            if (!(ownerRaw instanceof Player owner) || !owner.isAlive()) {
                iterator.remove();
                continue;
            }

            double age = level.getGameTime() - pulse.startGameTime;
            double maxAge = WHIP_RIPPLE_RADIUS * WHIP_RIPPLE_TICKS_PER_BLOCK
                    + WHIP_RIPPLE_WIDTH_TICKS + 1.0D;
            if (age > maxAge) {
                iterator.remove();
                continue;
            }

            double currentOuter = Math.min(WHIP_RIPPLE_RADIUS,
                    Math.max(0.0D, age / WHIP_RIPPLE_TICKS_PER_BLOCK + 0.5D));
            AABB area = new AABB(
                    pulse.impact.x - currentOuter - 2.0D, level.getMinBuildHeight(),
                    pulse.impact.z - currentOuter - 2.0D,
                    pulse.impact.x + currentOuter + 2.0D, level.getMaxBuildHeight(),
                    pulse.impact.z + currentOuter + 2.0D);
            for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, area,
                    living -> living.isAlive() && !isOwnerFriendly(owner, living))) {
                if (pulse.hitEntityIds.contains(victim.getId())) {
                    continue;
                }
                double dx = victim.getX() - pulse.impact.x;
                double dz = victim.getZ() - pulse.impact.z;
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance > WHIP_RIPPLE_RADIUS) {
                    continue;
                }
                double startDelay = Math.max(0.0D, distance - 0.5D)
                        * WHIP_RIPPLE_TICKS_PER_BLOCK;
                double endDelay = startDelay + WHIP_RIPPLE_WIDTH_TICKS;
                if (age < startDelay || age - 1.0D > endDelay) {
                    continue;
                }

                double surfaceY = findWhipRippleSurfaceY(
                        level, Mth.floor(victim.getX()), Mth.floor(victim.getZ()),
                        pulse.impact.y, pulse.preferHeightmap);
                if (Double.isNaN(surfaceY)) {
                    continue;
                }
                double feetAboveSurface = victim.getY() - surfaceY;
                if (feetAboveSurface < -0.20D
                        || feetAboveSurface > WHIP_RIPPLE_HEIGHT + 0.03D) {
                    continue;
                }

                pulse.hitEntityIds.add(victim.getId());
                victim.hurt(ModDamageTypes.royalSlimeWhip(owner), pulse.damage);
            }
        }
    }

    private static boolean isOwnerFriendly(Player owner, LivingEntity target) {
        if (target == owner || owner.isAlliedTo(target) || target.isAlliedTo(owner)) {
            return true;
        }

        if (target instanceof OwnableEntity ownable
                && owner.getUUID().equals(ownable.getOwnerUUID())) {
            return true;
        }

        return false;
    }

    private static double findWhipRippleSurfaceY(ServerLevel level, int x, int z,
                                                  double landingY, boolean preferHeightmap) {
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos(x, Mth.floor(landingY), z);
        if (!level.hasChunkAt(probe)) {
            return Double.NaN;
        }
        if (preferHeightmap) {
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            if (y >= level.getMinBuildHeight() && y < level.getMaxBuildHeight() - 1) {
                probe.set(x, y, z);
                BlockState state = level.getBlockState(probe);
                BlockState above = level.getBlockState(probe.above());
                if (isWhipRippleSurface(state, above)) {
                    return y + 1.0D;
                }
            }
        }

        int centerY = Mth.floor(landingY) - 1;
        int minY = Math.max(level.getMinBuildHeight(), centerY - 14);
        int maxY = Math.min(level.getMaxBuildHeight() - 2, centerY + 5);
        double bestY = Double.NaN;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int y = minY; y <= maxY; ++y) {
            probe.set(x, y, z);
            BlockState state = level.getBlockState(probe);
            BlockState above = level.getBlockState(probe.above());
            if (!isWhipRippleSurface(state, above)) {
                continue;
            }
            double surfaceY = y + 1.0D;
            double d = Math.abs(surfaceY - landingY);
            if (d < bestDistance) {
                bestDistance = d;
                bestY = surfaceY;
            }
        }
        return bestY;
    }

    private static boolean isWhipRippleSurface(BlockState state, BlockState above) {
        return !state.isAir() && state.canOcclude() && !above.canOcclude();
    }

    private static boolean damage(ServerLevel level, Player owner,
                                  Entity target, Vec3 contact, Vec3 slashDirection,
                                  float damageAmount, float unscaledDamageAmount) {
        if (!WhipEntityTargeting.mayDamageAtContact(owner, target, contact)) {
            return false;
        }
        if (target instanceof LivingEntity living) {
            return damage(level, owner, living, contact, slashDirection, damageAmount, unscaledDamageAmount);
        }
        return WhipEntityTargeting.hurtNonLiving(level, owner, target,
                owner.damageSources().playerAttack(owner), owner.getMainHandItem(), damageAmount);
    }

    private static boolean damage(ServerLevel level, Player owner,
                                  LivingEntity target, Vec3 contact, Vec3 slashDirection,
                                  float damageAmount, float unscaledDamageAmount) {
        ItemStack weapon = owner.getMainHandItem();
        DamageSource source = owner.damageSources().playerAttack(owner);

        float enchantedDamage = EnchantmentHelper.modifyDamage(
                level, weapon, target, source, damageAmount);
        float knockback = EnchantmentHelper.modifyKnockback(
                level, weapon, target, source, 0.00F);

        float beforeDamage = target.getHealth() + target.getAbsorptionAmount();
        Vec3 motionBefore = target.getDeltaMovement();
        boolean damaged = target.hurt(source, enchantedDamage);
        if (!damaged) return false;

        EnchantmentHelper.doPostAttackEffectsWithItemSource(level, target, source, weapon);
        target.setDeltaMovement(motionBefore);
        if (knockback > 0.0F) {
            target.knockback(knockback, owner.getX() - target.getX(), owner.getZ() - target.getZ());
        }
        target.hurtMarked = true;
        float actualDamage = Math.max(0.0F, beforeDamage
                - (target.getHealth() + target.getAbsorptionAmount()));
        WhipDamageDebug.record(owner, actualDamage);
        spawnHitLeaves(level, contact, slashDirection, actualDamage);
        return true;
    }

    private static void spawnHitLeaves(ServerLevel level, Vec3 contact, Vec3 slashDirection,
                                       float actualDamage) {
        int fragments = Mth.ceil(Math.max(0.0F, actualDamage));
        if (fragments <= 0 || contact == null || !finite(contact)) {
            return;
        }
        Vec3 slash = finite(slashDirection) && slashDirection.lengthSqr() > 1.0E-10D
                ? slashDirection.normalize() : Vec3.ZERO;
        for (int i = 0; i < fragments; ++i) {
            BlockState leaf = HIT_LEAF_STATES[level.random.nextInt(HIT_LEAF_STATES.length)];
            double x = contact.x + (level.random.nextDouble() - 0.5D) * 0.22D;
            double y = contact.y + (level.random.nextDouble() - 0.5D) * 0.18D;
            double z = contact.z + (level.random.nextDouble() - 0.5D) * 0.22D;
            double vx = slash.x * (0.04D + level.random.nextDouble() * 0.06D)
                    + (level.random.nextDouble() - 0.5D) * 0.08D;
            double vy = 0.05D + level.random.nextDouble() * 0.09D;
            double vz = slash.z * (0.04D + level.random.nextDouble() * 0.06D)
                    + (level.random.nextDouble() - 0.5D) * 0.08D;
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, leaf),
                    x, y, z, 0, vx, vy, vz, 1.0D);
        }
    }

    private static void commandOwnedPets(ServerLevel level, Player owner, LivingEntity target) {
        if (target == null || !target.isAlive() || isOwnerFriendly(owner, target)) {
            return;
        }
        AABB search = owner.getBoundingBox().inflate(PET_COMMAND_RADIUS);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, search, candidate ->
                candidate.isAlive() && candidate != target && candidate instanceof OwnableEntity)) {
            OwnableEntity ownable = (OwnableEntity) mob;
            if (owner.getUUID().equals(ownable.getOwnerUUID())) {
                mob.setTarget(target);
            }
        }
    }

    private static final class WhipShockwavePulse {
        final ServerLevel level;
        final UUID ownerId;
        final long startGameTime;
        final Vec3 impact;
        final float damage;
        final boolean preferHeightmap;
        final Set<Integer> hitEntityIds = new HashSet<>();

        WhipShockwavePulse(ServerLevel level, UUID ownerId, long startGameTime,
                           Vec3 impact, float damage, boolean preferHeightmap) {
            this.level = level;
            this.ownerId = ownerId;
            this.startGameTime = startGameTime;
            this.impact = impact;
            this.damage = damage;
            this.preferHeightmap = preferHeightmap;
        }
    }

    private record Strike(Entity target, Vec3 contact, int segmentIndex, double score, Vec3 slashDirection) {}
}
