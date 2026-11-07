package com.example.circlemage.spell;

import com.example.circlemage.mana.ManaData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

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
    public String requirementError(ServerPlayerEntity player, ManaData data) {
        return null;
    }

    /** Performs the effect. Returns true if it actually fired (mana is only spent on success). */
    public abstract boolean cast(ServerWorld world, ServerPlayerEntity player, ManaData data);

    /** Shared helper: an owned lightning bolt at a point. */
    protected static void strike(ServerWorld world, ServerPlayerEntity owner, double x, double y, double z) {
        LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world);
        if (bolt != null) {
            bolt.refreshPositionAfterTeleport(x, y, z);
            bolt.setChanneler(owner);
            world.spawnEntity(bolt);
        }
    }
}
