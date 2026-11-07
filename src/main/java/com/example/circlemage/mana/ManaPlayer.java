package com.example.circlemage.mana;

import net.minecraft.entity.player.PlayerEntity;

/**
 * Duck interface implemented by {@link PlayerEntity} (via mixin) to expose its {@link ManaData}.
 */
public interface ManaPlayer {

    ManaData circlemage$getManaData();

    static ManaData get(PlayerEntity player) {
        return ((ManaPlayer) player).circlemage$getManaData();
    }
}
