package com.example.circlemage.client;
import com.example.circlemage.network.SpellPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
public final class ClientPackets {
    @FunctionalInterface public interface Handler {
        void receive(Minecraft client, Object connection, FriendlyByteBuf buf, Object sender);
    }
    public static void registerGlobalReceiver(Identifier id, Handler handler) {
        ClientPlayNetworking.registerGlobalReceiver(SpellPayload.type(id), (payload, context) -> {
            FriendlyByteBuf buf = payload.buffer();
            try { handler.receive(context.client(), null, buf, context.responseSender()); }
            finally { buf.release(); }
        });
    }
    public static void send(Identifier id, FriendlyByteBuf buf) { ClientPlayNetworking.send(SpellPayload.copy(id, buf)); }
    private ClientPackets() {}
}
