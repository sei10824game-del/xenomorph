package com.xenomorph.entity;

import com.xenomorph.XenomorphMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Slithering newborn; grows into a drone (or a dog-runner when born from a wolf). */
public class ChestbursterEntity extends Monster {
    public Caste form = Caste.DRONE;   // what it grows into: depends on the host species
    private int age;
    public static final int GROW = 6000, BURST = 36;

    public ChestbursterEntity(EntityType<? extends ChestbursterEntity> t, Level l) { super(t, l); xpReward = 5; }
    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 10).add(Attributes.MOVEMENT_SPEED, 0.34).add(Attributes.ATTACK_DAMAGE, 2);
    }
    public int age() { return age; }
    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Player.class, 8f, 1.2, 1.5));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.4, true));
        goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 1.0));
        goalSelector.addGoal(5, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal(this, XenoEntity.class));
    }
    @Override public void tick() {
        super.tick();
        age++;
        if (age < BURST) { setDeltaMovement(getDeltaMovement().multiply(0.2, 1, 0.2)); if (age == 2) playSound(SoundEvents.RAVAGER_ROAR, 1.2f, 2.0f); }
        if (!level().isClientSide && age >= (form == Caste.RED_QUEEN_MOTHER ? 1500 : GROW) && level() instanceof ServerLevel sl) {
            XenoEntity x = XenomorphMod.XENOS.get(form).get().create(sl);
            if (x != null) { x.moveTo(getX(), getY(), getZ(), getYRot(), 0); sl.addFreshEntity(x); }
            discard();
        }
    }
    @Override public boolean isImmobile() { return age < BURST || super.isImmobile(); }
    @Override protected SoundEvent getAmbientSound() { return SoundEvents.SILVERFISH_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource s) { return SoundEvents.SPIDER_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.SPIDER_DEATH; }
    @Override public float getVoicePitch() { return 1.8f; }
    @Override public void addAdditionalSaveData(CompoundTag t) { super.addAdditionalSaveData(t); t.putInt("Age", age); t.putString("Form", form.name()); }
    @Override public void readAdditionalSaveData(CompoundTag t) { super.readAdditionalSaveData(t); age = t.getInt("Age"); if (t.contains("Form")) try { form = Caste.valueOf(t.getString("Form")); } catch (IllegalArgumentException ignored) { } }
}
