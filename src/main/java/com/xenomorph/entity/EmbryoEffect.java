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
    private final boolean red, praeto;
    public EmbryoEffect(boolean red, boolean praeto) { super(MobEffectCategory.HARMFUL, red ? 0xd01010 : praeto ? 0x6070a0 : 0x8a1f1f); this.red = red; this.praeto = praeto; }

    @Override public boolean isDurationEffectTick(int duration, int amp) { return duration == 1 || (duration <= 600 && duration % 20 == 0); }

    @Override public void applyEffectTick(LivingEntity host, int amp) {
        if (!(host.level() instanceof ServerLevel sl)) return;
        var inst = host.getEffect(this);
        if (inst != null && inst.getDuration() > 1) {          // last 30 s: the chest writhes, heart pounds, vision swims; worse as the moment nears
            float k = 1 - inst.getDuration() / 600f;
            host.playSound(SoundEvents.WARDEN_HEARTBEAT, 0.6f + 0.6f * k, 0.9f + 0.4f * k);
            sl.sendParticles(ParticleTypes.DAMAGE_INDICATOR, host.getX(), host.getY() + host.getBbHeight() * 0.7, host.getZ(), 1 + (int) (4 * k), 0.15, 0.1, 0.15, 0.02);
            if (k > 0.5f) sl.sendParticles(ParticleTypes.ITEM_SLIME, host.getX(), host.getY() + host.getBbHeight() * 0.7, host.getZ(), 2, 0.2, 0.1, 0.2, 0.01);
            if (host instanceof net.minecraft.world.entity.player.Player) host.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.CONFUSION, 80, 0, false, false));
            if (k > 0.8f) host.hurt(host.damageSources().magic(), 0.5f);
            return;
        }
        ChestbursterEntity b = XenomorphMod.BURSTER.get().create(sl);
        if (b != null) {
            b.form = praeto ? Caste.STALKER : red ? (host.getMaxHealth() >= 40 ? Caste.RED_QUEEN_MOTHER : host.getRandom().nextBoolean() ? Caste.RED_WARRIOR : Caste.RED_DRONE) : host instanceof HunterEntity h ? (h.kind == HunterEntity.Kind.PREDATOR ? (host.getRandom().nextInt(4) == 0 ? Caste.BERSERKER : Caste.PREDALIEN) : (host.getRandom().nextInt(4) == 0 ? Caste.ULTRAMORPH : Caste.DEACON)) : hostForm(host);
            b.moveTo(host.getX(), host.getY() + host.getBbHeight() * 0.6, host.getZ(), host.getYRot(), 0);
            sl.addFreshEntity(b);
        }
        sl.sendParticles(ParticleTypes.DAMAGE_INDICATOR, host.getX(), host.getY() + host.getBbHeight() * 0.7, host.getZ(), 30, 0.3, 0.3, 0.3, 0.2);
        sl.sendParticles(ParticleTypes.ITEM_SLIME, host.getX(), host.getY() + host.getBbHeight() * 0.7, host.getZ(), 30, 0.3, 0.3, 0.3, 0.2);
        host.playSound(SoundEvents.RAVAGER_ROAR, 1.5f, 1.8f);
        host.kill();
    }

    /** DNA reflex: the burster inherits traits of the host species. */
    static Caste hostForm(LivingEntity h) {
        if (h instanceof net.minecraft.world.entity.animal.Cow) return Caste.BULL_ALIEN;
        if (h instanceof net.minecraft.world.entity.animal.Ocelot || h instanceof net.minecraft.world.entity.animal.Cat) return Caste.PANTHER;
        if (h instanceof net.minecraft.world.entity.animal.goat.Goat) return Caste.GOAT;
        if (h instanceof net.minecraft.world.entity.ambient.Bat) return Caste.BAT;
        if (h instanceof net.minecraft.world.entity.animal.PolarBear) return Caste.WINTERBEAST;
        if (h instanceof Wolf) return h.getRandom().nextInt(8) == 0 ? Caste.DOG_QUEEN : Caste.RUNNER;   // a dog-born queen is rare
        if (h instanceof net.minecraft.world.entity.animal.horse.AbstractHorse || h instanceof net.minecraft.world.entity.animal.Pig || h instanceof net.minecraft.world.entity.animal.Sheep) return Caste.RUNNER;
        return h.getMaxHealth() >= 60 ? Caste.PRAETORIAN : Caste.DRONE;
    }
}
