package com.example.circlemage.item;

import com.example.circlemage.mana.ManaData;
import com.example.circlemage.mana.ManaPlayer;
import com.example.circlemage.network.ModNetworking;
import com.example.circlemage.particle.ModParticles;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Mana Crystal — a rare chest treasure (and The Prodigy's drop). Right-click to attune it: each one
 * permanently doubles the player's mana gain, up to {@link ManaData#MAX_CRYSTALS}. Beyond that it
 * refuses to be consumed so the player can't waste extras.
 */
public class ManaCrystalItem extends Item {

    public ManaCrystalItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResult use(Level world, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        if (world.isClientSide() || !(user instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }

        ManaData data = ManaPlayer.get(player);
        if (!data.consumeCrystal()) {
            com.example.circlemage.util.Messages.send(player, Component.literal("You can attune to no more than " + ManaData.MAX_CRYSTALS
                    + " Mana Crystals.").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }

        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        ModNetworking.syncToClient(player);

        if (world instanceof ServerLevel serverWorld) {
            serverWorld.sendParticles(ModParticles.RADIANCE, player.getX(), player.getY(0.6), player.getZ(),
                    50, 0.5, 0.9, 0.5, 0.08);
            serverWorld.sendParticles(ModParticles.FROST, player.getX(), player.getY(0.6), player.getZ(),
                    30, 0.5, 0.9, 0.5, 0.05);
        }
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.4f);
        com.example.circlemage.util.Messages.send(player, Component.literal("The Mana Crystal dissolves into you — natural mana flow +5/s ("
                + data.getCrystalsConsumed() + "/" + ManaData.MAX_CRYSTALS + ").")
                .withStyle(ChatFormatting.AQUA), false);
        return InteractionResult.SUCCESS;
    }
}
