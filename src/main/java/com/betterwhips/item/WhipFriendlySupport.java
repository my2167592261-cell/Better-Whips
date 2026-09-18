package com.betterwhips.item;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class WhipFriendlySupport {
    private static final int PET_SPEED_I_TICKS = 5 * 20;
    private static final int PET_SPEED_II_TICKS = 8 * 20;
    private static final int PLAYER_HASTE_I_TICKS = 3 * 20;
    private static final int PLAYER_HASTE_II_TICKS = 5 * 20;

    private static final Map<SupportKey, SupportState> PET_STATES = new HashMap<>();
    private static final Map<SupportKey, SupportState> PLAYER_STATES = new HashMap<>();
    private static final Map<SupportKey, SupportState> TRAINER_PET_STATES = new HashMap<>();
    private static final Map<SupportKey, SupportState> TRAINER_PLAYER_STATES = new HashMap<>();

    private WhipFriendlySupport() {}

    public enum Kind { HOSTILE, PET, PLAYER, OTHER_FRIENDLY }

    public record Resolution(Kind kind) {
        public boolean friendly() { return kind != Kind.HOSTILE; }
        public boolean friendlyPlayer() { return kind == Kind.PLAYER; }
    }

    public static Resolution classify(ServerLevel level, Player owner, LivingEntity target) {
        if (owner == null || target == null || target == owner) {
            return new Resolution(Kind.OTHER_FRIENDLY);
        }

        if (target instanceof Player playerTarget) {
            if (!samePlayerSide(owner, playerTarget)) {
                return new Resolution(Kind.HOSTILE);
            }
            return new Resolution(Kind.PLAYER);
        }

        if (target instanceof OwnableEntity ownable && isFriendlyPet(level, owner, target, ownable)) {
            return new Resolution(Kind.PET);
        }

        if (owner.isAlliedTo(target) || target.isAlliedTo(owner)) {
            return new Resolution(Kind.OTHER_FRIENDLY);
        }
        return new Resolution(Kind.HOSTILE);
    }

    public static Resolution resolveAndApply(ServerLevel level, Player owner, LivingEntity target) {
        Resolution resolution = classify(level, owner, target);
        if (resolution.kind() == Kind.PLAYER && target instanceof Player playerTarget) {
            applyPlayerHaste(level, owner, playerTarget);
        } else if (resolution.kind() == Kind.PET) {
            applyPetSpeed(level, owner, target);
        }
        return resolution;
    }

    public static Resolution resolveAndApplyTrainer(ServerLevel level, Player owner, LivingEntity target) {
        Resolution resolution = classify(level, owner, target);
        if (resolution.kind() == Kind.PLAYER && target instanceof Player playerTarget) {
            applyTrainerPlayerHaste(level, owner, playerTarget);
        } else if (resolution.kind() == Kind.PET) {
            applyTrainerPetSpeed(level, owner, target);
        }
        return resolution;
    }

    private static boolean samePlayerSide(Player owner, Player target) {

        return Objects.equals(owner.getTeam(), target.getTeam());
    }

    private static boolean isFriendlyPet(ServerLevel level, Player owner, LivingEntity target,
                                         OwnableEntity ownable) {
        UUID petOwnerId = ownable.getOwnerUUID();
        if (owner.getUUID().equals(petOwnerId)) {
            return true;
        }
        if (petOwnerId != null) {
            ServerPlayer petOwner = level.getServer().getPlayerList().getPlayer(petOwnerId);
            if (petOwner != null && samePlayerSide(owner, petOwner)) {
                return true;
            }
        }
        return owner.isAlliedTo(target) || target.isAlliedTo(owner);
    }

    private static void applyPetSpeed(ServerLevel level, Player owner, LivingEntity pet) {
        SupportKey key = new SupportKey(owner.getUUID(), pet.getUUID());
        int now = level.getServer().getTickCount();
        SupportState previous = PET_STATES.get(key);
        boolean secondStage = previous != null && previous.expiryTick >= now;
        int stage = secondStage ? 2 : 1;
        int duration = stage == 1 ? PET_SPEED_I_TICKS : PET_SPEED_II_TICKS;
        PET_STATES.put(key, new SupportState(stage, now + duration));
        pet.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, duration, stage - 1));
    }

    private static void applyPlayerHaste(ServerLevel level, Player owner, Player target) {
        SupportKey key = new SupportKey(owner.getUUID(), target.getUUID());
        int now = level.getServer().getTickCount();
        SupportState previous = PLAYER_STATES.get(key);
        boolean secondStage = previous != null && previous.expiryTick >= now;
        int stage = secondStage ? 2 : 1;
        int duration = stage == 1 ? PLAYER_HASTE_I_TICKS : PLAYER_HASTE_II_TICKS;
        PLAYER_STATES.put(key, new SupportState(stage, now + duration));
        target.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, duration, stage - 1));
    }

    private static void applyTrainerPetSpeed(ServerLevel level, Player owner, LivingEntity pet) {
        SupportKey key = new SupportKey(owner.getUUID(), pet.getUUID());
        int now = level.getServer().getTickCount();
        SupportState previous = TRAINER_PET_STATES.get(key);
        boolean secondStage = previous != null && previous.expiryTick >= now;
        int stage = secondStage ? 2 : 1;
        int duration = stage == 1 ? PET_SPEED_I_TICKS : PET_SPEED_II_TICKS;
        TRAINER_PET_STATES.put(key, new SupportState(stage, now + duration));

        pet.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, duration, stage));
    }

    private static void applyTrainerPlayerHaste(ServerLevel level, Player owner, Player target) {
        SupportKey key = new SupportKey(owner.getUUID(), target.getUUID());
        int now = level.getServer().getTickCount();
        SupportState previous = TRAINER_PLAYER_STATES.get(key);
        boolean secondStage = previous != null && previous.expiryTick >= now;
        int stage = secondStage ? 2 : 1;
        int duration = stage == 1 ? PLAYER_HASTE_I_TICKS : PLAYER_HASTE_II_TICKS;
        TRAINER_PLAYER_STATES.put(key, new SupportState(stage, now + duration));

        target.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, duration, stage));
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        int now = event.getServer().getTickCount();
        prune(PET_STATES, now);
        prune(PLAYER_STATES, now);
        prune(TRAINER_PET_STATES, now);
        prune(TRAINER_PLAYER_STATES, now);
    }

    private static void prune(Map<SupportKey, SupportState> map, int now) {
        Iterator<Map.Entry<SupportKey, SupportState>> iterator = map.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().expiryTick < now) {
                iterator.remove();
            }
        }
    }

    private record SupportKey(UUID owner, UUID target) {}
    private record SupportState(int stage, int expiryTick) {}
}
