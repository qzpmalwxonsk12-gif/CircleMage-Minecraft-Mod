package com.example.circlemage.item;

import com.example.circlemage.mana.ManaData;
import com.example.circlemage.mana.ManaPlayer;
import com.example.circlemage.network.ModNetworking;
import com.example.circlemage.particle.ModParticles;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * Mana Crystal — a rare chest treasure (and The Prodigy's drop). Right-click to attune it: each one
 * permanently doubles the player's mana gain, up to {@link ManaData#MAX_CRYSTALS}. Beyond that it
 * refuses to be consumed so the player can't waste extras.
 */
public class ManaCrystalItem extends Item {

    public ManaCrystalItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient || !(user instanceof ServerPlayerEntity player)) {
            return TypedActionResult.success(stack, world.isClient);
        }

        ManaData data = ManaPlayer.get(player);
        if (!data.consumeCrystal()) {
            player.sendMessage(Text.literal("You can attune to no more than " + ManaData.MAX_CRYSTALS
                    + " Mana Crystals.").formatted(Formatting.RED), true);
            return TypedActionResult.fail(stack);
        }

        if (!player.getAbilities().creativeMode) {
            stack.decrement(1);
        }
        ModNetworking.syncToClient(player);

        if (world instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(ModParticles.RADIANCE, player.getX(), player.getBodyY(0.6), player.getZ(),
                    50, 0.5, 0.9, 0.5, 0.08);
            serverWorld.spawnParticles(ModParticles.FROST, player.getX(), player.getBodyY(0.6), player.getZ(),
                    30, 0.5, 0.9, 0.5, 0.05);
        }
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.0f, 1.4f);
        player.sendMessage(Text.literal("The Mana Crystal dissolves into you — natural mana flow +5/s ("
                + data.getCrystalsConsumed() + "/" + ManaData.MAX_CRYSTALS + ").")
                .formatted(Formatting.AQUA), false);
        return TypedActionResult.success(stack);
    }
}
