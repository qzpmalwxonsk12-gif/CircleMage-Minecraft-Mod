package com.example.circlemage.command;

import com.example.circlemage.boss.BossZone;
import com.example.circlemage.boss.BossZoneManager;
import com.example.circlemage.entity.MasterEntity;
import com.example.circlemage.entity.ModEntities;
import com.example.circlemage.spell.Spell;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

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
                dispatcher.register(CommandManager.literal("bossmagic")
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(CommandManager.literal("domain").executes(BossMagicCommands::domain))
                        .then(CommandManager.literal("removedomain").executes(BossMagicCommands::removeDomain))
                        .then(CommandManager.literal("summonmaster").executes(BossMagicCommands::summonMaster))
                        .then(CommandManager.literal("meteorrain").executes(BossMagicCommands::meteorRain))
                        .then(CommandManager.literal("meteorfrequency")
                                .then(CommandManager.literal("magicmissile")
                                        .then(CommandManager.argument("rate", DoubleArgumentType.doubleArg(0.1, 1000.0))
                                                .executes(ctx -> meteorFrequency(ctx, false))))
                                .then(CommandManager.literal("fireball")
                                        .then(CommandManager.argument("rate", DoubleArgumentType.doubleArg(0.1, 1000.0))
                                                .executes(ctx -> meteorFrequency(ctx, true)))))
                        // Restrict ONE form of magic at a time (or disable). Future bosses can call
                        // BossZone.setRestricted(...) directly to build any custom set.
                        .then(CommandManager.literal("restrictmagic")
                                .then(CommandManager.literal("disable").executes(BossMagicCommands::restrictDisable))
                                .then(CommandManager.literal("magicmissile")
                                        .executes(ctx -> restrictOne(ctx, "Magic Missile",
                                                Spell.MAGIC_MISSILE.id, Spell.SPEAR_OF_LIGHT.id)))
                                .then(CommandManager.literal("fireball")
                                        .executes(ctx -> restrictOne(ctx, "Burn / Fireball", Spell.FIREBALL.id)))
                                .then(CommandManager.literal("windwall")
                                        .executes(ctx -> restrictOne(ctx, "Wind Wall", Spell.WALL_OF_WIND.id)))
                                .then(CommandManager.literal("thunder")
                                        .executes(ctx -> restrictOne(ctx, "Thunder", Spell.THUNDER.id)))
                                .then(CommandManager.literal("dash")
                                        .executes(ctx -> restrictOne(ctx, "Dash", Spell.DASH.id))))));
    }

    private static int domain(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = playerOrNull(ctx);
        if (player == null) {
            return 0;
        }
        ServerWorld world = player.getServerWorld();
        Vec3d center = player.getPos().add(0, 1.0, 0);

        if (BossZoneManager.zoneByOwner(player.getUuid()) != null) {
            ctx.getSource().sendError(Text.literal("You already have an active domain — /bossmagic removedomain first."));
            return 0;
        }
        if (BossZoneManager.wouldOverlap(world, center, DOMAIN_RADIUS)) {
            ctx.getSource().sendError(Text.literal("That would overlap an existing Boss Zone."));
            return 0;
        }
        BossZoneManager.register(new BossZone(world, center, DOMAIN_RADIUS, player.getUuid()));
        feedback(ctx, "Domain Barrier raised (radius 100). The area is now a Boss Zone.");
        return 1;
    }

    private static int removeDomain(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = playerOrNull(ctx);
        if (player == null) {
            return 0;
        }
        if (BossZoneManager.removeByOwner(player.getUuid())) {
            feedback(ctx, "Domain Barrier removed.");
            return 1;
        }
        ctx.getSource().sendError(Text.literal("You have no active domain."));
        return 0;
    }

    private static int summonMaster(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = playerOrNull(ctx);
        if (player == null) {
            return 0;
        }
        ServerWorld world = player.getServerWorld();
        // 50 blocks away in the direction the summoner faces (horizontal).
        Vec3d look = Vec3d.fromPolar(0.0f, player.getYaw());
        double dx = look.x, dz = look.z;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0e-4) {
            dx = 0;
            dz = 1;
            len = 1;
        }
        double x = player.getX() + dx / len * 50.0;
        double z = player.getZ() + dz / len * 50.0;
        int y = world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                (int) Math.floor(x), (int) Math.floor(z));

        MasterEntity master = ModEntities.MASTER.create(world);
        if (master == null) {
            ctx.getSource().sendError(Text.literal("Failed to create The Master."));
            return 0;
        }
        master.refreshPositionAndAngles(x + 0.5, y, z + 0.5, player.getYaw() + 180.0f, 0.0f);
        master.onSummoned();
        world.spawnEntity(master);
        feedback(ctx, "The Master has been summoned 50 blocks away — a beam marks his location.");
        return 1;
    }

    private static int meteorRain(CommandContext<ServerCommandSource> ctx) {
        BossZone zone = requireZone(ctx);
        if (zone == null) {
            return 0;
        }
        boolean active = zone.toggleMeteorRain();
        BossZoneManager.markDirty();
        feedback(ctx, active ? "Meteor Rain has begun within the domain." : "Meteor Rain stopped.");
        return 1;
    }

    private static int meteorFrequency(CommandContext<ServerCommandSource> ctx, boolean fireball) {
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
    private static int restrictOne(CommandContext<ServerCommandSource> ctx, String label, int... spellIds) {
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
        Text message = Text.literal("The domain suppresses certain forms of magic.").formatted(Formatting.DARK_PURPLE);
        for (ServerPlayerEntity p : zone.getWorld().getPlayers()) {
            if (zone.contains(p.getPos())) {
                p.sendMessage(message, false);
            }
        }
        feedback(ctx, "Magic Restriction Field now suppresses: " + label + ".");
        return 1;
    }

    private static int restrictDisable(CommandContext<ServerCommandSource> ctx) {
        BossZone zone = requireZone(ctx);
        if (zone == null) {
            return 0;
        }
        zone.setRestrictionsActive(false);
        BossZoneManager.markDirty();
        feedback(ctx, "Magic Restriction Field disabled.");
        return 1;
    }

    private static BossZone requireZone(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = playerOrNull(ctx);
        if (player == null) {
            return null;
        }
        BossZone zone = BossZoneManager.zoneByOwner(player.getUuid());
        if (zone == null) {
            ctx.getSource().sendError(Text.literal("No active Boss Zone — run /bossmagic domain first."));
        }
        return zone;
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
