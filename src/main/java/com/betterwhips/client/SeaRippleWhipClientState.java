package com.betterwhips.client;

import com.betterwhips.network.SeaRippleWhipNetwork;
import com.betterwhips.network.SeaRippleWhipNetwork.Snapshot;
import com.betterwhips.physics.SeaRippleWhipMotion;
import com.betterwhips.physics.SeaRippleWhipMotion.Stroke;
import com.betterwhips.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import java.util.*;
import static com.betterwhips.physics.SeaRippleWhipTuning.*;

public final class SeaRippleWhipClientState {
    private static final Map<UUID,TimedState> STATES=new HashMap<>();
    private static ClientLevel world;
    private static Prediction prediction;
    private static long inputTick=-1,localAttackReady,localBladeReady;
    private static int inputMask,combo;
    private SeaRippleWhipClientState() {}
    public static void clear() {
        STATES.clear();prediction=null;world=null;inputTick=-1;inputMask=0;combo=0;localAttackReady=localBladeReady=0;
    }
    static void tick() {
        ClientLevel level=Minecraft.getInstance().level;
        if(level!=world){clear();world=level;}
        if(level==null)return;
        STATES.entrySet().removeIf(e->level.getGameTime()-e.getValue().received>200);
        Player p=Minecraft.getInstance().player;
        if(p==null || !p.getMainHandItem().is(ModItems.UNTAMED_SEA_WHIP.get()))prediction=null;
        if(prediction!=null && level.getGameTime()-prediction.localStart>prediction.stroke.duration()+6)prediction=null;
    }
    public static void receive(Snapshot payload) {
        ClientLevel level=Minecraft.getInstance().level;
        if(level==null || !level.dimension().location().equals(payload.dimension()))return;
        if(level!=world){clear();world=level;}
        TimedState old=STATES.get(payload.uuid());
        if(old!=null && old.packet.serverTick()>payload.serverTick())return;
        STATES.put(payload.uuid(),new TimedState(payload,level.getGameTime()));
        Player p=Minecraft.getInstance().player;
        if(p!=null && p.getUUID().equals(payload.uuid())) {
            if(prediction!=null) {
                boolean same=payload.stroke().kind()==prediction.stroke.kind()
                    && (!prediction.acknowledged && payload.stroke().startTick()>=prediction.stroke.startTick()-2
                        || payload.stroke().startTick()==prediction.stroke.startTick());
                if(same) {

                    prediction=new Prediction(payload.stroke(),prediction.localStart,true);
                    localAttackReady=prediction.localStart+Math.max(0,payload.nextAttack()-payload.stroke().startTick());
                    if(payload.stroke().kind()==SeaRippleWhipMotion.BLADE)
                        localBladeReady=prediction.localStart+Math.max(0,payload.bladeReady()-payload.stroke().startTick());
                } else if(payload.stroke().startTick()>prediction.stroke.startTick()
                        || !prediction.acknowledged && level.getGameTime()-prediction.localStart>=5
                            && payload.serverTick()>=prediction.stroke.startTick())prediction=null;
            }
            if(prediction==null) {
                localAttackReady=level.getGameTime()+Math.max(0,payload.nextAttack()-payload.serverTick());
                localBladeReady=level.getGameTime()+Math.max(0,payload.bladeReady()-payload.serverTick());
                if(payload.stroke().kind()==SeaRippleWhipMotion.SWEEP_LEFT)combo=1;
                else if(payload.stroke().kind()==SeaRippleWhipMotion.SWEEP_RIGHT)combo=0;
            }
        }
    }
    static double clock(Player player,float partial) {
        TimedState state=STATES.get(player.getUUID());
        return state==null?player.level().getGameTime()+partial:
            state.packet.serverTick()+(player.level().getGameTime()-state.received)+partial;
    }
    static Stroke stroke(Player player,HumanoidArm arm) {
        boolean main=arm==player.getMainArm();
        if(main && player==Minecraft.getInstance().player && prediction!=null)return prediction.stroke;
        TimedState state=STATES.get(player.getUUID());
        if(main && state!=null)return state.packet.stroke();
        return Stroke.idle(player.position(),player.getLookAngle(),arm==HumanoidArm.RIGHT?1:-1,(long)clock(player,0));
    }
    static double age(Player player,Stroke stroke,float partial) {
        if(prediction!=null && stroke==prediction.stroke && player==Minecraft.getInstance().player)
            return player.level().getGameTime()-prediction.localStart+partial;
        return clock(player,partial)-stroke.startTick();
    }
    static long empoweredRemaining(Player player) {
        TimedState state=STATES.get(player.getUUID());
        return state==null?0:Math.max(0,state.packet.empoweredUntil()-(long)clock(player,0));
    }
    static long bladeRemaining(Player player) {
        return Math.max(0,localBladeReady-player.level().getGameTime());
    }
    static boolean input(int action) {
        Minecraft mc=Minecraft.getInstance();Player p=mc.player;
        if(p==null || mc.level==null || mc.screen!=null || mc.isPaused() || !p.isAlive() || p.isSpectator()
                || p.isSleeping() || p.isUsingItem() || !p.getMainHandItem().is(ModItems.UNTAMED_SEA_WHIP.get()))return false;
        if(world!=mc.level){clear();world=mc.level;}
        long tick=mc.level.getGameTime();if(inputTick!=tick){inputTick=tick;inputMask=0;}
        int bit=1<<action;if((inputMask&bit)!=0)return false;
        if(action==SeaRippleWhipNetwork.ATTACK && tick<localAttackReady
                || action==SeaRippleWhipNetwork.BLADE && tick<localBladeReady)return false;
        inputMask|=bit;
        boolean blade=action==SeaRippleWhipNetwork.BLADE;
        int duration=blade?9:Math.max(6,Math.min(16,(int)Math.ceil(p.getCurrentItemAttackStrengthDelay())));
        Stroke s=new Stroke(blade?SeaRippleWhipMotion.BLADE:(combo++&1)==0?SeaRippleWhipMotion.SWEEP_LEFT:SeaRippleWhipMotion.SWEEP_RIGHT,
            (long)clock(p,0),duration,p.position(),p.getLookAngle(),p.getMainArm()==HumanoidArm.RIGHT?1:-1,blade || empoweredRemaining(p)>0);
        prediction=new Prediction(s,tick,false);
        if(blade){localBladeReady=tick+BLADE_COOLDOWN;localAttackReady=Math.max(localAttackReady,tick+9);}
        else {localAttackReady=tick+Math.max(5,(int)Math.ceil(p.getCurrentItemAttackStrengthDelay()));p.resetAttackStrengthTicker();}
        p.swing(InteractionHand.MAIN_HAND);SeaRippleWhipNetwork.request(action);return true;
    }
    private record TimedState(Snapshot packet,long received) {}
    private record Prediction(Stroke stroke,long localStart,boolean acknowledged) {}
}
