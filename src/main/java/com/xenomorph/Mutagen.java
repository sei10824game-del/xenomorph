package com.xenomorph;

import com.xenomorph.entity.Caste;
import com.xenomorph.entity.XenoEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Chemical X, xenomorph style: use it on a creature to mutate it. Vanilla mobs become their xenomorph mutant counterpart
 * (zombie -> Hulk, skeleton -> Bone, creeper -> Blast, enderman -> Void, snow golem -> Frost, wither skeleton -> Wither, blaze, shulker, husk,
 * hoglin; zombies mutate by biome: snowy -> Frozen, jungle -> Jungle). Ordinary xenomorphs (hp under 200, no queens) become a random mutant.
 */
public class Mutagen extends Item {
    public Mutagen() { super(new Item.Properties().stacksTo(16)); }

    /** The mutant a creature turns into, or null if it cannot mutate. */
    public static Caste mutantFor(LivingEntity e) {
        if (e instanceof Player) return null;
        if (e instanceof XenoEntity x) {
            if (x.caste.hp >= 200 || x.caste.attacks.contains(com.xenomorph.entity.Attack.LAY) || x.cloneTicks > 0) return null;
            Caste[] all = {Caste.HULK_XENO, Caste.BONE_XENO, Caste.BLAST_XENO, Caste.VOID_XENO, Caste.FROST_XENO, Caste.WITHER_XENO, Caste.BLAZE_XENO, Caste.SHULKER_XENO, Caste.HUSK_XENO, Caste.HOG_XENO, Caste.FROZEN_XENO, Caste.JUNGLE_XENO};
            return all[e.getRandom().nextInt(all.length)];
        }
        if (e instanceof net.minecraft.world.entity.monster.Husk) return Caste.HUSK_XENO;
        if (e instanceof net.minecraft.world.entity.monster.Zombie z && !(e instanceof net.minecraft.world.entity.monster.ZombieVillager) && !(e instanceof net.minecraft.world.entity.monster.ZombifiedPiglin)) {
            var biome = e.level().getBiome(e.blockPosition());
            return biome.value().coldEnoughToSnow(e.blockPosition()) ? Caste.FROZEN_XENO : biome.is(net.minecraft.tags.BiomeTags.IS_JUNGLE) ? Caste.JUNGLE_XENO : Caste.HULK_XENO;
        }
        if (e instanceof net.minecraft.world.entity.monster.WitherSkeleton) return Caste.WITHER_XENO;
        if (e instanceof net.minecraft.world.entity.monster.AbstractSkeleton) return Caste.BONE_XENO;
        if (e instanceof net.minecraft.world.entity.monster.Creeper) return Caste.BLAST_XENO;
        if (e instanceof net.minecraft.world.entity.monster.EnderMan) return Caste.VOID_XENO;
        if (e instanceof net.minecraft.world.entity.animal.SnowGolem) return Caste.FROST_XENO;
        if (e instanceof net.minecraft.world.entity.monster.Blaze) return Caste.BLAZE_XENO;
        if (e instanceof net.minecraft.world.entity.monster.Shulker) return Caste.SHULKER_XENO;
        if (e instanceof net.minecraft.world.entity.monster.hoglin.Hoglin) return Caste.HOG_XENO;
        return null;
    }

    @Override public InteractionResult interactLivingEntity(ItemStack st, Player p, LivingEntity e, InteractionHand h) {
        if (e instanceof net.minecraft.world.entity.animal.Pig pig && !(e instanceof com.xenomorph.entity.SpiderPigEntity) && pig.getPersistentData().getBoolean("AteSpiderEye")) {   // Chemical X on a pig that ate a fermented spider eye
            if (!p.level().isClientSide && p.level() instanceof ServerLevel sl) {
                var sp = XenomorphMod.SPIDER_PIG.get().create(sl);
                if (sp != null) {
                    sp.moveTo(e.getX(), e.getY(), e.getZ(), e.getYRot(), 0); sl.addFreshEntity(sp); e.discard();
                    sl.sendParticles(ParticleTypes.SCULK_SOUL, sp.getX(), sp.getY() + 0.5, sp.getZ(), 25, 0.4, 0.4, 0.4, 0.05);
                    if (!p.getAbilities().instabuild) st.shrink(1);
                }
            }
            return InteractionResult.sidedSuccess(p.level().isClientSide);
        }
        Caste c = mutantFor(e);
        if (c == null) return InteractionResult.PASS;
        if (!p.level().isClientSide && p.level() instanceof ServerLevel sl) {
            XenoEntity m = XenomorphMod.XENOS.get(c).get().create(sl);
            if (m == null) return InteractionResult.PASS;
            m.moveTo(e.getX(), e.getY(), e.getZ(), e.getYRot(), 0);
            m.finalizeSpawn(sl, sl.getCurrentDifficultyAt(e.blockPosition()), net.minecraft.world.entity.MobSpawnType.CONVERSION, null, null);
            sl.addFreshEntity(m);
            sl.sendParticles(ParticleTypes.SCULK_SOUL, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 30, 0.5, 0.8, 0.5, 0.05);
            sl.sendParticles(ParticleTypes.ITEM_SLIME, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 30, 0.5, 0.8, 0.5, 0.1);
            sl.playSound(null, e.blockPosition(), SoundEvents.ZOMBIE_VILLAGER_CONVERTED, net.minecraft.sounds.SoundSource.HOSTILE, 1.5f, 0.5f);
            e.discard();
            if (!p.getAbilities().instabuild) st.shrink(1);
        }
        return InteractionResult.sidedSuccess(p.level().isClientSide);
    }
}
