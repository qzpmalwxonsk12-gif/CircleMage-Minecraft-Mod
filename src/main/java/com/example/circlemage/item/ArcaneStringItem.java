package com.example.circlemage.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * Arcane String — the mana-infused form of vanilla string. Always carries an enchantment-style glint
 * to read as "magical". Crafted into Arcane Yarn.
 */
public class ArcaneStringItem extends Item {

    public ArcaneStringItem(Settings settings) {
        super(settings);
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        return true;
    }
}
