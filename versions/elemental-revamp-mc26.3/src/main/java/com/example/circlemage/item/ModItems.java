package com.example.circlemage.item;

import com.example.circlemage.CircleMageMod;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;

public final class ModItems {

    /** A rare blue crystal that permanently doubles mana gain when attuned (max 3). */
    public static final Item MANA_CRYSTAL = reg("mana_crystal",
            new ManaCrystalItem(properties("mana_crystal").stacksTo(16).rarity(Rarity.RARE)));

    /** Vanilla string infused with mana — crafted into Arcane Yarn. Glows. */
    public static final Item ARCANE_STRING = reg("arcane_string",
            new ArcaneStringItem(properties("arcane_string")));

    /** Arcane Yarn — the spun form of Arcane String, woven into Mage's Robes. */
    public static final Item ARCANE_YARN = reg("arcane_yarn", new Item(properties("arcane_yarn")));

    // Mage's Robes (chestplate slot). Magic resistance: iron/amethyst 30%, emerald 40%, diamond 50%.
    public static final Item MAGE_ROBE_IRON = reg("mage_robe_iron",
            new MageRobeItem(RobeArmorMaterial.IRON, 0.30f, properties("mage_robe_iron").rarity(Rarity.UNCOMMON)));
    public static final Item MAGE_ROBE_AMETHYST = reg("mage_robe_amethyst",
            new MageRobeItem(RobeArmorMaterial.AMETHYST, 0.30f, properties("mage_robe_amethyst").rarity(Rarity.UNCOMMON)));
    public static final Item MAGE_ROBE_EMERALD = reg("mage_robe_emerald",
            new MageRobeItem(RobeArmorMaterial.EMERALD, 0.40f, properties("mage_robe_emerald").rarity(Rarity.UNCOMMON)));
    public static final Item MAGE_ROBE_DIAMOND = reg("mage_robe_diamond",
            new MageRobeItem(RobeArmorMaterial.DIAMOND, 0.50f, properties("mage_robe_diamond").rarity(Rarity.RARE)));

    /** Dropped by The Master — a glowing shard of higher magical knowledge (foreshadows the Laws). */
    public static final Item FRAGMENT_OF_LAW = reg("fragment_of_law",
            new FragmentOfLawItem(properties("fragment_of_law").stacksTo(16).rarity(Rarity.EPIC)));

    // --- v2 items -------------------------------------------------------------

    /** Residue shed by Wandering Mages — the base ingredient of bottled mana. */
    public static final Item MANA_DUST = reg("mana_dust", new Item(properties("mana_dust")));

    /** Bottled mana: drink to restore 500 mana on the spot. */
    public static final Item MANA_VIAL = reg("mana_vial",
            new ManaVialItem(properties("mana_vial").stacksTo(16)));

    /** A crystal-tipped wand: held-in-hand spell discount/damage boost + channel toggle on use. */
    public static final Item ARCANE_FOCUS = reg("arcane_focus",
            new ArcaneFocusItem(properties("arcane_focus").stacksTo(1).rarity(Rarity.UNCOMMON)));

    // --- v2.1: magic tomes -----------------------------------------------------

    public static final Item TOME_FIRE = reg("tome_fire",
            new MagicTomeItem(MagicTomeItem.Element.FIRE, properties("tome_fire").stacksTo(16).rarity(Rarity.UNCOMMON)));
    public static final Item TOME_WATER = reg("tome_water",
            new MagicTomeItem(MagicTomeItem.Element.WATER, properties("tome_water").stacksTo(16).rarity(Rarity.UNCOMMON)));
    public static final Item TOME_EARTH = reg("tome_earth",
            new MagicTomeItem(MagicTomeItem.Element.EARTH, properties("tome_earth").stacksTo(16).rarity(Rarity.UNCOMMON)));
    public static final Item TOME_WIND = reg("tome_wind",
            new MagicTomeItem(MagicTomeItem.Element.WIND, properties("tome_wind").stacksTo(16).rarity(Rarity.UNCOMMON)));
    public static final Item TOME_MISC = reg("tome_misc",
            new MagicTomeItem(MagicTomeItem.Element.MISC, properties("tome_misc").stacksTo(16).rarity(Rarity.UNCOMMON)));

    // --- v2.1: wand-making framework --------------------------------------------

    /** Distilled mana: dust dissolved in bottled water. The bottle survives crafting. */
    public static final Item MANA_LIQUID = reg("mana_liquid",
            new Item(properties("mana_liquid").stacksTo(16).craftRemainder(net.minecraft.world.item.Items.GLASS_BOTTLE)));

    /** Planks soaked in mana liquid — the wand-craft's structural wood. */
    public static final Item ARCANE_WOOD = reg("arcane_wood", new Item(properties("arcane_wood")));

    /** The binding agent for wand assembly (slot A of the Arcane Station). */
    public static final Item ARCANE_GLUE = reg("arcane_glue", new Item(properties("arcane_glue")));

    // --- v2.2: wand assembly ----------------------------------------------------

    /** A radiant core gem: a wand built around it sheds torchlight while held. */
    public static final Item GLOW_CRYSTAL = reg("glow_crystal", new Item(properties("glow_crystal")));

    /** A dormant core gem distilled from dragon's breath (its power comes later). */
    public static final Item DRAGON_CRYSTAL = reg("dragon_crystal",
            new Item(properties("dragon_crystal").rarity(Rarity.RARE)));

    /** The assembled wand itself (built at the Arcane Station; components live in NBT). */
    public static final Item WAND = reg("wand",
            new WandItem(properties("wand").stacksTo(1).rarity(Rarity.UNCOMMON)));

    private ModItems() {
    }

    private static Item.Properties properties(String name) { return new Item.Properties().setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM, Identifier.fromNamespaceAndPath(CircleMageMod.MOD_ID, name))); }

    private static Item reg(String name, Item item) {
        return Registry.register(BuiltInRegistries.ITEM, Identifier.fromNamespaceAndPath(CircleMageMod.MOD_ID, name), item);
    }

    public static void register() {
        // Mana infusion: channelling while holding string converts it into Arcane String.
        ManaInfusion.register(Items.STRING, ARCANE_STRING);

        // Make the new items reachable in creative for testing/building.
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(entries -> {
            entries.accept(MANA_CRYSTAL);
            entries.accept(ARCANE_STRING);
            entries.accept(ARCANE_YARN);
            entries.accept(FRAGMENT_OF_LAW);
            entries.accept(MANA_DUST);
            entries.accept(MANA_LIQUID);
            entries.accept(ARCANE_WOOD);
            entries.accept(ARCANE_GLUE);
            entries.accept(TOME_FIRE);
            entries.accept(TOME_WATER);
            entries.accept(TOME_EARTH);
            entries.accept(TOME_WIND);
            entries.accept(TOME_MISC);
            entries.accept(GLOW_CRYSTAL);
            entries.accept(DRAGON_CRYSTAL);
        });
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS).register(entries ->
                entries.accept(MANA_VIAL));
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(entries ->
                entries.accept(ARCANE_FOCUS));
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(entries -> {
            entries.accept(MAGE_ROBE_IRON);
            entries.accept(MAGE_ROBE_AMETHYST);
            entries.accept(MAGE_ROBE_EMERALD);
            entries.accept(MAGE_ROBE_DIAMOND);
        });
    }
}
