package com.example.circlemage.entity;

import com.example.circlemage.boss.BossZone;
import com.example.circlemage.boss.BossZoneManager;
import com.example.circlemage.particle.ModParticles;
import com.example.circlemage.network.ModNetworking;
import com.example.circlemage.spell.Spell;
import com.example.circlemage.world.WorldProgressState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The Master — the first major boss (Master of The Prodigy). A 5th-circle archmage who, on engagement,
 * raises a Domain Barrier (a {@link BossZone}) and fights with Arcane Barrage, two Meteor Rain phases,
 * and a one-time Barrier Punishment. At 0 HP he refuses death, ascends to the 6th circle, and begins a
 * 60-second countdown that ends in a cataclysmic ground Chaos Blast — then dies, dropping a Fragment of
 * Law. Built on the existing Boss Zone / Meteor Rain / Magic Missile / Dark Orb systems.
 */
public class MasterEntity extends PathAwareEntity {

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
    private static final DustParticleEffect TELEGRAPH = new DustParticleEffect(new Vector3f(1f, 0.1f, 0.1f), 1.4f);

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

    private ServerBossBar bossBar;

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
    private Vec3d hoverCenter = Vec3d.ZERO;
    private int dodgeReturnTicks = 0;

    // Phase 2 / finale.
    private boolean phase2 = false;
    private int ascensionBeamTicks = 0;
    private int countdown = -1;
    private int finaleTicks = -1;
    private boolean finaleDeath = false;

