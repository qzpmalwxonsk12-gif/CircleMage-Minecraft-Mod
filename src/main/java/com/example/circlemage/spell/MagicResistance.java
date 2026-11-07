package com.example.circlemage.spell;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.registry.tag.DamageTypeTags;

/**
 * Innate magic resistance — a shared stat for mages (the player, Wandering Mages and The Master).
 *
 * <p>Every circle of magehood grants a flat <b>5% resistance</b> to magical harm ("fire, magic missile,
 * etc."): a 3rd-circle mage shrugs off 15%, a 5th-circle mage 25%, and so on. The bonus stacks with
 * worn {@link com.example.circlemage.item.MageRobeItem Mage's Robes} (which reduce Magic Missile damage
 * at the projectile) and, for The Master, with his own natural and levitating bonuses.</p>
 *
 * <p>"Resistible" damage is fire and raw magic (Magic Missile / Fireball / meteor / the Dark Orb's magic
 * hits). Lightning is deliberately excluded: the Wandering Mage already has its own diminishing-Thunder
 * mechanic, and stacking both would double-dip.</p>
 */
public final class MagicResistance {

    /** Flat magic resistance granted per circle of magehood. */
    public static final float PER_CIRCLE = 0.05f;

    /** Resistance is always capped below total immunity so a mage can never be untouchable. */
    public static final float MAX_RESISTANCE = 0.95f;

    private MagicResistance() {
    }

    /** The innate magic resistance (0..1) of a mage at the given circle. */
    public static float forCircle(int circle) {
        return clamp(PER_CIRCLE * Math.max(0, circle));
    }

    /** Clamps a resistance fraction into [0, {@link #MAX_RESISTANCE}]. */
    public static float clamp(float resistance) {
        return Math.max(0.0f, Math.min(MAX_RESISTANCE, resistance));
    }

    /**
     * True if {@code source} is magical harm that innate magic resistance mitigates (fire or raw magic).
     * Unavoidable damage (the void, {@code /kill}, etc.) is never resisted.
     */
    public static boolean isResistible(DamageSource source) {
        if (source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        return source.isIn(DamageTypeTags.IS_FIRE)
                || source.isOf(DamageTypes.MAGIC)
                || source.isOf(DamageTypes.INDIRECT_MAGIC);
    }
}
