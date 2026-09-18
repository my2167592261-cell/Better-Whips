package com.betterwhips.item;

import com.betterwhips.network.SeaRippleWhipNetwork;
import com.betterwhips.network.SeaRippleWhipNetwork.*;
import com.betterwhips.physics.SeaRippleWhipMotion;
import com.betterwhips.physics.SeaRippleWhipMotion.Stroke;
import com.betterwhips.registry.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import java.util.*;
import static com.betterwhips.physics.SeaRippleWhipTuning.*;
import static com.betterwhips.network.SeaRippleWhipNetwork.ATTACK;
import static com.betterwhips.network.SeaRippleWhipNetwork.BLADE;

public final class SeaRippleWhipCombat {
    private static final Map<UUID,State> STATES=new HashMap<>();
    private static final ResourceLocation ATTACK_BOOST=ResourceLocation.fromNamespaceAndPath("better_whips","sea_rain_water_speed");
    private SeaRippleWhipCombat() {}
    public static boolean holding(Player player) { return player.getMainHandItem().is(ModItems.UNTAMED_SEA_WHIP.get()); }
    private static long now(ServerLevel level) { return level.getServer().overworld().getGameTime(); }
    private static int side(Player p) { return p.getMainArm()==HumanoidArm.RIGHT ? 1 : -1; }
    private static boolean valid(ServerLevel level,Player owner,Entity target) {
        if (!WhipEntityTargeting.canContact(owner,target) || target.isInvulnerable() || target instanceof ArmorStand) {
            return false;
        }
        if (target instanceof LivingEntity living) {
            return !WhipFriendlySupport.classify(level,owner,living).friendly();
        }
        return true;
    }
    public static void onAttackEntity(AttackEntityEvent event) {
        if(!holding(event.getEntity()))return;
        event.setCanceled(true);
        if(event.getEntity() instanceof ServerPlayer player)request(player,SeaRippleWhipNetwork.ATTACK);
    }
    public static void request(ServerPlayer player,int action) {
        if(action<ATTACK || action>BLADE || !holding(player) || !player.isAlive() || player.isSpectator() || player.isSleeping())return;
        ServerLevel level=player.serverLevel();long tick=now(level);
        State s=STATES.computeIfAbsent(player.getUUID(),key->new State(player,tick));
        if(s.level.getServer()!=level.getServer()) { s=new State(player,tick);STATES.put(player.getUUID(),s); }
        else if(s.level!=level)s.changeLevel(player,tick);
        if(s.requestTick!=tick){s.requestTick=tick;s.requestMask=0;}
        int bit=1<<action;if((s.requestMask&bit)!=0)return;s.requestMask|=bit;s.lastSeen=tick;
        if(action==ATTACK) {
            if(tick>=s.nextAttack && !(s.stroke.kind()==SeaRippleWhipMotion.BLADE && tick<s.stroke.startTick()+s.stroke.duration())) {
                boost(player,player.isInWaterOrRain());
                int period=Math.max(5,(int)Math.ceil(player.getCurrentItemAttackStrengthDelay()));
                s.nextAttack=tick+period;
                s.stroke=new Stroke((s.combo++&1)==0?SeaRippleWhipMotion.SWEEP_LEFT:SeaRippleWhipMotion.SWEEP_RIGHT,
                    tick,Math.max(6,Math.min(16,period)),player.position(),player.getLookAngle(),side(player),tick<s.empoweredUntil);
                s.tailHits.clear();player.resetAttackStrengthTicker();player.swing(InteractionHand.MAIN_HAND,true);
                level.playSound(null,player.blockPosition(),SoundEvents.PLAYER_ATTACK_SWEEP,SoundSource.PLAYERS,.6f,1.24f);
            }
        } else if(tick>=s.bladeReady) {
            s.bladeReady=tick+BLADE_COOLDOWN;
            s.stroke=new Stroke(SeaRippleWhipMotion.BLADE,tick,9,player.position(),player.getLookAngle(),side(player),true);
            s.tailHits.clear();s.nextAttack=Math.max(s.nextAttack,tick+9);
            level.playSound(null,player.blockPosition(),SoundEvents.TRIDENT_THROW.value(),SoundSource.PLAYERS,.8f,1.35f);
        }
        sync(player,s,tick);
    }
    public static void onServerTick(ServerTickEvent.Post event) {
        long tick=event.getServer().overworld().getGameTime();
        for(ServerPlayer p:event.getServer().getPlayerList().getPlayers())
            if(holding(p) && p.isAlive())STATES.computeIfAbsent(p.getUUID(),key->new State(p,tick));
        Iterator<Map.Entry<UUID,State>> iterator=STATES.entrySet().iterator();
        while(iterator.hasNext()) {
            var entry=iterator.next();ServerPlayer p=event.getServer().getPlayerList().getPlayer(entry.getKey());State s=entry.getValue();
            if(p==null){iterator.remove();continue;}
            if(s.level.getServer()!=event.getServer()) { s=new State(p,tick);entry.setValue(s); }
            else if(s.level!=p.serverLevel())s.changeLevel(p,tick);
            boolean held=holding(p) && p.isAlive() && !p.isSpectator() && !p.isSleeping();
            boost(p,held && p.isInWaterOrRain());
            if(!held) {
                if(s.stroke.kind()!=SeaRippleWhipMotion.IDLE){s.cancel(p,tick);sync(p,s,tick);}
                if(tick-s.lastSeen>2400)iterator.remove();
                continue;
            }
            s.lastSeen=tick;tickStroke(p,s,tick);
            if(tick%10==0)sync(p,s,tick);
        }
    }
    public static void onServerStopped(ServerStoppedEvent event) {
        STATES.entrySet().removeIf(e->e.getValue().level.getServer()==event.getServer());
    }
    private static void boost(Player p,boolean active) {
        var attribute=p.getAttribute(Attributes.ATTACK_SPEED);if(attribute==null)return;
        if(active && !attribute.hasModifier(ATTACK_BOOST))attribute.addTransientModifier(
            new AttributeModifier(ATTACK_BOOST,WATER_ATTACK_SPEED_BONUS,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        else if(!active)attribute.removeModifier(ATTACK_BOOST);
    }
    private static void tickStroke(ServerPlayer p,State s,long tick) {
        Stroke stroke=s.stroke;double age=tick-stroke.startTick();
        if(age>stroke.duration() || !SeaRippleWhipMotion.canHit(stroke,age) && !SeaRippleWhipMotion.canHit(stroke,age-1))return;
        ServerLevel level=p.serverLevel();Vec3 root=SeaRippleWhipMotion.grip(p.position(),stroke.aim(),p.getEyeHeight(),side(p));
        Vec3 oldRoot=SeaRippleWhipMotion.grip(new Vec3(p.xo,p.yo,p.zo),stroke.aim(),p.getEyeHeight(),side(p));
        List<Entity> targets=level.getEntities(p,new AABB(root,root).inflate(BLADE_REACH+1),e->valid(level,p,e));
        targets.sort(Comparator.comparingDouble(p::distanceToSqr));
        boolean blade=stroke.kind()==SeaRippleWhipMotion.BLADE;
        int[] tailOrder=tailHitOrder(stroke);
        Vec3[][][] contacts=new Vec3[SeaRippleWhipMotion.TAILS][][];
        for(int tail=0;tail<SeaRippleWhipMotion.TAILS;tail++)
            contacts[tail]=SeaRippleWhipMotion.contactSegmentsForTail(stroke,age,oldRoot,root,tail);

        for(Entity target:targets) {

            if(target.getBoundingBox().getCenter().subtract(p.getEyePosition()).dot(stroke.aim())<0)continue;
            if(level.clip(new ClipContext(root,target.getBoundingBox().getCenter(),ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,p)).getType()!=HitResult.Type.MISS)continue;

            UUID targetId=WhipEntityTargeting.contactKey(target);
            int hitMask=s.tailHits.getOrDefault(targetId,0);
            int resolved=Integer.bitCount(hitMask);
            for(int tail:tailOrder) {
                int bit=1<<tail;
                if((hitMask&bit)!=0)continue;
                double contactRadius=blade?.25:.14;
                Vec3 contact=SeaRippleWhipMotion.bestContact(target.getBoundingBox(),contacts[tail],contactRadius,
                    p.getEyePosition(),p.getLookAngle());
                if(contact==null || !WhipEntityTargeting.mayDamageAtContact(p,target,contact))continue;

                hitMask|=bit;
                s.tailHits.put(targetId,hitMask);
                float base=(float)p.getAttributeValue(Attributes.ATTACK_DAMAGE);
                float amount=WhipMultiHitDamage.scale(base*(blade?2.0F:1.0F),resolved);

                if(!blade && stroke.empowered() && resolved>0)amount*=2.0F;
                resolved++;
                if(!damage(p,target,contact,amount))continue;

                if(!blade) {
                    SeaRippleWhipNetwork.nearby(level,contact,new Impact(level.dimension().location(),
                        contact,stroke.aim(),target.getId(),target.getUUID(),
                        stroke.empowered(),Math.min(4,Math.max(.45f,target.getBbWidth())),level.random.nextLong()));
                }
                if(blade){s.empoweredUntil=tick+EMPOWER_TICKS;sync(p,s,tick);}
            }
        }
    }

    private static int[] tailHitOrder(Stroke stroke) {
        if(stroke.kind()==SeaRippleWhipMotion.SWEEP_RIGHT)return new int[]{4,3,2,1,0};
        return new int[]{0,1,2,3,4};
    }

    private static boolean damage(ServerPlayer owner,Entity target,Vec3 contact,float amount) {
        if (!WhipEntityTargeting.mayDamageAtContact(owner,target,contact))return false;
        if (target instanceof LivingEntity living) {
            return damage(owner,living,amount);
        }
        DamageSource source=owner.damageSources().playerAttack(owner);
        return WhipEntityTargeting.hurtNonLiving(owner.serverLevel(),owner,target,source,owner.getMainHandItem(),amount);
    }

    private static boolean damage(ServerPlayer owner,LivingEntity target,float amount) {
        if(amount<=0 || !target.isAlive())return false;
        DamageSource source=owner.damageSources().playerAttack(owner);
        float modified=EnchantmentHelper.modifyDamage(owner.serverLevel(),owner.getMainHandItem(),target,source,amount);
        float before=target.getHealth()+target.getAbsorptionAmount();
        int oldFrames=target.invulnerableTime;

        target.invulnerableTime=0;
        boolean hit;
        try { hit=target.hurt(source,modified); }
        finally { target.invulnerableTime=Math.max(oldFrames,target.invulnerableTime); }
        if(!hit)return false;
        EnchantmentHelper.doPostAttackEffectsWithItemSource(owner.serverLevel(),target,source,owner.getMainHandItem());
        WhipDamageDebug.record(owner,Math.max(0,before-target.getHealth()-target.getAbsorptionAmount()));
        return true;
    }
    private static void sync(ServerPlayer p,State s,long tick) {
        SeaRippleWhipNetwork.nearby(p.serverLevel(),p.position(),new Snapshot(p.getId(),p.getUUID(),
            p.level().dimension().location(),tick,s.stroke,s.nextAttack,s.bladeReady,s.empoweredUntil));
    }
    private static final class State {
        ServerLevel level;Stroke stroke;long nextAttack,bladeReady,empoweredUntil,lastSeen,requestTick=-1;int combo,requestMask;
        final Map<UUID,Integer> tailHits=new HashMap<>();
        State(ServerPlayer p,long tick){level=p.serverLevel();cancel(p,tick);lastSeen=tick;}
        void cancel(ServerPlayer p,long tick){stroke=Stroke.idle(p.position(),p.getLookAngle(),side(p),tick);tailHits.clear();}
        void changeLevel(ServerPlayer p,long tick){level=p.serverLevel();empoweredUntil=0;cancel(p,tick);}
    }
}
