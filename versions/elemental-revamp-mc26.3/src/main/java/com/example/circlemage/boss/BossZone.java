package com.example.circlemage.boss;

import com.example.circlemage.particle.ModParticles;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A reusable spherical Boss Zone — the core infrastructure for boss encounters. A zone owns:
 * <ul>
 *     <li>a <b>containment barrier</b> (the Domain Barrier): entities classified as "inside" cannot
 *     leave and "outside" entities cannot enter — this catches walking, knockback, projectiles,
 *     teleports and dashes (after-the-fact pushback), since membership is tracked by UUID;</li>
 *     <li>an optional <b>Meteor Rain</b> mechanic;</li>
 *     <li>a configurable <b>magic restriction</b> set (per-spell suppression).</li>
 * </ul>
 *
 * Zones are persisted (saved via {@link BossZoneState}, so they survive world reloads) and ticked by
 * {@link BossZoneManager}. New boss mechanics should be added as fields/methods here so future
 * encounters reuse the same framework.
 */
public class BossZone {

    private final ServerLevel world;
    private final Vec3 center;
    private final double radius;
    private final UUID owner;

    /** Entities classified as "inside" the barrier (membership, so teleports/dashes get pushed back). */
    private final Set<UUID> insiders = new HashSet<>();

    /** Magic restriction: which Spell ids are suppressed, and whether the restriction is live. */
    private final Set<Integer> restrictedSpells = new HashSet<>();
    private boolean restrictionsActive = false;

    /** Optional Meteor Rain mechanic (null until enabled). */
    private MeteorRain meteorRain = null;
    /** Meteor Rain frequency multipliers — separate per type (1.0.10.2 base rates: 50x / 30x). */
    private double meteorRateMissile = 50.0;
    private double meteorRateFireball = 30.0;

    private int age = 0;

    /** Command-created zone: classify everything currently inside as insiders right away. */
    public BossZone(ServerLevel world, Vec3 center, double radius, UUID owner) {
        this(world, center, radius, owner, true);
    }

    public BossZone(ServerLevel world, Vec3 center, double radius, UUID owner, boolean scanExisting) {
        this.world = world;
        this.center = center;
        this.radius = radius;
        this.owner = owner;
        if (scanExisting) {
            // Everything currently within the sphere starts as an insider.
            for (Entity e : world.getEntities((Entity)null, boxOf(radius), e -> contains(e.position()))) {
                insiders.add(e.getUUID());
            }
        }
        // (Loaded zones leave insiders empty; per-tick enforcement reclassifies entities as they move.)
    }

    public ServerLevel getWorld() {
        return world;
    }

    public Vec3 getCenter() {
        return center;
    }

    public double getRadius() {
        return radius;
    }

    public UUID getOwner() {
        return owner;
    }

    /** True if the point lies within the zone's sphere. */
    public boolean contains(Vec3 pos) {
        return pos.distanceToSqr(center) <= radius * radius;
    }

    private AABB boxOf(double r) {
        return new AABB(center.x - r, center.y - r, center.z - r, center.x + r, center.y + r, center.z + r);
    }

    // --- Magic restriction ----------------------------------------------------

    public void setRestrictionsActive(boolean active) {
        this.restrictionsActive = active;
    }

    public boolean isRestrictionsActive() {
        return restrictionsActive;
    }

    /** Configurable per-spell suppression (future bosses pick their own set). */
    public void setRestricted(int spellId, boolean restricted) {
        if (restricted) {
            restrictedSpells.add(spellId);
        } else {
            restrictedSpells.remove(spellId);
        }
    }

    public boolean isSpellRestricted(int spellId) {
        return restrictionsActive && restrictedSpells.contains(spellId);
    }

    /** Clears the suppressed-spell set (used when switching to a single restriction). */
    public void clearRestrictions() {
        restrictedSpells.clear();
    }

    // --- Meteor Rain ----------------------------------------------------------

    /** Toggles Meteor Rain; returns the new active state. */
    public boolean toggleMeteorRain() {
        if (meteorRain == null) {
            meteorRain = new MeteorRain(this);
            return true;
        }
        meteorRain = null;
        return false;
    }

    public boolean isMeteorRainActive() {
        return meteorRain != null;
    }

    public double getMeteorRateMissile() {
        return meteorRateMissile;
    }

    public double getMeteorRateFireball() {
        return meteorRateFireball;
    }

    /** Sets the Magic-Missile Meteor frequency multiplier (higher = more frequent). */
    public void setMeteorRateMissile(double rate) {
        this.meteorRateMissile = Math.max(0.1, rate);
    }

    /** Sets the Fireball Meteor frequency multiplier (higher = more frequent). */
    public void setMeteorRateFireball(double rate) {
        this.meteorRateFireball = Math.max(0.1, rate);
    }

    // --- Persistence ----------------------------------------------------------

    public CompoundTag writeNbt() {
        CompoundTag nbt = new CompoundTag();
        nbt.putString("dim", world.dimension().identifier().toString());
        nbt.putDouble("cx", center.x);
        nbt.putDouble("cy", center.y);
        nbt.putDouble("cz", center.z);
        nbt.putDouble("radius", radius);
        nbt.store("owner", net.minecraft.core.UUIDUtil.CODEC, owner);
        nbt.putBoolean("restrictActive", restrictionsActive);
        nbt.putIntArray("restricted", restrictedSpells.stream().mapToInt(Integer::intValue).toArray());
        nbt.putBoolean("meteor", meteorRain != null);
        nbt.putDouble("rateMissile", meteorRateMissile);
        nbt.putDouble("rateFireball", meteorRateFireball);
        return nbt;
    }

