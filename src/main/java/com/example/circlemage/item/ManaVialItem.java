package com.example.circlemage.item;

import com.example.circlemage.mana.Circles;
import com.example.circlemage.mana.ManaData;
import com.example.circlemage.mana.ManaPlayer;
import com.example.circlemage.network.ModNetworking;
import com.example.circlemage.particle.ModParticles;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsage;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;

import java.util.List;

/**
 * Mana Vial — bottled, distilled mana. Drinking one instantly restores {@link #RESTORE} mana
 * (never past the next circle's threshold, so none is wasted hoarding). Brewed from Mana Dust,
 * or bartered from a friendly Wandering Mage for an emerald.
 */
public class ManaVialItem extends Item {

    public static final double RESTORE = 500.0;

    public ManaVialItem(Settings settings) {
        super(settings);
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.DRINK;
    }

    @Override
    public int getMaxUseTime(ItemStack stack) {
        return 24;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        return ItemUsage.consumeHeldItem(world, user, hand);
    }

    @Override
    public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
        if (!(user instanceof ServerPlayerEntity player)) {
            return stack;
        }

        ManaData data = ManaPlayer.get(player);
        long cap = Circles.nextThreshold(data.getCircle());
        double restored = Math.min(RESTORE, Math.max(0.0, cap - data.getCurrentMana()));
        data.addMana(restored);
        ModNetworking.syncToClient(player);

        if (world instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(ModParticles.ARCANE, player.getX(), player.getBodyY(0.7), player.getZ(),
                    18, 0.3, 0.4, 0.3, 0.04);
        }
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.8f, 1.7f);
        player.sendMessage(Text.literal(String.format("+%.0f mana", restored)).formatted(Formatting.AQUA), true);

        if (!player.getAbilities().creativeMode) {
            stack.decrement(1);
            ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
            if (stack.isEmpty()) {
                return bottle;
            }
            if (!player.getInventory().insertStack(bottle)) {
                player.dropItem(bottle, false);
            }
        }
        return stack;
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.literal("Drink to restore " + (int) RESTORE + " mana").formatted(Formatting.AQUA));
        tooltip.add(Text.literal("(caps at your next circle's threshold)").formatted(Formatting.DARK_GRAY));
    }
}
