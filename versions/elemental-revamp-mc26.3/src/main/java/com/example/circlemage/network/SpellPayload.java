package com.example.circlemage.network;

import io.netty.buffer.Unpooled;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Registered, bounded payloads. The existing field order remains identical on both ends. */
public record SpellPayload(Type<SpellPayload> type, byte[] bytes) implements CustomPacketPayload {
    private static final Map<Identifier, Type<SpellPayload>> TYPES = new HashMap<>();
    public static Type<SpellPayload> type(Identifier id) {
        Type<SpellPayload> type = TYPES.get(id);
        if (type == null) throw new IllegalArgumentException("Unregistered CircleMage payload " + id);
        return type;
    }
    public static void register(Identifier id, boolean toServer, int length) {
        Type<SpellPayload> type = new Type<>(id);
        if (TYPES.putIfAbsent(id, type) != null) throw new IllegalStateException("Duplicate payload " + id);
        StreamCodec<RegistryFriendlyByteBuf, SpellPayload> codec = new StreamCodec<>() {
            public SpellPayload decode(RegistryFriendlyByteBuf buf) {
                if (buf.readableBytes() != length) throw new IllegalArgumentException("Invalid payload length for " + id);
                byte[] bytes = new byte[length]; buf.readBytes(bytes);
                return new SpellPayload(type, bytes);
            }
            public void encode(RegistryFriendlyByteBuf buf, SpellPayload value) {
                if (value.bytes.length != length) throw new IllegalArgumentException("Invalid outgoing payload length for " + id);
                buf.writeBytes(value.bytes);
            }
        };
        (toServer ? PayloadTypeRegistry.serverboundPlay() : PayloadTypeRegistry.clientboundPlay()).register(type, codec);
    }
    public static SpellPayload copy(Identifier id, FriendlyByteBuf buf) {
        byte[] bytes = new byte[buf.readableBytes()];
        buf.getBytes(buf.readerIndex(), bytes);
        buf.release();
        return new SpellPayload(type(id), bytes);
    }
    public FriendlyByteBuf buffer() { return new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes)); }
}
