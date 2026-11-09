package com.xenomorph.entity;

import com.xenomorph.XenomorphMod;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Wolf;

/** Implanted embryo: when the timer ends, a chestburster erupts and the host dies. */
public class EmbryoEffect extends MobEffect {
    private final boolean red;
    public EmbryoEffect(boolean red) { super(MobEffectCategory.HARMFUL, red ? 0xd01010 : 0x8a1f1f); this.red = red; }

    @Override public boolean isDurationEffectTick(int duration, int amp) { return duration == 1; }

    @Override public void applyEffectTick(LivingEntity host, int amp) {
        if (!(host.level() instanceof ServerLevel sl)) return;
        ChestbursterEntity b = XenomorphMod.BURSTER.get().create(sl);
        if (b != null) {
            b.form = red ? (host.getMaxHealth() >= 40 ? Caste.RED_QUEEN_MOTHER : host.getRandom().nextBoolean() ? Caste.RED_WARRIOR : Caste.RED_DRONE) : host instanceof HunterEntity h ? (h.kind == HunterEntity.Kind.PREDATOR ? (host.getRandom().nextInt(4) == 0 ? Caste.BERSERKER : Caste.PREDALIEN) : Caste.DEACON) : host instanceof Wolf ? Caste.RUNNER : Caste.DRONE;
            b.moveTo(host.getX(), host.getY() + host.getBbHeight() * 0.6, host.getZ(), host.getYRot(), 0);
            sl.addFreshEntity(b);
        }
        sl.sendParticles(ParticleTypes.DAMAGE_INDICATOR, host.getX(), host.getY() + host.getBbHeight() * 0.7, host.getZ(), 30, 0.3, 0.3, 0.3, 0.2);
        sl.sendParticles(ParticleTypes.ITEM_SLIME, host.getX(), host.getY() + host.getBbHeight() * 0.7, host.getZ(), 30, 0.3, 0.3, 0.3, 0.2);
        host.playSound(SoundEvents.RAVAGER_ROAR, 1.5f, 1.8f);
        host.kill();
    }
}
