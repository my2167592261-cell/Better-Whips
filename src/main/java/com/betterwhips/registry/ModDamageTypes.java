package com.betterwhips.registry;

import com.betterwhips.BetterWhipsMod;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class ModDamageTypes {
    private ModDamageTypes() {}

    public static final ResourceKey<DamageType> ROYAL_SLIME_WHIP = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(BetterWhipsMod.MOD_ID, "royal_slime_whip")
    );

    public static DamageSource royalSlimeWhip(Entity attacker) {
        return new RoyalSlimeWhipDamageSource(holder(attacker), attacker);
    }

    public static DamageSource diamondBladeMagic(Entity attacker) {
        Holder<DamageType> magic = attacker.level().registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(DamageTypes.MAGIC);
        return new DamageSource(magic, attacker, attacker);
    }

    public static DamageSource emeraldWhipMagic(Entity attacker) {
        Holder<DamageType> magic = attacker.level().registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(DamageTypes.MAGIC);
        return new DamageSource(magic, attacker, attacker);
    }

    public static DamageSource lightningWhip(Entity attacker) {
        Holder<DamageType> lightning = attacker.level().registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(DamageTypes.LIGHTNING_BOLT);
        return new DamageSource(lightning, attacker, attacker);
    }

    public static DamageSource amethystWhipMagic(Entity attacker) {
        Holder<DamageType> magic = attacker.level().registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(DamageTypes.MAGIC);
        return new DamageSource(magic, attacker, attacker);
    }

    public static DamageSource destructionWhipFire(Entity attacker) {
        Holder<DamageType> fire = attacker.level().registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(DamageTypes.ON_FIRE);
        return new DamageSource(fire, attacker, attacker);
    }

    public static DamageSource destructionWhipMagic(Entity attacker) {
        Holder<DamageType> magic = attacker.level().registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(DamageTypes.MAGIC);
        return new DamageSource(magic, attacker, attacker);
    }

    private static Holder<DamageType> holder(Entity context) {
        return context.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(ROYAL_SLIME_WHIP);
    }

    private static final class RoyalSlimeWhipDamageSource extends DamageSource {
        private RoyalSlimeWhipDamageSource(Holder<DamageType> type, Entity attacker) {
            super(type, attacker, attacker);
        }

        @Override
        public Component getLocalizedDeathMessage(LivingEntity victim) {
            Entity attacker = getEntity();
            if (attacker != null) {
                return Component.translatable(
                        "death.attack.better_whips.royal_slime_whip",
                        victim.getDisplayName(), attacker.getDisplayName());
            }
            return Component.translatable(
                    "death.attack.better_whips.royal_slime_whip.no_attacker",
                    victim.getDisplayName());
        }
    }
}
