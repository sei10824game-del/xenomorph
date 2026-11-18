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
}
