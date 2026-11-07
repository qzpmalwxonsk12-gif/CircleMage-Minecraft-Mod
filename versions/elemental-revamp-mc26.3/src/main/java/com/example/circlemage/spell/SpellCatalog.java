package com.example.circlemage.spell;

import java.util.List;

/** One source of truth for the tree, tome pools and authoritative tier/loadout rules. */
public final class SpellCatalog {
    public enum School {
        FIRE("Fire", 0xFFE87943), WATER("Water", 0xFF69BBE8),
        EARTH("Earth", 0xFFAC9365), WIND("Wind", 0xFF8CCEAD), MISC("Misc", 0xFFBC9AE2);
        public final String label;
        public final int color;
        School(String label, int color) { this.label = label; this.color = color; }
    }
    public record Entry(Spell spell, String name, School school, int tier, String description) {}
    public static final List<Entry> ALL = List.of(
        new Entry(Spell.FIRE_BOLT, "Fireball", School.FIRE, 1, "1 HP on impact; ignites for 5 seconds."),
        new Entry(Spell.ICE_VOLLEY, "Ice Volley", School.WATER, 1, "3 spikes, 1 HP each; each adds 5% slow for 4s (cap 30%)."),
        new Entry(Spell.STONE_SPIKE, "Stone Spike", School.EARTH, 1, "A ground spike erupts for 3 HP; requires ground nearby."),
        new Entry(Spell.WIND_BLADE, "Wind Blades", School.WIND, 1, "3 blades, 1 HP each, with a small knockback."),
        new Entry(Spell.MAGIC_MISSILE, "Magic Missile", School.MISC, 1, "Homing light bolt: 4 HP base, boosted by realm and equipment effects."),
        new Entry(Spell.FIREBALL, "Area Fire", School.FIRE, 2, "A burning field at the cursor. Radius 3, duration 5s."),
        new Entry(Spell.WATER_BUBBLE, "Water Bubble", School.WATER, 2, "A head bubble: 6 HP over 4s. Flame Rebirth/Dispel removes it."),
        new Entry(Spell.STONE_FIST, "Stone Fist", School.EARTH, 2, "Ground fist: 5 HP and knockback. Requires ground."),
        new Entry(Spell.STONE_DOME, "Stone Dome", School.EARTH, 2, "A 6s stone cage at the cursor; look down to protect yourself."),
        new Entry(Spell.WALL_OF_WIND, "Wind Wall", School.WIND, 2, "Push enemies away with a frontal gust."),
        new Entry(Spell.DASH, "Wind Escape", School.WIND, 2, "A movement burst in the held movement direction."),
        new Entry(Spell.VINE_GRAB, "Vine Grab", School.MISC, 2, "Root a target for 3s, within 2 blocks of the cursor."),
        new Entry(Spell.FLAME_REBIRTH, "Flame Rebirth", School.FIRE, 3, "Pay 3 HP to cleanse harmful effects, fire, webs and bubbles."),
        new Entry(Spell.SOOTHING_WATER, "Soothing Water", School.WATER, 3, "30s: double natural mana, fire immunity, steam immunity. Cast outside steam."),
        new Entry(Spell.TRACKING_MARK, "Mark", School.MISC, 3, "Glow/tracking on an aimed target for 30s."),
        new Entry(Spell.CLEAR_MAGIC, "Dispel / Anti Magic", School.MISC, 4, "Cleanse magic and gain 5s harmful-effect immunity. Guardian epiphany unlock."),
        new Entry(Spell.STEAM_EXPLOSION, "Steam Explosion", School.WATER, 3, "Fireball + Ice Volley knowledge: a radius-6 steam cloud for 30s.")
    );
    private SpellCatalog() {}
    public static Entry entry(int id) { return ALL.stream().filter(e -> e.spell.id == id).findFirst().orElse(null); }
    public static int tier(int id) { Entry e = entry(id); return e == null ? Integer.MAX_VALUE : e.tier; }
    public static boolean known(int learned, int id) {
        if (id == Spell.STEAM_EXPLOSION.id) return bit(learned, Spell.FIRE_BOLT.id) && bit(learned, Spell.ICE_VOLLEY.id);
        return entry(id) != null && bit(learned, id);
    }
    public static boolean bit(int mask, int id) { return id >= 0 && id < 32 && (mask & (1 << id)) != 0; }
    public static boolean usable(int circle, int learned, int bound, boolean unlimited, int id) {
        int tier = tier(id);
        return tier <= circle && known(learned, id) && (tier > 2 || bit(bound, id));
    }
    public static int countBound(int mask, int tier) {
        return (int) ALL.stream().filter(e -> e.tier == tier && bit(mask, e.spell.id)).count();
    }
    /** Returns a reason instead of mutating when an equip request is invalid. */
    public static String bindError(int circle, int learned, int bound, boolean unlimited, int id) {
        if (entry(id) == null) return "That spell is not part of this magic tree.";
        if (tier(id) > circle) return "Reach circle " + tier(id) + " first.";
        if (!known(learned, id)) return "Learn this magic from its school tome first.";
        if (tier(id) == 2 && !unlimited && !bit(bound, id) && countBound(bound, 2) >= 3)
            return "Your three T2 slots are full. Unbind a spell in J first.";
        return null;
    }
    public static int bind(int bound, int id, boolean unlimited) {
        if (tier(id) == 1 && !unlimited) {
            for (Entry e : ALL) if (e.tier == 1) bound &= ~(1 << e.spell.id);
        }
        return bound | (1 << id);
    }
}
