package com.example.circlemage.entity;

import com.example.circlemage.boss.BossZone;
import com.example.circlemage.boss.BossZoneManager;
import com.example.circlemage.particle.ModParticles;
import com.example.circlemage.network.ModNetworking;
import com.example.circlemage.spell.Spell;
import com.example.circlemage.world.WorldProgressState;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Master — the first major boss (Master of The Prodigy). A 5th-circle archmage who, on engagement,
 * raises a Domain Barrier (a {@link BossZone}) and fights with Arcane Barrage, two Meteor Rain phases,
 * and a one-time Barrier Punishment. At 0 HP he refuses death, ascends to the 6th circle, and begins a
 * 60-second countdown that ends in a cataclysmic ground Chaos Blast — then dies, dropping a Fragment of
 * Law. Built on the existing Boss Zone / Meteor Rain / Magic Missile / Dark Orb systems.
 */
public class MasterEntity extends PathfinderMob {

    public static final float MAX_HP = 100.0f;
    private static final double DOMAIN_RADIUS = 64.0;     // a touch larger than the 50-block trigger range
    private static final double TRIGGER_OUTER = 50.0;
    private static final double TRIGGER_APPROACH = 20.0;
    private static final double KEEP_DISTANCE = 10.0;

    private static final int BARRAGE_CAST_TICKS = 20;     // 1s wind-up
    private static final int BARRAGE_COUNT = 32;
    private static final int BARRAGE_COOLDOWN = 150;
    private static final int DASH_COOLDOWN = 100;         // 5s normally
    private static final int DASH_COOLDOWN_METEOR = 60;   // 3s during a meteor phase
    private static final double HOVER_HEIGHT = 10.0;
    private static final int COUNTDOWN_TICKS = 1200;      // 60s desperation countdown
    private static final DustParticleOptions TELEGRAPH = new DustParticleOptions(0xFF1A1A, 1.4f);

    // Innate magic resistance: 5%/circle (25% at the 5th) plus The Master's own bonuses.
    private static final float MASTER_NATURAL_RESIST = 0.10f;   // +10% just for being The Master
    private static final float MASTER_LEVITATE_RESIST = 0.50f;  // +50% while airborne (meteor phase): 35% -> 85%

    // The Master's Meteor Rain runs at double the base frequency (base 50x missile / 30x fireball).
    private static final double MASTER_METEOR_RATE_MISSILE = 100.0;
    private static final double MASTER_METEOR_RATE_FIREBALL = 60.0;

    // Barrier Punishment: the 3 retaliation missiles now land 2s after the player raises a Self Barrier.
    private static final int BARRIER_PUNISH_DELAY = 40;

    // Fire Line: every 20% HP lost, a 1s-telegraphed sweep of 16 equally-spaced fire lines from the
    // boss out to the barrier (or, on alternate casts, inward from the barrier). One line targets the
    // player. The "front" travels FIRE_LINE_SPEED blocks/tick along each line.
    private static final int FIRE_LINE_DIRS = 16;
    private static final int FIRE_LINE_CAST_TICKS = 20;   // 1s casting delay
    private static final double FIRE_LINE_SPEED = 2.0;    // blocks/tick the fire front travels
    private static final double FIRE_LINE_HIT_RADIUS = 1.6;

    private boolean triggered = false;
    private int circle = 5;

    // Neutral-phase trigger tracking.
    private final Set<UUID> seenWithinOuter = new HashSet<>();
    private int locatorBeamTicks = 0;

    private ServerBossEvent bossBar;

    // Ability state.
    private int barrageTicks = -1;
    private long barrageCooldownEnd = 0;
    private long dashCooldownEnd = 0;
    private boolean barrierPunished = false;
    // Barrier Punishment: the 3 retaliation missiles fire after a 2s delay.
    private long barrierPunishFireTick = -1;
    private UUID barrierPunishTarget = null;
    private int strafeSign = 1;

    // Fire Line attack state.
    private float fireLineThreshold = 0.80f;  // next HP fraction that triggers a Fire Line
    private int fireLineCasts = 0;            // how many Fire Lines have been cast (parity = direction)
    private int fireLineCastTicks = -1;       // >=0 while telegraphing the cast
    private boolean fireLineReverse = false;  // this cast's direction
    private double fireLineBaseAngle = 0.0;   // locked so one line points at the player
    private boolean fireLineSweeping = false; // true while the fire front is travelling
    private double fireLineFront = 0.0;       // current front distance from the centre
    private final Set<UUID> fireLineHit = new HashSet<>();

    // Meteor phases.
    private boolean meteorActive = false;
    private int meteorPhasesDone = 0;
    private int meteorPhase = 0;
    private Vec3 hoverCenter = Vec3.ZERO;
    private int dodgeReturnTicks = 0;

