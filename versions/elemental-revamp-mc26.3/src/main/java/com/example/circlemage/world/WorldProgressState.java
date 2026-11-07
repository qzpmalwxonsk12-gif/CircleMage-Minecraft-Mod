package com.example.circlemage.world;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.SavedDataStorage;

/**
 * World-global progression flags for the mage-encounter chain, persisted on the Overworld's
 * {@link SavedDataStorage} so they survive restarts and are shared across all dimensions.
 *
 * <p>This is the foundation for the future <b>Master</b> encounter framework. The only flag that
 * matters today is {@link #PRODIGY_DEFEATED} ({@link #isProdigyDefeated()}): once The Prodigy has
 * been slain it stays {@code true} forever, no further Prodigies may spawn, and {@link #canMasterSpawn()}
 * begins returning {@code true} so a later update can add Master spawn logic <i>without</i> touching
 * existing progression data or requiring a world reset.</p>
 */
public class WorldProgressState extends SavedData {

    /** Save id under the Overworld's persistent-state folder. */
    private static final String KEY = "circlemage_progress";

    /** Permanent: has The Prodigy ever been defeated in this world? Gates all future Master content. */
    private boolean prodigyDefeated = false;
    /** Transient-but-persisted: is a Prodigy currently alive somewhere in the world? Enforces "only one". */
    private boolean prodigyAlive = false;
    /** Permanent: has The Master been defeated? (future boss — tracked now for the journal/questline). */
    private boolean masterDefeated = false;
    /** Permanent: has The Lich been defeated? (future boss — tracked now for the journal/questline). */
    private boolean lichDefeated = false;

    /**
     * Mage towers (v2.3.0) that have already been initialised — chests filled, guardian spawned.
     * Keyed by the packed centre of the structure's bounding box so each tower fires exactly once.
     */
    private final java.util.Set<Long> initializedTowers = new java.util.HashSet<>();

    public WorldProgressState() {
    }

    /** Always resolves against the Overworld so the flags are world-global. */
    public static WorldProgressState get(ServerLevel anyWorld) {
        ServerLevel overworld = anyWorld.getServer().overworld();
        SavedDataStorage manager = overworld.getDataStorage();
        return manager.computeIfAbsent(new net.minecraft.world.level.saveddata.SavedDataType<>(
            net.minecraft.resources.Identifier.withDefaultNamespace(KEY), WorldProgressState::new,
            CompoundTag.CODEC.xmap(WorldProgressState::fromNbt, state -> state.save(new CompoundTag())), null));
    }

    private static WorldProgressState fromNbt(CompoundTag nbt) {
        WorldProgressState state = new WorldProgressState();
        state.prodigyDefeated = nbt.getBooleanOr("prodigyDefeated", false);
        state.prodigyAlive = nbt.getBooleanOr("prodigyAlive", false);
        state.masterDefeated = nbt.getBooleanOr("masterDefeated", false);
        state.lichDefeated = nbt.getBooleanOr("lichDefeated", false);
        for (long key : nbt.getLongArray("initializedTowers").orElseGet(() -> new long[0])) {
            state.initializedTowers.add(key);
        }
        return state;
    }

    public CompoundTag save(CompoundTag nbt) {
        nbt.putBoolean("prodigyDefeated", prodigyDefeated);
        nbt.putBoolean("prodigyAlive", prodigyAlive);
        nbt.putBoolean("masterDefeated", masterDefeated);
        nbt.putBoolean("lichDefeated", lichDefeated);
        nbt.putLongArray("initializedTowers", initializedTowers.stream().mapToLong(Long::longValue).toArray());
        return nbt;
    }

    // --- Mage towers (v2.3.0) ---------------------------------------------------

    public boolean isTowerInitialized(long key) {
        return initializedTowers.contains(key);
    }

    public void markTowerInitialized(long key) {
        if (initializedTowers.add(key)) {
            setDirty();
        }
    }

    // --- Prodigy --------------------------------------------------------------

    public boolean isProdigyDefeated() {
        return prodigyDefeated;
    }

    public boolean isProdigyAlive() {
        return prodigyAlive;
    }

    /** True if a brand-new Prodigy is allowed to spawn (none alive, none ever defeated). */
    public boolean canProdigySpawn() {
        return !prodigyDefeated && !prodigyAlive;
    }

    public void setProdigyAlive(boolean alive) {
        if (this.prodigyAlive != alive) {
            this.prodigyAlive = alive;
            setDirty();
        }
    }

    /** Called when The Prodigy is slain: locks out all future Prodigies and unlocks the Master tier. */
    public void markProdigyDefeated() {
        boolean changed = !prodigyDefeated || prodigyAlive;
        this.prodigyDefeated = true;
        this.prodigyAlive = false;
        if (changed) {
            setDirty();
        }
    }

    // --- Future bosses (Master / Lich) — tracked now for the journal & questline -------------

    public boolean isMasterDefeated() {
        return masterDefeated;
    }

    public void markMasterDefeated() {
        if (!masterDefeated) {
            masterDefeated = true;
            setDirty();
        }
    }

    public boolean isLichDefeated() {
        return lichDefeated;
    }

    public void markLichDefeated() {
        if (!lichDefeated) {
            lichDefeated = true;
            setDirty();
        }
    }

    // --- Future Master framework ---------------------------------------------

    /**
     * Eligibility gate for the (not-yet-implemented) Master encounters. Master spawn systems added in
     * a future update should consult this before spawning; it only opens once The Prodigy is defeated.
     */
    public boolean canMasterSpawn() {
        return prodigyDefeated;
    }
}
