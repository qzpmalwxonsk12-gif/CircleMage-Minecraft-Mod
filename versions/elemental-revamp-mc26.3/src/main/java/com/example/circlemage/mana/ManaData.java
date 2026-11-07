package com.example.circlemage.mana;

import net.minecraft.nbt.CompoundTag;

/**
 * Per-player magic state. Attached to every {@link net.minecraft.world.entity.player.Player}
 * via {@link com.example.circlemage.mixin.PlayerEntityMixin} and persisted in the player's NBT.
 */
public class ManaData {

    /** Base mana gained per second while channelling, before realm multipliers. */
    public static final double BASE_GAIN_PER_SECOND = 5.0;
    /** Base magic missile damage: 2 hearts. */
    public static final float BASE_DAMAGE = 4.0f;

    private double currentMana = 0.0;
    private int circle = 0;

    /** realmBuffUnlocked[c] == true means the permanent buff for the c-th realm has been earned. */
    private final boolean[] realmBuffUnlocked = new boolean[Circles.MAX_CIRCLE + 1];

    /** Persisted: number of Mana Crystals attuned (0..{@link #MAX_CRYSTALS}); each doubles mana gain. */
    public static final int MAX_CRYSTALS = 3;
    private int crystalsConsumed = 0;

    /** Persisted: whether this player has ever encountered a Wandering Mage (Mage Journal milestone). */
    private boolean encounteredWanderingMage = false;

    /**
     * Persisted intro/tutorial stage (v2.2.1): 0 = fresh (spawn the tutorial book on join),
     * 1 = book absorbed (prompt to press J), 2 = intro complete (book opened & closed once).
     */
    private int introStage = 0;

    /** Transient: whether the player is currently channelling (not persisted). */
    private boolean channeling = false;

    /** Transient: ticks of mana infusion accrued while channelling with an infusable item held. */
    private int infusionProgress = 0;

    /** Transient playtesting aid: extra multiplier on mana gained per second. */
    private double debugManaMultiplier = 1.0;

    /** Transient: world-tick at which each spell's cooldown ends, indexed by spell id. */
    private final long[] cooldownEnds = new long[32];

    /** Persisted: when true the player has reverted Magic Missile back from its Spear of Light form. */
    private boolean revertSpear = false;
    /** Persisted: when true the player has reverted Area Slow back from its Dark Orb form. */
    private boolean revertDarkOrb = false;

    /** Persisted: whether the Laws system has been discovered (gates the 9th circle), and the selected Law. */
    private boolean lawsUnlocked = false;
    private int selectedLaw = 0;
    /** Persisted: bitmask of individually-unlocked Laws (bit = Law ordinal). */
    private int unlockedLaws = 0;
    /** Persisted: unlock-condition progress. */
    private int deathCount = 0;
    private boolean voidComprehended = false;
    private final java.util.Set<String> hostilesHit = new java.util.HashSet<>();
    /** Persisted: the Storage law's extra inventory (27 stacks), kept as NBT. */
    private net.minecraft.nbt.ListTag storageItems = new net.minecraft.nbt.ListTag();

    /** Persisted: the player's single Thunder Mark (a teleport anchor) and its dimension. */
    private boolean hasThunderMark = false;
    private double markX, markY, markZ;
    private String markDim = "";

    /** Transient: combination-spell cooldown ends, indexed by combination id. */
    private final long[] comboCooldownEnds = new long[8];
    /** Transient: Blaze flamethrower runs until this world-tick. */
    private long blazeEndTick = 0;
    /** Transient: Divine Wrath beam target + expiry. */
    private int divineWrathTargetId = -1;
    private long divineWrathEndTick = 0;

    /** Transient: Law of Time (Time Stop) — frozen until this tick, with the mobs whose AI we paused. */
    private long timeStopEndTick = 0;
    private final java.util.Set<Integer> frozenMobs = new java.util.HashSet<>();

    /** Transient: Black Hole's expanding consume radius (shell cursor, 1..30). */
    private int blackHoleRadius = 1;

    /** Transient: world-tick until which the player is immune to negative effects. */
    private long immunityEndTick = 0;

    /** Transient playtesting aid: when false, all cooldowns are ignored. */
    private boolean cooldownsEnabled = true;

