package com.example.circlemage.mana;

import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;

/**
 * The Law of Space (Storage) extra inventory — 27 slots (one normal inventory's worth) backed by the
 * player's {@link ManaData} so its contents persist. Saves back to the data on every change.
 */
public class StorageInventory extends SimpleInventory {

    public static final int SIZE = 27;

    private final ManaData data;

    public StorageInventory(ManaData data) {
        super(SIZE);
        this.data = data;
        NbtList list = data.getStorageItems();
        for (int i = 0; i < list.size(); i++) {
            NbtCompound tag = list.getCompound(i);
            int slot = tag.getInt("Slot");
            if (slot >= 0 && slot < SIZE) {
                setStack(slot, ItemStack.fromNbt(tag));
            }
        }
    }

    @Override
    public void markDirty() {
        super.markDirty();
        NbtList list = new NbtList();
        for (int i = 0; i < SIZE; i++) {
            ItemStack stack = getStack(i);
            if (!stack.isEmpty()) {
                NbtCompound tag = new NbtCompound();
                tag.putInt("Slot", i);
                stack.writeNbt(tag);
                list.add(tag);
            }
        }
        data.setStorageItems(list);
    }
}
