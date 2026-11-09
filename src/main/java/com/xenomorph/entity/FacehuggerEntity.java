package com.xenomorph.entity;

import com.xenomorph.XenomorphMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Scuttles, leaps, clamps onto the face and implants an embryo. */
public class FacehuggerEntity extends Monster {
    private static final EntityDataAccessor<Boolean> ATTACHED = SynchedEntityData.defineId(FacehuggerEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> LEAP = SynchedEntityData.defineId(FacehuggerEntity.class, EntityDataSerializers.INT);
    private LivingEntity host;
    private int hostId = -1, attachTicks, leapCd;
    public int clientLeapTick;

    public FacehuggerEntity(EntityType<? extends FacehuggerEntity> t, Level l) { super(t, l); xpReward = 3; }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 8).add(Attributes.MOVEMENT_SPEED, 0.36).add(Attributes.ATTACK_DAMAGE, 1).add(Attributes.FOLLOW_RANGE, 24);
    }
    @Override protected void defineSynchedData() { super.defineSynchedData(); entityData.define(ATTACHED, false); entityData.define(LEAP, 0); }
    public boolean isAttached() { return entityData.get(ATTACHED); }
    /** >0 while airborne in a leap (ticks since launch) */
    public int leapTick() { return entityData.get(LEAP); }

    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new LeapAtTargetGoal(this, 0.55f) {
            @Override public boolean canUse() { return !isAttached() && leapCd <= 0 && super.canUse(); }
            @Override public void start() { super.start(); leapCd = 40; entityData.set(LEAP, 1); }
        });
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.5, true) { @Override public boolean canUse() { return !isAttached() && super.canUse(); } });
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.9));
        goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, true));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Animal.class, 10, true, false, e -> !(e instanceof net.minecraft.world.entity.animal.Wolf) || true));
    }

    @Override public void tick() {
        super.tick();
        if (leapCd > 0) leapCd--;
        if (level().isClientSide) { if (leapTick() > 0) clientLeapTick++; else clientLeapTick = 0; return; }
        if (leapTick() > 0) { int l = leapTick() + 1; entityData.set(LEAP, onGround() && l > 6 ? 0 : Math.min(l, 60)); }
        if (isAttached()) { tickAttached(); return; }
        LivingEntity t = getTarget();
        if (t != null && !XenoEntity.isXeno(t) && getBoundingBox().inflate(0.15).intersects(t.getBoundingBox()) && leapTick() > 0) attach(t);
    }

    private void attach(LivingEntity t) {
        if (t.hasEffect(XenomorphMod.EMBRYO.get())) return;
        net.minecraft.world.item.ItemStack helm = t.getItemBySlot(EquipmentSlot.HEAD);
        if (!helm.isEmpty()) { helm.hurtAndBreak(40, t, e -> e.broadcastBreakEvent(EquipmentSlot.HEAD)); t.hurt(damageSources().magic(), 2); if (!t.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) { setDeltaMovement(0, 0.3, 0); return; } }
        host = t; hostId = t.getId(); attachTicks = 0;
        entityData.set(ATTACHED, true);
        t.addEffect(new MobEffectInstance(XenomorphMod.EMBRYO.get(), 2400, 0, false, true, true));
        playSound(SoundEvents.SLIME_SQUISH, 1.2f, 0.6f);
    }
    private void tickAttached() {
        if (host == null || !host.isAlive()) { discard(); return; }
        attachTicks++;
        host.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 3, false, false));
        host.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 25, 0, false, false));
        host.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 25, 2, false, false));
        host.addEffect(new MobEffectInstance(MobEffects.WITHER, 6, 0, false, false));
        if (attachTicks == 1) host.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 0, false, false));
        Vec3 look = Vec3.directionFromRotation(0, host.yHeadRot);
        setPos(host.getX() + look.x * 0.05, host.getEyeY() - 0.2, host.getZ() + look.z * 0.05);
        setYRot(host.yHeadRot); yBodyRot = host.yHeadRot; setYHeadRot(host.yHeadRot);
        setDeltaMovement(Vec3.ZERO);
        if (attachTicks > 400) { // sated, falls off and dies
            hurt(damageSources().generic(), 1000f);
        }
    }
    @Override public boolean isNoGravity() { return isAttached() || super.isNoGravity(); }
    @Override public boolean isPushable() { return !isAttached(); }
    @Override public boolean hurt(DamageSource s, float a) {
        if (isAttached() && s.getEntity() == host) a *= 0.5f; // hard to peel off
        return super.hurt(s, a);
    }
    @Override public void die(DamageSource s) {
        super.die(s);
        if (!level().isClientSide && getLastHurtByMob() != null) getLastHurtByMob().hurt(damageSources().magic(), 1.5f);
    }
    @Override protected boolean isSunBurnTick() { return false; }
    @Override protected SoundEvent getHurtSound(DamageSource s) { return SoundEvents.SPIDER_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.SPIDER_DEATH; }
    @Override protected SoundEvent getAmbientSound() { return isAttached() ? null : SoundEvents.SILVERFISH_AMBIENT; }
}
