package com.example.circlemage.client;

/**
 * Client-side mirror of the player's mana state, updated from the server's MANA_SYNC packet
 * and read by the HUD / inventory overlays.
 */
public final class ClientManaState {

    public static double currentMana = 0.0;
    public static int circle = 0;
    public static long threshold = 100;
    public static boolean channeling = false;
    public static int unlockedBuffs = 0;
    public static boolean arrayUnlocked = false;
    public static int selectedArray = 0;
    public static boolean chantUnlocked = false;
    public static int selectedChant = 0;
    public static boolean revertSpear = false;
    public static boolean revertDarkOrb = false;
    public static boolean lawsUnlocked = false;
    public static int selectedLaw = 0;
    public static int unlockedLaws = 0;
    /** v2.1: bitmask of learned spells (bit = Spell id). */
    public static int unlockedSpells = 1;
    public static int boundSpells = 1;
    public static boolean antiMagicEpiphany = false;
    public static boolean magicLimitBroken = false;
    /** v2.2.1: intro/tutorial stage (0 fresh, 1 book absorbed, 2 complete). */
    public static int introStage = 0;

    private ClientManaState() {
    }

    public static void update(double currentMana, int circle, long threshold, boolean channeling,
                              int unlockedBuffs, boolean arrayUnlocked, int selectedArray,
                              boolean chantUnlocked, int selectedChant,
                              boolean revertSpear, boolean revertDarkOrb,
                              boolean lawsUnlocked, int selectedLaw, int unlockedLaws, int unlockedSpells,
                              int introStage, int boundSpells, boolean antiMagicEpiphany, boolean magicLimitBroken) {
        ClientManaState.currentMana = currentMana;
        ClientManaState.circle = circle;
        ClientManaState.threshold = threshold;
        ClientManaState.channeling = channeling;
        ClientManaState.unlockedBuffs = unlockedBuffs;
        ClientManaState.arrayUnlocked = arrayUnlocked;
        ClientManaState.selectedArray = selectedArray;
        ClientManaState.chantUnlocked = chantUnlocked;
        ClientManaState.selectedChant = selectedChant;
        ClientManaState.revertSpear = revertSpear;
        ClientManaState.revertDarkOrb = revertDarkOrb;
        ClientManaState.lawsUnlocked = lawsUnlocked;
        ClientManaState.selectedLaw = selectedLaw;
        ClientManaState.unlockedLaws = unlockedLaws;
        ClientManaState.unlockedSpells = unlockedSpells;
        ClientManaState.introStage = introStage;
        ClientManaState.boundSpells = boundSpells;
        ClientManaState.antiMagicEpiphany = antiMagicEpiphany;
        ClientManaState.magicLimitBroken = magicLimitBroken;
    }

    /** Catalog knowledge, including the Fireball + Ice Volley Steam recipe. */
    public static boolean isSpellUnlocked(int spellId) {
        return com.example.circlemage.spell.SpellCatalog.known(unlockedSpells, spellId);
    }

    /** Law ordinal 0 (NONE) is always selectable; others require their bit. */
    public static boolean isLawUnlocked(int ordinal) {
        return ordinal == 0 || (unlockedLaws & (1 << ordinal)) != 0;
    }

    /** True if this player currently casts Spear of Light in the Magic Missile slot. */
    public static boolean usesSpear() { return false; }

    /** True if this player currently casts Dark Orb in the Area Slow slot. */
    public static boolean usesDarkOrb() { return false; }
}
