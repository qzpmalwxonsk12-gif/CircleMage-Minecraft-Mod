package com.example.circlemage.item;

import com.example.circlemage.CircleMageMod;
import com.example.circlemage.mana.ManaData;
import com.example.circlemage.mana.ManaPlayer;
import com.example.circlemage.network.ModNetworking;
import com.example.circlemage.particle.ModParticles;
import com.example.circlemage.spell.Spell;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Magic Tome — a rune-sealed book found in chests across the world (roughly one per village or
 * two). Using it teaches one random spell from its element that the reader doesn't know yet; if
 * nothing in it is new (or the element's magic is beyond mortal knowledge — water and earth, for
 * now), the tome simply fizzles away. Either way the book is spent.
 */
public class MagicTomeItem extends Item {

    public enum Element {
        FIRE("Fire", new int[]{Spell.FIREBALL.id, Spell.FIRE_BOLT.id}),   // Area Fire + Fireball
        WATER("Water", new int[]{}),
        EARTH("Earth", new int[]{}),
        WIND("Wind", new int[]{Spell.WIND_BLADE.id, Spell.WALL_OF_WIND.id}),
        MISC("Miscellany", new int[]{Spell.BARRIER.id, Spell.THUNDER.id, Spell.AREA_SLOW.id});

        public final String displayName;
        public final int[] spellIds;

        Element(String displayName, int[] spellIds) {
            this.displayName = displayName;
            this.spellIds = spellIds;
        }
    }

    private final Element element;

    public MagicTomeItem(Element element, Settings settings) {
        super(settings);
        this.element = element;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient || !(user instanceof ServerPlayerEntity player)) {
            return TypedActionResult.success(stack, world.isClient);
        }

        ManaData data = ManaPlayer.get(player);
        List<Integer> teachable = new ArrayList<>();
        for (int id : element.spellIds) {
            if (!data.isSpellUnlocked(id)) {
                teachable.add(id);
            }
        }

        if (!player.getAbilities().creativeMode) {
            stack.decrement(1);
        }

        if (teachable.isEmpty()) {
            // Nothing to learn: the tome fizzles away.
            String line = element.spellIds.length == 0
                    ? "The pages are blank to your eyes — " + element.displayName.toLowerCase()
                    + " magic is not yet known to this world."
                    : "The tome crumbles to dust — it held nothing you don't already know.";
            player.sendMessage(Text.literal(line).formatted(Formatting.GRAY), false);
            if (world instanceof ServerWorld serverWorld) {
                serverWorld.spawnParticles(ModParticles.SMOKE, player.getX(), player.getBodyY(0.7), player.getZ(),
                        12, 0.3, 0.3, 0.3, 0.02);
                serverWorld.spawnParticles(ModParticles.DUST, player.getX(), player.getBodyY(0.7), player.getZ(),
                        10, 0.3, 0.3, 0.3, 0.03);
            }
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.7f, 1.4f);
            return TypedActionResult.success(stack);
        }

        int learned = teachable.get(world.random.nextInt(teachable.size()));
        data.unlockSpell(learned);
        ModNetworking.syncToClient(player);

        Spell spell = Spell.byId(learned);
        player.sendMessage(Text.literal("The tome sears its knowledge into your mind: "
                        + CircleMageMod.spellName(spell) + "!")
                .formatted(Formatting.GOLD, Formatting.BOLD), false);
        if (world instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(ModParticles.RADIANCE, player.getX(), player.getBodyY(0.7), player.getZ(),
                    30, 0.4, 0.6, 0.4, 0.06);
            serverWorld.spawnParticles(ModParticles.ARCANE, player.getX(), player.getBodyY(0.7), player.getZ(),
                    20, 0.4, 0.5, 0.4, 0.05);
        }
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.PLAYERS, 1.0f, 1.2f);
        return TypedActionResult.success(stack);
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        return true;
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.literal("A sealed tome of " + element.displayName.toLowerCase() + " magic.")
                .formatted(Formatting.LIGHT_PURPLE));
        tooltip.add(Text.literal("Use to absorb one of its spells — or watch it fizzle.")
                .formatted(Formatting.DARK_GRAY));
    }
}