    public MasterEntity(EntityType<? extends MasterEntity> type, World world) {
        super(type, world);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return PathAwareEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, MAX_HP)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 90.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.85);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 24.0f));
        // No targeting/wander goals: neutral until engaged; all combat is driven from mobTick().
    }

    /** Called from the summon command: name, persistence, and a brief locator beam. */
    public void onSummoned() {
        this.setCustomName(Text.literal("The Master").formatted(Formatting.GOLD, Formatting.BOLD));
        this.setCustomNameVisible(true);
        this.setPersistent();
        this.locatorBeamTicks = 25;
    }

    private float magicDamage() {
        // 5th circle ~ 8 (4 hearts), stronger after ascension. Robes still reduce Magic Missile damage.
        return phase2 ? 11.0f : 8.0f;
    }

    // --- Main loop ------------------------------------------------------------

    @Override
    protected void mobTick() {
        super.mobTick();
        if (!(this.getWorld() instanceof ServerWorld world)) {
            return;
        }
        long now = world.getTime();

        if (locatorBeamTicks > 0) {
            spawnBeam(world, this.getX(), this.getZ(), ModParticles.RADIANCE);
            locatorBeamTicks--;
        }

        if (!triggered) {
            tickNeutralTriggers(world);
            return;
        }

        BossZone zone = BossZoneManager.zoneByOwner(this.getUuid());
        if (zone == null) {
            zone = raiseDomain(world); // safety net (e.g. after a reload mid-fight)
        }
        updateBossBar(world, zone);

        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive() || !zone.contains(target.getPos())) {
            target = nearestPlayerInZone(world, zone);
            this.setTarget(target);
        }
        if (target != null) {
            this.getLookControl().lookAt(target, 30.0f, 30.0f);
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
    private void endEncounter(ServerWorld world) {
        BossZoneManager.removeByOwner(this.getUuid());
        if (bossBar != null) {
            bossBar.clearPlayers();
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

    private void tickNeutralTriggers(ServerWorld world) {
        if (this.age % 5 != 0) {
            return;
        }
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (!player.isAlive() || player.isSpectator() || player.isCreative()) {
                continue;
            }
            double dist = player.distanceTo(this);
            if (dist <= TRIGGER_APPROACH) {
                trigger(world, player, "You dare come here after what you've done.");
                return;
            }
            if (dist <= TRIGGER_OUTER) {
                seenWithinOuter.add(player.getUuid());
            } else if (seenWithinOuter.contains(player.getUuid())) {
                // Was within 50 and is now trying to leave — no escape.
                trigger(world, player, "I finally found you. You really think I'd let you escape?");
                return;
            }
        }
    }

    private void trigger(ServerWorld world, ServerPlayerEntity player, String line) {
        if (triggered) {
            return;
        }
        triggered = true;
        this.setPersistent();
        this.setTarget(player);
        raiseDomain(world);
        say(world, Text.literal(line).formatted(Formatting.DARK_RED, Formatting.ITALIC));
        world.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.HOSTILE, 1.5f, 0.6f);
    }

    private BossZone raiseDomain(ServerWorld world) {
        BossZone existing = BossZoneManager.zoneByOwner(this.getUuid());
        if (existing != null) {
            return existing;
        }
        BossZone zone = new BossZone(world, this.getPos().add(0, 1.0, 0), DOMAIN_RADIUS, this.getUuid());
        BossZoneManager.register(zone);
        return zone;
    }

    // --- Boss bar -------------------------------------------------------------

    private void updateBossBar(ServerWorld world, BossZone zone) {
        if (bossBar == null) {
            bossBar = new ServerBossBar(bossTitle(), BossBar.Color.PURPLE, BossBar.Style.PROGRESS);
        }
        // Keep the bar shown to everyone in the domain.
        Set<UUID> shouldSee = new HashSet<>();
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (zone.contains(player.getPos())) {
                shouldSee.add(player.getUuid());
                bossBar.addPlayer(player);
            } else {
                bossBar.removePlayer(player);
            }
        }
        if (countdown >= 0) {
            bossBar.setName(Text.literal("The Master | 6th Circle  —  " + (countdown / 20) + "s")
                    .formatted(Formatting.LIGHT_PURPLE));
            bossBar.setPercent(Math.max(0.0f, Math.min(1.0f, countdown / (float) COUNTDOWN_TICKS)));
        } else {
            bossBar.setName(bossTitle());
            bossBar.setPercent(Math.max(0.0f, this.getHealth() / this.getMaxHealth()));
        }
    }

    private Text bossTitle() {
        return Text.literal("The Master | " + circle + "th Circle").formatted(Formatting.DARK_PURPLE, Formatting.BOLD);
    }

    // --- Ability 1: Arcane Barrage -------------------------------------------

    private void tickBarrage(ServerWorld world, LivingEntity target, long now) {
        if (barrageTicks >= 0) {
            this.getNavigation().stop();
            this.setVelocity(0, this.getVelocity().y, 0);
            this.getLookControl().lookAt(target, 30, 30);
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
            say(world, Text.literal("\"Encircle them.\"").formatted(Formatting.DARK_PURPLE, Formatting.ITALIC));
            world.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.ENTITY_EVOKER_PREPARE_ATTACK, SoundCategory.HOSTILE, 1.0f, 0.8f);
        }
    }

    private void spawnBarrageTelegraph(ServerWorld world) {
        Vec3d eye = this.getEyePos();
        for (int i = 0; i < BARRAGE_COUNT; i++) {
            double a = (Math.PI * 2.0 * i) / BARRAGE_COUNT;
            double dx = Math.cos(a);
            double dz = Math.sin(a);
            for (double d = 1.0; d <= 5.0; d += 1.0) {
                world.spawnParticles(TELEGRAPH, eye.x + dx * d, eye.y, eye.z + dz * d, 1, 0, 0, 0, 0.0);
            }
        }
    }

    /** Fires 16 evenly-spaced, perfectly straight Magic Missiles (no deviation, no homing). */
    private void fireBarrage(ServerWorld world) {
        Vec3d eye = this.getEyePos();
        for (int i = 0; i < BARRAGE_COUNT; i++) {
            double a = (Math.PI * 2.0 * i) / BARRAGE_COUNT;
            Vec3d dir = new Vec3d(Math.cos(a), 0.0, Math.sin(a));
            MagicMissileEntity missile = new MagicMissileEntity(world, this, magicDamage());
            missile.setPosition(eye.x + dir.x * 1.2, eye.y, eye.z + dir.z * 1.2);
            missile.launch(dir);
            world.spawnEntity(missile);
        }
        world.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.ENTITY_ILLUSIONER_CAST_SPELL, SoundCategory.HOSTILE, 1.2f, 1.0f);
    }

    // --- Delayed Barrier Punishment ------------------------------------------

    /** Fires the queued 3-missile retaliation once its 2s delay elapses. */
    private void tickBarrierPunish(ServerWorld world, LivingEntity fallback, long now) {
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
                SoundEvents.ENTITY_ILLUSIONER_CAST_SPELL, SoundCategory.HOSTILE, 1.2f, 0.9f);
    }

    private void fireMissileAt(ServerWorld world, LivingEntity target) {
        Vec3d from = this.getEyePos();
        Vec3d dir = target.getEyePos().subtract(from);
        if (dir.lengthSquared() < 1.0e-4) {
            dir = this.getRotationVec(1.0f);
        }
        MagicMissileEntity missile = new MagicMissileEntity(world, this, magicDamage());
        missile.launch(dir.normalize());
        world.spawnEntity(missile);
    }

    // --- Ability 2: Meteor Rain phases ---------------------------------------

    private void manageMeteorPhase(ServerWorld world, BossZone zone) {
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

    private void startMeteorPhase(ServerWorld world, BossZone zone, int phase) {
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
        say(world, Text.literal("\"Rain ruin upon them.\"").formatted(Formatting.DARK_PURPLE, Formatting.ITALIC));
        world.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 0.7f, 1.4f);
    }

    private void endMeteorPhase(ServerWorld world, BossZone zone) {
        meteorActive = false;
        meteorPhasesDone++;
        this.setNoGravity(false);
        if (zone.isMeteorRainActive()) {
            zone.toggleMeteorRain();
            BossZoneManager.markDirty();
        }
        // Drop back toward the ground near the centre.
        this.requestTeleport(zone.getCenter().x, this.getY(), zone.getCenter().z);
    }

    private void tickMeteorPhase(ServerWorld world, LivingEntity target, long now) {
        // Dash-dodge incoming projectiles, then snap back to the hover point.
        if (dodgeReturnTicks > 0) {
            if (--dodgeReturnTicks == 0) {
                this.requestTeleport(hoverCenter.x, hoverCenter.y, hoverCenter.z);
                this.setVelocity(Vec3d.ZERO);
            }
        } else {
            this.setVelocity(Vec3d.ZERO);
            this.requestTeleport(hoverCenter.x, hoverCenter.y, hoverCenter.z);
            if (now >= dashCooldownEnd && incomingProjectile(world)) {
                Vec3d perp = new Vec3d(this.getRandom().nextDouble() - 0.5, 0, this.getRandom().nextDouble() - 0.5);
                if (perp.lengthSquared() < 1.0e-4) {
                    perp = new Vec3d(1, 0, 0);
                }
                perp = perp.normalize().multiply(1.1);
                this.setNoGravity(true);
                this.setVelocity(perp.x, 0.0, perp.z);
                this.velocityModified = true;
                dashCooldownEnd = now + DASH_COOLDOWN_METEOR;
                dodgeReturnTicks = 6;
            }
        }
        if (this.age % 4 == 0) {
            world.spawnParticles(ModParticles.VOID, this.getX(), this.getY(), this.getZ(), 4, 0.3, 0.4, 0.3, 0.02);
        }
    }

    private boolean incomingProjectile(ServerWorld world) {
        Vec3d center = this.getBoundingBox().getCenter();
        for (ProjectileEntity proj : world.getEntitiesByClass(ProjectileEntity.class,
                this.getBoundingBox().expand(9.0), p -> p.isAlive() && p.getOwner() != this)) {
            Vec3d toMe = center.subtract(proj.getPos());
            if (toMe.lengthSquared() < 0.04 || proj.getVelocity().lengthSquared() < 1.0e-4) {
                continue;
            }
            if (proj.getVelocity().normalize().dotProduct(toMe.normalize()) > 0.8) {
                return true;
            }
        }
        return false;
    }

    // --- Ability 3: Barrier Punishment ---------------------------------------

    /** Static hook: a player cast Self Barrier — if they're in a Master's domain, that Master reacts. */
    public static void tryBarrierPunish(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return;
        }
        BossZone zone = BossZoneManager.zoneContaining(world, player.getPos());
        if (zone == null) {
            return;
        }
        Entity owner = world.getEntity(zone.getOwner());
        if (owner instanceof MasterEntity master) {
            master.onPlayerUsedBarrier(world, player, zone);
        }
    }

    private void onPlayerUsedBarrier(ServerWorld world, ServerPlayerEntity player, BossZone zone) {
        if (!triggered || barrierPunished) {
            return;
        }
        barrierPunished = true;
        say(world, Text.literal("\"I won't make it that easy.\"").formatted(Formatting.DARK_RED, Formatting.ITALIC));
        // The 3 retaliation missiles land 2s later (fired from tickBarrierPunish).
        barrierPunishFireTick = world.getTime() + BARRIER_PUNISH_DELAY;
        barrierPunishTarget = player.getUuid();
        // Suppress Self Barrier for the rest of the encounter.
        zone.setRestricted(Spell.BARRIER.id, true);
        zone.setRestrictionsActive(true);
        BossZoneManager.markDirty();
        player.sendMessage(Text.literal("The Domain suppresses your Barrier — you cannot raise it here.")
                .formatted(Formatting.DARK_PURPLE), false);
    }

    // --- Phase 2: Desperation Ascension --------------------------------------

    private void beginPhase2(ServerWorld world) {
        phase2 = true;
        circle = 6;
        meteorActive = false;
        this.setNoGravity(false);
        BossZone zone = BossZoneManager.zoneByOwner(this.getUuid());
        if (zone != null && zone.isMeteorRainActive()) {
            zone.toggleMeteorRain();
        }
        ascensionBeamTicks = 30;
        countdown = COUNTDOWN_TICKS;
        say(world, Text.literal("\"I am unwilling.\"").formatted(Formatting.DARK_RED, Formatting.ITALIC, Formatting.BOLD));
        say(world, Text.literal("\"I'll sacrifice everything to kill you.\"").formatted(Formatting.DARK_RED, Formatting.ITALIC));
        world.spawnParticles(ModParticles.RADIANCE, this.getX(), this.getBodyY(0.5), this.getZ(), 80, 0.5, 1.0, 0.5, 0.2);
        world.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 2.0f, 0.7f);
        if (bossBar != null) {
            bossBar.setName(bossTitle());
        }
    }

    private void beginFinale(ServerWorld world) {
        finaleTicks = 40;
        BossZone zone = BossZoneManager.zoneByOwner(this.getUuid());
        if (zone != null) {
            this.requestTeleport(zone.getCenter().x, zone.getCenter().y, zone.getCenter().z);
        }
        this.getNavigation().stop();
        say(world, Text.literal("\"This is the end of the line...\"").formatted(Formatting.DARK_RED, Formatting.ITALIC, Formatting.BOLD));
        world.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.HOSTILE, 2.0f, 0.4f);
    }

    private void tickFinale(ServerWorld world) {
        this.getNavigation().stop();
        this.setVelocity(0, this.getVelocity().y, 0);
        // Gathering energy before the blast.
        world.spawnParticles(ModParticles.VOID, this.getX(), this.getBodyY(0.6), this.getZ(), 6, 0.4, 0.6, 0.4, 0.03);
        world.spawnParticles(ModParticles.ARCANE_TRAIL, this.getX(), this.getBodyY(0.6), this.getZ(), 3, 0.4, 0.6, 0.4, 0.02);
        if (--finaleTicks < 0) {
            unleashCataclysm(world);
        }
    }

    /** The cinematic finish: a full super Hollow Purple detonation into the ground, then The Master dies. */
    private void unleashCataclysm(ServerWorld world) {
        DarkOrbEntity orb = new DarkOrbEntity(world, this, false, true);
        orb.setPosition(this.getX(), this.getY() + 0.5, this.getZ());
        world.spawnEntity(orb);
        orb.triggerGroundCataclysm(0.5); // The Master's blast is half the player's super-nuke

        finaleDeath = true;
        this.kill(); // routed through damage(OUT_OF_WORLD) -> dies -> onDeath() cleans up
    }

    // --- Ability 4: Fire Line -------------------------------------------------

    /** Triggers a Fire Line each time the boss's health crosses a fresh 20% step. */
    private void maybeStartFireLine(ServerWorld world, LivingEntity target, BossZone zone) {
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

    private void startFireLine(ServerWorld world, LivingEntity target, BossZone zone) {
        fireLineCastTicks = FIRE_LINE_CAST_TICKS;
        fireLineReverse = (fireLineCasts % 2 == 1); // cast 1 forward, cast 2 reverse, then alternate
        fireLineCasts++;
        fireLineHit.clear();
        this.getNavigation().stop();
        // Lock the firing fan so one of the 16 lines points exactly at the player.
        Vec3d toPlayer = horizontal(target.getPos().subtract(this.getPos()));
        fireLineBaseAngle = Math.atan2(toPlayer.z, toPlayer.x);
        // "Look behind" on the first two casts only (then the alternation is silent).
        if (fireLineCasts <= 2) {
            say(world, Text.literal("\"Look behind.\"").formatted(Formatting.DARK_RED, Formatting.ITALIC, Formatting.BOLD));
        }
        world.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 1.5f, 0.6f);
    }

    private void tickFireLine(ServerWorld world, LivingEntity target, BossZone zone, long now) {
        this.getNavigation().stop();
        this.setVelocity(0, this.getVelocity().y, 0);
        if (target != null) {
            this.getLookControl().lookAt(target, 30, 30);
        }

        if (fireLineCastTicks >= 0) {
            if (fireLineCastTicks % 2 == 0) {
                telegraphFireLines(world, zone);
            }
            if (--fireLineCastTicks < 0) {
                fireLineSweeping = true;
                fireLineFront = fireLineReverse ? zone.getRadius() : 0.0;
                world.playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 1.2f, 1.2f);
            }
            return;
        }
        if (fireLineSweeping) {
            sweepFireLines(world, zone);
        }
    }

    /** Draws all 16 lines from the boss out to the barrier so the player can read the safe gaps. */
    private void telegraphFireLines(ServerWorld world, BossZone zone) {
        Vec3d origin = new Vec3d(this.getX(), this.getBodyY(0.5), this.getZ());
        double maxLen = zone.getRadius();
        for (int i = 0; i < FIRE_LINE_DIRS; i++) {
            double a = fireLineBaseAngle + (Math.PI * 2.0 * i) / FIRE_LINE_DIRS;
            double dx = Math.cos(a);
            double dz = Math.sin(a);
            for (double d = 2.0; d <= maxLen; d += 2.5) {
                double px = origin.x + dx * d;
                double pz = origin.z + dz * d;
                if (!zone.contains(new Vec3d(px, origin.y, pz))) {
                    break;
                }
                world.spawnParticles(TELEGRAPH, px, origin.y, pz, 1, 0, 0, 0, 0.0);
            }
        }
    }

    /** Advances the fire front along every line, spawning fire and damaging anything it sweeps over. */
    private void sweepFireLines(ServerWorld world, BossZone zone) {
        Vec3d origin = new Vec3d(this.getX(), this.getBodyY(0.5), this.getZ());
        double maxLen = zone.getRadius();
        for (int i = 0; i < FIRE_LINE_DIRS; i++) {
            double a = fireLineBaseAngle + (Math.PI * 2.0 * i) / FIRE_LINE_DIRS;
            Vec3d front = new Vec3d(origin.x + Math.cos(a) * fireLineFront, origin.y,
                    origin.z + Math.sin(a) * fireLineFront);
            if (!zone.contains(front)) {
                continue;
            }
            world.spawnParticles(ModParticles.EMBER, front.x, front.y, front.z, 12, 0.25, 0.45, 0.25, 0.05);
            world.spawnParticles(ModParticles.SMOKE, front.x, front.y, front.z, 3, 0.2, 0.2, 0.2, 0.01);
            damageFireFront(world, front);
        }
        if (this.age % 4 == 0) {
            world.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.ENTITY_BLAZE_BURN, SoundCategory.HOSTILE, 0.6f, 1.0f);
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

    private void damageFireFront(ServerWorld world, Vec3d front) {
        double r = FIRE_LINE_HIT_RADIUS;
        Box box = new Box(front.x - r, front.y - 2.0, front.z - r, front.x + r, front.y + 2.0, front.z + r);
        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, box, x -> x != this && x.isAlive())) {
            if (fireLineHit.contains(e.getUuid())) {
                continue;
            }
            double dx = e.getX() - front.x;
            double dz = e.getZ() - front.z;
            if (Math.sqrt(dx * dx + dz * dz) > r) {
                continue;
            }
            if (front.y < e.getY() - 0.5 || front.y > e.getY() + e.getHeight() + 0.5) {
                continue;
            }
            fireLineHit.add(e.getUuid());
            e.damage(world.getDamageSources().magic(), magicDamage());
            e.setOnFireFor(4);
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
    private void tickMovement(ServerWorld world, LivingEntity target, long now, BossZone zone) {
        Vec3d center = zone.getCenter();
        Vec3d mPos = this.getPos();
        Vec3d tPos = target.getPos();
        double distTarget = this.distanceTo(target);
        double dxc = mPos.x - center.x;
        double dzc = mPos.z - center.z;
        double distCenter = Math.sqrt(dxc * dxc + dzc * dzc);

        final double ORBIT = 16.0;           // how far from the centre the boss hovers
        final double MAX_FROM_CENTER = 28.0; // never drift past this from the centre

        // The boss wants the side of the centre OPPOSITE the player (maximising distance, staying central).
        Vec3d desired = horizontal(center.subtract(tPos)); // points from the player, through the centre, outward
        if (this.age % 70 == 0) {
            strafeSign = -strafeSign;
        }
        Vec3d perp = new Vec3d(-desired.z, 0, desired.x).multiply(strafeSign * 0.45);
        Vec3d anchorDir = desired.add(perp);
        anchorDir = anchorDir.lengthSquared() < 1.0e-4 ? desired : anchorDir.normalize();
        Vec3d spot = center.add(anchorDir.multiply(ORBIT));

        // Re-open the gap when crowded, and peel off the wall if pushed out there.
        if (distTarget < KEEP_DISTANCE) {
            Vec3d dashDir = horizontal(mPos.subtract(tPos)).add(horizontal(center.subtract(mPos)));
            tryDash(world, dashDir, now);
        } else if (distCenter > MAX_FROM_CENTER) {
            tryDash(world, horizontal(center.subtract(mPos)), now);
        }

        this.getNavigation().startMovingTo(spot.x, spot.y, spot.z, 1.0);
    }

    private boolean tryDash(ServerWorld world, Vec3d dir, long now) {
        if (now < dashCooldownEnd || dir.lengthSquared() < 1.0e-4) {
            return false;
        }
        dashCooldownEnd = now + DASH_COOLDOWN;
        Vec3d h = horizontal(dir).multiply(1.1);
        this.setVelocity(h.x, 0.2, h.z);
        this.velocityModified = true;
        this.fallDistance = 0.0f;
        world.spawnParticles(ModParticles.GALE, this.getX(), this.getY() + 0.9, this.getZ(), 10, 0.3, 0.3, 0.3, 0.3);
        return true;
    }

    // --- Damage / death -------------------------------------------------------

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (this.getWorld().isClient) {
            return super.damage(source, amount);
        }
        // Out-of-world / the finale's own kill bypasses all protection.
        if (source.isOf(DamageTypes.OUT_OF_WORLD) || finaleDeath) {
            return super.damage(source, amount);
        }
        // Attacking The Master starts the encounter.
        if (!triggered && source.getAttacker() instanceof ServerPlayerEntity attacker
                && this.getWorld() instanceof ServerWorld world) {
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
            if (!phase2 && this.getWorld() instanceof ServerWorld world) {
                this.setHealth(1.0f);
                beginPhase2(world);
                return true;
            }
            this.setHealth(Math.max(1.0f, this.getHealth()));
            return false; // invulnerable during the countdown
        }
        return super.damage(source, amt);
    }

    @Override
    public void onDeath(DamageSource damageSource) {
        if (!this.getWorld().isClient && this.getWorld() instanceof ServerWorld world) {
            BossZoneManager.removeByOwner(this.getUuid());
            if (bossBar != null) {
                bossBar.clearPlayers();
                bossBar.setVisible(false);
            }
            WorldProgressState.get(world).markMasterDefeated();
            this.dropStack(new net.minecraft.item.ItemStack(com.example.circlemage.item.ModItems.FRAGMENT_OF_LAW));
            say(world, Text.literal("The Master falls. A fragment of something greater is left behind...")
                    .formatted(Formatting.LIGHT_PURPLE));
            for (ServerPlayerEntity p : world.getPlayers()) {
                if (p.squaredDistanceTo(this) <= 96.0 * 96.0) {
                    ModNetworking.sendJournalSync(p);
                }
            }
        }
        super.onDeath(damageSource);
    }

    @Override
    public void remove(RemovalReason reason) {
        // If The Master is removed without dying (e.g. /kill, chunk discard), tidy up the domain/bar.
        if (!this.getWorld().isClient && reason.shouldDestroy()) {
            BossZoneManager.removeByOwner(this.getUuid());
            if (bossBar != null) {
                bossBar.clearPlayers();
            }
        }
        super.remove(reason);
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return false; // a summoned/engaged boss never despawns
    }

    // --- Helpers / NBT --------------------------------------------------------

    private LivingEntity nearestPlayerInZone(ServerWorld world, BossZone zone) {
        ServerPlayerEntity best = null;
        double bestSq = Double.MAX_VALUE;
        for (ServerPlayerEntity p : world.getPlayers()) {
            if (!p.isAlive() || p.isSpectator() || p.isCreative() || !zone.contains(p.getPos())) {
                continue;
            }
            double sq = p.squaredDistanceTo(this);
            if (sq < bestSq) {
                bestSq = sq;
                best = p;
            }
        }
        return best;
    }

    private void say(ServerWorld world, Text message) {
        double r2 = 96.0 * 96.0;
        for (ServerPlayerEntity p : world.getPlayers()) {
            if (p.squaredDistanceTo(this) <= r2) {
                p.sendMessage(message, false);
            }
        }
    }

    private void spawnBeam(ServerWorld world, double x, double z, net.minecraft.particle.DefaultParticleType particle) {
        double baseY = this.getY();
        for (double y = baseY; y <= baseY + 40.0; y += 0.5) {
            world.spawnParticles(particle, x, y, z, 1, 0.05, 0.0, 0.05, 0.0);
        }
    }

    private static Vec3d horizontal(Vec3d v) {
        Vec3d h = new Vec3d(v.x, 0.0, v.z);
        return h.lengthSquared() < 1.0e-4 ? new Vec3d(0, 0, 1) : h.normalize();
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putBoolean("triggered", triggered);
        nbt.putInt("circle", circle);
        nbt.putBoolean("phase2", phase2);
        nbt.putInt("countdown", countdown);
        nbt.putInt("meteorPhasesDone", meteorPhasesDone);
        nbt.putBoolean("barrierPunished", barrierPunished);
        nbt.putFloat("fireLineThreshold", fireLineThreshold);
        nbt.putInt("fireLineCasts", fireLineCasts);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        triggered = nbt.getBoolean("triggered");
        circle = nbt.contains("circle") ? nbt.getInt("circle") : 5;
        phase2 = nbt.getBoolean("phase2");
        countdown = nbt.contains("countdown") ? nbt.getInt("countdown") : -1;
        meteorPhasesDone = nbt.getInt("meteorPhasesDone");
        barrierPunished = nbt.getBoolean("barrierPunished");
        fireLineThreshold = nbt.contains("fireLineThreshold") ? nbt.getFloat("fireLineThreshold") : 0.80f;
        fireLineCasts = nbt.getInt("fireLineCasts");
    }
}
