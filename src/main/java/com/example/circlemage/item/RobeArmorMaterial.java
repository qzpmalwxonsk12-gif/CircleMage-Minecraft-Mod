package com.example.circlemage.item;

import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.recipe.Ingredient;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

/**
 * Armor material for the Mage's Robes. One entry per variant so each can use its own worn texture
 * ({@code assets/minecraft/textures/models/armor/<name>_layer_1.png}). Robes are cloth — they grant
 * little vanilla armor; their value is the custom magic resistance held on {@link MageRobeItem}.
 */
public enum RobeArmorMaterial implements ArmorMaterial {
    IRON("robe_iron"),
    AMETHYST("robe_amethyst"),
    EMERALD("robe_emerald"),
    DIAMOND("robe_diamond");

    private final String name;

    RobeArmorMaterial(String name) {
        this.name = name;
    }

    @Override
    public int getDurability(ArmorItem.Type type) {
        return 300;
    }

    @Override
    public int getProtection(ArmorItem.Type type) {
        return type == ArmorItem.Type.CHESTPLATE ? 4 : 0;
    }

    @Override
    public int getEnchantability() {
        return 9;
    }

    @Override
    public SoundEvent getEquipSound() {
        return SoundEvents.ITEM_ARMOR_EQUIP_LEATHER;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return Ingredient.EMPTY;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public float getToughness() {
        return 0.0f;
    }

    @Override
    public float getKnockbackResistance() {
        return 0.0f;
    }
}
