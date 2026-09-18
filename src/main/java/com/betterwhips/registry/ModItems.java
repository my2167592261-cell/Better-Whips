package com.betterwhips.registry;

import com.betterwhips.item.AmethystWhipItem;
import com.betterwhips.item.ChainWhipItem;
import com.betterwhips.item.DestructionWhipItem;
import com.betterwhips.item.DiamondBladeWhipItem;
import com.betterwhips.item.EmeraldWhipItem;
import com.betterwhips.item.GoldenHeavyWhipItem;
import com.betterwhips.item.LeatherWhipItem;
import com.betterwhips.item.LightningWhipItem;
import com.betterwhips.item.RoyalSlimeWhipItem;
import com.betterwhips.item.SeaRippleWhipItem;
import com.betterwhips.item.TrainerWhipItem;
import com.betterwhips.item.WindwhispererWhipItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("better_whips");
    public static final DeferredItem<RoyalSlimeWhipItem> ROYAL_SLIME_WHIP = ITEMS.register("royal_slime_whip", () -> new RoyalSlimeWhipItem(new Item.Properties().stacksTo(1).fireResistant()));
    public static final DeferredItem<LeatherWhipItem> LEATHER_WHIP = ITEMS.register("leather_whip", () -> new LeatherWhipItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<LightningWhipItem> LIGHTNING_WHIP = ITEMS.register("lightning_whip", () -> new LightningWhipItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ChainWhipItem> CHAIN_WHIP = ITEMS.register("chain_whip", () -> new ChainWhipItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<GoldenHeavyWhipItem> GOLDEN_HEAVY_WHIP = ITEMS.register("golden_heavy_whip", () -> new GoldenHeavyWhipItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<DiamondBladeWhipItem> DIAMOND_BLADE_WHIP = ITEMS.register("diamond_blade_whip", () -> new DiamondBladeWhipItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<EmeraldWhipItem> EMERALD_WHIP = ITEMS.register("emerald_whip", () -> new EmeraldWhipItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<AmethystWhipItem> AMETHYST_WHIP = ITEMS.register("amethyst_whip", () -> new AmethystWhipItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<TrainerWhipItem> TRAINER_WHIP = ITEMS.register("trainer_whip", () -> new TrainerWhipItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<SeaRippleWhipItem> UNTAMED_SEA_WHIP = ITEMS.register("untamed_sea_whip", () -> new SeaRippleWhipItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<DestructionWhipItem> DESTRUCTION_WHIP = ITEMS.register("destruction_whip", () -> new DestructionWhipItem(new Item.Properties().stacksTo(1).fireResistant()));
    public static final DeferredItem<WindwhispererWhipItem> WINDWHISPERER_WHIP = ITEMS.register("windwhisperer_whip", () -> new WindwhispererWhipItem(new Item.Properties().stacksTo(1)));

    private ModItems() {
    }
}
