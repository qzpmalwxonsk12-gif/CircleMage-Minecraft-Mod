package com.example.circlemage.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Arcane String — the mana-infused form of vanilla string. Always carries an enchantment-style glint
 * to read as "magical". Crafted into Arcane Yarn.
 */
public class ArcaneStringItem extends Item {

    public ArcaneStringItem(Properties settings) {
        super(settings);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
