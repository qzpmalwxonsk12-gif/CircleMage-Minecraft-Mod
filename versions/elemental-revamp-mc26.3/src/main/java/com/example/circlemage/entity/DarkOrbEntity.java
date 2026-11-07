package com.example.circlemage.entity;

import com.example.circlemage.mana.ManaPlayer;
import com.example.circlemage.network.ModNetworking;
import com.example.circlemage.particle.ModParticles;
import com.example.circlemage.spell.AreaEffect;
import com.example.circlemage.spell.AreaEffectManager;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

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
public class DarkOrbEntity extends Projectile {

    private static final EntityDataAccessor<Boolean> ABSORBING =
            SynchedEntityData.defineId(DarkOrbEntity.class, EntityDataSerializers.BOOLEAN);
    /** Synced so clients can target a floating orb with melee/arrows (server drives the rest). */
    private static final EntityDataAccessor<Boolean> FLOATING =
            SynchedEntityData.defineId(DarkOrbEntity.class, EntityDataSerializers.BOOLEAN);
    /** Synced render scale (the orb shrinks as it compresses before detonating). */
    private static final EntityDataAccessor<Float> SCALE =
            SynchedEntityData.defineId(DarkOrbEntity.class, EntityDataSerializers.FLOAT);
    /** Synced so the renderer hides the body while the blast carries the visuals. */
    private static final EntityDataAccessor<Boolean> DETONATING =
            SynchedEntityData.defineId(DarkOrbEntity.class, EntityDataSerializers.BOOLEAN);

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

    public DarkOrbEntity(EntityType<? extends DarkOrbEntity> type, Level world) {
        super(type, world);
        this.setNoGravity(true);
    }

    public DarkOrbEntity(Level world, LivingEntity owner, boolean floating, boolean fullyCharged) {
        this(ModEntities.DARK_ORB, world);
        this.setOwner(owner);
        this.floating = floating;
        this.fullyChargedOrb = fullyCharged;
        this.entityData.set(FLOATING, floating);
        Vec3 eye = owner.getEyePosition();
        this.setPos(eye.x, eye.y - 0.1, eye.z);
    }

    public void launch(Vec3 direction) {
        this.setDeltaMovement(direction.normalize().scale(floating ? FLOAT_SPEED : NORMAL_SPEED));
    }

    /**
     * Immediately detonates this orb as the super "Hollow Purple" cataclysm at its current position
     * (5-phase VFX). {@code scale} multiplies the power/radius/damage (1.0 = full; The Master uses 0.5).
     * Spawn the orb where the blast should occur, then call this.
     */
    public void triggerGroundCataclysm(double scale) {
        this.superNuke = true;
        this.blastScale = scale;
        this.setDeltaMovement(Vec3.ZERO);
        if (this.level() instanceof ServerLevel world) {
            detonate(world);
        }
    }

    public boolean isAbsorbing() {
        return this.entityData.get(ABSORBING);
    }

    public boolean isDetonating() {
        return this.entityData.get(DETONATING);
    }

