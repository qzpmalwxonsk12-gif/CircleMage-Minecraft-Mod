package com.example.circlemage.spell;

/**
 * Passive chants the player can select once unlocked. Each is always-on while selected; ATTACK and
 * REINFORCE drain mana over time as upkeep.
 */
public enum PassiveChant {
    NONE("No Chant"),
    CHANNELING("Channeling"),   // 2.5x mana regen, spells cost no mana
    ATTACK("Attack"),           // 1.30x spell damage, 200 mana/sec upkeep
    REINFORCE("Reinforce"),     // +20% speed, 30% damage reduction, 200 mana/sec upkeep
    VITALITY("Vitality");       // Regeneration III, 100 mana/sec upkeep

    public final String displayName;

    PassiveChant(String displayName) {
        this.displayName = displayName;
    }

    public static PassiveChant byId(int id) {
        PassiveChant[] values = values();
        if (id < 0 || id >= values.length) {
            return NONE;
        }
        return values[id];
    }
}
