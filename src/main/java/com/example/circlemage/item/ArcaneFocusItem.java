package com.example.circlemage.item;

import com.example.circlemage.CircleMageMod;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

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

    public ArcaneFocusItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient && user instanceof ServerPlayerEntity player) {
            CircleMageMod.toggleChanneling(player);
            player.getItemCooldownManager().set(this, 10); // debounce the toggle
        }
        return TypedActionResult.success(stack, world.isClient);
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        return true;
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.literal("While held: -15% spell cost, +10% spell damage").formatted(Formatting.AQUA));
        tooltip.add(Text.literal("Right-click to begin or end channelling").formatted(Formatting.DARK_GRAY));
    }
}
