package com.example.circlemage.mana;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

/**
 * The Law of Space (Storage) extra inventory — 27 slots (one normal inventory's worth) backed by the
 * player's {@link ManaData} so its contents persist. Saves back to the data on every change.
 */
public class StorageInventory extends SimpleContainer {

    public static final int SIZE = 27;

    private final ManaData data;
    private final net.minecraft.resources.RegistryOps<net.minecraft.nbt.Tag> ops;
    private boolean loading = true;

    public StorageInventory(ManaData data, net.minecraft.core.HolderLookup.Provider lookup) {
        super(SIZE);
        this.data = data;
        this.ops = lookup.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
        ListTag list = data.getStorageItems();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompoundOrEmpty(i);
            int slot = tag.getIntOr("Slot", 0);
            if (slot >= 0 && slot < SIZE) {
                CompoundTag item = tag.copy(); item.remove("Slot");
                if (item.contains("Count")) item = (CompoundTag)net.minecraft.util.datafix.DataFixers.getDataFixer().update(net.minecraft.util.datafix.fixes.References.ITEM_STACK, new com.mojang.serialization.Dynamic<net.minecraft.nbt.Tag>(net.minecraft.nbt.NbtOps.INSTANCE, item), 3465, net.minecraft.SharedConstants.getCurrentVersion().dataVersion().version()).getValue();
                setItem(slot, ItemStack.CODEC.parse(ops, item).getOrThrow());
            }
        }
        loading = false;
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (loading || data == null) return;
        ListTag list = new ListTag();
        for (int i = 0; i < SIZE; i++) {
            ItemStack stack = getItem(i);
            if (!stack.isEmpty()) {
                CompoundTag tag = (CompoundTag)ItemStack.CODEC.encodeStart(ops, stack).getOrThrow();
                tag.putInt("Slot", i);
                list.add(tag);
            }
        }
        data.setStorageItems(list);
    }
}
