package com.xenomorph;

import com.xenomorph.entity.XenoEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

/** Makes every other hostile mob (vanilla husks, other mods' monsters) attack xenos on sight. */
@Mod.EventBusSubscriber(modid = XenomorphMod.ID)
public final class Factions {
    @SubscribeEvent
    public static void join(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide || !(e.getEntity() instanceof Mob m) || !(m instanceof Enemy) || XenoEntity.faction(m) != 0) return;
        try {
            GoalSelector ts = ObfuscationReflectionHelper.getPrivateValue(Mob.class, m, "f_21346_"); // Mob.targetSelector
            if (ts != null) ts.addGoal(3, new NearestAttackableTargetGoal<>(m, LivingEntity.class, 10, true, false, t -> XenoEntity.faction(t) != 0));
        } catch (RuntimeException ex) { XenomorphMod.LOG.warn("faction goal failed for {}", m, ex); }
    }
}