    /**
     * Persisted: bitmask of learned spells (bit = Spell id). Magic Missile and Dash arrive with the
     * 1st circle; every other spell is sealed inside a magic tome (v2.1). Anti Magic keeps its own
     * advancement gate and is not tracked here.
     */
    private int unlockedSpells = 1; // Only Magic Missile at spawn.
    private int boundSpells = 1;
    private boolean antiMagicEpiphany = false;
    private boolean magicLimitBroken = false;
    public int getBoundSpells() { return boundSpells; }
    public void setBoundSpells(int mask) { boundSpells = mask; }
    public boolean hasAntiMagicEpiphany() { return antiMagicEpiphany; }
    public void setAntiMagicEpiphany(boolean value) { antiMagicEpiphany = value; }
    public boolean isMagicLimitBroken() { return magicLimitBroken; }
    public void setMagicLimitBroken(boolean value) { magicLimitBroken = value; }

    /** Persisted: whether the Magic Array system has been unlocked. */
    private boolean arrayUnlocked = false;

    /** Persisted: currently selected array type (ordinal of spell.ArrayType). */
    private int selectedArray = 0;

    /** Persisted: whether the passive chant system has been unlocked. */
    private boolean passiveChantUnlocked = false;

    /** Persisted: currently selected passive chant (ordinal of spell.PassiveChant). */
    private int selectedChant = 0;

    // Transient carving (channelled) state.
    private boolean carving = false;
    private long carveEndTick = 0;
    private long carveCooldownEnd = 0;
    private double carveStartX, carveStartY, carveStartZ;

    // Transient pending (cast-time) state.
    private int pendingSpellId = -1;
    private long pendingFireTick = 0;
    private boolean pendingEnhanced = false;

    /** Transient: remaining Wind Blade volley shots and the world-tick the next one fires. */
    private int windBladeShots = 0;
    private long windBladeNextTick = 0;

    /** Transient: entity id of the player's active dome barrier (-1 if none). */
    private int activeDomeId = -1;

    public int getActiveDomeId() {
        return activeDomeId;
    }

    public void setActiveDomeId(int activeDomeId) {
        this.activeDomeId = activeDomeId;
    }

    public boolean isPassiveChantUnlocked() {
        return passiveChantUnlocked;
    }

    public void setPassiveChantUnlocked(boolean passiveChantUnlocked) {
        this.passiveChantUnlocked = passiveChantUnlocked;
    }

    public int getSelectedChant() {
        return selectedChant;
    }

    public void setSelectedChant(int selectedChant) {
        this.selectedChant = selectedChant;
    }

    public boolean isRevertSpear() {
        return revertSpear;
    }

    public void setRevertSpear(boolean revertSpear) {
        this.revertSpear = revertSpear;
    }

    public boolean isRevertDarkOrb() {
        return revertDarkOrb;
    }

    public void setRevertDarkOrb(boolean revertDarkOrb) {
        this.revertDarkOrb = revertDarkOrb;
    }

    // --- Laws ---------------------------------------------------------------

    public boolean isLawsUnlocked() {
        return lawsUnlocked;
    }

    public void setLawsUnlocked(boolean lawsUnlocked) {
        this.lawsUnlocked = lawsUnlocked;
    }

    public int getSelectedLaw() {
        return selectedLaw;
    }

    public void setSelectedLaw(int selectedLaw) {
        this.selectedLaw = selectedLaw;
    }

    public int getUnlockedLaws() {
        return unlockedLaws;
    }

    public void setUnlockedLaws(int unlockedLaws) {
        this.unlockedLaws = unlockedLaws;
    }

    /** Law ordinal 0 (NONE) is always selectable; others require their bit. */
    public boolean isLawUnlocked(int ordinal) {
        return ordinal == 0 || (unlockedLaws & (1 << ordinal)) != 0;
    }

    public void unlockLawBit(int ordinal) {
        unlockedLaws |= (1 << ordinal);
    }

    public int getDeathCount() {
        return deathCount;
    }

    public void incrementDeathCount() {
        deathCount++;
    }

    public boolean isVoidComprehended() {
        return voidComprehended;
    }

    public void setVoidComprehended(boolean voidComprehended) {
        this.voidComprehended = voidComprehended;
    }

    public java.util.Set<String> getHostilesHit() {
        return hostilesHit;
    }

    public net.minecraft.nbt.ListTag getStorageItems() {
        return storageItems;
    }

