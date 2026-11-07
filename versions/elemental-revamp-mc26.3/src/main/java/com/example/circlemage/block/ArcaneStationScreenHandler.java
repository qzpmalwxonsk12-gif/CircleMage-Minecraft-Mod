package com.example.circlemage.block;

import com.example.circlemage.item.ModItems;
import com.example.circlemage.item.WandItem;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The Arcane Station's wand assembly (v2.2):
 * <b>A</b> Arcane Glue · <b>B</b> material (stick/bamboo/end rod/blaze rod/bone/iron/copper) ·
 * <b>C</b> core (glow crystal/heart of the sea/nether star/echo shard/dragon crystal) ·
 * <b>D</b> crystal tip (diamond/amethyst/emerald) · result.
 *
 * <p>Glue + material are required; core and crystal are optional. Taking the assembled wand
 * consumes one item from every occupied input. Leftovers are handed back on close.</p>
 */
public class ArcaneStationScreenHandler extends AbstractContainerMenu {

    public static final int SLOT_GLUE = 0;
    public static final int SLOT_MATERIAL = 1;
    public static final int SLOT_CORE = 2;
    public static final int SLOT_CRYSTAL = 3;
    public static final int SLOT_RESULT = 4;
    private static final int INPUT_SIZE = 4;
    private static final int PLAYER_START = 5;

    private final SimpleContainer input = new SimpleContainer(INPUT_SIZE) { @Override public void setChanged() { super.setChanged(); updateResult(); } };
    private final ResultContainer result = new ResultContainer();
    private final ContainerLevelAccess context;

    /** Client-side constructor (the factory registered with the ScreenHandlerType). */
    public ArcaneStationScreenHandler(int syncId, Inventory playerInventory) {
        this(syncId, playerInventory, ContainerLevelAccess.NULL);
    }

    public ArcaneStationScreenHandler(int syncId, Inventory playerInventory, ContainerLevelAccess context) {
        super(ModBlocks.ARCANE_STATION_SCREEN_HANDLER, syncId);
        this.context = context;

        // A / B / C / D across the top (see textures/gui/arcane_station.png).
        this.addSlot(new Slot(input, SLOT_GLUE, 26, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModItems.ARCANE_GLUE);
            }
        });
        this.addSlot(new Slot(input, SLOT_MATERIAL, 54, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return WandItem.materialFromIngredient(stack) != null;
            }
        });
        this.addSlot(new Slot(input, SLOT_CORE, 82, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return WandItem.coreFromIngredient(stack) != null;
            }
        });
        this.addSlot(new Slot(input, SLOT_CRYSTAL, 110, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return WandItem.crystalFromIngredient(stack) != null;
            }
        });

        // Result: take-only; taking it consumes one of each occupied input.
        this.addSlot(new Slot(result, 0, 148, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public void onTake(Player player, ItemStack stack) {
                consumeIngredients();
                context.execute((world, pos) -> world.playSound(null, pos,
                        SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 0.8f, 1.4f));
                super.onTake(player, stack);
            }
        });

        // Player inventory + hotbar.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
    }

    /** Recomputes the result wand whenever the inputs change. */
    private void updateResult() {
        WandItem.WandMaterial material = WandItem.materialFromIngredient(input.getItem(SLOT_MATERIAL));
        boolean hasGlue = input.getItem(SLOT_GLUE).is(ModItems.ARCANE_GLUE);
        ItemStack coreStack = input.getItem(SLOT_CORE);
        ItemStack crystalStack = input.getItem(SLOT_CRYSTAL);

        if (!hasGlue || material == null) {
            result.setItem(0, ItemStack.EMPTY);
        } else {
            result.setItem(0, WandItem.build(material,
                    coreStack.isEmpty() ? null : WandItem.coreFromIngredient(coreStack),
                    crystalStack.isEmpty() ? null : WandItem.crystalFromIngredient(crystalStack)));
        }
        this.broadcastChanges();
    }

    /** One of each occupied input goes into the wand. */
    private void consumeIngredients() {
        for (int i = 0; i < INPUT_SIZE; i++) {
            ItemStack stack = input.getItem(i);
            if (!stack.isEmpty()) {
                stack.shrink(1);
                input.setItem(i, stack);
            }
        }
        updateResult();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (index == SLOT_RESULT) {
            // Shift-click the wand out (consumes ingredients via onTakeItem).
            if (!this.moveItemStackTo(stack, PLAYER_START, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
            slot.onTake(player, stack);
        } else if (index < INPUT_SIZE) {
            // Station -> player inventory.
            if (!this.moveItemStackTo(stack, PLAYER_START, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // Player -> station: route by what the item actually is.
            int target;
            if (stack.is(ModItems.ARCANE_GLUE)) {
                target = SLOT_GLUE;
            } else if (WandItem.materialFromIngredient(stack) != null) {
                target = SLOT_MATERIAL;
            } else if (WandItem.coreFromIngredient(stack) != null) {
                target = SLOT_CORE;
            } else if (WandItem.crystalFromIngredient(stack) != null) {
                target = SLOT_CRYSTAL;
            } else {
                return ItemStack.EMPTY;
            }
            if (!this.moveItemStackTo(stack, target, target + 1, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(this.context, player, ModBlocks.ARCANE_STATION);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.context.execute((world, pos) -> this.clearContainer(player, this.input));
        if (this.context == ContainerLevelAccess.NULL) {
            this.clearContainer(player, this.input);
        }
    }
}
