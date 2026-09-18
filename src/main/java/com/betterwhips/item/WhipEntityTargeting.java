package com.betterwhips.item;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;

import java.util.List;
import java.util.UUID;

public final class WhipEntityTargeting {
    public static final double CROSSHAIR_ALL_TARGETS_ANGLE_DEGREES = 15.0D;
    private static final double CROSSHAIR_ALL_TARGETS_COS = Math.cos(Math.toRadians(CROSSHAIR_ALL_TARGETS_ANGLE_DEGREES));

    private WhipEntityTargeting() {}

    public static boolean canContact(Player owner, Entity entity) {
        if (entity == null || entity == owner || entity.isRemoved() || entity.isSpectator() || !entity.isAlive()) {
            return false;
        }

        if (entity instanceof LivingEntity) {
            return true;
        }

        return entity.isPickable() && entity.isAttackable() && !entity.skipAttackInteraction(owner);
    }

    public static List<Entity> query(ServerLevel level, Player owner, AABB bounds) {
        return level.getEntities(owner, bounds, entity -> canContact(owner, entity));
    }

    public static boolean mayDamageAtContact(Player owner, Entity target, Vec3 contact) {
        if (owner == null || target == null) {
            return false;
        }
        if (isHostileType(target)) {
            return true;
        }
        if (contact == null || !Double.isFinite(contact.x + contact.y + contact.z)) {
            return false;
        }

        Vec3 fromEye = contact.subtract(owner.getEyePosition());
        double lengthSqr = fromEye.lengthSqr();
        if (!(lengthSqr > 1.0E-8D) || !Double.isFinite(lengthSqr)) {
            return true;
        }
        Vec3 look = owner.getLookAngle();
        double lookLengthSqr = look.lengthSqr();
        if (!(lookLengthSqr > 1.0E-8D) || !Double.isFinite(lookLengthSqr)) {
            return false;
        }
        double dot = fromEye.dot(look) / Math.sqrt(lengthSqr * lookLengthSqr);
        return Double.isFinite(dot) && dot >= CROSSHAIR_ALL_TARGETS_COS;
    }

    public static boolean isHostileType(Entity entity) {
        Entity root = logicalParent(entity);
        return root instanceof Enemy || root.getType().getCategory() == MobCategory.MONSTER;
    }

    public static UUID contactKey(Entity entity) {
        Entity root = logicalParent(entity);
        return root.getUUID();
    }

    public static Entity logicalParent(Entity entity) {
        if (entity instanceof PartEntity<?> part && part.getParent() != null) {
            return part.getParent();
        }
        return entity;
    }

    public static LivingEntity livingParent(Entity entity) {
        Entity parent = logicalParent(entity);
        return parent instanceof LivingEntity living ? living : null;
    }

    public static boolean hurtNonLiving(ServerLevel level, Player owner, Entity target,
                                        DamageSource source, ItemStack weapon, float damageAmount) {
        if (damageAmount <= 0.0F || !canContact(owner, target)) {
            return false;
        }

        LivingEntity livingParent = livingParent(target);
        float finalDamage = damageAmount;
        if (livingParent != null) {
            finalDamage = EnchantmentHelper.modifyDamage(level, weapon, livingParent, source, damageAmount);
        }
        if (finalDamage <= 0.0F) {
            return false;
        }

        boolean damaged = target.hurt(source, finalDamage);
        if (damaged && livingParent != null) {
            EnchantmentHelper.doPostAttackEffectsWithItemSource(level, livingParent, source, weapon);
        }
        return damaged;
    }
}