    public void setStorageItems(net.minecraft.nbt.ListTag storageItems) {
        this.storageItems = storageItems;
    }

    // --- Thunder Mark -------------------------------------------------------

    public boolean hasThunderMark() {
        return hasThunderMark;
    }

    public void setThunderMark(double x, double y, double z, String dimension) {
        this.hasThunderMark = true;
        this.markX = x;
        this.markY = y;
        this.markZ = z;
        this.markDim = dimension;
    }

    public void clearThunderMark() {
        this.hasThunderMark = false;
    }

    public double getMarkX() {
        return markX;
    }

    public double getMarkY() {
        return markY;
    }

    public double getMarkZ() {
        return markZ;
    }

    public String getMarkDim() {
        return markDim;
    }

    // --- Combination cooldowns / channels -----------------------------------

    public long getComboCooldownEnd(int comboId) {
        return (comboId >= 0 && comboId < comboCooldownEnds.length) ? comboCooldownEnds[comboId] : 0;
    }

    public void setComboCooldownEnd(int comboId, long tick) {
        if (comboId >= 0 && comboId < comboCooldownEnds.length) {
            comboCooldownEnds[comboId] = tick;
        }
    }

    public long getBlazeEndTick() {
        return blazeEndTick;
    }

    public void setBlazeEndTick(long tick) {
        this.blazeEndTick = tick;
    }

    public int getDivineWrathTargetId() {
        return divineWrathTargetId;
    }

    public long getDivineWrathEndTick() {
        return divineWrathEndTick;
    }

    public void setDivineWrath(int targetId, long endTick) {
        this.divineWrathTargetId = targetId;
        this.divineWrathEndTick = endTick;
    }

    public long getTimeStopEndTick() {
        return timeStopEndTick;
    }

    public void setTimeStopEndTick(long tick) {
        this.timeStopEndTick = tick;
    }

    public java.util.Set<Integer> getFrozenMobs() {
        return frozenMobs;
    }

    public int getBlackHoleRadius() {
        return blackHoleRadius;
    }

    public void setBlackHoleRadius(int blackHoleRadius) {
        this.blackHoleRadius = blackHoleRadius;
    }

    public int getWindBladeShots() {
        return windBladeShots;
    }

    public long getWindBladeNextTick() {
        return windBladeNextTick;
    }

    /** Begins a Wind Blade volley of {@code shots} blades, the first firing on/after {@code firstTick}. */
    public void startWindBladeVolley(int shots, long firstTick) {
        this.windBladeShots = shots;
        this.windBladeNextTick = firstTick;
    }

    /** Records that one Wind Blade fired; schedules the next (or ends the volley). */
    public void advanceWindBladeVolley(long nextTick) {
        if (windBladeShots > 0) {
            windBladeShots--;
            windBladeNextTick = nextTick;
        }
    }

    public int getPendingSpellId() {
        return pendingSpellId;
    }

    public long getPendingFireTick() {
        return pendingFireTick;
    }

    public boolean isPendingEnhanced() {
        return pendingEnhanced;
    }

    public void setPendingCast(int spellId, long fireTick, boolean enhanced) {
        this.pendingSpellId = spellId;
        this.pendingFireTick = fireTick;
        this.pendingEnhanced = enhanced;
    }

    public void clearPendingCast() {
        this.pendingSpellId = -1;
    }

    /** The 5th-circle buff: 0.85x mana cost and cooldown. */
    public double circleDiscount() {
        return circle >= 5 ? 0.85 : 1.0;
    }

