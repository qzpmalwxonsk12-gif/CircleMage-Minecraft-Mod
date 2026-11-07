package com.example.circlemage.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * The Arcane Station — the wand-and-staff assembly bench (v2.1 framework). Crafted from arcane
 * wood and carpets; right-click opens the four-slot assembly screen
 * ({@link ArcaneStationScreenHandler}).
 */
public class ArcaneStationBlock extends Block {

    public ArcaneStationBlock(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
                              Hand hand, BlockHitResult hit) {
        if (world.isClient) {
            return ActionResult.SUCCESS;
        }
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, inventory, p) -> new ArcaneStationScreenHandler(
                        syncId, inventory, ScreenHandlerContext.create(world, pos)),
                Text.translatable("container.circlemage.arcane_station")));
        return ActionResult.CONSUME;
    }
}
