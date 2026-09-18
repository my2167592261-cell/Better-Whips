package com.betterwhips.item;

import com.betterwhips.item.LightningWhipItem;
import com.betterwhips.item.WhipDamageDebug;
import com.betterwhips.item.WhipFriendlySupport;
import com.betterwhips.item.WhipMultiHitDamage;
import com.betterwhips.network.LightningWhipNetwork;
import com.betterwhips.physics.WhipBlockCollision;
import com.betterwhips.physics.WhipMomentumContinuity;
import com.betterwhips.registry.ModDamageTypes;
import com.betterwhips.registry.ModItems;
import com.betterwhips.registry.ModSounds;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
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
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class LightningWhipCombat {
    private static final int SEGMENTS = 56;
    private static final int POINTS = 57;
    private static final int MIN_SUBSTEPS = 8;
    private static final int MAX_SUBSTEPS = 24;
    private static final int SOLVER_ITERATIONS = 5;
    private static final int RIGHT_CHARGE_TICKS = 60;
    private static final int RIGHT_SLAM_TICKS = 14;
    private static final int PRECISION_WINDUP_TICKS = 1;
    private static final int PRECISION_STROKE_TICKS = 3;
    private static final double PRECISION_RELEASE_RAW = 0.3;
    private static final int LEFT_DAMAGE_WINDOW_TICKS = 10;
    private static final int LINGER_AFTER_COOLDOWN_TICKS = 12;
    private static final double RIGHT_SPIN_TURNS_AT_FULL_CHARGE = 4.0;
    private static final double CHARGE_SPIN_SIGN = 1.0;
    private static final double CHARGE_CENTRIFUGAL_SCALE = 0.12;
    private static final double CHARGE_MAX_RADIAL_ACCEL = 150.0;
    private static final double CHARGE_MAX_TANGENTIAL_ACCEL = 90.0;
    private static final double RELEASE_FORCE_STIFFNESS = 150.0;
    private static final double RELEASE_MAX_ACCEL = 240.0;
    private static final double RIGHT_TIP_MAX_SCALE = 10.0;
    private static final double WHIP_RIPPLE_RADIUS = 80.0;
    private static final double WHIP_RIPPLE_TICKS_PER_BLOCK = 0.3;
    private static final double WHIP_RIPPLE_HEIGHT = 0.75;
    private static final double PET_COMMAND_RADIUS = 64.0;
    private static final double WHIP_RIPPLE_WIDTH_TICKS = 4.0;
    private static final double TICK_SECONDS = 0.05;
    private static final double MAX_SEGMENT_STRETCH = 1.0030;

    private static final double PHYSICAL_REST_SCALE = 1.0D;
    private static final double LENGTH_COMPLIANCE = 3.0E-8;
    private static final double BEND_COMPLIANCE = 5.2E-7;
    private static final double TICK_VELOCITY_RETENTION = 0.9975;
    private static final double GRAVITY = 0.0;
    private static final double COLLISION_RADIUS = 0.025;
    private static final int DEPENETRATION_PASSES = 8;
    private static final double DEPENETRATION_EPSILON = 1.0E-4;
    private static final double SELF_COLLISION_DISTANCE = 0.040;
    private static final double HANDLE_ROOT_MAX_ANGLE = Math.toRadians(16.0);
    private static final double SNAPSHOT_ROOT_MAX_DISTANCE_SQR = 20.25;
    private static final double SNAPSHOT_SEGMENT_STRETCH_LIMIT = 1.12;
    private static final double SNAPSHOT_REACH_SLACK = 1.25;
    private static final int HANDLE_BEND_SEGMENTS = 4;
    private static final double HANDLE_BEND_STIFFNESS_MULTIPLIER = 1.45;
    private static final double SURFACE_TANGENT_RETENTION_PER_TICK = 0.96;

    private static final double PRECISION_GUIDE_END = 0.72;
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
    private static final int ATTACK_SPEED_STACK_DURATION_TICKS = 20;
    private static final int ATTACK_SPEED_MAX_STACKS = 5;
    private static final double ATTACK_SPEED_PER_STACK = 0.1;
    private static final ResourceLocation WHIP_ATTACK_SPEED_STACK_ID = ResourceLocation.fromNamespaceAndPath("better_whips", "lightning_whip_attack_speed_stack");
    private static final double[] SEGMENT_COLLIDER_RADIUS = com.betterwhips.physics.LightningWhipDimensions.colliderRadii();
    private static final double MAX_SEGMENT_COLLIDER_RADIUS = 0.05913;
    private static final double ENTITY_QUERY_CACHE_MARGIN = 0.85;
    private static final double[] AUTHORED_PIVOT_Z = new double[]{0.0, -3.0266, -6.0279, -9.0037, -11.9541, -14.8792, -17.7788, -20.6531, -23.5019, -26.3254, -29.1234, -31.8961, -34.6434, -37.3652, -40.0617, -42.7328, -45.3784, -47.9987, -50.5936, -53.1631, -55.7072, -58.2258, -60.7191, -63.187, -65.6295, -68.0466, -70.4383, -72.8046, -75.1455, -77.4611, -79.7512, -82.0159, -84.2552, -86.4691, -88.6577, -90.8208, -92.9585, -95.0709, -97.1578, -99.2193, -101.2555, -103.2662, -105.2516, -107.2115, -109.1461, -111.0552, -112.939, -114.7974, -116.6303, -118.4379, -120.2201, -121.9769, -123.7082, -125.4142, -127.0948, -128.75, -135.77};
    private static final double[] REST_LENGTHS = LightningWhipCombat.buildRestLengths();
    private static final Map<UUID, ServerLashState> ACTIVE = new HashMap<UUID, ServerLashState>();
    private static final int MAX_PENDING_PRECISION_INPUTS = 1;
    private static final double PRECISION_REAR_PLANE_MARGIN = 0.10;
    private static final double PRECISION_REAR_REQUIRED_LENGTH_FRACTION = 0.48;
    private static final double PRECISION_RETRACTED_TIP_DISTANCE_SQR = 1.35 * 1.35;
    private static final Map<UUID, ArrayDeque<PendingPrecisionRequest>> PENDING_PRECISION = new HashMap<UUID, ArrayDeque<PendingPrecisionRequest>>();
    private static final Map<UUID, Long> NEXT_PRECISION_TICK = new HashMap<UUID, Long>();
    private static final List<WhipShockwavePulse> ACTIVE_SHOCKWAVES = new ArrayList<WhipShockwavePulse>();
    private static final Set<UUID> TIP_SPEED_DEBUG = new HashSet<UUID>();
    private static final Map<UUID, AttackSpeedStackState> ATTACK_SPEED_STACKS = new HashMap<UUID, AttackSpeedStackState>();

    private LightningWhipCombat() {
    }

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
        LightningWhipCombat.beginChargedSlam(level, player, hand);
    }

    public static void beginChargedSlam(ServerLevel level, Player player, InteractionHand hand) {
        HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        LightningWhipCombat.startOrRetarget(level, player, hand, arm, LashMode.CHARGED_SLAM, 0);
    }

    public static void cancelChargedSlam(Player player) {
        if (player == null) {
            return;
        }
        ServerLashState state = ACTIVE.get(player.getUUID());
        if (state != null && state.mode == LashMode.CHARGED_SLAM && state.ageTicks < 60) {
            ACTIVE.remove(player.getUUID());
        }
    }

    public static void tryBeginPrecision(ServerPlayer player) {
        LightningWhipCombat.tryBeginPrecision(player, null);
    }

    public static void tryBeginPrecision(ServerPlayer player, PrecisionPoseSnapshot snapshot) {
        long nextAllowed;
        long now;
        if (player == null) {
            return;
        }
        UUID playerId = player.getUUID();
        if (!LightningWhipCombat.canStartPrecision(player)) {
            PENDING_PRECISION.remove(playerId);
            NEXT_PRECISION_TICK.remove(playerId);
            return;
        }
        if (!LightningWhipCombat.precisionPoseReady(player, snapshot)) {
            return;
        }
        ArrayDeque<PendingPrecisionRequest> queue =
                PENDING_PRECISION.computeIfAbsent(playerId, ignored -> new ArrayDeque<>());
        if (queue.size() < MAX_PENDING_PRECISION_INPUTS) {
            queue.addLast(new PendingPrecisionRequest(snapshot));
        }
        if ((now = player.serverLevel().getGameTime()) >= (nextAllowed = NEXT_PRECISION_TICK.getOrDefault(playerId, Long.MIN_VALUE).longValue()) && !queue.isEmpty()) {
            PendingPrecisionRequest next = (PendingPrecisionRequest)queue.removeFirst();
            if (queue.isEmpty()) {
                PENDING_PRECISION.remove(playerId);
            }
            LightningWhipCombat.beginPrecisionNow(player, next.snapshot);
        }
    }

    private static boolean canStartPrecision(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator()
                && player.getMainHandItem().is((Item)ModItems.LIGHTNING_WHIP.get());
    }

    private static boolean precisionPoseReady(ServerPlayer player, PrecisionPoseSnapshot snapshot) {
        if (snapshot == null) {
            return true;
        }
        Vec3[] points = snapshot.points();
        if (points == null || points.length != 57) {
            return false;
        }
        Vec3 root = points[0];
        Vec3 tip = points[56];
        if (!LightningWhipCombat.finite(root) || !LightningWhipCombat.finite(tip)) {
            return false;
        }
        if (tip.distanceToSqr(root) <= PRECISION_RETRACTED_TIP_DISTANCE_SQR) {
            return true;
        }
        Vec3 forward = LightningWhipCombat.basisForward(player);
        return LightningWhipCombat.rearLengthFraction(
                points, player.position(), forward, PRECISION_REAR_PLANE_MARGIN)
                >= PRECISION_REAR_REQUIRED_LENGTH_FRACTION;
    }

    private static void beginPrecisionNow(ServerPlayer player, PrecisionPoseSnapshot snapshot) {
        player.resetAttackStrengthTicker();
        int period = Math.max(1, LightningWhipItem.attackPeriodTicks(player));
        NEXT_PRECISION_TICK.put(player.getUUID(), player.serverLevel().getGameTime() + (long)period);
        LightningWhipCombat.startOrRetarget(player.serverLevel(), player, InteractionHand.MAIN_HAND, player.getMainArm(), LashMode.PRECISION, 10, snapshot);
    }

    private static void tickPendingPrecisionAttacks(ServerTickEvent.Post event) {
        Iterator<Map.Entry<UUID, ArrayDeque<PendingPrecisionRequest>>> iterator = PENDING_PRECISION.entrySet().iterator();
        while (iterator.hasNext()) {
            long nextAllowed;
            Map.Entry<UUID, ArrayDeque<PendingPrecisionRequest>> entry = iterator.next();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            ArrayDeque<PendingPrecisionRequest> queue = entry.getValue();
            if (player == null || !LightningWhipCombat.canStartPrecision(player) || queue.isEmpty()) {
                NEXT_PRECISION_TICK.remove(entry.getKey());
                iterator.remove();
                continue;
            }
            long now = player.serverLevel().getGameTime();
            if (now < (nextAllowed = NEXT_PRECISION_TICK.getOrDefault(entry.getKey(), Long.MIN_VALUE).longValue())) continue;
            PendingPrecisionRequest pending = queue.removeFirst();
            if (queue.isEmpty()) {
                iterator.remove();
            }
            LightningWhipCombat.beginPrecisionNow(player, pending.snapshot);
        }
    }

    private static void startOrRetarget(ServerLevel level, Player player, InteractionHand hand, HumanoidArm arm, LashMode mode, int cooldownTicks) {
        LightningWhipCombat.startOrRetarget(level, player, hand, arm, mode, cooldownTicks, null);
    }

    private static void startOrRetarget(ServerLevel level, Player player, InteractionHand hand, HumanoidArm arm, LashMode mode, int cooldownTicks, PrecisionPoseSnapshot snapshot) {
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
        LightningWhipCombat.tickAttackSpeedStacks(event);
        LightningWhipCombat.tickPendingPrecisionAttacks(event);
        LightningWhipChain.tick(event);
        Iterator<Map.Entry<UUID, ServerLashState>> iterator = ACTIVE.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ServerLashState> entry = iterator.next();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                iterator.remove();
                continue;
            }
            if (!entry.getValue().tick(player)) continue;
            iterator.remove();
        }
        LightningWhipCombat.tickWhipShockwaves();
        Iterator<UUID> debugIterator = TIP_SPEED_DEBUG.iterator();
        while (debugIterator.hasNext()) {
            UUID playerId = debugIterator.next();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(playerId);
            if (player == null) {
                debugIterator.remove();
                continue;
            }
            ServerLashState state = ACTIVE.get(playerId);
            double speed = state == null ? 0.0 : state.tipSpeedBlocksPerSecond();
            player.displayClientMessage(Component.literal(String.format(Locale.ROOT, "\u97ad\u68a2\u901f\u5ea6: %.2f \u683c/s", speed)), true);
        }
    }

    private static boolean finite(Vec3 value) {
        return value != null && Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
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
        return particle <= 0 ? 0.0 : 1.0 / LightningWhipCombat.particleMass(particle);
    }

    private static double maximumVerletStep(int particle, double substepSeconds) {
        double taper = (double)particle / 56.0;
        double maximumSpeed = 220.0 + 380.0 * Math.pow(taper, 1.55);
        return maximumSpeed * substepSeconds;
    }

    private static double precisionRawProgressAtLightningTick(double lightningTick) {
        double emeraldTick = Math.max(0.0, lightningTick) * 2.0;
        if (emeraldTick < 3.0) {
            return PRECISION_RELEASE_RAW * Mth.clamp(emeraldTick / 3.0, 0.0, 1.0);
        }
        double stroke = Mth.clamp((emeraldTick - 3.0) / 4.0, 0.0, 1.0);
        return Mth.lerp(stroke, PRECISION_RELEASE_RAW, 1.0);
    }

    private static double[] buildRestLengths() {
        return com.betterwhips.physics.LightningWhipDimensions.restLengthsDouble();
}

    private static Vec3 basisForward(Player player) {
        Vec3 forward = player.getViewVector(1.0f);
        forward = new Vec3(forward.x, 0.0, forward.z);
        if (forward.lengthSqr() < 1.0E-10) {
            forward = Vec3.directionFromRotation(0.0f, player.yBodyRot);
        }
        return new Vec3(forward.x, 0.0, forward.z).normalize();
    }

    private static Vec3 aimDirection(Player player) {
        Vec3 aim = player.getViewVector(1.0f);
        if (aim.lengthSqr() < 1.0E-10) {
            aim = Vec3.directionFromRotation(player.getXRot(), player.getYRot());
        }
        return aim.normalize();
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

    private static double chargedTipScaleForCharge(double chargeTicks) {
        double charge = LightningWhipCombat.rightChargeProgress(chargeTicks);
        return 1.0 + 9.0 * charge;
    }

    private static double chargedTipScaleAfterRelease(double releaseTicks) {
        if (releaseTicks <= 14.0) {
            return 10.0;
        }
        double shrink = Mth.clamp((releaseTicks - 14.0) / 6.0, 0.0, 1.0);
        return Mth.lerp(shrink, 10.0, 1.0);
    }

    private static Vec3 handBase(Player player, HumanoidArm arm, Vec3 attackDirection) {
        Vec3 horizontal = new Vec3(attackDirection.x, 0.0, attackDirection.z);
        horizontal = horizontal.lengthSqr() < 1.0E-10 ? LightningWhipCombat.basisForward(player) : horizontal.normalize();
        Vec3 right = new Vec3(-horizontal.z, 0.0, horizontal.x);
        double side = arm == HumanoidArm.RIGHT ? 1.0 : -1.0;
        return player.position().add(0.0, (double)player.getEyeHeight() - 0.58, 0.0).add(right.scale(side * 0.34)).add(horizontal.scale(0.1));
    }

    private static Vec3 restHandAnchor(Player player, HumanoidArm arm) {
        return LightningWhipCombat.handBase(player, arm, LightningWhipCombat.basisForward(player));
    }

    private static Vec3 chargedSpinCenter(Player player, HumanoidArm arm, Vec3 attackDirection) {
        Vec3 forward = new Vec3(attackDirection.x, 0.0, attackDirection.z);
        forward = forward.lengthSqr() < 1.0E-10 ? LightningWhipCombat.basisForward(player) : forward.normalize();
        return LightningWhipCombat.handBase(player, arm, forward).add(forward.scale(0.32)).add(0.0, 0.68, 0.0);
    }

    private static Vec3 chargedSpinHandAnchor(Player player, HumanoidArm arm, Vec3 attackDirection, double chargeTicks) {
        Vec3 forward = new Vec3(attackDirection.x, 0.0, attackDirection.z);
        forward = forward.lengthSqr() < 1.0E-10 ? LightningWhipCombat.basisForward(player) : forward.normalize();
        Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
        double side = arm == HumanoidArm.RIGHT ? 1.0 : -1.0;
        double charge = LightningWhipCombat.rightChargeProgress(chargeTicks);
        double lift = LightningWhipCombat.smoothstep(Math.min(1.0, charge / 0.12));
        double theta = 1.0 * LightningWhipCombat.rightSpinTurns(chargeTicks) * Math.PI * 2.0;
        double radius = 0.18 * LightningWhipCombat.smoothstep(Math.min(1.0, charge / 0.18));
        Vec3 center = LightningWhipCombat.chargedSpinCenter(player, arm, forward);
        Vec3 raisedOrbit = center.add(forward.scale(Math.cos(theta) * radius)).add(right.scale(Math.sin(theta) * radius * side));
        return LightningWhipCombat.restHandAnchor(player, arm).lerp(raisedOrbit, lift);
    }

    private static Vec3 chargedReleaseHandAnchor(Player player, HumanoidArm arm, Vec3 attackDirection, double releaseProgress) {
        Vec3 forward = new Vec3(attackDirection.x, 0.0, attackDirection.z);
        forward = forward.lengthSqr() < 1.0E-10 ? LightningWhipCombat.basisForward(player) : forward.normalize();
        Vec3 base = LightningWhipCombat.handBase(player, arm, forward);
        double t = LightningWhipCombat.smoothstep(releaseProgress);
        double theta = Mth.lerp(t, 1.5707963267948966, -0.36);
        double radius = 0.72;
        Vec3 center = base.add(forward.scale(0.34)).add(0.0, 0.05, 0.0);
        return center.add(forward.scale(Math.cos(theta) * radius)).add(0.0, Math.sin(theta) * radius, 0.0);
    }

    private static Vec3 precisionHandAnchor(Player player, HumanoidArm arm, Vec3 attackDirection, double rawProgress) {
        return LightningWhipCombat.precisionHandAnchor(player, arm, attackDirection, rawProgress, 1.0);
    }

    private static Vec3 precisionHandAnchor(Player player, HumanoidArm arm, Vec3 attackDirection, double rawProgress, double swingSign) {
        double lateralOffset;
        double verticalOffset;
        double forwardOffset;
        Vec3 aim = attackDirection.lengthSqr() < 1.0E-10 ? LightningWhipCombat.aimDirection(player) : attackDirection.normalize();
        Vec3 horizontal = new Vec3(aim.x, 0.0, aim.z);
        horizontal = horizontal.lengthSqr() < 1.0E-10 ? LightningWhipCombat.basisForward(player) : horizontal.normalize();
        Vec3 right = new Vec3(-horizontal.z, 0.0, horizontal.x);
        double side = arm == HumanoidArm.RIGHT ? 1.0 : -1.0;
        double sign = swingSign >= 0.0 ? 1.0 : -1.0;
        Vec3 base = LightningWhipCombat.handBase(player, arm, aim);
        double raw = Mth.clamp(rawProgress, 0.0, 1.0);
        if (raw < 0.3) {
            double t = LightningWhipCombat.smoothstep(raw / 0.3);
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
            double t = LightningWhipCombat.smoothstep((raw - 0.72) / 0.28);
            forwardOffset = Mth.lerp(t, 1.16, 0.28);
            verticalOffset = Mth.lerp(t, -0.18, 0.0);
            lateralOffset = Mth.lerp(t, -side * sign * 0.14, 0.0);
        }
        return base.add(aim.scale(forwardOffset)).add(0.0, verticalOffset, 0.0).add(right.scale(lateralOffset));
    }

    private static Vec3 drivenHandAnchor(Player player, HumanoidArm arm, LashMode mode, Vec3 attackDirection, double rawProgress) {
        return mode == LashMode.CHARGED_SLAM ? LightningWhipCombat.chargedSpinHandAnchor(player, arm, attackDirection, rawProgress * 60.0) : LightningWhipCombat.precisionHandAnchor(player, arm, attackDirection, rawProgress);
    }

    private static void depenetrateFromBlocks(ServerLevel level, Vec3[] points, Vec3[] previous, WhipBlockCollision.TickCache blockTickCache) {
        for (int i = 1; i < 57; ++i) {
            Vec3 push;
            for (int pass = 0; pass < 8 && (push = LightningWhipCombat.nearestDepenetration(level, points[i], 0.025, blockTickCache)) != null && !(push.lengthSqr() < 1.0E-14); ++pass) {
                Vec3 normal;
                Vec3 oldPoint = points[i];
                Vec3 corrected = oldPoint.add(push);
                Vec3 velocity = oldPoint.subtract(previous[i]);
                double normalVelocity = velocity.dot(normal = push.normalize());
                if (normalVelocity < 0.0) {
                    velocity = velocity.subtract(normal.scale(normalVelocity));
                }
                points[i] = corrected;
                previous[i] = corrected.subtract(velocity);
            }
        }
    }

    private static Vec3 nearestDepenetration(ServerLevel level, Vec3 point, double radius, WhipBlockCollision.TickCache blockTickCache) {
        Vec3 best = null;
        int minX = Mth.floor(point.x - radius);
        int minY = Mth.floor(point.y - radius);
        int minZ = Mth.floor(point.z - radius);
        int maxX = Mth.floor(point.x + radius);
        int maxY = Mth.floor(point.y + radius);
        int maxZ = Mth.floor(point.z + radius);
        ArrayList<AABB> solids = new ArrayList<AABB>();
        for (BlockPos blockPos : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
            for (AABB world : blockTickCache.collisionBoxes(level, blockPos)) {
                solids.add(world.inflate(radius));
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

    private static ProjectileContact findTipLivingImpact(ServerLevel level, Player player, Vec3 beforeStart, Vec3 beforeTip, Vec3 afterStart, Vec3 afterTip, double tipScale, TickEntityQueryCache entityQueries) {
        double radius = SEGMENT_COLLIDER_RADIUS[55] * Math.max(1.0, tipScale);
        AABB search = new AABB(beforeStart, beforeTip).minmax(new AABB(afterStart, afterTip)).inflate(radius + 0.25);
        SegmentProjectileCollider tipProjectile = new SegmentProjectileCollider(55, beforeStart, beforeTip, afterStart, afterTip, radius);
        ProjectileContact best = null;
        for (Entity living : entityQueries.living(level, player, search)) {
            ProjectileContact contact;
            AABB targetBox;
            if (!WhipEntityTargeting.canContact(player, living) || !(targetBox = living.getBoundingBox()).intersects(search) || (contact = tipProjectile.findContact(targetBox)) == null || !WhipEntityTargeting.mayDamageAtContact(player, living, contact.contact()) || best != null && !(contact.score < best.score)) continue;
            best = contact;
        }
        return best;
    }

    private static ProjectileBatch buildProjectileBatch(Vec3[] before, Vec3[] after, double tipScale) {
        SegmentProjectileCollider[] colliders = new SegmentProjectileCollider[56];
        AABB[] segmentSweeps = new AABB[56];
        double maxRadius = Math.max(0.05913, SEGMENT_COLLIDER_RADIUS[55] * tipScale);
        for (int i = 0; i < 56; ++i) {
            double radius = SEGMENT_COLLIDER_RADIUS[i] * (i == 55 ? tipScale : 1.0);
            colliders[i] = new SegmentProjectileCollider(i, before[i], before[i + 1], after[i], after[i + 1], radius);
            segmentSweeps[i] = new AABB(before[i], before[i + 1]).minmax(new AABB(after[i], after[i + 1])).inflate(radius + 0.006);
        }
        AABB core = LightningWhipCombat.sweptBounds(before, after);
        return new ProjectileBatch(colliders, segmentSweeps, core.inflate(maxRadius));
    }

    private static List<Strike> findProjectileContacts(ServerLevel level, Player player, ProjectileBatch batch, Set<UUID> alreadyContacted, TickEntityQueryCache entityQueries) {
        AABB search = batch.livingSearch();
        ArrayList<Strike> strikes = new ArrayList<Strike>();
        for (Entity candidate : entityQueries.living(level, player, search)) {
            AABB targetBox;
            if (!WhipEntityTargeting.canContact(player, candidate) || alreadyContacted.contains(WhipEntityTargeting.contactKey(candidate)) || !(targetBox = candidate.getBoundingBox()).intersects(search)) continue;
            ProjectileContact best = null;
            int bestSegment = -1;
            for (int i = 0; i < 56; ++i) {
                ProjectileContact contact;
                if (!LightningWhipCombat.overlapsInclusive(batch.segmentSweeps()[i], targetBox) || (contact = batch.colliders()[i].findContact(targetBox)) == null || best != null && !(contact.score < best.score)) continue;
                best = contact;
                bestSegment = i;
                if (best.score <= 0.0) break;
            }
            if (best == null || !WhipEntityTargeting.mayDamageAtContact(player, candidate, best.contact())) continue;
            strikes.add(new Strike(candidate, best.contact, bestSegment, best.score));
        }
        strikes.sort(Comparator.comparingDouble(Strike::score));
        Set<UUID> uniqueContactTargets = new HashSet<>();
        strikes.removeIf(strike -> !uniqueContactTargets.add(WhipEntityTargeting.contactKey(strike.target())));
        return strikes;
    }

    private static AABB sweptBounds(Vec3[] before, Vec3[] after) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < 57; ++i) {
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
        return a.maxX >= b.minX && a.minX <= b.maxX && a.maxY >= b.minY && a.minY <= b.maxY && a.maxZ >= b.minZ && a.minZ <= b.maxZ;
    }

    private static double segmentAabbEntry(Vec3 start, Vec3 end, AABB box) {
        double[] range = new double[]{0.0, 1.0};
        Vec3 delta = end.subtract(start);
        if (!(LightningWhipCombat.clipAxis(start.x, delta.x, box.minX, box.maxX, range) && LightningWhipCombat.clipAxis(start.y, delta.y, box.minY, box.maxY, range) && LightningWhipCombat.clipAxis(start.z, delta.z, box.minZ, box.maxZ, range))) {
            return Double.NaN;
        }
        return range[0];
    }

    private static boolean clipAxis(double start, double delta, double minimum, double maximum, double[] range) {
        if (Math.abs(delta) < 1.0E-10) {
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
        int top;
        double surface = LightningWhipCombat.findWhipRippleSurfaceY(level, Mth.floor(impact.x), Mth.floor(impact.z), impact.y, false);
        if (Double.isNaN(surface) && (top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(impact.x), Mth.floor(impact.z))) > level.getMinBuildHeight() && top < level.getMaxBuildHeight()) {
            surface = top;
        }
        return Double.isNaN(surface) ? impact : new Vec3(impact.x, surface, impact.z);
    }

    private static void startWhipShockwave(ServerLevel level, Player owner, Vec3 impact, float impactDamage) {
        Vec3 origin = LightningWhipCombat.resolveShockwaveOrigin(level, impact);
        long seed = owner.getUUID().getMostSignificantBits() ^ Long.rotateLeft(owner.getUUID().getLeastSignificantBits(), 19) ^ level.getGameTime() * -7046029254386353131L;
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(origin.x), Mth.floor(origin.z));
        BlockPos skyProbe = BlockPos.containing(origin.x, origin.y + 1.0, origin.z);
        boolean preferHeightmap = level.canSeeSky(skyProbe) || Math.abs((double)surfaceY - origin.y) <= 3.0;
        ACTIVE_SHOCKWAVES.add(new WhipShockwavePulse(level, owner.getUUID(), level.getGameTime(), origin, impactDamage, preferHeightmap));
        LightningWhipNetwork.sendShockwave(level, origin, seed);
        SoundEvent landingSound = (level.getGameTime() + (long)owner.getId() & 1L) == 0L ? ModSounds.LANDING_QUAKE_1.get() : ModSounds.LANDING_QUAKE_2.get();
        level.playSound(null, origin.x, origin.y, origin.z, landingSound, SoundSource.PLAYERS, 32.0f, 1.0f);
        level.playSound(null, BlockPos.containing(origin), ModSounds.SHOCKWAVE.get(), SoundSource.PLAYERS, 0.75f, 1.12f);
    }

    private static void tickWhipShockwaves() {
        Iterator<WhipShockwavePulse> iterator = ACTIVE_SHOCKWAVES.iterator();
        while (iterator.hasNext()) {
            double maxAge;
            Player owner;
            WhipShockwavePulse pulse = iterator.next();
            ServerLevel level = pulse.level;
            Entity ownerRaw = level.getEntity(pulse.ownerId);
            if (!(ownerRaw instanceof Player) || !(owner = (Player)ownerRaw).isAlive()) {
                iterator.remove();
                continue;
            }
            double age = level.getGameTime() - pulse.startGameTime;
            if (age > (maxAge = 29.0)) {
                iterator.remove();
                continue;
            }
            double currentOuter = Math.min(80.0, Math.max(0.0, age / 0.3 + 0.5));
            AABB area = new AABB(pulse.impact.x - currentOuter - 2.0, level.getMinBuildHeight(), pulse.impact.z - currentOuter - 2.0, pulse.impact.x + currentOuter + 2.0, level.getMaxBuildHeight(), pulse.impact.z + currentOuter + 2.0);
            for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, area, living -> living.isAlive() && !LightningWhipCombat.isOwnerFriendly(owner, living))) {
                double feetAboveSurface;
                double surfaceY;
                double dz;
                double dx;
                double distance;
                if (pulse.hitEntityIds.contains(victim.getId()) || (distance = Math.sqrt((dx = victim.getX() - pulse.impact.x) * dx + (dz = victim.getZ() - pulse.impact.z) * dz)) > 80.0) continue;
                double startDelay = Math.max(0.0, distance - 0.5) * 0.3;
                double endDelay = startDelay + 4.0;
                if (age < startDelay || age - 1.0 > endDelay || Double.isNaN(surfaceY = LightningWhipCombat.findWhipRippleSurfaceY(level, Mth.floor(victim.getX()), Mth.floor(victim.getZ()), pulse.impact.y, pulse.preferHeightmap)) || (feetAboveSurface = victim.getY() - surfaceY) < -0.2 || feetAboveSurface > 0.78) continue;
                pulse.hitEntityIds.add(victim.getId());
                victim.hurt(ModDamageTypes.royalSlimeWhip(owner), pulse.damage);
            }
        }
    }

    private static boolean isOwnerFriendly(Player owner, LivingEntity target) {
        if (target == owner || owner.isAlliedTo(target) || target.isAlliedTo(owner)) {
            return true;
        }
        if (target instanceof OwnableEntity) {
            OwnableEntity ownable = (OwnableEntity)((Object)target);
            if (owner.getUUID().equals(ownable.getOwnerUUID())) {
                return true;
            }
        }
        return false;
    }

    private static double findWhipRippleSurfaceY(ServerLevel level, int x, int z, double landingY, boolean preferHeightmap) {
        int y;
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos(x, Mth.floor(landingY), z);
        if (!level.hasChunkAt(probe)) {
            return Double.NaN;
        }
        if (preferHeightmap && (y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1) >= level.getMinBuildHeight() && y < level.getMaxBuildHeight() - 1) {
            probe.set(x, y, z);
            BlockState state = level.getBlockState(probe);
            BlockState above = level.getBlockState((BlockPos)probe.above());
            if (LightningWhipCombat.isWhipRippleSurface(state, above)) {
                return (double)y + 1.0;
            }
        }
        int centerY = Mth.floor(landingY) - 1;
        int minY = Math.max(level.getMinBuildHeight(), centerY - 14);
        int maxY = Math.min(level.getMaxBuildHeight() - 2, centerY + 5);
        double bestY = Double.NaN;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int y2 = minY; y2 <= maxY; ++y2) {
            double surfaceY;
            double d;
            probe.set(x, y2, z);
            BlockState state = level.getBlockState(probe);
            BlockState above = level.getBlockState((BlockPos)probe.above());
            if (!LightningWhipCombat.isWhipRippleSurface(state, above) || !((d = Math.abs((surfaceY = (double)y2 + 1.0) - landingY)) < bestDistance)) continue;
            bestDistance = d;
            bestY = surfaceY;
        }
        return bestY;
    }

    private static boolean isWhipRippleSurface(BlockState state, BlockState above) {
        return !state.isAir() && state.canOcclude() && !above.canOcclude();
    }

    private static void grantAttackSpeedStack(Player owner) {
        ServerPlayer player;
        if (!(owner instanceof ServerPlayer) || !(player = (ServerPlayer)owner).getMainHandItem().is((Item)ModItems.LIGHTNING_WHIP.get())) {
            return;
        }
        AttributeInstance attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
        if (attackSpeed == null) {
            return;
        }
        AttackSpeedStackState state = ATTACK_SPEED_STACKS.computeIfAbsent(player.getUUID(), ignored -> new AttackSpeedStackState());
        state.stacks = Math.min(5, state.stacks + 1);
        state.ticksRemaining = 20 * state.stacks;
        attackSpeed.addOrUpdateTransientModifier(new AttributeModifier(WHIP_ATTACK_SPEED_STACK_ID, 0.1 * (double)state.stacks, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        LightningWhipNetwork.sendAttackSpeedStacks(player, state.stacks);
    }

    private static void tickAttackSpeedStacks(ServerTickEvent.Post event) {
        Iterator<Map.Entry<UUID, AttackSpeedStackState>> iterator = ATTACK_SPEED_STACKS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, AttackSpeedStackState> entry = iterator.next();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                iterator.remove();
                continue;
            }
            AttributeInstance attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
            AttackSpeedStackState state = entry.getValue();
            if (player.getMainHandItem().is((Item)ModItems.LIGHTNING_WHIP.get()) && --state.ticksRemaining > 0) continue;
            if (attackSpeed != null) {
                attackSpeed.removeModifier(WHIP_ATTACK_SPEED_STACK_ID);
            }
            LightningWhipNetwork.sendAttackSpeedStacks(player, 0);
            iterator.remove();
        }
    }

    private static boolean damage(ServerLevel level, Player owner, Entity target, Vec3 contact,
                                  float damageAmount, ServerLashState lash) {
        if (!WhipEntityTargeting.mayDamageAtContact(owner, target, contact)) {
            return false;
        }
        if (target instanceof LivingEntity living) {
            return damage(level, owner, living, contact, damageAmount, lash);
        }
        DamageSource source = ModDamageTypes.lightningWhip(owner);
        boolean damaged = WhipEntityTargeting.hurtNonLiving(
                level, owner, target, source, owner.getMainHandItem(), damageAmount);
        if (damageAmount > 0.0F) {
            LightningWhipNetwork.sendHitGlowBurst(level, contact, level.random.nextLong());
        }
        LivingEntity parent = WhipEntityTargeting.livingParent(target);
        if (damaged && parent != null) {
            LightningWhipNetwork.sendDirectWrap(level, parent, level.random.nextLong());
            LightningWhipTimeStop.freezeNow(level, parent, level.getGameTime());
            if (damageAmount > 0.0F && lash != null && !lash.arcWaveSpawned) {
                lash.arcWaveSpawned = true;
                LightningWhipChain.start(level, owner, parent, damageAmount * 0.10F, level.random.nextLong());
            }
        }
        return damaged;
    }

    private static boolean damage(ServerLevel level, Player owner, LivingEntity target, Vec3 contact,
                                  float damageAmount, ServerLashState lash) {
        ItemStack weapon = owner.getMainHandItem();
        DamageSource source = ModDamageTypes.lightningWhip(owner);
        WhipFriendlySupport.Resolution friendly = WhipFriendlySupport.resolveAndApply(level, owner, target);

        if (friendly.friendly()) {
            float beforeDamage = target.getHealth() + target.getAbsorptionAmount();
            boolean damaged = false;
            if (friendly.friendlyPlayer() && damageAmount > 0.0F) {
                Vec3 motionBefore = target.getDeltaMovement();
                damaged = target.hurt(source, Math.min(1.0F, damageAmount));
                if (damaged) {
                    target.setDeltaMovement(motionBefore);
                    target.hurtMarked = true;

                    LightningWhipNetwork.sendDirectWrap(level, target, level.random.nextLong());
                    LightningWhipTimeStop.freezeNow(level, target, level.getGameTime());
                }
            }
            WhipDamageDebug.record(owner, Math.max(0.0F,
                    beforeDamage - (target.getHealth() + target.getAbsorptionAmount())));
            if (damageAmount > 0.0F) {
                LightningWhipNetwork.sendHitGlowBurst(level, contact, level.random.nextLong());
            }
            return true;
        }

        float enchantedDamage = EnchantmentHelper.modifyDamage(level, weapon, target, source, damageAmount);
        float beforeDamage = target.getHealth() + target.getAbsorptionAmount();
        Vec3 motionBefore = target.getDeltaMovement();
        boolean damaged = target.hurt(source, enchantedDamage);

        if (enchantedDamage > 0.0F) {
            LightningWhipNetwork.sendHitGlowBurst(level, contact, level.random.nextLong());
        }
        if (!damaged) return false;

        target.setDeltaMovement(motionBefore);
        target.hurtMarked = true;
        LightningWhipNetwork.sendDirectWrap(level, target, level.random.nextLong());
        LightningWhipTimeStop.freezeNow(level, target, level.getGameTime());

        if (enchantedDamage > 0.0F && lash != null && !lash.arcWaveSpawned) {
            lash.arcWaveSpawned = true;
            LightningWhipChain.start(level, owner, target, enchantedDamage * 0.10F, level.random.nextLong());
        }

        EnchantmentHelper.doPostAttackEffectsWithItemSource(level, target, source, weapon);
        WhipDamageDebug.record(owner, Math.max(0.0F,
                beforeDamage - (target.getHealth() + target.getAbsorptionAmount())));
        LightningWhipCombat.commandOwnedPets(level, owner, target);
        return true;
    }

    private static void commandOwnedPets(ServerLevel level, Player owner, LivingEntity target) {
        if (target == null || !target.isAlive() || LightningWhipCombat.isOwnerFriendly(owner, target)) {
            return;
        }
        AABB search = owner.getBoundingBox().inflate(64.0);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, search, candidate -> candidate.isAlive() && candidate != target && candidate instanceof OwnableEntity)) {
            OwnableEntity ownable = (OwnableEntity)((Object)mob);
            if (!owner.getUUID().equals(ownable.getOwnerUUID())) continue;
            mob.setTarget(target);
        }
    }

    private static enum LashMode {
        CHARGED_SLAM,
        PRECISION;

    }

    private static final class ServerLashState {
        private final ServerLevel originalLevel;
        private final InteractionHand hand;
        private final HumanoidArm arm;
        private final int ownerEntityId;
        private final Vec3[] points = new Vec3[57];
        private final Vec3[] previous = new Vec3[57];
        private final double[] lengthLambda = new double[56];
        private final double[] bendLambda = new double[55];
        private final Vec3[] precisionFollowDirections = new Vec3[POINTS];
        private final Vec3[] precisionFollowDirectionDelta = new Vec3[POINTS];
        private boolean precisionFollowInitialized;
        private LashMode mode = LashMode.PRECISION;
        private Vec3 attackDirection = new Vec3(0.0, 0.0, 1.0);
        private double precisionSwingSign = 1.0;
        private boolean precisionMomentumCarry;
        private Vec3 driveOffset = Vec3.ZERO;
        private Vec3 syncedHandleAxis = Vec3.ZERO;
        private Vec3 syncedPrecisionDirection = Vec3.ZERO;
        private int ageTicks;
        private int precisionWindupTicks = 3;
        private int precisionStrokeTicks = 4;
        private int attackWindowTicks = 20;
        private int lifetimeTicks = 32;
        private int rightCooldownTicks;
        private boolean rightCooldownApplied;
        private boolean rightReleased;
        private int rightReleaseTicks = -1;
        private boolean shockwaveTriggered;
        private boolean crackPlayed;

        private boolean hitSoundPlayed;
        private boolean arcWaveSpawned;
        private double tipSpeedBlocksPerSecond;
        private double substepSeconds = 0.00625;
        private double substepSecondsSqr = this.substepSeconds * this.substepSeconds;
        private double substepVelocityRetention = Math.pow(TICK_VELOCITY_RETENTION, 0.125);
        private double surfaceTangentRetention = Math.pow(SURFACE_TANGENT_RETENTION_PER_TICK, 0.125);
        private final Set<UUID> contactedTargets = new HashSet<UUID>();

        ServerLashState(ServerLevel level, Player player, InteractionHand hand, HumanoidArm arm) {
            this.originalLevel = level;
            this.hand = hand;
            this.arm = arm;
            this.ownerEntityId = player.getId();
            this.initialize(LightningWhipCombat.restHandAnchor(player, arm), player);
        }

        boolean matches(ServerLevel level, InteractionHand hand, HumanoidArm arm) {
            return this.originalLevel == level && this.hand == hand && this.arm == arm;
        }

        double tipSpeedBlocksPerSecond() {
            return this.tipSpeedBlocksPerSecond;
        }

        void clearSyncedPrecisionPose() {
            this.syncedHandleAxis = Vec3.ZERO;
            this.syncedPrecisionDirection = Vec3.ZERO;
        }

        void importPrecisionPose(Player player, PrecisionPoseSnapshot snapshot) {
            int i;
            this.clearSyncedPrecisionPose();
            if (snapshot == null || snapshot.points() == null || snapshot.previous() == null || snapshot.points().length != 57 || snapshot.previous().length != 57) {
                return;
            }
            double dt = snapshot.substepSeconds();
            if (!Double.isFinite(dt) || dt < 0.002082333333333333 || dt > 0.007143857142857144) {
                return;
            }
            Vec3[] incoming = snapshot.points();
            Vec3[] incomingPrevious = snapshot.previous();
            for (int i2 = 0; i2 < 57; ++i2) {
                if (LightningWhipCombat.finite(incoming[i2]) && LightningWhipCombat.finite(incomingPrevious[i2])) continue;
                return;
            }
            if (incoming[0].distanceToSqr(player.getEyePosition()) > 20.25) {
                return;
            }
            double cumulative = 0.0;
            Vec3 root = incoming[0];
            for (i = 0; i < 56; ++i) {
                double segmentLength = incoming[i].distanceTo(incoming[i + 1]);
                if (segmentLength > REST_LENGTHS[i] * 1.12 + 0.015) {
                    return;
                }
                cumulative += REST_LENGTHS[i];
                if (!(incoming[i + 1].distanceTo(root) > cumulative + 1.25)) continue;
                return;
            }
            for (i = 1; i < 57; ++i) {
                double maxStoredStep;
                double storedStep = incoming[i].distanceTo(incomingPrevious[i]);
                if (!(storedStep > (maxStoredStep = LightningWhipCombat.maximumVerletStep(i, dt) * 1.35 + 0.08))) continue;
                return;
            }
            for (i = 0; i < 57; ++i) {
                this.points[i] = incoming[i];
                this.previous[i] = incomingPrevious[i];
            }
            this.substepSeconds = dt;
            this.substepSecondsSqr = dt * dt;
            int steps = Mth.clamp((int)Math.round(0.05 / dt), 7, 24);
            this.substepVelocityRetention = Math.pow(TICK_VELOCITY_RETENTION, 1.0 / (double)steps);
            this.surfaceTangentRetention = Math.pow(SURFACE_TANGENT_RETENTION_PER_TICK, 1.0 / (double)steps);
            Vec3 axis = snapshot.handleAxis();
            this.syncedHandleAxis = LightningWhipCombat.finite(axis) && axis.lengthSqr() > 1.0E-10 ? axis.normalize() : Vec3.ZERO;
            Vec3 direction = snapshot.attackDirection();
            this.syncedPrecisionDirection = LightningWhipCombat.finite(direction) && direction.lengthSqr() > 1.0E-8 ? direction.normalize() : Vec3.ZERO;
        }

        void startAttack(Player player, LashMode mode, int cooldownTicks) {
            this.mode = mode;
            this.attackDirection = mode == LashMode.PRECISION ? (this.syncedPrecisionDirection.lengthSqr() > 1.0E-10 ? this.syncedPrecisionDirection : LightningWhipCombat.aimDirection(player)) : LightningWhipCombat.basisForward(player);
            WhipMomentumContinuity.Continuation continuation = mode == LashMode.PRECISION && this.syncedPrecisionDirection.lengthSqr() > 1.0E-10 ? WhipMomentumContinuity.analyze(this.points, this.previous, this.substepSeconds, this.attackDirection, this.arm) : WhipMomentumContinuity.Continuation.NONE;
            this.precisionSwingSign = continuation.swingSign();
            this.precisionMomentumCarry = continuation.active();
            this.ageTicks = 0;
            this.precisionFollowInitialized = false;
            if (mode == LashMode.PRECISION) {
                int attackPeriod = LightningWhipItem.attackPeriodTicks(player);

                this.precisionWindupTicks = Mth.clamp(
                        Math.min(PRECISION_WINDUP_TICKS, Math.max(1, attackPeriod - 2)),
                        1, PRECISION_WINDUP_TICKS);
                int availableStrokeTicks = Math.max(1, attackPeriod - this.precisionWindupTicks - 1);
                this.precisionStrokeTicks = Mth.clamp(
                        availableStrokeTicks, 1, PRECISION_STROKE_TICKS);
            } else {
                this.precisionWindupTicks = 3;
                this.precisionStrokeTicks = 4;
            }
            this.attackWindowTicks = mode == LashMode.PRECISION ? 10 : 0;
            this.rightCooldownTicks = 0;
            this.rightCooldownApplied = false;
            this.rightReleased = false;
            this.rightReleaseTicks = -1;
            this.shockwaveTriggered = false;
            this.lifetimeTicks = mode == LashMode.PRECISION ? this.precisionWindupTicks + this.attackWindowTicks + 12 : Integer.MAX_VALUE;
            this.crackPlayed = false;
            this.hitSoundPlayed = false;
            this.arcWaveSpawned = false;
            this.contactedTargets.clear();
            Vec3 canonicalStart = mode == LashMode.CHARGED_SLAM ? LightningWhipCombat.chargedSpinHandAnchor(player, this.arm, this.attackDirection, 0.0) : LightningWhipCombat.precisionHandAnchor(player, this.arm, this.attackDirection, 0.0, this.precisionSwingSign);
            this.driveOffset = this.points[0].subtract(canonicalStart);
        }

        boolean tick(ServerPlayer player) {

            double currentPrecision = 0.0D;
            double previousPrecision = 0.0D;
            boolean driveActive;
            if (player.serverLevel() != this.originalLevel || !player.isAlive()) {
                return true;
            }
            if (!player.getItemInHand(this.hand).is((Item)ModItems.LIGHTNING_WHIP.get())) {
                return true;
            }
            boolean chargingRight = false;
            if (this.mode == LashMode.CHARGED_SLAM && !this.rightReleased) {
                boolean stillUsing;
                boolean bl = stillUsing = player.isUsingItem() && player.getUsedItemHand() == this.hand && player.getUseItem().is((Item)ModItems.LIGHTNING_WHIP.get());
                if (!stillUsing) {
                    if (this.ageTicks < 60) {
                        return true;
                    }
                    this.rightReleased = true;
                    this.rightReleaseTicks = 0;
                    this.attackDirection = LightningWhipCombat.basisForward(player);
                    Vec3 releaseStart = LightningWhipCombat.chargedReleaseHandAnchor(player, this.arm, this.attackDirection, 0.0);
                    this.driveOffset = this.points[0].subtract(releaseStart);
                    this.rightCooldownApplied = true;
                    this.rightCooldownTicks = LightningWhipItem.attackPeriodTicks(player);
                    player.getCooldowns().addCooldown((Item)ModItems.LIGHTNING_WHIP.get(), this.rightCooldownTicks);
                    player.resetAttackStrengthTicker();
                    this.lifetimeTicks = Math.max(14, this.rightCooldownTicks) + 12;
                    this.arcWaveSpawned = false;
                    this.contactedTargets.clear();
                } else {
                    ++this.ageTicks;
                    chargingRight = true;
                    this.attackDirection = LightningWhipCombat.basisForward(player);
                }
            } else if (this.mode == LashMode.PRECISION) {
                ++this.ageTicks;
            }
            if (this.mode == LashMode.CHARGED_SLAM && this.rightReleased) {
                ++this.rightReleaseTicks;
            }
            boolean chargeDrive = this.mode == LashMode.CHARGED_SLAM && chargingRight;
            boolean releaseDrive = this.mode == LashMode.CHARGED_SLAM && this.rightReleased && this.rightReleaseTicks <= 14;
            boolean precisionMotion = this.mode == LashMode.PRECISION && this.ageTicks <= this.precisionWindupTicks + this.precisionStrokeTicks;

            boolean precisionDrive = precisionMotion;
            boolean bl = driveActive = chargeDrive || releaseDrive || precisionMotion;
            if (this.mode == LashMode.PRECISION) {
                previousPrecision = LightningWhipCombat.precisionRawProgressAtLightningTick(this.ageTicks - 1.0);
                currentPrecision = LightningWhipCombat.precisionRawProgressAtLightningTick(this.ageTicks);
            }
            double chargeFromTicks = Math.max(0.0, (double)this.ageTicks - 1.0);
            double chargeToTicks = Math.max(0.0, (double)this.ageTicks);
            double releaseFrom = Mth.clamp(((double)this.rightReleaseTicks - 1.0) / 14.0, 0.0, 1.0);
            double releaseTo = Mth.clamp((double)this.rightReleaseTicks / 14.0, 0.0, 1.0);
            Vec3 targetRoot = chargeDrive ? LightningWhipCombat.chargedSpinHandAnchor(player, this.arm, this.attackDirection, chargeToTicks).add(this.driveOffset) : (releaseDrive ? LightningWhipCombat.chargedReleaseHandAnchor(player, this.arm, this.attackDirection, releaseTo).add(this.driveOffset) : (precisionMotion ? LightningWhipCombat.precisionHandAnchor(player, this.arm, this.attackDirection, currentPrecision, this.precisionSwingSign).add(this.driveOffset) : LightningWhipCombat.restHandAnchor(player, this.arm)));
            Vec3 startRoot = this.points[0];
            Vec3 rootDelta = targetRoot.subtract(startRoot);
            if (rootDelta.lengthSqr() > 9.0) {
                for (int i = 0; i < 57; ++i) {
                    this.points[i] = this.points[i].add(rootDelta);
                    this.previous[i] = this.previous[i].add(rootDelta);
                }
                startRoot = targetRoot;
            }
            boolean damageActive = this.mode == LashMode.PRECISION ? this.ageTicks > this.precisionWindupTicks && this.ageTicks <= this.precisionWindupTicks + this.attackWindowTicks : this.rightReleased && this.rightCooldownApplied && this.rightReleaseTicks <= this.rightCooldownTicks;
            double tipTravelThisTick = 0.0;
            WhipBlockCollision.TickCache blockTickCache = new WhipBlockCollision.TickCache();
            TickEntityQueryCache entityQueries = new TickEntityQueryCache();
            LightningWhipCombat.depenetrateFromBlocks(this.originalLevel, this.points, this.previous, blockTickCache);
            int substeps = this.chooseAdaptiveSubsteps(chargeDrive, releaseDrive, precisionMotion);
            this.configureAdaptiveTimestep(substeps);
            for (int substep = 0; substep < substeps; ++substep) {
                Vec3 substepRoot;
                Vec3[] before = (Vec3[])this.points.clone();
                double alpha = ((double)substep + 1.0) / (double)substeps;
                double precisionProgress = 1.0;
                double chargeTicks = chargeToTicks;
                double releaseProgress = releaseTo;
                if (chargeDrive) {
                    chargeTicks = Mth.lerp(alpha, chargeFromTicks, chargeToTicks);
                    substepRoot = LightningWhipCombat.chargedSpinHandAnchor(player, this.arm, this.attackDirection, chargeTicks).add(this.driveOffset);
                } else if (releaseDrive) {
                    releaseProgress = Mth.lerp(alpha, releaseFrom, releaseTo);
                    substepRoot = LightningWhipCombat.chargedReleaseHandAnchor(player, this.arm, this.attackDirection, releaseProgress).add(this.driveOffset);
                } else if (precisionMotion) {
                    precisionProgress = Mth.lerp(alpha, previousPrecision, currentPrecision);
                    substepRoot = LightningWhipCombat.precisionHandAnchor(player, this.arm, this.attackDirection, precisionProgress, this.precisionSwingSign).add(this.driveOffset);
                } else {
                    substepRoot = startRoot.lerp(targetRoot, alpha);
                }
                this.predict(substepRoot);
                if (chargeDrive) {
                    this.applyChargeForces(player, this.attackDirection, chargeTicks);
                } else if (releaseDrive) {
                    this.applyReleaseForces(player, this.attackDirection, releaseProgress);
                } else if (precisionDrive && precisionProgress >= 0.0
                        && precisionProgress < PRECISION_GUIDE_END) {
                    this.applyPrecisionGuidance(player, substepRoot, precisionProgress,
                            this.precisionMomentumCarry);
                }
                double tipScale = 1.0;
                if (this.mode == LashMode.CHARGED_SLAM) {
                    tipScale = this.rightReleased ? LightningWhipCombat.chargedTipScaleAfterRelease((double)this.rightReleaseTicks - 1.0 + alpha) : LightningWhipCombat.chargedTipScaleForCharge(chargeTicks);
                }
                WhipBlockCollision.SegmentEnvironment[] blockBroadphase = WhipBlockCollision.buildBroadphase(this.originalLevel, before, this.points, SEGMENT_COLLIDER_RADIUS, tipScale, blockTickCache);
                Arrays.fill(this.lengthLambda, 0.0);
                Arrays.fill(this.bendLambda, 0.0);
                WhipBlockCollision.SegmentContact tipBlockContact = null;
                Vec3 tipBeforeBlockSolve = this.points[56];
                for (int iteration = 0; iteration < 5; ++iteration) {
                    this.points[0] = substepRoot;
                    for (int i = 0; i < 56; ++i) {
                        this.solveDistance(i, i + 1, REST_LENGTHS[i], LENGTH_COMPLIANCE, this.lengthLambda, i, false);
                    }
                    if (!precisionDrive) {
                        this.enforceHandleAxis(substepRoot);
                        this.enforceHandleBendZone();
                        this.enforceHandleContinuity();
                    }
                    this.enforceAntiFold();
                    this.enforceMaximumStretch();
                    if (iteration != 1) continue;
                    tipBeforeBlockSolve = this.points[56];
                    WhipBlockCollision.SegmentContact contact = this.solveBlockCapsuleContacts(blockBroadphase, before, tipScale, false);
                    if (contact == null) continue;
                    tipBlockContact = contact;
                }
                this.points[0] = substepRoot;
                this.solveSelfCollision();
                WhipBlockCollision.SegmentContact finalTipContact = this.solveBlockCapsuleContacts(blockBroadphase, before, tipScale, true);
                if (finalTipContact != null) {
                    tipBlockContact = finalTipContact;
                }
                Vec3 tipMotionBeforeCollision = tipBeforeBlockSolve.subtract(before[56]);
                ProjectileContact enlargedTipLivingHit = this.mode == LashMode.CHARGED_SLAM && this.rightReleased ? LightningWhipCombat.findTipLivingImpact(this.originalLevel, player, before[55], before[56], this.points[55], this.points[56], tipScale, entityQueries) : null;
                double currentTipSpeed = tipMotionBeforeCollision.length() / this.substepSeconds;
                tipTravelThisTick += before[56].distanceTo(this.points[56]);
                if (this.mode == LashMode.CHARGED_SLAM && this.rightReleased && this.rightReleaseTicks <= 14 && !this.shockwaveTriggered && (tipBlockContact != null || enlargedTipLivingHit != null)) {
                    this.shockwaveTriggered = true;
                    float impactDamage = LightningWhipItem.damageForSpeed(currentTipSpeed);
                    Vec3 impact = tipBlockContact != null && (enlargedTipLivingHit == null || tipBlockContact.toi() <= enlargedTipLivingHit.score()) ? tipBlockContact.surfacePoint() : enlargedTipLivingHit.contact();
                    LightningWhipCombat.startWhipShockwave(this.originalLevel, player, impact, impactDamage);
                }
                if (damageActive) {
                    ProjectileBatch projectileBatch = LightningWhipCombat.buildProjectileBatch(before, this.points, tipScale);
                    for (Strike strike : LightningWhipCombat.findProjectileContacts(this.originalLevel, player, projectileBatch, this.contactedTargets, entityQueries)) {
                        double sectionSpeed = projectileBatch.colliders()[strike.segmentIndex()].averageMaterialSpeed(this.substepSeconds);
                        float contactDamage = WhipMultiHitDamage.scale(LightningWhipItem.damageForSpeed(sectionSpeed), this.contactedTargets.size());
                        UUID targetId = WhipEntityTargeting.contactKey(strike.target);
                        if (contactDamage > 0.0f) {
                            this.contactedTargets.add(targetId);
                        }
                        if (!this.hitSoundPlayed) {
                            this.hitSoundPlayed = true;
                            this.originalLevel.playSound(null, strike.contact.x, strike.contact.y, strike.contact.z,
                                    ModSounds.LIGHTNING_WHIP_HIT.get(), SoundSource.PLAYERS, 1.0F,
                                    0.96F + this.originalLevel.random.nextFloat() * 0.08F);
                        }
                        if (!LightningWhipCombat.damage(this.originalLevel, player, strike.target, strike.contact, contactDamage, this)) continue;
                        this.contactedTargets.add(targetId);
                    }
                }
                if (!damageActive || this.crackPlayed) continue;
                this.crackPlayed = true;
                this.originalLevel.playSound(null, player.blockPosition(), ModSounds.WHIP_SWING.get(), SoundSource.PLAYERS, this.mode == LashMode.CHARGED_SLAM ? 1.05f : 0.95f, this.mode == LashMode.CHARGED_SLAM ? 0.82f : 1.32f);
            }
            this.tipSpeedBlocksPerSecond = tipTravelThisTick / 0.05;
            if (this.mode == LashMode.CHARGED_SLAM) {
                if (!this.rightReleased) {
                    return false;
                }
                return this.rightReleaseTicks >= this.lifetimeTicks;
            }
            return this.ageTicks >= this.lifetimeTicks;
        }

        private int chooseAdaptiveSubsteps(boolean chargeDrive, boolean releaseDrive, boolean precisionDrive) {
            double maxSpeed = 0.0;
            double previousDt = Math.max(1.0E-6, this.substepSeconds);
            for (int i = 1; i < 57; ++i) {
                maxSpeed = Math.max(maxSpeed, this.points[i].subtract(this.previous[i]).length() / previousDt);
            }
            int steps = maxSpeed < 28.0 ? 6 : (maxSpeed < 58.0 ? 8 : (maxSpeed < 105.0 ? 12 : (maxSpeed < 165.0 ? 18 : 24)));
            if (releaseDrive) {
                steps = Math.max(steps, 18);
            } else if (chargeDrive || precisionDrive) {
                steps = Math.max(steps, 12);
            }
            return Mth.clamp(steps, MIN_SUBSTEPS, MAX_SUBSTEPS);
        }

        private void configureAdaptiveTimestep(int substeps) {
            double newDt = 0.05 / (double)substeps;
            if (Math.abs(newDt - this.substepSeconds) > 1.0E-12) {
                double ratio = newDt / this.substepSeconds;
                for (int i = 1; i < 57; ++i) {
                    Vec3 velocityStep = this.points[i].subtract(this.previous[i]).scale(ratio);
                    this.previous[i] = this.points[i].subtract(velocityStep);
                }
            }
            this.substepSeconds = newDt;
            this.substepSecondsSqr = newDt * newDt;
            this.substepVelocityRetention = Math.pow(TICK_VELOCITY_RETENTION, 1.0 / (double)substeps);
            this.surfaceTangentRetention = Math.pow(SURFACE_TANGENT_RETENTION_PER_TICK, 1.0 / (double)substeps);
        }

        private WhipBlockCollision.SegmentContact solveBlockCapsuleContacts(WhipBlockCollision.SegmentEnvironment[] broadphase, Vec3[] before, double tipScale, boolean updateVelocity) {
            WhipBlockCollision.SegmentContact tipContact = null;
            for (int i = 0; i < 56; ++i) {
                double invB;
                double radius = SEGMENT_COLLIDER_RADIUS[i] * (i == 55 ? Math.max(1.0, tipScale) : 1.0);
                WhipBlockCollision.SegmentContact contact = WhipBlockCollision.findContact(broadphase[i], before[i], before[i + 1], this.points[i], this.points[i + 1], radius, updateVelocity ? this.surfaceTangentRetention : 1.0);
                if (contact == null || contact.correction().lengthSqr() < 1.0E-16) continue;
                double u = Mth.clamp(contact.sample(), 0.0, 1.0);
                double gradientA = 1.0 - u;
                double gradientB = u;
                double invA = LightningWhipCombat.inverseMass(i);
                double denominator = gradientA * gradientA * invA + gradientB * gradientB * (invB = LightningWhipCombat.inverseMass(i + 1));
                if (denominator <= 1.0E-12) continue;
                if (invA > 0.0) {
                    this.points[i] = this.points[i].add(contact.correction().scale(gradientA * invA / denominator));
                }
                if (invB > 0.0) {
                    this.points[i + 1] = this.points[i + 1].add(contact.correction().scale(gradientB * invB / denominator));
                }
                if (updateVelocity) {
                    this.dampInwardVelocity(i, contact.normal());
                    this.dampInwardVelocity(i + 1, contact.normal());
                }
                if (i != 55 || tipContact != null && !(contact.toi() < tipContact.toi())) continue;
                tipContact = contact;
            }
            return tipContact;
        }

        private void dampInwardVelocity(int index, Vec3 normal) {
            if (index <= 0) {
                return;
            }
            Vec3 velocity = this.points[index].subtract(this.previous[index]);
            double inward = velocity.dot(normal);
            if (inward < 0.0) {
                velocity = velocity.subtract(normal.scale(inward));
            }
            velocity = velocity.scale(this.surfaceTangentRetention);
            this.previous[index] = this.points[index].subtract(velocity);
        }

        private void initialize(Vec3 root, Player player) {
            this.precisionFollowInitialized = false;
            Vec3 forward = LightningWhipCombat.basisForward(player);
            Vec3 hanging = this.initialDirection(this.originalLevel, player, root, forward);
            this.points[0] = root;
            this.previous[0] = root;
            Vec3 cursor = root;
            for (int i = 0; i < 56; ++i) {
                this.points[i + 1] = cursor = cursor.add(hanging.scale(REST_LENGTHS[i]));
                this.previous[i + 1] = cursor;
            }
        }

        private Vec3 initialDirection(ServerLevel level, Player player, Vec3 root, Vec3 forward) {
            double clearance;
            double total = 0.0;
            for (double restLength : REST_LENGTHS) {
                total += restLength;
            }
            BlockHitResult floorHit = level.clip(new ClipContext(root, root.add(0.0, -total - 0.25, 0.0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (floorHit.getType() != HitResult.Type.MISS && (clearance = Math.max(0.1, root.y - floorHit.getLocation().y - 0.05)) < total * 0.92) {
                double vertical = Mth.clamp(clearance / total * 0.7, 0.08, 0.3);
                return new Vec3(forward.x, -vertical, forward.z).normalize();
            }
            return new Vec3(forward.x * 0.16, -0.987, forward.z * 0.16).normalize();
        }

        private void applyChargeForces(Player player, Vec3 forwardInput, double chargeTicks) {
            Vec3 forward = new Vec3(forwardInput.x, 0.0, forwardInput.z);
            forward = forward.lengthSqr() < 1.0E-10 ? LightningWhipCombat.basisForward(player) : forward.normalize();
            Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
            double side = this.arm == HumanoidArm.RIGHT ? 1.0 : -1.0;
            Vec3 center = LightningWhipCombat.chargedSpinCenter(player, this.arm, forward).add(this.driveOffset);
            double charge = LightningWhipCombat.rightChargeProgress(chargeTicks);
            double omega = LightningWhipCombat.rightSpinOmega(chargeTicks);
            double envelope = LightningWhipCombat.smoothstep(charge);
            for (int i = 1; i < 57; ++i) {
                Vec3 radial = this.points[i].subtract(center);
                radial = new Vec3(radial.x, 0.0, radial.z);
                double radius = radial.length();
                if (radius < 1.0E-5) continue;
                Vec3 radialDir = radial.scale(1.0 / radius);
                double forwardPart = radialDir.dot(forward);
                double rightPart = radialDir.dot(right);
                Vec3 tangentDir = forward.scale(-rightPart).add(right.scale(forwardPart)).scale(1.0 * side);
                if (tangentDir.lengthSqr() > 1.0E-10) {
                    tangentDir = tangentDir.normalize();
                }
                double taper = (double)i / 56.0;
                double weight = Math.pow(taper, 1.3) * envelope;
                double radialAccel = Math.min(150.0, omega * omega * radius * 0.12) * weight;
                Vec3 velocity = this.points[i].subtract(this.previous[i]);
                double tangentSpeed = velocity.dot(tangentDir) / this.substepSeconds;
                double desiredTangentSpeed = omega * radius;
                double tangentAccel = Mth.clamp((desiredTangentSpeed - tangentSpeed) * 8.0, -90.0, 90.0) * weight;
                Vec3 acceleration = radialDir.scale(radialAccel).add(tangentDir.scale(tangentAccel));
                this.points[i] = this.points[i].add(acceleration.scale(this.substepSecondsSqr));
            }
        }

        private void applyReleaseForces(Player player, Vec3 forwardInput, double releaseProgress) {
            Vec3 forward = new Vec3(forwardInput.x, 0.0, forwardInput.z);
            forward = forward.lengthSqr() < 1.0E-10 ? LightningWhipCombat.basisForward(player) : forward.normalize();
            Vec3 up = new Vec3(0.0, 1.0, 0.0);
            Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
            double release = LightningWhipCombat.smoothstep(releaseProgress);
            double planeBlend = LightningWhipCombat.smoothstep(Mth.clamp(releaseProgress / 0.3, 0.0, 1.0));
            Vec3 center = LightningWhipCombat.chargedSpinCenter(player, this.arm, forward).add(this.driveOffset);
            double pendulumAngle = release * Math.PI * 0.82;
            Vec3 verticalRadialDir = up.scale(Math.cos(pendulumAngle)).add(forward.scale(Math.sin(pendulumAngle))).normalize();
            Vec3 verticalTangentDir = forward.scale(Math.cos(pendulumAngle)).add(up.scale(-Math.sin(pendulumAngle))).normalize();
            double accumulatedLength = 0.0;
            for (int i = 1; i < 57; ++i) {
                double centripetalAccel;
                Vec3 acceleration;
                double accelLength;
                accumulatedLength += REST_LENGTHS[i - 1];
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
                double speedPerSecond = stepSpeed / Math.max(1.0E-6, this.substepSeconds);
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
                this.points[i] = this.points[i].add(acceleration.scale(this.substepSecondsSqr));
            }
        }

        private void predict(Vec3 root) {
            this.points[0] = root;
            this.previous[0] = root;
            for (int i = 1; i < 57; ++i) {
                double maximumStep;
                Vec3 current = this.points[i];
                Vec3 velocity = current.subtract(this.previous[i]).scale(this.substepVelocityRetention);
                double speed = velocity.length();
                if (speed > (maximumStep = LightningWhipCombat.maximumVerletStep(i, this.substepSeconds))) {
                    velocity = velocity.scale(maximumStep / speed);
                }
                this.previous[i] = current;
                this.points[i] = current.add(velocity).add(0.0, GRAVITY * this.substepSecondsSqr, 0.0);
            }
        }

        private void applyPrecisionGuidance(Player player, Vec3 root, double rawProgress,
                                            boolean momentumCarry) {
            Vec3 aim = this.attackDirection.lengthSqr() > 1.0E-10
                    ? this.attackDirection.normalize() : LightningWhipCombat.aimDirection(player);
            Vec3 center = player.getEyePosition();
            Vec3 sourceDirection = this.precisionCrosshairSweepDirection(
                    player, aim, rawProgress, this.precisionSwingSign);
            if (!this.precisionFollowInitialized) {
                this.initializePrecisionFollowDirections(center, sourceDirection);
            }

            double dt = Math.max(1.0E-5, this.substepSeconds);
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
                    ? Mth.lerp(LightningWhipCombat.smoothstep(Mth.clamp(rawProgress / 0.18, 0.0, 1.0)), 0.72, 1.0)
                    : 1.0;
            Vec3 rootTarget = center.add(sourceDirection.scale(rootRadius));
            Vec3 rootAcceleration = rootTarget.subtract(this.points[0])
                    .scale(PRECISION_FOLLOW_POSITION_ACCEL * 0.42);
            double rootAccelLength = rootAcceleration.length();
            if (rootAccelLength > PRECISION_FOLLOW_MAX_ACCEL * 0.55) {
                rootAcceleration = rootAcceleration.scale(
                        (PRECISION_FOLLOW_MAX_ACCEL * 0.55) / rootAccelLength);
            }
            this.points[0] = this.points[0].add(rootAcceleration.scale(this.substepSecondsSqr));

            for (int i = 1; i < POINTS; ++i) {
                accumulatedLength += REST_LENGTHS[i - 1] * PHYSICAL_REST_SCALE;
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
                this.points[i] = this.points[i].add(acceleration.scale(this.substepSecondsSqr));
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
                    ? attackDirection.normalize() : LightningWhipCombat.aimDirection(player);
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

        private void solveDistance(int a, int b, double rest, double compliance, double[] lambdas, int lambdaIndex, boolean minimumOnly) {
            Vec3 delta = this.points[b].subtract(this.points[a]);
            double distance = delta.length();
            if (distance < 1.0E-9 || minimumOnly && distance >= rest) {
                if (minimumOnly) {
                    lambdas[lambdaIndex] = 0.0;
                }
                return;
            }
            double weightA = LightningWhipCombat.inverseMass(a);
            double weightB = LightningWhipCombat.inverseMass(b);
            double alpha = compliance / this.substepSecondsSqr;
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

        private void enforceHandleAxis(Vec3 root) {
            double minimumDot;
            if (this.syncedHandleAxis.lengthSqr() < 1.0E-10) {
                return;
            }
            Vec3 segment = this.points[1].subtract(root);
            double length = segment.length();
            if (length < 1.0E-9) {
                return;
            }
            Vec3 axis = this.syncedHandleAxis.normalize();
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

        private void enforceAntiFold() {
            for (int i = 1; i < 56; ++i) {
                double adjacent = REST_LENGTHS[i - 1] + REST_LENGTHS[i];
                double taper = ((double)i - 1.0) / Math.max(1.0, 54.0);
                double minimumRatio = 0.28 + 0.62 * Math.pow(1.0 - taper, 1.55);
                this.solveDistance(i - 1, i + 1, adjacent * minimumRatio, BEND_COMPLIANCE, this.bendLambda, i - 1, true);
            }
        }

        private void enforceHandleBendZone() {
            int reinforced = Math.min(20, 55);
            for (int i = 1; i <= reinforced; ++i) {
                double adjacent = REST_LENGTHS[i - 1] + REST_LENGTHS[i];
                double t = ((double)i - 1.0) / Math.max(1.0, (double)reinforced - 1.0);
                double baseMinimumRatio = Mth.lerp(t, 0.97, 0.78);
                double minimumRatio = 1.0 - (1.0 - baseMinimumRatio) / HANDLE_BEND_STIFFNESS_MULTIPLIER;
                this.enforceMinimumSpan(i - 1, i + 1, adjacent * minimumRatio);
            }
        }

        private void enforceHandleContinuity() {
            int[] checkpoints = new int[]{3, 4, 6, 8, 12, 16};
            double[] ratios = new double[]{0.7, 0.58, 0.5, 0.42, 0.36, 0.32};
            double accumulated = 0.0;
            int next = 0;
            for (int i = 0; i < Math.min(20, 56); ++i) {
                accumulated += REST_LENGTHS[i];
                int count = i + 1;
                if (next >= checkpoints.length || count != checkpoints[next]) continue;
                this.enforceMinimumSpan(0, count, accumulated * ratios[next]);
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
            double weightA = LightningWhipCombat.inverseMass(a);
            double weightB = LightningWhipCombat.inverseMass(b);
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

        private void enforceMaximumStretch() {
            for (int i = 0; i < 56; ++i) {
                double maximum;
                Vec3 delta = this.points[i + 1].subtract(this.points[i]);
                double distance = delta.length();
                if (distance <= (maximum = REST_LENGTHS[i] * MAX_SEGMENT_STRETCH) || distance < 1.0E-9) continue;
                double weightA = LightningWhipCombat.inverseMass(i);
                double weightB = LightningWhipCombat.inverseMass(i + 1);
                double totalWeight = weightA + weightB;
                Vec3 correction = delta.scale((distance - maximum) / distance);
                if (weightA > 0.0) {
                    this.points[i] = this.points[i].add(correction.scale(weightA / totalWeight));
                }
                if (!(weightB > 0.0)) continue;
                this.points[i + 1] = this.points[i + 1].subtract(correction.scale(weightB / totalWeight));
            }
        }

        private void solveSelfCollision() {
            double minimum = SELF_COLLISION_DISTANCE;
            double minimumSqr = minimum * minimum;
            for (int i = 1; i < 57; ++i) {
                Vec3 a = this.points[i];
                for (int j = i + 3; j < 57; ++j) {
                    double weightB;
                    double distanceSqr;
                    double dz;
                    double dy;
                    Vec3 b = this.points[j];
                    double dx = b.x - a.x;
                    if (Math.abs(dx) >= minimum || Math.abs(dy = b.y - a.y) >= minimum || Math.abs(dz = b.z - a.z) >= minimum || (distanceSqr = dx * dx + dy * dy + dz * dz) >= minimumSqr || distanceSqr < 1.0E-12) continue;
                    double distance = Math.sqrt(distanceSqr);
                    double weightA = LightningWhipCombat.inverseMass(i);
                    double totalWeight = weightA + (weightB = LightningWhipCombat.inverseMass(j));
                    if (totalWeight <= 0.0) continue;
                    double scale = (minimum - distance) / distance;
                    Vec3 correction = new Vec3(dx * scale, dy * scale, dz * scale);
                    this.points[i] = this.points[i].add(correction.scale(-weightA / totalWeight));
                    this.points[j] = this.points[j].add(correction.scale(weightB / totalWeight));
                    a = this.points[i];
                }
            }
        }
    }

    public record PrecisionPoseSnapshot(Vec3[] points, Vec3[] previous, double substepSeconds, Vec3 handleAxis, Vec3 attackDirection) {
    }

    private static final class PendingPrecisionRequest {
        private final PrecisionPoseSnapshot snapshot;

        private PendingPrecisionRequest(PrecisionPoseSnapshot snapshot) {
            this.snapshot = snapshot;
        }
    }

    private record SegmentProjectileCollider(int segmentIndex, Vec3 beforeStart, Vec3 beforeEnd, Vec3 afterStart, Vec3 afterEnd, double radius) {
        private static final double[] MATERIAL_SAMPLES = new double[]{0.0, 0.125, 0.25, 0.375, 0.5, 0.625, 0.75, 0.875, 1.0};

        double averageMaterialSpeed(double dtSeconds) {
            double dt = Math.max(1.0E-6, dtSeconds);
            double startSpeed = this.beforeStart.distanceTo(this.afterStart) / dt;
            double endSpeed = this.beforeEnd.distanceTo(this.afterEnd) / dt;
            return (startSpeed + endSpeed) * 0.5;
        }

        ProjectileContact findContact(AABB target) {
            AABB swept = new AABB(this.beforeStart, this.beforeEnd).minmax(new AABB(this.afterStart, this.afterEnd)).inflate(this.radius + 0.006);
            if (!LightningWhipCombat.overlapsInclusive(swept, target)) {
                return null;
            }
            AABB expanded = target.inflate(this.radius + 0.006);
            ProjectileContact best = null;
            double beforeEntry = LightningWhipCombat.segmentAabbEntry(this.beforeStart, this.beforeEnd, expanded);
            if (Double.isFinite(beforeEntry)) {
                Vec3 contact = this.beforeStart.lerp(this.beforeEnd, beforeEntry);
                return new ProjectileContact(this.beforeStart, contact, 0.0);
            }
            double currentEntry = LightningWhipCombat.segmentAabbEntry(this.afterStart, this.afterEnd, expanded);
            if (Double.isFinite(currentEntry)) {
                Vec3 vec3 = this.afterStart.lerp(this.afterEnd, currentEntry);
                ProjectileContact current = new ProjectileContact(this.afterStart, vec3, 1.0);
                if (best == null || current.score < best.score) {
                    best = current;
                }
            }
            for (double sample : MATERIAL_SAMPLES) {
                Vec3 to;
                Vec3 from = this.beforeStart.lerp(this.beforeEnd, sample);
                double entry = LightningWhipCombat.segmentAabbEntry(from, to = this.afterStart.lerp(this.afterEnd, sample), expanded);
                if (!Double.isFinite(entry)) continue;
                Vec3 contact = from.lerp(to, entry);
                if (entry <= 0.0) {
                    return new ProjectileContact(from, contact, 0.0);
                }
                if (best != null && !(entry < best.score)) continue;
                best = new ProjectileContact(from, contact, entry);
            }
            double d = Math.max(this.beforeStart.distanceTo(this.afterStart), this.beforeEnd.distanceTo(this.afterEnd));
            double sliceSpacing = Math.max(0.035, this.radius * 0.85);
            int temporalSlices = Mth.clamp((int)Math.ceil(d / sliceSpacing), 1, 10);
            for (int slice = 1; slice < temporalSlices; ++slice) {
                Vec3 end;
                double t = (double)slice / (double)temporalSlices;
                if (best != null && best.score <= t) break;
                Vec3 start = this.beforeStart.lerp(this.afterStart, t);
                double entry = LightningWhipCombat.segmentAabbEntry(start, end = this.beforeEnd.lerp(this.afterEnd, t), expanded);
                if (!Double.isFinite(entry)) continue;
                Vec3 contact = start.lerp(end, entry);
                if (best != null && !(t < best.score)) continue;
                best = new ProjectileContact(start, contact, t);
            }
            return best;
        }
    }

    private static final class TickEntityQueryCache {
        private AABB livingBounds;
        private List<Entity> living = List.of();

        private TickEntityQueryCache() {
        }

        List<Entity> living(ServerLevel level, Player owner, AABB requested) {
            if (!TickEntityQueryCache.containsBounds(this.livingBounds, requested)) {
                this.livingBounds = TickEntityQueryCache.refreshBounds(this.livingBounds, requested);
                this.living = WhipEntityTargeting.query(level, owner, this.livingBounds);
            }
            return this.living;
        }

        private static AABB refreshBounds(AABB current, AABB requested) {
            AABB combined = current == null ? requested : current.minmax(requested);
            return combined.inflate(0.85);
        }

        private static boolean containsBounds(AABB outer, AABB inner) {
            return outer != null && inner.minX >= outer.minX && inner.maxX <= outer.maxX && inner.minY >= outer.minY && inner.maxY <= outer.maxY && inner.minZ >= outer.minZ && inner.maxZ <= outer.maxZ;
        }
    }

    private record ProjectileContact(Vec3 traceStart, Vec3 contact, double score) {
    }

    private record ProjectileBatch(SegmentProjectileCollider[] colliders, AABB[] segmentSweeps, AABB livingSearch) {
    }

    private record Strike(Entity target, Vec3 contact, int segmentIndex, double score) {
    }

    private static final class WhipShockwavePulse {
        final ServerLevel level;
        final UUID ownerId;
        final long startGameTime;
        final Vec3 impact;
        final float damage;
        final boolean preferHeightmap;
        final Set<Integer> hitEntityIds = new HashSet<Integer>();

        WhipShockwavePulse(ServerLevel level, UUID ownerId, long startGameTime, Vec3 impact, float damage, boolean preferHeightmap) {
            this.level = level;
            this.ownerId = ownerId;
            this.startGameTime = startGameTime;
            this.impact = impact;
            this.damage = damage;
            this.preferHeightmap = preferHeightmap;
        }
    }

    private static final class AttackSpeedStackState {
        private int stacks;
        private int ticksRemaining;

        private AttackSpeedStackState() {
        }
    }
}
