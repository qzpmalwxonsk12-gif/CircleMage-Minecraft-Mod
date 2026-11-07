package com.example.circlemage.entity;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;

/**
 * The Wandering Mage's combat brain. Runs while the mage has a player target and drives all of its
 * ranged-fighter behaviour:
 *
 * <ul>
 *     <li><b>Casting by range</b> — Magic Missile at long/mid range, Burn beneath the player at mid
 *     range, Wind Wall to create space when rushed, and a once-per-life Thunder below 50% HP.</li>
 *     <li><b>Projectile evasion</b> — dash perpendicular to incoming projectiles (or sidestep if dash
 *     is on cooldown), taking priority over positioning.</li>
 *     <li><b>Positioning</b> — back off (dash if able) when the player rushes in, close the gap when
 *     the player retreats, and otherwise strafe while holding combat distance. It never flees.</li>
 * </ul>
 *
 * Targeting itself is handled on the entity ({@link WanderingMageEntity#damage} sets the target;
 * {@link WanderingMageEntity#mobTick()} drops it when the player dies or leaves the 75-block range).
 */
public class WanderingMageCombatGoal extends Goal {

    /** Preferred standoff distance the mage tries to hold when neither rushing nor retreating. */
    private static final double IDEAL_DISTANCE = 18.0;
    private static final double MAX_RANGE = 50.0;

    private final WanderingMageEntity mage;

    public WanderingMageCombatGoal(WanderingMageEntity mage) {
        this.mage = mage;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        LivingEntity target = mage.getTarget();
        return !mage.isRetreating() && target != null && target.isAlive();
    }

    @Override
    public boolean shouldContinue() {
        LivingEntity target = mage.getTarget();
        return !mage.isRetreating() && target != null && target.isAlive()
                && mage.squaredDistanceTo(target) <= 75.0 * 75.0;
    }

    @Override
    public void stop() {
        mage.getNavigation().stop();
        mage.prevTargetDist = -1.0;
    }

    @Override
    public boolean canStop() {
        // Only yielding (retreat) interrupts combat — otherwise the mage stays aggressive until
        // target loss/death.
        return mage.isRetreating();
    }

    @Override
    public void tick() {
        LivingEntity target = mage.getTarget();
        if (target == null) {
            return;
        }

        mage.getLookControl().lookAt(target, 30.0f, 30.0f);

        // While committing to a Magic Missile the mage is frozen: keep facing the target (done above)
        // but take no other action — no movement, no dash, no other spell — until the shot fires.
        if (mage.isBusyCasting()) {
            return;
        }

        double dist = mage.distanceTo(target);
        boolean canSee = mage.canSee(target);

        // --- Defensive AI: projectile evasion takes priority over everything else. ---
        if (mage.handleProjectileEvasion()) {
            return;
        }

        // --- Combat AI: cast by range band. ---
        if (dist >= 20.0 && dist <= MAX_RANGE) {
            // Long range: prefer Magic Missile whenever it's off cooldown and line of sight exists.
            if (canSee) {
                mage.tryStartMagicMissile(target);
            }
        } else if (dist >= 5.0) {
            // Mid range: keep firing Magic Missile and lay Burn beneath the player.
            if (canSee) {
                mage.tryStartMagicMissile(target);
            }
            if (!mage.isBusyCasting()) {
                mage.tryBurn(target);
            }
        } else {
            // Close range: shove the player away to re-open distance.
            mage.tryWindWall(target);
        }

        // Emergency ability: Thunder once, below half health (not while committing a missile).
        if (!mage.isBusyCasting() && mage.getHealth() <= mage.getMaxHealth() * 0.5f) {
            mage.tryThunder(target);
        }

        // --- Positioning AI (skipped if a missile commit just began this tick). ---
        if (!mage.isBusyCasting()) {
            updatePositioning(target, dist);
        }
    }

    private void updatePositioning(LivingEntity target, double dist) {
        double prev = mage.prevTargetDist;
        mage.prevTargetDist = dist;
        boolean rushing = prev >= 0 && dist < prev - 0.03;     // player closing the gap
        boolean retreating = prev >= 0 && dist > prev + 0.03;  // player backing away

        Vec3d mPos = mage.getPos();
        Vec3d tPos = target.getPos();
        Vec3d away = horizontal(mPos.subtract(tPos));   // from target -> mage
        Vec3d toward = horizontal(tPos.subtract(mPos)); // from mage -> target

        // Mages only ever walk (speed factor 1.0) — they never sprint. Dash is their only burst.
        if (dist < 6.0 || rushing) {
            // Player rushed in: dash backwards if able, otherwise back off normally (never flee far).
            if (!mage.tryDash(away)) {
                Vec3d spot = mPos.add(away.multiply(8.0));
                mage.getNavigation().startMovingTo(spot.x, spot.y, spot.z, 1.0);
            }
        } else if (retreating || dist > IDEAL_DISTANCE + 8.0) {
            // Player retreated (or drifted too far): move toward them, dashing to close big gaps.
            if (dist > 25.0) {
                mage.tryDash(toward);
            }
            Vec3d spot = tPos.add(away.multiply(IDEAL_DISTANCE)); // hold at the standoff distance
            mage.getNavigation().startMovingTo(spot.x, spot.y, spot.z, 1.0);
        } else {
            // Hold distance: strafe around the player, flipping direction periodically.
            if (mage.age % 40 == 0) {
                mage.strafeSign = -mage.strafeSign;
            }
            Vec3d perp = new Vec3d(-toward.z, 0.0, toward.x).multiply(mage.strafeSign);
            Vec3d spot = tPos.add(away.multiply(IDEAL_DISTANCE)).add(perp.multiply(4.0));
            mage.getNavigation().startMovingTo(spot.x, spot.y, spot.z, 1.0);
        }
    }

    private static Vec3d horizontal(Vec3d v) {
        Vec3d h = new Vec3d(v.x, 0.0, v.z);
        return h.lengthSquared() < 1.0e-4 ? new Vec3d(0, 0, 1) : h.normalize();
    }
}
