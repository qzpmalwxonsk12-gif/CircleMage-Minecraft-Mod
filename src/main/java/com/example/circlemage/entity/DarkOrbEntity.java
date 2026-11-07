package com.example.circlemage.entity;

import com.example.circlemage.mana.ManaPlayer;
import com.example.circlemage.network.ModNetworking;
import com.example.circlemage.particle.ModParticles;
import com.example.circlemage.spell.AreaEffect;
import com.example.circlemage.spell.AreaEffectManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * The 6th-circle transform of Area Slow: a gravity-free void orb (range-capped) with two forms:
 * <ul>
 *     <li><b>Normal</b> (thrown): flies straight and explodes where it lands into a wide Slowness
 *     field that also immobilises everything caught in it for one second.</li>
 *     <li><b>Active chant</b> (floating): hovers where it stops instead of exploding. Hitting it bursts
 *     a tighter Slowness II + Poison field with the same root. If hit by an active-chant Spear of Light
 *     it instead absorbs the spear and detonates as Hollow Purple after a 3-second collapse — and if
 *     both the orb and the spear were fully charged, that detonation is a 2.5x cataclysm.</li>
 * </ul>
 */
public class DarkOrbEntity extends ProjectileEntity {

    private static final TrackedData<Boolean> ABSORBING =
            DataTracker.registerData(DarkOrbEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    /** Synced so clients can target a floating orb with melee/arrows (server drives the rest). */
    private static final TrackedData<Boolean> FLOATING =
            DataTracker.registerData(DarkOrbEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    /** Synced render scale (the orb shrinks as it compresses before detonating). */
    private static final TrackedData<Float> SCALE =
            DataTracker.registerData(DarkOrbEntity.class, TrackedDataHandlerRegistry.FLOAT);
    /** Synced so the renderer hides the body while the blast carries the visuals. */
    private static final TrackedData<Boolean> DETONATING =
            DataTracker.registerData(DarkOrbEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    private static final double NORMAL_SPEED = 1.2;
    private static final double FLOAT_SPEED = 1.4;
    private static final double MAX_DISTANCE = 30.0;       // thrown orb cap
    private static final double FLOAT_MAX_DISTANCE = 50.0; // floating (active-chant) orb cap

    private static final double NORMAL_RADIUS = 10.0;
    private static final int NORMAL_DURATION = 160; // 8s, Slowness I
    private static final double ACTIVE_RADIUS = 6.0;
    private static final int ACTIVE_DURATION = 120; // 6s, Slowness II + Poison
    private static final int IMMOBILIZE_TICKS = 20; // 1s
    private static final int IMMOBILIZE_AMP = 6;    // effectively rooted
    private static final int ABSORB_DELAY = 60;     // 3s collapse before Hollow Purple
    private static final int HOVER_LIFETIME = 200;  // 10s floating before fizzling

    // Hollow Purple blast tuning. Super = both orb and spear fully charged.
    private static final float NUKE_POWER = 10.0f;
    private static final double NUKE_RADIUS = 12.0;
    private static final float SUPER_POWER = 27.0f;   // 1.5x the explosion power (was 18; 1.0.8.1)
    private static final double SUPER_RADIUS = 30.0;  // 2.5x effect radius
    private static final double SUPER_DAMAGE_SCALE = 3.75; // 1.5x damage (was 2.5; 1.0.8.1)

    // Detonation phase timing.
    private static final int FLASH_TICKS = 3;
    private static final int BLOWOUT_TICKS = 5;   // expanding white light sphere
    private static final int SHOCK_TICKS = 16;    // shockwave ring front (outruns the smoke)
    private static final int DETO_LIFETIME = 200; // 10s residual energy scar before the orb is removed

    private boolean floating = false;
    private boolean fullyChargedOrb = false;
    private boolean landed = false;
    private boolean superNuke = false;
    /** Scales the super-nuke's power/radius/damage (1.0 = the full Hollow Purple; bosses may reduce it). */
    private double blastScale = 1.0;
    /** Effective radius used by the detonation aftermath (set when the blast fires). */
    private double detoRadius = SUPER_RADIUS;
    private double traveled = 0.0;
    private int hoverTicks = HOVER_LIFETIME;
    private int explodeTimer = -1;
    private boolean detonating = false;
    private int detoAge = 0;

    public DarkOrbEntity(EntityType<? extends DarkOrbEntity> type, World world) {
        super(type, world);
        this.setNoGravity(true);
    }

    public DarkOrbEntity(World world, LivingEntity owner, boolean floating, boolean fullyCharged) {
        this(ModEntities.DARK_ORB, world);
        this.setOwner(owner);
        this.floating = floating;
        this.fullyChargedOrb = fullyCharged;
        this.dataTracker.set(FLOATING, floating);
        Vec3d eye = owner.getEyePos();
        this.setPosition(eye.x, eye.y - 0.1, eye.z);
    }

    public void launch(Vec3d direction) {
        this.setVelocity(direction.normalize().multiply(floating ? FLOAT_SPEED : NORMAL_SPEED));
    }

    /**
     * Immediately detonates this orb as the super "Hollow Purple" cataclysm at its current position
     * (5-phase VFX). {@code scale} multiplies the power/radius/damage (1.0 = full; The Master uses 0.5).
     * Spawn the orb where the blast should occur, then call this.
     */
    public void triggerGroundCataclysm(double scale) {
        this.superNuke = true;
        this.blastScale = scale;
        this.setVelocity(Vec3d.ZERO);
        if (this.getWorld() instanceof ServerWorld world) {
            detonate(world);
        }
    }

    public boolean isAbsorbing() {
        return this.dataTracker.get(ABSORBING);
    }

    public boolean isDetonating() {
        return this.dataTracker.get(DETONATING);
    }

    public float getScale() {
        return this.dataTracker.get(SCALE);
    }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(ABSORBING, false);
        this.dataTracker.startTracking(FLOATING, false);
        this.dataTracker.startTracking(SCALE, 1.0f);
        this.dataTracker.startTracking(DETONATING, false);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.getWorld() instanceof ServerWorld world)) {
            return;
        }

        // Aftermath of a Hollow Purple detonation: spawn the shockwave/aftermath, then expire.
        if (detonating) {
            tickDetonation(world);
            return;
        }

        // Collapsing after absorbing a spear. The super version plays the full instability/compression
        // buildup; the normal chaos blast just swirls (1.0.3) before its explosion.
        if (explodeTimer >= 0) {
            if (superNuke) {
                tickChargeUp(world);
            } else {
                spawnAbsorbParticles(world);
            }
            this.setVelocity(Vec3d.ZERO);
            if (--explodeTimer < 0) {
                if (superNuke) {
                    detonate(world);
                } else {
                    normalChaosExplosion(world);
                }
            }
            return;
        }

        if (floating && landed) {
            this.setVelocity(Vec3d.ZERO);
            spawnIdleParticles(world);
            if (--hoverTicks <= 0) {
                fizzle();
            }
            return;
        }

        // Straight, gravity-free flight.
        Vec3d velocity = this.getVelocity();
        HitResult hit = ProjectileUtil.getCollision(this, this::canHit);
        if (hit.getType() != HitResult.Type.MISS) {
            this.onCollision(hit);
            // Stop here if the collision removed us or settled a floating orb (don't advance into the block).
            if (this.isRemoved() || landed) {
                return;
            }
        }

        Vec3d next = this.getPos().add(velocity);
        this.setPosition(next.x, next.y, next.z);
        traveled += velocity.length();

        world.spawnParticles(ModParticles.VOID, getX(), getY(), getZ(), 3, 0.07, 0.07, 0.07, 0.01);

        double maxDist = floating ? FLOAT_MAX_DISTANCE : MAX_DISTANCE;
        if (traveled >= maxDist) {
            if (floating) {
                land();
            } else {
                explode(false);
            }
        }
    }

