package com.example.circlemage.item;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.ItemStack;

/**
 * A Mage's Robe — a chestplate-slot garment that reduces incoming magic damage (notably Magic
 * Missile). Each variant has its own magic resistance (iron/amethyst 30%, emerald 40%, diamond 50%).
 */
public class MageRobeItem extends Item {

    private final float magicResistance;

    public MageRobeItem(RobeArmorMaterial material, float magicResistance, Properties settings) {
        super(settings.humanoidArmor(material.material, ArmorType.CHESTPLATE).durability(300));
        this.magicResistance = magicResistance;
    }

    public float getMagicResistance() {
        return magicResistance;
    }

    /** The fraction (0..1) of magic damage the entity's worn robe negates, or 0 if none. */
    public static float resistanceOf(LivingEntity entity) {
        ItemStack chest = entity.getItemBySlot(EquipmentSlot.CHEST);
        if (chest.getItem() instanceof MageRobeItem robe) {
            return robe.getMagicResistance();
        }
        return 0.0f;
    }
}
