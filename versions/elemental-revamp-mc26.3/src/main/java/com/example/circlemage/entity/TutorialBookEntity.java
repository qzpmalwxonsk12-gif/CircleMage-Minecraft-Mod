package com.example.circlemage.entity;

import com.example.circlemage.CircleMageMod;
import com.example.circlemage.particle.ModParticles;
import java.util.UUID;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The intro tutorial book (v2.2.1): a glowing tome that rests on the ground where a fresh player
 * "wakes up". Right-clicking it plays a short cinematic — the book floats up, then shoots into the
 * reader and vanishes — after which {@link CircleMageMod#completeBookIntro} advances the intro and
 * prompts them to press J. It despawns on its own if left untouched.
 */
public class TutorialBookEntity extends Entity {

    /** 0 = resting/idle, 1 = playing the absorb animation. */
    private static final EntityDataAccessor<Integer> STATE =
            SynchedEntityData.defineId(TutorialBookEntity.class, EntityDataSerializers.INT);
    /** Ticks elapsed since activation (drives the client animation). */
    private static final EntityDataAccessor<Integer> ANIM =
            SynchedEntityData.defineId(TutorialBookEntity.class, EntityDataSerializers.INT);

    public static final int RISE_TICKS = 14;   // float upward
    public static final int SHOOT_TICKS = 12;  // fly into the reader
    private static final int IDLE_DESPAWN = 2400; // 2 minutes untouched

    private UUID readerUuid;
    private Vec3 restPos = Vec3.ZERO;

    public TutorialBookEntity(EntityType<? extends TutorialBookEntity> type, Level world) {
        super(type, world);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        builder.define(STATE, 0);
        builder.define(ANIM, 0);
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        if (restPos.equals(Vec3.ZERO)) {
            restPos = this.position();
        }
    }

    public int getState() {
        return this.entityData.get(STATE);
    }

    public int getAnim() {
        return this.entityData.get(ANIM);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 hit) {
        if (this.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (getState() == 0) {
            this.restPos = this.position();
            this.readerUuid = player.getUUID();
            this.entityData.set(STATE, 1);
            this.entityData.set(ANIM, 0);
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0f, 1.2f);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    public void tick() {
        super.tick();

        if (getState() == 0) {
            // Idle: bob is purely visual (client). Server just times out and sprinkles motes.
            if (!this.level().isClientSide()) {
                if (this.tickCount > IDLE_DESPAWN) {
                    this.discard();
                    return;
                }
                if (this.level() instanceof ServerLevel world && this.tickCount % 6 == 0) {
                    world.sendParticles(ModParticles.ARCANE, this.getX(), this.getY() + 0.35, this.getZ(),
                            1, 0.12, 0.08, 0.12, 0.01);
                }
            }
            return;
        }

        // Absorbing.
        int anim = getAnim();
        if (!this.level().isClientSide()) {
            ServerPlayer reader = readerUuid == null ? null
                    : (ServerPlayer) this.level().getPlayerByUUID(readerUuid);

            if (anim < RISE_TICKS) {
                // Float straight up from the rest position.
                double t = anim / (double) RISE_TICKS;
                this.setPos(restPos.x, restPos.y + 1.1 * ease(t), restPos.z);
            } else if (reader != null) {
                // Shoot toward the reader's chest, closing the gap each tick.
                Vec3 target = reader.position().add(0, reader.getEyeHeight() * 0.6, 0);
                Vec3 cur = this.position();
                double f = 0.45;
                this.setPos(cur.x + (target.x - cur.x) * f, cur.y + (target.y - cur.y) * f,
                        cur.z + (target.z - cur.z) * f);
            }

            if (this.level() instanceof ServerLevel world) {
                world.sendParticles(ModParticles.ARCANE, this.getX(), this.getY() + 0.2, this.getZ(),
                        2, 0.1, 0.1, 0.1, 0.02);
            }

            if (anim >= RISE_TICKS + SHOOT_TICKS) {
                if (reader != null && this.level() instanceof ServerLevel world) {
                    world.sendParticles(ModParticles.RADIANCE, reader.getX(), reader.getY(0.6), reader.getZ(),
                            40, 0.4, 0.7, 0.4, 0.08);
                    world.sendParticles(ModParticles.ARCANE, reader.getX(), reader.getY(0.6), reader.getZ(),
                            30, 0.4, 0.6, 0.4, 0.06);
                    world.playSound(null, reader.getX(), reader.getY(), reader.getZ(),
                            SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0f, 0.9f);
                    CircleMageMod.completeBookIntro(reader);
                }
                this.discard();
                return;
            }
            this.entityData.set(ANIM, anim + 1);
        }
    }

    private static double ease(double t) {
        return t * t * (3.0 - 2.0 * t); // smoothstep
    }

    protected void readAdditionalSaveDataLegacy(CompoundTag nbt) {
        if (nbt.read("reader", net.minecraft.core.UUIDUtil.CODEC).isPresent()) {
            this.readerUuid = nbt.read("reader", net.minecraft.core.UUIDUtil.CODEC).orElse(null);
        }
        this.restPos = this.position();
    }

    protected void addAdditionalSaveDataLegacy(CompoundTag nbt) {
        if (readerUuid != null) {
            nbt.store("reader", net.minecraft.core.UUIDUtil.CODEC, readerUuid);
        }
    }

    @Override
    public boolean canBeCollidedWith(Entity other) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return getState() == 0; // only interactable while resting
    }

    @Override protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput value) {  CompoundTag tag = new CompoundTag(); addAdditionalSaveDataLegacy(tag); com.example.circlemage.util.NbtBridge.write(value, tag); }

    @Override protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput value) {  readAdditionalSaveDataLegacy(com.example.circlemage.util.NbtBridge.read(value)); }

    @Override public boolean hurtServer(net.minecraft.server.level.ServerLevel world, net.minecraft.world.damagesource.DamageSource source, float amount) { return false; }
}
