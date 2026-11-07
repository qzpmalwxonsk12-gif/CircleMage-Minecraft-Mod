package com.example.circlemage.entity;

import com.example.circlemage.item.ModItems;
import com.example.circlemage.mana.ManaData;
import com.example.circlemage.particle.ModParticles;
import com.example.circlemage.spell.AreaEffect;
import com.example.circlemage.spell.AreaEffectManager;
import com.example.circlemage.spell.Spell;
import com.example.circlemage.world.WorldProgressState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.example.circlemage.mana.ManaPlayer;
import com.example.circlemage.network.ModNetworking;
import org.jetbrains.annotations.Nullable;

/**
 * The Wandering Mage — a rare roaming NPC that demonstrates the magic system and acts as an early
 * combat encounter. Passive until attacked, has infinite mana (but still obeys spell cooldowns), and
 * fights as a ranged caster using Magic Missile, Wind Wall, Burn, a once-per-life Thunder, and Dash.
 *
 * <p>Spawns at Circle 1, 2 or 3 (random). A Circle 3 mage has a 1-in-3 chance to become <b>The
 * Prodigy</b> — a single, world-unique elite whose defeat unlocks the future Master framework
 * ({@link WorldProgressState}). The combat brain lives in {@link WanderingMageCombatGoal}; this class
 * owns the stats, abilities, cooldowns and progression bookkeeping.</p>
 */
public class WanderingMageEntity extends PathfinderMob {

