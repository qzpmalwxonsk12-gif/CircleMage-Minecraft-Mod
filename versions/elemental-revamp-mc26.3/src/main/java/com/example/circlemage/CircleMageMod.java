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
import com.example.circlemage.spell.SpellCatalog.Entry;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import com.example.circlemage.network.ServerPackets;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.EmptyLootItem;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.providers.number.floats.ConstantValue;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CircleMageMod implements ModInitializer {

    public static final String MOD_ID = "circlemage";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final Identifier ANTI_MAGIC_ADVANCEMENT = Identifier.fromNamespaceAndPath(MOD_ID, "basic_anti_magic");
    public static final Identifier ARRAY_ADVANCEMENT = Identifier.fromNamespaceAndPath(MOD_ID, "array_carving");
    public static final Identifier COMBINATION_ADVANCEMENT = Identifier.fromNamespaceAndPath(MOD_ID, "combination_magic");
    public static final Identifier LAWS_ADVANCEMENT = Identifier.fromNamespaceAndPath(MOD_ID, "laws");

    /** Per-second chance that the Laws system unlocks on its own while playing (0.01%). */
    private static final double LAWS_UNLOCK_CHANCE = 0.0001;

    /** Maximum spell charge (ticks) reachable by holding a primed active chant — must match the client. */
    public static final int PRIME_MAX_TICKS = 60; // 3 seconds

    /** Attribute modifier used to freeze the player in place while channelling (-100% move speed). */
    private static final Identifier CHANNEL_SLOW_UUID = Identifier.fromNamespaceAndPath(MOD_ID, "channel_slow_uuid");
    private static final AttributeModifier CHANNEL_SLOW_MODIFIER = new AttributeModifier(
            CHANNEL_SLOW_UUID, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

    /** Reinforce chant: +20% movement speed. */
    private static final Identifier REINFORCE_SPEED_UUID = Identifier.fromNamespaceAndPath(MOD_ID, "reinforce_speed_uuid");
    private static final AttributeModifier REINFORCE_SPEED_MODIFIER = new AttributeModifier(
            REINFORCE_SPEED_UUID, 0.20, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

    /** Legacy Vitality bonus-hearts modifier UUID. Vitality no longer grants hearts (1.0.8.3); this is
     *  kept only so {@link #applyVitality} can strip the modifier from players who still carry it. */
    private static final Identifier VITALITY_HEALTH_UUID = Identifier.fromNamespaceAndPath(MOD_ID, "vitality_health_uuid");

    // Roll chances (per magic use).
    private static final double ANTI_MAGIC_CHANCE = 0.05; // After witnessing guardian Dispel.
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
        com.example.circlemage.spell.RevampStatusEffects.register();
        registerWanderingMageSpawning();
        com.example.circlemage.item.ModItems.register();
        com.example.circlemage.block.ModBlocks.register();
        registerManaCrystalLoot();
        ModParticles.register();
        ModNetworking.registerPayloads();
        registerPacketReceivers();
        ModCommands.register();
        com.example.circlemage.command.BossMagicCommands.register();
        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);

        // Law of Life: cancel the player's death, reviving them where they stand. 100000 mana normally;
        // in hardcore it costs 10x and shows the "give up?" screen (you can't, of course).
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayer player) {
                ManaData data = ManaPlayer.get(player);
                boolean hardcore = player.level().getServer() != null && player.level().getServer().isHardcore();
                double cost = hardcore ? 1000000.0 : 100000.0;
                if (data.getCircle() >= Circles.MAX_CIRCLE && data.isLawUnlocked(Law.LIFE.ordinal())
                        && Law.byId(data.getSelectedLaw()) == Law.LIFE && data.trySpendMana(cost)) {
                    player.setHealth(player.getMaxHealth());
                    player.removeAllEffects();
                    player.setRemainingFireTicks(0);
                    if (player.level() instanceof ServerLevel world) {
                        world.sendParticles(ModParticles.RADIANCE, player.getX(), player.getY(0.5), player.getZ(),
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
            AreaEffectManager.clear();
            MagicArrayManager.clear();
            WAND_LIGHTS.clear();
        });
        // Clean up a leaving player's glow-wand light so it never lingers in the world.
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> removeWandLight(server, handler.player.getUUID()));

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
        SpawnPlacements.register(ModEntities.WANDERING_MAGE, net.minecraft.world.entity.SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, WanderingMageEntity::canSpawn);
        BiomeModifications.addSpawn(BiomeSelectors.foundInOverworld(), MobCategory.MONSTER,
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
        LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
            if (source.isBuiltin() && key.identifier().getNamespace().equals("minecraft") && key.identifier().getPath().startsWith("chests/")) {
                tableBuilder.withPool(LootPool.lootPool()
                        .setRolls(net.minecraft.core.Holder.direct(new net.minecraft.world.level.storage.loot.providers.number.ints.ConstantValue(1)))
                        .add(LootItem.lootTableItem(com.example.circlemage.item.ModItems.MANA_CRYSTAL).setWeight(1))
                        .add(EmptyLootItem.emptyItem().setWeight(9)));
                tableBuilder.withPool(LootPool.lootPool()
                        .setRolls(net.minecraft.core.Holder.direct(new net.minecraft.world.level.storage.loot.providers.number.ints.ConstantValue(1)))
                        .add(LootItem.lootTableItem(com.example.circlemage.item.ModItems.TOME_FIRE).setWeight(3))
                        .add(LootItem.lootTableItem(com.example.circlemage.item.ModItems.TOME_WIND).setWeight(3))
                        .add(LootItem.lootTableItem(com.example.circlemage.item.ModItems.TOME_MISC).setWeight(3))
                        .add(LootItem.lootTableItem(com.example.circlemage.item.ModItems.TOME_WATER).setWeight(2))
                        .add(LootItem.lootTableItem(com.example.circlemage.item.ModItems.TOME_EARTH).setWeight(2))
                        .add(EmptyLootItem.emptyItem().setWeight(143)));
            }
        });
    }

    private void registerPacketReceivers() {
        ServerPackets.registerGlobalReceiver(ModNetworking.BIND_SPELL, (server, player, handler, buf, sender) -> {
            int id=buf.readInt(); boolean equip=buf.readBoolean();
            server.execute(() -> {
                ManaData data=ManaPlayer.get(player);
                var catalog=com.example.circlemage.spell.SpellCatalog.entry(id);
                if(catalog==null)return;
                if(equip){
                    String error=com.example.circlemage.spell.SpellCatalog.bindError(data.getCircle(),data.getUnlockedSpells(),data.getBoundSpells(),data.isMagicLimitBroken(),id);
                    if(error!=null){com.example.circlemage.util.Messages.send(player, Component.literal(error).withStyle(ChatFormatting.RED),true);return;}
                    data.setBoundSpells(com.example.circlemage.spell.SpellCatalog.bind(data.getBoundSpells(),id,data.isMagicLimitBroken()));
                }else data.setBoundSpells(data.getBoundSpells() & ~(1<<id));
                ModNetworking.syncToClient(player);
            });
        });
        ServerPackets.registerGlobalReceiver(ModNetworking.CHANNEL_TOGGLE,
                (server, player, handler, buf, sender) -> server.execute(() -> toggleChanneling(player)));

        ServerPackets.registerGlobalReceiver(ModNetworking.CAST, (server, player, handler, buf, sender) -> {
            int spellId = buf.readInt();
            boolean primed = buf.readBoolean();
            int charge = buf.readInt();
            server.execute(() -> handleCast(player, spellId, primed, charge));
        });

        ServerPackets.registerGlobalReceiver(ModNetworking.COMBINE, (server, player, handler, buf, sender) -> {
            int spellA = buf.readInt();
            int charge = buf.readInt();
            int spellB = buf.readInt();
            server.execute(() -> handleCombine(player, spellA, charge, spellB));
        });

        ServerPackets.registerGlobalReceiver(ModNetworking.SELECT_FORM, (server, player, handler, buf, sender) -> {
            int baseSpellId = buf.readInt();
            boolean revert = buf.readBoolean();
            server.execute(() -> selectForm(player, baseSpellId, revert));
        });

        ServerPackets.registerGlobalReceiver(ModNetworking.CARVE,
                (server, player, handler, buf, sender) -> server.execute(() -> startCarving(player)));

        ServerPackets.registerGlobalReceiver(ModNetworking.SELECT_ARRAY, (server, player, handler, buf, sender) -> {
            int arrayId = buf.readInt();
            server.execute(() -> selectArray(player, arrayId));
        });

        ServerPackets.registerGlobalReceiver(ModNetworking.SELECT_CHANT, (server, player, handler, buf, sender) -> {
            int chantId = buf.readInt();
            server.execute(() -> selectChant(player, chantId));
        });

        ServerPackets.registerGlobalReceiver(ModNetworking.SELECT_LAW, (server, player, handler, buf, sender) -> {
            int lawId = buf.readInt();
            server.execute(() -> selectLaw(player, lawId));
        });

        ServerPackets.registerGlobalReceiver(ModNetworking.ACTIVATE_LAW,
                (server, player, handler, buf, sender) -> server.execute(() -> activateLaw(player)));

        ServerPackets.registerGlobalReceiver(ModNetworking.OPEN_STORAGE,
                (server, player, handler, buf, sender) -> server.execute(() -> openStorage(player)));

        ServerPackets.registerGlobalReceiver(ModNetworking.DASH, (server, player, handler, buf, sender) -> {
            float forward = buf.readFloat();
            float strafe = buf.readFloat();
            server.execute(() -> handleDash(player, forward, strafe));
        });

        ServerPackets.registerGlobalReceiver(ModNetworking.REQUEST_JOURNAL,
                (server, player, handler, buf, sender) -> server.execute(() -> ModNetworking.sendJournalSync(player)));

        ServerPackets.registerGlobalReceiver(ModNetworking.BOOK_CLOSED,
                (server, player, handler, buf, sender) -> server.execute(() -> onBookClosed(player)));
    }

    // ------------------------------------------------------------------------
    // Intro cinematic (v2.2.1): the "woke up with a book" sequence
    // ------------------------------------------------------------------------

    /** Fresh player (intro stage 0): drop the tutorial book in front of them and show the opening line. */
    private void maybeStartBookIntro(ServerPlayer player) {
        ManaData data = ManaPlayer.get(player);
        if (data.getIntroStage() != 0 || !(player.level() instanceof ServerLevel world)) {
            return;
        }
        Vec3 look = player.getViewVector(1.0f);
        Vec3 horiz = new Vec3(look.x, 0.0, look.z);
        if (horiz.lengthSqr() < 1.0e-4) {
            horiz = new Vec3(0, 0, 1);
        }
        Vec3 pos = player.position().add(horiz.normalize().scale(1.1)).add(0, 0.3, 0);
        com.example.circlemage.entity.TutorialBookEntity book =
                new com.example.circlemage.entity.TutorialBookEntity(ModEntities.TUTORIAL_BOOK, world);
        book.setPos(pos.x, pos.y, pos.z);
        book.setYRot(player.getYRot());
        world.addFreshEntity(book);
        world.sendParticles(ModParticles.ARCANE, pos.x, pos.y + 0.2, pos.z, 20, 0.2, 0.2, 0.2, 0.03);
        sendCinematic(player, Component.literal("You just woke up with a book?").withStyle(ChatFormatting.LIGHT_PURPLE),
                Component.literal("Right-click the book...").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    /** Called by the tutorial book when its absorb animation finishes: advance the intro, prompt J. */
    public static void completeBookIntro(ServerPlayer player) {
        ManaData data = ManaPlayer.get(player);
        if (data.getIntroStage() > 0) {
            return;
        }
        data.setIntroStage(1);
        ModNetworking.syncToClient(player);
        sendCinematic(player, Component.literal("The book just disappeared...").withStyle(ChatFormatting.LIGHT_PURPLE),
                Component.literal("(press J to open it)").withStyle(ChatFormatting.AQUA));
    }

    /** First time the spellbook is closed after the intro: the "pages are missing" beat. */
    private void onBookClosed(ServerPlayer player) {
        ManaData data = ManaPlayer.get(player);
        if (data.getIntroStage() == 1) {
            data.setIntroStage(2);
            ModNetworking.syncToClient(player);
            sendCinematic(player,
                    Component.literal("Pages in the book seem to be missing...").withStyle(ChatFormatting.LIGHT_PURPLE),
                    Component.literal("They will fill as your power grows.").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }

    /** Intro beats land in the in-game chat box (v2.3.0 — previously full-screen titles). */
    private static void sendCinematic(ServerPlayer player, Component title, Component subtitle) {
        com.example.circlemage.util.Messages.send(player, title, false);
        if (subtitle != null) {
            com.example.circlemage.util.Messages.send(player, subtitle, false);
        }
    }

    // ------------------------------------------------------------------------
    // Server tick: channelling, lingering fields, anti-magic immunity
    // ------------------------------------------------------------------------

    private void onServerTick(MinecraftServer server) {
        AreaEffectManager.tickAll();
        MagicArrayManager.tickAll();
        com.example.circlemage.boss.BossZoneManager.tickAll();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ManaData data = ManaPlayer.get(player);
            long now = player.level().getGameTime();

            // Safety net: the circle-1 basics are always known at circle 1+ (covers pre-2.1 saves
            // and debug/command-set circles).
            if (data.getCircle() >= 1 && !data.isSpellUnlocked(Spell.MAGIC_MISSILE.id)) {
                data.unlockSpell(Spell.MAGIC_MISSILE.id);
                // Wind Escape is learned from its tome at circle 2.
            }

            // Natural mana flow (v2.1): +1/s per circle, +5/s per attuned Mana Crystal — no
            // channelling required. Circle ascension can trigger from it like any other gain.
            double naturalPerSecond = data.naturalRegenPerSecond()
                    * (player.hasEffect(com.example.circlemage.spell.RevampStatusEffects.SOOTHING) ? 2.0 : 1.0);
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
                player.setRemainingFireTicks(0);
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
                MobEffectInstance regen = player.getEffect(MobEffects.REGENERATION);
                if (regen == null || regen.getDuration() <= 1 || regen.getAmplifier() < 2) {
                    player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 2, false, false, true));
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
    private boolean tickManaInfusion(ServerPlayer player, ManaData data, long now) {
        net.minecraft.world.item.ItemStack held = player.getMainHandItem();
        net.minecraft.world.item.Item output = com.example.circlemage.item.ManaInfusion.outputFor(held.getItem());
        if (output == null || held.isEmpty()) {
            data.resetInfusion();
            return false;
        }
        if (data.tickInfusion()) {
            held.shrink(1);
            giveOrDrop(player, new net.minecraft.world.item.ItemStack(output));
            if (player.level() instanceof ServerLevel world) {
                world.sendParticles(ModParticles.ARCANE, player.getX(), player.getY(0.7), player.getZ(),
                        24, 0.3, 0.4, 0.3, 0.05);
                world.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.7f, 1.6f);
            }
        }
        return true;
    }

    // ------------------------------------------------------------------------
    // v2.2 wand cosmetics: held particles + glow-crystal dynamic light
    // ------------------------------------------------------------------------

    /** Where each player's glow-crystal wand light block currently sits. */
    private static final java.util.Map<java.util.UUID, net.minecraft.core.GlobalPos> WAND_LIGHTS =
            new java.util.HashMap<>();

    private void tickWandCosmetics(ServerPlayer player, long now) {
        ItemStack wand = com.example.circlemage.item.WandItem.heldWand(player);
        com.example.circlemage.item.WandItem.WandMaterial material =
                wand == null ? null : com.example.circlemage.item.WandItem.materialOf(wand);

        // End rod: small purple motes drifting off the wand. Blaze rod: embers.
        if (material != null && now % 4 == 0 && player.level() instanceof ServerLevel world) {
            var particle = switch (material) {
                case END_ROD -> ModParticles.BIND_MOTE;
                case BLAZE_ROD -> ModParticles.EMBER;
                default -> null;
            };
            if (particle != null) {
                Vec3 look = player.getViewVector(1.0f);
                Vec3 right = look.cross(new Vec3(0, 1, 0));
                if (right.lengthSqr() < 1.0e-4) {
                    right = new Vec3(1, 0, 0);
                }
                boolean mainHand = player.getMainHandItem() == wand;
                Vec3 hand = player.getEyePosition().add(look.scale(0.45))
                        .add(right.normalize().scale(mainHand ? 0.3 : -0.3)).add(0, -0.25, 0);
                world.sendParticles(particle, hand.x, hand.y, hand.z, 1, 0.03, 0.06, 0.03, 0.01);
            }
        }

        tickWandLight(player, wand);
    }

    /** Glow-crystal core: keep an invisible light block (torch level 14) at the carrier's head. */
    private void tickWandLight(ServerPlayer player, ItemStack wand) {
        boolean glowing = wand != null
                && com.example.circlemage.item.WandItem.coreOf(wand)
                == com.example.circlemage.item.WandItem.WandCore.GLOW_CRYSTAL;
        java.util.UUID id = player.getUUID();
        if (!glowing) {
            removeWandLight(player.level().getServer(), id);
            return;
        }
        if (!(player.level() instanceof ServerLevel world)) {
            return;
        }
        net.minecraft.core.BlockPos eye = net.minecraft.core.BlockPos.containing(player.getEyePosition());
        net.minecraft.core.GlobalPos prev = WAND_LIGHTS.get(id);
        if (prev != null && prev.dimension() == world.dimension() && prev.pos().equals(eye)) {
            return; // still lit from here
        }
        removeWandLight(player.level().getServer(), id);
        if (world.getBlockState(eye).isAir()) {
            world.setBlockAndUpdate(eye, net.minecraft.world.level.block.Blocks.LIGHT.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LightBlock.LEVEL, 14));
            WAND_LIGHTS.put(id, net.minecraft.core.GlobalPos.of(world.dimension(), eye));
        }
    }

    /** Clears a player's wand light (if it still exists and is still a light block). */
    private static void removeWandLight(MinecraftServer server, java.util.UUID id) {
        net.minecraft.core.GlobalPos prev = WAND_LIGHTS.remove(id);
        if (prev == null || server == null) {
            return;
        }
        ServerLevel world = server.getLevel(prev.dimension());
        if (world != null && world.getBlockState(prev.pos()).is(net.minecraft.world.level.block.Blocks.LIGHT)) {
            world.setBlockAndUpdate(prev.pos(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        }
    }

    /** A rising spiral of arcane motes that gathers around a channelling mage. */
    private void spawnChannelAura(ServerPlayer player, long now) {
        if (!(player.level() instanceof ServerLevel world) || now % 3 != 0) {
            return;
        }
        double radius = 0.55;
        double baseAngle = (now % 40) * (Math.PI * 2.0 / 40.0);
        for (int i = 0; i < 2; i++) {
            double angle = baseAngle + i * Math.PI;
            double px = player.getX() + Math.cos(angle) * radius;
            double pz = player.getZ() + Math.sin(angle) * radius;
            double py = player.getY() + (now % 20) / 20.0 * 1.9;
            world.sendParticles(ModParticles.ARCANE, px, py, pz, 1, 0.0, 0.04, 0.0, 0.01);
        }
    }

    private void tickChantUpkeep(ServerPlayer player, ManaData data) {
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
            com.example.circlemage.util.Messages.send(player, Component.literal("Your " + chant.displayName + " chant fizzled out — not enough mana.")
                    .withStyle(ChatFormatting.RED), true);
        } else {
            ModNetworking.syncToClient(player);
        }
    }

    private void cleanseNegativeEffects(ServerPlayer player) {
        for (MobEffectInstance instance : new ArrayList<>(player.getActiveEffects())) {
            if (instance.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
                player.removeEffect(instance.getEffect());
            }
        }
    }

    // ------------------------------------------------------------------------
    // Channelling
    // ------------------------------------------------------------------------

    /** Toggles mana channelling (also used by the Arcane Focus item's right-click). */
    public static void toggleChanneling(ServerPlayer player) {
        ManaData data = ManaPlayer.get(player);
        boolean nowChanneling = !data.isChanneling();
        data.setChanneling(nowChanneling);
        applyChannelLock(player, nowChanneling);
        ModNetworking.syncToClient(player);
    }

    private static void applyChannelLock(ServerPlayer player, boolean locked) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        if (locked) {
            if (!speed.hasModifier(CHANNEL_SLOW_MODIFIER.id())) {
                speed.addTransientModifier(CHANNEL_SLOW_MODIFIER);
            }
        } else {
            speed.removeModifier(CHANNEL_SLOW_UUID);
            speed.removeModifier(Identifier.withDefaultNamespace("8d2f1c4a-3b6e-4f0a-9c1d-7e5a2b8f3c1d"));
        }
    }

    private void tryAdvanceCircle(ServerPlayer player, ManaData data) {
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
            com.example.circlemage.util.Messages.send(player, Component.literal("You have ascended to the " + target + " circle of magehood!")
                    .withStyle(ChatFormatting.LIGHT_PURPLE), false);
            // The 1st circle awakens the two innate techniques; all other magic hides in tomes.
            if (target == 1) {
                data.unlockSpell(Spell.MAGIC_MISSILE.id);
                // Wind Escape is learned from its tome at circle 2.
                com.example.circlemage.util.Messages.send(player, Component.literal("Magic awakens within you: Magic Missile unlocked! "
                        + "Seek magic tomes to learn more.").withStyle(ChatFormatting.GOLD), false);
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
    private Spell resolveForm(Spell base, ManaData data) { return base; }

    /**
     * @param primed true if this came from a held active chant (the hold was the cast time)
     * @param charge how long the spell was primed, in ticks (PRIME_MAX_TICKS = fully charged)
     */
    private void handleCast(ServerPlayer player, int spellId, boolean primed, int charge) {
        if(!player.isAlive()||player.isSpectator())return;
        Spell base = Spell.byId(spellId);
        if (base != null) {
            ManaData check=ManaPlayer.get(player);
            if(!com.example.circlemage.spell.SpellCatalog.usable(check.getCircle(),check.getUnlockedSpells(),check.getBoundSpells(),check.isMagicLimitBroken(),spellId)){
                com.example.circlemage.util.Messages.send(player, Component.literal("This magic is locked by its circle, tome knowledge, or J-menu loadout.").withStyle(ChatFormatting.RED),true);return;
            }
            String reason=com.example.circlemage.spell.RevampMagic.failure(player,base);
            if(reason!=null){com.example.circlemage.util.Messages.send(player, Component.literal(reason).withStyle(ChatFormatting.RED),true);return;}
        }
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
        long now = player.level().getGameTime();
        Spell spell = resolveForm(base, data);

        // Anti-magic must be learned before it can be cast.
        if (spell == Spell.CLEAR_MAGIC && !hasAdvancement(player, ANTI_MAGIC_ADVANCEMENT)) {
            com.example.circlemage.util.Messages.send(player, Component.literal("You have not yet learned Basic Anti Magic.")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        // v2.1: every other spell must be known (circle-1 basics or learned from a magic tome).
        if (!isSpellKnown(data, base.id)) {
            com.example.circlemage.util.Messages.send(player, spellLockedMessage(base), true);
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
            enhanced = player.isShiftKeyDown();
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
            com.example.circlemage.util.Messages.send(player, Component.literal(String.format("%s is on cooldown (%.1fs)", spellName(spell), secondsLeft))
                    .withStyle(ChatFormatting.RED), true);
            return;
        }

        // Mana gate. 5th circle: 0.85x cost. Channeling chant: free. Focus/wand (v2/v2.2) discount.
        boolean channelingChant = data.isPassiveChantUnlocked()
                && PassiveChant.byId(data.getSelectedChant()) == PassiveChant.CHANNELING;
        double focusDiscount = holdingFocus(player) ? com.example.circlemage.item.ArcaneFocusItem.COST_MULT : 1.0;
        int effectiveCost = channelingChant ? 0 : (int) Math.round(spell.manaCost * data.circleDiscount()
                * focusDiscount * com.example.circlemage.item.WandItem.costMultiplier(player, spell.id));
        if (!data.trySpendMana(effectiveCost)) {
            com.example.circlemage.util.Messages.send(player, Component.literal("Not enough mana — " + effectiveCost + " required.")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }

        data.setCooldownEnd(spell.id, now + effectiveCooldown);
        doMagicUseRolls(player, data);

        if (primed && chantable) {
            // The player already held to charge, so the spell fires immediately.
            if (enhanced) {
                com.example.circlemage.util.Messages.send(player, getChantText(spell).withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC), false);
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
                com.example.circlemage.util.Messages.send(player, Component.literal("Casting " + spellName(spell) + "...").withStyle(ChatFormatting.AQUA), true);
            }
        }
        ModNetworking.syncToClient(player);
    }

    private void performSpell(ServerPlayer player, ManaData data, Spell spell, boolean enhanced, boolean fullyCharged) {
        long now = player.level().getGameTime();
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
            default -> com.example.circlemage.spell.RevampMagic.cast(player, spell);
        }
    }

    /** True if the spell is known: in the learned bitmask, or an innate circle-1 basic. */
    private boolean isSpellKnown(ManaData data, int spellId) {
        return com.example.circlemage.spell.SpellCatalog.known(data.getUnlockedSpells(), spellId);
    }

    private Component spellLockedMessage(Spell base) {
        String hint = (base == Spell.MAGIC_MISSILE || base == Spell.DASH)
                ? "Reach the 1st circle to awaken it."
                : "Its secret sleeps in a magic tome.";
        return Component.literal("You have not learned " + spellName(base) + ". " + hint).withStyle(ChatFormatting.RED);
    }

    // --- Fireball (v2.1, Fire Tome) -------------------------------------------

    /** T1 Fireball: half a heart on impact and five seconds of ignition. */
    private void castFireBolt(ServerPlayer player, ManaData data) {
        Level world = player.level();
        Vec3 look = player.getViewVector(1.0f);

        float damage = 1.0f; // T1 Fireball: half a heart before target mitigation.
        MagicMissileEntity bolt = new MagicMissileEntity(world, player, damage);
        bolt.setFireBolt(true);
        bolt.launch(look);
        world.addFreshEntity(bolt);

        if (world instanceof ServerLevel serverWorld) {
            Vec3 muzzle = player.getEyePosition().add(look.scale(0.6)).add(0, -0.1, 0);
            serverWorld.sendParticles(ModParticles.EMBER, muzzle.x, muzzle.y, muzzle.z, 10, 0.12, 0.12, 0.12, 0.03);
        }
        playSpellSound(player, SoundEvents.FIRECHARGE_USE, 0.8f, 1.3f);
    }

    // --- Wind Blade ----------------------------------------------------------

    /** Number of blades in a single Wind Blade volley, and the gap (ticks) between them. */
    private static final int WIND_BLADE_VOLLEY = 3;
    private static final int WIND_BLADE_GAP = 2; // ~100ms between blades

    /** Begins a Wind Blade volley; the blades are fired over the next few ticks in {@link #onServerTick}. */
    private void castWindBlade(ServerPlayer player, ManaData data) {
        long now = player.level().getGameTime();
        data.startWindBladeVolley(WIND_BLADE_VOLLEY, now);
        playSpellSound(player, SoundEvents.PHANTOM_FLAP, 0.9f, 0.8f);
    }

    /** One of three T1 blades: half a heart and a small knockback. */
    private void firePlayerWindBlade(ServerPlayer player, ManaData data) {
        Level world = player.level();
        Vec3 look = player.getViewVector(1.0f);
        com.example.circlemage.entity.WindBladeEntity blade =
                new com.example.circlemage.entity.WindBladeEntity(world, player, 1.0f);
        blade.launch(look);
        world.addFreshEntity(blade);
        if (world instanceof ServerLevel serverWorld) {
            Vec3 muzzle = player.getEyePosition().add(look.scale(0.6)).add(0, -0.1, 0);
            serverWorld.sendParticles(ModParticles.GALE, muzzle.x, muzzle.y, muzzle.z, 10, 0.12, 0.12, 0.12, 0.05);
        }
        playSpellSound(player, SoundEvents.PHANTOM_FLAP, 0.6f, 1.5f);
    }

    // --- Dash ----------------------------------------------------------------

    /** Gate Dash on its own cost/cooldown, then dash toward the held movement input. */
    private void handleDash(ServerPlayer player, float forward, float strafe) {
        if(!player.isAlive()||player.isSpectator())return;
        ManaData check=ManaPlayer.get(player);
        if(!Float.isFinite(forward)||!Float.isFinite(strafe))return;
        if(!com.example.circlemage.spell.SpellCatalog.usable(check.getCircle(),check.getUnlockedSpells(),check.getBoundSpells(),check.isMagicLimitBroken(),Spell.DASH.id)){
            com.example.circlemage.util.Messages.send(player, Component.literal("Learn and bind Wind Escape at circle 2 in J first.").withStyle(ChatFormatting.RED),true);return;
        }
        if(player.hasEffect(com.example.circlemage.spell.RevampStatusEffects.ROOTED))return;
        forward=Math.max(-1,Math.min(1,forward));strafe=Math.max(-1,Math.min(1,strafe));
        if (isSpellSuppressed(player, Spell.DASH.id)) {
            return;
        }
        ManaData data = ManaPlayer.get(player);
        if (!isSpellKnown(data, Spell.DASH.id)) {
            com.example.circlemage.util.Messages.send(player, spellLockedMessage(Spell.DASH), true);
            return;
        }
        long now = player.level().getGameTime();
        Spell spell = Spell.DASH;

        int effectiveCooldown = (int) Math.round(spell.cooldownTicks * data.circleDiscount()
                * com.example.circlemage.item.WandItem.cooldownMultiplier(player, spell.id));
        long cooldownEnd = data.getCooldownEnd(spell.id);
        if (data.isCooldownsEnabled() && now < cooldownEnd) {
            double secondsLeft = (cooldownEnd - now) / 20.0;
            com.example.circlemage.util.Messages.send(player, Component.literal(String.format("Dash is on cooldown (%.1fs)", secondsLeft))
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        boolean channelingChant = data.isPassiveChantUnlocked()
                && PassiveChant.byId(data.getSelectedChant()) == PassiveChant.CHANNELING;
        double focusDiscount = holdingFocus(player) ? com.example.circlemage.item.ArcaneFocusItem.COST_MULT : 1.0;
        int effectiveCost = channelingChant ? 0 : (int) Math.round(spell.manaCost * data.circleDiscount()
                * focusDiscount * com.example.circlemage.item.WandItem.costMultiplier(player, spell.id));
        if (!data.trySpendMana(effectiveCost)) {
            com.example.circlemage.util.Messages.send(player, Component.literal("Not enough mana — " + effectiveCost + " required.")
                    .withStyle(ChatFormatting.RED), true);
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
    private void castDash(ServerPlayer player, float forward, float strafe) {
        // Use Minecraft's own movement-input -> world-velocity transform so the dash goes exactly where
        // the keys move the player: world.x = sideways*cos - forward*sin, world.z = forward*cos + sideways*sin.
        double yawRad = Math.toRadians(player.getYRot());
        double sin = Math.sin(yawRad);
        double cos = Math.cos(yawRad);
        double dx = strafe * cos - forward * sin;
        double dz = forward * cos + strafe * sin;
        Vec3 dir = new Vec3(dx, 0.0, dz);
        if (dir.lengthSqr() < 1.0e-4) {
            dir = new Vec3(-sin, 0.0, cos); // no movement key held -> dash forward
        }
        dir = dir.normalize();

        double power = 0.95; // dash strength reduced 50% (was 1.9; 1.0.8.1)
        player.setDeltaMovement(dir.x * power, 0.32, dir.z * power);
        player.syncVelocity = true;
        player.fallDistance = 0.0f;

        if (player.level() instanceof ServerLevel world) {
            Vec3 p = player.position();
            for (int i = 0; i < 14; i++) {
                world.sendParticles(ModParticles.GALE, p.x, p.y + 0.9, p.z, 1,
                        -dir.x * 0.4, 0.05, -dir.z * 0.4, 0.4);
            }
            world.sendParticles(ModParticles.ARCANE_TRAIL, p.x, p.y + 0.9, p.z, 8, 0.2, 0.4, 0.2, 0.05);
        }
        playSpellSound(player, SoundEvents.PHANTOM_FLAP, 0.8f, 1.6f);
    }

    // ------------------------------------------------------------------------
    // Combination magic
    // ------------------------------------------------------------------------

    /** True (and notifies the player) if a Boss Zone restriction suppresses this Spell id for the player. */
    private boolean isSpellSuppressed(ServerPlayer player, int spellId) {
        if (com.example.circlemage.boss.BossZoneManager.isSpellRestricted(player, spellId)) {
            com.example.circlemage.util.Messages.send(player, Component.literal("The domain suppresses certain forms of magic.")
                    .withStyle(ChatFormatting.LIGHT_PURPLE), true);
            return true;
        }
        return false;
    }

    private void handleCombine(ServerPlayer player, int primedId, int charge, int secondId) {
        // V2 combination is a separately selectable recipe, so it works with the one-T1 limit.
        if ((primedId==Spell.FIRE_BOLT.id && secondId==Spell.ICE_VOLLEY.id)
            || (primedId==Spell.ICE_VOLLEY.id && secondId==Spell.FIRE_BOLT.id))
            handleCast(player, Spell.STEAM_EXPLOSION.id, false, 0);
    }

    /** Draws the player's Thunder Mark (a sparking rune) while they're near it. */
    private void tickThunderMarkVisual(ServerPlayer player, ManaData data, long now) {
        if (!data.hasThunderMark() || now % 8 != 0) {
            return;
        }
        if (!(player.level() instanceof ServerLevel world)) {
            return;
        }
        String dim = world.dimension().identifier().toString();
        if (!dim.equals(data.getMarkDim())) {
            return;
        }
        double mx = data.getMarkX();
        double my = data.getMarkY();
        double mz = data.getMarkZ();
        if (player.distanceToSqr(mx, my, mz) > 64.0 * 64.0) {
            return;
        }
        int pts = 12;
        double r = 1.2;
        for (int i = 0; i < pts; i++) {
            double a = (Math.PI * 2.0 * i) / pts + now * 0.04;
            world.sendParticles(ModParticles.SPARK, mx + Math.cos(a) * r, my + 0.1, mz + Math.sin(a) * r, 1, 0, 0, 0, 0.0);
        }
        // Inner cross + occasional spark burst so it reads as a rune.
        world.sendParticles(ModParticles.SPARK, mx, my + 0.1, mz, 1, 0.4, 0.0, 0.4, 0.0);
        if (now % 32 == 0) {
            world.sendParticles(ModParticles.SPARK, mx, my + 0.3, mz, 8, 0.2, 0.5, 0.2, 0.25);
        }
    }

    /** Blaze flamethrower upkeep: drains mana, streams flame, burns entities in the cone. */
    private void tickBlaze(ServerPlayer player, ManaData data, long now) {
        if (now >= data.getBlazeEndTick()) {
            return;
        }
        if (!(player.level() instanceof ServerLevel world)) {
            return;
        }
        if (!data.trySpendMana(500.0 / 20.0)) { // 500 mana/sec, stops early if the mana runs out
            data.setBlazeEndTick(0);
            ModNetworking.syncToClient(player);
            return;
        }
        ModNetworking.syncToClient(player);

        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        double range = 6.0;
        for (double d = 0.6; d <= range; d += 0.5) {
            Vec3 p = eye.add(look.scale(d));
            world.sendParticles(ModParticles.EMBER, p.x, p.y, p.z, 2, 0.15, 0.15, 0.15, 0.03);
            world.sendParticles(ModParticles.SMOKE, p.x, p.y, p.z, 1, 0.1, 0.1, 0.1, 0.01);
        }
        AABB box = player.getBoundingBox().inflate(range);
        for (LivingEntity e : world.getEntitiesOfClass(LivingEntity.class, box, x -> x != player && x.isAlive())) {
            Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            double forward = to.dot(look);
            if (forward <= 0 || forward > range) {
                continue;
            }
            if (to.subtract(look.scale(forward)).length() > 1.6) {
                continue;
            }
            e.igniteForSeconds(4);
            if (now % 4 == 0) {
                e.hurt(player.damageSources().magic(), 3.0f);
            }
        }
        if (now % 4 == 0) {
            world.playSound(null, eye.x, eye.y, eye.z, SoundEvents.BLAZE_AMBIENT, SoundSource.PLAYERS, 0.5f, 1.3f);
        }
    }

    /** Divine Wrath: a tracking pillar of light that hammers the locked target for ~3 seconds. */
    private void tickDivineWrath(ServerPlayer player, ManaData data, long now) {
        if (now >= data.getDivineWrathEndTick()) {
            return;
        }
        if (!(player.level() instanceof ServerLevel world)) {
            return;
        }
        Entity ent = world.getEntity(data.getDivineWrathTargetId());
        if (!(ent instanceof LivingEntity target) || !target.isAlive()) {
            data.setDivineWrath(-1, 0);
            return;
        }
        Vec3 tp = target.position();
        double top = tp.y + 18.0;
        for (double y = tp.y; y <= top; y += 0.4) {
            world.sendParticles(ModParticles.LIGHT, tp.x, y, tp.z, 1, 0.12, 0.0, 0.12, 0.0);
            if (((int) (y * 2)) % 2 == 0) {
                world.sendParticles(ModParticles.RADIANCE, tp.x, y, tp.z, 1, 0.18, 0.0, 0.18, 0.0);
            }
        }
        if (now % 4 == 0) {
            for (double ring = 1.0; ring <= 3.0; ring += 1.0) {
                int pts = (int) (ring * 8);
                for (int i = 0; i < pts; i++) {
                    double a = (Math.PI * 2.0 * i) / pts;
                    world.sendParticles(ModParticles.RADIANCE, tp.x + Math.cos(a) * ring, tp.y + 0.2, tp.z + Math.sin(a) * ring,
                            1, 0, 0, 0, 0.0);
                }
            }
        }
        if (now % 10 == 0) {
            target.hurt(player.damageSources().indirectMagic(player, player), 3.0f);
        }
        if (now % 20 == 0) {
            world.playSound(null, tp.x, tp.y, tp.z, SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 1.0f, 1.6f);
            world.playSound(null, tp.x, tp.y, tp.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.5f, 0.7f);
        }
    }

    // ------------------------------------------------------------------------
    // Laws (9th-circle ultimate system)
    // ------------------------------------------------------------------------

    /** True if the player may use the Laws (unlocked + 9th circle). */
    private boolean canUseLaws(ManaData data) {
        return data.isLawsUnlocked() && data.getCircle() >= Circles.MAX_CIRCLE;
    }

    private void tickLawsUnlock(ServerPlayer player, ManaData data, long now) {
        if (data.isLawsUnlocked() || now % 20 != 0) {
            return;
        }
        if (player.level().getRandom().nextDouble() < LAWS_UNLOCK_CHANCE) {
            LawUnlocks.unlockBase(player, data);
        }
    }

    private void selectLaw(ServerPlayer player, int lawId) {
        ManaData data = ManaPlayer.get(player);
        Law law = Law.byId(lawId);
        if (!data.isLawsUnlocked() || !data.isLawUnlocked(law.ordinal())) {
            com.example.circlemage.util.Messages.send(player, Component.literal("That Law is not unlocked.").withStyle(ChatFormatting.RED), true);
            return;
        }
        data.setSelectedLaw(law.ordinal());
        com.example.circlemage.util.Messages.send(player, Component.literal("Active Law: " + law.displayName).withStyle(ChatFormatting.LIGHT_PURPLE), true);
        ModNetworking.syncToClient(player);
    }

    /** The selected law's ongoing per-tick effect, plus the global time-stop upkeep. */
    private void tickActiveLaw(ServerPlayer player, ManaData data, long now) {
        if (!(player.level() instanceof ServerLevel world)) {
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

    private boolean drainLawPerSecond(ServerPlayer player, ManaData data, long now, double perSecond) {
        if (now % 20 != 0) {
            return true;
        }
        if (!data.trySpendMana(perSecond)) {
            data.setSelectedLaw(Law.NONE.ordinal());
            com.example.circlemage.util.Messages.send(player, Component.literal("Your Law fades — not enough mana.").withStyle(ChatFormatting.RED), true);
            ModNetworking.syncToClient(player);
            return false;
        }
        ModNetworking.syncToClient(player);
        return true;
    }

    private void tickGravityAmp(ServerLevel world, ServerPlayer player, ManaData data, long now) {
        double range = 10.0;
        Vec3 c = player.position();
        AABB box = player.getBoundingBox().inflate(range);
        boolean affecting = false;
        for (LivingEntity e : world.getEntitiesOfClass(LivingEntity.class, box, x -> x != player && x.isAlive())) {
            double dist = e.position().distanceTo(c);
            if (dist > range) {
                continue;
            }
            affecting = true;
            int stacks = (int) Math.min(10, Math.max(1, Math.ceil(range - dist)));
            if (stacks >= 10) {
                e.hurt(world.damageSources().cramming(), 1000.0f); // crushed
            } else {
                e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 10, Math.min(9, stacks), false, false, true));
                e.push(0.0, -0.08 * stacks, 0.0);
                e.syncVelocity = true;
            }
        }
        if (affecting) {
            if (now % 4 == 0) {
                world.sendParticles(ModParticles.VOID, c.x, c.y + 1.0, c.z, 6, range * 0.4, 0.6, range * 0.4, 0.02);
            }
            drainLawPerSecond(player, data, now, 2000.0);
        }
    }

    private static final int BLACK_HOLE_RANGE = 30;

    /** Black Hole: drags loose (already-broken) items in from up to 30 blocks and collects them. */
    private void tickBlackHole(ServerLevel world, ServerPlayer player, ManaData data, long now) {
        if (!drainLawPerSecond(player, data, now, 250.0)) {
            return;
        }
        Vec3 center = player.position();
        AABB box = player.getBoundingBox().inflate(BLACK_HOLE_RANGE);
        for (ItemEntity item : world.getEntitiesOfClass(ItemEntity.class, box, ItemEntity::isAlive)) {
            Vec3 pull = center.subtract(item.position());
            double dist = pull.length();
            if (dist > BLACK_HOLE_RANGE) {
                continue;
            }
            if (dist < 1.2) {
                // Close enough — vacuum straight into the inventory.
                ItemStack stack = item.getItem();
                if (!stack.isEmpty()) {
                    giveOrDrop(player, stack.copy());
                    item.discard();
                }
            } else {
                Vec3 v = pull.normalize().scale(0.6);
                item.setDeltaMovement(v.x, v.y + 0.05, v.z);
                item.syncVelocity = true;
                item.setPickUpDelay(0);
            }
        }
        if (now % 4 == 0) {
            world.sendParticles(ModParticles.VOID, player.getX(), player.getY() + 1.0, player.getZ(), 10, 1.0, 1.0, 1.0, 0.0);
        }
    }

    private void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack) && !stack.isEmpty()) {
            ItemEntity item = new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), stack);
            player.level().addFreshEntity(item);
        }
    }

    private void tickLoveOfWorld(ServerLevel world, ServerPlayer player, ManaData data, long now) {
        if (!drainLawPerSecond(player, data, now, 4000.0)) {
            return;
        }
        if (now % 30 == 0) {
            player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 60, 0, false, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, false, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 60, 0, false, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.HASTE, 60, 0, false, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.SPEED, 60, 0, false, false, true));
        }
        // Regeneration I, applied only when missing so its heal ticks actually fire.
        MobEffectInstance regen = player.getEffect(MobEffects.REGENERATION);
        if (regen == null || regen.getDuration() <= 1) {
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0, false, false, true));
        }
        // "feather falling" is handled in LivingEntityMixin (fall damage is negated).
    }

    private void tickTimeStop(ServerLevel world, ServerPlayer player, ManaData data, long now) {
        if (now < data.getTimeStopEndTick()) {
            AABB box = player.getBoundingBox().inflate(200.0);
            for (Entity e : world.getEntities(player, box)) {
                if (e instanceof ServerPlayer) {
                    continue;
                }
                e.setDeltaMovement(Vec3.ZERO);
                e.syncVelocity = true;
                if (e instanceof Mob mob && !mob.isNoAi()) {
                    mob.setNoAi(true);
                    data.getFrozenMobs().add(mob.getId());
                }
            }
        } else if (!data.getFrozenMobs().isEmpty()) {
            for (int id : data.getFrozenMobs()) {
                if (world.getEntity(id) instanceof Mob mob) {
                    mob.setNoAi(false);
                }
            }
            data.getFrozenMobs().clear();
        }
    }

    /** Triggered by the "Activate Law" key. */
    private void activateLaw(ServerPlayer player) {
        ManaData data = ManaPlayer.get(player);
        if (!data.isLawsUnlocked()) {
            return;
        }
        if (data.getCircle() < Circles.MAX_CIRCLE) {
            com.example.circlemage.util.Messages.send(player, Component.literal("Laws can only be wielded at the 9th circle.").withStyle(ChatFormatting.RED), true);
            return;
        }
        ServerLevel world = (ServerLevel) player.level();
        switch (Law.byId(data.getSelectedLaw())) {
            case SPATIAL_REND -> castSpatialRend(world, player, data);
            case TIME_STOP -> castTimeStop(world, player, data);
            case TIME_REVERSAL -> castReversal(world, player, data);
            default -> com.example.circlemage.util.Messages.send(player, Component.literal("This Law has no activated effect.").withStyle(ChatFormatting.GRAY), true);
        }
    }

    private void castSpatialRend(ServerLevel world, ServerPlayer player, ManaData data) {
        if (!data.trySpendMana(10000.0)) {
            com.example.circlemage.util.Messages.send(player, Component.literal("Not enough mana — 10000 required.").withStyle(ChatFormatting.RED), true);
            return;
        }
        Vec3 look = player.getViewVector(1.0f);
        SpatialRendEntity rend = new SpatialRendEntity(world, player, look);
        world.addFreshEntity(rend);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 2.0f, 1.4f);
        ModNetworking.syncToClient(player);
    }

    private void castTimeStop(ServerLevel world, ServerPlayer player, ManaData data) {
        if (!data.trySpendMana(10000.0)) {
            com.example.circlemage.util.Messages.send(player, Component.literal("Not enough mana — 10000 required.").withStyle(ChatFormatting.RED), true);
            return;
        }
        data.setTimeStopEndTick(world.getGameTime() + 100); // 5 seconds
        world.sendParticles(ModParticles.RADIANCE, player.getX(), player.getY() + 1.0, player.getZ(), 60, 3.0, 2.0, 3.0, 0.0);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.4f, 0.3f);
        com.example.circlemage.util.Messages.send(player, Component.literal("Time, be still.").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC), false);
        ModNetworking.syncToClient(player);
    }

    private void castReversal(ServerLevel world, ServerPlayer player, ManaData data) {
        if (!data.trySpendMana(2000.0)) {
            com.example.circlemage.util.Messages.send(player, Component.literal("Not enough mana — 2000 required.").withStyle(ChatFormatting.RED), true);
            return;
        }
        player.setHealth(player.getMaxHealth());
        world.sendParticles(ModParticles.RADIANCE, player.getX(), player.getY(0.5), player.getZ(), 40, 0.5, 1.0, 0.5, 0.05);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.6f);
        ModNetworking.syncToClient(player);
    }

    private void openStorage(ServerPlayer player) {
        ManaData data = ManaPlayer.get(player);
        if (!canUseLaws(data) || Law.byId(data.getSelectedLaw()) != Law.STORAGE) {
            com.example.circlemage.util.Messages.send(player, Component.literal("Select the Storage law (at the 9th circle) first.").withStyle(ChatFormatting.RED), true);
            return;
        }
        com.example.circlemage.mana.StorageInventory inv = new com.example.circlemage.mana.StorageInventory(data, player.registryAccess());
        player.openMenu(new SimpleMenuProvider(
                (syncId, playerInv, p) -> ChestMenu.threeRows(syncId, playerInv, inv),
                Component.literal("Spatial Storage")));
    }

    private void selectForm(ServerPlayer player, int baseSpellId, boolean revert) {
        if (com.example.circlemage.spell.SpellCatalog.entry(baseSpellId) != null) return; // Legacy forms are not player magic in V2.
        ManaData data = ManaPlayer.get(player);
        Spell base = Spell.byId(baseSpellId);
        if (base == Spell.MAGIC_MISSILE) {
            if (data.getCircle() < 5) {
                return;
            }
            data.setRevertSpear(revert);
            com.example.circlemage.util.Messages.send(player, Component.literal("Bolt form: " + (revert ? "Magic Missile" : "Spear of Light"))
                    .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        } else if (base == Spell.AREA_SLOW) {
            if (data.getCircle() < 6) {
                return;
            }
            data.setRevertDarkOrb(revert);
            com.example.circlemage.util.Messages.send(player, Component.literal("Slow form: " + (revert ? "Area Slow" : "Dark Orb"))
                    .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        } else {
            return;
        }
        ModNetworking.syncToClient(player);
    }

    /** The incantation a player "says" when casting an enhanced (active-chant) spell. */
    private net.minecraft.network.chat.MutableComponent getChantText(Spell spell) {
        String line = switch (spell) {
            case MAGIC_MISSILE -> "By piercing light, strike true!";
            case SPEAR_OF_LIGHT -> "Lance of dawn, pierce the veil!";
            case FIREBALL -> "Let the world burn!";
            case AREA_SLOW -> "Time itself, be still.";
            case DARK_ORB -> "Devour all, sphere of night.";
            case THUNDER -> "Heaven's wrath, descend!";
            case WALL_OF_WIND -> "Howling gale, repel all!";
            default -> "";
        };
        return Component.literal("\"" + line + "\"");
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
            case DASH -> "Wind Escape";
            case WIND_BLADE -> "Wind Blades";
            case FIRE_BOLT -> "Fireball";
            default -> { var e=com.example.circlemage.spell.SpellCatalog.entry(spell.id); yield e==null?spell.name():e.name(); }
        };
    }

    /** True if the player wields an Arcane Focus in either hand (-15% spell cost, +10% damage). */
    public static boolean holdingFocus(net.minecraft.world.entity.player.Player player) {
        return player.getMainHandItem().is(com.example.circlemage.item.ModItems.ARCANE_FOCUS)
                || player.getOffhandItem().is(com.example.circlemage.item.ModItems.ARCANE_FOCUS);
    }

    /** Passive ATTACK chant multiplies spell damage by 1.30; an Arcane Focus adds another 1.10x;
     *  a held wand's core (v2.2) can add more for its school. */
    private float attackChantMultiplier(ServerPlayer player, ManaData data, int spellId) {
        float mult = (data.isPassiveChantUnlocked()
                && PassiveChant.byId(data.getSelectedChant()) == PassiveChant.ATTACK) ? 1.30f : 1.0f;
        if (holdingFocus(player)) {
            mult *= com.example.circlemage.item.ArcaneFocusItem.DAMAGE_MULT;
        }
        mult *= com.example.circlemage.item.WandItem.damageMultiplier(player, spellId);
        return mult;
    }

    private void selectChant(ServerPlayer player, int chantId) {
        ManaData data = ManaPlayer.get(player);
        if (!data.isPassiveChantUnlocked()) {
            return;
        }
        setChant(player, data, PassiveChant.byId(chantId));
        com.example.circlemage.util.Messages.send(player, Component.literal("Passive chant: " + PassiveChant.byId(data.getSelectedChant()).displayName)
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    private void setChant(ServerPlayer player, ManaData data, PassiveChant chant) {
        data.setSelectedChant(chant.ordinal());
        applyReinforceSpeed(player, chant == PassiveChant.REINFORCE);
        applyVitality(player, chant == PassiveChant.VITALITY);
        ModNetworking.syncToClient(player);
    }

    private void applyReinforceSpeed(ServerPlayer player, boolean active) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        if (active) {
            if (!speed.hasModifier(REINFORCE_SPEED_MODIFIER.id())) {
                speed.addTransientModifier(REINFORCE_SPEED_MODIFIER);
            }
        } else {
            speed.removeModifier(REINFORCE_SPEED_UUID);
            speed.removeModifier(Identifier.withDefaultNamespace("2a7c9e10-5b4d-4f2a-8c3e-1d6f9b0a4e72"));
        }
    }

    private void applyVitality(ServerPlayer player, boolean active) {
        // Vitality no longer grants bonus hearts (1.0.8.3). Always strip the legacy +health modifier so
        // any player who still carries it from an older version reverts to normal max health.
        AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.removeModifier(VITALITY_HEALTH_UUID);
            health.removeModifier(Identifier.withDefaultNamespace("4f1b8d3e-6c2a-4e9b-9f1d-3a7c5e0b2d84"));
        }
    }

    /** Rolls that fire on every successful magic use. */
    private void doMagicUseRolls(ServerPlayer player, ManaData data) {
        Level world = player.level();

        // Each successful cast after the guardian epiphany has a 5% insight chance.
        if (data.hasAntiMagicEpiphany() && !data.isSpellUnlocked(Spell.CLEAR_MAGIC.id) && world.getRandom().nextDouble() < ANTI_MAGIC_CHANCE) {
            data.unlockSpell(Spell.CLEAR_MAGIC.id);
            grantAdvancement(player, ANTI_MAGIC_ADVANCEMENT);
            com.example.circlemage.util.Messages.send(player, Component.literal("Insight becomes understanding: Dispel unlocked! The 4th-circle barrier is broken.").withStyle(ChatFormatting.GOLD),false);
            ModNetworking.sendJournalSync(player);
        }
        // A 5% chance to permanently unlock the current realm's buff (2x mana, 1.5x damage).
        if (data.getCircle() >= 1 && !data.isRealmBuffUnlocked(data.getCircle())
                && world.getRandom().nextDouble() < REALM_BUFF_CHANCE) {
            if (data.unlockRealmBuff(data.getCircle())) {
                com.example.circlemage.util.Messages.send(player, Component.literal("The power of the " + data.getCircle()
                                + " circle flows through you permanently! (+2x mana, +1.2x magic damage)")
                        .withStyle(ChatFormatting.GOLD), false);
            }
        }
        // From the 3rd circle, a 1% chance to unlock the Magic Array system.
        if (data.getCircle() >= 3 && !data.isArrayUnlocked() && world.getRandom().nextDouble() < ARRAY_UNLOCK_CHANCE) {
            data.setArrayUnlocked(true);
            grantAdvancement(player, ARRAY_ADVANCEMENT);
            com.example.circlemage.util.Messages.send(player, Component.literal("You have unlocked Magic Arrays! Press K to choose an array, then C to carve it.")
                    .withStyle(ChatFormatting.GOLD), false);
        }
        // From the 4th circle, a 5% chance to unlock Passive Chants.
        if (data.getCircle() >= 4 && !data.isPassiveChantUnlocked() && world.getRandom().nextDouble() < CHANT_UNLOCK_CHANCE) {
            data.setPassiveChantUnlocked(true);
            com.example.circlemage.util.Messages.send(player, Component.literal("You have unlocked Passive Chants! Press K to choose one.")
                    .withStyle(ChatFormatting.GOLD), false);
        }
        // From the 5th circle, a 2% chance to learn Combination Magic (required to reach the 6th circle).
        if (data.getCircle() >= 5 && !hasAdvancement(player, COMBINATION_ADVANCEMENT)
                && world.getRandom().nextDouble() < COMBO_ADV_CHANCE) {
            grantAdvancement(player, COMBINATION_ADVANCEMENT);
        }
    }

    // --- Magic Missile -------------------------------------------------------

    private void castMagicMissile(ServerPlayer player, ManaData data, boolean enhanced) {
        Level world = player.level();
        Vec3 look = player.getViewVector(1.0f);
        Vec3 cursorPos = player.pick(50.0, 1.0f, false).getLocation();

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
        world.addFreshEntity(missile);

        // Muzzle flash at the casting hand.
        if (world instanceof ServerLevel serverWorld) {
            Vec3 muzzle = player.getEyePosition().add(look.scale(0.6)).add(0, -0.1, 0);
            serverWorld.sendParticles(ModParticles.ARCANE, muzzle.x, muzzle.y, muzzle.z, 8, 0.1, 0.1, 0.1, 0.02);
            serverWorld.sendParticles(ModParticles.ARCANE_TRAIL, muzzle.x, muzzle.y, muzzle.z, 3, 0.05, 0.05, 0.05, 0.01);
        }

        playSpellSound(player, SoundEvents.ILLUSIONER_CAST_SPELL, 0.7f, 1.4f);
    }

    // --- Spear of Light (5th-circle Magic Missile transform) -----------------

    private void castSpearOfLight(ServerPlayer player, ManaData data, boolean enhanced, boolean fullyCharged) {
        Level world = player.level();
        Vec3 look = player.getViewVector(1.0f);
        Vec3 cursorPos = player.pick(50.0, 1.0f, false).getLocation();
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
        world.addFreshEntity(spear);

        if (world instanceof ServerLevel serverWorld) {
            Vec3 muzzle = player.getEyePosition().add(look.scale(0.6)).add(0, -0.1, 0);
            serverWorld.sendParticles(ModParticles.LIGHT, muzzle.x, muzzle.y, muzzle.z, 12, 0.12, 0.12, 0.12, 0.03);
        }
        playSpellSound(player, SoundEvents.ILLUSIONER_CAST_SPELL, 0.9f, 1.9f);
    }

    private LivingEntity findTargetNearCursor(Level world, ServerPlayer player, Vec3 cursorPos) {
        AABB searchBox = new AABB(cursorPos.subtract(1.0, 1.0, 1.0), cursorPos.add(1.0, 1.0, 1.0));
        List<LivingEntity> candidates = world.getEntitiesOfClass(LivingEntity.class, searchBox,
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

    private void castFireball(ServerPlayer player, ManaData data, boolean enhanced) {
        ServerLevel world = (ServerLevel) player.level();
        Vec3 cursor = player.pick(50.0, 1.0f, false).getLocation();

        // Base: 3-block radius, 5s. Enhanced: +2 radius and extra burn damage per second.
        double radius = enhanced ? 5.0 : 3.0;
        float fireBonus = enhanced ? (1.0f * attackChantMultiplier(player, data, Spell.FIREBALL.id)) : 0.0f;
        AreaEffectManager.add(new AreaEffect(world, cursor, radius, AreaEffect.Type.FIRE, 100, 0, fireBonus));

        // Bloom of embers from the impact point plus a bright arcane flash.
        world.sendParticles(ModParticles.EMBER, cursor.x, cursor.y + 0.3, cursor.z,
                (int) (radius * 14), radius * 0.4, 0.4, radius * 0.4, 0.06);
        world.sendParticles(ModParticles.ARCANE_TRAIL, cursor.x, cursor.y + 0.3, cursor.z, 12, 0.3, 0.3, 0.3, 0.12);
        world.playSound(null, cursor.x, cursor.y, cursor.z,
                SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    // --- Area Slow -----------------------------------------------------------

    private void castAreaSlow(ServerPlayer player, boolean enhanced) {
        ServerLevel world = (ServerLevel) player.level();
        Vec3 cursor = player.pick(50.0, 1.0f, false).getLocation();

        // 10-block radius slow field, 8s. Base Slowness I; enhanced Slowness II.
        int amplifier = enhanced ? 1 : 0;
        AreaEffectManager.add(new AreaEffect(world, cursor, 10.0, AreaEffect.Type.SLOW, 160, amplifier, 0.0f));

        world.playSound(null, cursor.x, cursor.y, cursor.z,
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 0.6f);
    }

    // --- Dark Orb (6th-circle Area Slow transform) ---------------------------

    private void castDarkOrb(ServerPlayer player, ManaData data, boolean enhanced, boolean fullyCharged) {
        Level world = player.level();
        Vec3 look = player.getViewVector(1.0f);

        // enhanced (active chant) -> floating orb that waits to be hit; otherwise a thrown orb.
        DarkOrbEntity orb = new DarkOrbEntity(world, player, enhanced, fullyCharged);
        orb.launch(look);
        world.addFreshEntity(orb);

        if (world instanceof ServerLevel serverWorld) {
            Vec3 muzzle = player.getEyePosition().add(look.scale(0.6)).add(0, -0.1, 0);
            serverWorld.sendParticles(ModParticles.VOID, muzzle.x, muzzle.y, muzzle.z, 12, 0.12, 0.12, 0.12, 0.03);
        }
        if (enhanced) {
            com.example.circlemage.util.Messages.send(player, Component.literal("The void sphere hangs in the air, waiting to be struck...")
                    .withStyle(ChatFormatting.DARK_PURPLE), true);
        }
        playSpellSound(player, SoundEvents.BEACON_DEACTIVATE, 0.8f, 0.5f);
    }

    // --- Anti Magic / Clear --------------------------------------------------

    private void castClearMagic(ServerPlayer player, ManaData data, long now) {
        com.example.circlemage.entity.ArcaneEffectEntity.dispel(player.level(),player);
        cleanseNegativeEffects(player);
        player.setRemainingFireTicks(0);
        int cleared = AreaEffectManager.clearAffecting(player);
        data.setImmunityEndTick(now + ANTI_MAGIC_IMMUNITY_TICKS);

        ServerLevel world = (ServerLevel) player.level();
        // Radiant pulse that washes outward from the caster.
        world.sendParticles(ModParticles.RADIANCE, player.getX(), player.getY(0.5), player.getZ(),
                50, 0.5, 1.0, 0.5, 0.06);
        world.sendParticles(ModParticles.ARCANE_TRAIL, player.getX(), player.getY(0.5), player.getZ(),
                16, 0.6, 1.0, 0.6, 0.04);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.8f, 1.6f);

        com.example.circlemage.util.Messages.send(player, Component.literal("Anti-magic surge! Cleared " + cleared
                        + " field(s); immune to negative effects for 5s.")
                .withStyle(ChatFormatting.AQUA), false);
    }

    // --- Wall of Wind --------------------------------------------------------

    private void castWallOfWind(ServerPlayer player, boolean enhanced, boolean fullyCharged) {
        Level world = player.level();
        Vec3 eye = player.getEyePosition();
        double strength = enhanced ? 2.5 * 1.5 : 2.5; // enhanced: +50% knockback

        // Full charge: a cyclone that blasts in every direction instead of just the look direction.
        if (fullyCharged) {
            double range = 10.0;
            AABB box = player.getBoundingBox().inflate(range);
            for (LivingEntity living : world.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive())) {
                Vec3 to = living.position().subtract(player.position());
                Vec3 outward = new Vec3(to.x, 0.0, to.z);
                if (outward.lengthSqr() > range * range) {
                    continue;
                }
                if (outward.lengthSqr() < 1.0e-4) {
                    outward = new Vec3(world.getRandom().nextDouble() - 0.5, 0, world.getRandom().nextDouble() - 0.5);
                }
                Vec3 push = outward.normalize().scale(strength).add(0, 0.5, 0);
                living.push(push.x, push.y, push.z);
                living.syncVelocity = true;
            }
            if (world instanceof ServerLevel serverWorld) {
                for (int i = 0; i < 36; i++) {
                    double ang = (Math.PI * 2.0 * i) / 36.0;
                    Vec3 dir = new Vec3(Math.cos(ang), 0, Math.sin(ang));
                    for (double f = 1.0; f <= range; f += 2.0) {
                        serverWorld.sendParticles(ModParticles.GALE,
                                eye.x + dir.x * f, eye.y - 0.3 + world.getRandom().nextDouble() * 1.4, eye.z + dir.z * f,
                                1, dir.x * strength * 0.3, 0.02, dir.z * strength * 0.3, 0.6);
                    }
                }
                serverWorld.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 1.4f, 0.5f);
            }
            return;
        }

        Vec3 look = player.getViewVector(1.0f);
        Vec3 horiz = new Vec3(look.x, 0.0, look.z);
        if (horiz.lengthSqr() < 1.0e-4) {
            horiz = new Vec3(0, 0, 1);
        }
        horiz = horiz.normalize();

        double range = 10.0;     // how far the wall reaches
        double halfWidth = 2.5;  // 5 blocks wide

        AABB box = player.getBoundingBox().inflate(range);
        for (LivingEntity living : world.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive())) {
            Vec3 to = living.position().subtract(eye);
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
            Vec3 push = horiz.scale(strength).add(0, 0.45, 0);
            living.push(push.x, push.y, push.z);
            living.syncVelocity = true;
        }

        // Enhanced wall recoils the caster too, but much less than the target.
        if (enhanced) {
            Vec3 recoil = horiz.scale(-strength * 0.35).add(0, 0.15, 0);
            player.push(recoil.x, recoil.y, recoil.z);
            player.syncVelocity = true;
        }

        if (world instanceof ServerLevel serverWorld) {
            // A visible gale sweeping along the wall: gusts march outward across its full width.
            Vec3 perp = new Vec3(-horiz.z, 0.0, horiz.x);
            Vec3 gustVel = horiz.scale(strength * 0.35);
            for (double f = 1.0; f <= range; f += 1.0) {
                for (double w = -halfWidth; w <= halfWidth; w += 1.25) {
                    double gx = eye.x + horiz.x * f + perp.x * w;
                    double gy = eye.y - 0.3 + world.getRandom().nextDouble() * 1.4;
                    double gz = eye.z + horiz.z * f + perp.z * w;
                    serverWorld.sendParticles(ModParticles.GALE, gx, gy, gz, 1,
                            gustVel.x, 0.02, gustVel.z, 0.6);
                }
            }
            serverWorld.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 1.2f, 0.7f);
        }
    }

    // --- Thunder -------------------------------------------------------------

    private void castThunder(ServerPlayer player, boolean enhanced, boolean fullyCharged) {
        ServerLevel world = (ServerLevel) player.level();
        Vec3 cursor = player.pick(50.0, 1.0f, false).getLocation();

        // Base 1 bolt; active chant 3; fully charged 6.
        int bolts = fullyCharged ? 6 : (enhanced ? 3 : 1);
        strikeLightning(world, player, cursor.x, cursor.y, cursor.z);
        int extra = bolts - 1;
        for (int i = 0; i < extra; i++) {
            double angle = (Math.PI * 2.0 * i) / extra;
            strikeLightning(world, player, cursor.x + Math.cos(angle) * 1.4, cursor.y, cursor.z + Math.sin(angle) * 1.4);
        }
    }

    private void strikeLightning(ServerLevel world, ServerPlayer player, double x, double y, double z) {
        LightningBolt bolt = net.minecraft.world.entity.EntityTypes.LIGHTNING_BOLT.create(world, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        if (bolt != null) {
            bolt.snapTo(x, y, z);
            bolt.setCause(player);
            world.addFreshEntity(bolt);
        }
        // Vanilla lightning has no attacker, so it wouldn't aggro mobs. Mark the player as the
        // attacker on everything near the strike so the Thunder reads as a player attack.
        AABB box = new AABB(x - 4.0, y - 4.0, z - 4.0, x + 4.0, y + 4.0, z + 4.0);
        for (LivingEntity living : world.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && e.isAlive())) {
            living.setLastHurtByMob(player);
            if (living instanceof Mob mob) {
                mob.setTarget(player);
            }
        }
    }

    // --- Barrier -------------------------------------------------------------

    private void castBarrier(ServerPlayer player, boolean dome) {
        ServerLevel world = (ServerLevel) player.level();

        if (dome) {
            ManaData data = ManaPlayer.get(player);
            // Replace any existing dome.
            if (data.getActiveDomeId() >= 0) {
                Entity old = world.getEntity(data.getActiveDomeId());
                if (old != null) {
                    old.discard();
                }
            }
            MagicBarrierEntity shield = new MagicBarrierEntity(ModEntities.MAGIC_BARRIER, world);
            shield.setPos(player.getX(), player.getY() + 1.0, player.getZ());
            shield.initDome(player, 200, 5); // 10s timer / 5 hits
            world.addFreshEntity(shield);
            data.setActiveDomeId(shield.getId());
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8f, 1.5f);
            com.example.circlemage.util.Messages.send(player, Component.literal("A dodecahedral shield envelops you (absorbs 5 hits / 10s; "
                            + "hold sneak to sustain it at 100 mana/s).").withStyle(ChatFormatting.AQUA), false);
            return;
        }

        // Wall: a 5x5x1 barrier facing the player, up to 6 blocks out.
        Vec3 look = player.getViewVector(1.0f);
        Vec3 horiz = new Vec3(look.x, 0.0, look.z);
        if (horiz.lengthSqr() < 1.0e-4) {
            horiz = new Vec3(0, 0, 1);
        }
        horiz = horiz.normalize();

        Vec3 cursor = player.pick(6.0, 1.0f, false).getLocation();
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
        wall.setPos(cx, cy, cz);
        wall.initWall(player, halfX, halfY, halfZ, 140); // 7s
        world.addFreshEntity(wall);
        world.playSound(null, cx, cy, cz, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.7f, 1.1f);
    }

    // --- Magic Array carving -------------------------------------------------

    private void selectArray(ServerPlayer player, int arrayId) {
        ManaData data = ManaPlayer.get(player);
        if (!data.isArrayUnlocked()) {
            return;
        }
        ArrayType type = ArrayType.byId(arrayId);
        data.setSelectedArray(type.ordinal());
        ModNetworking.syncToClient(player);
        com.example.circlemage.util.Messages.send(player, Component.literal("Selected array: " + type.displayName)
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    private void startCarving(ServerPlayer player) {
        ManaData data = ManaPlayer.get(player);
        long now = player.level().getGameTime();

        if (!data.isArrayUnlocked()) {
            com.example.circlemage.util.Messages.send(player, Component.literal("Magic Arrays are still locked. Reach the 3rd circle and keep casting.")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        if (data.isCarving()) {
            return;
        }
        if (data.isCooldownsEnabled() && now < data.getCarveCooldownEnd()) {
            double secondsLeft = (data.getCarveCooldownEnd() - now) / 20.0;
            com.example.circlemage.util.Messages.send(player, Component.literal(String.format("Carving on cooldown (%.1fs)", secondsLeft))
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        if (data.getCurrentMana() < CARVE_COST) {
            com.example.circlemage.util.Messages.send(player, Component.literal("Not enough mana — " + CARVE_COST + " required to carve.")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }

        data.setCarving(true);
        data.setCarveEndTick(now + CARVE_DURATION_TICKS);
        data.setCarveStart(player.getX(), player.getY(), player.getZ());
        com.example.circlemage.util.Messages.send(player, Component.literal("Carving array... hold still.").withStyle(ChatFormatting.AQUA), true);
    }

    private void tickCarving(ServerPlayer player, ManaData data, long now) {
        // Interrupt on movement.
        if (data.carveMovedSquared(player.getX(), player.getY(), player.getZ()) > CARVE_MOVE_THRESHOLD_SQ) {
            data.setCarving(false);
            data.setCarveCooldownEnd(now + CARVE_CANCEL_COOLDOWN);
            com.example.circlemage.util.Messages.send(player, Component.literal("Carving interrupted! (3s cooldown)").withStyle(ChatFormatting.RED), true);
            return;
        }

        // Progress particles: arcane motes spiral inward toward the carver's hands.
        if (player.level() instanceof ServerLevel serverWorld) {
            double prog = (double) (now - (data.getCarveEndTick() - CARVE_DURATION_TICKS)) / CARVE_DURATION_TICKS;
            double ringR = 1.4 * (1.0 - Math.max(0.0, Math.min(1.0, prog))) + 0.3;
            for (int i = 0; i < 3; i++) {
                double a = serverWorld.getRandom().nextDouble() * Math.PI * 2.0;
                serverWorld.sendParticles(ModParticles.ARCANE,
                        player.getX() + Math.cos(a) * ringR, player.getY() + 0.1,
                        player.getZ() + Math.sin(a) * ringR, 1, 0.0, 0.05, 0.0, 0.0);
            }
        }

        if (now >= data.getCarveEndTick()) {
            data.setCarving(false);
            if (!data.trySpendMana(CARVE_COST)) {
                com.example.circlemage.util.Messages.send(player, Component.literal("Not enough mana to finish carving.").withStyle(ChatFormatting.RED), true);
                return;
            }
            data.setCarveCooldownEnd(now + CARVE_SUCCESS_COOLDOWN);
            createArray(player, data);
            ModNetworking.syncToClient(player);
        }
    }

    private void createArray(ServerPlayer player, ManaData data) {
        ServerLevel world = (ServerLevel) player.level();
        ArrayType type = ArrayType.byId(data.getSelectedArray());
        Vec3 center = computeArrayCenter(player);
        MagicArrayManager.add(new MagicArray(world, center, type, ARRAY_LIFETIME_TICKS));
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 1.0f, 1.2f);
        com.example.circlemage.util.Messages.send(player, Component.literal("Carved a " + type.displayName + " array (5-block radius).")
                .withStyle(ChatFormatting.LIGHT_PURPLE), false);
    }

    /**
     * The array forms in the direction the player is looking, between 3 and 5 blocks away, so it is
     * never carved under the player and never further than 5 blocks out.
     */
    private Vec3 computeArrayCenter(ServerPlayer player) {
        Vec3 look = player.getViewVector(1.0f);
        Vec3 horiz = new Vec3(look.x, 0.0, look.z);
        if (horiz.lengthSqr() < 1.0e-4) {
            horiz = new Vec3(0, 0, 1);
        }
        horiz = horiz.normalize();

        // How far the crosshair is, horizontally, capped at 7 and floored at 3.
        Vec3 cursor = player.pick(8.0, 1.0f, false).getLocation();
        double dx = cursor.x - player.getX();
        double dz = cursor.z - player.getZ();
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        double dist = Math.max(3.0, Math.min(7.0, horizontalDist));

        return new Vec3(player.getX() + horiz.x * dist, player.getY(), player.getZ() + horiz.z * dist);
    }

    /** A brief elemental rune flash in front of the caster when an enhanced spell releases. */
    private void spawnCastRune(ServerPlayer player, Spell spell) {
        if (!(player.level() instanceof ServerLevel world)) {
            return;
        }
        var rune = switch (spell) {
            case FIREBALL -> ModParticles.RUNE_FIRE;
            case THUNDER -> ModParticles.RUNE_THUNDER;
            case WALL_OF_WIND, WIND_BLADE, DASH -> ModParticles.RUNE_WIND;
            default -> ModParticles.RUNE_BIND;
        };
        Vec3 look = player.getViewVector(1.0f);
        Vec3 p = player.getEyePosition().add(look.scale(1.4)).add(0, -0.2, 0);
        world.sendParticles(rune, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
    }

    private void playSpellSound(ServerPlayer player, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        if (player.level() instanceof ServerLevel serverWorld) {
            serverWorld.playSound(null, player.getX(), player.getY(), player.getZ(),
                    sound, SoundSource.PLAYERS, volume, pitch);
        }
    }

    // ------------------------------------------------------------------------
    // Advancements
    // ------------------------------------------------------------------------

    private boolean hasAdvancement(ServerPlayer player, Identifier id) {
        AdvancementHolder advancement = player.level().getServer().getAdvancements().get(id);
        if (advancement == null) {
            return false;
        }
        return player.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    private void grantAdvancement(ServerPlayer player, Identifier id) {
        AdvancementHolder advancement = player.level().getServer().getAdvancements().get(id);
        if (advancement == null) {
            return;
        }
        if (player.getAdvancements().getOrStartProgress(advancement).isDone()) {
            return;
        }
        for (String criterion : advancement.value().criteria().keySet()) {
            player.getAdvancements().award(advancement, criterion);
        }
    }
}
