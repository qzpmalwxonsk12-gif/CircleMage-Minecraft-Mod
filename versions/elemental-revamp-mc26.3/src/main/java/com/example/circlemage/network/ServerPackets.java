package com.example.circlemage.network;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
public final class ServerPackets {
    @FunctionalInterface public interface Handler {
        void receive(MinecraftServer server, ServerPlayer player, Object connection, FriendlyByteBuf buf, Object sender);
    }
    public static void registerGlobalReceiver(Identifier id, Handler handler) {
        ServerPlayNetworking.registerGlobalReceiver(SpellPayload.type(id), (payload, context) -> {
            FriendlyByteBuf buf = payload.buffer();
            try { handler.receive(context.server(), context.player(), null, buf, context.responseSender()); }
            finally { buf.release(); }
        });
    }
    public static void send(ServerPlayer player, Identifier id, FriendlyByteBuf buf) {
        ServerPlayNetworking.send(player, SpellPayload.copy(id, buf));
    }
    private ServerPackets() {}
}