    @Override
    protected void onBlockHit(BlockHitResult blockHitResult) {
        super.onBlockHit(blockHitResult);
        if (this.getWorld().isClient) {
            return;
        }
        if (floating) {
            // Settle just outside the block (and a touch higher) so the orb hovers above the surface
            // rather than phasing into it.
            Vec3d dir = this.getVelocity().lengthSquared() > 1.0e-6 ? this.getVelocity().normalize() : new Vec3d(0, 0, 1);
            Vec3d p = blockHitResult.getPos().subtract(dir.multiply(0.7));
            this.setPosition(p.x, p.y + 0.5, p.z);
            land();
        } else {
            explode(false);
        }
    }

    @Override
    protected void onEntityHit(EntityHitResult entityHitResult) {
        super.onEntityHit(entityHitResult);
        if (this.getWorld().isClient || floating) {
            return; // floating orbs pass through entities until they settle
        }
        explode(false);
    }

    /** During flight a floating orb ignores entities (only blocks settle it); a normal orb hits all. */
    @Override
    protected boolean canHit(Entity entity) {
        if (floating && !landed) {
            return false;
        }
        return super.canHit(entity) && entity != this.getOwner();
    }

    /** Whether OTHER projectiles/attacks can strike this orb — only the live floating form is a target. */
    @Override
    public boolean canHit() {
        return this.dataTracker.get(FLOATING) && !this.isRemoved() && explodeTimer < 0 && !detonating;
    }

