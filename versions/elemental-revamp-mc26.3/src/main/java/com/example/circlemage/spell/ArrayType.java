package com.example.circlemage.spell;

/**
 * The effects a carved Magic Array can be set to. The ordinal is what the client sends when the
 * player picks one in the inventory UI.
 */
public enum ArrayType {
    FIRE("Fire"),
    THUNDER("Thunder"),
    WIND("Wind"),
    BIND("Bind");

    public final String displayName;

    ArrayType(String displayName) {
        this.displayName = displayName;
    }

    public static ArrayType byId(int id) {
        ArrayType[] values = values();
        if (id < 0 || id >= values.length) {
            return FIRE;
        }
        return values[id];
    }
}
