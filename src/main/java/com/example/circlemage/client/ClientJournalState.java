package com.example.circlemage.client;

/**
 * Client-side mirror of the Mage Journal snapshot (JOURNAL_SYNC). Populated on join and whenever the
 * Spellbook is opened (the screen requests a fresh copy), and read by {@link SpellbookScreen}.
 */
public final class ClientJournalState {

    public static boolean received = false;

    public static int circle = 0;
    public static boolean antiMagic = false;
    public static boolean combination = false;
    public static boolean arrays = false;
    public static boolean chants = false;
    public static boolean laws = false;
    public static int unlockedLaws = 0;
    public static int realmBuffs = 0;

    public static boolean encounteredMage = false;
    public static boolean prodigyDefeated = false;
    public static boolean masterDefeated = false;
    public static boolean lichDefeated = false;

    private ClientJournalState() {
    }

    public static void update(int circle, boolean antiMagic, boolean combination, boolean arrays,
                              boolean chants, boolean laws, int unlockedLaws, int realmBuffs,
                              boolean encounteredMage, boolean prodigyDefeated,
                              boolean masterDefeated, boolean lichDefeated) {
        ClientJournalState.circle = circle;
        ClientJournalState.antiMagic = antiMagic;
        ClientJournalState.combination = combination;
        ClientJournalState.arrays = arrays;
        ClientJournalState.chants = chants;
        ClientJournalState.laws = laws;
        ClientJournalState.unlockedLaws = unlockedLaws;
        ClientJournalState.realmBuffs = realmBuffs;
        ClientJournalState.encounteredMage = encounteredMage;
        ClientJournalState.prodigyDefeated = prodigyDefeated;
        ClientJournalState.masterDefeated = masterDefeated;
        ClientJournalState.lichDefeated = lichDefeated;
        ClientJournalState.received = true;
    }

    public static boolean isLawUnlocked(int ordinal) {
        return ordinal == 0 || (unlockedLaws & (1 << ordinal)) != 0;
    }
}