    public float getScale() {
        return this.entityData.get(SCALE);
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        builder.define(ABSORBING, false);
        builder.define(FLOATING, false);
        builder.define(SCALE, 1.0f);
        builder.define(DETONATING, false);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel world)) {
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
            this.setDeltaMovement(Vec3.ZERO);
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
            this.setDeltaMovement(Vec3.ZERO);
            spawnIdleParticles(world);
            if (--hoverTicks <= 0) {
                fizzle();
            }
            return;
        }

        // Straight, gravity-free flight.
        Vec3 velocity = this.getDeltaMovement();
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() != HitResult.Type.MISS) {
            this.onHit(hit);
            // Stop here if the collision removed us or settled a floating orb (don't advance into the block).
            if (this.isRemoved() || landed) {
                return;
            }
        }

        Vec3 next = this.position().add(velocity);
        this.setPos(next.x, next.y, next.z);
        traveled += velocity.length();

        world.sendParticles(ModParticles.VOID, getX(), getY(), getZ(), 3, 0.07, 0.07, 0.07, 0.01);

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
    protected void onHitBlock(BlockHitResult blockHitResult) {
        super.onHitBlock(blockHitResult);
        if (this.level().isClientSide()) {
            return;
        }
        if (floating) {
            // Settle just outside the block (and a touch higher) so the orb hovers above the surface
            // rather than phasing into it.
            Vec3 dir = this.getDeltaMovement().lengthSqr() > 1.0e-6 ? this.getDeltaMovement().normalize() : new Vec3(0, 0, 1);
            Vec3 p = blockHitResult.getLocation().subtract(dir.scale(0.7));
            this.setPos(p.x, p.y + 0.5, p.z);
            land();
        } else {
            explode(false);
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult entityHitResult) {
        super.onHitEntity(entityHitResult);
        if (this.level().isClientSide() || floating) {
            return; // floating orbs pass through entities until they settle
        }
        explode(false);
    }

    /** During flight a floating orb ignores entities (only blocks settle it); a normal orb hits all. */
    @Override
    protected boolean canHitEntity(Entity entity) {
        if (floating && !landed) {
            return false;
        }
        return super.canHitEntity(entity) && entity != this.getOwner();
    }

    /** Whether OTHER projectiles/attacks can strike this orb — only the live floating form is a target. */
    @Override
    public boolean isPickable() {
        return this.entityData.get(FLOATING) && !this.isRemoved() && explodeTimer < 0 && !detonating;
    }

    @Override
    public boolean isAttackable() {
        return this.entityData.get(FLOATING) && !detonating;
    }

    @Override
    public boolean hurtServer(net.minecraft.server.level.ServerLevel damageWorld, DamageSource source, float amount) {
        if (this.level().isClientSide() || !floating || this.isRemoved() || explodeTimer >= 0 || detonating) {
            return false;
        }
        Entity src = source.getDirectEntity();
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
        this.setDeltaMovement(Vec3.ZERO);
        if (this.level() instanceof ServerLevel world) {
            world.playSound(null, getX(), getY(), getZ(),
                    SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.PLAYERS, 0.7f, 0.7f);
        }
    }

    private void beginAbsorb(boolean superVersion) {
        // superVersion: both spells fully charged -> the full 5-phase cataclysm.
        // otherwise: the normal chaos blast (1.0.3-style explosion after the same collapse delay).
        explodeTimer = ABSORB_DELAY;
        landed = true;
        superNuke = superVersion;
        this.setDeltaMovement(Vec3.ZERO);
        this.entityData.set(ABSORBING, true);
        if (this.level() instanceof ServerLevel world) {
            world.playSound(null, getX(), getY(), getZ(),
                    SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.9f, 0.5f);
        }
    }

    // --- Phases 1 & 2: instability and energy compression --------------------

    private void tickChargeUp(ServerLevel world) {
        int elapsed = ABSORB_DELAY - explodeTimer;
        float progress = (float) elapsed / ABSORB_DELAY; // 0..1
        boolean compression = progress >= 0.5f;
        Vec3 c = this.position();

        // Shrinking sphere: gentle pulse during instability, then collapse during compression.
        float scale;
        if (!compression) {
            scale = 0.95f + 0.1f * (float) Math.sin(elapsed * 0.6);
        } else {
            float cp = (progress - 0.5f) / 0.5f;
            scale = 1.0f - 0.8f * cp;
        }
        this.entityData.set(SCALE, Math.max(0.15f, scale));

        // Orbiting particles spiralling inward; density and speed ramp up over time.
        int density = compression ? 7 : 3 + (int) (progress * 5);
        double baseR = compression ? (3.0 * (1.0f - (progress - 0.5f) / 0.5f) + 0.6) : 3.2;
        double spin = elapsed * (compression ? 0.9 : 0.5);
        double inward = compression ? 0.35 : 0.18;
        for (int i = 0; i < density; i++) {
            double a = spin + i * (Math.PI * 2.0 / density);
            double rr = baseR * (0.5 + world.getRandom().nextDouble() * 0.6);
            double px = c.x + Math.cos(a) * rr;
            double pz = c.z + Math.sin(a) * rr;
            double py = c.y + (world.getRandom().nextDouble() * 2 - 1) * rr * 0.5;
            world.sendParticles(ModParticles.VOID, px, py, pz, 0,
                    (c.x - px), (c.y - py), (c.z - pz), inward);
            if (i % 2 == 0) {
                world.sendParticles(ModParticles.LIGHT, px, py, pz, 0,
                        (c.x - px), (c.y - py), (c.z - pz), inward);
            }
        }

        // Distortion ring sweeping around the orb.
        if (elapsed % 4 == 0) {
            int pts = 24;
            double rr = baseR + 1.2;
            for (int i = 0; i < pts; i++) {
                double a = spin + i * (Math.PI * 2.0 / pts);
                world.sendParticles(ModParticles.VOID, c.x + Math.cos(a) * rr, c.y, c.z + Math.sin(a) * rr,
                        1, 0, 0, 0, 0.0);
            }
        }

        // Pulsing brightness at the core.
        if (elapsed % (compression ? 2 : 5) == 0) {
            world.sendParticles(ModParticles.RADIANCE, c.x, c.y, c.z, 2, 0.1, 0.1, 0.1, 0.0);
        }

        // Phase 2: a suction nobody walks out of, plus air dragged inward.
        if (compression) {
            double suctionR = 14.0;
            AABB box = new AABB(c.x - suctionR, c.y - suctionR, c.z - suctionR,
                    c.x + suctionR, c.y + suctionR, c.z + suctionR);
            for (LivingEntity e : world.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
                Vec3 to = c.subtract(e.position());
                double dist = to.length();
                if (dist < 0.6 || dist > suctionR) {
                    continue;
                }
                double strength = 0.3 + 0.55 * (1.0 - dist / suctionR);
                Vec3 pull = to.normalize().scale(strength);
                e.push(pull.x, pull.y * 0.4, pull.z);
                e.syncVelocity = true;
            }
            for (int i = 0; i < 8; i++) {
                double a = world.getRandom().nextDouble() * Math.PI * 2.0;
                double rr = 8.0 + world.getRandom().nextDouble() * 4.0;
                double px = c.x + Math.cos(a) * rr;
                double pz = c.z + Math.sin(a) * rr;
                double py = c.y + (world.getRandom().nextDouble() * 2 - 1) * 3.0;
                world.sendParticles(ModParticles.VOID, px, py, pz, 0,
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
                    SimpleParticleType p = (((int) (s * 2)) % 2 == 0) ? ModParticles.VOID : ModParticles.ARCANE_TRAIL;
                    double dirx;
                    double diry;
                    double dirz;
                    double sp;
                    if (compression) {
                        dirx = c.x - px; diry = c.y - py; dirz = c.z - pz; sp = 0.55; // snap inward
                    } else {
                        dirx = px - c.x; diry = py - c.y; dirz = pz - c.z; sp = 0.15; // reach outward
                    }
                    world.sendParticles(p, px, py, pz, 0, dirx, diry, dirz, sp);
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
            world.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS,
                    0.6f, 0.4f + progress * 1.4f);
        }
    }

    // --- Phase 3: detonation flash (and the blast itself) --------------------

    private void detonate(ServerLevel world) {
        Vec3 c = this.position();
        double scale = superNuke ? blastScale : 1.0;
        double radius = (superNuke ? SUPER_RADIUS : NUKE_RADIUS) * scale;
        float power = (float) ((superNuke ? SUPER_POWER : NUKE_POWER) * scale);
        double dmgScale = (superNuke ? SUPER_DAMAGE_SCALE : 1.0) * scale;
        this.detoRadius = radius;

        world.explode(this, c.x, c.y, c.z, power, false, Level.ExplosionInteraction.TNT);
        AreaEffectManager.add(new AreaEffect(world, c, radius, AreaEffect.Type.SLOW, 200, 1, 0.0f).setPoison(true));
        immobilize(world, c, radius);

        // Flat, blast-resistance-ignoring damage + outward knockback (the shockwave begins).
        AABB box = new AABB(c.x - radius, c.y - radius, c.z - radius, c.x + radius, c.y + radius, c.z + radius);
        for (LivingEntity living : world.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
            if (living instanceof ServerPlayer p && ManaPlayer.get(p).isImmune(world.getGameTime())) {
                continue;
            }
            double d = living.position().distanceTo(c);
            if (d > radius) {
                continue;
            }
            float dmg = (float) (40.0 * dmgScale * (1.0 - d / radius)) + 6.0f;
            living.hurt(world.damageSources().magic(), dmg);
            Vec3 kb = living.position().subtract(c);
            if (kb.lengthSqr() < 1.0e-4) {
                kb = new Vec3(0, 1, 0);
            }
            kb = kb.normalize().scale(1.6 * (1.0 - d / radius) + 0.4).add(0, 0.55, 0);
            living.push(kb.x, kb.y, kb.z);
            living.syncVelocity = true;
        }

        // Phase 3: the impact frame (B&W desaturation + white blowout) and the blinding flash.
        ModNetworking.sendImpact(world, c.x, c.y, c.z, 80.0, 3);
        spawnFlash(world, c);

        // Phase 1 of the camera shake: the initial impact (stronger the closer you are).
        ModNetworking.sendShakeFalloff(world, c.x, c.y, c.z, 70.0, 3.6f, 12);

        // Layered detonation audio: a sharp crack, then a deep bass impact.
        world.playSound(null, c.x, c.y, c.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 3.0f, 1.4f);
        world.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 4.0f, 0.5f);
        world.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 4.0f, 0.4f);

        detonating = true;
        detoAge = 0;
        this.entityData.set(DETONATING, true);
        this.entityData.set(ABSORBING, false);
        this.setDeltaMovement(Vec3.ZERO);
    }

    private void spawnFlash(ServerLevel world, Vec3 c) {
        world.sendParticles(ModParticles.RADIANCE, c.x, c.y + 1.0, c.z, superNuke ? 120 : 70, 0.6, 0.6, 0.6, 0.5);
        world.sendParticles(ModParticles.ARCANE_TRAIL, c.x, c.y + 1.0, c.z, superNuke ? 180 : 100, 1.0, 1.0, 1.0, 0.8);
        // Light rays bursting outward like an impact frame.
        int rays = superNuke ? 48 : 28;
        for (int i = 0; i < rays; i++) {
            double phi = Math.acos(1.0 - 2.0 * (i + 0.5) / rays);
            double theta = Math.PI * (1.0 + Math.sqrt(5.0)) * i;
            double sx = Math.sin(phi) * Math.cos(theta);
            double sy = Math.cos(phi);
            double sz = Math.sin(phi) * Math.sin(theta);
            world.sendParticles(ModParticles.LIGHT, c.x, c.y + 1.0, c.z, 0, sx, sy, sz, superNuke ? 1.6 : 1.2);
        }
    }

    // --- Phases 4 & 5: shockwave and aftermath -------------------------------

    private void tickDetonation(ServerLevel world) {
        detoAge++;
        Vec3 c = this.position();
        double radius = detoRadius;

        // Phase 3 tail: a couple more frames of blinding white.
        if (detoAge <= FLASH_TICKS) {
            world.sendParticles(ModParticles.RADIANCE, c.x, c.y + 1.0, c.z, 30, 0.7, 0.7, 0.7, 0.4);
            world.sendParticles(ModParticles.ARCANE_TRAIL, c.x, c.y + 1.0, c.z, 40, 0.9, 0.9, 0.9, 0.6);
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
                world.sendParticles(ModParticles.RADIANCE, c.x + sx * br, c.y + 1.0 + sy * br, c.z + sz * br, 1, 0, 0, 0, 0.0);
            }
        }

        // Phase 4: the shockwave front — fast expanding rings on three planes (outruns the smoke).
        if (detoAge <= SHOCK_TICKS) {
            double rr = radius * ((double) detoAge / SHOCK_TICKS);
            ringPlane(world, c, rr, 0, 1, 0, ModParticles.LIGHT);
            ringPlane(world, c, rr, 1, 0, 0, ModParticles.VOID);
            ringPlane(world, c, rr, 0.7, 0.7, 0, ModParticles.VOID);
            for (int i = 0; i < 12; i++) {
                double a = world.getRandom().nextDouble() * Math.PI * 2.0;
                world.sendParticles(ModParticles.DUST, c.x + Math.cos(a) * rr, c.y + 0.3, c.z + Math.sin(a) * rr,
                        0, Math.cos(a), 0.2, Math.sin(a), 0.4);
            }
        }

        // Camera shake phase 2: the secondary shockwave jolt as the front sweeps past.
        if (detoAge == 10) {
            ModNetworking.sendShakeFalloff(world, c.x, c.y, c.z, 80.0, 2.2f, 12);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 3.0f, 0.7f);
        }

        // Phase 5: lingering aftermath in distinct layers (smoke / sparks / dust / residual magic).
        if (detoAge % 3 == 0) {
            for (int i = 0; i < 7; i++) {
                double a = world.getRandom().nextDouble() * Math.PI * 2.0;
                double rr = world.getRandom().nextDouble() * radius * 0.55;
                world.sendParticles(ModParticles.SMOKE, c.x + Math.cos(a) * rr, c.y + 0.5 + world.getRandom().nextDouble() * 2.5,
                        c.z + Math.sin(a) * rr, 1, 0.2, 0.1, 0.2, 0.02);
            }
            for (int i = 0; i < 3; i++) {
                double a = world.getRandom().nextDouble() * Math.PI * 2.0;
                double rr = world.getRandom().nextDouble() * radius * 0.5;
                world.sendParticles(ModParticles.SPARK, c.x + Math.cos(a) * rr, c.y + 0.4 + world.getRandom().nextDouble() * 1.8,
                        c.z + Math.sin(a) * rr, 1, 0.05, 0.2, 0.05, 0.05);
            }
            for (int i = 0; i < 3; i++) {
                double a = world.getRandom().nextDouble() * Math.PI * 2.0;
                double rr = world.getRandom().nextDouble() * radius * 0.6;
                world.sendParticles(ModParticles.DUST, c.x + Math.cos(a) * rr, c.y + 0.2,
                        c.z + Math.sin(a) * rr, 1, 0.1, 0.0, 0.1, 0.02);
            }
            world.sendParticles(ModParticles.ARCANE, c.x, c.y + 0.6, c.z, 2, radius * 0.3, 0.4, radius * 0.3, 0.02);
        }

        // Residual energy scar (whole 10s): black-and-white motes slowly orbiting the crater.
        double scarR = 3.5 + 1.5 * Math.sin(detoAge * 0.05);
        double ang = detoAge * 0.2;
        for (int k = 0; k < 2; k++) {
            double a = ang + k * Math.PI;
            double bx = c.x + Math.cos(a) * scarR;
            double bz = c.z + Math.sin(a) * scarR;
            double by = c.y + 0.4 + 0.5 * Math.sin(detoAge * 0.1 + k);
            world.sendParticles(ModParticles.VOID, bx, by, bz, 0, (c.x - bx) * 0.02, 0.0, (c.z - bz) * 0.02, 0.3);
            world.sendParticles(ModParticles.ARCANE_TRAIL, c.x - Math.cos(a) * scarR, by, c.z - Math.sin(a) * scarR,
                    1, 0, 0, 0, 0.0);
        }

        // Lingering energy hum.
        if (detoAge % 24 == 0) {
            world.playSound(null, c.x, c.y, c.z, SoundEvents.CONDUIT_AMBIENT, SoundSource.PLAYERS, 0.5f, 0.5f);
        }

        if (detoAge >= DETO_LIFETIME) {
            this.discard();
        }
    }

    /** A ring of particles in the plane whose normal is (nx,ny,nz). */
    private void ringPlane(ServerLevel world, Vec3 c, double r, double nx, double ny, double nz, SimpleParticleType p) {
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
            world.sendParticles(p, px, py, pz, 1, 0, 0, 0, 0.0);
        }
    }

    private void explode(boolean active) {
        if (!(this.level() instanceof ServerLevel world)) {
            return;
        }
        Vec3 center = this.position();
        double radius = active ? ACTIVE_RADIUS : NORMAL_RADIUS;
        int duration = active ? ACTIVE_DURATION : NORMAL_DURATION;
        int amp = active ? 1 : 0;

        AreaEffectManager.add(new AreaEffect(world, center, radius, AreaEffect.Type.SLOW, duration, amp, 0.0f)
                .setPoison(active));
        immobilize(world, center, radius);

        world.sendParticles(ModParticles.VOID, center.x, center.y + 0.4, center.z,
                (int) (radius * 12), radius * 0.4, 0.5, radius * 0.4, 0.08);
        world.sendParticles(ModParticles.FROST, center.x, center.y + 0.4, center.z,
                (int) (radius * 6), radius * 0.4, 0.4, radius * 0.4, 0.05);
        world.playSound(null, center.x, center.y, center.z,
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 0.4f);
        this.discard();
    }

    /**
     * The normal chaos blast (1.0.3): an active-chant spear absorbed into the orb without both being
     * fully charged. A single mid-size Hollow Purple explosion with its lingering void field — no
     * 5-phase collapse, suction, impact frame, or aftermath.
     */
    private void normalChaosExplosion(ServerLevel world) {
        Vec3 c = this.position();

        world.explode(this, c.x, c.y, c.z, NUKE_POWER, false, Level.ExplosionInteraction.TNT);
        AreaEffectManager.add(new AreaEffect(world, c, NUKE_RADIUS, AreaEffect.Type.SLOW, 200, 1, 0.0f).setPoison(true));
        immobilize(world, c, NUKE_RADIUS);

        AABB box = new AABB(c.x - NUKE_RADIUS, c.y - NUKE_RADIUS, c.z - NUKE_RADIUS,
                c.x + NUKE_RADIUS, c.y + NUKE_RADIUS, c.z + NUKE_RADIUS);
        for (LivingEntity living : world.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
            if (living instanceof ServerPlayer p && ManaPlayer.get(p).isImmune(world.getGameTime())) {
                continue;
            }
            double d = living.position().distanceTo(c);
            if (d > NUKE_RADIUS) {
                continue;
            }
            float dmg = (float) (40.0 * (1.0 - d / NUKE_RADIUS)) + 6.0f;
            living.hurt(world.damageSources().magic(), dmg);
        }

        spawnNormalBlastParticles(world, c);
        world.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 4.0f, 0.6f);
        world.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 4.0f, 0.5f);
        this.discard();
    }

    /** Purple-and-white bloom for the normal chaos blast: bright core, void sphere, shock rings. */
    private void spawnNormalBlastParticles(ServerLevel world, Vec3 c) {
        world.sendParticles(ModParticles.RADIANCE, c.x, c.y + 1.0, c.z, 40, 0.6, 0.6, 0.6, 0.1);
        world.sendParticles(ModParticles.ARCANE_TRAIL, c.x, c.y + 1.0, c.z, 60, 1.0, 1.0, 1.0, 0.3);
        for (int i = 0; i < 260; i++) {
            double phi = Math.acos(1.0 - 2.0 * world.getRandom().nextDouble());
            double theta = world.getRandom().nextDouble() * Math.PI * 2.0;
            double rr = NUKE_RADIUS * Math.cbrt(world.getRandom().nextDouble());
            double sx = Math.sin(phi) * Math.cos(theta);
            double sy = Math.cos(phi);
            double sz = Math.sin(phi) * Math.sin(theta);
            world.sendParticles(ModParticles.VOID, c.x + sx * rr, c.y + 1.0 + sy * rr, c.z + sz * rr, 1, 0, 0, 0, 0.0);
        }
        for (double ring = 2.0; ring <= NUKE_RADIUS; ring += 2.0) {
            int pts = (int) (ring * 6);
            for (int i = 0; i < pts; i++) {
                double a = (Math.PI * 2.0 * i) / pts;
                double px = c.x + Math.cos(a) * ring;
                double pz = c.z + Math.sin(a) * ring;
                world.sendParticles(ModParticles.VOID, px, c.y + 0.3, pz, 1, 0, 0, 0, 0.0);
                if (((int) ring) % 4 == 0) {
                    world.sendParticles(ModParticles.LIGHT, px, c.y + 0.3, pz, 1, 0, 0, 0, 0.0);
                }
            }
        }
    }

    /** Oil-and-water swirl shown while the normal chaos blast collapses (1.0.3). */
    private void spawnAbsorbParticles(ServerLevel world) {
        world.sendParticles(ModParticles.VOID, getX(), getY(), getZ(), 4, 0.35, 0.35, 0.35, 0.02);
        world.sendParticles(ModParticles.ARCANE_TRAIL, getX(), getY(), getZ(), 3, 0.35, 0.35, 0.35, 0.02);
        world.sendParticles(ModParticles.LIGHT, getX(), getY(), getZ(), 2, 0.3, 0.3, 0.3, 0.01);
    }

    /** Roots every (non-immune) living entity in range for one second. */
    private void immobilize(ServerLevel world, Vec3 center, double radius) {
        AABB box = new AABB(center.x - radius, center.y - radius, center.z - radius,
                center.x + radius, center.y + radius, center.z + radius);
        for (LivingEntity living : world.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
            double dx = living.getX() - center.x;
            double dz = living.getZ() - center.z;
            if ((dx * dx + dz * dz) > radius * radius) {
                continue;
            }
            if (living instanceof ServerPlayer player && ManaPlayer.get(player).isImmune(world.getGameTime())) {
                continue;
            }
            living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, IMMOBILIZE_TICKS, IMMOBILIZE_AMP, false, true, true));
            living.setDeltaMovement(Vec3.ZERO);
            living.syncVelocity = true;
        }
    }

    private void fizzle() {
        if (this.level() instanceof ServerLevel world) {
            world.sendParticles(ModParticles.VOID, getX(), getY(), getZ(), 16, 0.3, 0.3, 0.3, 0.05);
            world.playSound(null, getX(), getY(), getZ(),
                    SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.PLAYERS, 0.4f, 1.4f);
        }
        this.discard();
    }

    private void spawnIdleParticles(ServerLevel world) {
        if (world.getGameTime() % 3 == 0) {
            world.sendParticles(ModParticles.VOID, getX(), getY(), getZ(), 2, 0.25, 0.25, 0.25, 0.01);
        }
    }

    public void addAdditionalSaveDataLegacy(CompoundTag nbt) {
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

    public void readAdditionalSaveDataLegacy(CompoundTag nbt) {
        floating = nbt.getBooleanOr("floating", false);
        fullyChargedOrb = nbt.getBooleanOr("fullyCharged", false);
        this.entityData.set(FLOATING, floating);
        landed = nbt.getBooleanOr("landed", false);
        superNuke = nbt.getBooleanOr("superNuke", false);
        if (nbt.contains("blastScale")) {
            blastScale = nbt.getDoubleOr("blastScale", 0d);
        }
        if (nbt.contains("detoRadius")) {
            detoRadius = nbt.getDoubleOr("detoRadius", 0d);
        }
        traveled = nbt.getDoubleOr("traveled", 0d);
        hoverTicks = nbt.contains("hover") ? nbt.getIntOr("hover", 0) : HOVER_LIFETIME;
        explodeTimer = nbt.contains("explodeTimer") ? nbt.getIntOr("explodeTimer", 0) : -1;
        detonating = nbt.getBooleanOr("detonating", false);
        detoAge = nbt.getIntOr("detoAge", 0);
        this.setNoGravity(true);
    }

    @Override protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput value) {  CompoundTag tag = new CompoundTag(); addAdditionalSaveDataLegacy(tag); com.example.circlemage.util.NbtBridge.write(value, tag); }

    @Override protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput value) {  readAdditionalSaveDataLegacy(com.example.circlemage.util.NbtBridge.read(value)); }
}
