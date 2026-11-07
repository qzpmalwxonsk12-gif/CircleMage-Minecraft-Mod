package com.example.circlemage.spell;

/**
 * The castable spells, each with its mana cost, cooldown and base cast time (in server ticks;
 * 20 ticks = 1 second). The {@code id} is the value sent over the network in the CAST packet.
 */
public enum Spell {
    // Stable wire IDs preserve the imported NPC/boss implementations. Player tiers are in SpellCatalog.
    MAGIC_MISSILE(0, 50, 20, 0),
    FIREBALL(1, 300, 140, 0),       // Player-facing Area Fire.
    AREA_SLOW(2, 500, 100, 15),     // Legacy boss/internal spell.
    CLEAR_MAGIC(3, 750, 200, 0),
    WALL_OF_WIND(4, 200, 60, 0),
    THUNDER(5, 500, 100, 15),       // Legacy boss/internal spell.
    BARRIER(6, 750, 160, 30),       // Legacy boss/internal spell.
    SPEAR_OF_LIGHT(7, 75, 20, 0),   // Legacy boss/internal spell.
    DARK_ORB(8, 600, 120, 0),       // Legacy boss/internal spell.
    DASH(9, 100, 40, 0),
    WIND_BLADE(10, 60, 50, 0),
    FIRE_BOLT(11, 35, 25, 0),      // Player-facing T1 Fireball.
    ICE_VOLLEY(12, 70, 60, 0),
    STONE_SPIKE(13, 65, 55, 0),
    STONE_FIST(14, 160, 80, 0),
    STONE_DOME(15, 300, 200, 0),
    WATER_BUBBLE(16, 240, 160, 0),
    VINE_GRAB(17, 180, 140, 0),
    SOOTHING_WATER(18, 450, 800, 0),
    TRACKING_MARK(19, 120, 200, 0),
    FLAME_REBIRTH(20, 250, 400, 0),
    STEAM_EXPLOSION(21, 600, 600, 0);

    public final int id;
    public final int manaCost;
    public final int cooldownTicks;
    public final int castTimeTicks;

    Spell(int id, int manaCost, int cooldownTicks, int castTimeTicks) {
        this.id = id;
        this.manaCost = manaCost;
        this.cooldownTicks = cooldownTicks;
        this.castTimeTicks = castTimeTicks;
    }

    public static Spell byId(int id) {
        for (Spell spell : values()) {
            if (spell.id == id) {
                return spell;
            }
        }
        return null;
    }
}
