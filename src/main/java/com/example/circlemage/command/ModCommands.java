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
import net.minecraft.command.argument.ItemStackArgumentType;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;

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
            dispatcher.register(CommandManager.literal("circlemage")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(CommandManager.literal("manamultiplier")
                            .then(CommandManager.argument("value", DoubleArgumentType.doubleArg(0.0))
                                    .executes(ModCommands::setManaMultiplier)))
                    .then(CommandManager.literal("addmana")
                            .then(CommandManager.argument("amount", DoubleArgumentType.doubleArg())
                                    .executes(ModCommands::addMana)))
                    .then(CommandManager.literal("setmana")
                            .then(CommandManager.argument("amount", DoubleArgumentType.doubleArg(0.0))
                                    .executes(ModCommands::setMana)))
                    .then(CommandManager.literal("setcircle")
                            .then(CommandManager.argument("circle", IntegerArgumentType.integer(0, Circles.MAX_CIRCLE))
                                    .executes(ModCommands::setCircle)))
                    .then(CommandManager.literal("cooldowns")
                            .then(CommandManager.argument("enabled", BoolArgumentType.bool())
                                    .executes(ModCommands::setCooldowns)))
                    .then(CommandManager.literal("unlocklaws")
                            .executes(ModCommands::unlockLaws))
                    .then(CommandManager.literal("unlockalllaws")
                            .executes(ModCommands::unlockAllLaws))
                    .then(CommandManager.literal("spawnmage")
                            .then(CommandManager.argument("circle", IntegerArgumentType.integer(1, 3))
                                    .executes(ModCommands::spawnMage)))
                    .then(CommandManager.literal("spawnprodigy")
                            .executes(ModCommands::spawnProdigy)));

            // Law of Creation: usable by any player who has that Law active (costs mana, not op).
            dispatcher.register(CommandManager.literal("lawcreate")
                    .then(CommandManager.argument("item", ItemStackArgumentType.itemStack(registryAccess))
                            .executes(ctx -> lawCreate(ctx, 1))
                            .then(CommandManager.argument("count", IntegerArgumentType.integer(1, 64))
                                    .executes(ctx -> lawCreate(ctx, IntegerArgumentType.getInteger(ctx, "count"))))));
        });
    }

    private static int unlockLaws(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = playerOrNull(ctx);
        if (player == null) {
            return 0;
        }
        LawUnlocks.unlockBase(player, ManaPlayer.get(player));
        feedback(ctx, "Laws discovered (Love of the World + Creation). Others have their own conditions.");
        return 1;
    }

    private static int unlockAllLaws(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = playerOrNull(ctx);
        if (player == null) {
            return 0;
        }
        LawUnlocks.unlockAll(player, ManaPlayer.get(player));
        feedback(ctx, "All Laws unlocked.");
        return 1;
    }

    private static int lawCreate(CommandContext<ServerCommandSource> ctx, int count) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendError(Text.literal("This command must be run by a player."));
            return 0;
        }
        ManaData data = ManaPlayer.get(player);
        if (!data.isLawsUnlocked() || data.getCircle() < Circles.MAX_CIRCLE
                || Law.byId(data.getSelectedLaw()) != Law.CREATION) {
            ctx.getSource().sendError(Text.literal("You must have the Law of Creation active at the 9th circle."));
            return 0;
        }
        double cost = 2000.0 * count;
        if (!data.trySpendMana(cost)) {
            ctx.getSource().sendError(Text.literal("Not enough mana — " + (long) cost + " required."));
            return 0;
        }
        ItemStack stack = ItemStackArgumentType.getItemStackArgument(ctx, "item").createStack(count, false);
        if (!player.getInventory().insertStack(stack) && !stack.isEmpty()) {
            player.dropItem(stack, false);
        }
        ModNetworking.syncToClient(player);
        feedback(ctx, "Created " + count + "x " + stack.getName().getString() + " for " + (long) cost + " mana.");
        return 1;
    }

    private static int spawnMage(CommandContext<ServerCommandSource> ctx) {
        return spawnMageVariant(ctx, IntegerArgumentType.getInteger(ctx, "circle"), false);
    }

    private static int spawnProdigy(CommandContext<ServerCommandSource> ctx) {
        // The Prodigy is always a Circle 3 mage.
        return spawnMageVariant(ctx, 3, true);
    }

    /** Spawns a Wandering Mage of the given circle (and optionally the Prodigy) in front of the source. */
    private static int spawnMageVariant(CommandContext<ServerCommandSource> ctx, int circle, boolean prodigy) {
        ServerCommandSource source = ctx.getSource();
        ServerWorld world = source.getWorld();
        Vec2f rotation = source.getRotation();
        Vec3d look = Vec3d.fromPolar(rotation);
        Vec3d pos = source.getPosition().add(look.x * 4.0, 0.0, look.z * 4.0);

        WanderingMageEntity mage = ModEntities.WANDERING_MAGE.create(world);
        if (mage == null) {
            source.sendError(Text.literal("Failed to create the Wandering Mage entity."));
            return 0;
        }
        mage.refreshPositionAndAngles(pos.x, pos.y, pos.z, rotation.y + 180.0f, 0.0f);
        mage.configureForSpawn(circle, prodigy);
        world.spawnEntity(mage);

        feedback(ctx, prodigy ? "Spawned The Prodigy (Circle 3)." : "Spawned a Circle " + circle + " Wandering Mage.");
        return 1;
    }

    private static int setManaMultiplier(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = playerOrNull(ctx);
        if (player == null) {
            return 0;
        }
        double value = DoubleArgumentType.getDouble(ctx, "value");
        ManaPlayer.get(player).setDebugManaMultiplier(value);
        feedback(ctx, "Mana-gain debug multiplier set to " + value + "x");
        return 1;
    }

    private static int addMana(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = playerOrNull(ctx);
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

    private static int setMana(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = playerOrNull(ctx);
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

    private static int setCircle(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = playerOrNull(ctx);
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

    private static int setCooldowns(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = playerOrNull(ctx);
        if (player == null) {
            return 0;
        }
        boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
        ManaPlayer.get(player).setCooldownsEnabled(enabled);
        feedback(ctx, "Spell cooldowns " + (enabled ? "enabled" : "disabled"));
        return 1;
    }

    private static ServerPlayerEntity playerOrNull(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendError(Text.literal("This command must be run by a player."));
        }
        return player;
    }

    private static void feedback(CommandContext<ServerCommandSource> ctx, String message) {
        ctx.getSource().sendFeedback(() -> Text.literal(message).formatted(Formatting.LIGHT_PURPLE), false);
    }
}