    /** Copies all persistent + session state from another instance (used on respawn). */
    public void copyFrom(ManaData other) {
        this.currentMana = other.currentMana;
        this.circle = other.circle;
        this.unlockedSpells = other.unlockedSpells;
        this.boundSpells = other.boundSpells;
        this.antiMagicEpiphany = other.antiMagicEpiphany;
        this.magicLimitBroken = other.magicLimitBroken;
        System.arraycopy(other.realmBuffUnlocked, 0, this.realmBuffUnlocked, 0, this.realmBuffUnlocked.length);
        this.crystalsConsumed = other.crystalsConsumed;
        this.encounteredWanderingMage = other.encounteredWanderingMage;
        this.introStage = other.introStage;
        this.debugManaMultiplier = other.debugManaMultiplier;
        this.cooldownsEnabled = other.cooldownsEnabled;
        this.arrayUnlocked = other.arrayUnlocked;
        this.selectedArray = other.selectedArray;
        this.passiveChantUnlocked = other.passiveChantUnlocked;
        this.selectedChant = other.selectedChant;
        this.revertSpear = other.revertSpear;
        this.revertDarkOrb = other.revertDarkOrb;
        this.hasThunderMark = other.hasThunderMark;
        this.markX = other.markX;
        this.markY = other.markY;
        this.markZ = other.markZ;
        this.markDim = other.markDim;
        this.lawsUnlocked = other.lawsUnlocked;
        this.selectedLaw = other.selectedLaw;
        this.storageItems = other.storageItems;
        this.unlockedLaws = other.unlockedLaws;
        this.deathCount = other.deathCount;
        this.voidComprehended = other.voidComprehended;
        this.hostilesHit.clear();
        this.hostilesHit.addAll(other.hostilesHit);
    }

    public double getCurrentMana() {
        return currentMana;
    }

    public boolean isCooldownsEnabled() {
        return cooldownsEnabled;
    }

    public void setCooldownsEnabled(boolean cooldownsEnabled) {
        this.cooldownsEnabled = cooldownsEnabled;
    }

    // --- Spell unlocks (v2.1) -------------------------------------------------

    public int getUnlockedSpells() {
        return unlockedSpells;
    }

    public void setUnlockedSpells(int unlockedSpells) {
        this.unlockedSpells = unlockedSpells;
    }

    public boolean isSpellUnlocked(int spellId) {
        return spellId >= 0 && spellId < 32 && (unlockedSpells & (1 << spellId)) != 0;
    }

    /** Learns a spell. Returns true if it was newly learned. */
    public boolean unlockSpell(int spellId) {
        if (spellId < 0 || spellId >= 32 || isSpellUnlocked(spellId)) {
            return false;
        }
        unlockedSpells |= (1 << spellId);
        return true;
    }

    public boolean isArrayUnlocked() {
        return arrayUnlocked;
    }

    public void setArrayUnlocked(boolean arrayUnlocked) {
        this.arrayUnlocked = arrayUnlocked;
    }

    public int getSelectedArray() {
        return selectedArray;
    }

    public void setSelectedArray(int selectedArray) {
        this.selectedArray = selectedArray;
    }

    public boolean isCarving() {
        return carving;
    }

    public void setCarving(boolean carving) {
        this.carving = carving;
    }

    public long getCarveEndTick() {
        return carveEndTick;
    }

    public void setCarveEndTick(long carveEndTick) {
        this.carveEndTick = carveEndTick;
    }

    public long getCarveCooldownEnd() {
        return carveCooldownEnd;
    }

    public void setCarveCooldownEnd(long carveCooldownEnd) {
        this.carveCooldownEnd = carveCooldownEnd;
    }

    public void setCarveStart(double x, double y, double z) {
        this.carveStartX = x;
        this.carveStartY = y;
        this.carveStartZ = z;
    }

    public double carveMovedSquared(double x, double y, double z) {
        double dx = x - carveStartX;
        double dy = y - carveStartY;
        double dz = z - carveStartZ;
        return dx * dx + dy * dy + dz * dz;
    }

    /** Spend mana if affordable; returns true if the cost was paid. */
    public boolean trySpendMana(double cost) {
        if (currentMana < cost) {
            return false;
        }
        currentMana -= cost;
        return true;
    }

    public double getDebugManaMultiplier() {
        return debugManaMultiplier;
    }

    public void setDebugManaMultiplier(double debugManaMultiplier) {
        this.debugManaMultiplier = debugManaMultiplier;
    }

    public long getCooldownEnd(int spellId) {
        return cooldownEnds[spellId];
    }

    public void setCooldownEnd(int spellId, long tick) {
        cooldownEnds[spellId] = tick;
    }

    public long getImmunityEndTick() {
        return immunityEndTick;
    }

    public void setImmunityEndTick(long tick) {
        this.immunityEndTick = tick;
    }

    public boolean isImmune(long now) {
        return now < immunityEndTick;
    }

    public void setCurrentMana(double currentMana) {
        this.currentMana = currentMana;
    }

    public void addMana(double amount) {
        this.currentMana += amount;
    }

