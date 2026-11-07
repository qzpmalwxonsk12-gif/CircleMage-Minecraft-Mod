package com.example.circlemage.block;

import com.example.circlemage.CircleMageMod;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

public final class ModBlocks {

    /** The wand/staff assembly bench (v2.1 framework). */
    public static final Block ARCANE_STATION = Registry.register(Registries.BLOCK,
            new Identifier(CircleMageMod.MOD_ID, "arcane_station"),
            new ArcaneStationBlock(AbstractBlock.Settings.create()
                    .strength(2.5f)
                    .sounds(BlockSoundGroup.WOOD)));

    public static final Item ARCANE_STATION_ITEM = Registry.register(Registries.ITEM,
            new Identifier(CircleMageMod.MOD_ID, "arcane_station"),
            new BlockItem(ARCANE_STATION, new Item.Settings()));

    public static final ScreenHandlerType<ArcaneStationScreenHandler> ARCANE_STATION_SCREEN_HANDLER =
            Registry.register(Registries.SCREEN_HANDLER,
                    new Identifier(CircleMageMod.MOD_ID, "arcane_station"),
                    new ScreenHandlerType<>(ArcaneStationScreenHandler::new, FeatureFlags.VANILLA_FEATURES));

    private ModBlocks() {
    }

    public static void register() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries ->
                entries.add(ARCANE_STATION_ITEM));
    }
}
