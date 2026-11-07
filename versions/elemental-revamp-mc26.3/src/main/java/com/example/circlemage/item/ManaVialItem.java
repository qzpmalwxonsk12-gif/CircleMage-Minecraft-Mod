package com.example.circlemage.item;

import com.example.circlemage.mana.Circles;
import com.example.circlemage.mana.ManaData;
import com.example.circlemage.mana.ManaPlayer;
import com.example.circlemage.network.ModNetworking;
import com.example.circlemage.particle.ModParticles;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

/**
 * Mana Vial — bottled, distilled mana. Drinking one instantly restores {@link #RESTORE} mana
 * (never past the next circle's threshold, so none is wasted hoarding). Brewed from Mana Dust,
 * or bartered from a friendly Wandering Mage for an emerald.
 */
public class ManaVialItem extends Item {

    public static final double RESTORE = 500.0;

    public ManaVialItem(Properties settings) {
        super(settings);
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.DRINK;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return 24;
    }

    @Override
    public InteractionResult use(Level world, Player user, InteractionHand hand) {
        return ItemUtils.startUsingInstantly(world, user, hand);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level world, LivingEntity user) {
        if (!(user instanceof ServerPlayer player)) {
            return stack;
        }

        ManaData data = ManaPlayer.get(player);
        long cap = Circles.nextThreshold(data.getCircle());
        double restored = Math.min(RESTORE, Math.max(0.0, cap - data.getCurrentMana()));
        data.addMana(restored);
        ModNetworking.syncToClient(player);

        if (world instanceof ServerLevel serverWorld) {
            serverWorld.sendParticles(ModParticles.ARCANE, player.getX(), player.getY(0.7), player.getZ(),
                    18, 0.3, 0.4, 0.3, 0.04);
        }
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, 1.7f);
        com.example.circlemage.util.Messages.send(player, Component.literal(String.format("+%.0f mana", restored)).withStyle(ChatFormatting.AQUA), true);

        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
            ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
            if (stack.isEmpty()) {
                return bottle;
            }
            if (!player.getInventory().add(bottle)) {
                player.drop(bottle, false, net.minecraft.util.Prediction.SERVER_ONLY);
            }
        }
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext world, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> tooltip, TooltipFlag context) {
        tooltip.accept(Component.literal("Drink to restore " + (int) RESTORE + " mana").withStyle(ChatFormatting.AQUA));
        tooltip.accept(Component.literal("(caps at your next circle's threshold)").withStyle(ChatFormatting.DARK_GRAY));
    }
}