    // Phase 2 / finale.
    private boolean phase2 = false;
    private int ascensionBeamTicks = 0;
    private int countdown = -1;
    private int finaleTicks = -1;
    private boolean finaleDeath = false;

    public MasterEntity(EntityType<? extends MasterEntity> type, Level world) {
        super(type, world);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HP)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 90.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.85);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 24.0f));
        // No targeting/wander goals: neutral until engaged; all combat is driven from mobTick().
    }

    /** Called from the summon command: name, persistence, and a brief locator beam. */
    public void onSummoned() {
        this.setCustomName(Component.literal("The Master").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        this.setCustomNameVisible(true);
        this.setPersistenceRequired();
        this.locatorBeamTicks = 25;
    }

    private float magicDamage() {
        // 5th circle ~ 8 (4 hearts), stronger after ascension. Robes still reduce Magic Missile damage.
        return phase2 ? 11.0f : 8.0f;
    }

    // --- Main loop ------------------------------------------------------------

    @Override
    protected void customServerAiStep(ServerLevel aiWorld) {
        super.customServerAiStep(aiWorld);
        if (!(this.level() instanceof ServerLevel world)) {
            return;
        }
        long now = world.getGameTime();

        if (locatorBeamTicks > 0) {
            spawnBeam(world, this.getX(), this.getZ(), ModParticles.RADIANCE);
            locatorBeamTicks--;
        }

        if (!triggered) {
            tickNeutralTriggers(world);
            return;
        }

        BossZone zone = BossZoneManager.zoneByOwner(this.getUUID());
        if (zone == null) {
            zone = raiseDomain(world); // safety net (e.g. after a reload mid-fight)
        }
        updateBossBar(world, zone);

        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive() || !zone.contains(target.position())) {
            target = nearestPlayerInZone(world, zone);
            this.setTarget(target);
        }
        if (target != null && this.hasLineOfSight(target)) {
            this.getLookControl().setLookAt(target, 30.0f, 30.0f);
        }

        if (ascensionBeamTicks > 0) {
            spawnBeam(world, this.getX(), this.getZ(), ModParticles.LIGHT);
            ascensionBeamTicks--;
        }

        // Finale sequence overrides everything.
        if (finaleTicks >= 0) {
            tickFinale(world);
            return;
        }

        // Desperation countdown (phase 2): combat continues, bar drains over 60s.
        if (countdown >= 0) {
            countdown--;
            if (countdown <= 0) {
                beginFinale(world);
                return;
            }
        }

        if (target == null) {
            // The encounter ends when the player dies (or otherwise leaves the fight).
            endEncounter(world);
            return;
        }

        // A player hidden by steam keeps the encounter alive but cannot be aimed at.
        // setTarget is also blocked by the shared mob mixin; this covers the local fallback.
        if (!this.hasLineOfSight(target)) return;

        // Delayed Barrier Punishment: the 3 retaliation missiles land 2s after the Self Barrier was raised.
        tickBarrierPunish(world, target, now);

        // Fire Line takes over the boss while it telegraphs and sweeps.
        if (fireLineCastTicks >= 0 || fireLineSweeping) {
            tickFireLine(world, target, zone, now);
            return;
        }

        manageMeteorPhase(world, zone);
        if (meteorActive) {
            tickMeteorPhase(world, target, now);
            return;
        }

        // A 20% HP drop triggers a Fire Line (when the boss is free to cast it).
        if (barrageTicks < 0) {
            maybeStartFireLine(world, target, zone);
            if (fireLineCastTicks >= 0) {
                return;
            }
        }

        tickBarrage(world, target, now);
        if (barrageTicks < 0) {
            tickMovement(world, target, now, zone);
        }
    }

    /** Ends the encounter (player died / left): tears down the domain + bar and resets to a fresh boss. */
    private void endEncounter(ServerLevel world) {
        BossZoneManager.removeByOwner(this.getUUID());
        if (bossBar != null) {
            bossBar.removeAllPlayers();
            bossBar.setVisible(false);
            bossBar = null;
        }
        triggered = false;
        phase2 = false;
        circle = 5;
        countdown = -1;
        finaleTicks = -1;
        meteorActive = false;
        meteorPhasesDone = 0;
        meteorPhase = 0;
        barrierPunished = false;
        barrierPunishFireTick = -1;
        barrierPunishTarget = null;
        fireLineThreshold = 0.80f;
        fireLineCasts = 0;
        fireLineCastTicks = -1;
        fireLineSweeping = false;
        fireLineHit.clear();
        barrageTicks = -1;
        this.setNoGravity(false);
        this.setTarget(null);
        this.seenWithinOuter.clear();
        this.setHealth(this.getMaxHealth());
    }

    // --- Neutral: engagement triggers ----------------------------------------

    private void tickNeutralTriggers(ServerLevel world) {
        if (this.tickCount % 5 != 0) {
            return;
        }
        for (ServerPlayer player : world.players()) {
            if (!player.isAlive() || player.isSpectator() || player.isCreative()) {
                continue;
            }
            double dist = player.distanceTo(this);
            if (dist <= TRIGGER_APPROACH) {
                trigger(world, player, "You dare come here after what you've done.");
                return;
            }
            if (dist <= TRIGGER_OUTER) {
                seenWithinOuter.add(player.getUUID());
            } else if (seenWithinOuter.contains(player.getUUID())) {
                // Was within 50 and is now trying to leave — no escape.
                trigger(world, player, "I finally found you. You really think I'd let you escape?");
                return;
            }
        }
    }

    private void trigger(ServerLevel world, ServerPlayer player, String line) {
        if (triggered) {
            return;
        }
        triggered = true;
        this.setPersistenceRequired();
        this.setTarget(player);
        raiseDomain(world);
        say(world, Component.literal(line).withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
        world.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 1.5f, 0.6f);
    }

    private BossZone raiseDomain(ServerLevel world) {
        BossZone existing = BossZoneManager.zoneByOwner(this.getUUID());
        if (existing != null) {
            return existing;
        }
        BossZone zone = new BossZone(world, this.position().add(0, 1.0, 0), DOMAIN_RADIUS, this.getUUID());
        BossZoneManager.register(zone);
        return zone;
    }

    // --- Boss bar -------------------------------------------------------------

    private void updateBossBar(ServerLevel world, BossZone zone) {
        if (bossBar == null) {
            bossBar = new ServerBossEvent(java.util.UUID.randomUUID(), bossTitle(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
        }
        // Keep the bar shown to everyone in the domain.
        Set<UUID> shouldSee = new HashSet<>();
        for (ServerPlayer player : world.players()) {
            if (zone.contains(player.position())) {
                shouldSee.add(player.getUUID());
                bossBar.addPlayer(player);
            } else {
                bossBar.removePlayer(player);
            }
        }
        if (countdown >= 0) {
            bossBar.setName(Component.literal("The Master | 6th Circle  —  " + (countdown / 20) + "s")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            bossBar.setProgress(Math.max(0.0f, Math.min(1.0f, countdown / (float) COUNTDOWN_TICKS)));
        } else {
            bossBar.setName(bossTitle());
            bossBar.setProgress(Math.max(0.0f, this.getHealth() / this.getMaxHealth()));
        }
    }

    private Component bossTitle() {
        return Component.literal("The Master | " + circle + "th Circle").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD);
    }

    // --- Ability 1: Arcane Barrage -------------------------------------------

    private void tickBarrage(ServerLevel world, LivingEntity target, long now) {
        if (barrageTicks >= 0) {
            this.getNavigation().stop();
            this.setDeltaMovement(0, this.getDeltaMovement().y, 0);
            this.getLookControl().setLookAt(target, 30, 30);
            spawnBarrageTelegraph(world);
            if (--barrageTicks < 0) {
                fireBarrage(world);
                barrageCooldownEnd = now + BARRAGE_COOLDOWN;
            }
            return;
        }
        double dist = this.distanceTo(target);
        if (now >= barrageCooldownEnd && dist <= 45.0) {
            barrageTicks = BARRAGE_CAST_TICKS;
            say(world, Component.literal("\"Encircle them.\"").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
            world.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.HOSTILE, 1.0f, 0.8f);
        }
    }

    private void spawnBarrageTelegraph(ServerLevel world) {
        Vec3 eye = this.getEyePosition();
        for (int i = 0; i < BARRAGE_COUNT; i++) {
            double a = (Math.PI * 2.0 * i) / BARRAGE_COUNT;
            double dx = Math.cos(a);
            double dz = Math.sin(a);
            for (double d = 1.0; d <= 5.0; d += 1.0) {
                world.sendParticles(TELEGRAPH, eye.x + dx * d, eye.y, eye.z + dz * d, 1, 0, 0, 0, 0.0);
            }
        }
    }

    /** Fires 16 evenly-spaced, perfectly straight Magic Missiles (no deviation, no homing). */
    private void fireBarrage(ServerLevel world) {
        Vec3 eye = this.getEyePosition();
        for (int i = 0; i < BARRAGE_COUNT; i++) {
            double a = (Math.PI * 2.0 * i) / BARRAGE_COUNT;
            Vec3 dir = new Vec3(Math.cos(a), 0.0, Math.sin(a));
            MagicMissileEntity missile = new MagicMissileEntity(world, this, magicDamage());
            missile.setPos(eye.x + dir.x * 1.2, eye.y, eye.z + dir.z * 1.2);
            missile.launch(dir);
            world.addFreshEntity(missile);
        }
        world.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.HOSTILE, 1.2f, 1.0f);
    }

    // --- Delayed Barrier Punishment ------------------------------------------

    /** Fires the queued 3-missile retaliation once its 2s delay elapses. */
    private void tickBarrierPunish(ServerLevel world, LivingEntity fallback, long now) {
        if (barrierPunishFireTick < 0 || now < barrierPunishFireTick) {
            return;
        }
        barrierPunishFireTick = -1;
        LivingEntity victim = fallback;
        if (barrierPunishTarget != null && world.getEntity(barrierPunishTarget) instanceof LivingEntity le && le.isAlive()) {
            victim = le;
        }
        barrierPunishTarget = null;
        if (victim == null) {
            return;
        }
        for (int i = 0; i < 3; i++) {
            fireMissileAt(world, victim);
        }
        world.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.HOSTILE, 1.2f, 0.9f);
    }

    private void fireMissileAt(ServerLevel world, LivingEntity target) {
        Vec3 from = this.getEyePosition();
        Vec3 dir = target.getEyePosition().subtract(from);
        if (dir.lengthSqr() < 1.0e-4) {
            dir = this.getViewVector(1.0f);
        }
        MagicMissileEntity missile = new MagicMissileEntity(world, this, magicDamage());
        missile.launch(dir.normalize());
        world.addFreshEntity(missile);
    }

    // --- Ability 2: Meteor Rain phases ---------------------------------------

    private void manageMeteorPhase(ServerLevel world, BossZone zone) {
        if (phase2) {
            return;
        }
        float frac = this.getHealth() / this.getMaxHealth();
        if (!meteorActive) {
            if (meteorPhasesDone == 0 && frac <= 0.70f) {
                startMeteorPhase(world, zone, 1);
            } else if (meteorPhasesDone == 1 && frac <= 0.20f) {
                startMeteorPhase(world, zone, 2);
            }
        } else if (meteorPhase == 1 && frac <= 0.50f) {
            endMeteorPhase(world, zone);
        }
        // The 2nd meteor phase ends at 0 HP, which beginPhase2() handles.
    }

    private void startMeteorPhase(ServerLevel world, BossZone zone, int phase) {
        meteorActive = true;
        meteorPhase = phase;
        hoverCenter = zone.getCenter().add(0, HOVER_HEIGHT, 0);
        this.setNoGravity(true);
        this.barrageTicks = -1;
        // The Master's meteor shower runs at double the base frequency.
        zone.setMeteorRateMissile(MASTER_METEOR_RATE_MISSILE);
        zone.setMeteorRateFireball(MASTER_METEOR_RATE_FIREBALL);
        if (!zone.isMeteorRainActive()) {
            zone.toggleMeteorRain();
            BossZoneManager.markDirty();
        }
        say(world, Component.literal("\"Rain ruin upon them.\"").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
        world.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.7f, 1.4f);
    }

    private void endMeteorPhase(ServerLevel world, BossZone zone) {
        meteorActive = false;
        meteorPhasesDone++;
        this.setNoGravity(false);
        if (zone.isMeteorRainActive()) {
            zone.toggleMeteorRain();
            BossZoneManager.markDirty();
        }
        // Drop back toward the ground near the centre.
        this.teleportTo(zone.getCenter().x, this.getY(), zone.getCenter().z);
    }

    private void tickMeteorPhase(ServerLevel world, LivingEntity target, long now) {
        // Dash-dodge incoming projectiles, then snap back to the hover point.
        if (dodgeReturnTicks > 0) {
            if (--dodgeReturnTicks == 0) {
                this.teleportTo(hoverCenter.x, hoverCenter.y, hoverCenter.z);
                this.setDeltaMovement(Vec3.ZERO);
            }
        } else {
            this.setDeltaMovement(Vec3.ZERO);
            this.teleportTo(hoverCenter.x, hoverCenter.y, hoverCenter.z);
            if (now >= dashCooldownEnd && incomingProjectile(world)) {
                Vec3 perp = new Vec3(this.getRandom().nextDouble() - 0.5, 0, this.getRandom().nextDouble() - 0.5);
                if (perp.lengthSqr() < 1.0e-4) {
                    perp = new Vec3(1, 0, 0);
                }
                perp = perp.normalize().scale(1.1);
                this.setNoGravity(true);
                this.setDeltaMovement(perp.x, 0.0, perp.z);
                this.syncVelocity = true;
                dashCooldownEnd = now + DASH_COOLDOWN_METEOR;
                dodgeReturnTicks = 6;
            }
        }
        if (this.tickCount % 4 == 0) {
            world.sendParticles(ModParticles.VOID, this.getX(), this.getY(), this.getZ(), 4, 0.3, 0.4, 0.3, 0.02);
        }
    }

    private boolean incomingProjectile(ServerLevel world) {
        Vec3 center = this.getBoundingBox().getCenter();
        for (Projectile proj : world.getEntitiesOfClass(Projectile.class,
                this.getBoundingBox().inflate(9.0), p -> p.isAlive() && p.getOwner() != this)) {
            Vec3 toMe = center.subtract(proj.position());
            if (toMe.lengthSqr() < 0.04 || proj.getDeltaMovement().lengthSqr() < 1.0e-4) {
                continue;
            }
            if (proj.getDeltaMovement().normalize().dot(toMe.normalize()) > 0.8) {
                return true;
            }
        }
        return false;
    }

    // --- Ability 3: Barrier Punishment ---------------------------------------

    /** Static hook: a player cast Self Barrier — if they're in a Master's domain, that Master reacts. */
    public static void tryBarrierPunish(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel world)) {
            return;
        }
        BossZone zone = BossZoneManager.zoneContaining(world, player.position());
        if (zone == null) {
            return;
        }
        Entity owner = world.getEntity(zone.getOwner());
        if (owner instanceof MasterEntity master) {
            master.onPlayerUsedBarrier(world, player, zone);
        }
    }

    private void onPlayerUsedBarrier(ServerLevel world, ServerPlayer player, BossZone zone) {
        if (!triggered || barrierPunished) {
            return;
        }
        barrierPunished = true;
        say(world, Component.literal("\"I won't make it that easy.\"").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
        // The 3 retaliation missiles land 2s later (fired from tickBarrierPunish).
        barrierPunishFireTick = world.getGameTime() + BARRIER_PUNISH_DELAY;
        barrierPunishTarget = player.getUUID();
        // Suppress Self Barrier for the rest of the encounter.
        zone.setRestricted(Spell.BARRIER.id, true);
        zone.setRestrictionsActive(true);
        BossZoneManager.markDirty();
        com.example.circlemage.util.Messages.send(player, Component.literal("The Domain suppresses your Barrier — you cannot raise it here.")
                .withStyle(ChatFormatting.DARK_PURPLE), false);
    }

    // --- Phase 2: Desperation Ascension --------------------------------------

    private void beginPhase2(ServerLevel world) {
        phase2 = true;
        circle = 6;
        meteorActive = false;
        this.setNoGravity(false);
        BossZone zone = BossZoneManager.zoneByOwner(this.getUUID());
        if (zone != null && zone.isMeteorRainActive()) {
            zone.toggleMeteorRain();
        }
        ascensionBeamTicks = 30;
        countdown = COUNTDOWN_TICKS;
        say(world, Component.literal("\"I am unwilling.\"").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC, ChatFormatting.BOLD));
        say(world, Component.literal("\"I'll sacrifice everything to kill you.\"").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
        world.sendParticles(ModParticles.RADIANCE, this.getX(), this.getY(0.5), this.getZ(), 80, 0.5, 1.0, 0.5, 0.2);
        world.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 2.0f, 0.7f);
        if (bossBar != null) {
            bossBar.setName(bossTitle());
        }
    }

    private void beginFinale(ServerLevel world) {
        finaleTicks = 40;
        BossZone zone = BossZoneManager.zoneByOwner(this.getUUID());
        if (zone != null) {
            this.teleportTo(zone.getCenter().x, zone.getCenter().y, zone.getCenter().z);
        }
        this.getNavigation().stop();
        say(world, Component.literal("\"This is the end of the line...\"").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC, ChatFormatting.BOLD));
        world.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 2.0f, 0.4f);
    }

    private void tickFinale(ServerLevel world) {
        this.getNavigation().stop();
        this.setDeltaMovement(0, this.getDeltaMovement().y, 0);
        // Gathering energy before the blast.
        world.sendParticles(ModParticles.VOID, this.getX(), this.getY(0.6), this.getZ(), 6, 0.4, 0.6, 0.4, 0.03);
        world.sendParticles(ModParticles.ARCANE_TRAIL, this.getX(), this.getY(0.6), this.getZ(), 3, 0.4, 0.6, 0.4, 0.02);
        if (--finaleTicks < 0) {
            unleashCataclysm(world);
        }
    }

    /** The cinematic finish: a full super Hollow Purple detonation into the ground, then The Master dies. */
    private void unleashCataclysm(ServerLevel world) {
        DarkOrbEntity orb = new DarkOrbEntity(world, this, false, true);
        orb.setPos(this.getX(), this.getY() + 0.5, this.getZ());
        world.addFreshEntity(orb);
        orb.triggerGroundCataclysm(0.5); // The Master's blast is half the player's super-nuke

        finaleDeath = true;
        this.kill((ServerLevel)level()); // routed through damage(OUT_OF_WORLD) -> dies -> onDeath() cleans up
    }

    // --- Ability 4: Fire Line -------------------------------------------------

    /** Triggers a Fire Line each time the boss's health crosses a fresh 20% step. */
    private void maybeStartFireLine(ServerLevel world, LivingEntity target, BossZone zone) {
        if (phase2 || meteorActive || fireLineSweeping || fireLineCastTicks >= 0 || fireLineThreshold <= 0.0f) {
            return;
        }
        float frac = this.getHealth() / this.getMaxHealth();
        if (frac > fireLineThreshold) {
            return;
        }
        // Drop the threshold past every step we've blown through, so one big hit fires only one line.
        while (fireLineThreshold > 0.0f && frac <= fireLineThreshold) {
            fireLineThreshold -= 0.20f;
        }
        startFireLine(world, target, zone);
    }

    private void startFireLine(ServerLevel world, LivingEntity target, BossZone zone) {
        fireLineCastTicks = FIRE_LINE_CAST_TICKS;
        fireLineReverse = (fireLineCasts % 2 == 1); // cast 1 forward, cast 2 reverse, then alternate
        fireLineCasts++;
        fireLineHit.clear();
        this.getNavigation().stop();
        // Lock the firing fan so one of the 16 lines points exactly at the player.
        Vec3 toPlayer = horizontal(target.position().subtract(this.position()));
        fireLineBaseAngle = Math.atan2(toPlayer.z, toPlayer.x);
        // "Look behind" on the first two casts only (then the alternation is silent).
        if (fireLineCasts <= 2) {
            say(world, Component.literal("\"Look behind.\"").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC, ChatFormatting.BOLD));
        }
        world.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.5f, 0.6f);
    }

    private void tickFireLine(ServerLevel world, LivingEntity target, BossZone zone, long now) {
        this.getNavigation().stop();
        this.setDeltaMovement(0, this.getDeltaMovement().y, 0);
        if (target != null) {
            this.getLookControl().setLookAt(target, 30, 30);
        }

        if (fireLineCastTicks >= 0) {
            if (fireLineCastTicks % 2 == 0) {
                telegraphFireLines(world, zone);
            }
            if (--fireLineCastTicks < 0) {
                fireLineSweeping = true;
                fireLineFront = fireLineReverse ? zone.getRadius() : 0.0;
                world.playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.2f, 1.2f);
            }
            return;
        }
        if (fireLineSweeping) {
            sweepFireLines(world, zone);
        }
    }

    /** Draws all 16 lines from the boss out to the barrier so the player can read the safe gaps. */
    private void telegraphFireLines(ServerLevel world, BossZone zone) {
        Vec3 origin = new Vec3(this.getX(), this.getY(0.5), this.getZ());
        double maxLen = zone.getRadius();
        for (int i = 0; i < FIRE_LINE_DIRS; i++) {
            double a = fireLineBaseAngle + (Math.PI * 2.0 * i) / FIRE_LINE_DIRS;
            double dx = Math.cos(a);
            double dz = Math.sin(a);
            for (double d = 2.0; d <= maxLen; d += 2.5) {
                double px = origin.x + dx * d;
                double pz = origin.z + dz * d;
                if (!zone.contains(new Vec3(px, origin.y, pz))) {
                    break;
                }
                world.sendParticles(TELEGRAPH, px, origin.y, pz, 1, 0, 0, 0, 0.0);
            }
        }
    }

    /** Advances the fire front along every line, spawning fire and damaging anything it sweeps over. */
    private void sweepFireLines(ServerLevel world, BossZone zone) {
        Vec3 origin = new Vec3(this.getX(), this.getY(0.5), this.getZ());
        double maxLen = zone.getRadius();
        for (int i = 0; i < FIRE_LINE_DIRS; i++) {
            double a = fireLineBaseAngle + (Math.PI * 2.0 * i) / FIRE_LINE_DIRS;
            Vec3 front = new Vec3(origin.x + Math.cos(a) * fireLineFront, origin.y,
                    origin.z + Math.sin(a) * fireLineFront);
            if (!zone.contains(front)) {
                continue;
            }
            world.sendParticles(ModParticles.EMBER, front.x, front.y, front.z, 12, 0.25, 0.45, 0.25, 0.05);
            world.sendParticles(ModParticles.SMOKE, front.x, front.y, front.z, 3, 0.2, 0.2, 0.2, 0.01);
            damageFireFront(world, front);
        }
        if (this.tickCount % 4 == 0) {
            world.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.BLAZE_BURN, SoundSource.HOSTILE, 0.6f, 1.0f);
        }
        if (fireLineReverse) {
            fireLineFront -= FIRE_LINE_SPEED;
            if (fireLineFront <= 0.0) {
                endFireLine();
            }
        } else {
            fireLineFront += FIRE_LINE_SPEED;
            if (fireLineFront >= maxLen) {
                endFireLine();
            }
        }
    }

    private void damageFireFront(ServerLevel world, Vec3 front) {
        double r = FIRE_LINE_HIT_RADIUS;
        AABB box = new AABB(front.x - r, front.y - 2.0, front.z - r, front.x + r, front.y + 2.0, front.z + r);
        for (LivingEntity e : world.getEntitiesOfClass(LivingEntity.class, box, x -> x != this && x.isAlive())) {
            if (fireLineHit.contains(e.getUUID())) {
                continue;
            }
            double dx = e.getX() - front.x;
            double dz = e.getZ() - front.z;
            if (Math.sqrt(dx * dx + dz * dz) > r) {
                continue;
            }
            if (front.y < e.getY() - 0.5 || front.y > e.getY() + e.getBbHeight() + 0.5) {
                continue;
            }
            fireLineHit.add(e.getUUID());
            e.hurt(world.damageSources().magic(), magicDamage());
            e.igniteForSeconds(4);
        }
    }

    private void endFireLine() {
        fireLineSweeping = false;
        fireLineFront = 0.0;
        fireLineHit.clear();
    }

    // --- Movement AI ----------------------------------------------------------

    /**
     * Kiting movement: The Master orbits the domain centre on the side away from the player, keeping
     * near the middle of the zone instead of backpedalling into the barrier (which used to leave it
     * pinned against the wall as an easy target).
     */
    private void tickMovement(ServerLevel world, LivingEntity target, long now, BossZone zone) {
        Vec3 center = zone.getCenter();
        Vec3 mPos = this.position();
        Vec3 tPos = target.position();
        double distTarget = this.distanceTo(target);
        double dxc = mPos.x - center.x;
        double dzc = mPos.z - center.z;
        double distCenter = Math.sqrt(dxc * dxc + dzc * dzc);

        final double ORBIT = 16.0;           // how far from the centre the boss hovers
        final double MAX_FROM_CENTER = 28.0; // never drift past this from the centre

        // The boss wants the side of the centre OPPOSITE the player (maximising distance, staying central).
        Vec3 desired = horizontal(center.subtract(tPos)); // points from the player, through the centre, outward
        if (this.tickCount % 70 == 0) {
            strafeSign = -strafeSign;
        }
        Vec3 perp = new Vec3(-desired.z, 0, desired.x).scale(strafeSign * 0.45);
        Vec3 anchorDir = desired.add(perp);
        anchorDir = anchorDir.lengthSqr() < 1.0e-4 ? desired : anchorDir.normalize();
        Vec3 spot = center.add(anchorDir.scale(ORBIT));

        // Re-open the gap when crowded, and peel off the wall if pushed out there.
        if (distTarget < KEEP_DISTANCE) {
            Vec3 dashDir = horizontal(mPos.subtract(tPos)).add(horizontal(center.subtract(mPos)));
            tryDash(world, dashDir, now);
        } else if (distCenter > MAX_FROM_CENTER) {
            tryDash(world, horizontal(center.subtract(mPos)), now);
        }

        this.getNavigation().moveTo(spot.x, spot.y, spot.z, 1.0);
    }

    private boolean tryDash(ServerLevel world, Vec3 dir, long now) {
        if (now < dashCooldownEnd || dir.lengthSqr() < 1.0e-4) {
            return false;
        }
        dashCooldownEnd = now + DASH_COOLDOWN;
        Vec3 h = horizontal(dir).scale(1.1);
        this.setDeltaMovement(h.x, 0.2, h.z);
        this.syncVelocity = true;
        this.fallDistance = 0.0f;
        world.sendParticles(ModParticles.GALE, this.getX(), this.getY() + 0.9, this.getZ(), 10, 0.3, 0.3, 0.3, 0.3);
        return true;
    }

    // --- Damage / death -------------------------------------------------------

    @Override
    public boolean hurtServer(net.minecraft.server.level.ServerLevel damageWorld, DamageSource source, float amount) {
        if (this.level().isClientSide()) {
            return super.hurtServer(damageWorld, source, amount);
        }
        // Out-of-world / the finale's own kill bypasses all protection.
        if (source.is(DamageTypes.FELL_OUT_OF_WORLD) || finaleDeath) {
            return super.hurtServer(damageWorld, source, amount);
        }
        // Attacking The Master starts the encounter.
        if (!triggered && source.getEntity() instanceof ServerPlayer attacker
                && this.level() instanceof ServerLevel world) {
            trigger(world, attacker, "Your insolence knows no bounds. I am going to enjoy this.");
        }

        // Innate magic resistance: 5%/circle (25% at the 5th) + 10% natural, +50% more while airborne
        // during a meteor phase (35% grounded -> 85% levitating). Applies to fire / raw magic only.
        float amt = amount;
        if (com.example.circlemage.spell.MagicResistance.isResistible(source)) {
            float resist = com.example.circlemage.spell.MagicResistance.forCircle(circle) + MASTER_NATURAL_RESIST;
            if (meteorActive) {
                resist += MASTER_LEVITATE_RESIST;
            }
            amt = amount * (1.0f - com.example.circlemage.spell.MagicResistance.clamp(resist));
        }

        // Death prevention: at 0 HP, ascend once; afterwards stay alive until the finale.
        if (this.getHealth() - amt <= 0.0f) {
            if (!phase2 && this.level() instanceof ServerLevel world) {
                this.setHealth(1.0f);
                beginPhase2(world);
                return true;
            }
            this.setHealth(Math.max(1.0f, this.getHealth()));
            return false; // invulnerable during the countdown
        }
        return super.hurtServer(damageWorld, source, amt);
    }

    @Override
    public void die(DamageSource damageSource) {
        if (!this.level().isClientSide() && this.level() instanceof ServerLevel world) {
            BossZoneManager.removeByOwner(this.getUUID());
            if (bossBar != null) {
                bossBar.removeAllPlayers();
                bossBar.setVisible(false);
            }
            WorldProgressState.get(world).markMasterDefeated();
            this.spawnAtLocation((ServerLevel)level(), new net.minecraft.world.item.ItemStack(com.example.circlemage.item.ModItems.FRAGMENT_OF_LAW));
            say(world, Component.literal("The Master falls. A fragment of something greater is left behind...")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            for (ServerPlayer p : world.players()) {
                if (p.distanceToSqr(this) <= 96.0 * 96.0) {
                    ModNetworking.sendJournalSync(p);
                }
            }
        }
        super.die(damageSource);
    }

    @Override
    public void remove(RemovalReason reason) {
        // If The Master is removed without dying (e.g. /kill, chunk discard), tidy up the domain/bar.
        if (!this.level().isClientSide() && reason.shouldDestroy()) {
            BossZoneManager.removeByOwner(this.getUUID());
            if (bossBar != null) {
                bossBar.removeAllPlayers();
            }
        }
        super.remove(reason);
    }

    @Override
    public boolean removeWhenFarAway(double distanceSquared) {
        return false; // a summoned/engaged boss never despawns
    }

    // --- Helpers / NBT --------------------------------------------------------

    private LivingEntity nearestPlayerInZone(ServerLevel world, BossZone zone) {
        ServerPlayer best = null;
        double bestSq = Double.MAX_VALUE;
        for (ServerPlayer p : world.players()) {
            if (!p.isAlive() || p.isSpectator() || p.isCreative() || !zone.contains(p.position())) {
                continue;
            }
            double sq = p.distanceToSqr(this);
            if (sq < bestSq) {
                bestSq = sq;
                best = p;
            }
        }
        return best;
    }

    private void say(ServerLevel world, Component message) {
        double r2 = 96.0 * 96.0;
        for (ServerPlayer p : world.players()) {
            if (p.distanceToSqr(this) <= r2) {
                com.example.circlemage.util.Messages.send(p, message, false);
            }
        }
    }

    private void spawnBeam(ServerLevel world, double x, double z, net.minecraft.core.particles.SimpleParticleType particle) {
        double baseY = this.getY();
        for (double y = baseY; y <= baseY + 40.0; y += 0.5) {
            world.sendParticles(particle, x, y, z, 1, 0.05, 0.0, 0.05, 0.0);
        }
    }

    private static Vec3 horizontal(Vec3 v) {
        Vec3 h = new Vec3(v.x, 0.0, v.z);
        return h.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : h.normalize();
    }

    public void addAdditionalSaveDataLegacy(CompoundTag nbt) {
        
        nbt.putBoolean("triggered", triggered);
        nbt.putInt("circle", circle);
        nbt.putBoolean("phase2", phase2);
        nbt.putInt("countdown", countdown);
        nbt.putInt("meteorPhasesDone", meteorPhasesDone);
        nbt.putBoolean("barrierPunished", barrierPunished);
        nbt.putFloat("fireLineThreshold", fireLineThreshold);
        nbt.putInt("fireLineCasts", fireLineCasts);
    }

    public void readAdditionalSaveDataLegacy(CompoundTag nbt) {
        
        triggered = nbt.getBooleanOr("triggered", false);
        circle = nbt.contains("circle") ? nbt.getIntOr("circle", 0) : 5;
        phase2 = nbt.getBooleanOr("phase2", false);
        countdown = nbt.contains("countdown") ? nbt.getIntOr("countdown", 0) : -1;
        meteorPhasesDone = nbt.getIntOr("meteorPhasesDone", 0);
        barrierPunished = nbt.getBooleanOr("barrierPunished", false);
        fireLineThreshold = nbt.contains("fireLineThreshold") ? nbt.getFloatOr("fireLineThreshold", 0f) : 0.80f;
        fireLineCasts = nbt.getIntOr("fireLineCasts", 0);
    }

    @Override protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput value) { super.addAdditionalSaveData(value); CompoundTag tag = new CompoundTag(); addAdditionalSaveDataLegacy(tag); com.example.circlemage.util.NbtBridge.write(value, tag); }

    @Override protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput value) { super.readAdditionalSaveData(value); readAdditionalSaveDataLegacy(com.example.circlemage.util.NbtBridge.read(value)); }
}
