package com.example.circlemage.item;

import com.example.circlemage.CircleMageMod;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

public final class ModItems {

    /** A rare blue crystal that permanently doubles mana gain when attuned (max 3). */
    public static final Item MANA_CRYSTAL = reg("mana_crystal",
            new ManaCrystalItem(new Item.Settings().maxCount(16).rarity(Rarity.RARE)));

    /** Vanilla string infused with mana — crafted into Arcane Yarn. Glows. */
    public static final Item ARCANE_STRING = reg("arcane_string",
            new ArcaneStringItem(new Item.Settings()));

    /** Arcane Yarn — the spun form of Arcane String, woven into Mage's Robes. */
    public static final Item ARCANE_YARN = reg("arcane_yarn", new Item(new Item.Settings()));

    // Mage's Robes (chestplate slot). Magic resistance: iron/amethyst 30%, emerald 40%, diamond 50%.
    public static final Item MAGE_ROBE_IRON = reg("mage_robe_iron",
            new MageRobeItem(RobeArmorMaterial.IRON, 0.30f, new Item.Settings().rarity(Rarity.UNCOMMON)));
    public static final Item MAGE_ROBE_AMETHYST = reg("mage_robe_amethyst",
            new MageRobeItem(RobeArmorMaterial.AMETHYST, 0.30f, new Item.Settings().rarity(Rarity.UNCOMMON)));
    public static final Item MAGE_ROBE_EMERALD = reg("mage_robe_emerald",
            new MageRobeItem(RobeArmorMaterial.EMERALD, 0.40f, new Item.Settings().rarity(Rarity.UNCOMMON)));
    public static final Item MAGE_ROBE_DIAMOND = reg("mage_robe_diamond",
            new MageRobeItem(RobeArmorMaterial.DIAMOND, 0.50f, new Item.Settings().rarity(Rarity.RARE)));

    /** Dropped by The Master — a glowing shard of higher magical knowledge (foreshadows the Laws). */
    public static final Item FRAGMENT_OF_LAW = reg("fragment_of_law",
            new FragmentOfLawItem(new Item.Settings().maxCount(16).rarity(Rarity.EPIC)));

    // --- v2 items -------------------------------------------------------------

    /** Residue shed by Wandering Mages — the base ingredient of bottled mana. */
    public static final Item MANA_DUST = reg("mana_dust", new Item(new Item.Settings()));

    /** Bottled mana: drink to restore 500 mana on the spot. */
    public static final Item MANA_VIAL = reg("mana_vial",
            new ManaVialItem(new Item.Settings().maxCount(16)));

    /** A crystal-tipped wand: held-in-hand spell discount/damage boost + channel toggle on use. */
    public static final Item ARCANE_FOCUS = reg("arcane_focus",
            new ArcaneFocusItem(new Item.Settings().maxCount(1).rarity(Rarity.UNCOMMON)));

    // --- v2.1: magic tomes -----------------------------------------------------

    public static final Item TOME_FIRE = reg("tome_fire",
            new MagicTomeItem(MagicTomeItem.Element.FIRE, new Item.Settings().maxCount(16).rarity(Rarity.UNCOMMON)));
    public static final Item TOME_WATER = reg("tome_water",
            new MagicTomeItem(MagicTomeItem.Element.WATER, new Item.Settings().maxCount(16).rarity(Rarity.UNCOMMON)));
    public static final Item TOME_EARTH = reg("tome_earth",
            new MagicTomeItem(MagicTomeItem.Element.EARTH, new Item.Settings().maxCount(16).rarity(Rarity.UNCOMMON)));
    public static final Item TOME_WIND = reg("tome_wind",
            new MagicTomeItem(MagicTomeItem.Element.WIND, new Item.Settings().maxCount(16).rarity(Rarity.UNCOMMON)));
    public static final Item TOME_MISC = reg("tome_misc",
            new MagicTomeItem(MagicTomeItem.Element.MISC, new Item.Settings().maxCount(16).rarity(Rarity.UNCOMMON)));

    // --- v2.1: wand-making framework --------------------------------------------

    /** Distilled mana: dust dissolved in bottled water. The bottle survives crafting. */
    public static final Item MANA_LIQUID = reg("mana_liquid",
            new Item(new Item.Settings().maxCount(16).recipeRemainder(net.minecraft.item.Items.GLASS_BOTTLE)));

    /** Planks soaked in mana liquid — the wand-craft's structural wood. */
    public static final Item ARCANE_WOOD = reg("arcane_wood", new Item(new Item.Settings()));

    /** The binding agent for wand assembly (slot A of the Arcane Station). */
    public static final Item ARCANE_GLUE = reg("arcane_glue", new Item(new Item.Settings()));

    // --- v2.2: wand assembly ----------------------------------------------------

    /** A radiant core gem: a wand built around it sheds torchlight while held. */
    public static final Item GLOW_CRYSTAL = reg("glow_crystal", new Item(new Item.Settings()));

    /** A dormant core gem distilled from dragon's breath (its power comes later). */
    public static final Item DRAGON_CRYSTAL = reg("dragon_crystal",
            new Item(new Item.Settings().rarity(Rarity.RARE)));

    /** The assembled wand itself (built at the Arcane Station; components live in NBT). */
    public static final Item WAND = reg("wand",
            new WandItem(new Item.Settings().maxCount(1).rarity(Rarity.UNCOMMON)));

    private ModItems() {
    }

    private static Item reg(String name, Item item) {
        return Registry.register(Registries.ITEM, new Identifier(CircleMageMod.MOD_ID, name), item);
    }

    public static void register() {
        // Mana infusion: channelling while holding string converts it into Arcane String.
        ManaInfusion.register(Items.STRING, ARCANE_STRING);

        // Make the new items reachable in creative for testing/building.
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(entries -> {
            entries.add(MANA_CRYSTAL);
            entries.add(ARCANE_STRING);
            entries.add(ARCANE_YARN);
            entries.add(FRAGMENT_OF_LAW);
            entries.add(MANA_DUST);
            entries.add(MANA_LIQUID);
            entries.add(ARCANE_WOOD);
            entries.add(ARCANE_GLUE);
            entries.add(TOME_FIRE);
            entries.add(TOME_WATER);
            entries.add(TOME_EARTH);
            entries.add(TOME_WIND);
            entries.add(TOME_MISC);
            entries.add(GLOW_CRYSTAL);
            entries.add(DRAGON_CRYSTAL);
        });
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FOOD_AND_DRINK).register(entries ->
                entries.add(MANA_VIAL));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries ->
                entries.add(ARCANE_FOCUS));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> {
            entries.add(MAGE_ROBE_IRON);
            entries.add(MAGE_ROBE_AMETHYST);
            entries.add(MAGE_ROBE_EMERALD);
            entries.add(MAGE_ROBE_DIAMOND);
        });
    }
}
