package com.example.circlemage.util;

import com.mojang.serialization.*;
import java.util.stream.Stream;
import net.minecraft.nbt.*;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Keeps existing custom save keys while using Minecraft's registry-aware storage API. */
public final class NbtBridge {
    public static final MapCodec<CompoundTag> ROOT = new MapCodec<>() {
        @Override public <T> DataResult<CompoundTag> decode(DynamicOps<T> ops, MapLike<T> input) {
            return CompoundTag.CODEC.parse(ops, ops.createMap(input.entries()));
        }
        @Override public <T> RecordBuilder<T> encode(CompoundTag value, DynamicOps<T> ops, RecordBuilder<T> prefix) {
            value.forEach((key, tag) -> prefix.add(key, NbtOps.INSTANCE.convertTo(ops, tag)));
            return prefix;
        }
        @Override public <T> Stream<T> keys(DynamicOps<T> ops) { return Stream.empty(); }
    };
    public static CompoundTag read(ValueInput input) { return input.read(ROOT).orElseGet(CompoundTag::new); }
    public static void write(ValueOutput output, CompoundTag tag) { output.store(ROOT, tag); }
    private NbtBridge() {}
}
