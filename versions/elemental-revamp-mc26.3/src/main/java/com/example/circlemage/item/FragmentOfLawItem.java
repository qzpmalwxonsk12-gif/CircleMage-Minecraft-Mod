package com.example.circlemage.item;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Fragment of Law — dropped by The Master. A glowing shard inscribed with symbols the player cannot
 * yet read; a deliberate breadcrumb toward the Laws. Has no use yet beyond flavour/progression.
 */
public class FragmentOfLawItem extends Item {

    public FragmentOfLawItem(Properties settings) {
        super(settings);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext world, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> tooltip, TooltipFlag context) {
        tooltip.accept(Component.literal("Incomprehensible symbols crawl across its surface.").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.accept(Component.literal("A fragment of some higher truth — you cannot yet grasp its meaning.").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
    }
}
