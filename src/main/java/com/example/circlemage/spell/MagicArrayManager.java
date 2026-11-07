package com.example.circlemage.spell;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Tracks active carved {@link MagicArray}s and ticks them from the server tick loop (transient). */
public final class MagicArrayManager {

    private static final List<MagicArray> ACTIVE = new ArrayList<>();

    private MagicArrayManager() {
    }

    public static void add(MagicArray array) {
        ACTIVE.add(array);
    }

    public static void tickAll() {
        Iterator<MagicArray> it = ACTIVE.iterator();
        while (it.hasNext()) {
            MagicArray array = it.next();
            array.tick();
            if (array.isExpired()) {
                it.remove();
            }
        }
    }
}
