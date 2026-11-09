package com.hearthvale;

import com.hearthvale.block.VillageHearthBlock;
import com.hearthvale.entity.NpcEntity;
import com.hearthvale.item.JournalItem;
import com.hearthvale.item.SurveyorDeedItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRegistry {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Hearthvale.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Hearthvale.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, Hearthvale.MODID);

    public static final DeferredBlock<VillageHearthBlock> HEARTH_BLOCK = BLOCKS.registerBlock("village_hearth",
            VillageHearthBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(3.0F, 6.0F)
                    .requiresCorrectToolForDrops().lightLevel(s -> 12).sound(SoundType.STONE));

    public static final DeferredItem<BlockItem> HEARTH_ITEM = ITEMS.registerSimpleBlockItem("village_hearth", HEARTH_BLOCK);
    public static final DeferredItem<SurveyorDeedItem> DEED = ITEMS.registerItem("surveyor_deed", SurveyorDeedItem::new, new Item.Properties().stacksTo(1));
    public static final DeferredItem<JournalItem> JOURNAL = ITEMS.registerItem("village_journal", JournalItem::new, new Item.Properties().stacksTo(1));
    public static final DeferredItem<Item> BOUQUET = ITEMS.registerSimpleItem("bouquet", new Item.Properties().stacksTo(16));
    public static final DeferredItem<Item> PENDANT = ITEMS.registerSimpleItem("promise_pendant", new Item.Properties().stacksTo(1));

    public static final DeferredHolder<EntityType<?>, EntityType<NpcEntity>> NPC = ENTITIES.register("npc",
            () -> EntityType.Builder.of(NpcEntity::new, MobCategory.MISC).sized(0.6F, 1.8F).clientTrackingRange(10).build("npc"));

    private ModRegistry() {}

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        ENTITIES.register(bus);
    }
}
