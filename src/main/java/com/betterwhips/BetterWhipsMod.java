package com.betterwhips;

import com.betterwhips.command.WhipCommands;
import com.betterwhips.item.AmethystWhipCombat;
import com.betterwhips.item.ChainWhipCombat;
import com.betterwhips.item.DestructionWhipCombat;
import com.betterwhips.item.DiamondBladeWhipCombat;
import com.betterwhips.item.EmeraldWhipCombat;
import com.betterwhips.item.GoldenHeavyWhipCombat;
import com.betterwhips.item.LeatherWhipCombat;
import com.betterwhips.item.LightningWhipCombat;
import com.betterwhips.item.LightningWhipDrops;
import com.betterwhips.item.LightningWhipTimeStop;
import com.betterwhips.item.RoyalSlimeWhipCombat;
import com.betterwhips.item.TrainerWhipCombat;
import com.betterwhips.item.SeaRippleWhipCombat;
import com.betterwhips.item.SeaRippleWhipProtection;
import com.betterwhips.item.WhipFriendlySupport;
import com.betterwhips.item.WindwhispererWhipCombat;
import com.betterwhips.item.WindwhispererWhipDrops;
import com.betterwhips.network.AmethystWhipNetwork;
import com.betterwhips.network.ChainWhipNetwork;
import com.betterwhips.network.DestructionWhipNetwork;
import com.betterwhips.network.DiamondBladeWhipNetwork;
import com.betterwhips.network.EmeraldWhipNetwork;
import com.betterwhips.network.GoldenHeavyWhipNetwork;
import com.betterwhips.network.LeatherWhipNetwork;
import com.betterwhips.network.LightningWhipNetwork;
import com.betterwhips.network.RoyalSlimeWhipNetwork;
import com.betterwhips.network.TrainerWhipNetwork;
import com.betterwhips.network.SeaRippleWhipNetwork;
import com.betterwhips.network.WindwhispererWhipNetwork;
import com.betterwhips.registry.ModCreativeTabs;
import com.betterwhips.registry.ModEffects;
import com.betterwhips.registry.ModItems;
import com.betterwhips.registry.ModSounds;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value="better_whips")
public final class BetterWhipsMod {
    public static final String MOD_ID = "better_whips";

    public BetterWhipsMod(IEventBus modBus) {
        ModItems.ITEMS.register(modBus);
        ModEffects.MOB_EFFECTS.register(modBus);
        ModSounds.SOUND_EVENTS.register(modBus);
        ModCreativeTabs.CREATIVE_TABS.register(modBus);
        modBus.addListener(RoyalSlimeWhipNetwork::register);
        modBus.addListener(LeatherWhipNetwork::register);
        modBus.addListener(LightningWhipNetwork::register);
        modBus.addListener(ChainWhipNetwork::register);
        modBus.addListener(GoldenHeavyWhipNetwork::register);
        modBus.addListener(DiamondBladeWhipNetwork::register);
        modBus.addListener(EmeraldWhipNetwork::register);
        modBus.addListener(AmethystWhipNetwork::register);
        modBus.addListener(TrainerWhipNetwork::register);
        modBus.addListener(SeaRippleWhipNetwork::register);
        modBus.addListener(DestructionWhipNetwork::register);
        modBus.addListener(WindwhispererWhipNetwork::register);
        NeoForge.EVENT_BUS.addListener(RoyalSlimeWhipCombat::onServerTick);
        NeoForge.EVENT_BUS.addListener(LeatherWhipCombat::onServerTick);
        NeoForge.EVENT_BUS.addListener(LightningWhipCombat::onServerTick);
        NeoForge.EVENT_BUS.addListener(LightningWhipDrops::onEntityJoinLevel);
        NeoForge.EVENT_BUS.addListener(LightningWhipDrops::onServerTick);
        NeoForge.EVENT_BUS.addListener(LightningWhipDrops::onServerStopped);
        NeoForge.EVENT_BUS.addListener(LightningWhipTimeStop::onEntityTickPre);
        NeoForge.EVENT_BUS.addListener(ChainWhipCombat::onServerTick);
        NeoForge.EVENT_BUS.addListener(GoldenHeavyWhipCombat::onServerTick);
        NeoForge.EVENT_BUS.addListener(DiamondBladeWhipCombat::onServerTick);
        NeoForge.EVENT_BUS.addListener(EmeraldWhipCombat::onServerTick);
        NeoForge.EVENT_BUS.addListener(AmethystWhipCombat::onServerTick);
        NeoForge.EVENT_BUS.addListener(TrainerWhipCombat::onServerTick);
        NeoForge.EVENT_BUS.addListener(SeaRippleWhipCombat::onServerTick);
        NeoForge.EVENT_BUS.addListener(SeaRippleWhipCombat::onAttackEntity);
        NeoForge.EVENT_BUS.addListener(SeaRippleWhipCombat::onServerStopped);
        NeoForge.EVENT_BUS.addListener(SeaRippleWhipProtection::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(SeaRippleWhipProtection::onServerTick);
        NeoForge.EVENT_BUS.addListener(SeaRippleWhipProtection::onServerStopped);
        NeoForge.EVENT_BUS.addListener(DestructionWhipCombat::onServerTick);
        NeoForge.EVENT_BUS.addListener(WindwhispererWhipCombat::onServerTick);
        NeoForge.EVENT_BUS.addListener(WindwhispererWhipDrops::onBlockBreak);
        NeoForge.EVENT_BUS.addListener(WhipFriendlySupport::onServerTick);
        NeoForge.EVENT_BUS.addListener(WhipCommands::register);
    }
}
