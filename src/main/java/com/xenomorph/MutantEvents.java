package com.xenomorph;

import com.xenomorph.entity.Caste;
import com.xenomorph.entity.XenoEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.event.entity.EntityStruckByLightningEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Lightning does not hurt a Blast xenomorph: it charges it (Mutant Creeper behaviour). */
@Mod.EventBusSubscriber(modid = XenomorphMod.ID)
public final class MutantEvents {
    private MutantEvents() {}

    @SubscribeEvent
    public static void onLightning(EntityStruckByLightningEvent e) {
        if (!(e.getEntity() instanceof XenoEntity x) || x.caste != Caste.BLAST_XENO) return;
        e.setCanceled(true);
        x.setHealth(x.getMaxHealth());
        x.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 6000, 1));
        x.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 6000, 0));
        x.addEffect(new MobEffectInstance(MobEffects.GLOWING, 6000, 0, false, false));
    }

    /** Feeding a fermented spider eye to a pig primes it for the Mutagen. */
    @SubscribeEvent
    public static void onFeed(net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract e) {
        if (!(e.getTarget() instanceof net.minecraft.world.entity.animal.Pig pig) || pig instanceof com.xenomorph.entity.SpiderPigEntity) return;
        var st = e.getItemStack();
        if (st.getItem() != net.minecraft.world.item.Items.FERMENTED_SPIDER_EYE || pig.getPersistentData().getBoolean("AteSpiderEye")) return;
        if (!e.getLevel().isClientSide) {
            pig.getPersistentData().putBoolean("AteSpiderEye", true);
            if (!e.getEntity().getAbilities().instabuild) st.shrink(1);
            if (e.getLevel() instanceof net.minecraft.server.level.ServerLevel sl) sl.sendParticles(net.minecraft.core.particles.ParticleTypes.WITCH, pig.getX(), pig.getY() + 0.8, pig.getZ(), 12, 0.3, 0.3, 0.3, 0.05);
        }
        e.setCanceled(true); e.setCancellationResult(net.minecraft.world.InteractionResult.sidedSuccess(e.getLevel().isClientSide));
    }
}
