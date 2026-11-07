package com.example.circlemage.block;

import com.example.circlemage.item.ModItems;
import com.example.circlemage.item.WandItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.CraftingResultInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.slot.Slot;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

/**
 * The Arcane Station's wand assembly (v2.2):
 * <b>A</b> Arcane Glue · <b>B</b> material (stick/bamboo/end rod/blaze rod/bone/iron/copper) ·
 * <b>C</b> core (glow crystal/heart of the sea/nether star/echo shard/dragon crystal) ·
 * <b>D</b> crystal tip (diamond/amethyst/emerald) · result.
 *
 * <p>Glue + material are required; core and crystal are optional. Taking the assembled wand
 * consumes one item from every occupied input. Leftovers are handed back on close.</p>
 */
public class ArcaneStationScreenHandler extends ScreenHandler {

    public static final int SLOT_GLUE = 0;
    public static final int SLOT_MATERIAL = 1;
    public static final int SLOT_CORE = 2;
    public static final int SLOT_CRYSTAL = 3;
    public static final int SLOT_RESULT = 4;
    private static final int INPUT_SIZE = 4;
    private static final int PLAYER_START = 5;

    private final SimpleInventory input = new SimpleInventory(INPUT_SIZE);
    private final CraftingResultInventory result = new CraftingResultInventory();
    private final ScreenHandlerContext context;

    /** Client-side constructor (the factory registered with the ScreenHandlerType). */
    public ArcaneStationScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, ScreenHandlerContext.EMPTY);
    }

    public ArcaneStationScreenHandler(int syncId, PlayerInventory playerInventory, ScreenHandlerContext context) {
        super(ModBlocks.ARCANE_STATION_SCREEN_HANDLER, syncId);
        this.context = context;
        this.input.addListener(inv -> updateResult());

        // A / B / C / D across the top (see textures/gui/arcane_station.png).
        this.addSlot(new Slot(input, SLOT_GLUE, 26, 35) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return stack.isOf(ModItems.ARCANE_GLUE);
            }
        });
        this.addSlot(new Slot(input, SLOT_MATERIAL, 54, 35) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return WandItem.materialFromIngredient(stack) != null;
            }
        });
        this.addSlot(new Slot(input, SLOT_CORE, 82, 35) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return WandItem.coreFromIngredient(stack) != null;
            }
        });
        this.addSlot(new Slot(input, SLOT_CRYSTAL, 110, 35) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return WandItem.crystalFromIngredient(stack) != null;
            }
        });

        // Result: take-only; taking it consumes one of each occupied input.
        this.addSlot(new Slot(result, 0, 148, 35) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return false;
            }

            @Override
            public void onTakeItem(PlayerEntity player, ItemStack stack) {
                consumeIngredients();
                context.run((world, pos) -> world.playSound(null, pos,
                        SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.BLOCKS, 0.8f, 1.4f));
                super.onTakeItem(player, stack);
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
        WandItem.WandMaterial material = WandItem.materialFromIngredient(input.getStack(SLOT_MATERIAL));
        boolean hasGlue = input.getStack(SLOT_GLUE).isOf(ModItems.ARCANE_GLUE);
        ItemStack coreStack = input.getStack(SLOT_CORE);
        ItemStack crystalStack = input.getStack(SLOT_CRYSTAL);

        if (!hasGlue || material == null) {
            result.setStack(0, ItemStack.EMPTY);
        } else {
            result.setStack(0, WandItem.build(material,
                    coreStack.isEmpty() ? null : WandItem.coreFromIngredient(coreStack),
                    crystalStack.isEmpty() ? null : WandItem.crystalFromIngredient(crystalStack)));
        }
        this.sendContentUpdates();
    }

    /** One of each occupied input goes into the wand. */
    private void consumeIngredients() {
        for (int i = 0; i < INPUT_SIZE; i++) {
            ItemStack stack = input.getStack(i);
            if (!stack.isEmpty()) {
                stack.decrement(1);
                input.setStack(i, stack);
            }
        }
        updateResult();
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasStack()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getStack();
        ItemStack original = stack.copy();

        if (index == SLOT_RESULT) {
            // Shift-click the wand out (consumes ingredients via onTakeItem).
            if (!this.insertItem(stack, PLAYER_START, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
            slot.onTakeItem(player, stack);
        } else if (index < INPUT_SIZE) {
            // Station -> player inventory.
            if (!this.insertItem(stack, PLAYER_START, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // Player -> station: route by what the item actually is.
            int target;
            if (stack.isOf(ModItems.ARCANE_GLUE)) {
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
            if (!this.insertItem(stack, target, target + 1, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.setStack(ItemStack.EMPTY);
        } else {
            slot.markDirty();
        }
        return original;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return canUse(this.context, player, ModBlocks.ARCANE_STATION);
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        this.context.run((world, pos) -> this.dropInventory(player, this.input));
        if (this.context == ScreenHandlerContext.EMPTY) {
            this.dropInventory(player, this.input);
        }
    }
}