    public int getCircle() {
        return circle;
    }

    public void setCircle(int circle) {
        this.circle = circle;
    }

    public boolean isChanneling() {
        return channeling;
    }

    public void setChanneling(boolean channeling) {
        this.channeling = channeling;
    }

    /** Advances the infusion timer; returns true once a full second (20 ticks) has elapsed (and resets). */
    public boolean tickInfusion() {
        if (++infusionProgress >= 20) {
            infusionProgress = 0;
            return true;
        }
        return false;
    }

    public void resetInfusion() {
        infusionProgress = 0;
    }

    public boolean isRealmBuffUnlocked(int circle) {
        if (circle < 1 || circle >= realmBuffUnlocked.length) {
            return false;
        }
        return realmBuffUnlocked[circle];
    }

    /** Returns true if this call actually changed the state (was previously locked). */
    public boolean unlockRealmBuff(int circle) {
        if (circle < 1 || circle >= realmBuffUnlocked.length || realmBuffUnlocked[circle]) {
            return false;
        }
        realmBuffUnlocked[circle] = true;
        return true;
    }

    public int unlockedBuffCount() {
        int count = 0;
        for (int i = 1; i < realmBuffUnlocked.length; i++) {
            if (realmBuffUnlocked[i]) {
                count++;
            }
        }
        return count;
    }

    /**
     * Each unlocked realm buff doubles channelled mana. (Since v2.1, Mana Crystals no longer double
     * channelling — they feed the natural regeneration instead, see {@link #naturalRegenPerSecond}.)
     */
    public double manaMultiplier() {
        return Math.pow(2.0, unlockedBuffCount());
    }

    /**
     * Passive mana per second (v2.1), flowing even without channelling once the 1st circle is
     * reached: +1 per circle, +5 per attuned Mana Crystal.
     */
    public double naturalRegenPerSecond() {
        return circle >= 1 ? circle + 5.0 * crystalsConsumed : 0.0;
    }

    public int getCrystalsConsumed() {
        return crystalsConsumed;
    }

    public int getIntroStage() {
        return introStage;
    }

    public void setIntroStage(int introStage) {
        this.introStage = introStage;
    }

    public boolean isEncounteredWanderingMage() {
        return encounteredWanderingMage;
    }

    /** Records a first Wandering Mage encounter. Returns true if this changed the state. */
    public boolean setEncounteredWanderingMage() {
        if (encounteredWanderingMage) {
            return false;
        }
        encounteredWanderingMage = true;
        return true;
    }

    /** Attunes one more Mana Crystal (capped at {@link #MAX_CRYSTALS}). Returns true if it took effect. */
    public boolean consumeCrystal() {
        if (crystalsConsumed >= MAX_CRYSTALS) {
            return false;
        }
        crystalsConsumed++;
        return true;
    }

    /**
     * Each unlocked realm buff grants a 1.2x magic damage boost. (Reduced globally from 1.5x in
     * 1.0.8 when the Wandering Mage NPC — which also receives realm buffs — was introduced.)
     */
    public static final double REALM_DAMAGE_PER_BUFF = 1.2;

    public float damageMultiplier() {
        return (float) Math.pow(REALM_DAMAGE_PER_BUFF, unlockedBuffCount());
    }

