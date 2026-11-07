package com.example.circlemage.mana;

import net.minecraft.world.entity.player.Player;

/**
 * Duck interface implemented by {@link Player} (via mixin) to expose its {@link ManaData}.
 */
public interface ManaPlayer {

    ManaData circlemage$getManaData();

    static ManaData get(Player player) {
        return ((ManaPlayer) player).circlemage$getManaData();
    }
}
