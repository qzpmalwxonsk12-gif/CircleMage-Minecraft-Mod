package com.example.circlemage.item;
import java.util.Map;
import net.minecraft.resources.*;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.equipment.*;
/** Four cloth equipment assets, each retaining four armor points and 300 durability. */
public enum RobeArmorMaterial {
    IRON("robe_iron"), AMETHYST("robe_amethyst"), EMERALD("robe_emerald"), DIAMOND("robe_diamond");
    public final ArmorMaterial material;
    RobeArmorMaterial(String name) {
        material = new ArmorMaterial(19, Map.of(ArmorType.CHESTPLATE,4,ArmorType.HELMET,0,ArmorType.LEGGINGS,0,ArmorType.BOOTS,0,ArmorType.BODY,0),
            9, SoundEvents.ARMOR_EQUIP_LEATHER, 0, 0,
            TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("circlemage","robe_repairs")),
            ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath("circlemage",name)));
    }
}
