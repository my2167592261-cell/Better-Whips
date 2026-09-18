package com.betterwhips.item;

import com.betterwhips.network.LightningWhipNetwork;
import com.betterwhips.network.LightningWhipNetwork.ArcTarget;
import com.betterwhips.registry.ModDamageTypes;
import com.betterwhips.registry.ModSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class LightningWhipChain {
    public static final double HOP_RANGE = 10.0;
    public static final int MAX_HOPS = 6;
    public static final int HOP_INTERVAL_TICKS = 4;
    public static final int ROUTE_ENDPOINTS = MAX_HOPS + 1;

    public static final int ARC_CONTROL_COOLDOWN_TICKS = 4;
    private static final int MAX_ACTIVE_CHAINS = 128;
    private static final int MAX_CONTROL_COOLDOWN_ENTRIES = 2048;
    private static final List<ActiveChain> ACTIVE = new ArrayList<>();
    private static final Map<ArcControlKey, Long> ARC_CONTROL_READY_AT = new HashMap<>();

    private LightningWhipChain() {}

    public static void start(ServerLevel level, Player owner, LivingEntity first,
                             float arcDamage, long seed) {
        if (level == null || owner == null || first == null || !first.isAlive() || !(arcDamage > 0.0F)) return;
        List<LivingEntity> cycle = selectCycle(level, owner, first);
        if (cycle.isEmpty()) return;

        ArrayList<ArcTarget> route = new ArrayList<>(ROUTE_ENDPOINTS);
        int[] routeEntityIds = new int[ROUTE_ENDPOINTS];

        for (int i = 0; i < ROUTE_ENDPOINTS; ++i) {
            LivingEntity endpoint = cycle.get(i % cycle.size());
            route.add(ArcTarget.of(endpoint));
            routeEntityIds[i] = endpoint.getId();
        }

        if (ACTIVE.size() >= MAX_ACTIVE_CHAINS) ACTIVE.removeFirst();
        ACTIVE.add(new ActiveChain(level, owner.getUUID(), level.getGameTime(),
                routeEntityIds, arcDamage, 1));
        LightningWhipNetwork.sendArcRoute(level, route, seed);
    }

    public static void tick(ServerTickEvent.Post event) {
        Iterator<ActiveChain> iterator = ACTIVE.iterator();
        while (iterator.hasNext()) {
            ActiveChain chain = iterator.next();
            long now = chain.level.getGameTime();
            ServerPlayer owner = event.getServer().getPlayerList().getPlayer(chain.ownerId);
            if (owner == null || owner.serverLevel() != chain.level) {
                iterator.remove();
                continue;
            }

            while (chain.nextHop <= MAX_HOPS
                    && now >= chain.startGameTime + (long)chain.nextHop * HOP_INTERVAL_TICKS) {
                int targetId = chain.routeEntityIds[chain.nextHop];
                Entity raw = chain.level.getEntity(targetId);
                if (raw instanceof LivingEntity target && target.isAlive() && target != owner
                        && !WhipFriendlySupport.classify(chain.level, owner, target).friendly()) {
                    playArcLightningSound(chain.level, target);
                    applyArcDamage(chain.level, owner, target, chain.damage, now);
                }
                ++chain.nextHop;
            }
            if (chain.nextHop > MAX_HOPS) iterator.remove();
        }
    }

    private static void playArcLightningSound(ServerLevel level, LivingEntity target) {
        Vec3 center = target.getBoundingBox().getCenter();
        level.playSound(null, center.x, center.y, center.z,
                ModSounds.LIGHTNING_ARC.get(), SoundSource.PLAYERS,
                0.46F, 0.96F + level.random.nextFloat() * 0.08F);
    }

    private static void applyArcDamage(ServerLevel level, Player owner, LivingEntity target,
                                       float damage, long now) {
        if (!(damage > 0.0F)) return;
        Vec3 motionBefore = target.getDeltaMovement();
        float beforeDamage = target.getHealth() + target.getAbsorptionAmount();

        int invulnerableBefore = target.invulnerableTime;
        target.invulnerableTime = 0;
        boolean hurt = target.hurt(ModDamageTypes.lightningWhip(owner), damage);
        int invulnerableAfterArc = target.invulnerableTime;
        target.invulnerableTime = Math.max(invulnerableBefore, invulnerableAfterArc);
        if (!hurt) return;

        target.setDeltaMovement(motionBefore);
        target.hurtMarked = true;

        if (claimArcControlWindow(level, target, now)) {
            LightningWhipNetwork.sendDirectWrap(level, target, level.random.nextLong());
            LightningWhipTimeStop.freezeNow(level, target, now);
        }
        WhipDamageDebug.record(owner, Math.max(0.0F,
                beforeDamage - (target.getHealth() + target.getAbsorptionAmount())));
    }

    private static boolean claimArcControlWindow(ServerLevel level, LivingEntity target, long now) {
        ArcControlKey key = new ArcControlKey(level.dimension(), target.getUUID());
        Long readyAt = ARC_CONTROL_READY_AT.get(key);
        if (readyAt != null && now < readyAt) return false;
        ARC_CONTROL_READY_AT.put(key, now + ARC_CONTROL_COOLDOWN_TICKS);
        if (ARC_CONTROL_READY_AT.size() > MAX_CONTROL_COOLDOWN_ENTRIES) {
            ARC_CONTROL_READY_AT.entrySet().removeIf(entry -> entry.getValue() + 40L < now);
        }
        return true;
    }

    private static List<LivingEntity> selectCycle(ServerLevel level, Player owner, LivingEntity first) {
        ArrayList<LivingEntity> targets = new ArrayList<>();
        Set<Integer> visited = new HashSet<>();
        LivingEntity current = first;
        targets.add(first);
        visited.add(first.getId());

        for (int hop = 0; hop < MAX_HOPS; ++hop) {
            Vec3 center = current.getBoundingBox().getCenter();
            AABB area = new AABB(center, center).inflate(HOP_RANGE);
            LivingEntity nearest = null;
            double nearestDistance = HOP_RANGE * HOP_RANGE;
            for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, area,
                    entity -> entity != owner && entity.isAlive() && !entity.isSpectator()
                            && !entity.isInvulnerable() && !visited.contains(entity.getId())
                            && !WhipFriendlySupport.classify(level, owner, entity).friendly())) {
                Vec3 destination = candidate.getBoundingBox().getCenter();
                double distance = center.distanceToSqr(destination);
                if (distance > nearestDistance) continue;
                if (distance == nearestDistance && nearest != null && candidate.getId() > nearest.getId()) continue;
                if (level.clip(new ClipContext(center, destination, ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE, current)).getType() != HitResult.Type.MISS) continue;
                nearest = candidate;
                nearestDistance = distance;
            }
            if (nearest == null) break;
            targets.add(nearest);
            visited.add(nearest.getId());
            current = nearest;
        }
        return targets;
    }

    private record ArcControlKey(ResourceKey<Level> dimension, UUID targetId) {}

    private static final class ActiveChain {
        final ServerLevel level;
        final UUID ownerId;
        final long startGameTime;
        final int[] routeEntityIds;
        final float damage;
        int nextHop;

        ActiveChain(ServerLevel level, UUID ownerId, long startGameTime,
                    int[] routeEntityIds, float damage, int nextHop) {
            this.level = level;
            this.ownerId = ownerId;
            this.startGameTime = startGameTime;
            this.routeEntityIds = routeEntityIds;
            this.damage = damage;
            this.nextHop = nextHop;
        }
    }
}
