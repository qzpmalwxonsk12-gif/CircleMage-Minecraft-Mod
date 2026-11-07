package com.example.circlemage.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Arcane Station — the wand-and-staff assembly bench (v2.1 framework). Crafted from arcane
 * wood and carpets; right-click opens the four-slot assembly screen
 * ({@link ArcaneStationScreenHandler}).
 */
public class ArcaneStationBlock extends Block {

    public ArcaneStationBlock(Properties settings) {
        super(settings);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player,
                              BlockHitResult hit) {
        if (world.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        player.openMenu(new SimpleMenuProvider(
                (syncId, inventory, p) -> new ArcaneStationScreenHandler(
                        syncId, inventory, ContainerLevelAccess.create(world, pos)),
                Component.translatable("container.circlemage.arcane_station")));
        return InteractionResult.CONSUME;
    }
}
