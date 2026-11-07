package com.example.circlemage.spell;

/**
 * The castable spells, each with its mana cost, cooldown and base cast time (in server ticks;
 * 20 ticks = 1 second). The {@code id} is the value sent over the network in the CAST packet.
 */
public enum Spell {
    MAGIC_MISSILE(0, 50, 20, 0),    // 1s cooldown, instant
    FIREBALL(1, 300, 140, 0),       // 7s cooldown, instant
    AREA_SLOW(2, 500, 100, 15),     // 5s cooldown, 0.75s cast time
    CLEAR_MAGIC(3, 750, 200, 0),    // 10s cooldown, anti-magic (hold-to-cast handled on the client)
    WALL_OF_WIND(4, 200, 60, 0),    // 3s cooldown, instant
    THUNDER(5, 500, 100, 15),       // 5s cooldown, 0.75s cast time
    BARRIER(6, 750, 160, 30),       // 8s cooldown, 1.5s cast time
    // 5th-circle transform of Magic Missile: faster, hits harder, its own design.
    SPEAR_OF_LIGHT(7, 75, 20, 0),   // 1s cooldown, instant
    // 6th-circle transform of Area Slow: a fired void orb.
    DARK_ORB(8, 600, 120, 0),       // 6s cooldown, instant
    // Movement skill: a quick burst dash in the look direction. Default keybind is unbound
    // (the player chooses it in Options -> Controls).
    DASH(9, 100, 40, 0),           // 2s cooldown, instant
    // Wind Blade: a quick volley of slicing gusts toward the look direction; each blade deals 1/3 of
    // a Magic Missile's damage plus knockback. Default keybind is unbound (player picks it).
    WIND_BLADE(10, 150, 60, 0),    // 3s cooldown, instant (fires a 5-blade volley)
    // Fireball (v2.1, from the Fire Tome): a blazing bolt that deals 2 hearts and ignites its target.
    // (Spell id 1, historically "Fireball", is now displayed as "Area Fire".)
    FIRE_BOLT(11, 100, 40, 0);     // 2s cooldown, instant

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
