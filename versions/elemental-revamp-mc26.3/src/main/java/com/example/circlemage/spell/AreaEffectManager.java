package com.example.circlemage.spell;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.world.entity.player.Player;

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
    public static void clear() { ACTIVE.clear(); }

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
    public static int clearAffecting(Player player) {
        int cleared = 0;
        Iterator<AreaEffect> it = ACTIVE.iterator();
        while (it.hasNext()) {
            AreaEffect effect=it.next();
            if (effect.world() == player.level() && effect.contains(player.position())) {
                it.remove();
                cleared++;
            }
        }
        return cleared;
    }
}
