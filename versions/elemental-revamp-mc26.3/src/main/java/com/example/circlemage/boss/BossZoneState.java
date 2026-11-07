package com.example.circlemage.boss;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Persists all active {@link BossZone}s so domain barriers, meteor rain, restrictions and frequencies
 * survive world reloads (the bug where command effects reset on rejoin). The state lives on the
 * Overworld's persistent-state manager and serialises whatever the {@link BossZoneManager} currently
 * holds; {@code BossZoneManager.markDirty()} flags it for saving on any change.
 */
public class BossZoneState extends SavedData {

    private static final String KEY = "circlemage_bosszones";

    private BossZoneState() {
    }

    /** Loads (deserialising zones into the manager) or creates the state, and attaches it for saving. */
    public static BossZoneState getAndLoad(MinecraftServer server) {
        BossZoneState state = server.overworld().getDataStorage().computeIfAbsent(
                new net.minecraft.world.level.saveddata.SavedDataType<>(
                    net.minecraft.resources.Identifier.withDefaultNamespace(KEY), BossZoneState::new,
                    CompoundTag.CODEC.xmap(nbt -> fromNbt(server, nbt), saved -> saved.save(new CompoundTag())), null));
        BossZoneManager.attachState(state);
        return state;
    }

    private static BossZoneState fromNbt(MinecraftServer server, CompoundTag nbt) {
        BossZoneState state = new BossZoneState();
        ListTag list = nbt.getListOrEmpty("zones");
        for (int i = 0; i < list.size(); i++) {
            BossZone zone = BossZone.fromNbt(server, list.getCompoundOrEmpty(i));
            if (zone != null) {
                BossZoneManager.register(zone);
            }
        }
        return state;
    }

    public CompoundTag save(CompoundTag nbt) {
        ListTag list = new ListTag();
        for (BossZone zone : BossZoneManager.getZones()) {
            list.add(zone.writeNbt());
        }
        nbt.put("zones", list);
        return nbt;
    }
}
