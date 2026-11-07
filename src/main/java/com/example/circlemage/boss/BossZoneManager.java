package com.example.circlemage.boss;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Server-side registry that owns and ticks all active {@link BossZone}s. Zones are transient (for
 * events/testing/bosses, not persisted across restarts). Provides the lookups the rest of the mod
 * needs: which zone contains a position, the zone owned by a player, and whether a spell is
 * suppressed for a player by the zone they stand in.
 */
public final class BossZoneManager {

    private static final List<BossZone> ZONES = new ArrayList<>();
    /** The persistent-state handle (attached on load); markDirty() flags it for saving. */
    private static BossZoneState state;

    private BossZoneManager() {
    }

    public static void register(BossZone zone) {
        ZONES.add(zone);
        markDirty();
    }

    public static List<BossZone> getZones() {
        return ZONES;
    }

    /** Called by {@link BossZoneState} on load so changes can be flagged for saving. */
    public static void attachState(BossZoneState s) {
        state = s;
    }

    /** Flags the persisted state dirty so the current zones are written on the next world save. */
    public static void markDirty() {
        if (state != null) {
            state.markDirty();
        }
    }

    /** Clears the live registry (on server stop) so zones never leak between worlds in one session. */
    public static void clear() {
        ZONES.clear();
        state = null;
    }

    /** True if a new zone of {@code radius} at {@code center} would overlap an existing one. */
    public static boolean wouldOverlap(ServerWorld world, Vec3d center, double radius) {
        for (BossZone zone : ZONES) {
            if (zone.getWorld() != world) {
                continue;
            }
            double minSeparation = radius + zone.getRadius();
            if (center.squaredDistanceTo(zone.getCenter()) < minSeparation * minSeparation) {
                return true;
            }
        }
        return false;
    }

    public static BossZone zoneByOwner(UUID owner) {
        for (BossZone zone : ZONES) {
            if (zone.getOwner().equals(owner)) {
                return zone;
            }
        }
        return null;
    }

    public static BossZone zoneContaining(ServerWorld world, Vec3d pos) {
        for (BossZone zone : ZONES) {
            if (zone.getWorld() == world && zone.contains(pos)) {
                return zone;
            }
        }
        return null;
    }

    /** Removes the zone owned by {@code owner}; returns true if one was removed. */
    public static boolean removeByOwner(UUID owner) {
        boolean removed = ZONES.removeIf(zone -> zone.getOwner().equals(owner));
        if (removed) {
            markDirty();
        }
        return removed;
    }

    /** True if the player currently stands in a zone that suppresses the given Spell id. */
    public static boolean isSpellRestricted(ServerPlayerEntity player, int spellId) {
        if (ZONES.isEmpty()) {
            return false;
        }
        ServerWorld world = player.getServerWorld();
        Vec3d pos = player.getPos();
        for (BossZone zone : ZONES) {
            if (zone.getWorld() == world && zone.contains(pos) && zone.isSpellRestricted(spellId)) {
                return true;
            }
        }
        return false;
    }

    public static void tickAll() {
        if (ZONES.isEmpty()) {
            return;
        }
        for (Iterator<BossZone> it = ZONES.iterator(); it.hasNext(); ) {
            BossZone zone = it.next();
            if (zone.getWorld().getServer() == null) {
                it.remove();
                continue;
            }
            zone.tick();
        }
    }
}
