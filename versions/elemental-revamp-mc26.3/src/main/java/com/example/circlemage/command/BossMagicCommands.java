package com.example.circlemage.command;

import com.example.circlemage.boss.BossZone;
import com.example.circlemage.boss.BossZoneManager;
import com.example.circlemage.entity.MasterEntity;
import com.example.circlemage.entity.ModEntities;
import com.example.circlemage.spell.Spell;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Test/admin commands for the Boss Magic systems (op only). These abilities are boss/event-only — they
 * are never available to players except through these commands.
 *
 *   /bossmagic domain                      raise a 100-block Domain Barrier (Boss Zone) on yourself
 *   /bossmagic removedomain                remove your Domain Barrier
 *   /bossmagic meteorrain                  toggle Meteor Rain inside your Boss Zone
 *   /bossmagic restrictmagic enable|disable  toggle the Magic Restriction Field in your Boss Zone
 */
public final class BossMagicCommands {

    private static final double DOMAIN_RADIUS = 50.0;

    private BossMagicCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("bossmagic")
                        .requires(source -> source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
                        .then(Commands.literal("domain").executes(BossMagicCommands::domain))
                        .then(Commands.literal("removedomain").executes(BossMagicCommands::removeDomain))
                        .then(Commands.literal("summonmaster").executes(BossMagicCommands::summonMaster))
                        .then(Commands.literal("meteorrain").executes(BossMagicCommands::meteorRain))
                        .then(Commands.literal("meteorfrequency")
                                .then(Commands.literal("magicmissile")
                                        .then(Commands.argument("rate", DoubleArgumentType.doubleArg(0.1, 1000.0))
                                                .executes(ctx -> meteorFrequency(ctx, false))))
                                .then(Commands.literal("fireball")
                                        .then(Commands.argument("rate", DoubleArgumentType.doubleArg(0.1, 1000.0))
                                                .executes(ctx -> meteorFrequency(ctx, true)))))
                        // Restrict ONE form of magic at a time (or disable). Future bosses can call
                        // BossZone.setRestricted(...) directly to build any custom set.
                        .then(Commands.literal("restrictmagic")
                                .then(Commands.literal("disable").executes(BossMagicCommands::restrictDisable))
                                .then(Commands.literal("magicmissile")
                                        .executes(ctx -> restrictOne(ctx, "Magic Missile",
                                                Spell.MAGIC_MISSILE.id, Spell.SPEAR_OF_LIGHT.id)))
                                .then(Commands.literal("fireball")
                                        .executes(ctx -> restrictOne(ctx, "Burn / Fireball", Spell.FIREBALL.id)))
                                .then(Commands.literal("windwall")
                                        .executes(ctx -> restrictOne(ctx, "Wind Wall", Spell.WALL_OF_WIND.id)))
                                .then(Commands.literal("thunder")
                                        .executes(ctx -> restrictOne(ctx, "Thunder", Spell.THUNDER.id)))
                                .then(Commands.literal("dash")
                                        .executes(ctx -> restrictOne(ctx, "Dash", Spell.DASH.id))))));
    }

    private static int domain(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = playerOrNull(ctx);
        if (player == null) {
            return 0;
        }
        ServerLevel world = player.level();
        Vec3 center = player.position().add(0, 1.0, 0);

        if (BossZoneManager.zoneByOwner(player.getUUID()) != null) {
            ctx.getSource().sendFailure(Component.literal("You already have an active domain — /bossmagic removedomain first."));
            return 0;
        }
        if (BossZoneManager.wouldOverlap(world, center, DOMAIN_RADIUS)) {
            ctx.getSource().sendFailure(Component.literal("That would overlap an existing Boss Zone."));
            return 0;
        }
        BossZoneManager.register(new BossZone(world, center, DOMAIN_RADIUS, player.getUUID()));
        feedback(ctx, "Domain Barrier raised (radius 100). The area is now a Boss Zone.");
        return 1;
    }

    private static int removeDomain(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = playerOrNull(ctx);
        if (player == null) {
            return 0;
        }
        if (BossZoneManager.removeByOwner(player.getUUID())) {
            feedback(ctx, "Domain Barrier removed.");
            return 1;
        }
        ctx.getSource().sendFailure(Component.literal("You have no active domain."));
        return 0;
    }

    private static int summonMaster(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = playerOrNull(ctx);
        if (player == null) {
            return 0;
        }
        ServerLevel world = player.level();
        // 50 blocks away in the direction the summoner faces (horizontal).
        Vec3 look = Vec3.directionFromRotation(0.0f, player.getYRot());
        double dx = look.x, dz = look.z;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0e-4) {
            dx = 0;
            dz = 1;
            len = 1;
        }
        double x = player.getX() + dx / len * 50.0;
        double z = player.getZ() + dz / len * 50.0;
        int y = world.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (int) Math.floor(x), (int) Math.floor(z));

        MasterEntity master = ModEntities.MASTER.create(world, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        if (master == null) {
            ctx.getSource().sendFailure(Component.literal("Failed to create The Master."));
            return 0;
        }
        master.snapTo(x + 0.5, y, z + 0.5, player.getYRot() + 180.0f, 0.0f);
        master.onSummoned();
        world.addFreshEntity(master);
        feedback(ctx, "The Master has been summoned 50 blocks away — a beam marks his location.");
        return 1;
    }

    private static int meteorRain(CommandContext<CommandSourceStack> ctx) {
        BossZone zone = requireZone(ctx);
        if (zone == null) {
            return 0;
        }
        boolean active = zone.toggleMeteorRain();
        BossZoneManager.markDirty();
        feedback(ctx, active ? "Meteor Rain has begun within the domain." : "Meteor Rain stopped.");
        return 1;
    }

    private static int meteorFrequency(CommandContext<CommandSourceStack> ctx, boolean fireball) {
        BossZone zone = requireZone(ctx);
        if (zone == null) {
            return 0;
        }
        double rate = DoubleArgumentType.getDouble(ctx, "rate");
        if (fireball) {
            zone.setMeteorRateFireball(rate);
        } else {
            zone.setMeteorRateMissile(rate);
        }
        BossZoneManager.markDirty();
        feedback(ctx, String.format("%s Meteor frequency set to %.1fx.",
                fireball ? "Fireball" : "Magic Missile", rate));
        return 1;
    }

    /** Suppresses exactly ONE form of magic (clearing any previous restriction). */
    private static int restrictOne(CommandContext<CommandSourceStack> ctx, String label, int... spellIds) {
        BossZone zone = requireZone(ctx);
        if (zone == null) {
            return 0;
        }
        zone.clearRestrictions();
        for (int id : spellIds) {
            zone.setRestricted(id, true);
        }
        zone.setRestrictionsActive(true);
        BossZoneManager.markDirty();
        Component message = Component.literal("The domain suppresses certain forms of magic.").withStyle(ChatFormatting.DARK_PURPLE);
        for (ServerPlayer p : zone.getWorld().players()) {
            if (zone.contains(p.position())) {
                com.example.circlemage.util.Messages.send(p, message, false);
            }
        }
        feedback(ctx, "Magic Restriction Field now suppresses: " + label + ".");
        return 1;
    }

    private static int restrictDisable(CommandContext<CommandSourceStack> ctx) {
        BossZone zone = requireZone(ctx);
        if (zone == null) {
            return 0;
        }
        zone.setRestrictionsActive(false);
        BossZoneManager.markDirty();
        feedback(ctx, "Magic Restriction Field disabled.");
        return 1;
    }

    private static BossZone requireZone(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = playerOrNull(ctx);
        if (player == null) {
            return null;
        }
        BossZone zone = BossZoneManager.zoneByOwner(player.getUUID());
        if (zone == null) {
            ctx.getSource().sendFailure(Component.literal("No active Boss Zone — run /bossmagic domain first."));
        }
        return zone;
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
