package com.xenomorph.entity;

import com.xenomorph.XenomorphMod;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

public class AcidSpit extends ThrowableProjectile {
    private float dmg = 4;
    public boolean splash, plasma;

    public AcidSpit(EntityType<? extends AcidSpit> t, Level l) { super(t, l); }
    public AcidSpit(Level l, LivingEntity owner, float dmg) { super(XenomorphMod.SPIT.get(), owner, l); this.dmg = dmg; }

    @Override protected void defineSynchedData() {}
    @Override protected float getGravity() { return 0.03f; }

    @Override public void tick() {
        super.tick();
        if (level().isClientSide) level().addParticle(plasma ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.ITEM_SLIME, getX(), getY(), getZ(), 0, 0, 0);
        if (tickCount > 80) discard();
    }
    @Override protected void onHitEntity(EntityHitResult r) {
        if (r.getEntity() instanceof LivingEntity e && (getOwner() instanceof XenoEntity o ? !o.friendly(e) : getOwner() instanceof HunterEntity ? !(e instanceof HunterEntity) : !XenoEntity.isXeno(e))) {
            if (plasma) { e.hurt(damageSources().indirectMagic(this, getOwner()), dmg); return; }
            if (e instanceof XenoEntity k && k.caste.kSeries()) return;     // acid immunity
            e.hurt(damageSources().indirectMagic(this, getOwner()), dmg);
            e.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0));
            for (EquipmentSlot s : EquipmentSlot.values()) {
                if (s.getType() != EquipmentSlot.Type.ARMOR || e.getItemBySlot(s).isEmpty()) continue;
                e.getItemBySlot(s).hurtAndBreak(6, e, x -> x.broadcastBreakEvent(s));
            }
        }
    }
    @Override protected void onHit(HitResult r) {
        super.onHit(r);
        if (!level().isClientSide) {
            ((net.minecraft.server.level.ServerLevel) level()).sendParticles(ParticleTypes.ITEM_SLIME, getX(), getY(), getZ(), 14, 0.2, 0.2, 0.2, 0.1);
            playSound(net.minecraft.sounds.SoundEvents.LAVA_EXTINGUISH, 0.8f, 1.5f);
            if (splash) {
                for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(3, 1.5, 3), e -> !(getOwner() instanceof XenoEntity o ? o.friendly(e) : XenoEntity.isXeno(e)))) {
                    e.hurt(damageSources().indirectMagic(this, getOwner()), dmg * 0.7f);
                    e.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 0));
                }
                net.minecraft.world.entity.AreaEffectCloud c = new net.minecraft.world.entity.AreaEffectCloud(level(), getX(), getY(), getZ());
                c.setRadius(2.2f); c.setDuration(80); c.setParticle(ParticleTypes.ITEM_SLIME); c.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0));
                level().addFreshEntity(c);
            }
            discard();
        }
    }
}
