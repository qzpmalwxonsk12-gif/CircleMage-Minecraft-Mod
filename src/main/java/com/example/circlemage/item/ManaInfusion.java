package com.example.circlemage.item;

import net.minecraft.item.Item;

import java.util.HashMap;
import java.util.Map;

/**
 * A small framework for "infusing" items with mana. While a player channels mana and holds an
 * infusable item, each second of channelling converts one of that item into its arcane form instead
 * of accumulating mana. Mappings are registered at startup (see {@link ModItems#register}); add more
 * with {@link #register} to support future infusions.
 */
public final class ManaInfusion {

    private static final Map<Item, Item> RECIPES = new HashMap<>();

    private ManaInfusion() {
    }

    public static void register(Item input, Item output) {
        RECIPES.put(input, output);
    }

    /** The arcane form of {@code input}, or null if it cannot be infused. */
    public static Item outputFor(Item input) {
        return RECIPES.get(input);
    }

    public static boolean isInfusable(Item input) {
        return RECIPES.containsKey(input);
    }
}
