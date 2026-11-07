package com.example.circlemage.item;

import com.example.circlemage.spell.Spell;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Arcane Wand — assembled at the Arcane Station from glue + a material (+ optional core and
 * crystal). The components are stored in NBT and drive both the look (material/crystal colours via
 * the item colour provider, plus held particles for end-rod and blaze wands) and the casting
 * bonuses, which apply per magic <i>school</i> while the wand is held in either hand.
 */
public class WandItem extends Item {

    /** Slot-B materials. Colour is the layer-0 tint; endRod/blaze also buff their school. */
    public enum WandMaterial {
        STICK(0x8B6C42, "Stick"),
        BAMBOO(0x9BA55B, "Bamboo"),
        END_ROD(0xF4F1E5, "End Rod"),
        BLAZE_ROD(0xF8C33C, "Blaze Rod"),
        BONE(0xEDE4CE, "Bone"),
        IRON(0xD8D8D8, "Iron"),
        COPPER(0xC46F50, "Copper");

        public final int color;
        public final String displayName;

        WandMaterial(int color, String displayName) {
            this.color = color;
            this.displayName = displayName;
        }
    }

    /** Slot-C cores. */
    public enum WandCore {
        GLOW_CRYSTAL("Glow Crystal", "Sheds torchlight while held"),
        HEART_OF_SEA("Heart of the Sea", "-20% cost, +20% damage for water magic"),
        NETHER_STAR("Nether Star", "-20% cost & cooldown, +20% damage for all magic"),
        ECHO_SHARD("Echo Shard", "-30% cost & cooldown, +15% damage for misc magic"),
        DRAGON_CRYSTAL("Dragon Crystal", "Its power is still dormant...");

        public final String displayName;
        public final String description;

        WandCore(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
    }

    /** Slot-D crystal tips (cosmetic; layer-1 tint). */
    public enum WandCrystal {
        DIAMOND(0x4AEDD9, "Diamond"),
        AMETHYST(0x9A5CC6, "Amethyst"),
        EMERALD(0x2BDD62, "Emerald");

        public final int color;
        public final String displayName;

        WandCrystal(int color, String displayName) {
            this.color = color;
            this.displayName = displayName;
        }
    }

    /** The magic schools the wand bonuses key off (matches the tome elements). */
    public enum School {
        FIRE, WIND, WATER, MISC, OTHER;

        public static School of(int spellId) {
            if (spellId == Spell.FIREBALL.id || spellId == Spell.FIRE_BOLT.id) {
                return FIRE;
            }
            if (spellId == Spell.WALL_OF_WIND.id || spellId == Spell.WIND_BLADE.id) {
                return WIND;
            }
            // Misc: slow (and its Dark Orb form), anti magic, thunder, barrier.
            if (spellId == Spell.AREA_SLOW.id || spellId == Spell.DARK_ORB.id
                    || spellId == Spell.CLEAR_MAGIC.id || spellId == Spell.THUNDER.id
                    || spellId == Spell.BARRIER.id) {
                return MISC;
            }
            return OTHER;
        }
    }

    public WandItem(Settings settings) {
        super(settings);
    }

    // --- Assembly / component NBT ----------------------------------------------

    /** Builds a wand from its components (crystal drives the tipped model via CustomModelData). */
    public static ItemStack build(WandMaterial material, @Nullable WandCore core, @Nullable WandCrystal crystal) {
        ItemStack stack = new ItemStack(ModItems.WAND);
        NbtCompound nbt = stack.getOrCreateNbt();
        nbt.putString("material", material.name());
        if (core != null) {
            nbt.putString("core", core.name());
        }
        if (crystal != null) {
            nbt.putString("crystal", crystal.name());
            nbt.putInt("CustomModelData", 1);
        }
        return stack;
    }

    @Nullable
    public static WandMaterial materialOf(ItemStack stack) {
        return enumOf(stack, "material", WandMaterial.values());
    }

    @Nullable
    public static WandCore coreOf(ItemStack stack) {
        return enumOf(stack, "core", WandCore.values());
    }

    @Nullable
    public static WandCrystal crystalOf(ItemStack stack) {
        return enumOf(stack, "crystal", WandCrystal.values());
    }

    @Nullable
    private static <T extends Enum<T>> T enumOf(ItemStack stack, String key, T[] values) {
        if (!(stack.getItem() instanceof WandItem) || stack.getNbt() == null) {
            return null;
        }
        String name = stack.getNbt().getString(key);
        for (T value : values) {
            if (value.name().equals(name)) {
                return value;
            }
        }
        return null;
    }

    // --- Ingredient mapping (Arcane Station slot filters + assembly) ------------

    @Nullable
    public static WandMaterial materialFromIngredient(ItemStack stack) {
        if (stack.isOf(Items.STICK)) {
            return WandMaterial.STICK;
        }
        if (stack.isOf(Items.BAMBOO)) {
            return WandMaterial.BAMBOO;
        }
        if (stack.isOf(Items.END_ROD)) {
            return WandMaterial.END_ROD;
        }
        if (stack.isOf(Items.BLAZE_ROD)) {
            return WandMaterial.BLAZE_ROD;
        }
        if (stack.isOf(Items.BONE)) {
            return WandMaterial.BONE;
        }
        if (stack.isOf(Items.IRON_INGOT)) {
            return WandMaterial.IRON;
        }
        if (stack.isOf(Items.COPPER_INGOT)) {
            return WandMaterial.COPPER;
        }
        return null;
    }

