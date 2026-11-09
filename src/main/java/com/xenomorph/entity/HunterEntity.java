package com.xenomorph.entity;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Xeno hunters. Predator: cloaks, plasma caster, wrist blades. Engineer: huge, hits like a truck.
 * Neutral to players until hurt, hostile to every xeno faction. Both are valid hosts: a Predator gives birth to a
 * Predalien, an Engineer to a Deacon.
 */
public class HunterEntity extends Monster {
    public enum Kind { PREDATOR, ENGINEER }
    public final Kind kind;
    private int shootCd = 40, swingCd;

    public HunterEntity(EntityType<? extends HunterEntity> t, Level l, Kind kind) {
        super(t, l); this.kind = kind; this.xpReward = 30; setMaxUpStep(1.1f);
    }
    public static AttributeSupplier.Builder attributes(Kind k) {
        boolean p = k == Kind.PREDATOR;
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, p ? 90 : 140).add(Attributes.ATTACK_DAMAGE, p ? 11 : 16)
                .add(Attributes.MOVEMENT_SPEED, p ? 0.33 : 0.28).add(Attributes.FOLLOW_RANGE, 40).add(Attributes.ARMOR, p ? 10 : 6)
                .add(Attributes.KNOCKBACK_RESISTANCE, p ? 0.4 : 0.8);
    }

    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new HuntGoal());
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, net.minecraft.world.entity.player.Player.class, 10));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false, e -> { int f = XenoEntity.faction(e); return f == 1 || f == 2 || f == 4 || f == 5; }));
    }

    @Override public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (swingCd > 0) swingCd--;
        if (shootCd > 0) shootCd--;
        if (kind == Kind.PREDATOR) {                 // cloak while stalking, drop it to fight
            LivingEntity t = getTarget();
            boolean stalk = t != null && distanceToSqr(t) > 100;
            if (stalk && !hasEffect(MobEffects.INVISIBILITY)) addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 60, 0, false, false));
            else if (t != null && !stalk) removeEffect(MobEffects.INVISIBILITY);
        }
    }

    private class HuntGoal extends Goal {
        HuntGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        @Override public boolean canUse() { LivingEntity t = getTarget(); return t != null && t.isAlive(); }
        @Override public void tick() {
            LivingEntity t = getTarget(); if (t == null) return;
            double d = Math.sqrt(distanceToSqr(t));
            getLookControl().setLookAt(t, 40, 40);
            if (kind == Kind.PREDATOR && d > 5 && d < 24 && hasLineOfSight(t)) {
                if (d < 12) getNavigation().stop(); else getNavigation().moveTo(t, 1.0);
                if (shootCd <= 0) {
                    shootCd = 45; swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                    AcidSpit b = new AcidSpit(level(), HunterEntity.this, 9f); b.plasma = true;
                    Vec3 e = position().add(0, getEyeHeight() - 0.2, 0); b.setPos(e);
                    b.shoot(t.getX() - e.x, t.getEyeY() - e.y, t.getZ() - e.z, 2.0f, 1f);
                    level().addFreshEntity(b); playSound(SoundEvents.BLAZE_SHOOT, 1.5f, 0.6f);
                }
            } else {
                getNavigation().moveTo(t, 1.2);
                if (d < 2.8 && swingCd <= 0) {
                    swingCd = kind == Kind.PREDATOR ? 14 : 24; swing(net.minecraft.world.InteractionHand.MAIN_HAND); doHurtTarget(t);
                    if (kind == Kind.ENGINEER) { Vec3 f = t.position().subtract(position()).multiply(1, 0, 1).normalize(); t.push(f.x * 1.4, 0.5, f.z * 1.4); t.hurtMarked = true; playSound(SoundEvents.IRON_GOLEM_ATTACK, 1.5f, 0.5f); }
                }
            }
        }
    }

    @Override protected SoundEvent getAmbientSound() { return kind == Kind.PREDATOR ? SoundEvents.RAVAGER_STUNNED : null; }
    @Override protected SoundEvent getHurtSound(DamageSource s) { return kind == Kind.PREDATOR ? SoundEvents.RAVAGER_HURT : SoundEvents.IRON_GOLEM_HURT; }
    @Override protected SoundEvent getDeathSound() { return kind == Kind.PREDATOR ? SoundEvents.RAVAGER_DEATH : SoundEvents.IRON_GOLEM_DEATH; }
    @Override public boolean removeWhenFarAway(double d) { return false; }
}
