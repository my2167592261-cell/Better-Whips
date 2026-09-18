package com.betterwhips.client;

import com.betterwhips.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import java.util.Locale;

public final class SeaRippleWhipHud {
    private SeaRippleWhipHud() {}
    public static void onRenderGuiLayer(RenderGuiLayerEvent.Post event) {
        Minecraft mc=Minecraft.getInstance();
        if(!event.getName().equals(VanillaGuiLayers.HOTBAR) || mc.options.hideGui || mc.player==null
            || mc.player.isSpectator() || !mc.player.getMainHandItem().is(ModItems.UNTAMED_SEA_WHIP.get()))return;
        long boost=SeaRippleWhipClientState.empoweredRemaining(mc.player),cooldown=SeaRippleWhipClientState.bladeRemaining(mc.player);
        Component text=boost>0?Component.translatable("hud.better_whips.sea.empowered",seconds(boost)):
            cooldown>0?Component.translatable("hud.better_whips.sea.cooldown",seconds(cooldown)):Component.translatable("hud.better_whips.sea.ready");
        var gui=event.getGuiGraphics();
        gui.drawCenteredString(mc.font,text,gui.guiWidth()/2,gui.guiHeight()-64,boost>0?0x99ffee:0xaacddd);
    }
    private static String seconds(long ticks) { return String.format(Locale.ROOT,"%.1f",ticks/20.0); }
}
