package com.example.circlemage.spell;

/**
 * The Laws — the 9th-circle ultimate system. A mage selects one active Law. Some are passive (active
 * while selected), some are event-driven (Life, Causality), and some are triggered with the
 * "Activate Law" key (Spatial Rend, Time Stop, Reversal). {@code category} groups them for the UI.
 */
public enum Law {
    NONE("No Law", "General"),
    LIFE("Law of Life", "Life"),
    SPATIAL_REND("Spatial Rend", "Space"),
    SPACE_MANIPULATION("Space Manipulation", "Space"),
    STORAGE("Storage", "Space"),
    GRAVITY_AMP("Gravity Amplification", "Gravity"),
    BLACK_HOLE("Black Hole", "Gravity"),
    TIME_STOP("Time Stop", "Time"),
    TIME_REVERSAL("Time Reversal", "Time"),
    CREATION("Law of Creation", "Creation"),
    CAUSALITY("Law of Causality", "Causality"),
    LOVE_OF_WORLD("Love of the World", "World");

    public final String displayName;
    public final String category;

    Law(String displayName, String category) {
        this.displayName = displayName;
        this.category = category;
    }

    public static Law byId(int id) {
        Law[] values = values();
        if (id < 0 || id >= values.length) {
            return NONE;
        }
        return values[id];
    }
}