    /** Reconstructs a saved zone (insiders are re-derived at runtime). Returns null if its dimension is gone. */
    public static BossZone fromNbt(MinecraftServer server, CompoundTag nbt) {
        ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, Identifier.parse(nbt.getStringOr("dim", "")));
        ServerLevel world = server.getLevel(dimKey);
        if (world == null) {
            return null;
        }
        Vec3 center = new Vec3(nbt.getDoubleOr("cx", 0d), nbt.getDoubleOr("cy", 0d), nbt.getDoubleOr("cz", 0d));
        BossZone zone = new BossZone(world, center, nbt.getDoubleOr("radius", 0d), nbt.read("owner", net.minecraft.core.UUIDUtil.CODEC).orElse(null), false);
        zone.restrictionsActive = nbt.getBooleanOr("restrictActive", false);
        for (int id : nbt.getIntArray("restricted").orElseGet(() -> new int[0])) {
            zone.restrictedSpells.add(id);
        }
        if (nbt.contains("rateMissile")) {
            zone.meteorRateMissile = nbt.getDoubleOr("rateMissile", 0d);
        }
        if (nbt.contains("rateFireball")) {
            zone.meteorRateFireball = nbt.getDoubleOr("rateFireball", 0d);
        }
        if (nbt.getBooleanOr("meteor", false)) {
            zone.meteorRain = new MeteorRain(zone);
        }
        return zone;
    }

    // --- Tick -----------------------------------------------------------------

    public void tick() {
        age++;
        enforceContainment();
        if (age % 4 == 0) {
            renderShell();
        }
        if (meteorRain != null) {
            meteorRain.tick();
        }
    }

    /** Keeps insiders in and outsiders out, pushing back anything that crosses the shell. */
    private void enforceContainment() {
        double buffer = 2.0;
        List<Entity> nearby = world.getEntities((Entity)null, boxOf(radius + buffer + 4.0),
                e -> !e.isSpectator() && !(e instanceof net.minecraft.world.entity.item.ItemEntity));
        for (Entity e : nearby) {
            Vec3 pos = e.position();
            double d = Math.sqrt(pos.distanceToSqr(center));
            boolean insider = insiders.contains(e.getUUID());

            // Classify never-seen entities only when clearly inside; otherwise treat as outsiders.
            if (!insider && d < radius - buffer) {
                insiders.add(e.getUUID());
                insider = true;
            }

            Vec3 radial = pos.subtract(center);
            if (radial.lengthSqr() < 1.0e-6) {
                continue; // dead centre — nothing to push
            }
            radial = radial.normalize();
            Vec3 velocity = e.getDeltaMovement();
            double outwardVel = velocity.dot(radial);

            if (insider) {
                if (d > radius) {
                    // Crossed out — push back hard (or kill a stray projectile).
                    if (e instanceof Projectile) {
                        e.discard();
                        continue;
                    }
                    pushTo(e, radial, radius - 0.6, radial, true);
                } else if (d > radius - 1.0 && outwardVel > 0) {
                    cancelRadial(e, velocity, radial, outwardVel);
                }
            } else {
                if (d < radius) {
                    if (e instanceof Projectile) {
                        e.discard();
                        continue;
                    }
                    pushTo(e, radial, radius + 0.6, radial, false);
                } else if (d < radius + 1.0 && outwardVel < 0) {
                    cancelRadial(e, velocity, radial, outwardVel);
                }
            }
        }
    }

    private void cancelRadial(Entity e, Vec3 velocity, Vec3 radial, double outwardVel) {
        Vec3 corrected = velocity.subtract(radial.scale(outwardVel));
        e.setDeltaMovement(corrected);
        e.syncVelocity = true;
    }

    private void pushTo(Entity e, Vec3 radial, double targetDist, Vec3 radialNorm, boolean insider) {
        Vec3 clamped = center.add(radial.scale(targetDist));
        Vec3 velocity = e.getDeltaMovement();
        double outwardVel = velocity.dot(radialNorm);
        // Cancel the velocity component that points across the shell.
        if ((insider && outwardVel > 0) || (!insider && outwardVel < 0)) {
            velocity = velocity.subtract(radialNorm.scale(outwardVel));
        }
        e.setDeltaMovement(velocity);
        e.syncVelocity = true;
        e.fallDistance = 0.0f;
        if (e instanceof ServerPlayer player) {
            player.connection.teleport(clamped.x, clamped.y, clamped.z, player.getYRot(), player.getXRot());
        } else {
            e.setPos(clamped.x, clamped.y, clamped.z);
        }
    }

    /** Draws a patch of the barrier shell around any player standing near the wall (cheap + obvious). */
    private void renderShell() {
        for (ServerPlayer player : world.players()) {
            Vec3 toPlayer = player.position().subtract(center);
            double d = toPlayer.length();
            if (Math.abs(d - radius) > 30.0 || d < 1.0e-4) {
                continue; // only show the wall when a player is near it
            }
            Vec3 radial = toPlayer.normalize();
            Vec3 up = Math.abs(radial.y) < 0.99 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
            Vec3 t1 = radial.cross(up).normalize();
            Vec3 t2 = radial.cross(t1).normalize();
            for (double u = -10; u <= 10; u += 2.0) {
                for (double v = -10; v <= 10; v += 2.0) {
                    Vec3 offset = radial.scale(radius).add(t1.scale(u)).add(t2.scale(v));
                    Vec3 p = center.add(offset.normalize().scale(radius)); // re-project onto the sphere
                    world.sendParticles(ModParticles.BARRIER_SHARD, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
                }
            }
        }
    }
}
