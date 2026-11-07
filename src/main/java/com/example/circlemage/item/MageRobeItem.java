package com.example.circlemage.item;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.ItemStack;

/**
 * A Mage's Robe — a chestplate-slot garment that reduces incoming magic damage (notably Magic
 * Missile). Each variant has its own magic resistance (iron/amethyst 30%, emerald 40%, diamond 50%).
 */
public class MageRobeItem extends ArmorItem {

    private final float magicResistance;

    public MageRobeItem(ArmorMaterial material, float magicResistance, Settings settings) {
        super(material, ArmorItem.Type.CHESTPLATE, settings);
        this.magicResistance = magicResistance;
    }

    public float getMagicResistance() {
        return magicResistance;
    }

    /** The fraction (0..1) of magic damage the entity's worn robe negates, or 0 if none. */
    public static float resistanceOf(LivingEntity entity) {
        ItemStack chest = entity.getEquippedStack(EquipmentSlot.CHEST);
        if (chest.getItem() instanceof MageRobeItem robe) {
            return robe.getMagicResistance();
        }
        return 0.0f;
    }
}
