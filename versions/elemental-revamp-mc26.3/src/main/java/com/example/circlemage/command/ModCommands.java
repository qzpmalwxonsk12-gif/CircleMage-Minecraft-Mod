package com.example.circlemage.command;

import com.example.circlemage.entity.ModEntities;
import com.example.circlemage.entity.WanderingMageEntity;
import com.example.circlemage.mana.Circles;
import com.example.circlemage.mana.ManaData;
import com.example.circlemage.mana.ManaPlayer;
import com.example.circlemage.network.ModNetworking;
import com.example.circlemage.spell.Law;
import com.example.circlemage.spell.LawUnlocks;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * Quality-of-life commands for playtesting. All require permission level 2 (cheats/op).
 *
 *   /circlemage manamultiplier <value>   set the debug multiplier on mana gained per second
 *   /circlemage addmana <amount>         add mana to the bar
 *   /circlemage setmana <amount>         set the mana bar value
 *   /circlemage setcircle <0-9>          jump to a circle
 */
public final class ModCommands {

    private ModCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("circlemage")
                    .requires(source -> source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
                    .then(Commands.literal("manamultiplier")
                            .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0))
                                    .executes(ModCommands::setManaMultiplier)))
                    .then(Commands.literal("addmana")
                            .then(Commands.argument("amount", DoubleArgumentType.doubleArg())
                                    .executes(ModCommands::addMana)))
                    .then(Commands.literal("setmana")
                            .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.0))
                                    .executes(ModCommands::setMana)))
                    .then(Commands.literal("setcircle")
                            .then(Commands.argument("circle", IntegerArgumentType.integer(0, Circles.MAX_CIRCLE))
                                    .executes(ModCommands::setCircle)))
                    .then(Commands.literal("cooldowns")
                            .then(Commands.argument("enabled", BoolArgumentType.bool())
                                    .executes(ModCommands::setCooldowns)))
                    .then(Commands.literal("unlocklaws")
                            .executes(ModCommands::unlockLaws))
                    .then(Commands.literal("unlockalllaws")
                            .executes(ModCommands::unlockAllLaws))
                    .then(Commands.literal("spawnmage")
                            .then(Commands.argument("circle", IntegerArgumentType.integer(1, 3))
                                    .executes(ModCommands::spawnMage)))
                    .then(Commands.literal("spawnprodigy")
                            .executes(ModCommands::spawnProdigy)));

            dispatcher.register(Commands.literal("breakmagiclimit")
                .requires(source -> source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
                .executes(ctx -> {
                    ServerPlayer player=ctx.getSource().getPlayer();
                    if(player==null)return 0;
                    ManaPlayer.get(player).setMagicLimitBroken(true);
                    ModNetworking.syncToClient(player);
                    com.example.circlemage.util.Messages.send(player, Component.literal("T1/T2 binding limits removed. Circle, knowledge, mana and cooldown requirements remain."),false);
                    return 1;
                }));

            // Law of Creation: usable by any player who has that Law active (costs mana, not op).
            dispatcher.register(Commands.literal("lawcreate")
                    .then(Commands.argument("item", ItemArgument.item(registryAccess))
                            .executes(ctx -> lawCreate(ctx, 1))
                            .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                    .executes(ctx -> lawCreate(ctx, IntegerArgumentType.getInteger(ctx, "count"))))));
        });
    }

    private static int unlockLaws(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = playerOrNull(ctx);
        if (player == null) {
            return 0;
        }
        LawUnlocks.unlockBase(player, ManaPlayer.get(player));
        feedback(ctx, "Laws discovered (Love of the World + Creation). Others have their own conditions.");
        return 1;
    }

    private static int unlockAllLaws(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = playerOrNull(ctx);
        if (player == null) {
            return 0;
        }
        LawUnlocks.unlockAll(player, ManaPlayer.get(player));
        feedback(ctx, "All Laws unlocked.");
        return 1;
    }

    private static int lawCreate(CommandContext<CommandSourceStack> ctx, int count) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("This command must be run by a player."));
            return 0;
        }
        ManaData data = ManaPlayer.get(player);
        if (!data.isLawsUnlocked() || data.getCircle() < Circles.MAX_CIRCLE
                || Law.byId(data.getSelectedLaw()) != Law.CREATION) {
            ctx.getSource().sendFailure(Component.literal("You must have the Law of Creation active at the 9th circle."));
            return 0;
        }
        double cost = 2000.0 * count;
        if (!data.trySpendMana(cost)) {
            ctx.getSource().sendFailure(Component.literal("Not enough mana — " + (long) cost + " required."));
            return 0;
        }
        ItemStack stack = ItemArgument.getItem(ctx, "item").createItemStack(count);
        if (!player.getInventory().add(stack) && !stack.isEmpty()) {
            player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
        }
        ModNetworking.syncToClient(player);
        feedback(ctx, "Created " + count + "x " + stack.getHoverName().getString() + " for " + (long) cost + " mana.");
        return 1;
    }

    private static int spawnMage(CommandContext<CommandSourceStack> ctx) {
        return spawnMageVariant(ctx, IntegerArgumentType.getInteger(ctx, "circle"), false);
    }

    private static int spawnProdigy(CommandContext<CommandSourceStack> ctx) {
        // The Prodigy is always a Circle 3 mage.
        return spawnMageVariant(ctx, 3, true);
    }

    /** Spawns a Wandering Mage of the given circle (and optionally the Prodigy) in front of the source. */
    private static int spawnMageVariant(CommandContext<CommandSourceStack> ctx, int circle, boolean prodigy) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel world = source.getLevel();
        Vec2 rotation = source.getRotation();
        Vec3 look = Vec3.directionFromRotation(rotation);
        Vec3 pos = source.getPosition().add(look.x * 4.0, 0.0, look.z * 4.0);

        WanderingMageEntity mage = ModEntities.WANDERING_MAGE.create(world, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        if (mage == null) {
            source.sendFailure(Component.literal("Failed to create the Wandering Mage entity."));
            return 0;
        }
        mage.snapTo(pos.x, pos.y, pos.z, rotation.y + 180.0f, 0.0f);
        mage.configureForSpawn(circle, prodigy);
        world.addFreshEntity(mage);

        feedback(ctx, prodigy ? "Spawned The Prodigy (Circle 3)." : "Spawned a Circle " + circle + " Wandering Mage.");
        return 1;
    }

    private static int setManaMultiplier(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = playerOrNull(ctx);
        if (player == null) {
            return 0;
        }
        double value = DoubleArgumentType.getDouble(ctx, "value");
        ManaPlayer.get(player).setDebugManaMultiplier(value);
        feedback(ctx, "Mana-gain debug multiplier set to " + value + "x");
        return 1;
    }

    private static int addMana(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = playerOrNull(ctx);
        if (player == null) {
            return 0;
        }
        double amount = DoubleArgumentType.getDouble(ctx, "amount");
        ManaData data = ManaPlayer.get(player);
        data.addMana(amount);
        ModNetworking.syncToClient(player);
        feedback(ctx, "Added " + amount + " mana (now " + (long) data.getCurrentMana() + ")");
        return 1;
    }

    private static int setMana(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = playerOrNull(ctx);
        if (player == null) {
            return 0;
        }
        double amount = DoubleArgumentType.getDouble(ctx, "amount");
        ManaData data = ManaPlayer.get(player);
        data.setCurrentMana(amount);
        ModNetworking.syncToClient(player);
        feedback(ctx, "Mana set to " + amount);
        return 1;
    }

    private static int setCircle(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = playerOrNull(ctx);
        if (player == null) {
            return 0;
        }
        int circle = IntegerArgumentType.getInteger(ctx, "circle");
        ManaData data = ManaPlayer.get(player);
        data.setCircle(circle);
        data.setCurrentMana(0.0);
        ModNetworking.syncToClient(player);
        feedback(ctx, "Circle set to " + circle);
        return 1;
    }

    private static int setCooldowns(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = playerOrNull(ctx);
        if (player == null) {
            return 0;
        }
        boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
        ManaPlayer.get(player).setCooldownsEnabled(enabled);
        feedback(ctx, "Spell cooldowns " + (enabled ? "enabled" : "disabled"));
        return 1;
    }

    private static ServerPlayer playerOrNull(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("This command must be run by a player."));
        }
        return player;
    }

    private static void feedback(CommandContext<CommandSourceStack> ctx, String message) {
        ctx.getSource().sendSuccess(() -> Component.literal(message).withStyle(ChatFormatting.LIGHT_PURPLE), false);
    }
}