    private static final EntityDataAccessor<Integer> CIRCLE =
            SynchedEntityData.defineId(WanderingMageEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> PRODIGY =
            SynchedEntityData.defineId(WanderingMageEntity.class, EntityDataSerializers.BOOLEAN);
    /** Synced telegraph state: whether a Magic Missile is being cast, and the locked firing direction. */
    private static final EntityDataAccessor<Boolean> CASTING =
            SynchedEntityData.defineId(WanderingMageEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> AIM_X =
            SynchedEntityData.defineId(WanderingMageEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> AIM_Y =
            SynchedEntityData.defineId(WanderingMageEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> AIM_Z =
            SynchedEntityData.defineId(WanderingMageEntity.class, EntityDataSerializers.FLOAT);

    /** Magic Missile cast-commit: stand still and telegraph for 0.25s before firing. */
    private static final int MISSILE_CAST_TICKS = 5;
    /** Max horizontal (yaw-only) deviation in degrees applied to an inaccurate shot. Never vertical. */
    private static final double MISSILE_INACCURACY_DEG = 5.0;
    /** Fraction of Magic Missiles fired perfectly accurate; the rest get a horizontal deviation. */
    private static final double MISSILE_ACCURATE_CHANCE = 0.5;
    /** Each consecutive Thunder hit on this mage does 20% less than the previous one. */
    private static final double THUNDER_FALLOFF = 0.8;

    /** Realm damage multiplier reduced globally to 1.2x (matches {@link ManaData#REALM_DAMAGE_PER_BUFF}). */
    private static final double REALM_DAMAGE_PER_BUFF = ManaData.REALM_DAMAGE_PER_BUFF;

    /** A pool of flavourful given names shown above each mage's head. */
    private static final String[] NAMES = {
            "Aldric", "Bryn", "Caelum", "Dorian", "Elowen", "Fenwick", "Gideon", "Halric", "Isolde",
            "Joran", "Kael", "Lyra", "Mordecai", "Nyx", "Orin", "Perrin", "Quill", "Rowan", "Sael",
            "Thessaly", "Ulric", "Vesper", "Wren", "Xanthe", "Yorick", "Zephyra"
    };

    // Per-ability cooldown end ticks (world time). Infinite mana, so only cooldowns gate casting.
    private long missileCooldownEnd = 0;
    private long windCooldownEnd = 0;
    private long burnCooldownEnd = 0;
    private long dashCooldownEnd = 0;
    /** Thunder is a one-time emergency below 50% HP. */
    private boolean usedThunder = false;

    /** Magic Missile cast-commit countdown (>0 while committing; the locked direction is below). */
    private int missileCastTicks = -1;
    private double lockedDirX, lockedDirY, lockedDirZ;
    /** How many Thunder hits this mage has taken in a row (reset by any non-fire, non-lightning hit). */
    private int thunderHitStreak = 0;

    /** Transient: last measured distance to the target, used to detect rush/retreat (combat goal). */
    public double prevTargetDist = -1.0;
    /** Transient: alternates strafe direction over time. */
    public int strafeSign = 1;

    /** Below this health fraction a non-Prodigy mage yields, flees and teleports away. */
    private static final float RETREAT_HEALTH_FRACTION = 0.25f;
    /** Transient: yielding/fleeing state and the world-tick the escape teleport fires. */
    private boolean retreating = false;
    private long dispelCooldownEnd = 0;
    private long escapeTick = 0;
    /** Transient: players this mage has already greeted (per life). */
    private final java.util.Set<java.util.UUID> greetedPlayers = new java.util.HashSet<>();

    /** Tower guardian mode (v2.3.0): post to defend, and the radius that counts as trespassing. */
    private boolean guarding = false;
    private double guardX, guardY, guardZ;
    private double guardRadius = 12.0;
    /** Transient: world-tick until which lore chatter is on cooldown. */
    private long loreCooldownEnd = 0;

    /** Friendly small talk for a first meeting. */
    private static final String[] GREETINGS = {
            "Ah, a fellow traveler. The mana runs thick around here, doesn't it?",
            "Well met. Few walk these lands with open eyes.",
            "Greetings. I'm charting the ley-currents — they've been restless lately.",
            "Oh! You startled me. I was listening to the mana on the wind.",
    };

    /** Hints offered on a chat (right-click), keyed to how far along the player is. */
    private static final String[] LORE_EARLY = {
            "Be still, open yourself, and channel — the world gives mana to the patient.",
            "Every mage begins at the first circle. Channel until the world answers.",
    };
    private static final String[] LORE_MID = {
            "From the third circle onward, keep casting — arrays and the anti-magic reveal themselves to the persistent.",
            "Carve your arrays before a hard fight. The ground remembers what the hand inscribes.",
    };
    private static final String[] LORE_LATE = {
            "Hold a spell in your grip before releasing it. Held power can be woven with a second magic.",
            "They say the ninth circle touches the Laws themselves. I never dared climb that high.",
    };

    public WanderingMageEntity(EntityType<? extends WanderingMageEntity> type, Level world) {
        super(type, world);
    }

    /**
     * Natural-spawn predicate (paired with {@code SpawnRestriction.Location.ON_GROUND}). Mages roam
     * lit, exposed surfaces so the player meets them while exploring in daylight rather than in caves.
     */
    public static boolean canSpawn(EntityType<WanderingMageEntity> type, ServerLevelAccessor world,
                                   EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        if (reason == EntitySpawnReason.SPAWNER || reason == EntitySpawnReason.SPAWN_ITEM_USE || reason == EntitySpawnReason.COMMAND) {
            return true;
        }
        return world.getRawBrightness(pos, 0) > 7
                && world.getBlockState(pos.below()).isValidSpawn(world, pos.below(), type);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25) // a walking pace — mages never sprint
                .add(Attributes.FOLLOW_RANGE, 80.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.2);
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(CIRCLE, 1);
        builder.define(PRODIGY, false);
        builder.define(CASTING, false);
        builder.define(AIM_X, 0.0f);
        builder.define(AIM_Y, 0.0f);
        builder.define(AIM_Z, 1.0f);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new WanderingMageCombatGoal(this));
        this.goalSelector.addGoal(6, new MageIdleChannelGoal(this));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8) {
            @Override public boolean canUse() { return !WanderingMageEntity.this.isGuarding() && super.canUse(); }
        });
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 10.0f));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        // No target-selector goals: the mage is passive and only acquires a target when attacked
        // (see damage()) — by a player or by a hostile mob it must defend against.
    }

    // --- Stats / identity -----------------------------------------------------

    public int getCircle() {
        return this.entityData.get(CIRCLE);
    }

    public boolean isProdigy() {
        return this.entityData.get(PRODIGY);
    }

    /** Magic damage per hit: base 2 hearts scaled by the mage's realm buffs (one per circle) and Prodigy bonus. */
    public float magicDamage() {
        double mult = Math.pow(REALM_DAMAGE_PER_BUFF, getCircle());
        if (isProdigy()) {
            mult *= 1.2; // Prodigy: +20% damage
        }
        return (float) (ManaData.BASE_DAMAGE * mult);
    }

    /**
     * Ability cooldown. Normal mages run at 125% of the base spell cooldown; the Prodigy runs at the
     * base 100% (its old 20% reduction was removed in 1.0.8.1). Dash has its own multiplier (see
     * {@link #DASH_COOLDOWN_MULT}).
     */
    private long cooldown(int baseTicks) {
        return Math.round(baseTicks * (isProdigy() ? 1.0 : 1.25));
    }

    /** Dash cooldown multiplier — 125% for every mage, Prodigy included. */
    private static final double DASH_COOLDOWN_MULT = 1.25;

    // --- Spawn-time setup -----------------------------------------------------

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor world, DifficultyInstance difficulty, EntitySpawnReason spawnReason,
                                 @Nullable SpawnGroupData entityData) {
        SpawnGroupData data = super.finalizeSpawn(world, difficulty, spawnReason, entityData);

        RandomSource random = this.getRandom();
        int circle = 1 + random.nextInt(3); // Circle 1..3, uniformly
        boolean prodigy = false;

        // Only Circle 3 mages are eligible, with a 1-in-3 chance, and only when the world allows a new
        // Prodigy (none currently alive, none ever defeated).
        if (circle == 3 && world instanceof ServerLevelAccessor && !this.level().isClientSide()) {
            WorldProgressState progress = WorldProgressState.get((ServerLevel) this.level());
            if (progress.canProdigySpawn() && random.nextInt(3) == 0) {
                prodigy = true;
                progress.setProdigyAlive(true);
            }
        }

        applyVariant(circle, prodigy, random);
        return data;
    }

    /**
     * Forces a specific variant on an already-created (e.g. command-spawned) mage, bypassing the
     * random roll in {@link #finalizeSpawn}. For a Prodigy this also claims the world's "one Prodigy"
     * slot so the natural-spawn rules stay consistent.
     */
    public void configureForSpawn(int circle, boolean prodigy) {
        int clamped = Math.max(1, Math.min(3, circle));
        applyVariant(clamped, prodigy, this.getRandom());
        if (prodigy && !this.level().isClientSide() && this.level() instanceof ServerLevel world) {
            WorldProgressState.get(world).setProdigyAlive(true);
        }
    }

    /**
     * Turns this mage into a tower guardian (v2.3.0): it holds its post, never despawns, and turns
     * hostile the moment a player crosses into the tower's grounds.
     */
    public void configureAsGuard(Vec3 post, double radius) {
        this.guarding = true;
        this.guardX = post.x;
        this.guardY = post.y;
        this.guardZ = post.z;
        this.guardRadius = radius;
        this.setPersistenceRequired();
        String name = plainName();
        this.setCustomName(Component.literal(name + " — Tower Guardian").withStyle(ChatFormatting.RED));
    }

    public boolean isGuarding() {
        return guarding;
    }

    /** Assigns circle/prodigy, scales health, and sets the floating name. */
    private void applyVariant(int circle, boolean prodigy, RandomSource random) {
        this.entityData.set(CIRCLE, circle);
        this.entityData.set(PRODIGY, prodigy);

        double maxHealth = prodigy ? 60.0 : 20.0 + 4.0 * circle; // base 20 + 2 hearts/circle; Prodigy 30 hearts
        AttributeInstance healthAttr = this.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr != null) {
            healthAttr.setBaseValue(maxHealth);
        }
        this.setHealth((float) maxHealth);

        String name = NAMES[random.nextInt(NAMES.length)];
        if (prodigy) {
            this.setCustomName(Component.literal(name + " the Prodigy").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            this.setPersistenceRequired(); // a world-unique elite must never despawn
        } else {
            this.setCustomName(Component.literal(name + " — Circle " + circle + " Mage").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        this.setCustomNameVisible(true);
    }

    // --- Targeting ------------------------------------------------------------

    @Override
    public boolean hurtServer(net.minecraft.server.level.ServerLevel damageWorld, DamageSource source, float amount) {
        // Diminishing Thunder: each consecutive lightning hit deals 20% less than the previous one.
        // The streak resets when the mage is struck by any other (non-fire) attack — fire is ignored
        // since it's usually the lightning's own ignition side-effect.
        boolean lightning = source.is(DamageTypes.LIGHTNING_BOLT);
        float dealt = lightning ? (float) (amount * Math.pow(THUNDER_FALLOFF, thunderHitStreak)) : amount;
        // Innate magic resistance: 5% per circle against fire / raw magic (lightning keeps its own falloff).
        if (!lightning && com.example.circlemage.spell.MagicResistance.isResistible(source)) {
            dealt *= (1.0f - com.example.circlemage.spell.MagicResistance.forCircle(getCircle()));
        }

        boolean result = super.hurtServer(damageWorld, source, dealt);
        if (result && !this.level().isClientSide()) {
            if (lightning) {
                thunderHitStreak++;
            } else if (!source.is(DamageTypeTags.IS_FIRE)) {
                thunderHitStreak = 0;
            }
            // Self-defence: whoever attacked (player or hostile mob) becomes the target — unless
            // the mage is already trying to escape.
            if (!retreating && source.getEntity() instanceof LivingEntity attacker
                    && attacker.isAlive() && attacker != this) {
                this.setTarget(attacker);
            }
            // Badly hurt non-Prodigies yield rather than fight to the death.
            if (!isProdigy() && !guarding && this.isAlive() && !retreating
                    && this.getHealth() <= this.getMaxHealth() * RETREAT_HEALTH_FRACTION) {
                beginRetreat();
            }
        }
        return result;
    }

    // --- Retreat / surrender ---------------------------------------------------

    /** True while the mage has yielded and is fleeing toward its escape teleport. */
    public boolean isRetreating() {
        return retreating;
    }

    /** Yield: break off combat, toss a tribute of Mana Dust, flee, and teleport away shortly after. */
    private void beginRetreat() {
        retreating = true;
        this.setTarget(null);
        this.prevTargetDist = -1.0;
        this.escapeTick = this.level().getGameTime() + 70; // ~3.5s of fleeing before the escape
        this.spawnAtLocation((ServerLevel)level(), new net.minecraft.world.item.ItemStack(ModItems.MANA_DUST));
        sayNearby("Enough — I yield! Take the dust and let me be!");
        this.playSound(SoundEvents.VILLAGER_HURT, 1.0f, 1.2f);
    }

    /** Speaks a line into chat for every player close enough to be part of the scene. */
    private void sayNearby(String line) {
        if (!(this.level() instanceof ServerLevel world)) {
            return;
        }
        Component text = Component.literal("<" + plainName() + "> " + line).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
        for (ServerPlayer player : world.getPlayers(p -> p.distanceToSqr(this) <= 24.0 * 24.0)) {
            com.example.circlemage.util.Messages.send(player, text, false);
        }
    }

    /** The mage's given name without the title suffix (for chat lines). */
    private String plainName() {
        String full = this.getName().getString();
        int cut = full.indexOf(" —");
        if (cut < 0) {
            cut = full.indexOf(" the ");
        }
        return cut > 0 ? full.substring(0, cut) : full;
    }

    @Override
    public void tick() {
        super.tick();
        // Record a Mage Journal "encounter" for any player who comes near (server-side, throttled).
        if (!this.level().isClientSide() && this.tickCount % 40 == 0 && this.level() instanceof ServerLevel world) {
            for (ServerPlayer player : world.getPlayers(p -> p.distanceToSqr(this) <= 24.0 * 24.0)) {
                if (ManaPlayer.get(player).setEncounteredWanderingMage()) {
                    com.example.circlemage.util.Messages.send(player, Component.literal("Your journal records a new encounter: Wandering Mage.")
                            .withStyle(ChatFormatting.LIGHT_PURPLE), false);
                    ModNetworking.sendJournalSync(player);
                }
            }
            tickGreetings(world);
        }
    }

    /** A peaceful mage greets each player once when they first come close. (Guards don't chat.) */
    private void tickGreetings(ServerLevel world) {
        if (this.getTarget() != null || retreating || guarding) {
            return;
        }
        for (ServerPlayer player : world.getPlayers(p -> p.distanceToSqr(this) <= 6.0 * 6.0)) {
            if (greetedPlayers.add(player.getUUID())) {
                this.getLookControl().setLookAt(player, 30.0f, 30.0f);
                com.example.circlemage.util.Messages.send(player, Component.literal("<" + plainName() + "> "
                                + GREETINGS[this.getRandom().nextInt(GREETINGS.length)])
                        .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), false);
            }
        }
    }

    /**
     * Right-click conversation. An emerald buys a Mana Vial; empty-handed (or anything else) earns
     * a bit of guidance matched to the player's own progress. Hostile or fleeing mages don't chat.
     */
    @Override
    protected net.minecraft.world.InteractionResult mobInteract(Player player, net.minecraft.world.InteractionHand hand) {
        if (guarding) {
            if (!this.level().isClientSide() && this.level().getGameTime() >= loreCooldownEnd) {
                loreCooldownEnd = this.level().getGameTime() + 60;
                sayNearby("This tower is not yours. Begone.");
            }
            return net.minecraft.world.InteractionResult.PASS;
        }
        if (this.getTarget() != null || retreating || !this.isAlive()) {
            return net.minecraft.world.InteractionResult.PASS;
        }
        if (this.level().isClientSide()) {
            return net.minecraft.world.InteractionResult.SUCCESS;
        }
        if (hand != net.minecraft.world.InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer serverPlayer)) {
            return net.minecraft.world.InteractionResult.CONSUME;
        }
        this.getLookControl().setLookAt(player, 30.0f, 30.0f);

        net.minecraft.world.item.ItemStack held = player.getItemInHand(hand);
        if (held.is(net.minecraft.world.item.Items.EMERALD)) {
            if (!player.getAbilities().instabuild) {
                held.shrink(1);
            }
            net.minecraft.world.item.ItemStack vial = new net.minecraft.world.item.ItemStack(ModItems.MANA_VIAL);
            if (!player.getInventory().add(vial)) {
                player.drop(vial, false, net.minecraft.util.Prediction.SERVER_ONLY);
            }
            sayNearby("A fair trade, traveler. Drink it slowly — bottled mana bites.");
            this.playSound(SoundEvents.VILLAGER_TRADE, 1.0f, 1.1f);
            if (this.level() instanceof ServerLevel world) {
                world.sendParticles(com.example.circlemage.particle.ModParticles.ARCANE,
                        this.getX(), this.getY(0.7), this.getZ(), 10, 0.3, 0.4, 0.3, 0.03);
            }
            return net.minecraft.world.InteractionResult.CONSUME;
        }

        long now = this.level().getGameTime();
        if (now >= loreCooldownEnd) {
            loreCooldownEnd = now + 60; // don't monologue when click-spammed
            int circle = ManaPlayer.get(serverPlayer).getCircle();
            String[] pool = circle <= 1 ? LORE_EARLY : (circle <= 4 ? LORE_MID : LORE_LATE);
            com.example.circlemage.util.Messages.send(serverPlayer, Component.literal("<" + plainName() + "> "
                            + pool[this.getRandom().nextInt(pool.length)])
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), false);
            this.playSound(SoundEvents.VILLAGER_AMBIENT, 1.0f, 1.1f);
        }
        return net.minecraft.world.InteractionResult.CONSUME;
    }

    @Override
    protected void customServerAiStep(ServerLevel aiWorld) {
        super.customServerAiStep(aiWorld);
        if(guarding && isOnFire() && level() instanceof ServerLevel w && w.getGameTime()>=dispelCooldownEnd) {
            dispelCooldownEnd=w.getGameTime()+160;
            com.example.circlemage.spell.RevampMagic.guardianEpiphany(this,w);
        }
        if(guarding && getTarget()!=null && getTarget().distanceToSqr(guardX,guardY,guardZ)>(guardRadius+12)*(guardRadius+12))setTarget(null);

        // Yielded: keep fleeing the nearest player, then vanish in a flash of light.
        if (retreating && this.level() instanceof ServerLevel world) {
            this.setTarget(null);
            long now = world.getGameTime();
            if (now >= escapeTick) {
                world.sendParticles(ModParticles.ARCANE, this.getX(), this.getY(0.5), this.getZ(),
                        40, 0.4, 0.9, 0.4, 0.08);
                world.sendParticles(ModParticles.RADIANCE, this.getX(), this.getY(0.5), this.getZ(),
                        20, 0.3, 0.8, 0.3, 0.05);
                world.playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 1.0f, 1.4f);
                sayNearby("Until we meet again...");
                this.discard();
                return;
            }
            if (this.tickCount % 10 == 0) {
                Player nearest = world.getNearestPlayer(this, 24.0);
                if (nearest != null) {
                    Vec3 away = this.position().subtract(nearest.position());
                    away = new Vec3(away.x, 0.0, away.z);
                    if (away.lengthSqr() < 1.0e-4) {
                        away = new Vec3(1, 0, 0);
                    }
                    Vec3 spot = this.position().add(away.normalize().scale(10.0));
                    this.getNavigation().moveTo(spot.x, spot.y, spot.z, 1.15);
                }
            }
            return;
        }

        // Drop the target only on the documented conditions: player dead, beyond 75 blocks, or gone.
        LivingEntity target = this.getTarget();
        if (target != null && (!target.isAlive() || this.distanceToSqr(target) > 75.0 * 75.0)) {
            this.setTarget(null);
            this.prevTargetDist = -1.0;
        }

        // Tower guardian: anyone stepping onto the tower grounds is an intruder; otherwise stay
        // near the post (guards never wander off with the idle goals).
        if (guarding && !retreating && this.getTarget() == null && this.tickCount % 10 == 0
                && this.level() instanceof ServerLevel guardWorld) {
            Player intruder = guardWorld.getNearestPlayer(guardX, guardY, guardZ, guardRadius, true);
            if (intruder != null && intruder.isAlive()) {
                this.setTarget(intruder);
                sayNearby("You trespass on a mage's tower. Leave — or be removed!");
                this.playSound(SoundEvents.EVOKER_PREPARE_ATTACK, 1.0f, 1.2f);
            } else if (this.distanceToSqr(guardX, guardY, guardZ) > 20.0 * 20.0) {
                this.getNavigation().moveTo(guardX, guardY, guardZ, 1.0);
            }
        }

        // Magic Missile cast-commit: hold perfectly still, keep aim on the target, then fire the
        // locked shot when the 0.25s window elapses. The mage cannot move/dash/cast while committing.
        if (missileCastTicks > 0) {
            LivingEntity castTarget = this.getTarget();
            if (castTarget == null || !castTarget.isAlive()) {
                cancelMissileCast();
            } else {
                this.getNavigation().stop();
                this.setDeltaMovement(0.0, this.getDeltaMovement().y, 0.0); // no drift while committing
                this.getLookControl().setLookAt(castTarget, 30.0f, 30.0f);
                missileCastTicks--;
                if (missileCastTicks == 0) {
                    fireLockedMissile();
                    cancelMissileCast();
                }
            }
        }
    }

    /** True while the mage is mid Magic-Missile commit (frozen, telegraphing, cannot act otherwise). */
    public boolean isBusyCasting() {
        return missileCastTicks > 0;
    }

    // --- Abilities (called by the combat goal) --------------------------------

    /**
     * Long/mid range: begin a Magic Missile cast. The mage briefly commits — it locks the firing
     * direction now (prediction + horizontal inaccuracy), telegraphs it, holds still for 0.25s, and
     * then fires the locked shot in {@link #fireLockedMissile} (see {@link #customServerAiStep}).
     */
    public void tryStartMagicMissile(LivingEntity target) {
        long now = this.level().getGameTime();
        if (now < missileCooldownEnd || isBusyCasting() || !(this.level() instanceof ServerLevel)) {
            return;
        }
        missileCooldownEnd = now + cooldown(Spell.MAGIC_MISSILE.cooldownTicks);
        beginMissileCast(target);
    }

    /** Locks the final firing direction (horizontal lead + horizontal-only inaccuracy) and telegraphs it. */
    private void beginMissileCast(LivingEntity target) {
        Vec3 from = this.getEyePosition();
        Vec3 targetCenter = target.getBoundingBox().getCenter();
        // Prediction leads the target's LATERAL (x/z) movement only. The aim height is the target's
        // current centre — never lead vertical velocity, so a jumping/falling player can't drag the
        // shot into the sky or the ground.
        double leadTicks = from.distanceTo(targetCenter) / MagicMissileEntity.SPEED;
        Vec3 vel = target.getDeltaMovement();
        Vec3 predicted = new Vec3(
                targetCenter.x + vel.x * leadTicks,
                targetCenter.y,
                targetCenter.z + vel.z * leadTicks);
        Vec3 dir = predicted.subtract(from);
        if (dir.lengthSqr() < 1.0e-4) {
            dir = this.getViewVector(1.0f);
        }
        dir = dir.normalize();

        // Inaccuracy: half of all shots are perfectly accurate; the rest get a purely horizontal
        // (yaw) deviation in [-5, +5] degrees — a misjudged lateral lead, never a vertical miss.
        if (this.getRandom().nextDouble() >= MISSILE_ACCURATE_CHANCE) {
            double spread = Math.toRadians((this.getRandom().nextDouble() * 2.0 - 1.0) * MISSILE_INACCURACY_DEG);
            double cos = Math.cos(spread);
            double sin = Math.sin(spread);
            // Rotate only the horizontal (x/z) components about Y; y is untouched, so length stays 1.
            dir = new Vec3(dir.x * cos - dir.z * sin, dir.y, dir.x * sin + dir.z * cos);
        }

        lockedDirX = dir.x;
        lockedDirY = dir.y;
        lockedDirZ = dir.z;
        missileCastTicks = MISSILE_CAST_TICKS;
        this.entityData.set(CASTING, true);
        this.entityData.set(AIM_X, (float) dir.x);
        this.entityData.set(AIM_Y, (float) dir.y);
        this.entityData.set(AIM_Z, (float) dir.z);
        this.getNavigation().stop();
        this.playSound(SoundEvents.EVOKER_CAST_SPELL, 0.8f, 1.5f);
    }

    /** Fires the Magic Missile straight along the locked direction (no homing — it follows the telegraph). */
    private void fireLockedMissile() {
        if (!(this.level() instanceof ServerLevel)) {
            return;
        }
        Vec3 dir = new Vec3(lockedDirX, lockedDirY, lockedDirZ);
        if (dir.lengthSqr() < 1.0e-4) {
            dir = this.getViewVector(1.0f);
        }
        MagicMissileEntity missile = new MagicMissileEntity(this.level(), this, magicDamage());
        missile.launch(dir.normalize());
        this.level().addFreshEntity(missile);
        this.playSound(SoundEvents.ILLUSIONER_CAST_SPELL, 0.8f, 1.3f);
    }

    private void cancelMissileCast() {
        missileCastTicks = -1;
        this.entityData.set(CASTING, false);
    }

    // --- Telegraph state (read by the client renderer) ------------------------

    public boolean isCasting() {
        return this.entityData.get(CASTING);
    }

    public Vec3 getAimDirection() {
        return new Vec3(this.entityData.get(AIM_X), this.entityData.get(AIM_Y), this.entityData.get(AIM_Z));
    }

    /** Close range: a Wind Wall that knocks the target away to re-open distance. */
    public void tryWindWall(LivingEntity target) {
        long now = this.level().getGameTime();
        if (now < windCooldownEnd) {
            return;
        }
        windCooldownEnd = now + cooldown(Spell.WALL_OF_WIND.cooldownTicks);

        Vec3 push = target.position().subtract(this.position());
        push = new Vec3(push.x, 0.0, push.z);
        if (push.lengthSqr() < 1.0e-4) {
            push = new Vec3(this.getRandom().nextDouble() - 0.5, 0, this.getRandom().nextDouble() - 0.5);
        }
        push = push.normalize().scale(2.0).add(0, 0.45, 0);
        target.push(push.x, push.y, push.z);
        target.syncVelocity = true;

        if (this.level() instanceof ServerLevel world) {
            Vec3 eye = this.getEyePosition();
            Vec3 horiz = push.multiply(1, 0, 1).normalize();
            for (double f = 1.0; f <= 5.0; f += 1.0) {
                world.sendParticles(ModParticles.GALE, eye.x + horiz.x * f, eye.y, eye.z + horiz.z * f,
                        2, 0.3, 0.3, 0.3, 0.4);
            }
        }
        this.playSound(SoundEvents.PHANTOM_FLAP, 1.0f, 0.8f);
    }

    /** Mid range: lay a lingering fire field beneath the target. */
    public void tryBurn(LivingEntity target) {
        long now = this.level().getGameTime();
        if (now < burnCooldownEnd || !(this.level() instanceof ServerLevel world)) {
            return;
        }
        burnCooldownEnd = now + cooldown(Spell.FIREBALL.cooldownTicks);
        float burnBonus = magicDamage() * 0.25f;
        AreaEffectManager.add(new AreaEffect(world, target.position(), 3.0, AreaEffect.Type.FIRE, 100, 0, burnBonus));
        world.sendParticles(ModParticles.EMBER, target.getX(), target.getY() + 0.2, target.getZ(),
                40, 1.2, 0.3, 1.2, 0.06);
        world.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 1.0f, 1.0f);
    }

    /** Emergency (once per life, below 50% HP): call lightning down on the target. */
    public void tryThunder(LivingEntity target) {
        if (usedThunder || !(this.level() instanceof ServerLevel world)) {
            return;
        }
        usedThunder = true;
        LightningBolt bolt = net.minecraft.world.entity.EntityTypes.LIGHTNING_BOLT.create(world, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        if (bolt != null) {
            bolt.snapTo(target.getX(), target.getY(), target.getZ());
            world.addFreshEntity(bolt);
        }
        world.sendParticles(ModParticles.SPARK, this.getX(), this.getY() + 1.0, this.getZ(), 30, 0.4, 0.6, 0.4, 0.3);
    }

    public boolean isDashReady() {
        return this.level().getGameTime() >= dashCooldownEnd;
    }

    /** Dash: a quick burst in {@code direction} (used for evasion and repositioning). */
    public boolean tryDash(Vec3 direction) {
        if (!isDashReady()) {
            return false;
        }
        Vec3 horiz = new Vec3(direction.x, 0.0, direction.z);
        if (horiz.lengthSqr() < 1.0e-4) {
            return false;
        }
        dashCooldownEnd = this.level().getGameTime() + Math.round(Spell.DASH.cooldownTicks * DASH_COOLDOWN_MULT);
        horiz = horiz.normalize().scale(0.75); // dash strength reduced 50% (was 1.5; 1.0.8.1)
        this.setDeltaMovement(horiz.x, 0.3, horiz.z);
        this.syncVelocity = true;
        this.fallDistance = 0.0f;
        if (this.level() instanceof ServerLevel world) {
            world.sendParticles(ModParticles.GALE, this.getX(), this.getY() + 0.9, this.getZ(), 12, 0.3, 0.3, 0.3, 0.3);
        }
        this.playSound(SoundEvents.PHANTOM_FLAP, 0.7f, 1.5f);
        return true;
    }

    /**
     * Projectile evasion. If an incoming projectile is heading at the mage, dash perpendicular to it
     * (priority 1) or, if dash is on cooldown, hop sideways via normal movement (priority 2).
     *
     * @return true if an evasive action was taken this tick.
     */
    public boolean handleProjectileEvasion() {
        if (!(this.level() instanceof ServerLevel world)) {
            return false;
        }
        AABB box = this.getBoundingBox().inflate(10.0);
        Vec3 myCenter = this.getBoundingBox().getCenter();
        for (Projectile proj : world.getEntitiesOfClass(Projectile.class, box,
                p -> p.isAlive() && p.getOwner() != this)) {
            Vec3 toMe = myCenter.subtract(proj.position());
            double dist = toMe.length();
            if (dist < 0.2 || dist > 10.0) {
                continue;
            }
            Vec3 vel = proj.getDeltaMovement();
            if (vel.lengthSqr() < 1.0e-4) {
                continue;
            }
            if (vel.normalize().dot(toMe.normalize()) > 0.85) {
                // Heading roughly at us — evade perpendicular to the projectile's flight.
                Vec3 perp = new Vec3(-vel.z, 0.0, vel.x).normalize().scale(this.getRandom().nextBoolean() ? 1 : -1);
                if (tryDash(perp)) {
                    return true;
                }
                this.push(perp.x * 0.6, 0.1, perp.z * 0.6);
                this.syncVelocity = true;
                return true;
            }
        }
        return false;
    }

    // --- Death / removal ------------------------------------------------------

    @Override
    public void die(DamageSource damageSource) {
        // Every mage sheds Mana Dust; the Prodigy sheds a generous pile (plus its crystal, below).
        if (!this.level().isClientSide()) {
            int dust = 1 + this.getRandom().nextInt(2) + (isProdigy() ? 2 : 0);
            this.spawnAtLocation((ServerLevel)level(), new net.minecraft.world.item.ItemStack(ModItems.MANA_DUST, dust));
        }
        if (!this.level().isClientSide() && isProdigy() && this.level() instanceof ServerLevel world) {
            // Prodigy death event: lock out future Prodigies and unlock the Master framework.
            WorldProgressState.get(world).markProdigyDefeated();
            // Reward: drop a Mana Crystal.
            this.spawnAtLocation((ServerLevel)level(), new net.minecraft.world.item.ItemStack(ModItems.MANA_CRYSTAL));
            Component line = Component.literal("My master will avenge me...").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC);
            if (damageSource.getEntity() instanceof Player killer) {
                com.example.circlemage.util.Messages.send(killer, line, false);
            }
            for (Player nearby : world.getPlayers(p -> p.distanceToSqr(this) <= 64.0 * 64.0)) {
                if (damageSource.getEntity() != nearby) {
                    com.example.circlemage.util.Messages.send(nearby, line, false);
                }
            }
        }
        super.die(damageSource);
    }

    @Override
    public void remove(RemovalReason reason) {
        // If a Prodigy is truly gone (killed or discarded — not merely a chunk unload), free the
        // "one Prodigy at a time" slot so a future one may eventually spawn (unless it was defeated,
        // which onDeath already handled by also setting the permanent flag).
        if (!this.level().isClientSide() && isProdigy() && reason.shouldDestroy()
                && this.level() instanceof ServerLevel world) {
            WorldProgressState.get(world).setProdigyAlive(false);
        }
        super.remove(reason);
    }

    @Override
    public boolean removeWhenFarAway(double distanceSquared) {
        return !isProdigy() && super.removeWhenFarAway(distanceSquared);
    }

    // --- NBT ------------------------------------------------------------------

    public void addAdditionalSaveDataLegacy(CompoundTag nbt) {
        
        nbt.putInt("mageCircle", getCircle());
        nbt.putBoolean("prodigy", isProdigy());
        nbt.putBoolean("usedThunder", usedThunder);
        nbt.putBoolean("guarding", guarding);
        nbt.putLong("dispelCooldownEnd",dispelCooldownEnd);
        if (guarding) {
            nbt.putDouble("guardX", guardX);
            nbt.putDouble("guardY", guardY);
            nbt.putDouble("guardZ", guardZ);
            nbt.putDouble("guardRadius", guardRadius);
        }
    }

    public void readAdditionalSaveDataLegacy(CompoundTag nbt) {
        
        this.entityData.set(CIRCLE, Math.max(1, Math.min(3, nbt.getIntOr("mageCircle", 0))));
        this.entityData.set(PRODIGY, nbt.getBooleanOr("prodigy", false));
        usedThunder = nbt.getBooleanOr("usedThunder", false);
        guarding = nbt.getBooleanOr("guarding", false);
        dispelCooldownEnd=nbt.getLongOr("dispelCooldownEnd", 0L);
        if (guarding) {
            guardX = nbt.getDoubleOr("guardX", 0d);
            guardY = nbt.getDoubleOr("guardY", 0d);
            guardZ = nbt.getDoubleOr("guardZ", 0d);
            guardRadius = Math.max(4.0, nbt.getDoubleOr("guardRadius", 0d));
        }
    }

    @Override protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput value) { super.addAdditionalSaveData(value); CompoundTag tag = new CompoundTag(); addAdditionalSaveDataLegacy(tag); com.example.circlemage.util.NbtBridge.write(value, tag); }

    @Override protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput value) { super.readAdditionalSaveData(value); readAdditionalSaveDataLegacy(com.example.circlemage.util.NbtBridge.read(value)); }
}
