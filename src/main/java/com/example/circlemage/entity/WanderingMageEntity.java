package com.example.circlemage.entity;

import com.example.circlemage.item.ModItems;
import com.example.circlemage.mana.ManaData;
import com.example.circlemage.particle.ModParticles;
import com.example.circlemage.spell.AreaEffect;
import com.example.circlemage.spell.AreaEffectManager;
import com.example.circlemage.spell.Spell;
import com.example.circlemage.world.WorldProgressState;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import com.example.circlemage.mana.ManaPlayer;
import com.example.circlemage.network.ModNetworking;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
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
public class WanderingMageEntity extends PathAwareEntity {

    private static final TrackedData<Integer> CIRCLE =
            DataTracker.registerData(WanderingMageEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> PRODIGY =
            DataTracker.registerData(WanderingMageEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    /** Synced telegraph state: whether a Magic Missile is being cast, and the locked firing direction. */
    private static final TrackedData<Boolean> CASTING =
            DataTracker.registerData(WanderingMageEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Float> AIM_X =
            DataTracker.registerData(WanderingMageEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> AIM_Y =
            DataTracker.registerData(WanderingMageEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> AIM_Z =
            DataTracker.registerData(WanderingMageEntity.class, TrackedDataHandlerRegistry.FLOAT);

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

    public WanderingMageEntity(EntityType<? extends WanderingMageEntity> type, World world) {
        super(type, world);
    }

    /**
     * Natural-spawn predicate (paired with {@code SpawnRestriction.Location.ON_GROUND}). Mages roam
     * lit, exposed surfaces so the player meets them while exploring in daylight rather than in caves.
     */
    public static boolean canSpawn(EntityType<WanderingMageEntity> type, ServerWorldAccess world,
                                   SpawnReason reason, BlockPos pos, Random random) {
        if (reason == SpawnReason.SPAWNER || reason == SpawnReason.SPAWN_EGG || reason == SpawnReason.COMMAND) {
            return true;
        }
        return world.getBaseLightLevel(pos, 0) > 7
                && world.getBlockState(pos.down()).allowsSpawning(world, pos.down(), type);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return PathAwareEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25) // a walking pace — mages never sprint
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 80.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.2);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(CIRCLE, 1);
        this.dataTracker.startTracking(PRODIGY, false);
        this.dataTracker.startTracking(CASTING, false);
        this.dataTracker.startTracking(AIM_X, 0.0f);
        this.dataTracker.startTracking(AIM_Y, 0.0f);
        this.dataTracker.startTracking(AIM_Z, 1.0f);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(1, new WanderingMageCombatGoal(this));
        this.goalSelector.add(6, new MageIdleChannelGoal(this));
        this.goalSelector.add(7, new WanderAroundFarGoal(this, 0.8));
        this.goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 10.0f));
        this.goalSelector.add(9, new LookAroundGoal(this));
        // No target-selector goals: the mage is passive and only acquires a target when attacked
        // (see damage()) — by a player or by a hostile mob it must defend against.
    }

    // --- Stats / identity -----------------------------------------------------

    public int getCircle() {
        return this.dataTracker.get(CIRCLE);
    }

    public boolean isProdigy() {
        return this.dataTracker.get(PRODIGY);
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
    public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason,
                                 @Nullable EntityData entityData, @Nullable NbtCompound entityNbt) {
        EntityData data = super.initialize(world, difficulty, spawnReason, entityData, entityNbt);

        Random random = this.getRandom();
        int circle = 1 + random.nextInt(3); // Circle 1..3, uniformly
        boolean prodigy = false;

        // Only Circle 3 mages are eligible, with a 1-in-3 chance, and only when the world allows a new
        // Prodigy (none currently alive, none ever defeated).
        if (circle == 3 && world instanceof ServerWorldAccess && !this.getWorld().isClient) {
            WorldProgressState progress = WorldProgressState.get((ServerWorld) this.getWorld());
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
     * random roll in {@link #initialize}. For a Prodigy this also claims the world's "one Prodigy"
     * slot so the natural-spawn rules stay consistent.
     */
    public void configureForSpawn(int circle, boolean prodigy) {
        int clamped = Math.max(1, Math.min(3, circle));
        applyVariant(clamped, prodigy, this.getRandom());
        if (prodigy && !this.getWorld().isClient && this.getWorld() instanceof ServerWorld world) {
            WorldProgressState.get(world).setProdigyAlive(true);
        }
    }

    /**
     * Turns this mage into a tower guardian (v2.3.0): it holds its post, never despawns, and turns
     * hostile the moment a player crosses into the tower's grounds.
     */
    public void configureAsGuard(Vec3d post, double radius) {
        this.guarding = true;
        this.guardX = post.x;
        this.guardY = post.y;
        this.guardZ = post.z;
        this.guardRadius = radius;
        this.setPersistent();
        String name = plainName();
        this.setCustomName(Text.literal(name + " — Tower Guardian").formatted(Formatting.RED));
    }

    public boolean isGuarding() {
        return guarding;
    }

    /** Assigns circle/prodigy, scales health, and sets the floating name. */
    private void applyVariant(int circle, boolean prodigy, Random random) {
        this.dataTracker.set(CIRCLE, circle);
        this.dataTracker.set(PRODIGY, prodigy);

        double maxHealth = prodigy ? 60.0 : 20.0 + 4.0 * circle; // base 20 + 2 hearts/circle; Prodigy 30 hearts
        EntityAttributeInstance healthAttr = this.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (healthAttr != null) {
            healthAttr.setBaseValue(maxHealth);
        }
        this.setHealth((float) maxHealth);

        String name = NAMES[random.nextInt(NAMES.length)];
        if (prodigy) {
            this.setCustomName(Text.literal(name + " the Prodigy").formatted(Formatting.GOLD, Formatting.BOLD));
            this.setPersistent(); // a world-unique elite must never despawn
        } else {
            this.setCustomName(Text.literal(name + " — Circle " + circle + " Mage").formatted(Formatting.LIGHT_PURPLE));
        }
        this.setCustomNameVisible(true);
    }

    // --- Targeting ------------------------------------------------------------

    @Override
    public boolean damage(DamageSource source, float amount) {
        // Diminishing Thunder: each consecutive lightning hit deals 20% less than the previous one.
        // The streak resets when the mage is struck by any other (non-fire) attack — fire is ignored
        // since it's usually the lightning's own ignition side-effect.
        boolean lightning = source.isOf(DamageTypes.LIGHTNING_BOLT);
        float dealt = lightning ? (float) (amount * Math.pow(THUNDER_FALLOFF, thunderHitStreak)) : amount;
        // Innate magic resistance: 5% per circle against fire / raw magic (lightning keeps its own falloff).
        if (!lightning && com.example.circlemage.spell.MagicResistance.isResistible(source)) {
            dealt *= (1.0f - com.example.circlemage.spell.MagicResistance.forCircle(getCircle()));
        }

        boolean result = super.damage(source, dealt);
        if (result && !this.getWorld().isClient) {
            if (lightning) {
                thunderHitStreak++;
            } else if (!source.isIn(DamageTypeTags.IS_FIRE)) {
                thunderHitStreak = 0;
            }
            // Self-defence: whoever attacked (player or hostile mob) becomes the target — unless
            // the mage is already trying to escape.
            if (!retreating && source.getAttacker() instanceof LivingEntity attacker
                    && attacker.isAlive() && attacker != this) {
                this.setTarget(attacker);
            }
            // Badly hurt non-Prodigies yield rather than fight to the death.
            if (!isProdigy() && this.isAlive() && !retreating
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
        this.escapeTick = this.getWorld().getTime() + 70; // ~3.5s of fleeing before the escape
        this.dropStack(new net.minecraft.item.ItemStack(ModItems.MANA_DUST));
        sayNearby("Enough — I yield! Take the dust and let me be!");
        this.playSound(SoundEvents.ENTITY_VILLAGER_HURT, 1.0f, 1.2f);
    }

    /** Speaks a line into chat for every player close enough to be part of the scene. */
    private void sayNearby(String line) {
        if (!(this.getWorld() instanceof ServerWorld world)) {
            return;
        }
        Text text = Text.literal("<" + plainName() + "> " + line).formatted(Formatting.GRAY, Formatting.ITALIC);
        for (ServerPlayerEntity player : world.getPlayers(p -> p.squaredDistanceTo(this) <= 24.0 * 24.0)) {
            player.sendMessage(text, false);
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
        if (!this.getWorld().isClient && this.age % 40 == 0 && this.getWorld() instanceof ServerWorld world) {
            for (ServerPlayerEntity player : world.getPlayers(p -> p.squaredDistanceTo(this) <= 24.0 * 24.0)) {
                if (ManaPlayer.get(player).setEncounteredWanderingMage()) {
                    player.sendMessage(Text.literal("Your journal records a new encounter: Wandering Mage.")
                            .formatted(Formatting.LIGHT_PURPLE), false);
                    ModNetworking.sendJournalSync(player);
                }
            }
            tickGreetings(world);
        }
    }

    /** A peaceful mage greets each player once when they first come close. (Guards don't chat.) */
    private void tickGreetings(ServerWorld world) {
        if (this.getTarget() != null || retreating || guarding) {
            return;
        }
        for (ServerPlayerEntity player : world.getPlayers(p -> p.squaredDistanceTo(this) <= 6.0 * 6.0)) {
            if (greetedPlayers.add(player.getUuid())) {
                this.getLookControl().lookAt(player, 30.0f, 30.0f);
                player.sendMessage(Text.literal("<" + plainName() + "> "
                                + GREETINGS[this.getRandom().nextInt(GREETINGS.length)])
                        .formatted(Formatting.GRAY, Formatting.ITALIC), false);
            }
        }
    }

    /**
     * Right-click conversation. An emerald buys a Mana Vial; empty-handed (or anything else) earns
     * a bit of guidance matched to the player's own progress. Hostile or fleeing mages don't chat.
     */
    @Override
    protected net.minecraft.util.ActionResult interactMob(PlayerEntity player, net.minecraft.util.Hand hand) {
        if (guarding) {
            if (!this.getWorld().isClient && this.getWorld().getTime() >= loreCooldownEnd) {
                loreCooldownEnd = this.getWorld().getTime() + 60;
                sayNearby("This tower is not yours. Begone.");
            }
            return net.minecraft.util.ActionResult.PASS;
        }
        if (this.getTarget() != null || retreating || !this.isAlive()) {
            return net.minecraft.util.ActionResult.PASS;
        }
        if (this.getWorld().isClient) {
            return net.minecraft.util.ActionResult.SUCCESS;
        }
        if (hand != net.minecraft.util.Hand.MAIN_HAND || !(player instanceof ServerPlayerEntity serverPlayer)) {
            return net.minecraft.util.ActionResult.CONSUME;
        }
        this.getLookControl().lookAt(player, 30.0f, 30.0f);

        net.minecraft.item.ItemStack held = player.getStackInHand(hand);
        if (held.isOf(net.minecraft.item.Items.EMERALD)) {
            if (!player.getAbilities().creativeMode) {
                held.decrement(1);
            }
            net.minecraft.item.ItemStack vial = new net.minecraft.item.ItemStack(ModItems.MANA_VIAL);
            if (!player.getInventory().insertStack(vial)) {
                player.dropItem(vial, false);
            }
            sayNearby("A fair trade, traveler. Drink it slowly — bottled mana bites.");
            this.playSound(SoundEvents.ENTITY_VILLAGER_TRADE, 1.0f, 1.1f);
            if (this.getWorld() instanceof ServerWorld world) {
                world.spawnParticles(com.example.circlemage.particle.ModParticles.ARCANE,
                        this.getX(), this.getBodyY(0.7), this.getZ(), 10, 0.3, 0.4, 0.3, 0.03);
            }
            return net.minecraft.util.ActionResult.CONSUME;
        }

        long now = this.getWorld().getTime();
        if (now >= loreCooldownEnd) {
            loreCooldownEnd = now + 60; // don't monologue when click-spammed
            int circle = ManaPlayer.get(serverPlayer).getCircle();
            String[] pool = circle <= 1 ? LORE_EARLY : (circle <= 4 ? LORE_MID : LORE_LATE);
            serverPlayer.sendMessage(Text.literal("<" + plainName() + "> "
                            + pool[this.getRandom().nextInt(pool.length)])
                    .formatted(Formatting.GRAY, Formatting.ITALIC), false);
            this.playSound(SoundEvents.ENTITY_VILLAGER_AMBIENT, 1.0f, 1.1f);
        }
        return net.minecraft.util.ActionResult.CONSUME;
    }

    @Override
    protected void mobTick() {
        super.mobTick();

        // Yielded: keep fleeing the nearest player, then vanish in a flash of light.
        if (retreating && this.getWorld() instanceof ServerWorld world) {
            this.setTarget(null);
            long now = world.getTime();
            if (now >= escapeTick) {
                world.spawnParticles(ModParticles.ARCANE, this.getX(), this.getBodyY(0.5), this.getZ(),
                        40, 0.4, 0.9, 0.4, 0.08);
                world.spawnParticles(ModParticles.RADIANCE, this.getX(), this.getBodyY(0.5), this.getZ(),
                        20, 0.3, 0.8, 0.3, 0.05);
                world.playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.NEUTRAL, 1.0f, 1.4f);
                sayNearby("Until we meet again...");
                this.discard();
                return;
            }
            if (this.age % 10 == 0) {
                PlayerEntity nearest = world.getClosestPlayer(this, 24.0);
                if (nearest != null) {
                    Vec3d away = this.getPos().subtract(nearest.getPos());
                    away = new Vec3d(away.x, 0.0, away.z);
                    if (away.lengthSquared() < 1.0e-4) {
                        away = new Vec3d(1, 0, 0);
                    }
                    Vec3d spot = this.getPos().add(away.normalize().multiply(10.0));
                    this.getNavigation().startMovingTo(spot.x, spot.y, spot.z, 1.15);
                }
            }
            return;
        }

        // Drop the target only on the documented conditions: player dead, beyond 75 blocks, or gone.
        LivingEntity target = this.getTarget();
        if (target != null && (!target.isAlive() || this.squaredDistanceTo(target) > 75.0 * 75.0)) {
            this.setTarget(null);
            this.prevTargetDist = -1.0;
        }

        // Tower guardian: anyone stepping onto the tower grounds is an intruder; otherwise stay
        // near the post (guards never wander off with the idle goals).
        if (guarding && !retreating && this.getTarget() == null && this.age % 10 == 0
                && this.getWorld() instanceof ServerWorld guardWorld) {
            PlayerEntity intruder = guardWorld.getClosestPlayer(guardX, guardY, guardZ, guardRadius, true);
            if (intruder != null && intruder.isAlive()) {
                this.setTarget(intruder);
                sayNearby("You trespass on a mage's tower. Leave — or be removed!");
                this.playSound(SoundEvents.ENTITY_EVOKER_PREPARE_ATTACK, 1.0f, 1.2f);
            } else if (this.squaredDistanceTo(guardX, guardY, guardZ) > 20.0 * 20.0) {
                this.getNavigation().startMovingTo(guardX, guardY, guardZ, 1.0);
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
                this.setVelocity(0.0, this.getVelocity().y, 0.0); // no drift while committing
                this.getLookControl().lookAt(castTarget, 30.0f, 30.0f);
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
     * then fires the locked shot in {@link #fireLockedMissile} (see {@link #mobTick}).
     */
    public void tryStartMagicMissile(LivingEntity target) {
        long now = this.getWorld().getTime();
        if (now < missileCooldownEnd || isBusyCasting() || !(this.getWorld() instanceof ServerWorld)) {
            return;
        }
        missileCooldownEnd = now + cooldown(Spell.MAGIC_MISSILE.cooldownTicks);
        beginMissileCast(target);
    }

    /** Locks the final firing direction (horizontal lead + horizontal-only inaccuracy) and telegraphs it. */
    private void beginMissileCast(LivingEntity target) {
        Vec3d from = this.getEyePos();
        Vec3d targetCenter = target.getBoundingBox().getCenter();
        // Prediction leads the target's LATERAL (x/z) movement only. The aim height is the target's
        // current centre — never lead vertical velocity, so a jumping/falling player can't drag the
        // shot into the sky or the ground.
        double leadTicks = from.distanceTo(targetCenter) / MagicMissileEntity.SPEED;
        Vec3d vel = target.getVelocity();
        Vec3d predicted = new Vec3d(
                targetCenter.x + vel.x * leadTicks,
                targetCenter.y,
                targetCenter.z + vel.z * leadTicks);
        Vec3d dir = predicted.subtract(from);
        if (dir.lengthSquared() < 1.0e-4) {
            dir = this.getRotationVec(1.0f);
        }
        dir = dir.normalize();

        // Inaccuracy: half of all shots are perfectly accurate; the rest get a purely horizontal
        // (yaw) deviation in [-5, +5] degrees — a misjudged lateral lead, never a vertical miss.
        if (this.getRandom().nextDouble() >= MISSILE_ACCURATE_CHANCE) {
            double spread = Math.toRadians((this.getRandom().nextDouble() * 2.0 - 1.0) * MISSILE_INACCURACY_DEG);
            double cos = Math.cos(spread);
            double sin = Math.sin(spread);
            // Rotate only the horizontal (x/z) components about Y; y is untouched, so length stays 1.
            dir = new Vec3d(dir.x * cos - dir.z * sin, dir.y, dir.x * sin + dir.z * cos);
        }

        lockedDirX = dir.x;
        lockedDirY = dir.y;
        lockedDirZ = dir.z;
        missileCastTicks = MISSILE_CAST_TICKS;
        this.dataTracker.set(CASTING, true);
        this.dataTracker.set(AIM_X, (float) dir.x);
        this.dataTracker.set(AIM_Y, (float) dir.y);
        this.dataTracker.set(AIM_Z, (float) dir.z);
        this.getNavigation().stop();
        this.playSound(SoundEvents.ENTITY_EVOKER_CAST_SPELL, 0.8f, 1.5f);
    }

    /** Fires the Magic Missile straight along the locked direction (no homing — it follows the telegraph). */
    private void fireLockedMissile() {
        if (!(this.getWorld() instanceof ServerWorld)) {
            return;
        }
        Vec3d dir = new Vec3d(lockedDirX, lockedDirY, lockedDirZ);
        if (dir.lengthSquared() < 1.0e-4) {
            dir = this.getRotationVec(1.0f);
        }
        MagicMissileEntity missile = new MagicMissileEntity(this.getWorld(), this, magicDamage());
        missile.launch(dir.normalize());
        this.getWorld().spawnEntity(missile);
        this.playSound(SoundEvents.ENTITY_ILLUSIONER_CAST_SPELL, 0.8f, 1.3f);
    }

    private void cancelMissileCast() {
        missileCastTicks = -1;
        this.dataTracker.set(CASTING, false);
    }

    // --- Telegraph state (read by the client renderer) ------------------------

    public boolean isCasting() {
        return this.dataTracker.get(CASTING);
    }

    public Vec3d getAimDirection() {
        return new Vec3d(this.dataTracker.get(AIM_X), this.dataTracker.get(AIM_Y), this.dataTracker.get(AIM_Z));
    }

    /** Close range: a Wind Wall that knocks the target away to re-open distance. */
    public void tryWindWall(LivingEntity target) {
        long now = this.getWorld().getTime();
        if (now < windCooldownEnd) {
            return;
        }
        windCooldownEnd = now + cooldown(Spell.WALL_OF_WIND.cooldownTicks);

        Vec3d push = target.getPos().subtract(this.getPos());
        push = new Vec3d(push.x, 0.0, push.z);
        if (push.lengthSquared() < 1.0e-4) {
            push = new Vec3d(this.getRandom().nextDouble() - 0.5, 0, this.getRandom().nextDouble() - 0.5);
        }
        push = push.normalize().multiply(2.0).add(0, 0.45, 0);
        target.addVelocity(push.x, push.y, push.z);
        target.velocityModified = true;

        if (this.getWorld() instanceof ServerWorld world) {
            Vec3d eye = this.getEyePos();
            Vec3d horiz = push.multiply(1, 0, 1).normalize();
            for (double f = 1.0; f <= 5.0; f += 1.0) {
                world.spawnParticles(ModParticles.GALE, eye.x + horiz.x * f, eye.y, eye.z + horiz.z * f,
                        2, 0.3, 0.3, 0.3, 0.4);
            }
        }
        this.playSound(SoundEvents.ENTITY_PHANTOM_FLAP, 1.0f, 0.8f);
    }

    /** Mid range: lay a lingering fire field beneath the target. */
    public void tryBurn(LivingEntity target) {
        long now = this.getWorld().getTime();
        if (now < burnCooldownEnd || !(this.getWorld() instanceof ServerWorld world)) {
            return;
        }
        burnCooldownEnd = now + cooldown(Spell.FIREBALL.cooldownTicks);
        float burnBonus = magicDamage() * 0.25f;
        AreaEffectManager.add(new AreaEffect(world, target.getPos(), 3.0, AreaEffect.Type.FIRE, 100, 0, burnBonus));
        world.spawnParticles(ModParticles.EMBER, target.getX(), target.getY() + 0.2, target.getZ(),
                40, 1.2, 0.3, 1.2, 0.06);
        world.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.HOSTILE, 1.0f, 1.0f);
    }

    /** Emergency (once per life, below 50% HP): call lightning down on the target. */
    public void tryThunder(LivingEntity target) {
        if (usedThunder || !(this.getWorld() instanceof ServerWorld world)) {
            return;
        }
        usedThunder = true;
        LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world);
        if (bolt != null) {
            bolt.refreshPositionAfterTeleport(target.getX(), target.getY(), target.getZ());
            world.spawnEntity(bolt);
        }
        world.spawnParticles(ModParticles.SPARK, this.getX(), this.getY() + 1.0, this.getZ(), 30, 0.4, 0.6, 0.4, 0.3);
    }

    public boolean isDashReady() {
        return this.getWorld().getTime() >= dashCooldownEnd;
    }

    /** Dash: a quick burst in {@code direction} (used for evasion and repositioning). */
    public boolean tryDash(Vec3d direction) {
        if (!isDashReady()) {
            return false;
        }
        Vec3d horiz = new Vec3d(direction.x, 0.0, direction.z);
        if (horiz.lengthSquared() < 1.0e-4) {
            return false;
        }
        dashCooldownEnd = this.getWorld().getTime() + Math.round(Spell.DASH.cooldownTicks * DASH_COOLDOWN_MULT);
        horiz = horiz.normalize().multiply(0.75); // dash strength reduced 50% (was 1.5; 1.0.8.1)
        this.setVelocity(horiz.x, 0.3, horiz.z);
        this.velocityModified = true;
        this.fallDistance = 0.0f;
        if (this.getWorld() instanceof ServerWorld world) {
            world.spawnParticles(ModParticles.GALE, this.getX(), this.getY() + 0.9, this.getZ(), 12, 0.3, 0.3, 0.3, 0.3);
        }
        this.playSound(SoundEvents.ENTITY_PHANTOM_FLAP, 0.7f, 1.5f);
        return true;
    }

    /**
     * Projectile evasion. If an incoming projectile is heading at the mage, dash perpendicular to it
     * (priority 1) or, if dash is on cooldown, hop sideways via normal movement (priority 2).
     *
     * @return true if an evasive action was taken this tick.
     */
    public boolean handleProjectileEvasion() {
        if (!(this.getWorld() instanceof ServerWorld world)) {
            return false;
        }
        Box box = this.getBoundingBox().expand(10.0);
        Vec3d myCenter = this.getBoundingBox().getCenter();
        for (ProjectileEntity proj : world.getEntitiesByClass(ProjectileEntity.class, box,
                p -> p.isAlive() && p.getOwner() != this)) {
            Vec3d toMe = myCenter.subtract(proj.getPos());
            double dist = toMe.length();
            if (dist < 0.2 || dist > 10.0) {
                continue;
            }
            Vec3d vel = proj.getVelocity();
            if (vel.lengthSquared() < 1.0e-4) {
                continue;
            }
            if (vel.normalize().dotProduct(toMe.normalize()) > 0.85) {
                // Heading roughly at us — evade perpendicular to the projectile's flight.
                Vec3d perp = new Vec3d(-vel.z, 0.0, vel.x).normalize().multiply(this.getRandom().nextBoolean() ? 1 : -1);
                if (tryDash(perp)) {
                    return true;
                }
                this.addVelocity(perp.x * 0.6, 0.1, perp.z * 0.6);
                this.velocityModified = true;
                return true;
            }
        }
        return false;
    }

    // --- Death / removal ------------------------------------------------------

    @Override
    public void onDeath(DamageSource damageSource) {
        // Every mage sheds Mana Dust; the Prodigy sheds a generous pile (plus its crystal, below).
        if (!this.getWorld().isClient) {
            int dust = 1 + this.getRandom().nextInt(2) + (isProdigy() ? 2 : 0);
            this.dropStack(new net.minecraft.item.ItemStack(ModItems.MANA_DUST, dust));
        }
        if (!this.getWorld().isClient && isProdigy() && this.getWorld() instanceof ServerWorld world) {
            // Prodigy death event: lock out future Prodigies and unlock the Master framework.
            WorldProgressState.get(world).markProdigyDefeated();
            // Reward: drop a Mana Crystal.
            this.dropStack(new net.minecraft.item.ItemStack(ModItems.MANA_CRYSTAL));
            Text line = Text.literal("My master will avenge me...").formatted(Formatting.DARK_RED, Formatting.ITALIC);
            if (damageSource.getAttacker() instanceof PlayerEntity killer) {
                killer.sendMessage(line, false);
            }
            for (PlayerEntity nearby : world.getPlayers(p -> p.squaredDistanceTo(this) <= 64.0 * 64.0)) {
                if (damageSource.getAttacker() != nearby) {
                    nearby.sendMessage(line, false);
                }
            }
        }
        super.onDeath(damageSource);
    }

    @Override
    public void remove(RemovalReason reason) {
        // If a Prodigy is truly gone (killed or discarded — not merely a chunk unload), free the
        // "one Prodigy at a time" slot so a future one may eventually spawn (unless it was defeated,
        // which onDeath already handled by also setting the permanent flag).
        if (!this.getWorld().isClient && isProdigy() && reason.shouldDestroy()
                && this.getWorld() instanceof ServerWorld world) {
            WorldProgressState.get(world).setProdigyAlive(false);
        }
        super.remove(reason);
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return !isProdigy() && super.canImmediatelyDespawn(distanceSquared);
    }

    // --- NBT ------------------------------------------------------------------

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("mageCircle", getCircle());
        nbt.putBoolean("prodigy", isProdigy());
        nbt.putBoolean("usedThunder", usedThunder);
        nbt.putBoolean("guarding", guarding);
        if (guarding) {
            nbt.putDouble("guardX", guardX);
            nbt.putDouble("guardY", guardY);
            nbt.putDouble("guardZ", guardZ);
            nbt.putDouble("guardRadius", guardRadius);
        }
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        this.dataTracker.set(CIRCLE, Math.max(1, Math.min(3, nbt.getInt("mageCircle"))));
        this.dataTracker.set(PRODIGY, nbt.getBoolean("prodigy"));
        usedThunder = nbt.getBoolean("usedThunder");
        guarding = nbt.getBoolean("guarding");
        if (guarding) {
            guardX = nbt.getDouble("guardX");
            guardY = nbt.getDouble("guardY");
            guardZ = nbt.getDouble("guardZ");
            guardRadius = Math.max(4.0, nbt.getDouble("guardRadius"));
        }
    }
}