    public void writeNbt(CompoundTag nbt) {
        nbt.putDouble("mana", currentMana);
        nbt.putInt("circle", circle);
        int mask = 0;
        for (int i = 1; i < realmBuffUnlocked.length; i++) {
            if (realmBuffUnlocked[i]) {
                mask |= (1 << i);
            }
        }
        nbt.putInt("realmBuffs", mask);
        nbt.putInt("unlockedSpells", unlockedSpells);
        nbt.putInt("v2BoundSpells", boundSpells);
        nbt.putBoolean("v2AntiMagicEpiphany", antiMagicEpiphany);
        nbt.putBoolean("v2MagicLimitBroken", magicLimitBroken);
        nbt.putInt("crystalsConsumed", crystalsConsumed);
        nbt.putBoolean("encounteredWanderingMage", encounteredWanderingMage);
        nbt.putInt("introStage", introStage);
        nbt.putBoolean("arrayUnlocked", arrayUnlocked);
        nbt.putInt("selectedArray", selectedArray);
        nbt.putBoolean("passiveChantUnlocked", passiveChantUnlocked);
        nbt.putInt("selectedChant", selectedChant);
        nbt.putBoolean("revertSpear", revertSpear);
        nbt.putBoolean("revertDarkOrb", revertDarkOrb);
        nbt.putBoolean("hasThunderMark", hasThunderMark);
        nbt.putDouble("markX", markX);
        nbt.putDouble("markY", markY);
        nbt.putDouble("markZ", markZ);
        nbt.putString("markDim", markDim);
        nbt.putBoolean("lawsUnlocked", lawsUnlocked);
        nbt.putInt("selectedLaw", selectedLaw);
        nbt.put("storageItems", storageItems);
        nbt.putInt("unlockedLaws", unlockedLaws);
        nbt.putInt("deathCount", deathCount);
        nbt.putBoolean("voidComprehended", voidComprehended);
        net.minecraft.nbt.ListTag hostiles = new net.minecraft.nbt.ListTag();
        for (String id : hostilesHit) {
            hostiles.add(net.minecraft.nbt.StringTag.valueOf(id));
        }
        nbt.put("hostilesHit", hostiles);
    }

    public void readNbt(CompoundTag nbt) {
        currentMana = nbt.getDoubleOr("mana", 0d);
        circle = nbt.getIntOr("circle", 0);
        int mask = nbt.getIntOr("realmBuffs", 0);
        for (int i = 1; i < realmBuffUnlocked.length; i++) {
            realmBuffUnlocked[i] = (mask & (1 << i)) != 0;
        }
        unlockedSpells = nbt.getIntOr("unlockedSpells", 0) | 1;
        boundSpells = nbt.contains("v2BoundSpells") ? nbt.getIntOr("v2BoundSpells", 0) : 1;
        antiMagicEpiphany = nbt.getBooleanOr("v2AntiMagicEpiphany", false);
        magicLimitBroken = nbt.getBooleanOr("v2MagicLimitBroken", false);
        // Pre-2.1 save: spells weren't tracked. Grant the circle-1 basics; the rest must be
        // re-learned from magic tomes (the new progression).
        if (!nbt.contains("unlockedSpells") && circle >= 1) {
            unlockSpell(0); // Magic Missile
            // Wind Escape now requires a Wind Tome and circle 2.
        }
        crystalsConsumed = Math.min(MAX_CRYSTALS, nbt.getIntOr("crystalsConsumed", 0));
        encounteredWanderingMage = nbt.getBooleanOr("encounteredWanderingMage", false);
        // Intro stage. Established pre-2.2.1 players (already past the 1st circle) skip the cinematic.
        if (nbt.contains("introStage")) {
            introStage = nbt.getIntOr("introStage", 0);
        } else {
            introStage = circle >= 1 ? 2 : 0;
        }
        arrayUnlocked = nbt.getBooleanOr("arrayUnlocked", false);
        selectedArray = nbt.getIntOr("selectedArray", 0);
        passiveChantUnlocked = nbt.getBooleanOr("passiveChantUnlocked", false);
        selectedChant = nbt.getIntOr("selectedChant", 0);
        revertSpear = nbt.getBooleanOr("revertSpear", false);
        revertDarkOrb = nbt.getBooleanOr("revertDarkOrb", false);
        hasThunderMark = nbt.getBooleanOr("hasThunderMark", false);
        markX = nbt.getDoubleOr("markX", 0d);
        markY = nbt.getDoubleOr("markY", 0d);
        markZ = nbt.getDoubleOr("markZ", 0d);
        markDim = nbt.getStringOr("markDim", "");
        lawsUnlocked = nbt.getBooleanOr("lawsUnlocked", false);
        selectedLaw = nbt.getIntOr("selectedLaw", 0);
        storageItems = nbt.getListOrEmpty("storageItems");
        unlockedLaws = nbt.getIntOr("unlockedLaws", 0);
        deathCount = nbt.getIntOr("deathCount", 0);
        voidComprehended = nbt.getBooleanOr("voidComprehended", false);
        hostilesHit.clear();
        net.minecraft.nbt.ListTag hostiles = nbt.getListOrEmpty("hostilesHit");
        for (int i = 0; i < hostiles.size(); i++) {
            hostilesHit.add(hostiles.getString(i).orElse(""));
        }
    }
}
