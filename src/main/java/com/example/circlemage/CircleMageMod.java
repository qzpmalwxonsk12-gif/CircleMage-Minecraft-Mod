package com.example.circlemage;

import com.example.circlemage.command.ModCommands;
import com.example.circlemage.entity.DarkOrbEntity;
import com.example.circlemage.entity.MagicBarrierEntity;
import com.example.circlemage.entity.MagicMissileEntity;
import com.example.circlemage.entity.ModEntities;
import com.example.circlemage.entity.SpatialRendEntity;
import com.example.circlemage.entity.WanderingMageEntity;
import com.example.circlemage.spell.Law;
import com.example.circlemage.spell.LawUnlocks;
import com.example.circlemage.mana.Circles;
import com.example.circlemage.mana.ManaData;
import com.example.circlemage.mana.ManaPlayer;
import com.example.circlemage.network.ModNetworking;
import com.example.circlemage.particle.ModParticles;
import com.example.circlemage.spell.AreaEffect;
import com.example.circlemage.spell.AreaEffectManager;
import com.example.circlemage.spell.ArrayType;
import com.example.circlemage.spell.CombinationSpell;
import com.example.circlemage.spell.Combinations;
import com.example.circlemage.spell.MagicArray;
import com.example.circlemage.spell.MagicArrayManager;
import com.example.circlemage.spell.PassiveChant;
import com.example.circlemage.spell.Spell;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.entry.EmptyEntry;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.advancement.Advancement;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CircleMageMod implements ModInitializer {

    public static final String MOD_ID = "circlemage";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final Identifier ANTI_MAGIC_ADVANCEMENT = new Identifier(MOD_ID, "basic_anti_magic");
    public static final Identifier ARRAY_ADVANCEMENT = new Identifier(MOD_ID, "array_carving");
    public static final Identifier COMBINATION_ADVANCEMENT = new Identifier(MOD_ID, "combination_magic");
    public static final Identifier LAWS_ADVANCEMENT = new Identifier(MOD_ID, "laws");

    /** Per-second chance that the Laws system unlocks on its own while playing (0.01%). */
    private static final double LAWS_UNLOCK_CHANCE = 0.0001;

    /** Maximum spell charge (ticks) reachable by holding a primed active chant — must match the client. */
    public static final int PRIME_MAX_TICKS = 60; // 3 seconds

    /** Attribute modifier used to freeze the player in place while channelling (-100% move speed). */
    private static final UUID CHANNEL_SLOW_UUID = UUID.fromString("8d2f1c4a-3b6e-4f0a-9c1d-7e5a2b8f3c1d");
    private static final EntityAttributeModifier CHANNEL_SLOW_MODIFIER = new EntityAttributeModifier(
            CHANNEL_SLOW_UUID, "Channelling mana", -1.0, EntityAttributeModifier.Operation.MULTIPLY_TOTAL);

    /** Reinforce chant: +20% movement speed. */
    private static final UUID REINFORCE_SPEED_UUID = UUID.fromString("2a7c9e10-5b4d-4f2a-8c3e-1d6f9b0a4e72");
    private static final EntityAttributeModifier REINFORCE_SPEED_MODIFIER = new EntityAttributeModifier(
            REINFORCE_SPEED_UUID, "Reinforce chant", 0.20, EntityAttributeModifier.Operation.MULTIPLY_TOTAL);

    /** Legacy Vitality bonus-hearts modifier UUID. Vitality no longer grants hearts (1.0.8.3); this is
     *  kept only so {@link #applyVitality} can strip the modifier from players who still carry it. */
    private static final UUID VITALITY_HEALTH_UUID = UUID.fromString("4f1b8d3e-6c2a-4e9b-9f1d-3a7c5e0b2d84");

    // Roll chances (per magic use).
    private static final double ANTI_MAGIC_CHANCE = 0.005; // 0.5%
    private static final double REALM_BUFF_CHANCE = 0.05;  // 5%
    private static final double ARRAY_UNLOCK_CHANCE = 0.01; // 1%
    private static final double CHANT_UNLOCK_CHANCE = 0.05; // 5%
    private static final double COMBO_ADV_CHANCE = 0.02;    // 2% to learn Combination Magic at the 5th circle

    /** Passive chant upkeep (ATTACK/REINFORCE): 200 mana per second. */
    private static final double CHANT_UPKEEP_PER_TICK = 200.0 / 20.0;
    /** Vitality chant upkeep: 100 mana per second (added 1.0.8.3). */
    private static final double VITALITY_UPKEEP_PER_TICK = 100.0 / 20.0;

    private static final int ANTI_MAGIC_IMMUNITY_TICKS = 100; // 5 seconds

    // Magic Array carving.
    private static final int CARVE_COST = 1000;
    private static final int CARVE_DURATION_TICKS = 40;       // 2s to carve
    private static final int CARVE_SUCCESS_COOLDOWN = 100;    // 5s after carving
    private static final int CARVE_CANCEL_COOLDOWN = 60;      // 3s if interrupted
    private static final int ARRAY_LIFETIME_TICKS = 600;      // arrays persist 30s
    private static final double CARVE_MOVE_THRESHOLD_SQ = 0.04; // ~0.2 block

    @Override
    public void onInitialize() {
        ModEntities.register();
        registerWanderingMageSpawning();
        com.example.circlemage.item.ModItems.register();
        com.example.circlemage.block.ModBlocks.register();
        registerManaCrystalLoot();
        ModParticles.register();
        registerPacketReceivers();
        ModCommands.register();
        com.example.circlemage.command.BossMagicCommands.register();
        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);

        // Law of Life: cancel the player's death, reviving them where they stand. 100000 mana normally;
        // in hardcore it costs 10x and shows the "give up?" screen (you can't, of course).
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayerEntity player) {
                ManaData data = ManaPlayer.get(player);
                boolean hardcore = player.getServer() != null && player.getServer().isHardcore();
                double cost = hardcore ? 1000000.0 : 100000.0;
                if (data.getCircle() >= Circles.MAX_CIRCLE && data.isLawUnlocked(Law.LIFE.ordinal())
                        && Law.byId(data.getSelectedLaw()) == Law.LIFE && data.trySpendMana(cost)) {
                    player.setHealth(player.getMaxHealth());
                    player.clearStatusEffects();
                    player.setFireTicks(0);
                    if (player.getWorld() instanceof ServerWorld world) {
                        world.spawnParticles(ModParticles.RADIANCE, player.getX(), player.getBodyY(0.5), player.getZ(),
                                60, 0.5, 1.0, 0.5, 0.1);
                    }
                    if (hardcore) {
                        ModNetworking.sendGiveUp(player);
                    }
                    ModNetworking.syncToClient(player);
                    return false; // death cancelled
                }
            }
            return true;
        });

        // Preserve all progress across death (and dimension changes).
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            ManaPlayer.get(newPlayer).copyFrom(ManaPlayer.get(oldPlayer));
            // Re-apply chant attribute modifiers on the new entity.
            ManaData data = ManaPlayer.get(newPlayer);
            PassiveChant chant = PassiveChant.byId(data.getSelectedChant());
            if (data.isPassiveChantUnlocked()) {
                applyReinforceSpeed(newPlayer, chant == PassiveChant.REINFORCE);
                applyVitality(newPlayer, chant == PassiveChant.VITALITY);
            }
        });
        // Push the preserved state to the client after respawn and on join; count deaths for Law of Life.
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (!alive) { // actually died (not an end-portal return)
                LawUnlocks.recordDeath(newPlayer, ManaPlayer.get(newPlayer));
            }
            ModNetworking.syncToClient(newPlayer);
        });
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ModNetworking.syncToClient(handler.player);
            ModNetworking.sendJournalSync(handler.player);
            // Fresh player: play the "woke up with a book" intro one tick later (world/pos settled).
            server.execute(() -> maybeStartBookIntro(handler.player));
        });

        // Boss Zones persist across world reloads: load them when the server starts, and clear the
        // live registry when it stops so zones never leak between worlds in one session.
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(
                com.example.circlemage.boss.BossZoneState::getAndLoad);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            com.example.circlemage.boss.BossZoneManager.clear();
            WAND_LIGHTS.clear();
        });
        // Clean up a leaving player's glow-wand light so it never lingers in the world.
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> removeWandLight(server, handler.player.getUuid()));

        LOGGER.info("Circle Mage initialised.");
    }

    /**
     * Wandering Mage: attributes, the on-ground/lit natural-spawn restriction, and an Overworld spawn
     * entry. The {@code WEIGHT} is the knob for rarity — tuned for roughly one encounter every
     * 100–200 blocks of surface exploration; raise it for more frequent mages, lower it for fewer.
     */
    private void registerWanderingMageSpawning() {
        final int WEIGHT = 6;
        FabricDefaultAttributeRegistry.register(ModEntities.WANDERING_MAGE, WanderingMageEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.MASTER,
                com.example.circlemage.entity.MasterEntity.createAttributes());
        SpawnRestriction.register(ModEntities.WANDERING_MAGE, SpawnRestriction.Location.ON_GROUND,
                Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, WanderingMageEntity::canSpawn);
        BiomeModifications.addSpawn(BiomeSelectors.foundInOverworld(), SpawnGroup.MONSTER,
                ModEntities.WANDERING_MAGE, WEIGHT, 1, 1);
    }

    /**
     * Sprinkles mod treasure into vanilla chest loot ("chests across the world"):
     * <ul>
     *     <li>Mana Crystals — a 1-in-10 roll per chest (a typical village yields about one).</li>
     *     <li>Magic Tomes (v2.1) — a 1-in-12 roll per chest, weighted toward the elements that
     *     currently hold spells, so a player finds roughly one tome per village or two.</li>
     * </ul>
     * The {@code EmptyEntry} weights are the rarity knobs.
     */
    private void registerManaCrystalLoot() {
        LootTableEvents.MODIFY.register((resourceManager, lootManager, id, tableBuilder, source) -> {
            if (source.isBuiltin() && id.getNamespace().equals("minecraft") && id.getPath().startsWith("chests/")) {
                tableBuilder.pool(LootPool.builder()
                        .rolls(ConstantLootNumberProvider.create(1.0f))
                        .with(ItemEntry.builder(com.example.circlemage.item.ModItems.MANA_CRYSTAL).weight(1))
                        .with(EmptyEntry.builder().weight(9)));
                tableBuilder.pool(LootPool.builder()
                        .rolls(ConstantLootNumberProvider.create(1.0f))
                        .with(ItemEntry.builder(com.example.circlemage.item.ModItems.TOME_FIRE).weight(3))
                        .with(ItemEntry.builder(com.example.circlemage.item.ModItems.TOME_WIND).weight(3))
                        .with(ItemEntry.builder(com.example.circlemage.item.ModItems.TOME_MISC).weight(3))
                        .with(ItemEntry.builder(com.example.circlemage.item.ModItems.TOME_WATER).weight(2))
                        .with(ItemEntry.builder(com.example.circlemage.item.ModItems.TOME_EARTH).weight(2))
                        .with(EmptyEntry.builder().weight(143)));
            }
        });
    }

    private void registerPacketReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(ModNetworking.CHANNEL_TOGGLE,
                (server, player, handler, buf, sender) -> server.execute(() -> toggleChanneling(player)));

        ServerPlayNetworking.registerGlobalReceiver(ModNetworking.CAST, (server, player, handler, buf, sender) -> {
            int spellId = buf.readInt();
            boolean primed = buf.readBoolean();
            int charge = buf.readInt();
            server.execute(() -> handleCast(player, spellId, primed, charge));
        });

        ServerPlayNetworking.registerGlobalReceiver(ModNetworking.COMBINE, (server, player, handler, buf, sender) -> {
            int spellA = buf.readInt();
            int charge = buf.readInt();
            int spellB = buf.readInt();
            server.execute(() -> handleCombine(player, spellA, charge, spellB));
        });

        ServerPlayNetworking.registerGlobalReceiver(ModNetworking.SELECT_FORM, (server, player, handler, buf, sender) -> {
            int baseSpellId = buf.readInt();
            boolean revert = buf.readBoolean();
            server.execute(() -> selectForm(player, baseSpellId, revert));
        });

        ServerPlayNetworking.registerGlobalReceiver(ModNetworking.CARVE,
                (server, player, handler, buf, sender) -> server.execute(() -> startCarving(player)));

        ServerPlayNetworking.registerGlobalReceiver(ModNetworking.SELECT_ARRAY, (server, player, handler, buf, sender) -> {
            int arrayId = buf.readInt();
            server.execute(() -> selectArray(player, arrayId));
        });

        ServerPlayNetworking.registerGlobalReceiver(ModNetworking.SELECT_CHANT, (server, player, handler, buf, sender) -> {
            int chantId = buf.readInt();
            server.execute(() -> selectChant(player, chantId));
        });

        ServerPlayNetworking.registerGlobalReceiver(ModNetworking.SELECT_LAW, (server, player, handler, buf, sender) -> {
            int lawId = buf.readInt();
            server.execute(() -> selectLaw(player, lawId));
        });

        ServerPlayNetworking.registerGlobalReceiver(ModNetworking.ACTIVATE_LAW,
                (server, player, handler, buf, sender) -> server.execute(() -> activateLaw(player)));

        ServerPlayNetworking.registerGlobalReceiver(ModNetworking.OPEN_STORAGE,
                (server, player, handler, buf, sender) -> server.execute(() -> openStorage(player)));

        ServerPlayNetworking.registerGlobalReceiver(ModNetworking.DASH, (server, player, handler, buf, sender) -> {
            float forward = buf.readFloat();
            float strafe = buf.readFloat();
            server.execute(() -> handleDash(player, forward, strafe));
        });

        ServerPlayNetworking.registerGlobalReceiver(ModNetworking.REQUEST_JOURNAL,
                (server, player, handler, buf, sender) -> server.execute(() -> ModNetworking.sendJournalSync(player)));

        ServerPlayNetworking.registerGlobalReceiver(ModNetworking.BOOK_CLOSED,
                (server, player, handler, buf, sender) -> server.execute(() -> onBookClosed(player)));
    }

    // ------------------------------------------------------------------------
    // Intro cinematic (v2.2.1): the "woke up with a book" sequence
    // ------------------------------------------------------------------------

    /** Fresh player (intro stage 0): drop the tutorial book in front of them and show the opening line. */
    private void maybeStartBookIntro(ServerPlayerEntity player) {
        ManaData data = ManaPlayer.get(player);
        if (data.getIntroStage() != 0 || !(player.getWorld() instanceof ServerWorld world)) {
            return;
        }
        Vec3d look = player.getRotationVec(1.0f);
        Vec3d horiz = new Vec3d(look.x, 0.0, look.z);
        if (horiz.lengthSquared() < 1.0e-4) {
            horiz = new Vec3d(0, 0, 1);
        }
        Vec3d pos = player.getPos().add(horiz.normalize().multiply(1.1)).add(0, 0.3, 0);
        com.example.circlemage.entity.TutorialBookEntity book =
                new com.example.circlemage.entity.TutorialBookEntity(ModEntities.TUTORIAL_BOOK, world);
        book.setPosition(pos.x, pos.y, pos.z);
        book.setYaw(player.getYaw());
        world.spawnEntity(book);
        world.spawnParticles(ModParticles.ARCANE, pos.x, pos.y + 0.2, pos.z, 20, 0.2, 0.2, 0.2, 0.03);
        sendCinematic(player, Text.literal("You just woke up with a book?").formatted(Formatting.LIGHT_PURPLE),
                Text.literal("Right-click the book...").formatted(Formatting.GRAY, Formatting.ITALIC));
    }

    /** Called by the tutorial book when its absorb animation finishes: advance the intro, prompt J. */
    public static void completeBookIntro(ServerPlayerEntity player) {
        ManaData data = ManaPlayer.get(player);
        if (data.getIntroStage() > 0) {
            return;
        }
        data.setIntroStage(1);
        ModNetworking.syncToClient(player);
        sendCinematic(player, Text.literal("The book just disappeared...").formatted(Formatting.LIGHT_PURPLE),
                Text.literal("(press J to open it)").formatted(Formatting.AQUA));
    }

    /** First time the spellbook is closed after the intro: the "pages are missing" beat. */
    private void onBookClosed(ServerPlayerEntity player) {
        ManaData data = ManaPlayer.get(player);
        if (data.getIntroStage() == 1) {
            data.setIntroStage(2);
            ModNetworking.syncToClient(player);
            sendCinematic(player,
                    Text.literal("Pages in the book seem to be missing...").formatted(Formatting.LIGHT_PURPLE),
                    Text.literal("They will fill as your power grows.").formatted(Formatting.GRAY, Formatting.ITALIC));
        }
    }

    /** Intro beats land in the in-game chat box (v2.3.0 — previously full-screen titles). */
    private static void sendCinematic(ServerPlayerEntity player, Text title, Text subtitle) {
        player.sendMessage(title, false);
        if (subtitle != null) {
            player.sendMessage(subtitle, false);
        }
    }

    // ------------------------------------------------------------------------
    // Server tick: channelling, lingering fields, anti-magic immunity
    // ------------------------------------------------------------------------

    private void onServerTick(MinecraftServer server) {
        AreaEffectManager.tickAll();
        MagicArrayManager.tickAll();
        com.example.circlemage.boss.BossZoneManager.tickAll();

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ManaData data = ManaPlayer.get(player);
            long now = player.getWorld().getTime();

            // Safety net: the circle-1 basics are always known at circle 1+ (covers pre-2.1 saves
            // and debug/command-set circles).
            if (data.getCircle() >= 1 && !data.isSpellUnlocked(Spell.MAGIC_MISSILE.id)) {
                data.unlockSpell(Spell.MAGIC_MISSILE.id);
                data.unlockSpell(Spell.DASH.id);
            }

            // Natural mana flow (v2.1): +1/s per circle, +5/s per attuned Mana Crystal — no
            // channelling required. Circle ascension can trigger from it like any other gain.
            double naturalPerSecond = data.naturalRegenPerSecond();
            if (naturalPerSecond > 0.0) {
                data.addMana(naturalPerSecond / 20.0);
                tryAdvanceCircle(player, data);
                if (!data.isChanneling()) { // channelling below syncs every tick already
                    ModNetworking.syncToClient(player);
                }
            }

            // v2.2: held-wand cosmetics (end rod / blaze rod particles, glow-crystal torchlight).
            tickWandCosmetics(player, now);

            // v2.3: mage-tower discovery — fill chests & post the guardian the first time a
            // player sets foot inside one (throttled; staggered by entity id).
            if ((now + player.getId()) % 60 == 0) {
                com.example.circlemage.world.MageTowers.playerTick(player);
            }

            // While immune, continuously cleanse negative effects and fire.
            if (data.isImmune(now)) {
                cleanseNegativeEffects(player);
                player.setFireTicks(0);
            }

            if (data.isCarving()) {
                tickCarving(player, data, now);
            }

            // Persistent / channelled combination magic.
            tickThunderMarkVisual(player, data, now);
            tickBlaze(player, data, now);
            tickDivineWrath(player, data, now);

            // Laws: passive unlock chance + the selected law's ongoing effect.
            tickLawsUnlock(player, data, now);
            tickActiveLaw(player, data, now);

            // Wind Blade volley: fire the queued blades a couple of ticks apart.
            if (data.getWindBladeShots() > 0 && now >= data.getWindBladeNextTick()) {
                firePlayerWindBlade(player, data);
                data.advanceWindBladeVolley(now + WIND_BLADE_GAP);
            }

            // Fire a pending cast-time spell when its timer elapses.
            if (data.getPendingSpellId() >= 0 && now >= data.getPendingFireTick()) {
                Spell pending = Spell.byId(data.getPendingSpellId());
                boolean enhanced = data.isPendingEnhanced();
                data.clearPendingCast();
                if (pending != null) {
                    performSpell(player, data, pending, enhanced, false);
                    ModNetworking.syncToClient(player);
                }
            }

            // Passive chant upkeep (ATTACK / REINFORCE drain 200 mana/sec).
            tickChantUpkeep(player, data);

            // Vitality chant: maintain Regeneration III (100 mana/sec upkeep; no longer any bonus hearts).
            // Only (re)apply when the effect is missing or about to expire. Re-adding a fresh
            // instance every tick kept resetting the effect's internal duration counter, so it
            // never reached a "heal tick" (Regeneration heals when duration % (50 >> amplifier)
            // == 0) and the player never actually regenerated. Letting it count down naturally
            // lets the heal fire as intended.
            if (data.isPassiveChantUnlocked()
                    && PassiveChant.byId(data.getSelectedChant()) == PassiveChant.VITALITY) {
                StatusEffectInstance regen = player.getStatusEffect(StatusEffects.REGENERATION);
                if (regen == null || regen.getDuration() <= 1 || regen.getAmplifier() < 2) {
                    player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 2, false, false, true));
                }
            }

            if (data.isChanneling()) {
                // Mana infusion: while channelling and holding an infusable item, convert it into its
                // arcane form (one per second) INSTEAD of accumulating mana.
                if (tickManaInfusion(player, data, now)) {
                    spawnChannelAura(player, now);
                } else {
                    // Base 5/sec, multiplied by realm buffs, debug multiplier, and the Channeling chant.
                    double regenChant = (data.isPassiveChantUnlocked()
                            && PassiveChant.byId(data.getSelectedChant()) == PassiveChant.CHANNELING) ? 2.5 : 1.0;
                    // From the 1st circle onward, mana flows 3x faster.
                    double circleGain = data.getCircle() >= 1 ? 3.0 : 1.0;
                    double gainPerTick = (ManaData.BASE_GAIN_PER_SECOND / 20.0)
                            * data.manaMultiplier() * data.getDebugManaMultiplier() * regenChant * circleGain;
                    data.addMana(gainPerTick);
                    tryAdvanceCircle(player, data);
                    ModNetworking.syncToClient(player);
                    spawnChannelAura(player, now);
                }
            } else {
                data.resetInfusion();
            }
        }
    }

    /**
     * If the channelling player holds an infusable item, advance the infusion and (each second)
     * convert one item into its arcane form. Returns true if an infusion is in progress this tick
     * (so the normal mana gain is skipped).
     */
    private boolean tickManaInfusion(ServerPlayerEntity player, ManaData data, long now) {
        net.minecraft.item.ItemStack held = player.getMainHandStack();
        net.minecraft.item.Item output = com.example.circlemage.item.ManaInfusion.outputFor(held.getItem());
        if (output == null || held.isEmpty()) {
            data.resetInfusion();
            return false;
        }
        if (data.tickInfusion()) {
            held.decrement(1);
            giveOrDrop(player, new net.minecraft.item.ItemStack(output));
            if (player.getWorld() instanceof ServerWorld world) {
                world.spawnParticles(ModParticles.ARCANE, player.getX(), player.getBodyY(0.7), player.getZ(),
                        24, 0.3, 0.4, 0.3, 0.05);
                world.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.7f, 1.6f);
            }
        }
        return true;
    }

    // ------------------------------------------------------------------------
    // v2.2 wand cosmetics: held particles + glow-crystal dynamic light
    // ------------------------------------------------------------------------

    /** Where each player's glow-crystal wand light block currently sits. */
    private static final java.util.Map<java.util.UUID, net.minecraft.util.math.GlobalPos> WAND_LIGHTS =
            new java.util.HashMap<>();

    private void tickWandCosmetics(ServerPlayerEntity player, long now) {
        ItemStack wand = com.example.circlemage.item.WandItem.heldWand(player);
        com.example.circlemage.item.WandItem.WandMaterial material =
                wand == null ? null : com.example.circlemage.item.WandItem.materialOf(wand);

        // End rod: small purple motes drifting off the wand. Blaze rod: embers.
        if (material != null && now % 4 == 0 && player.getWorld() instanceof ServerWorld world) {
            var particle = switch (material) {
                case END_ROD -> ModParticles.BIND_MOTE;
                case BLAZE_ROD -> ModParticles.EMBER;
                default -> null;
            };
            if (particle != null) {
                Vec3d look = player.getRotationVec(1.0f);
                Vec3d right = look.crossProduct(new Vec3d(0, 1, 0));
                if (right.lengthSquared() < 1.0e-4) {
                    right = new Vec3d(1, 0, 0);
                }
                boolean mainHand = player.getMainHandStack() == wand;
                Vec3d hand = player.getEyePos().add(look.multiply(0.45))
                        .add(right.normalize().multiply(mainHand ? 0.3 : -0.3)).add(0, -0.25, 0);
                world.spawnParticles(particle, hand.x, hand.y, hand.z, 1, 0.03, 0.06, 0.03, 0.01);
            }
        }

        tickWandLight(player, wand);
    }

    /** Glow-crystal core: keep an invisible light block (torch level 14) at the carrier's head. */
    private void tickWandLight(ServerPlayerEntity player, ItemStack wand) {
        boolean glowing = wand != null
                && com.example.circlemage.item.WandItem.coreOf(wand)
                == com.example.circlemage.item.WandItem.WandCore.GLOW_CRYSTAL;
        java.util.UUID id = player.getUuid();
        if (!glowing) {
            removeWandLight(player.getServer(), id);
            return;
        }
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return;
        }
        net.minecraft.util.math.BlockPos eye = net.minecraft.util.math.BlockPos.ofFloored(player.getEyePos());
        net.minecraft.util.math.GlobalPos prev = WAND_LIGHTS.get(id);
        if (prev != null && prev.getDimension() == world.getRegistryKey() && prev.getPos().equals(eye)) {
            return; // still lit from here
        }
        removeWandLight(player.getServer(), id);
        if (world.getBlockState(eye).isAir()) {
            world.setBlockState(eye, net.minecraft.block.Blocks.LIGHT.getDefaultState()
                    .with(net.minecraft.block.LightBlock.LEVEL_15, 14));
            WAND_LIGHTS.put(id, net.minecraft.util.math.GlobalPos.create(world.getRegistryKey(), eye));
        }
    }

    /** Clears a player's wand light (if it still exists and is still a light block). */
    private static void removeWandLight(MinecraftServer server, java.util.UUID id) {
        net.minecraft.util.math.GlobalPos prev = WAND_LIGHTS.remove(id);
        if (prev == null || server == null) {
            return;
        }
        ServerWorld world = server.getWorld(prev.getDimension());
        if (world != null && world.getBlockState(prev.getPos()).isOf(net.minecraft.block.Blocks.LIGHT)) {
            world.setBlockState(prev.getPos(), net.minecraft.block.Blocks.AIR.getDefaultState());
        }
    }

    /** A rising spiral of arcane motes that gathers around a channelling mage. */
    private void spawnChannelAura(ServerPlayerEntity player, long now) {
        if (!(player.getWorld() instanceof ServerWorld world) || now % 3 != 0) {
            return;
        }
        double radius = 0.55;
        double baseAngle = (now % 40) * (Math.PI * 2.0 / 40.0);
        for (int i = 0; i < 2; i++) {
            double angle = baseAngle + i * Math.PI;
            double px = player.getX() + Math.cos(angle) * radius;
            double pz = player.getZ() + Math.sin(angle) * radius;
            double py = player.getY() + (now % 20) / 20.0 * 1.9;
            world.spawnParticles(ModParticles.ARCANE, px, py, pz, 1, 0.0, 0.04, 0.0, 0.01);
        }
    }

    private void tickChantUpkeep(ServerPlayerEntity player, ManaData data) {
        if (!data.isPassiveChantUnlocked()) {
            return;
        }
        PassiveChant chant = PassiveChant.byId(data.getSelectedChant());
        double upkeep;
        switch (chant) {
            case ATTACK, REINFORCE -> upkeep = CHANT_UPKEEP_PER_TICK; // 200/sec
            case VITALITY -> upkeep = VITALITY_UPKEEP_PER_TICK;       // 100/sec
            default -> {
                return; // NONE / CHANNELING: no upkeep
            }
        }
        if (!data.trySpendMana(upkeep)) {
            // Out of mana: deactivate the chant.
            setChant(player, data, PassiveChant.NONE);
            player.sendMessage(Text.literal("Your " + chant.displayName + " chant fizzled out — not enough mana.")
                    .formatted(Formatting.RED), true);
        } else {
            ModNetworking.syncToClient(player);
        }
    }

    private void cleanseNegativeEffects(ServerPlayerEntity player) {
        for (StatusEffectInstance instance : new ArrayList<>(player.getStatusEffects())) {
            if (instance.getEffectType().getCategory() == StatusEffectCategory.HARMFUL) {
                player.removeStatusEffect(instance.getEffectType());
            }
        }
    }

    // ------------------------------------------------------------------------
    // Channelling
    // ------------------------------------------------------------------------

    /** Toggles mana channelling (also used by the Arcane Focus item's right-click). */
    public static void toggleChanneling(ServerPlayerEntity player) {
        ManaData data = ManaPlayer.get(player);
        boolean nowChanneling = !data.isChanneling();
        data.setChanneling(nowChanneling);
        applyChannelLock(player, nowChanneling);
        ModNetworking.syncToClient(player);
    }

    private static void applyChannelLock(ServerPlayerEntity player, boolean locked) {
        EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        if (locked) {
            if (!speed.hasModifier(CHANNEL_SLOW_MODIFIER)) {
                speed.addTemporaryModifier(CHANNEL_SLOW_MODIFIER);
            }
        } else {
            speed.removeModifier(CHANNEL_SLOW_UUID);
        }
    }

    private void tryAdvanceCircle(ServerPlayerEntity player, ManaData data) {
        while (data.getCircle() < Circles.MAX_CIRCLE) {
            int target = data.getCircle() + 1;
            long required = Circles.THRESHOLDS[target];
            if (data.getCurrentMana() < required) {
                return;
            }
            // Some circles are gated behind an advancement in addition to their mana cost:
            // circle 4 -> Basic Anti Magic, circle 6 -> Combination Magic, circle 9 -> Laws.
            Identifier gate = advancementGate(target);
            if (gate != null && !hasAdvancement(player, gate)) {
                data.setCurrentMana(required); // cap progress at the locked gate
                return;
            }
            data.setCircle(target);
            data.setCurrentMana(0.0);
            player.sendMessage(Text.literal("You have ascended to the " + target + " circle of magehood!")
                    .formatted(Formatting.LIGHT_PURPLE), false);
            // The 1st circle awakens the two innate techniques; all other magic hides in tomes.
            if (target == 1) {
                data.unlockSpell(Spell.MAGIC_MISSILE.id);
                data.unlockSpell(Spell.DASH.id);
                player.sendMessage(Text.literal("Magic awakens within you: Magic Missile and Dash unlocked! "
                        + "Seek magic tomes to learn more.").formatted(Formatting.GOLD), false);
            }
        }
        // At the maximum circle, hold mana at the final threshold.
        if (data.getCircle() >= Circles.MAX_CIRCLE) {
            long cap = Circles.THRESHOLDS[Circles.MAX_CIRCLE];
            if (data.getCurrentMana() > cap) {
                data.setCurrentMana(cap);
            }
        }
    }

    /** The advancement required to ascend to {@code targetCircle}, or null if there is no gate. */
    private Identifier advancementGate(int targetCircle) {
        return switch (targetCircle) {
            case 4 -> ANTI_MAGIC_ADVANCEMENT;
            case 6 -> COMBINATION_ADVANCEMENT;
            case 9 -> LAWS_ADVANCEMENT;
            default -> null;
        };
    }

    // ------------------------------------------------------------------------
    // Casting
    // ------------------------------------------------------------------------

    /** Maps a base spell to its active transform if the player has it (5th: Spear, 6th: Dark Orb). */
    private Spell resolveForm(Spell base, ManaData data) {
        if (base == Spell.MAGIC_MISSILE && data.getCircle() >= 5 && !data.isRevertSpear()) {
            return Spell.SPEAR_OF_LIGHT;
        }
        if (base == Spell.AREA_SLOW && data.getCircle() >= 6 && !data.isRevertDarkOrb()) {
            return Spell.DARK_ORB;
        }
        return base;
    }

    /**
     * @param primed true if this came from a held active chant (the hold was the cast time)
     * @param charge how long the spell was primed, in ticks (PRIME_MAX_TICKS = fully charged)
     */
    private void handleCast(ServerPlayerEntity player, int spellId, boolean primed, int charge) {
        Spell base = Spell.byId(spellId);
        if (base == null) {
            return;
        }
        // Boss Zone magic restriction: certain spells are suppressed inside an active domain.
        if (isSpellSuppressed(player, spellId)) {
            return;
        }
        // The Master punishes the first Self Barrier raised inside his domain.
        if (base == Spell.BARRIER) {
            com.example.circlemage.entity.MasterEntity.tryBarrierPunish(player);
        }

        ManaData data = ManaPlayer.get(player);
        long now = player.getWorld().getTime();
        Spell spell = resolveForm(base, data);

        // Anti-magic must be learned before it can be cast.
        if (spell == Spell.CLEAR_MAGIC && !hasAdvancement(player, ANTI_MAGIC_ADVANCEMENT)) {
            player.sendMessage(Text.literal("You have not yet learned Basic Anti Magic.")
                    .formatted(Formatting.RED), true);
            return;
        }
        // v2.1: every other spell must be known (circle-1 basics or learned from a magic tome).
        if (spell != Spell.CLEAR_MAGIC && !isSpellKnown(data, base.id)) {
            player.sendMessage(spellLockedMessage(base), true);
            return;
        }

        // Don't start a new cast while one is already charging.
        if (data.getPendingSpellId() >= 0) {
            return;
        }

        // Barrier uses sneaking for dome-vs-wall at any circle. Every other (chantable) spell is
        // "enhanced" when cast from a held active chant (primed); holding to the cap fully charges it.
        boolean chantable = spell != Spell.CLEAR_MAGIC && spell != Spell.BARRIER
                && spell != Spell.DASH && spell != Spell.WIND_BLADE && spell != Spell.FIRE_BOLT;
        boolean enhanced;
        if (spell == Spell.BARRIER) {
            enhanced = player.isSneaking();
        } else {
            enhanced = primed && chantable && data.getCircle() >= 5;
        }
        boolean fullyCharged = enhanced && chantable && charge >= PRIME_MAX_TICKS;

        // Cooldown gate (skippable via the cooldowns debug toggle). 5th circle: 0.85x cooldown;
        // a held wand's core (v2.2) can shorten it further for its school.
        int effectiveCooldown = (int) Math.round(spell.cooldownTicks * data.circleDiscount()
                * com.example.circlemage.item.WandItem.cooldownMultiplier(player, spell.id));
        long cooldownEnd = data.getCooldownEnd(spell.id);
        if (data.isCooldownsEnabled() && now < cooldownEnd) {
            double secondsLeft = (cooldownEnd - now) / 20.0;
            player.sendMessage(Text.literal(String.format("%s is on cooldown (%.1fs)", spellName(spell), secondsLeft))
                    .formatted(Formatting.RED), true);
            return;
        }

        // Mana gate. 5th circle: 0.85x cost. Channeling chant: free. Focus/wand (v2/v2.2) discount.
        boolean channelingChant = data.isPassiveChantUnlocked()
                && PassiveChant.byId(data.getSelectedChant()) == PassiveChant.CHANNELING;
        double focusDiscount = holdingFocus(player) ? com.example.circlemage.item.ArcaneFocusItem.COST_MULT : 1.0;
        int effectiveCost = channelingChant ? 0 : (int) Math.round(spell.manaCost * data.circleDiscount()
                * focusDiscount * com.example.circlemage.item.WandItem.costMultiplier(player, spell.id));
        if (!data.trySpendMana(effectiveCost)) {
            player.sendMessage(Text.literal("Not enough mana — " + effectiveCost + " required.")
                    .formatted(Formatting.RED), true);
            return;
        }

        data.setCooldownEnd(spell.id, now + effectiveCooldown);
        doMagicUseRolls(player, data);

        if (primed && chantable) {
            // The player already held to charge, so the spell fires immediately.
            if (enhanced) {
                player.sendMessage(getChantText(spell).formatted(Formatting.DARK_PURPLE, Formatting.ITALIC), false);
                spawnCastRune(player, spell);
            }
            performSpell(player, data, spell, enhanced, fullyCharged);
        } else {
            // Normal tap: keep the base cast-time behaviour (no enhancement except a barrier dome).
            // v2.2: an end-rod/blaze-rod wand casts its school 20% faster.
            int castTime = (int) Math.round(spell.castTimeTicks
                    * com.example.circlemage.item.WandItem.castTimeMultiplier(player, spell.id));
            if (castTime <= 0) {
                performSpell(player, data, spell, enhanced, false);
            } else {
                data.setPendingCast(spell.id, now + castTime, enhanced);
                player.sendMessage(Text.literal("Casting " + spellName(spell) + "...").formatted(Formatting.AQUA), true);
            }
        }
        ModNetworking.syncToClient(player);
    }

    private void performSpell(ServerPlayerEntity player, ManaData data, Spell spell, boolean enhanced, boolean fullyCharged) {
        long now = player.getWorld().getTime();
        switch (spell) {
            case MAGIC_MISSILE -> castMagicMissile(player, data, enhanced);
            case SPEAR_OF_LIGHT -> castSpearOfLight(player, data, enhanced, fullyCharged);
            case FIREBALL -> castFireball(player, data, enhanced);
            case AREA_SLOW -> castAreaSlow(player, enhanced);
            case DARK_ORB -> castDarkOrb(player, data, enhanced, fullyCharged);
            case CLEAR_MAGIC -> castClearMagic(player, data, now);
            case WALL_OF_WIND -> castWallOfWind(player, enhanced, fullyCharged);
            case THUNDER -> castThunder(player, enhanced, fullyCharged);
            case BARRIER -> castBarrier(player, enhanced);
            case DASH -> castDash(player, 1.0f, 0.0f); // fallback (forward); the real dash uses the DASH packet
            case WIND_BLADE -> castWindBlade(player, data);
            case FIRE_BOLT -> castFireBolt(player, data);
        }
    }

    /** True if the spell is known: in the learned bitmask, or an innate circle-1 basic. */
    private boolean isSpellKnown(ManaData data, int spellId) {
        if (data.isSpellUnlocked(spellId)) {
            return true;
        }
        return (spellId == Spell.MAGIC_MISSILE.id || spellId == Spell.DASH.id) && data.getCircle() >= 1;
    }

    private Text spellLockedMessage(Spell base) {
        String hint = (base == Spell.MAGIC_MISSILE || base == Spell.DASH)
                ? "Reach the 1st circle to awaken it."
                : "Its secret sleeps in a magic tome.";
        return Text.literal("You have not learned " + spellName(base) + ". " + hint).formatted(Formatting.RED);
    }

    // --- Fireball (v2.1, Fire Tome) -------------------------------------------

    /** A blazing bolt: 2 hearts of magic damage and ignites whatever it strikes. */
    private void castFireBolt(ServerPlayerEntity player, ManaData data) {
        World world = player.getWorld();
        Vec3d look = player.getRotationVec(1.0f);

        float damage = ManaData.BASE_DAMAGE * data.damageMultiplier()
                * attackChantMultiplier(player, data, Spell.FIRE_BOLT.id);
        MagicMissileEntity bolt = new MagicMissileEntity(world, player, damage);
        bolt.setFireBolt(true);
        bolt.launch(look);
        world.spawnEntity(bolt);

        if (world instanceof ServerWorld serverWorld) {
            Vec3d muzzle = player.getEyePos().add(look.multiply(0.6)).add(0, -0.1, 0);
            serverWorld.spawnParticles(ModParticles.EMBER, muzzle.x, muzzle.y, muzzle.z, 10, 0.12, 0.12, 0.12, 0.03);
        }
        playSpellSound(player, SoundEvents.ITEM_FIRECHARGE_USE, 0.8f, 1.3f);
    }

    // --- Wind Blade ----------------------------------------------------------

    /** Number of blades in a single Wind Blade volley, and the gap (ticks) between them. */
    private static final int WIND_BLADE_VOLLEY = 5;
    private static final int WIND_BLADE_GAP = 2; // ~100ms between blades

    /** Begins a Wind Blade volley; the blades are fired over the next few ticks in {@link #onServerTick}. */
    private void castWindBlade(ServerPlayerEntity player, ManaData data) {
        long now = player.getWorld().getTime();
        data.startWindBladeVolley(WIND_BLADE_VOLLEY, now);
        playSpellSound(player, SoundEvents.ENTITY_PHANTOM_FLAP, 0.9f, 0.8f);
    }

    /** Fires one Wind Blade toward the player's current look — 1/3 of a Magic Missile's damage. */
    private void firePlayerWindBlade(ServerPlayerEntity player, ManaData data) {
        World world = player.getWorld();
        Vec3d look = player.getRotationVec(1.0f);
        float missileDamage = ManaData.BASE_DAMAGE * data.damageMultiplier()
                * attackChantMultiplier(player, data, Spell.WIND_BLADE.id);
        com.example.circlemage.entity.WindBladeEntity blade =
                new com.example.circlemage.entity.WindBladeEntity(world, player, missileDamage / 3.0f);
        blade.launch(look);
        world.spawnEntity(blade);
        if (world instanceof ServerWorld serverWorld) {
            Vec3d muzzle = player.getEyePos().add(look.multiply(0.6)).add(0, -0.1, 0);
            serverWorld.spawnParticles(ModParticles.GALE, muzzle.x, muzzle.y, muzzle.z, 10, 0.12, 0.12, 0.12, 0.05);
        }
        playSpellSound(player, SoundEvents.ENTITY_PHANTOM_FLAP, 0.6f, 1.5f);
    }

    // --- Dash ----------------------------------------------------------------

    /** Gate Dash on its own cost/cooldown, then dash toward the held movement input. */
    private void handleDash(ServerPlayerEntity player, float forward, float strafe) {
        if (isSpellSuppressed(player, Spell.DASH.id)) {
            return;
        }
        ManaData data = ManaPlayer.get(player);
        if (!isSpellKnown(data, Spell.DASH.id)) {
            player.sendMessage(spellLockedMessage(Spell.DASH), true);
            return;
        }
        long now = player.getWorld().getTime();
        Spell spell = Spell.DASH;

        int effectiveCooldown = (int) Math.round(spell.cooldownTicks * data.circleDiscount()
                * com.example.circlemage.item.WandItem.cooldownMultiplier(player, spell.id));
        long cooldownEnd = data.getCooldownEnd(spell.id);
        if (data.isCooldownsEnabled() && now < cooldownEnd) {
            double secondsLeft = (cooldownEnd - now) / 20.0;
            player.sendMessage(Text.literal(String.format("Dash is on cooldown (%.1fs)", secondsLeft))
                    .formatted(Formatting.RED), true);
            return;
        }
        boolean channelingChant = data.isPassiveChantUnlocked()
                && PassiveChant.byId(data.getSelectedChant()) == PassiveChant.CHANNELING;
        double focusDiscount = holdingFocus(player) ? com.example.circlemage.item.ArcaneFocusItem.COST_MULT : 1.0;
        int effectiveCost = channelingChant ? 0 : (int) Math.round(spell.manaCost * data.circleDiscount()
                * focusDiscount * com.example.circlemage.item.WandItem.costMultiplier(player, spell.id));
        if (!data.trySpendMana(effectiveCost)) {
            player.sendMessage(Text.literal("Not enough mana — " + effectiveCost + " required.")
                    .formatted(Formatting.RED), true);
            return;
        }
        data.setCooldownEnd(spell.id, now + effectiveCooldown);
        doMagicUseRolls(player, data);
        castDash(player, forward, strafe);
        ModNetworking.syncToClient(player);
    }

    /**
     * A quick horizontal burst toward the player's movement input (relative to facing), with a small
     * hop and no fall damage. {@code forward}/{@code strafe} are the client's movement-input values
     * (forward +1 = W, strafe +1 = A/left); with no key held the dash goes forward.
     */
    private void castDash(ServerPlayerEntity player, float forward, float strafe) {
        // Use Minecraft's own movement-input -> world-velocity transform so the dash goes exactly where
        // the keys move the player: world.x = sideways*cos - forward*sin, world.z = forward*cos + sideways*sin.
        double yawRad = Math.toRadians(player.getYaw());
        double sin = Math.sin(yawRad);
        double cos = Math.cos(yawRad);
        double dx = strafe * cos - forward * sin;
        double dz = forward * cos + strafe * sin;
        Vec3d dir = new Vec3d(dx, 0.0, dz);
        if (dir.lengthSquared() < 1.0e-4) {
            dir = new Vec3d(-sin, 0.0, cos); // no movement key held -> dash forward
        }
        dir = dir.normalize();

        double power = 0.95; // dash strength reduced 50% (was 1.9; 1.0.8.1)
        player.setVelocity(dir.x * power, 0.32, dir.z * power);
        player.velocityModified = true;
        player.fallDistance = 0.0f;

        if (player.getWorld() instanceof ServerWorld world) {
            Vec3d p = player.getPos();
            for (int i = 0; i < 14; i++) {
                world.spawnParticles(ModParticles.GALE, p.x, p.y + 0.9, p.z, 1,
                        -dir.x * 0.4, 0.05, -dir.z * 0.4, 0.4);
            }
            world.spawnParticles(ModParticles.ARCANE_TRAIL, p.x, p.y + 0.9, p.z, 8, 0.2, 0.4, 0.2, 0.05);
        }
        playSpellSound(player, SoundEvents.ENTITY_PHANTOM_FLAP, 0.8f, 1.6f);
    }

    // ------------------------------------------------------------------------
    // Combination magic
    // ------------------------------------------------------------------------

    /** True (and notifies the player) if a Boss Zone restriction suppresses this Spell id for the player. */
    private boolean isSpellSuppressed(ServerPlayerEntity player, int spellId) {
        if (com.example.circlemage.boss.BossZoneManager.isSpellRestricted(player, spellId)) {
            player.sendMessage(Text.literal("The domain suppresses certain forms of magic.")
                    .formatted(Formatting.LIGHT_PURPLE), true);
            return true;
        }
        return false;
    }

    private void handleCombine(ServerPlayerEntity player, int primedId, int charge, int secondId) {
        if (isSpellSuppressed(player, primedId) || isSpellSuppressed(player, secondId)) {
            return;
        }
        ManaData data = ManaPlayer.get(player);
        if (data.getCircle() < 5) {
            return;
        }
        if (!hasAdvancement(player, COMBINATION_ADVANCEMENT)) {
            player.sendMessage(Text.literal("You have not yet learned Combination Magic.")
                    .formatted(Formatting.RED), true);
            return;
        }
        Spell primed = Spell.byId(primedId);
        Spell second = Spell.byId(secondId);
        if (primed == null || second == null) {
            return;
        }
        if (!isSpellKnown(data, primedId) || !isSpellKnown(data, secondId)) {
            player.sendMessage(Text.literal("You cannot combine magic you have not learned.")
                    .formatted(Formatting.RED), true);
            return;
        }
        primed = resolveForm(primed, data);
        second = resolveForm(second, data);
        boolean primedFull = charge >= PRIME_MAX_TICKS;

        CombinationSpell combo = Combinations.find(primed, primedFull, second);
        if (combo == null) {
            player.sendMessage(Text.literal("Those magics don't combine. (" + spellName(primed) + " + " + spellName(second) + ")")
                    .formatted(Formatting.GRAY), true);
            return;
        }

        long now = player.getWorld().getTime();
        if (data.isCooldownsEnabled() && now < data.getComboCooldownEnd(combo.id)) {
            double secondsLeft = (data.getComboCooldownEnd(combo.id) - now) / 20.0;
            player.sendMessage(Text.literal(String.format("%s is on cooldown (%.1fs)", combo.name, secondsLeft))
                    .formatted(Formatting.RED), true);
            return;
        }

        String requirementError = combo.requirementError(player, data);
        if (requirementError != null) {
            player.sendMessage(Text.literal(requirementError).formatted(Formatting.RED), true);
            return;
        }

        boolean channelingChant = data.isPassiveChantUnlocked()
                && PassiveChant.byId(data.getSelectedChant()) == PassiveChant.CHANNELING;
        int cost = channelingChant ? 0 : combo.manaCost;
        // Law of Space (Space Manipulation): teleport magics (Thunder Warp) are free.
        if (combo == Combinations.THUNDER_WARP && canUseLaws(data)
                && Law.byId(data.getSelectedLaw()) == Law.SPACE_MANIPULATION) {
            cost = 0;
        }
        if (data.getCurrentMana() < cost) {
            player.sendMessage(Text.literal("Not enough mana — " + cost + " required for " + combo.name + ".")
                    .formatted(Formatting.RED), true);
            return;
        }

        ServerWorld world = (ServerWorld) player.getWorld();
        if (!combo.cast(world, player, data)) {
            return; // the combo declined to fire (e.g. lost its target) — don't charge mana
        }
        data.trySpendMana(cost);
        data.setComboCooldownEnd(combo.id, now + combo.cooldownTicks);
        doMagicUseRolls(player, data);
        player.sendMessage(Text.literal("Combination Magic: " + combo.name + "!")
                .formatted(Formatting.DARK_PURPLE, Formatting.BOLD), false);
        ModNetworking.syncToClient(player);
    }

    /** Draws the player's Thunder Mark (a sparking rune) while they're near it. */
    private void tickThunderMarkVisual(ServerPlayerEntity player, ManaData data, long now) {
        if (!data.hasThunderMark() || now % 8 != 0) {
            return;
        }
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return;
        }
        String dim = world.getRegistryKey().getValue().toString();
        if (!dim.equals(data.getMarkDim())) {
            return;
        }
        double mx = data.getMarkX();
        double my = data.getMarkY();
        double mz = data.getMarkZ();
        if (player.squaredDistanceTo(mx, my, mz) > 64.0 * 64.0) {
            return;
        }
        int pts = 12;
        double r = 1.2;
        for (int i = 0; i < pts; i++) {
            double a = (Math.PI * 2.0 * i) / pts + now * 0.04;
            world.spawnParticles(ModParticles.SPARK, mx + Math.cos(a) * r, my + 0.1, mz + Math.sin(a) * r, 1, 0, 0, 0, 0.0);
        }
        // Inner cross + occasional spark burst so it reads as a rune.
        world.spawnParticles(ModParticles.SPARK, mx, my + 0.1, mz, 1, 0.4, 0.0, 0.4, 0.0);
        if (now % 32 == 0) {
            world.spawnParticles(ModParticles.SPARK, mx, my + 0.3, mz, 8, 0.2, 0.5, 0.2, 0.25);
        }
    }

    /** Blaze flamethrower upkeep: drains mana, streams flame, burns entities in the cone. */
    private void tickBlaze(ServerPlayerEntity player, ManaData data, long now) {
        if (now >= data.getBlazeEndTick()) {
            return;
        }
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return;
        }
        if (!data.trySpendMana(500.0 / 20.0)) { // 500 mana/sec, stops early if the mana runs out
            data.setBlazeEndTick(0);
            ModNetworking.syncToClient(player);
            return;
        }
        ModNetworking.syncToClient(player);

        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVec(1.0f);
        double range = 6.0;
        for (double d = 0.6; d <= range; d += 0.5) {
            Vec3d p = eye.add(look.multiply(d));
            world.spawnParticles(ModParticles.EMBER, p.x, p.y, p.z, 2, 0.15, 0.15, 0.15, 0.03);
            world.spawnParticles(ModParticles.SMOKE, p.x, p.y, p.z, 1, 0.1, 0.1, 0.1, 0.01);
        }
        Box box = player.getBoundingBox().expand(range);
        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, box, x -> x != player && x.isAlive())) {
            Vec3d to = e.getBoundingBox().getCenter().subtract(eye);
            double forward = to.dotProduct(look);
            if (forward <= 0 || forward > range) {
                continue;
            }
            if (to.subtract(look.multiply(forward)).length() > 1.6) {
                continue;
            }
            e.setOnFireFor(4);
            if (now % 4 == 0) {
                e.damage(player.getDamageSources().magic(), 3.0f);
            }
        }
        if (now % 4 == 0) {
            world.playSound(null, eye.x, eye.y, eye.z, SoundEvents.ENTITY_BLAZE_AMBIENT, SoundCategory.PLAYERS, 0.5f, 1.3f);
        }
    }

    /** Divine Wrath: a tracking pillar of light that hammers the locked target for ~3 seconds. */
    private void tickDivineWrath(ServerPlayerEntity player, ManaData data, long now) {
        if (now >= data.getDivineWrathEndTick()) {
            return;
        }
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return;
        }
        Entity ent = world.getEntityById(data.getDivineWrathTargetId());
        if (!(ent instanceof LivingEntity target) || !target.isAlive()) {
            data.setDivineWrath(-1, 0);
            return;
        }
        Vec3d tp = target.getPos();
        double top = tp.y + 18.0;
        for (double y = tp.y; y <= top; y += 0.4) {
            world.spawnParticles(ModParticles.LIGHT, tp.x, y, tp.z, 1, 0.12, 0.0, 0.12, 0.0);
            if (((int) (y * 2)) % 2 == 0) {
                world.spawnParticles(ModParticles.RADIANCE, tp.x, y, tp.z, 1, 0.18, 0.0, 0.18, 0.0);
            }
        }
        if (now % 4 == 0) {
            for (double ring = 1.0; ring <= 3.0; ring += 1.0) {
                int pts = (int) (ring * 8);
                for (int i = 0; i < pts; i++) {
                    double a = (Math.PI * 2.0 * i) / pts;
                    world.spawnParticles(ModParticles.RADIANCE, tp.x + Math.cos(a) * ring, tp.y + 0.2, tp.z + Math.sin(a) * ring,
                            1, 0, 0, 0, 0.0);
                }
            }
        }
        if (now % 10 == 0) {
            target.damage(player.getDamageSources().indirectMagic(player, player), 3.0f);
        }
        if (now % 20 == 0) {
            world.playSound(null, tp.x, tp.y, tp.z, SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.PLAYERS, 1.0f, 1.6f);
            world.playSound(null, tp.x, tp.y, tp.z, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 0.5f, 0.7f);
        }
    }

    // ------------------------------------------------------------------------
    // Laws (9th-circle ultimate system)
    // ------------------------------------------------------------------------

    /** True if the player may use the Laws (unlocked + 9th circle). */
    private boolean canUseLaws(ManaData data) {
        return data.isLawsUnlocked() && data.getCircle() >= Circles.MAX_CIRCLE;
    }

    private void tickLawsUnlock(ServerPlayerEntity player, ManaData data, long now) {
        if (data.isLawsUnlocked() || now % 20 != 0) {
            return;
        }
        if (player.getWorld().random.nextDouble() < LAWS_UNLOCK_CHANCE) {
            LawUnlocks.unlockBase(player, data);
        }
    }

    private void selectLaw(ServerPlayerEntity player, int lawId) {
        ManaData data = ManaPlayer.get(player);
        Law law = Law.byId(lawId);
        if (!data.isLawsUnlocked() || !data.isLawUnlocked(law.ordinal())) {
            player.sendMessage(Text.literal("That Law is not unlocked.").formatted(Formatting.RED), true);
            return;
        }
        data.setSelectedLaw(law.ordinal());
        player.sendMessage(Text.literal("Active Law: " + law.displayName).formatted(Formatting.LIGHT_PURPLE), true);
        ModNetworking.syncToClient(player);
    }

    /** The selected law's ongoing per-tick effect, plus the global time-stop upkeep. */
    private void tickActiveLaw(ServerPlayerEntity player, ManaData data, long now) {
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return;
        }
        tickTimeStop(world, player, data, now); // persists even if the law is switched
        if (!canUseLaws(data)) {
            return;
        }
        switch (Law.byId(data.getSelectedLaw())) {
            case GRAVITY_AMP -> tickGravityAmp(world, player, data, now);
            case BLACK_HOLE -> tickBlackHole(world, player, data, now);
            case LOVE_OF_WORLD -> tickLoveOfWorld(world, player, data, now);
            default -> {
            }
        }
    }

    private boolean drainLawPerSecond(ServerPlayerEntity player, ManaData data, long now, double perSecond) {
        if (now % 20 != 0) {
            return true;
        }
        if (!data.trySpendMana(perSecond)) {
            data.setSelectedLaw(Law.NONE.ordinal());
            player.sendMessage(Text.literal("Your Law fades — not enough mana.").formatted(Formatting.RED), true);
            ModNetworking.syncToClient(player);
            return false;
        }
        ModNetworking.syncToClient(player);
        return true;
    }

    private void tickGravityAmp(ServerWorld world, ServerPlayerEntity player, ManaData data, long now) {
        double range = 10.0;
        Vec3d c = player.getPos();
        Box box = player.getBoundingBox().expand(range);
        boolean affecting = false;
        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, box, x -> x != player && x.isAlive())) {
            double dist = e.getPos().distanceTo(c);
            if (dist > range) {
                continue;
            }
            affecting = true;
            int stacks = (int) Math.min(10, Math.max(1, Math.ceil(range - dist)));
            if (stacks >= 10) {
                e.damage(world.getDamageSources().cramming(), 1000.0f); // crushed
            } else {
                e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 10, Math.min(9, stacks), false, false, true));
                e.addVelocity(0.0, -0.08 * stacks, 0.0);
                e.velocityModified = true;
            }
        }
        if (affecting) {
            if (now % 4 == 0) {
                world.spawnParticles(ModParticles.VOID, c.x, c.y + 1.0, c.z, 6, range * 0.4, 0.6, range * 0.4, 0.02);
            }
            drainLawPerSecond(player, data, now, 2000.0);
        }
    }

    private static final int BLACK_HOLE_RANGE = 30;

    /** Black Hole: drags loose (already-broken) items in from up to 30 blocks and collects them. */
    private void tickBlackHole(ServerWorld world, ServerPlayerEntity player, ManaData data, long now) {
        if (!drainLawPerSecond(player, data, now, 250.0)) {
            return;
        }
        Vec3d center = player.getPos();
        Box box = player.getBoundingBox().expand(BLACK_HOLE_RANGE);
        for (ItemEntity item : world.getEntitiesByClass(ItemEntity.class, box, ItemEntity::isAlive)) {
            Vec3d pull = center.subtract(item.getPos());
            double dist = pull.length();
            if (dist > BLACK_HOLE_RANGE) {
                continue;
            }
            if (dist < 1.2) {
                // Close enough — vacuum straight into the inventory.
                ItemStack stack = item.getStack();
                if (!stack.isEmpty()) {
                    giveOrDrop(player, stack.copy());
                    item.discard();
                }
            } else {
                Vec3d v = pull.normalize().multiply(0.6);
                item.setVelocity(v.x, v.y + 0.05, v.z);
                item.velocityModified = true;
                item.setPickupDelay(0);
            }
        }
        if (now % 4 == 0) {
            world.spawnParticles(ModParticles.VOID, player.getX(), player.getY() + 1.0, player.getZ(), 10, 1.0, 1.0, 1.0, 0.0);
        }
    }

    private void giveOrDrop(ServerPlayerEntity player, ItemStack stack) {
        if (!player.getInventory().insertStack(stack) && !stack.isEmpty()) {
            ItemEntity item = new ItemEntity(player.getWorld(), player.getX(), player.getY(), player.getZ(), stack);
            player.getWorld().spawnEntity(item);
        }
    }

    private void tickLoveOfWorld(ServerWorld world, ServerPlayerEntity player, ManaData data, long now) {
        if (!drainLawPerSecond(player, data, now, 4000.0)) {
            return;
        }
        if (now % 30 == 0) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.WATER_BREATHING, 60, 0, false, false, true));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 400, 0, false, false, true));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 60, 0, false, false, true));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, 60, 0, false, false, true));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 60, 0, false, false, true));
        }
        // Regeneration I, applied only when missing so its heal ticks actually fire.
        StatusEffectInstance regen = player.getStatusEffect(StatusEffects.REGENERATION);
        if (regen == null || regen.getDuration() <= 1) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 0, false, false, true));
        }
        // "feather falling" is handled in LivingEntityMixin (fall damage is negated).
    }

    private void tickTimeStop(ServerWorld world, ServerPlayerEntity player, ManaData data, long now) {
        if (now < data.getTimeStopEndTick()) {
            Box box = player.getBoundingBox().expand(200.0);
            for (Entity e : world.getOtherEntities(player, box)) {
                if (e instanceof ServerPlayerEntity) {
                    continue;
                }
                e.setVelocity(Vec3d.ZERO);
                e.velocityModified = true;
                if (e instanceof MobEntity mob && !mob.isAiDisabled()) {
                    mob.setAiDisabled(true);
                    data.getFrozenMobs().add(mob.getId());
                }
            }
        } else if (!data.getFrozenMobs().isEmpty()) {
            for (int id : data.getFrozenMobs()) {
                if (world.getEntityById(id) instanceof MobEntity mob) {
                    mob.setAiDisabled(false);
                }
            }
            data.getFrozenMobs().clear();
        }
    }

    /** Triggered by the "Activate Law" key. */
    private void activateLaw(ServerPlayerEntity player) {
        ManaData data = ManaPlayer.get(player);
        if (!data.isLawsUnlocked()) {
            return;
        }
        if (data.getCircle() < Circles.MAX_CIRCLE) {
            player.sendMessage(Text.literal("Laws can only be wielded at the 9th circle.").formatted(Formatting.RED), true);
            return;
        }
        ServerWorld world = (ServerWorld) player.getWorld();
        switch (Law.byId(data.getSelectedLaw())) {
            case SPATIAL_REND -> castSpatialRend(world, player, data);
            case TIME_STOP -> castTimeStop(world, player, data);
            case TIME_REVERSAL -> castReversal(world, player, data);
            default -> player.sendMessage(Text.literal("This Law has no activated effect.").formatted(Formatting.GRAY), true);
        }
    }

    private void castSpatialRend(ServerWorld world, ServerPlayerEntity player, ManaData data) {
        if (!data.trySpendMana(10000.0)) {
            player.sendMessage(Text.literal("Not enough mana — 10000 required.").formatted(Formatting.RED), true);
            return;
        }
        Vec3d look = player.getRotationVec(1.0f);
        SpatialRendEntity rend = new SpatialRendEntity(world, player, look);
        world.spawnEntity(rend);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 2.0f, 1.4f);
        ModNetworking.syncToClient(player);
    }

    private void castTimeStop(ServerWorld world, ServerPlayerEntity player, ManaData data) {
        if (!data.trySpendMana(10000.0)) {
            player.sendMessage(Text.literal("Not enough mana — 10000 required.").formatted(Formatting.RED), true);
            return;
        }
        data.setTimeStopEndTick(world.getTime() + 100); // 5 seconds
        world.spawnParticles(ModParticles.RADIANCE, player.getX(), player.getY() + 1.0, player.getZ(), 60, 3.0, 2.0, 3.0, 0.0);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 1.4f, 0.3f);
        player.sendMessage(Text.literal("Time, be still.").formatted(Formatting.DARK_PURPLE, Formatting.ITALIC), false);
        ModNetworking.syncToClient(player);
    }

    private void castReversal(ServerWorld world, ServerPlayerEntity player, ManaData data) {
        if (!data.trySpendMana(2000.0)) {
            player.sendMessage(Text.literal("Not enough mana — 2000 required.").formatted(Formatting.RED), true);
            return;
        }
        player.setHealth(player.getMaxHealth());
        world.spawnParticles(ModParticles.RADIANCE, player.getX(), player.getBodyY(0.5), player.getZ(), 40, 0.5, 1.0, 0.5, 0.05);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.0f, 1.6f);
        ModNetworking.syncToClient(player);
    }

    private void openStorage(ServerPlayerEntity player) {
        ManaData data = ManaPlayer.get(player);
        if (!canUseLaws(data) || Law.byId(data.getSelectedLaw()) != Law.STORAGE) {
            player.sendMessage(Text.literal("Select the Storage law (at the 9th circle) first.").formatted(Formatting.RED), true);
            return;
        }
        com.example.circlemage.mana.StorageInventory inv = new com.example.circlemage.mana.StorageInventory(data);
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, playerInv, p) -> GenericContainerScreenHandler.createGeneric9x3(syncId, playerInv, inv),
                Text.literal("Spatial Storage")));
    }

    private void selectForm(ServerPlayerEntity player, int baseSpellId, boolean revert) {
        ManaData data = ManaPlayer.get(player);
        Spell base = Spell.byId(baseSpellId);
        if (base == Spell.MAGIC_MISSILE) {
            if (data.getCircle() < 5) {
                return;
            }
            data.setRevertSpear(revert);
            player.sendMessage(Text.literal("Bolt form: " + (revert ? "Magic Missile" : "Spear of Light"))
                    .formatted(Formatting.LIGHT_PURPLE), true);
        } else if (base == Spell.AREA_SLOW) {
            if (data.getCircle() < 6) {
                return;
            }
            data.setRevertDarkOrb(revert);
            player.sendMessage(Text.literal("Slow form: " + (revert ? "Area Slow" : "Dark Orb"))
                    .formatted(Formatting.LIGHT_PURPLE), true);
        } else {
            return;
        }
        ModNetworking.syncToClient(player);
    }

    /** The incantation a player "says" when casting an enhanced (active-chant) spell. */
    private net.minecraft.text.MutableText getChantText(Spell spell) {
        String line = switch (spell) {
            case MAGIC_MISSILE -> "By piercing light, strike true!";
            case SPEAR_OF_LIGHT -> "Lance of dawn, pierce the veil!";
            case FIREBALL -> "Let the world burn!";
            case AREA_SLOW -> "Time itself, be still.";
            case DARK_ORB -> "Devour all, sphere of night.";
            case THUNDER -> "Heaven's wrath, descend!";
            case WALL_OF_WIND -> "Howling gale, repel all!";
            case CLEAR_MAGIC, BARRIER, DASH, WIND_BLADE, FIRE_BOLT -> "";
        };
        return Text.literal("\"" + line + "\"");
    }

    /** Server-side display names. Note the v2.1 rename: id 1 is "Area Fire"; the new bolt is "Fireball". */
    public static String spellName(Spell spell) {
        return switch (spell) {
            case MAGIC_MISSILE -> "Magic Missile";
            case SPEAR_OF_LIGHT -> "Spear of Light";
            case FIREBALL -> "Area Fire";
            case AREA_SLOW -> "Area Slow";
            case DARK_ORB -> "Dark Orb";
            case CLEAR_MAGIC -> "Anti Magic";
            case BARRIER -> "Barrier";
            case WALL_OF_WIND -> "Wall of Wind";
            case THUNDER -> "Thunder";
            case DASH -> "Dash";
            case WIND_BLADE -> "Wind Blade";
            case FIRE_BOLT -> "Fireball";
        };
    }

    /** True if the player wields an Arcane Focus in either hand (-15% spell cost, +10% damage). */
    public static boolean holdingFocus(net.minecraft.entity.player.PlayerEntity player) {
        return player.getMainHandStack().isOf(com.example.circlemage.item.ModItems.ARCANE_FOCUS)
                || player.getOffHandStack().isOf(com.example.circlemage.item.ModItems.ARCANE_FOCUS);
    }

    /** Passive ATTACK chant multiplies spell damage by 1.30; an Arcane Focus adds another 1.10x;
     *  a held wand's core (v2.2) can add more for its school. */
    private float attackChantMultiplier(ServerPlayerEntity player, ManaData data, int spellId) {
        float mult = (data.isPassiveChantUnlocked()
                && PassiveChant.byId(data.getSelectedChant()) == PassiveChant.ATTACK) ? 1.30f : 1.0f;
        if (holdingFocus(player)) {
            mult *= com.example.circlemage.item.ArcaneFocusItem.DAMAGE_MULT;
        }
        mult *= com.example.circlemage.item.WandItem.damageMultiplier(player, spellId);
        return mult;
    }

    private void selectChant(ServerPlayerEntity player, int chantId) {
        ManaData data = ManaPlayer.get(player);
        if (!data.isPassiveChantUnlocked()) {
            return;
        }
        setChant(player, data, PassiveChant.byId(chantId));
        player.sendMessage(Text.literal("Passive chant: " + PassiveChant.byId(data.getSelectedChant()).displayName)
                .formatted(Formatting.LIGHT_PURPLE), true);
    }

    private void setChant(ServerPlayerEntity player, ManaData data, PassiveChant chant) {
        data.setSelectedChant(chant.ordinal());
        applyReinforceSpeed(player, chant == PassiveChant.REINFORCE);
        applyVitality(player, chant == PassiveChant.VITALITY);
        ModNetworking.syncToClient(player);
    }

    private void applyReinforceSpeed(ServerPlayerEntity player, boolean active) {
        EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        if (active) {
            if (!speed.hasModifier(REINFORCE_SPEED_MODIFIER)) {
                speed.addTemporaryModifier(REINFORCE_SPEED_MODIFIER);
            }
        } else {
            speed.removeModifier(REINFORCE_SPEED_UUID);
        }
    }

    private void applyVitality(ServerPlayerEntity player, boolean active) {
        // Vitality no longer grants bonus hearts (1.0.8.3). Always strip the legacy +health modifier so
        // any player who still carries it from an older version reverts to normal max health.
        EntityAttributeInstance health = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (health != null) {
            health.removeModifier(VITALITY_HEALTH_UUID);
        }
    }

    /** Rolls that fire on every successful magic use. */
    private void doMagicUseRolls(ServerPlayerEntity player, ManaData data) {
        World world = player.getWorld();

        // From the 3rd circle, a 0.5% chance to earn the Basic Anti Magic advancement.
        if (data.getCircle() >= 3 && world.random.nextDouble() < ANTI_MAGIC_CHANCE) {
            grantAdvancement(player, ANTI_MAGIC_ADVANCEMENT);
        }
        // A 5% chance to permanently unlock the current realm's buff (2x mana, 1.5x damage).
        if (data.getCircle() >= 1 && !data.isRealmBuffUnlocked(data.getCircle())
                && world.random.nextDouble() < REALM_BUFF_CHANCE) {
            if (data.unlockRealmBuff(data.getCircle())) {
                player.sendMessage(Text.literal("The power of the " + data.getCircle()
                                + " circle flows through you permanently! (+2x mana, +1.2x magic damage)")
                        .formatted(Formatting.GOLD), false);
            }
        }
        // From the 3rd circle, a 1% chance to unlock the Magic Array system.
        if (data.getCircle() >= 3 && !data.isArrayUnlocked() && world.random.nextDouble() < ARRAY_UNLOCK_CHANCE) {
            data.setArrayUnlocked(true);
            grantAdvancement(player, ARRAY_ADVANCEMENT);
            player.sendMessage(Text.literal("You have unlocked Magic Arrays! Open your inventory to choose one to carve.")
                    .formatted(Formatting.GOLD), false);
        }
        // From the 4th circle, a 5% chance to unlock Passive Chants.
        if (data.getCircle() >= 4 && !data.isPassiveChantUnlocked() && world.random.nextDouble() < CHANT_UNLOCK_CHANCE) {
            data.setPassiveChantUnlocked(true);
            player.sendMessage(Text.literal("You have unlocked Passive Chants! Open your inventory to choose one.")
                    .formatted(Formatting.GOLD), false);
        }
        // From the 5th circle, a 2% chance to learn Combination Magic (required to reach the 6th circle).
        if (data.getCircle() >= 5 && !hasAdvancement(player, COMBINATION_ADVANCEMENT)
                && world.random.nextDouble() < COMBO_ADV_CHANCE) {
            grantAdvancement(player, COMBINATION_ADVANCEMENT);
        }
    }

    // --- Magic Missile -------------------------------------------------------

    private void castMagicMissile(ServerPlayerEntity player, ManaData data, boolean enhanced) {
        World world = player.getWorld();
        Vec3d look = player.getRotationVec(1.0f);
        Vec3d cursorPos = player.raycast(50.0, 1.0f, false).getPos();

        LivingEntity target = findTargetNearCursor(world, player, cursorPos);

        // Realm buffs, ATTACK chant (1.30x) and active-chant enhancement (1.5x) all stack.
        float damage = ManaData.BASE_DAMAGE * data.damageMultiplier()
                * attackChantMultiplier(player, data, Spell.MAGIC_MISSILE.id);
        if (enhanced) {
            damage *= 1.5f;
        }
        MagicMissileEntity missile = new MagicMissileEntity(world, player, damage);
        missile.launch(look);
        if (target != null) {
            missile.setHomingTarget(target);
        }
        world.spawnEntity(missile);

        // Muzzle flash at the casting hand.
        if (world instanceof ServerWorld serverWorld) {
            Vec3d muzzle = player.getEyePos().add(look.multiply(0.6)).add(0, -0.1, 0);
            serverWorld.spawnParticles(ModParticles.ARCANE, muzzle.x, muzzle.y, muzzle.z, 8, 0.1, 0.1, 0.1, 0.02);
            serverWorld.spawnParticles(ModParticles.ARCANE_TRAIL, muzzle.x, muzzle.y, muzzle.z, 3, 0.05, 0.05, 0.05, 0.01);
        }

        playSpellSound(player, SoundEvents.ENTITY_ILLUSIONER_CAST_SPELL, 0.7f, 1.4f);
    }

    // --- Spear of Light (5th-circle Magic Missile transform) -----------------

    private void castSpearOfLight(ServerPlayerEntity player, ManaData data, boolean enhanced, boolean fullyCharged) {
        World world = player.getWorld();
        Vec3d look = player.getRotationVec(1.0f);
        Vec3d cursorPos = player.raycast(50.0, 1.0f, false).getPos();
        LivingEntity target = findTargetNearCursor(world, player, cursorPos);

        // Base spear: 1.25x missile damage, 2.5x missile speed. Active chant: another 1.25x on both.
        float damage = ManaData.BASE_DAMAGE * 1.25f * data.damageMultiplier()
                * attackChantMultiplier(player, data, Spell.SPEAR_OF_LIGHT.id);
        double speed = MagicMissileEntity.SPEED * 2.5;
        if (enhanced) {
            damage *= 1.25f;
            speed *= 1.25;
        }
        MagicMissileEntity spear = new MagicMissileEntity(world, player, damage);
        spear.setSpear(true, speed);
        spear.setEnhanced(enhanced);
        spear.setFullyCharged(fullyCharged);
        spear.launch(look);
        if (target != null) {
            spear.setHomingTarget(target);
        }
        world.spawnEntity(spear);

        if (world instanceof ServerWorld serverWorld) {
            Vec3d muzzle = player.getEyePos().add(look.multiply(0.6)).add(0, -0.1, 0);
            serverWorld.spawnParticles(ModParticles.LIGHT, muzzle.x, muzzle.y, muzzle.z, 12, 0.12, 0.12, 0.12, 0.03);
        }
        playSpellSound(player, SoundEvents.ENTITY_ILLUSIONER_CAST_SPELL, 0.9f, 1.9f);
    }

    private LivingEntity findTargetNearCursor(World world, ServerPlayerEntity player, Vec3d cursorPos) {
        Box searchBox = new Box(cursorPos.subtract(1.0, 1.0, 1.0), cursorPos.add(1.0, 1.0, 1.0));
        List<LivingEntity> candidates = world.getEntitiesByClass(LivingEntity.class, searchBox,
                e -> e != player && e.isAlive());

        LivingEntity best = null;
        double bestDist = 1.0; // must be within 1 block of the cursor
        for (LivingEntity entity : candidates) {
            double dist = entity.getBoundingBox().getCenter().distanceTo(cursorPos);
            if (dist <= bestDist) {
                bestDist = dist;
                best = entity;
            }
        }
        return best;
    }

    // --- Fireball ------------------------------------------------------------

    private void castFireball(ServerPlayerEntity player, ManaData data, boolean enhanced) {
        ServerWorld world = (ServerWorld) player.getWorld();
        Vec3d cursor = player.raycast(50.0, 1.0f, false).getPos();

        // Base: 3-block radius, 5s. Enhanced: +2 radius and extra burn damage per second.
        double radius = enhanced ? 5.0 : 3.0;
        float fireBonus = enhanced ? (1.0f * attackChantMultiplier(player, data, Spell.FIREBALL.id)) : 0.0f;
        AreaEffectManager.add(new AreaEffect(world, cursor, radius, AreaEffect.Type.FIRE, 100, 0, fireBonus));

        // Bloom of embers from the impact point plus a bright arcane flash.
        world.spawnParticles(ModParticles.EMBER, cursor.x, cursor.y + 0.3, cursor.z,
                (int) (radius * 14), radius * 0.4, 0.4, radius * 0.4, 0.06);
        world.spawnParticles(ModParticles.ARCANE_TRAIL, cursor.x, cursor.y + 0.3, cursor.z, 12, 0.3, 0.3, 0.3, 0.12);
        world.playSound(null, cursor.x, cursor.y, cursor.z,
                SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 1.0f, 1.0f);
    }

    // --- Area Slow -----------------------------------------------------------

    private void castAreaSlow(ServerPlayerEntity player, boolean enhanced) {
        ServerWorld world = (ServerWorld) player.getWorld();
        Vec3d cursor = player.raycast(50.0, 1.0f, false).getPos();

        // 10-block radius slow field, 8s. Base Slowness I; enhanced Slowness II.
        int amplifier = enhanced ? 1 : 0;
        AreaEffectManager.add(new AreaEffect(world, cursor, 10.0, AreaEffect.Type.SLOW, 160, amplifier, 0.0f));

        world.playSound(null, cursor.x, cursor.y, cursor.z,
                SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.0f, 0.6f);
    }

    // --- Dark Orb (6th-circle Area Slow transform) ---------------------------

    private void castDarkOrb(ServerPlayerEntity player, ManaData data, boolean enhanced, boolean fullyCharged) {
        World world = player.getWorld();
        Vec3d look = player.getRotationVec(1.0f);

        // enhanced (active chant) -> floating orb that waits to be hit; otherwise a thrown orb.
        DarkOrbEntity orb = new DarkOrbEntity(world, player, enhanced, fullyCharged);
        orb.launch(look);
        world.spawnEntity(orb);

        if (world instanceof ServerWorld serverWorld) {
            Vec3d muzzle = player.getEyePos().add(look.multiply(0.6)).add(0, -0.1, 0);
            serverWorld.spawnParticles(ModParticles.VOID, muzzle.x, muzzle.y, muzzle.z, 12, 0.12, 0.12, 0.12, 0.03);
        }
        if (enhanced) {
            player.sendMessage(Text.literal("The void sphere hangs in the air, waiting to be struck...")
                    .formatted(Formatting.DARK_PURPLE), true);
        }
        playSpellSound(player, SoundEvents.BLOCK_BEACON_DEACTIVATE, 0.8f, 0.5f);
    }

    // --- Anti Magic / Clear --------------------------------------------------

    private void castClearMagic(ServerPlayerEntity player, ManaData data, long now) {
        cleanseNegativeEffects(player);
        player.setFireTicks(0);
        int cleared = AreaEffectManager.clearAffecting(player);
        data.setImmunityEndTick(now + ANTI_MAGIC_IMMUNITY_TICKS);

        ServerWorld world = (ServerWorld) player.getWorld();
        // Radiant pulse that washes outward from the caster.
        world.spawnParticles(ModParticles.RADIANCE, player.getX(), player.getBodyY(0.5), player.getZ(),
                50, 0.5, 1.0, 0.5, 0.06);
        world.spawnParticles(ModParticles.ARCANE_TRAIL, player.getX(), player.getBodyY(0.5), player.getZ(),
                16, 0.6, 1.0, 0.6, 0.04);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 0.8f, 1.6f);

        player.sendMessage(Text.literal("Anti-magic surge! Cleared " + cleared
                        + " field(s); immune to negative effects for 5s.")
                .formatted(Formatting.AQUA), false);
    }

    // --- Wall of Wind --------------------------------------------------------

    private void castWallOfWind(ServerPlayerEntity player, boolean enhanced, boolean fullyCharged) {
        World world = player.getWorld();
        Vec3d eye = player.getEyePos();
        double strength = enhanced ? 2.5 * 1.5 : 2.5; // enhanced: +50% knockback

        // Full charge: a cyclone that blasts in every direction instead of just the look direction.
        if (fullyCharged) {
            double range = 10.0;
            Box box = player.getBoundingBox().expand(range);
            for (LivingEntity living : world.getEntitiesByClass(LivingEntity.class, box, e -> e != player && e.isAlive())) {
                Vec3d to = living.getPos().subtract(player.getPos());
                Vec3d outward = new Vec3d(to.x, 0.0, to.z);
                if (outward.lengthSquared() > range * range) {
                    continue;
                }
                if (outward.lengthSquared() < 1.0e-4) {
                    outward = new Vec3d(world.random.nextDouble() - 0.5, 0, world.random.nextDouble() - 0.5);
                }
                Vec3d push = outward.normalize().multiply(strength).add(0, 0.5, 0);
                living.addVelocity(push.x, push.y, push.z);
                living.velocityModified = true;
            }
            if (world instanceof ServerWorld serverWorld) {
                for (int i = 0; i < 36; i++) {
                    double ang = (Math.PI * 2.0 * i) / 36.0;
                    Vec3d dir = new Vec3d(Math.cos(ang), 0, Math.sin(ang));
                    for (double f = 1.0; f <= range; f += 2.0) {
                        serverWorld.spawnParticles(ModParticles.GALE,
                                eye.x + dir.x * f, eye.y - 0.3 + world.random.nextDouble() * 1.4, eye.z + dir.z * f,
                                1, dir.x * strength * 0.3, 0.02, dir.z * strength * 0.3, 0.6);
                    }
                }
                serverWorld.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ENTITY_PHANTOM_FLAP, SoundCategory.PLAYERS, 1.4f, 0.5f);
            }
            return;
        }

        Vec3d look = player.getRotationVec(1.0f);
        Vec3d horiz = new Vec3d(look.x, 0.0, look.z);
        if (horiz.lengthSquared() < 1.0e-4) {
            horiz = new Vec3d(0, 0, 1);
        }
        horiz = horiz.normalize();

        double range = 10.0;     // how far the wall reaches
        double halfWidth = 2.5;  // 5 blocks wide

        Box box = player.getBoundingBox().expand(range);
        for (LivingEntity living : world.getEntitiesByClass(LivingEntity.class, box, e -> e != player && e.isAlive())) {
            Vec3d to = living.getPos().subtract(eye);
            double forward = to.x * horiz.x + to.z * horiz.z; // projection along the look direction
            if (forward <= 0 || forward > range) {
                continue;
            }
            // Lateral (horizontal) distance from the aim line.
            double lateralX = to.x - horiz.x * forward;
            double lateralZ = to.z - horiz.z * forward;
            if ((lateralX * lateralX + lateralZ * lateralZ) > (halfWidth * halfWidth)) {
                continue;
            }
            // Knock back several blocks along the look direction.
            Vec3d push = horiz.multiply(strength).add(0, 0.45, 0);
            living.addVelocity(push.x, push.y, push.z);
            living.velocityModified = true;
        }

        // Enhanced wall recoils the caster too, but much less than the target.
        if (enhanced) {
            Vec3d recoil = horiz.multiply(-strength * 0.35).add(0, 0.15, 0);
            player.addVelocity(recoil.x, recoil.y, recoil.z);
            player.velocityModified = true;
        }

        if (world instanceof ServerWorld serverWorld) {
            // A visible gale sweeping along the wall: gusts march outward across its full width.
            Vec3d perp = new Vec3d(-horiz.z, 0.0, horiz.x);
            Vec3d gustVel = horiz.multiply(strength * 0.35);
            for (double f = 1.0; f <= range; f += 1.0) {
                for (double w = -halfWidth; w <= halfWidth; w += 1.25) {
                    double gx = eye.x + horiz.x * f + perp.x * w;
                    double gy = eye.y - 0.3 + world.random.nextDouble() * 1.4;
                    double gz = eye.z + horiz.z * f + perp.z * w;
                    serverWorld.spawnParticles(ModParticles.GALE, gx, gy, gz, 1,
                            gustVel.x, 0.02, gustVel.z, 0.6);
                }
            }
            serverWorld.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENTITY_PHANTOM_FLAP, SoundCategory.PLAYERS, 1.2f, 0.7f);
        }
    }

    // --- Thunder -------------------------------------------------------------

    private void castThunder(ServerPlayerEntity player, boolean enhanced, boolean fullyCharged) {
        ServerWorld world = (ServerWorld) player.getWorld();
        Vec3d cursor = player.raycast(50.0, 1.0f, false).getPos();

        // Base 1 bolt; active chant 3; fully charged 6.
        int bolts = fullyCharged ? 6 : (enhanced ? 3 : 1);
        strikeLightning(world, player, cursor.x, cursor.y, cursor.z);
        int extra = bolts - 1;
        for (int i = 0; i < extra; i++) {
            double angle = (Math.PI * 2.0 * i) / extra;
            strikeLightning(world, player, cursor.x + Math.cos(angle) * 1.4, cursor.y, cursor.z + Math.sin(angle) * 1.4);
        }
    }

    private void strikeLightning(ServerWorld world, ServerPlayerEntity player, double x, double y, double z) {
        LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world);
        if (bolt != null) {
            bolt.refreshPositionAfterTeleport(x, y, z);
            bolt.setChanneler(player);
            world.spawnEntity(bolt);
        }
        // Vanilla lightning has no attacker, so it wouldn't aggro mobs. Mark the player as the
        // attacker on everything near the strike so the Thunder reads as a player attack.
        Box box = new Box(x - 4.0, y - 4.0, z - 4.0, x + 4.0, y + 4.0, z + 4.0);
        for (LivingEntity living : world.getEntitiesByClass(LivingEntity.class, box,
                e -> e != player && e.isAlive())) {
            living.setAttacker(player);
            if (living instanceof MobEntity mob) {
                mob.setTarget(player);
            }
        }
    }

    // --- Barrier -------------------------------------------------------------

    private void castBarrier(ServerPlayerEntity player, boolean dome) {
        ServerWorld world = (ServerWorld) player.getWorld();

        if (dome) {
            ManaData data = ManaPlayer.get(player);
            // Replace any existing dome.
            if (data.getActiveDomeId() >= 0) {
                Entity old = world.getEntityById(data.getActiveDomeId());
                if (old != null) {
                    old.discard();
                }
            }
            MagicBarrierEntity shield = new MagicBarrierEntity(ModEntities.MAGIC_BARRIER, world);
            shield.setPosition(player.getX(), player.getY() + 1.0, player.getZ());
            shield.initDome(player, 200, 5); // 10s timer / 5 hits
            world.spawnEntity(shield);
            data.setActiveDomeId(shield.getId());
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.8f, 1.5f);
            player.sendMessage(Text.literal("A dodecahedral shield envelops you (absorbs 5 hits / 10s; "
                            + "hold sneak to sustain it at 100 mana/s).").formatted(Formatting.AQUA), false);
            return;
        }

        // Wall: a 5x5x1 barrier facing the player, up to 6 blocks out.
        Vec3d look = player.getRotationVec(1.0f);
        Vec3d horiz = new Vec3d(look.x, 0.0, look.z);
        if (horiz.lengthSquared() < 1.0e-4) {
            horiz = new Vec3d(0, 0, 1);
        }
        horiz = horiz.normalize();

        Vec3d cursor = player.raycast(6.0, 1.0f, false).getPos();
        double dx = cursor.x - player.getX();
        double dz = cursor.z - player.getZ();
        double dist = Math.max(2.0, Math.min(6.0, Math.sqrt(dx * dx + dz * dz)));
        double cx = player.getX() + horiz.x * dist;
        double cz = player.getZ() + horiz.z * dist;
        double cy = player.getY() + 2.0; // 5-tall wall spans roughly feet..+4.5

        // Wide perpendicular to the look direction, thin along it.
        boolean lookAlongZ = Math.abs(horiz.z) >= Math.abs(horiz.x);
        float halfX = lookAlongZ ? 2.5f : 0.5f;
        float halfZ = lookAlongZ ? 0.5f : 2.5f;
        float halfY = 2.5f;

        MagicBarrierEntity wall = new MagicBarrierEntity(ModEntities.MAGIC_BARRIER, world);
        wall.setPosition(cx, cy, cz);
        wall.initWall(player, halfX, halfY, halfZ, 140); // 7s
        world.spawnEntity(wall);
        world.playSound(null, cx, cy, cz, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.7f, 1.1f);
    }

    // --- Magic Array carving -------------------------------------------------

    private void selectArray(ServerPlayerEntity player, int arrayId) {
        ManaData data = ManaPlayer.get(player);
        if (!data.isArrayUnlocked()) {
            return;
        }
        ArrayType type = ArrayType.byId(arrayId);
        data.setSelectedArray(type.ordinal());
        ModNetworking.syncToClient(player);
        player.sendMessage(Text.literal("Selected array: " + type.displayName)
                .formatted(Formatting.LIGHT_PURPLE), true);
    }

    private void startCarving(ServerPlayerEntity player) {
        ManaData data = ManaPlayer.get(player);
        long now = player.getWorld().getTime();

        if (!data.isArrayUnlocked()) {
            player.sendMessage(Text.literal("Magic Arrays are still locked. Reach the 3rd circle and keep casting.")
                    .formatted(Formatting.RED), true);
            return;
        }
        if (data.isCarving()) {
            return;
        }
        if (data.isCooldownsEnabled() && now < data.getCarveCooldownEnd()) {
            double secondsLeft = (data.getCarveCooldownEnd() - now) / 20.0;
            player.sendMessage(Text.literal(String.format("Carving on cooldown (%.1fs)", secondsLeft))
                    .formatted(Formatting.RED), true);
            return;
        }
        if (data.getCurrentMana() < CARVE_COST) {
            player.sendMessage(Text.literal("Not enough mana — " + CARVE_COST + " required to carve.")
                    .formatted(Formatting.RED), true);
            return;
        }

        data.setCarving(true);
        data.setCarveEndTick(now + CARVE_DURATION_TICKS);
        data.setCarveStart(player.getX(), player.getY(), player.getZ());
        player.sendMessage(Text.literal("Carving array... hold still.").formatted(Formatting.AQUA), true);
    }

    private void tickCarving(ServerPlayerEntity player, ManaData data, long now) {
        // Interrupt on movement.
        if (data.carveMovedSquared(player.getX(), player.getY(), player.getZ()) > CARVE_MOVE_THRESHOLD_SQ) {
            data.setCarving(false);
            data.setCarveCooldownEnd(now + CARVE_CANCEL_COOLDOWN);
            player.sendMessage(Text.literal("Carving interrupted! (3s cooldown)").formatted(Formatting.RED), true);
            return;
        }

        // Progress particles: arcane motes spiral inward toward the carver's hands.
        if (player.getWorld() instanceof ServerWorld serverWorld) {
            double prog = (double) (now - (data.getCarveEndTick() - CARVE_DURATION_TICKS)) / CARVE_DURATION_TICKS;
            double ringR = 1.4 * (1.0 - Math.max(0.0, Math.min(1.0, prog))) + 0.3;
            for (int i = 0; i < 3; i++) {
                double a = serverWorld.random.nextDouble() * Math.PI * 2.0;
                serverWorld.spawnParticles(ModParticles.ARCANE,
                        player.getX() + Math.cos(a) * ringR, player.getY() + 0.1,
                        player.getZ() + Math.sin(a) * ringR, 1, 0.0, 0.05, 0.0, 0.0);
            }
        }

        if (now >= data.getCarveEndTick()) {
            data.setCarving(false);
            if (!data.trySpendMana(CARVE_COST)) {
                player.sendMessage(Text.literal("Not enough mana to finish carving.").formatted(Formatting.RED), true);
                return;
            }
            data.setCarveCooldownEnd(now + CARVE_SUCCESS_COOLDOWN);
            createArray(player, data);
            ModNetworking.syncToClient(player);
        }
    }

    private void createArray(ServerPlayerEntity player, ManaData data) {
        ServerWorld world = (ServerWorld) player.getWorld();
        ArrayType type = ArrayType.byId(data.getSelectedArray());
        Vec3d center = computeArrayCenter(player);
        MagicArrayManager.add(new MagicArray(world, center, type, ARRAY_LIFETIME_TICKS));
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 1.0f, 1.2f);
        player.sendMessage(Text.literal("Carved a " + type.displayName + " array (5-block radius).")
                .formatted(Formatting.LIGHT_PURPLE), false);
    }

    /**
     * The array forms in the direction the player is looking, between 3 and 5 blocks away, so it is
     * never carved under the player and never further than 5 blocks out.
     */
    private Vec3d computeArrayCenter(ServerPlayerEntity player) {
        Vec3d look = player.getRotationVec(1.0f);
        Vec3d horiz = new Vec3d(look.x, 0.0, look.z);
        if (horiz.lengthSquared() < 1.0e-4) {
            horiz = new Vec3d(0, 0, 1);
        }
        horiz = horiz.normalize();

        // How far the crosshair is, horizontally, capped at 7 and floored at 3.
        Vec3d cursor = player.raycast(8.0, 1.0f, false).getPos();
        double dx = cursor.x - player.getX();
        double dz = cursor.z - player.getZ();
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        double dist = Math.max(3.0, Math.min(7.0, horizontalDist));

        return new Vec3d(player.getX() + horiz.x * dist, player.getY(), player.getZ() + horiz.z * dist);
    }

    /** A brief elemental rune flash in front of the caster when an enhanced spell releases. */
    private void spawnCastRune(ServerPlayerEntity player, Spell spell) {
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return;
        }
        var rune = switch (spell) {
            case FIREBALL -> ModParticles.RUNE_FIRE;
            case THUNDER -> ModParticles.RUNE_THUNDER;
            case WALL_OF_WIND, WIND_BLADE, DASH -> ModParticles.RUNE_WIND;
            default -> ModParticles.RUNE_BIND;
        };
        Vec3d look = player.getRotationVec(1.0f);
        Vec3d p = player.getEyePos().add(look.multiply(1.4)).add(0, -0.2, 0);
        world.spawnParticles(rune, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
    }

    private void playSpellSound(ServerPlayerEntity player, net.minecraft.sound.SoundEvent sound, float volume, float pitch) {
        if (player.getWorld() instanceof ServerWorld serverWorld) {
            serverWorld.playSound(null, player.getX(), player.getY(), player.getZ(),
                    sound, SoundCategory.PLAYERS, volume, pitch);
        }
    }

    // ------------------------------------------------------------------------
    // Advancements
    // ------------------------------------------------------------------------

    private boolean hasAdvancement(ServerPlayerEntity player, Identifier id) {
        Advancement advancement = player.getServer().getAdvancementLoader().get(id);
        if (advancement == null) {
            return false;
        }
        return player.getAdvancementTracker().getProgress(advancement).isDone();
    }

    private void grantAdvancement(ServerPlayerEntity player, Identifier id) {
        Advancement advancement = player.getServer().getAdvancementLoader().get(id);
        if (advancement == null) {
            return;
        }
        if (player.getAdvancementTracker().getProgress(advancement).isDone()) {
            return;
        }
        for (String criterion : advancement.getCriteria().keySet()) {
            player.getAdvancementTracker().grantCriterion(advancement, criterion);
        }
    }
}
