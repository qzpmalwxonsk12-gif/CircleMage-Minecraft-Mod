package com.example.circlemage.util;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
public final class Messages {
    public static void send(Player player, Component text, boolean overlay) {
        if (overlay) player.sendOverlayMessage(text); else player.sendSystemMessage(text);
    }
    private Messages() {}
}
