package com.example.circlemage.block;

import com.example.circlemage.CircleMageMod;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class ModBlocks {

    /** The wand/staff assembly bench (v2.1 framework). */
    public static final Block ARCANE_STATION = Registry.register(BuiltInRegistries.BLOCK,
            Identifier.fromNamespaceAndPath(CircleMageMod.MOD_ID, "arcane_station"),
            new ArcaneStationBlock(BlockBehaviour.Properties.of().setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BLOCK, Identifier.fromNamespaceAndPath(CircleMageMod.MOD_ID,"arcane_station")))
                    .strength(2.5f)
                    .sound(SoundType.WOOD)));

    public static final Item ARCANE_STATION_ITEM = Registry.register(BuiltInRegistries.ITEM,
            Identifier.fromNamespaceAndPath(CircleMageMod.MOD_ID, "arcane_station"),
            new BlockItem(ARCANE_STATION, new Item.Properties().setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM, Identifier.fromNamespaceAndPath(CircleMageMod.MOD_ID,"arcane_station"))).useBlockDescriptionPrefix()));

    public static final MenuType<ArcaneStationScreenHandler> ARCANE_STATION_SCREEN_HANDLER =
            Registry.register(BuiltInRegistries.MENU,
                    Identifier.fromNamespaceAndPath(CircleMageMod.MOD_ID, "arcane_station"),
                    new MenuType<>(ArcaneStationScreenHandler::new, FeatureFlags.VANILLA_SET));

    private ModBlocks() {
    }

    public static void register() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(entries ->
                entries.accept(ARCANE_STATION_ITEM));
    }
}
