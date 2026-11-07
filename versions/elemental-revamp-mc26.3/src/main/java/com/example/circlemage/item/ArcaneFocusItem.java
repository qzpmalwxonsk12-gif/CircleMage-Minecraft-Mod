package com.example.circlemage.item;

import com.example.circlemage.CircleMageMod;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Arcane Focus — a crystal-tipped wand that concentrates a mage's flow. While held (either hand)
 * spells cost {@code 15%} less mana and deal {@code +10%} damage (see the cost/damage hooks in
 * {@link CircleMageMod}). Raising it (right-click) toggles mana channelling, so a wand-wielding
 * mage never needs the channel key.
 */
public class ArcaneFocusItem extends Item {

    /** Mana-cost multiplier while a focus is held. */
    public static final double COST_MULT = 0.85;
    /** Spell-damage multiplier while a focus is held. */
    public static final float DAMAGE_MULT = 1.10f;

    public ArcaneFocusItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResult use(Level world, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        if (!world.isClientSide() && user instanceof ServerPlayer player) {
            CircleMageMod.toggleChanneling(player);
            player.getCooldowns().addCooldown(stack, 10); // debounce the toggle
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext world, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> tooltip, TooltipFlag context) {
        tooltip.accept(Component.literal("While held: -15% spell cost, +10% spell damage").withStyle(ChatFormatting.AQUA));
        tooltip.accept(Component.literal("Right-click to begin or end channelling").withStyle(ChatFormatting.DARK_GRAY));
    }
}
