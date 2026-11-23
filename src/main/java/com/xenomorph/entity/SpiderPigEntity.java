package com.xenomorph.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.Level;

/** A pig that ate a fermented spider eye and was dosed with Mutagen: fast, wall-climbing, and prone to sudden leaps. */
public class SpiderPigEntity extends Pig {
    private static final EntityDataAccessor<Boolean> CLIMBING = SynchedEntityData.defineId(SpiderPigEntity.class, EntityDataSerializers.BOOLEAN);

    public SpiderPigEntity(EntityType<? extends Pig> type, Level level) { super(type, level); }
    public static AttributeSupplier.Builder attributes() { return Pig.createAttributes().add(Attributes.MOVEMENT_SPEED, 0.36).add(Attributes.MAX_HEALTH, 24); }

    @Override protected void defineSynchedData() { super.defineSynchedData(); entityData.define(CLIMBING, false); }
    @Override protected PathNavigation createNavigation(Level l) { return new WallClimberNavigation(this, l); }
    @Override public boolean onClimbable() { return entityData.get(CLIMBING); }
    @Override public void tick() {
        super.tick();
        if (!level().isClientSide) {
            entityData.set(CLIMBING, horizontalCollision);
            if (onGround() && random.nextInt(160) == 0) { setDeltaMovement(getDeltaMovement().add((random.nextDouble() - 0.5) * 0.6, 0.55, (random.nextDouble() - 0.5) * 0.6)); hasImpulse = true; }   // unpredictable hops
        }
    }
    @Override public boolean causeFallDamage(float f, float m, net.minecraft.world.damagesource.DamageSource s) { return false; }
}
