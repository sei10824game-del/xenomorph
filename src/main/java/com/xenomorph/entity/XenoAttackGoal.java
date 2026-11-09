package com.xenomorph.entity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/** Chases the target and picks a fitting attack by distance, caste and cooldown. */
public class XenoAttackGoal extends Goal {
    private final XenoEntity x;
    private int repath, stuck;
    private net.minecraft.world.phys.Vec3 lastPos = net.minecraft.world.phys.Vec3.ZERO;

    public XenoAttackGoal(XenoEntity x) { this.x = x; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }

    @Override public boolean canUse() { LivingEntity t = x.getTarget(); return t != null && t.isAlive(); }
    @Override public boolean canContinueToUse() { return canUse() || x.attack() != Attack.NONE; }
    @Override public void stop() { x.getNavigation().stop(); }

    @Override public void tick() {
        LivingEntity t = x.getTarget();
        if (t == null || x.attack() != Attack.NONE) return;
        double d = Math.sqrt(x.distanceToSqr(t));
        x.getLookControl().setLookAt(t, 30, 30);
        if (--repath <= 0) {
            repath = 4 + x.getRandom().nextInt(4);
            double sp = d > 6 && !x.isBiped() ? 1.5 : 1.15;
            // no path (big body, odd terrain, unreachable): steer straight at the target instead of standing still
            if (!x.getNavigation().moveTo(t, sp) || x.getNavigation().isDone() && d > x.reach())
                x.getMoveControl().setWantedPosition(t.getX(), t.getY(), t.getZ(), sp);
        }
        if (++stuck >= 20) {                                  // watchdog: barely moved for a second while chasing
            stuck = 0;
            if (x.position().distanceToSqr(lastPos) < 0.25 && d > 3) { x.getJumpControl().jump(); x.getMoveControl().setWantedPosition(t.getX(), t.getY(), t.getZ(), 1.3); x.getNavigation().stop(); repath = 0; }
            lastPos = x.position();
        }
        if (x.cooldown > 0 || !x.hasLineOfSight(t)) return;
        double sc = Math.max(1, x.caste.scale * 0.9);
        List<Attack> pool = new ArrayList<>();
        for (Attack a : x.caste.attacks) {
            if (!x.ready(a)) continue;
            double max = a.max < 8 ? a.max * sc : a.max;
            if (d < a.min || d > max) continue;
            int w = switch (a) { case CLAW_L, CLAW_R -> 4; case DOUBLE_CLAW -> 3; case BITE -> d < 2.4 * sc ? 2 : 0;
                case GRAB -> 2; case TAIL_STAB -> 3; case TAIL_SWEEP -> x.level().getEntitiesOfClass(LivingEntity.class, x.getBoundingBox().inflate(3.5), e -> !XenoEntity.isXeno(e)).size() > 1 ? 5 : 1;
                case SCREECH -> x.getHealth() < x.getMaxHealth() * 0.7f || d > 8 ? 3 : 0;
                case LAY -> 0; case SUMMON -> 2; case DETONATE -> d < 4.5 ? 6 : 0; case SPORE -> 2; case IMPREGNATE, GRAB_SLAM, DEATH_ROLL -> 3; default -> 4; };
            for (int i = 0; i < w; i++) pool.add(a);
        }
        if (!pool.isEmpty()) x.startAttack(pool.get(x.getRandom().nextInt(pool.size())));
    }
}