    @Nullable
    public static WandCore coreFromIngredient(ItemStack stack) {
        if (stack.isOf(ModItems.GLOW_CRYSTAL)) {
            return WandCore.GLOW_CRYSTAL;
        }
        if (stack.isOf(Items.HEART_OF_THE_SEA)) {
            return WandCore.HEART_OF_SEA;
        }
        if (stack.isOf(Items.NETHER_STAR)) {
            return WandCore.NETHER_STAR;
        }
        if (stack.isOf(Items.ECHO_SHARD)) {
            return WandCore.ECHO_SHARD;
        }
        if (stack.isOf(ModItems.DRAGON_CRYSTAL)) {
            return WandCore.DRAGON_CRYSTAL;
        }
        return null;
    }

    @Nullable
    public static WandCrystal crystalFromIngredient(ItemStack stack) {
        if (stack.isOf(Items.DIAMOND)) {
            return WandCrystal.DIAMOND;
        }
        if (stack.isOf(Items.AMETHYST_SHARD)) {
            return WandCrystal.AMETHYST;
        }
        if (stack.isOf(Items.EMERALD)) {
            return WandCrystal.EMERALD;
        }
        return null;
    }

    // --- Casting modifiers (read the wand held in either hand) ------------------

    @Nullable
    public static ItemStack heldWand(PlayerEntity player) {
        if (player.getMainHandStack().getItem() instanceof WandItem) {
            return player.getMainHandStack();
        }
        if (player.getOffHandStack().getItem() instanceof WandItem) {
            return player.getOffHandStack();
        }
        return null;
    }

    public static double costMultiplier(PlayerEntity player, int spellId) {
        ItemStack wand = heldWand(player);
        if (wand == null) {
            return 1.0;
        }
        School school = School.of(spellId);
        double mult = 1.0;
        WandMaterial material = materialOf(wand);
        if (material == WandMaterial.END_ROD && school == School.MISC) {
            mult *= 0.8;
        }
        if (material == WandMaterial.BLAZE_ROD && school == School.FIRE) {
            mult *= 0.8;
        }
        WandCore core = coreOf(wand);
        if (core == WandCore.HEART_OF_SEA && school == School.WATER) {
            mult *= 0.8;
        }
        if (core == WandCore.NETHER_STAR) {
            mult *= 0.8;
        }
        if (core == WandCore.ECHO_SHARD && school == School.MISC) {
            mult *= 0.7;
        }
        return mult;
    }

    public static double cooldownMultiplier(PlayerEntity player, int spellId) {
        ItemStack wand = heldWand(player);
        if (wand == null) {
            return 1.0;
        }
        School school = School.of(spellId);
        double mult = 1.0;
        WandCore core = coreOf(wand);
        if (core == WandCore.NETHER_STAR) {
            mult *= 0.8;
        }
        if (core == WandCore.ECHO_SHARD && school == School.MISC) {
            mult *= 0.7;
        }
        return mult;
    }

    /** End rod / blaze rod: 20% faster casting (shorter cast time) for their school. */
    public static double castTimeMultiplier(PlayerEntity player, int spellId) {
        ItemStack wand = heldWand(player);
        if (wand == null) {
            return 1.0;
        }
        School school = School.of(spellId);
        WandMaterial material = materialOf(wand);
        if (material == WandMaterial.END_ROD && school == School.MISC) {
            return 0.8;
        }
        if (material == WandMaterial.BLAZE_ROD && school == School.FIRE) {
            return 0.8;
        }
        return 1.0;
    }

    public static float damageMultiplier(PlayerEntity player, int spellId) {
        ItemStack wand = heldWand(player);
        if (wand == null) {
            return 1.0f;
        }
        School school = School.of(spellId);
        float mult = 1.0f;
        WandCore core = coreOf(wand);
        if (core == WandCore.HEART_OF_SEA && school == School.WATER) {
            mult *= 1.2f;
        }
        if (core == WandCore.NETHER_STAR) {
            mult *= 1.2f;
        }
        if (core == WandCore.ECHO_SHARD && school == School.MISC) {
            mult *= 1.15f;
        }
        return mult;
    }

    // --- Presentation ------------------------------------------------------------

    @Override
    public boolean hasGlint(ItemStack stack) {
        return coreOf(stack) != null;
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        WandMaterial material = materialOf(stack);
        if (material != null) {
            String extra = switch (material) {
                case END_ROD -> " — -20% cost & 20% faster casting for misc magic";
                case BLAZE_ROD -> " — -20% cost & 20% faster casting for fire magic";
                default -> "";
            };
            tooltip.add(Text.literal("Material: " + material.displayName + extra).formatted(Formatting.GRAY));
        }
        WandCore core = coreOf(stack);
        if (core != null) {
            tooltip.add(Text.literal("Core: " + core.displayName + " — " + core.description)
                    .formatted(Formatting.AQUA));
        }
        WandCrystal crystal = crystalOf(stack);
        if (crystal != null) {
            tooltip.add(Text.literal("Crystal: " + crystal.displayName).formatted(Formatting.LIGHT_PURPLE));
        }
        tooltip.add(Text.literal("Bonuses apply while held in either hand").formatted(Formatting.DARK_GRAY));
    }
}
