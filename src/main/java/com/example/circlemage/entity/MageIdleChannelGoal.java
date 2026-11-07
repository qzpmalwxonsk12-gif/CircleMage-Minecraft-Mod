package com.example.circlemage.entity;

import com.example.circlemage.particle.ModParticles;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.server.world.ServerWorld;

import java.util.EnumSet;

/**
 * Ambient behaviour: an idle Wandering Mage occasionally stops to channel mana — it stands still
 * for a few seconds wrapped in the same rising spiral of arcane motes a channelling player gets.
 * Purely cosmetic (mages have infinite mana), but it makes them read as practitioners living in
 * the world rather than props waiting to be attacked.
 */
public class MageIdleChannelGoal extends Goal {

    private final WanderingMageEntity mage;
    private int ticksLeft;

    public MageIdleChannelGoal(WanderingMageEntity mage) {
        this.mage = mage;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        return mage.getTarget() == null && !mage.isRetreating() && mage.isOnGround()
                && mage.getRandom().nextInt(180) == 0; // roughly every ~9s of idling
    }

    @Override
    public boolean shouldContinue() {
        return ticksLeft > 0 && mage.getTarget() == null && !mage.isRetreating();
    }

    @Override
    public void start() {
        ticksLeft = 80 + mage.getRandom().nextInt(80); // 4-8 seconds
        mage.getNavigation().stop();
    }

    @Override
    public void tick() {
        ticksLeft--;
        mage.getNavigation().stop();

        if (!(mage.getWorld() instanceof ServerWorld world) || mage.age % 3 != 0) {
            return;
        }
        // The player's channel aura: two motes spiralling up around the body.
        double radius = 0.55;
        double baseAngle = (mage.age % 40) * (Math.PI * 2.0 / 40.0);
        for (int i = 0; i < 2; i++) {
            double angle = baseAngle + i * Math.PI;
            double px = mage.getX() + Math.cos(angle) * radius;
            double pz = mage.getZ() + Math.sin(angle) * radius;
            double py = mage.getY() + (mage.age % 20) / 20.0 * 1.9;
            world.spawnParticles(ModParticles.ARCANE, px, py, pz, 1, 0.0, 0.04, 0.0, 0.01);
        }
    }
}