    @Override
    public boolean isAttackable() {
        return this.dataTracker.get(FLOATING) && !detonating;
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (this.getWorld().isClient || !floating || this.isRemoved() || explodeTimer >= 0 || detonating) {
            return false;
        }
        Entity src = source.getSource();
        // An active-chant spear is absorbed into a Hollow Purple blast. If BOTH the orb and the spear
        // were fully charged it's the super cataclysm (full 5-phase); otherwise it's the normal chaos
        // blast (the 1.0.3-style explosion). Any other hit is just the small active burst.
        if (src instanceof MagicMissileEntity spear && spear.isSpear() && spear.isEnhanced()) {
            beginAbsorb(fullyChargedOrb && spear.isFullyCharged());
        } else {
            explode(true);
        }
        return true;
    }

    private void land() {
        landed = true;
        this.setVelocity(Vec3d.ZERO);
        if (this.getWorld() instanceof ServerWorld world) {
            world.playSound(null, getX(), getY(), getZ(),
                    SoundEvents.BLOCK_SCULK_CATALYST_BLOOM, SoundCategory.PLAYERS, 0.7f, 0.7f);
        }
    }

    private void beginAbsorb(boolean superVersion) {
        // superVersion: both spells fully charged -> the full 5-phase cataclysm.
        // otherwise: the normal chaos blast (1.0.3-style explosion after the same collapse delay).
        explodeTimer = ABSORB_DELAY;
        landed = true;
        superNuke = superVersion;
        this.setVelocity(Vec3d.ZERO);
        this.dataTracker.set(ABSORBING, true);
        if (this.getWorld() instanceof ServerWorld world) {
            world.playSound(null, getX(), getY(), getZ(),
                    SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 0.9f, 0.5f);
        }
    }

    // --- Phases 1 & 2: instability and energy compression --------------------

