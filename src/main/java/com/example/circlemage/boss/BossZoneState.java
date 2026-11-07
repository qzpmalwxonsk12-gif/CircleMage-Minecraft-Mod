package com.example.circlemage.boss;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

/**
 * Persists all active {@link BossZone}s so domain barriers, meteor rain, restrictions and frequencies
 * survive world reloads (the bug where command effects reset on rejoin). The state lives on the
 * Overworld's persistent-state manager and serialises whatever the {@link BossZoneManager} currently
 * holds; {@code BossZoneManager.markDirty()} flags it for saving on any change.
 */
public class BossZoneState extends PersistentState {

    private static final String KEY = "circlemage_bosszones";

    private BossZoneState() {
    }

    /** Loads (deserialising zones into the manager) or creates the state, and attaches it for saving. */
    public static BossZoneState getAndLoad(MinecraftServer server) {
        BossZoneState state = server.getOverworld().getPersistentStateManager().getOrCreate(
                nbt -> fromNbt(server, nbt), BossZoneState::new, KEY);
        BossZoneManager.attachState(state);
        return state;
    }

    private static BossZoneState fromNbt(MinecraftServer server, NbtCompound nbt) {
        BossZoneState state = new BossZoneState();
        NbtList list = nbt.getList("zones", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            BossZone zone = BossZone.fromNbt(server, list.getCompound(i));
            if (zone != null) {
                BossZoneManager.register(zone);
            }
        }
        return state;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList list = new NbtList();
        for (BossZone zone : BossZoneManager.getZones()) {
            list.add(zone.writeNbt());
        }
        nbt.put("zones", list);
        return nbt;
    }
}
