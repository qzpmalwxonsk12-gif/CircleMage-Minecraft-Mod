package com.example.circlemage.spell;

import net.minecraft.entity.player.PlayerEntity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Tracks all active {@link AreaEffect}s and ticks them from the server tick loop. State is transient
 * (effects do not survive a restart, which is fine for short-lived spell fields).
 */
public final class AreaEffectManager {

    private static final List<AreaEffect> ACTIVE = new ArrayList<>();

    private AreaEffectManager() {
    }

    public static void add(AreaEffect effect) {
        ACTIVE.add(effect);
    }

    public static void tickAll() {
        Iterator<AreaEffect> it = ACTIVE.iterator();
        while (it.hasNext()) {
            AreaEffect effect = it.next();
            effect.tick();
            if (effect.isExpired()) {
                it.remove();
            }
        }
    }

    /** Removes every active field that currently overlaps the player. Returns how many were cleared. */
    public static int clearAffecting(PlayerEntity player) {
        int cleared = 0;
        Iterator<AreaEffect> it = ACTIVE.iterator();
        while (it.hasNext()) {
            if (it.next().contains(player.getPos())) {
                it.remove();
                cleared++;
            }
        }
        return cleared;
    }
}
