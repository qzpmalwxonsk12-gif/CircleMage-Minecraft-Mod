package com.example.circlemage.boss;

import com.example.circlemage.particle.ModParticles;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

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

    private final ServerWorld world;
    private final Vec3d center;
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
    public BossZone(ServerWorld world, Vec3d center, double radius, UUID owner) {
        this(world, center, radius, owner, true);
    }

    public BossZone(ServerWorld world, Vec3d center, double radius, UUID owner, boolean scanExisting) {
        this.world = world;
        this.center = center;
        this.radius = radius;
        this.owner = owner;
        if (scanExisting) {
            // Everything currently within the sphere starts as an insider.
            for (Entity e : world.getOtherEntities(null, boxOf(radius), e -> contains(e.getPos()))) {
                insiders.add(e.getUuid());
            }
        }
        // (Loaded zones leave insiders empty; per-tick enforcement reclassifies entities as they move.)
    }

    public ServerWorld getWorld() {
        return world;
    }

    public Vec3d getCenter() {
        return center;
    }

    public double getRadius() {
        return radius;
    }

    public UUID getOwner() {
        return owner;
    }

    /** True if the point lies within the zone's sphere. */
    public boolean contains(Vec3d pos) {
        return pos.squaredDistanceTo(center) <= radius * radius;
    }

    private Box boxOf(double r) {
        return new Box(center.x - r, center.y - r, center.z - r, center.x + r, center.y + r, center.z + r);
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

    public NbtCompound writeNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("dim", world.getRegistryKey().getValue().toString());
        nbt.putDouble("cx", center.x);
        nbt.putDouble("cy", center.y);
        nbt.putDouble("cz", center.z);
        nbt.putDouble("radius", radius);
        nbt.putUuid("owner", owner);
        nbt.putBoolean("restrictActive", restrictionsActive);
        nbt.putIntArray("restricted", restrictedSpells.stream().mapToInt(Integer::intValue).toArray());
        nbt.putBoolean("meteor", meteorRain != null);
        nbt.putDouble("rateMissile", meteorRateMissile);
        nbt.putDouble("rateFireball", meteorRateFireball);
        return nbt;
    }

    /** Reconstructs a saved zone (insiders are re-derived at runtime). Returns null if its dimension is gone. */
    public static BossZone fromNbt(MinecraftServer server, NbtCompound nbt) {
        RegistryKey<World> dimKey = RegistryKey.of(RegistryKeys.WORLD, new Identifier(nbt.getString("dim")));
        ServerWorld world = server.getWorld(dimKey);
        if (world == null) {
            return null;
        }
        Vec3d center = new Vec3d(nbt.getDouble("cx"), nbt.getDouble("cy"), nbt.getDouble("cz"));
        BossZone zone = new BossZone(world, center, nbt.getDouble("radius"), nbt.getUuid("owner"), false);
        zone.restrictionsActive = nbt.getBoolean("restrictActive");
        for (int id : nbt.getIntArray("restricted")) {
            zone.restrictedSpells.add(id);
        }
        if (nbt.contains("rateMissile")) {
            zone.meteorRateMissile = nbt.getDouble("rateMissile");
        }
        if (nbt.contains("rateFireball")) {
            zone.meteorRateFireball = nbt.getDouble("rateFireball");
        }
        if (nbt.getBoolean("meteor")) {
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
        List<Entity> nearby = world.getOtherEntities(null, boxOf(radius + buffer + 4.0),
                e -> !e.isSpectator() && !(e instanceof net.minecraft.entity.ItemEntity));
        for (Entity e : nearby) {
            Vec3d pos = e.getPos();
            double d = Math.sqrt(pos.squaredDistanceTo(center));
            boolean insider = insiders.contains(e.getUuid());

            // Classify never-seen entities only when clearly inside; otherwise treat as outsiders.
            if (!insider && d < radius - buffer) {
                insiders.add(e.getUuid());
                insider = true;
            }

            Vec3d radial = pos.subtract(center);
            if (radial.lengthSquared() < 1.0e-6) {
                continue; // dead centre — nothing to push
            }
            radial = radial.normalize();
            Vec3d velocity = e.getVelocity();
            double outwardVel = velocity.dotProduct(radial);

            if (insider) {
                if (d > radius) {
                    // Crossed out — push back hard (or kill a stray projectile).
                    if (e instanceof ProjectileEntity) {
                        e.discard();
                        continue;
                    }
                    pushTo(e, radial, radius - 0.6, radial, true);
                } else if (d > radius - 1.0 && outwardVel > 0) {
                    cancelRadial(e, velocity, radial, outwardVel);
                }
            } else {
                if (d < radius) {
                    if (e instanceof ProjectileEntity) {
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

    private void cancelRadial(Entity e, Vec3d velocity, Vec3d radial, double outwardVel) {
        Vec3d corrected = velocity.subtract(radial.multiply(outwardVel));
        e.setVelocity(corrected);
        e.velocityModified = true;
    }

    private void pushTo(Entity e, Vec3d radial, double targetDist, Vec3d radialNorm, boolean insider) {
        Vec3d clamped = center.add(radial.multiply(targetDist));
        Vec3d velocity = e.getVelocity();
        double outwardVel = velocity.dotProduct(radialNorm);
        // Cancel the velocity component that points across the shell.
        if ((insider && outwardVel > 0) || (!insider && outwardVel < 0)) {
            velocity = velocity.subtract(radialNorm.multiply(outwardVel));
        }
        e.setVelocity(velocity);
        e.velocityModified = true;
        e.fallDistance = 0.0f;
        if (e instanceof ServerPlayerEntity player) {
            player.networkHandler.requestTeleport(clamped.x, clamped.y, clamped.z, player.getYaw(), player.getPitch());
        } else {
            e.setPosition(clamped.x, clamped.y, clamped.z);
        }
    }

    /** Draws a patch of the barrier shell around any player standing near the wall (cheap + obvious). */
    private void renderShell() {
        for (ServerPlayerEntity player : world.getPlayers()) {
            Vec3d toPlayer = player.getPos().subtract(center);
            double d = toPlayer.length();
            if (Math.abs(d - radius) > 30.0 || d < 1.0e-4) {
                continue; // only show the wall when a player is near it
            }
            Vec3d radial = toPlayer.normalize();
            Vec3d up = Math.abs(radial.y) < 0.99 ? new Vec3d(0, 1, 0) : new Vec3d(1, 0, 0);
            Vec3d t1 = radial.crossProduct(up).normalize();
            Vec3d t2 = radial.crossProduct(t1).normalize();
            for (double u = -10; u <= 10; u += 2.0) {
                for (double v = -10; v <= 10; v += 2.0) {
                    Vec3d offset = radial.multiply(radius).add(t1.multiply(u)).add(t2.multiply(v));
                    Vec3d p = center.add(offset.normalize().multiply(radius)); // re-project onto the sphere
                    world.spawnParticles(ModParticles.BARRIER_SHARD, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
                }
            }
        }
    }
}
