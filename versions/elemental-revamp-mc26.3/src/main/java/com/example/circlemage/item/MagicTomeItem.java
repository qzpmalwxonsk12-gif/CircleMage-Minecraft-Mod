package com.example.circlemage.item;

import com.example.circlemage.CircleMageMod;
import com.example.circlemage.mana.ManaData;
import com.example.circlemage.mana.ManaPlayer;
import com.example.circlemage.network.ModNetworking;
import com.example.circlemage.particle.ModParticles;
import com.example.circlemage.spell.Spell;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Magic Tome — a rune-sealed book found in chests across the world (roughly one per village or
 * two). Using it teaches one random spell from its element that the reader doesn't know yet; if
 * nothing in it is new (or the element's magic is beyond mortal knowledge — water and earth, for
 * now), the tome simply fizzles away. Either way the book is spent.
 */
public class MagicTomeItem extends Item {

    public enum Element {
        FIRE("Fire", new int[]{Spell.FIRE_BOLT.id, Spell.FIREBALL.id, Spell.FLAME_REBIRTH.id}),   // Area Fire + Fireball
        WATER("Water", new int[]{Spell.ICE_VOLLEY.id, Spell.WATER_BUBBLE.id, Spell.SOOTHING_WATER.id}),
        EARTH("Earth", new int[]{Spell.STONE_SPIKE.id, Spell.STONE_FIST.id, Spell.STONE_DOME.id}),
        WIND("Wind", new int[]{Spell.WIND_BLADE.id, Spell.WALL_OF_WIND.id, Spell.DASH.id}),
        MISC("Miscellany", new int[]{Spell.MAGIC_MISSILE.id, Spell.VINE_GRAB.id, Spell.TRACKING_MARK.id});

        public final String displayName;
        public final int[] spellIds;

        Element(String displayName, int[] spellIds) {
            this.displayName = displayName;
            this.spellIds = spellIds;
        }
    }

    private final Element element;

    public MagicTomeItem(Element element, Properties settings) {
        super(settings);
        this.element = element;
    }

    @Override
    public InteractionResult use(Level world, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        if (world.isClientSide() || !(user instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }

        ManaData data = ManaPlayer.get(player);
        List<Integer> teachable = new ArrayList<>();
        for (int id : element.spellIds) {
            if (!data.isSpellUnlocked(id) && com.example.circlemage.spell.SpellCatalog.tier(id) <= data.getCircle()) {
                teachable.add(id);
            }
        }

        // Consume only after a valid new spell is selected.

        if (teachable.isEmpty()) {
            com.example.circlemage.util.Messages.send(player, Component.literal("No new spell is available at your circle. Keep this tome for later.").withStyle(ChatFormatting.GRAY),false);
            return InteractionResult.FAIL;
        }
        if (!player.getAbilities().instabuild) stack.shrink(1);

        int learned = teachable.get(world.getRandom().nextInt(teachable.size()));
        data.unlockSpell(learned);
        ModNetworking.syncToClient(player);

        Spell spell = Spell.byId(learned);
        com.example.circlemage.util.Messages.send(player, Component.literal("The tome sears its knowledge into your mind: "
                        + CircleMageMod.spellName(spell) + "!")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        if (world instanceof ServerLevel serverWorld) {
            serverWorld.sendParticles(ModParticles.RADIANCE, player.getX(), player.getY(0.7), player.getZ(),
                    30, 0.4, 0.6, 0.4, 0.06);
            serverWorld.sendParticles(ModParticles.ARCANE, player.getX(), player.getY(0.7), player.getZ(),
                    20, 0.4, 0.5, 0.4, 0.05);
        }
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0f, 1.2f);
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext world, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> tooltip, TooltipFlag context) {
        tooltip.accept(Component.literal("A sealed tome of " + element.displayName.toLowerCase() + " magic.")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.accept(Component.literal("Use to learn an eligible spell. Empty pools keep the tome.")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
