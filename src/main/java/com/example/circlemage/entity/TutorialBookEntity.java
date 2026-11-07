package com.example.circlemage.entity;

import com.example.circlemage.CircleMageMod;
import com.example.circlemage.particle.ModParticles;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * The intro tutorial book (v2.2.1): a glowing tome that rests on the ground where a fresh player
 * "wakes up". Right-clicking it plays a short cinematic — the book floats up, then shoots into the
 * reader and vanishes — after which {@link CircleMageMod#completeBookIntro} advances the intro and
 * prompts them to press J. It despawns on its own if left untouched.
 */
public class TutorialBookEntity extends Entity {

    /** 0 = resting/idle, 1 = playing the absorb animation. */
    private static final TrackedData<Integer> STATE =
            DataTracker.registerData(TutorialBookEntity.class, TrackedDataHandlerRegistry.INTEGER);
    /** Ticks elapsed since activation (drives the client animation). */
    private static final TrackedData<Integer> ANIM =
            DataTracker.registerData(TutorialBookEntity.class, TrackedDataHandlerRegistry.INTEGER);

    public static final int RISE_TICKS = 14;   // float upward
    public static final int SHOOT_TICKS = 12;  // fly into the reader
    private static final int IDLE_DESPAWN = 2400; // 2 minutes untouched

    private UUID readerUuid;
    private Vec3d restPos = Vec3d.ZERO;

    public TutorialBookEntity(EntityType<? extends TutorialBookEntity> type, World world) {
        super(type, world);
        this.noClip = true;
        this.setNoGravity(true);
    }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(STATE, 0);
        this.dataTracker.startTracking(ANIM, 0);
    }

    @Override
    public void onStartedTrackingBy(ServerPlayerEntity player) {
        super.onStartedTrackingBy(player);
        if (restPos.equals(Vec3d.ZERO)) {
            restPos = this.getPos();
        }
    }

    public int getState() {
        return this.dataTracker.get(STATE);
    }

    public int getAnim() {
        return this.dataTracker.get(ANIM);
    }

    @Override
    public ActionResult interact(PlayerEntity player, Hand hand) {
        if (this.getWorld().isClient) {
            return ActionResult.SUCCESS;
        }
        if (getState() == 0) {
            this.restPos = this.getPos();
            this.readerUuid = player.getUuid();
            this.dataTracker.set(STATE, 1);
            this.dataTracker.set(ANIM, 0);
            this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 1.0f, 1.2f);
            return ActionResult.CONSUME;
        }
        return ActionResult.PASS;
    }

    @Override
    public void tick() {
        super.tick();

        if (getState() == 0) {
            // Idle: bob is purely visual (client). Server just times out and sprinkles motes.
            if (!this.getWorld().isClient) {
                if (this.age > IDLE_DESPAWN) {
                    this.discard();
                    return;
                }
                if (this.getWorld() instanceof ServerWorld world && this.age % 6 == 0) {
                    world.spawnParticles(ModParticles.ARCANE, this.getX(), this.getY() + 0.35, this.getZ(),
                            1, 0.12, 0.08, 0.12, 0.01);
                }
            }
            return;
        }

        // Absorbing.
        int anim = getAnim();
        if (!this.getWorld().isClient) {
            ServerPlayerEntity reader = readerUuid == null ? null
                    : (ServerPlayerEntity) this.getWorld().getPlayerByUuid(readerUuid);

            if (anim < RISE_TICKS) {
                // Float straight up from the rest position.
                double t = anim / (double) RISE_TICKS;
                this.setPosition(restPos.x, restPos.y + 1.1 * ease(t), restPos.z);
            } else if (reader != null) {
                // Shoot toward the reader's chest, closing the gap each tick.
                Vec3d target = reader.getPos().add(0, reader.getStandingEyeHeight() * 0.6, 0);
                Vec3d cur = this.getPos();
                double f = 0.45;
                this.setPosition(cur.x + (target.x - cur.x) * f, cur.y + (target.y - cur.y) * f,
                        cur.z + (target.z - cur.z) * f);
            }

            if (this.getWorld() instanceof ServerWorld world) {
                world.spawnParticles(ModParticles.ARCANE, this.getX(), this.getY() + 0.2, this.getZ(),
                        2, 0.1, 0.1, 0.1, 0.02);
            }

            if (anim >= RISE_TICKS + SHOOT_TICKS) {
                if (reader != null && this.getWorld() instanceof ServerWorld world) {
                    world.spawnParticles(ModParticles.RADIANCE, reader.getX(), reader.getBodyY(0.6), reader.getZ(),
                            40, 0.4, 0.7, 0.4, 0.08);
                    world.spawnParticles(ModParticles.ARCANE, reader.getX(), reader.getBodyY(0.6), reader.getZ(),
                            30, 0.4, 0.6, 0.4, 0.06);
                    world.playSound(null, reader.getX(), reader.getY(), reader.getZ(),
                            SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.PLAYERS, 1.0f, 0.9f);
                    CircleMageMod.completeBookIntro(reader);
                }
                this.discard();
                return;
            }
            this.dataTracker.set(ANIM, anim + 1);
        }
    }

    private static double ease(double t) {
        return t * t * (3.0 - 2.0 * t); // smoothstep
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.containsUuid("reader")) {
            this.readerUuid = nbt.getUuid("reader");
        }
        this.restPos = this.getPos();
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        if (readerUuid != null) {
            nbt.putUuid("reader", readerUuid);
        }
    }

    @Override
    public boolean isCollidable() {
        return false;
    }

    @Override
    public boolean canHit() {
        return getState() == 0; // only interactable while resting
    }

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket() {
        return new EntitySpawnS2CPacket(this);
    }
}
