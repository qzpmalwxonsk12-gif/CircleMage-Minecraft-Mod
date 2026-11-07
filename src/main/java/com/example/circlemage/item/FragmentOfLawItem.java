package com.example.circlemage.item;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Fragment of Law — dropped by The Master. A glowing shard inscribed with symbols the player cannot
 * yet read; a deliberate breadcrumb toward the Laws. Has no use yet beyond flavour/progression.
 */
public class FragmentOfLawItem extends Item {

    public FragmentOfLawItem(Settings settings) {
        super(settings);
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        return true;
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.literal("Incomprehensible symbols crawl across its surface.").formatted(Formatting.GRAY, Formatting.ITALIC));
        tooltip.add(Text.literal("A fragment of some higher truth — you cannot yet grasp its meaning.").formatted(Formatting.DARK_PURPLE, Formatting.ITALIC));
    }
}
