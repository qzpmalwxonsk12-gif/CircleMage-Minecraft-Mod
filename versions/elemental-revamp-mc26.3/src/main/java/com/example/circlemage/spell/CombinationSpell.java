package com.example.circlemage.spell;

import com.example.circlemage.mana.ManaData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;

/**
 * Common framework for combination spells (cast by active-chant-priming spell A then pressing spell B).
 * Subclasses declare their trigger ({@link #matches}), cost/cooldown, optional extra requirement, and
 * effect ({@link #cast}). Concrete combinations live in {@link Combinations}.
 */
public abstract class CombinationSpell {

    public final int id;
    public final String name;
    public final int manaCost;
    public final int cooldownTicks;

    protected CombinationSpell(int id, String name, int manaCost, int cooldownTicks) {
        this.id = id;
        this.name = name;
        this.manaCost = manaCost;
        this.cooldownTicks = cooldownTicks;
    }

    /**
     * @param primed     the resolved primed (held) spell
     * @param primedFull whether the primed spell was held to full charge
     * @param second     the resolved second (pressed) spell
     */
    public abstract boolean matches(Spell primed, boolean primedFull, Spell second);

    /** Extra requirement beyond mana/cooldown; return an error message to block, or null to allow. */
    public String requirementError(ServerPlayer player, ManaData data) {
        return null;
    }

    /** Performs the effect. Returns true if it actually fired (mana is only spent on success). */
    public abstract boolean cast(ServerLevel world, ServerPlayer player, ManaData data);

    /** Shared helper: an owned lightning bolt at a point. */
    protected static void strike(ServerLevel world, ServerPlayer owner, double x, double y, double z) {
        LightningBolt bolt = net.minecraft.world.entity.EntityTypes.LIGHTNING_BOLT.create(world, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        if (bolt != null) {
            bolt.snapTo(x, y, z);
            bolt.setCause(owner);
            world.addFreshEntity(bolt);
        }
    }
}
