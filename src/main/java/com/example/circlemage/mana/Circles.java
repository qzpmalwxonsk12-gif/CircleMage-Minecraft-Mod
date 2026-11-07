package com.example.circlemage.mana;

/**
 * Defines the nine "circles" of magehood and the mana thresholds required to ascend to each.
 *
 * THRESHOLDS[c] = total channelled mana required to advance FROM circle (c-1) TO circle c.
 * Index 0 is unused (you start at circle 0, the threshold to reach circle 1 lives at index 1).
 */
public final class Circles {

    public static final int MAX_CIRCLE = 9;

    // index:        0   1     2      3       4       5        6        7        8         9
    public static final long[] THRESHOLDS = {
            0L, 100L, 5000L, 20000L, 50000L, 100000L, 200000L, 400000L, 700000L, 10_000_000_000L
    };

    private Circles() {
    }

    /** Mana required to advance from {@code circle} to {@code circle + 1}. Returns the final
     *  threshold once the mage is already at the maximum circle. */
    public static long nextThreshold(int circle) {
        if (circle < 0) {
            return THRESHOLDS[1];
        }
        if (circle >= MAX_CIRCLE) {
            return THRESHOLDS[MAX_CIRCLE];
        }
        return THRESHOLDS[circle + 1];
    }

    /** The fourth circle (and only the fourth, per the spec) is gated behind the
     *  "basic anti magic" advancement in addition to its mana cost. */
    public static boolean requiresAdvancement(int targetCircle) {
        return targetCircle == 4;
    }
}