    private void tickChargeUp(ServerWorld world) {
        int elapsed = ABSORB_DELAY - explodeTimer;
        float progress = (float) elapsed / ABSORB_DELAY; // 0..1
        boolean compression = progress >= 0.5f;
        Vec3d c = this.getPos();

        // Shrinking sphere: gentle pulse during instability, then collapse during compression.
        float scale;
        if (!compression) {
            scale = 0.95f + 0.1f * (float) Math.sin(elapsed * 0.6);
        } else {
            float cp = (progress - 0.5f) / 0.5f;
            scale = 1.0f - 0.8f * cp;
        }
        this.dataTracker.set(SCALE, Math.max(0.15f, scale));

        // Orbiting particles spiralling inward; density and speed ramp up over time.
        int density = compression ? 7 : 3 + (int) (progress * 5);
        double baseR = compression ? (3.0 * (1.0f - (progress - 0.5f) / 0.5f) + 0.6) : 3.2;
        double spin = elapsed * (compression ? 0.9 : 0.5);
        double inward = compression ? 0.35 : 0.18;
        for (int i = 0; i < density; i++) {
            double a = spin + i * (Math.PI * 2.0 / density);
            double rr = baseR * (0.5 + world.random.nextDouble() * 0.6);
            double px = c.x + Math.cos(a) * rr;
            double pz = c.z + Math.sin(a) * rr;
            double py = c.y + (world.random.nextDouble() * 2 - 1) * rr * 0.5;
            world.spawnParticles(ModParticles.VOID, px, py, pz, 0,
                    (c.x - px), (c.y - py), (c.z - pz), inward);
            if (i % 2 == 0) {
                world.spawnParticles(ModParticles.LIGHT, px, py, pz, 0,
                        (c.x - px), (c.y - py), (c.z - pz), inward);
            }
        }

        // Distortion ring sweeping around the orb.
        if (elapsed % 4 == 0) {
            int pts = 24;
            double rr = baseR + 1.2;
            for (int i = 0; i < pts; i++) {
                double a = spin + i * (Math.PI * 2.0 / pts);
                world.spawnParticles(ModParticles.VOID, c.x + Math.cos(a) * rr, c.y, c.z + Math.sin(a) * rr,
                        1, 0, 0, 0, 0.0);
            }
        }

        // Pulsing brightness at the core.
        if (elapsed % (compression ? 2 : 5) == 0) {
            world.spawnParticles(ModParticles.RADIANCE, c.x, c.y, c.z, 2, 0.1, 0.1, 0.1, 0.0);
        }

        // Phase 2: a suction nobody walks out of, plus air dragged inward.
        if (compression) {
            double suctionR = 14.0;
            Box box = new Box(c.x - suctionR, c.y - suctionR, c.z - suctionR,
                    c.x + suctionR, c.y + suctionR, c.z + suctionR);
            for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, box, LivingEntity::isAlive)) {
                Vec3d to = c.subtract(e.getPos());
                double dist = to.length();
                if (dist < 0.6 || dist > suctionR) {
                    continue;
                }
                double strength = 0.3 + 0.55 * (1.0 - dist / suctionR);
                Vec3d pull = to.normalize().multiply(strength);
                e.addVelocity(pull.x, pull.y * 0.4, pull.z);
                e.velocityModified = true;
            }
            for (int i = 0; i < 8; i++) {
                double a = world.random.nextDouble() * Math.PI * 2.0;
                double rr = 8.0 + world.random.nextDouble() * 4.0;
                double px = c.x + Math.cos(a) * rr;
                double pz = c.z + Math.sin(a) * rr;
                double py = c.y + (world.random.nextDouble() * 2 - 1) * 3.0;
                world.spawnParticles(ModParticles.VOID, px, py, pz, 0,
                        (c.x - px), (c.y - py), (c.z - pz), 0.5);
            }
        }

        // Black-and-white energy tendrils that reach out, then snap back inward as detonation nears.
        if (elapsed % 2 == 0) {
            int tendrils = 5;
            double reach = compression ? (5.0 + 5.0 * ((progress - 0.5) / 0.5)) : (1.5 + 3.0 * (progress / 0.5));
            for (int t = 0; t < tendrils; t++) {
                double a = (Math.PI * 2.0 * t / tendrils) + elapsed * 0.15;
                for (double s = 0.5; s <= reach; s += 0.7) {
                    double px = c.x + Math.cos(a) * s;
                    double py = c.y + Math.sin(elapsed * 0.2 + t) * 0.4;
                    double pz = c.z + Math.sin(a) * s;
                    DefaultParticleType p = (((int) (s * 2)) % 2 == 0) ? ModParticles.VOID : ModParticles.ARCANE_TRAIL;
                    double dirx;
                    double diry;
                    double dirz;
                    double sp;
                    if (compression) {
                        dirx = c.x - px; diry = c.y - py; dirz = c.z - pz; sp = 0.55; // snap inward
                    } else {
                        dirx = px - c.x; diry = py - c.y; dirz = pz - c.z; sp = 0.15; // reach outward
                    }
                    world.spawnParticles(p, px, py, pz, 0, dirx, diry, dirz, sp);
                }
            }
        }

        // Light screen shake that builds into the collapse.
        if (elapsed % 4 == 0) {
            float shake = compression ? (0.4f + 1.2f * ((progress - 0.5f) / 0.5f)) : 0.25f;
            ModNetworking.sendShakeFalloff(world, c.x, c.y, c.z, 40.0, shake, 6);
        }

        // Rising pitch.
        if (elapsed % 6 == 0) {
            world.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.PLAYERS,
                    0.6f, 0.4f + progress * 1.4f);
        }
    }

    // --- Phase 3: detonation flash (and the blast itself) --------------------

    private void detonate(ServerWorld world) {
        Vec3d c = this.getPos();
        double scale = superNuke ? blastScale : 1.0;
        double radius = (superNuke ? SUPER_RADIUS : NUKE_RADIUS) * scale;
        float power = (float) ((superNuke ? SUPER_POWER : NUKE_POWER) * scale);
        double dmgScale = (superNuke ? SUPER_DAMAGE_SCALE : 1.0) * scale;
        this.detoRadius = radius;

        world.createExplosion(this, c.x, c.y, c.z, power, false, World.ExplosionSourceType.TNT);
        AreaEffectManager.add(new AreaEffect(world, c, radius, AreaEffect.Type.SLOW, 200, 1, 0.0f).setPoison(true));
        immobilize(world, c, radius);

        // Flat, blast-resistance-ignoring damage + outward knockback (the shockwave begins).
        Box box = new Box(c.x - radius, c.y - radius, c.z - radius, c.x + radius, c.y + radius, c.z + radius);
        for (LivingEntity living : world.getEntitiesByClass(LivingEntity.class, box, LivingEntity::isAlive)) {
            if (living instanceof ServerPlayerEntity p && ManaPlayer.get(p).isImmune(world.getTime())) {
                continue;
            }
            double d = living.getPos().distanceTo(c);
            if (d > radius) {
                continue;
            }
            float dmg = (float) (40.0 * dmgScale * (1.0 - d / radius)) + 6.0f;
            living.damage(world.getDamageSources().magic(), dmg);
            Vec3d kb = living.getPos().subtract(c);
            if (kb.lengthSquared() < 1.0e-4) {
                kb = new Vec3d(0, 1, 0);
            }
            kb = kb.normalize().multiply(1.6 * (1.0 - d / radius) + 0.4).add(0, 0.55, 0);
            living.addVelocity(kb.x, kb.y, kb.z);
            living.velocityModified = true;
        }

        // Phase 3: the impact frame (B&W desaturation + white blowout) and the blinding flash.
        ModNetworking.sendImpact(world, c.x, c.y, c.z, 80.0, 3);
        spawnFlash(world, c);

        // Phase 1 of the camera shake: the initial impact (stronger the closer you are).
        ModNetworking.sendShakeFalloff(world, c.x, c.y, c.z, 70.0, 3.6f, 12);

        // Layered detonation audio: a sharp crack, then a deep bass impact.
        world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.PLAYERS, 3.0f, 1.4f);
        world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 4.0f, 0.5f);
        world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 4.0f, 0.4f);

        detonating = true;
        detoAge = 0;
        this.dataTracker.set(DETONATING, true);
        this.dataTracker.set(ABSORBING, false);
        this.setVelocity(Vec3d.ZERO);
    }

    private void spawnFlash(ServerWorld world, Vec3d c) {
        world.spawnParticles(ModParticles.RADIANCE, c.x, c.y + 1.0, c.z, superNuke ? 120 : 70, 0.6, 0.6, 0.6, 0.5);
        world.spawnParticles(ModParticles.ARCANE_TRAIL, c.x, c.y + 1.0, c.z, superNuke ? 180 : 100, 1.0, 1.0, 1.0, 0.8);
        // Light rays bursting outward like an impact frame.
        int rays = superNuke ? 48 : 28;
        for (int i = 0; i < rays; i++) {
            double phi = Math.acos(1.0 - 2.0 * (i + 0.5) / rays);
            double theta = Math.PI * (1.0 + Math.sqrt(5.0)) * i;
            double sx = Math.sin(phi) * Math.cos(theta);
            double sy = Math.cos(phi);
            double sz = Math.sin(phi) * Math.sin(theta);
            world.spawnParticles(ModParticles.LIGHT, c.x, c.y + 1.0, c.z, 0, sx, sy, sz, superNuke ? 1.6 : 1.2);
        }
    }

    // --- Phases 4 & 5: shockwave and aftermath -------------------------------

    private void tickDetonation(ServerWorld world) {
        detoAge++;
        Vec3d c = this.getPos();
        double radius = detoRadius;

        // Phase 3 tail: a couple more frames of blinding white.
        if (detoAge <= FLASH_TICKS) {
            world.spawnParticles(ModParticles.RADIANCE, c.x, c.y + 1.0, c.z, 30, 0.7, 0.7, 0.7, 0.4);
            world.spawnParticles(ModParticles.ARCANE_TRAIL, c.x, c.y + 1.0, c.z, 40, 0.9, 0.9, 0.9, 0.6);
        }

        // Light blowout: a large white sphere expands from the core then fades.
        if (detoAge <= BLOWOUT_TICKS) {
            double br = radius * 0.7 * ((double) detoAge / BLOWOUT_TICKS);
            int pts = 90;
            for (int i = 0; i < pts; i++) {
                double phi = Math.acos(1.0 - 2.0 * (i + 0.5) / pts);
                double theta = Math.PI * (1.0 + Math.sqrt(5.0)) * i;
                double sx = Math.sin(phi) * Math.cos(theta);
                double sy = Math.cos(phi);
                double sz = Math.sin(phi) * Math.sin(theta);
                world.spawnParticles(ModParticles.RADIANCE, c.x + sx * br, c.y + 1.0 + sy * br, c.z + sz * br, 1, 0, 0, 0, 0.0);
            }
        }

        // Phase 4: the shockwave front — fast expanding rings on three planes (outruns the smoke).
        if (detoAge <= SHOCK_TICKS) {
            double rr = radius * ((double) detoAge / SHOCK_TICKS);
            ringPlane(world, c, rr, 0, 1, 0, ModParticles.LIGHT);
            ringPlane(world, c, rr, 1, 0, 0, ModParticles.VOID);
            ringPlane(world, c, rr, 0.7, 0.7, 0, ModParticles.VOID);
            for (int i = 0; i < 12; i++) {
                double a = world.random.nextDouble() * Math.PI * 2.0;
                world.spawnParticles(ModParticles.DUST, c.x + Math.cos(a) * rr, c.y + 0.3, c.z + Math.sin(a) * rr,
                        0, Math.cos(a), 0.2, Math.sin(a), 0.4);
            }
        }

        // Camera shake phase 2: the secondary shockwave jolt as the front sweeps past.
        if (detoAge == 10) {
            ModNetworking.sendShakeFalloff(world, c.x, c.y, c.z, 80.0, 2.2f, 12);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 3.0f, 0.7f);
        }

        // Phase 5: lingering aftermath in distinct layers (smoke / sparks / dust / residual magic).
        if (detoAge % 3 == 0) {
            for (int i = 0; i < 7; i++) {
                double a = world.random.nextDouble() * Math.PI * 2.0;
                double rr = world.random.nextDouble() * radius * 0.55;
                world.spawnParticles(ModParticles.SMOKE, c.x + Math.cos(a) * rr, c.y + 0.5 + world.random.nextDouble() * 2.5,
                        c.z + Math.sin(a) * rr, 1, 0.2, 0.1, 0.2, 0.02);
            }
            for (int i = 0; i < 3; i++) {
                double a = world.random.nextDouble() * Math.PI * 2.0;
                double rr = world.random.nextDouble() * radius * 0.5;
                world.spawnParticles(ModParticles.SPARK, c.x + Math.cos(a) * rr, c.y + 0.4 + world.random.nextDouble() * 1.8,
                        c.z + Math.sin(a) * rr, 1, 0.05, 0.2, 0.05, 0.05);
            }
            for (int i = 0; i < 3; i++) {
                double a = world.random.nextDouble() * Math.PI * 2.0;
                double rr = world.random.nextDouble() * radius * 0.6;
                world.spawnParticles(ModParticles.DUST, c.x + Math.cos(a) * rr, c.y + 0.2,
                        c.z + Math.sin(a) * rr, 1, 0.1, 0.0, 0.1, 0.02);
            }
            world.spawnParticles(ModParticles.ARCANE, c.x, c.y + 0.6, c.z, 2, radius * 0.3, 0.4, radius * 0.3, 0.02);
        }

        // Residual energy scar (whole 10s): black-and-white motes slowly orbiting the crater.
        double scarR = 3.5 + 1.5 * Math.sin(detoAge * 0.05);
        double ang = detoAge * 0.2;
        for (int k = 0; k < 2; k++) {
            double a = ang + k * Math.PI;
            double bx = c.x + Math.cos(a) * scarR;
            double bz = c.z + Math.sin(a) * scarR;
            double by = c.y + 0.4 + 0.5 * Math.sin(detoAge * 0.1 + k);
            world.spawnParticles(ModParticles.VOID, bx, by, bz, 0, (c.x - bx) * 0.02, 0.0, (c.z - bz) * 0.02, 0.3);
            world.spawnParticles(ModParticles.ARCANE_TRAIL, c.x - Math.cos(a) * scarR, by, c.z - Math.sin(a) * scarR,
                    1, 0, 0, 0, 0.0);
        }

        // Lingering energy hum.
        if (detoAge % 24 == 0) {
            world.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_CONDUIT_AMBIENT, SoundCategory.PLAYERS, 0.5f, 0.5f);
        }

        if (detoAge >= DETO_LIFETIME) {
            this.discard();
        }
    }

    /** A ring of particles in the plane whose normal is (nx,ny,nz). */
    private void ringPlane(ServerWorld world, Vec3d c, double r, double nx, double ny, double nz, DefaultParticleType p) {
        double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
        nx /= nl;
        ny /= nl;
        nz /= nl;
        // Pick a helper axis not parallel to the normal, then build two in-plane axes.
        double hx = Math.abs(ny) < 0.9 ? 0 : 1;
        double hy = Math.abs(ny) < 0.9 ? 1 : 0;
        double hz = 0;
        // u = normalize(cross(n, h))
        double ux = ny * hz - nz * hy;
        double uy = nz * hx - nx * hz;
        double uz = nx * hy - ny * hx;
        double ul = Math.sqrt(ux * ux + uy * uy + uz * uz);
        ux /= ul;
        uy /= ul;
        uz /= ul;
        // v = cross(n, u)
        double vx = ny * uz - nz * uy;
        double vy = nz * ux - nx * uz;
        double vz = nx * uy - ny * ux;
        int pts = Math.min(72, Math.max(16, (int) (r * 3)));
        for (int i = 0; i < pts; i++) {
            double a = (Math.PI * 2.0 * i) / pts;
            double ca = Math.cos(a);
            double sa = Math.sin(a);
            double px = c.x + (ux * ca + vx * sa) * r;
            double py = c.y + 0.5 + (uy * ca + vy * sa) * r;
            double pz = c.z + (uz * ca + vz * sa) * r;
            world.spawnParticles(p, px, py, pz, 1, 0, 0, 0, 0.0);
        }
    }

    private void explode(boolean active) {
        if (!(this.getWorld() instanceof ServerWorld world)) {
            return;
        }
        Vec3d center = this.getPos();
        double radius = active ? ACTIVE_RADIUS : NORMAL_RADIUS;
        int duration = active ? ACTIVE_DURATION : NORMAL_DURATION;
        int amp = active ? 1 : 0;

        AreaEffectManager.add(new AreaEffect(world, center, radius, AreaEffect.Type.SLOW, duration, amp, 0.0f)
                .setPoison(active));
        immobilize(world, center, radius);

        world.spawnParticles(ModParticles.VOID, center.x, center.y + 0.4, center.z,
                (int) (radius * 12), radius * 0.4, 0.5, radius * 0.4, 0.08);
        world.spawnParticles(ModParticles.FROST, center.x, center.y + 0.4, center.z,
                (int) (radius * 6), radius * 0.4, 0.4, radius * 0.4, 0.05);
        world.playSound(null, center.x, center.y, center.z,
                SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.0f, 0.4f);
        this.discard();
    }

    /**
     * The normal chaos blast (1.0.3): an active-chant spear absorbed into the orb without both being
     * fully charged. A single mid-size Hollow Purple explosion with its lingering void field — no
     * 5-phase collapse, suction, impact frame, or aftermath.
     */
    private void normalChaosExplosion(ServerWorld world) {
        Vec3d c = this.getPos();

        world.createExplosion(this, c.x, c.y, c.z, NUKE_POWER, false, World.ExplosionSourceType.TNT);
        AreaEffectManager.add(new AreaEffect(world, c, NUKE_RADIUS, AreaEffect.Type.SLOW, 200, 1, 0.0f).setPoison(true));
        immobilize(world, c, NUKE_RADIUS);

        Box box = new Box(c.x - NUKE_RADIUS, c.y - NUKE_RADIUS, c.z - NUKE_RADIUS,
                c.x + NUKE_RADIUS, c.y + NUKE_RADIUS, c.z + NUKE_RADIUS);
        for (LivingEntity living : world.getEntitiesByClass(LivingEntity.class, box, LivingEntity::isAlive)) {
            if (living instanceof ServerPlayerEntity p && ManaPlayer.get(p).isImmune(world.getTime())) {
                continue;
            }
            double d = living.getPos().distanceTo(c);
            if (d > NUKE_RADIUS) {
                continue;
            }
            float dmg = (float) (40.0 * (1.0 - d / NUKE_RADIUS)) + 6.0f;
            living.damage(world.getDamageSources().magic(), dmg);
        }

        spawnNormalBlastParticles(world, c);
        world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 4.0f, 0.6f);
        world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 4.0f, 0.5f);
        this.discard();
    }

    /** Purple-and-white bloom for the normal chaos blast: bright core, void sphere, shock rings. */
    private void spawnNormalBlastParticles(ServerWorld world, Vec3d c) {
        world.spawnParticles(ModParticles.RADIANCE, c.x, c.y + 1.0, c.z, 40, 0.6, 0.6, 0.6, 0.1);
        world.spawnParticles(ModParticles.ARCANE_TRAIL, c.x, c.y + 1.0, c.z, 60, 1.0, 1.0, 1.0, 0.3);
        for (int i = 0; i < 260; i++) {
            double phi = Math.acos(1.0 - 2.0 * world.random.nextDouble());
            double theta = world.random.nextDouble() * Math.PI * 2.0;
            double rr = NUKE_RADIUS * Math.cbrt(world.random.nextDouble());
            double sx = Math.sin(phi) * Math.cos(theta);
            double sy = Math.cos(phi);
            double sz = Math.sin(phi) * Math.sin(theta);
            world.spawnParticles(ModParticles.VOID, c.x + sx * rr, c.y + 1.0 + sy * rr, c.z + sz * rr, 1, 0, 0, 0, 0.0);
        }
        for (double ring = 2.0; ring <= NUKE_RADIUS; ring += 2.0) {
            int pts = (int) (ring * 6);
            for (int i = 0; i < pts; i++) {
                double a = (Math.PI * 2.0 * i) / pts;
                double px = c.x + Math.cos(a) * ring;
                double pz = c.z + Math.sin(a) * ring;
                world.spawnParticles(ModParticles.VOID, px, c.y + 0.3, pz, 1, 0, 0, 0, 0.0);
                if (((int) ring) % 4 == 0) {
                    world.spawnParticles(ModParticles.LIGHT, px, c.y + 0.3, pz, 1, 0, 0, 0, 0.0);
                }
            }
        }
    }

    /** Oil-and-water swirl shown while the normal chaos blast collapses (1.0.3). */
    private void spawnAbsorbParticles(ServerWorld world) {
        world.spawnParticles(ModParticles.VOID, getX(), getY(), getZ(), 4, 0.35, 0.35, 0.35, 0.02);
        world.spawnParticles(ModParticles.ARCANE_TRAIL, getX(), getY(), getZ(), 3, 0.35, 0.35, 0.35, 0.02);
        world.spawnParticles(ModParticles.LIGHT, getX(), getY(), getZ(), 2, 0.3, 0.3, 0.3, 0.01);
    }

    /** Roots every (non-immune) living entity in range for one second. */
    private void immobilize(ServerWorld world, Vec3d center, double radius) {
        Box box = new Box(center.x - radius, center.y - radius, center.z - radius,
                center.x + radius, center.y + radius, center.z + radius);
        for (LivingEntity living : world.getEntitiesByClass(LivingEntity.class, box, LivingEntity::isAlive)) {
            double dx = living.getX() - center.x;
            double dz = living.getZ() - center.z;
            if ((dx * dx + dz * dz) > radius * radius) {
                continue;
            }
            if (living instanceof ServerPlayerEntity player && ManaPlayer.get(player).isImmune(world.getTime())) {
                continue;
            }
            living.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, IMMOBILIZE_TICKS, IMMOBILIZE_AMP, false, true, true));
            living.setVelocity(Vec3d.ZERO);
            living.velocityModified = true;
        }
    }

    private void fizzle() {
        if (this.getWorld() instanceof ServerWorld world) {
            world.spawnParticles(ModParticles.VOID, getX(), getY(), getZ(), 16, 0.3, 0.3, 0.3, 0.05);
            world.playSound(null, getX(), getY(), getZ(),
                    SoundEvents.BLOCK_SCULK_SHRIEKER_SHRIEK, SoundCategory.PLAYERS, 0.4f, 1.4f);
        }
        this.discard();
    }

    private void spawnIdleParticles(ServerWorld world) {
        if (world.getTime() % 3 == 0) {
            world.spawnParticles(ModParticles.VOID, getX(), getY(), getZ(), 2, 0.25, 0.25, 0.25, 0.01);
        }
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putBoolean("floating", floating);
        nbt.putBoolean("fullyCharged", fullyChargedOrb);
        nbt.putBoolean("landed", landed);
        nbt.putBoolean("superNuke", superNuke);
        nbt.putDouble("blastScale", blastScale);
        nbt.putDouble("detoRadius", detoRadius);
        nbt.putDouble("traveled", traveled);
        nbt.putInt("hover", hoverTicks);
        nbt.putInt("explodeTimer", explodeTimer);
        nbt.putBoolean("detonating", detonating);
        nbt.putInt("detoAge", detoAge);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        floating = nbt.getBoolean("floating");
        fullyChargedOrb = nbt.getBoolean("fullyCharged");
        this.dataTracker.set(FLOATING, floating);
        landed = nbt.getBoolean("landed");
        superNuke = nbt.getBoolean("superNuke");
        if (nbt.contains("blastScale")) {
            blastScale = nbt.getDouble("blastScale");
        }
        if (nbt.contains("detoRadius")) {
            detoRadius = nbt.getDouble("detoRadius");
        }
        traveled = nbt.getDouble("traveled");
        hoverTicks = nbt.contains("hover") ? nbt.getInt("hover") : HOVER_LIFETIME;
        explodeTimer = nbt.contains("explodeTimer") ? nbt.getInt("explodeTimer") : -1;
        detonating = nbt.getBoolean("detonating");
        detoAge = nbt.getInt("detoAge");
        this.setNoGravity(true);
    }

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket() {
        return new EntitySpawnS2CPacket(this);
    }
}
